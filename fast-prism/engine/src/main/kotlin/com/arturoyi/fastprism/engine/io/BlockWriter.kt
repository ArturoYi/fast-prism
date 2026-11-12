/**
 * 从一个响应体里按块读出字节。
 *
 * @file
 */
package com.arturoyi.fastprism.engine.io

import com.arturoyi.fastprism.core.model.Defaults
import com.arturoyi.fastprism.engine.InterruptToken
import java.io.ByteArrayOutputStream
import java.io.InputStream

/**
 * 把网络读取和落盘节奏分开。
 *
 * 读缓冲默认是调用方给定的大小。累计到 [Defaults.SYNC_BUFFER_BYTES]，或距离上次提交已经超过
 * [Defaults.SYNC_INTERVAL_MS]，或读到结尾时，才把这段交给 [onChunk]。
 *
 * @param token 其他分块失败时变为中断。
 * @param bufferSize 单次从输入流读取的字节数，必须大于 0。
 */
internal class BlockWriter(
    private val token: InterruptToken,
    private val bufferSize: Int,
) {
    init {
        require(bufferSize > 0) { "bufferSize 必须大于 0" }
    }

    /**
     * 读取 [body]，并按节奏调用 [onChunk]。
     *
     * [remaining] 为 null 时一直读到流结束。中断后仍会提交已经读到、还没交出的字节。
     *
     * @param body 响应正文。
     * @param remaining 这一分块还要读取的字节数。未知时为 null。
     * @param onChunk 收到一段准备同步的字节。
     * @return 从流里读出的字节数。
     * @throws java.io.IOException 读取失败，或 [onChunk] 把写入失败包装成 IO 异常抛出。
     */
    internal fun pump(
        body: InputStream,
        remaining: Long?,
        onChunk: (bytes: ByteArray, off: Int, len: Int) -> Unit,
    ): Long {
        val buffer = ByteArray(bufferSize)
        val pending = ByteArrayOutputStream()
        var since = System.nanoTime()
        var readTotal = 0L
        while (!token.isInterrupted()) {
            val want = if (remaining == null) {
                buffer.size
            } else {
                val left = remaining - readTotal
                if (left <= 0L) break
                minOf(buffer.size.toLong(), left).toInt()
            }
            val read = body.read(buffer, 0, want)
            if (read < 0) break
            pending.write(buffer, 0, read)
            readTotal += read
            val elapsedMs = (System.nanoTime() - since) / 1_000_000L
            if (pending.size() >= Defaults.SYNC_BUFFER_BYTES || elapsedMs >= Defaults.SYNC_INTERVAL_MS) {
                emit(pending, onChunk)
                since = System.nanoTime()
            }
        }
        emit(pending, onChunk)
        return readTotal
    }

    private fun emit(
        pending: ByteArrayOutputStream,
        onChunk: (bytes: ByteArray, off: Int, len: Int) -> Unit,
    ) {
        if (pending.size() == 0) return
        val bytes = pending.toByteArray()
        pending.reset()
        onChunk(bytes, 0, bytes.size)
    }
}
