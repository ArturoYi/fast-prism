/**
 * 从内容地址读头尾或全文。不解码。
 *
 * @file
 */
package com.arturoyi.albumcleaner.scan

import android.content.ContentResolver
import android.net.Uri
import android.os.CancellationSignal
import android.os.OperationCanceledException
import android.os.ParcelFileDescriptor
import java.io.FileInputStream
import java.nio.ByteBuffer
import java.nio.channels.FileChannel
import java.security.MessageDigest

/**
 * 按文件大小决定要不要读字节。打不开时返回 null，不让整轮扫描失败。
 */
internal interface FileBytes {
    /**
     * 头尾各最多 4KB 的 SHA-256。
     *
     * @param size 索引里的字节数。不知道时传 0，改用文件本身的长度。
     */
    fun partial(uri: String, size: Long, signal: CancellationSignal): String?

    /** 全文 SHA-256。只在局部哈希撞车时调用。 */
    fun full(uri: String, signal: CancellationSignal): String?
}

/**
 * 用文件描述符定位到头尾，中间的字节不读。
 *
 * @param resolver 应用的内容解析器。
 */
internal class ContentFileBytes(
    private val resolver: ContentResolver,
) : FileBytes {
    override fun partial(uri: String, size: Long, signal: CancellationSignal): String? {
        throwIfCancelled(signal)
        return read {
            useChannel(uri) { channel ->
                val length = fileLength(channel, size)
                val headLength = minOf(HASH_EDGE_BYTES.toLong(), length).toInt()
                val head = readAt(channel, position = 0L, length = headLength)
                throwIfCancelled(signal)
                if (length <= HASH_EDGE_BYTES) {
                    partialSha256(head, head)
                } else {
                    val tailLength = HASH_EDGE_BYTES
                    val tailStart = length - tailLength
                    val tail = readAt(channel, tailStart, tailLength)
                    partialSha256(head, tail)
                }
            }
        }
    }

    override fun full(uri: String, signal: CancellationSignal): String? {
        throwIfCancelled(signal)
        return read {
            useChannel(uri) { channel ->
                val digest = MessageDigest.getInstance("SHA-256")
                val buffer = ByteBuffer.allocate(FULL_CHUNK)
                var position = 0L
                while (true) {
                    throwIfCancelled(signal)
                    buffer.clear()
                    val readCount = channel.read(buffer, position)
                    if (readCount <= 0) break
                    digest.update(buffer.array(), 0, readCount)
                    position += readCount
                }
                digest.digest().toHex()
            }
        }
    }

    private inline fun <T> read(block: () -> T?): T? {
        return try {
            block()
        } catch (cancelled: OperationCanceledException) {
            throw cancelled
        } catch (security: SecurityException) {
            throw security
        } catch (_: Exception) {
            null
        }
    }

    private fun fileLength(channel: FileChannel, declared: Long): Long {
        if (declared > 0L) return declared
        val actual = channel.size()
        return if (actual > 0L) actual else 0L
    }

    private fun readAt(channel: FileChannel, position: Long, length: Int): ByteArray {
        if (length <= 0) return ByteArray(0)
        val buffer = ByteBuffer.allocate(length)
        var readTotal = 0
        while (readTotal < length) {
            val read = channel.read(buffer, position + readTotal)
            if (read < 0) break
            if (read == 0) break
            readTotal += read
        }
        return if (readTotal == length) buffer.array() else buffer.array().copyOf(readTotal)
    }

    private inline fun <T> useChannel(uri: String, block: (FileChannel) -> T): T? {
        val descriptor = open(uri) ?: return null
        return descriptor.use {
            FileInputStream(it.fileDescriptor).channel.use { channel -> block(channel) }
        }
    }

    private fun open(uri: String): ParcelFileDescriptor? {
        return try {
            resolver.openFileDescriptor(Uri.parse(uri), "r")
        } catch (cancelled: OperationCanceledException) {
            throw cancelled
        } catch (security: SecurityException) {
            throw security
        } catch (_: Exception) {
            null
        }
    }

    private fun throwIfCancelled(signal: CancellationSignal) {
        if (signal.isCanceled) throw OperationCanceledException()
    }

    private companion object {
        const val FULL_CHUNK: Int = 64 * 1024
    }
}
