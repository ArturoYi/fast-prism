/**
 * 把内容 Uri 当作下载目标。
 *
 * 文件描述符能定位时按偏移写。管道这类不能定位的目标会标明 [supportSeek] 为 false。
 *
 * @file
 */
package com.arturoyi.fastdowload.android.io

import android.content.ContentResolver
import android.net.Uri
import android.os.ParcelFileDescriptor
import com.arturoyi.fastdowload.core.spi.OutputTarget
import com.arturoyi.fastdowload.core.spi.SeekableSink
import java.io.FileOutputStream
import java.io.IOException
import java.nio.ByteBuffer

/**
 * 通过文件描述符写入一个 Uri。
 *
 * 字节直接写在目标上，[atomicCommit] 不再改名。
 *
 * @param descriptor 以读写方式打开的描述符。关闭写入端时一并关闭。
 */
public class SafUriSink(
    private val descriptor: ParcelFileDescriptor,
) : OutputTarget {
    /**
     * 用内容解析器打开 [uri]。
     *
     * @param resolver 用来打开描述符。
     * @param uri 要写入的内容地址。
     * @throws IOException 描述符打不开。
     */
    public constructor(resolver: ContentResolver, uri: Uri) : this(
        resolver.openFileDescriptor(uri, "rw") ?: throw IOException("无法打开 $uri"),
    )

    private val output = FileOutputStream(descriptor.fileDescriptor)
    private val channel = output.channel
    private val seekable: Boolean = try {
        channel.position()
        true
    } catch (_: IOException) {
        false
    }

    /**
     * 描述符能否按偏移写入。
     *
     * @return 能读取当前位置时为 true。
     */
    public override fun supportSeek(): Boolean = seekable

    /**
     * 打开写入端。
     *
     * 同一个 [SafUriSink] 只应打开一次。关闭写入端会关闭描述符。
     *
     * @return 写到这个 Uri 的写入端。
     */
    public override fun openSink(): SeekableSink = DescriptorSink(output, channel, descriptor, seekable)

    /**
     * 把目标扩展到 [length]。
     *
     * @param length 需要的字节数。
     * @throws IOException 描述符不能定位，或扩展失败。
     */
    public override fun preAllocate(length: Long) {
        if (!seekable) throw IOException("目标不支持预分配")
        if (channel.size() < length) channel.truncate(length)
    }

    /**
     * 内容已经写在 Uri 上，没有临时文件要改名。
     */
    public override fun atomicCommit(): Unit = Unit
}

private class DescriptorSink(
    private val output: FileOutputStream,
    private val channel: java.nio.channels.FileChannel,
    private val descriptor: ParcelFileDescriptor,
    private val seekable: Boolean,
) : SeekableSink {
    public override fun seek(offset: Long) {
        if (!seekable) throw IOException("目标不支持定位")
        require(offset >= 0L) { "offset 不能为负" }
        channel.position(offset)
    }

    public override fun write(bytes: ByteArray, off: Int, len: Int) {
        channel.write(ByteBuffer.wrap(bytes, off, len))
    }

    public override fun flushAndSync() {
        channel.force(true)
    }

    public override fun setLength(length: Long) {
        channel.truncate(length)
    }

    public override fun close() {
        output.close()
        descriptor.close()
    }
}
