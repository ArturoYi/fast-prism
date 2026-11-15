/**
 * 用 Cronet 执行下载请求。
 *
 * 调用方自己提供 `CronetEngine`。这个模块不附带原生库。
 *
 * @file
 */
package com.arturoyi.fastdowload.cronet

import com.arturoyi.fastdowload.core.model.Defaults
import com.arturoyi.fastdowload.core.model.HttpRequest
import com.arturoyi.fastdowload.core.model.HttpResponse
import com.arturoyi.fastdowload.core.spi.HttpClient
import com.arturoyi.fastdowload.engine.http.parseContentRange
import org.chromium.net.CronetEngine
import org.chromium.net.CronetException
import org.chromium.net.UrlRequest
import org.chromium.net.UrlResponseInfo
import java.io.IOException
import java.io.InputStream
import java.io.PipedInputStream
import java.io.PipedOutputStream
import java.nio.ByteBuffer
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference

/**
 * 把 [HttpRequest] 交给已经建好的 Cronet 引擎。
 *
 * 方法会阻塞到响应头返回。正文在回调线程里写入管道，调用方在自己的线程上读取。
 * 不安装缓存。重定向交给 Cronet 跟随。未指定 `Accept-Encoding` 时发送 `identity`。
 *
 * @param engine 调用方持有的引擎。关闭本客户端不会关闭引擎。
 */
public class CronetHttpClient(
    private val engine: CronetEngine,
) : HttpClient {
    private val executor = Executors.newCachedThreadPool { runnable ->
        Thread(runnable, "fastdowload-cronet").apply { isDaemon = true }
    }
    private val closed = AtomicBoolean(false)

    /**
     * 发送 [request] 并返回尚未读完的响应。
     *
     * 连接超过 [HttpRequest.connectTimeoutMs] 仍没有响应头时取消请求并抛出 [IOException]。
     *
     * @param request 方法、地址、请求头、可选区间和超时。
     * @return 状态码、响应头和正文流。
     * @throws IOException 连接失败、超时，或客户端已经关闭。
     */
    public override fun execute(request: HttpRequest): HttpResponse {
        check(!closed.get()) { "Cronet 客户端已关闭" }
        val headersReady = CountDownLatch(1)
        val failure = AtomicReference<IOException>()
        val status = AtomicInteger()
        val responseHeaders = AtomicReference<Map<String, List<String>>>(emptyMap())
        val finalUrl = AtomicReference<String?>(null)
        val output = PipedOutputStream()
        val input = PipedInputStream(output, PIPE_SIZE)
        lateinit var call: UrlRequest
        val head = request.method.equals("HEAD", ignoreCase = true)
        val callback = object : UrlRequest.Callback() {
            override fun onRedirectReceived(
                request: UrlRequest,
                info: UrlResponseInfo,
                newLocationUrl: String,
            ) {
                finalUrl.set(newLocationUrl)
                request.followRedirect()
            }

            override fun onResponseStarted(request: UrlRequest, info: UrlResponseInfo) {
                status.set(info.httpStatusCode)
                responseHeaders.set(info.allHeaders)
                finalUrl.set(info.url)
                headersReady.countDown()
                if (head || info.httpStatusCode == 204 || info.httpStatusCode == 205) {
                    closeQuietly(output)
                    return
                }
                request.read(ByteBuffer.allocateDirect(BUFFER_SIZE))
            }

            override fun onReadCompleted(
                request: UrlRequest,
                info: UrlResponseInfo,
                byteBuffer: ByteBuffer,
            ) {
                byteBuffer.flip()
                val bytes = ByteArray(byteBuffer.remaining())
                byteBuffer.get(bytes)
                byteBuffer.clear()
                try {
                    if (bytes.isNotEmpty()) output.write(bytes)
                } catch (error: IOException) {
                    failure.compareAndSet(null, error)
                    request.cancel()
                    closeQuietly(output)
                    return
                }
                request.read(byteBuffer)
            }

            override fun onSucceeded(request: UrlRequest, info: UrlResponseInfo) {
                closeQuietly(output)
            }

            override fun onFailed(request: UrlRequest, info: UrlResponseInfo?, error: CronetException) {
                failure.compareAndSet(null, error)
                headersReady.countDown()
                closeQuietly(output)
            }

            override fun onCanceled(request: UrlRequest, info: UrlResponseInfo?) {
                headersReady.countDown()
                closeQuietly(output)
            }
        }
        val builder = engine.newUrlRequestBuilder(request.url, callback, executor)
        builder.setHttpMethod(request.method)
        headersFor(request).forEach { (name, value) -> builder.addHeader(name, value) }
        request.range?.let { range -> builder.addHeader("Range", rangeHeader(range)) }
        call = builder.build()
        call.start()
        if (!headersReady.await(request.connectTimeoutMs.toLong(), TimeUnit.MILLISECONDS)) {
            call.cancel()
            throw IOException("连接超时")
        }
        failure.get()?.let { error ->
            call.cancel()
            throw error
        }
        val headers = responseHeaders.get()
        val contentRange = headers.entries
            .firstOrNull { (name, _) -> name.equals("Content-Range", ignoreCase = true) }
            ?.value
            ?.firstOrNull()
            ?.let(::parseContentRange)
        val resolved = finalUrl.get()
        return HttpResponse(
            code = status.get(),
            headers = headers,
            body = ErrorInputStream(input, failure),
            contentLength = headers.entries
                .firstOrNull { (name, _) -> name.equals("Content-Length", ignoreCase = true) }
                ?.value
                ?.firstOrNull()
                ?.toLongOrNull()
                ?: -1L,
            contentRange = contentRange,
            redirectUrl = resolved?.takeIf { it != request.url },
            release = {
                call.cancel()
                closeQuietly(input)
            },
        )
    }

    /**
     * 用 `HEAD` 读取长度。失败时返回 `-1`。
     *
     * @param request 地址、请求头和超时。
     * @return 字节数。未知时为 `-1`。
     */
    public override fun getContentLength(request: HttpRequest): Long {
        return try {
            execute(request.copy(method = "HEAD", range = null)).use { response ->
                if (response.code in 200..299) response.contentLength else -1L
            }
        } catch (_: IOException) {
            -1L
        }
    }

    /**
     * 停止回调线程。不关闭 [engine]。
     */
    public override fun close() {
        if (!closed.compareAndSet(false, true)) return
        executor.shutdownNow()
    }

    private fun headersFor(request: HttpRequest): Map<String, String> {
        return buildMap {
            if (request.headers.keys.none { it.equals("User-Agent", ignoreCase = true) }) {
                put("User-Agent", Defaults.USER_AGENT)
            }
            if (request.headers.keys.none { it.equals("Accept-Encoding", ignoreCase = true) }) {
                put("Accept-Encoding", "identity")
            }
            putAll(request.headers)
        }
    }

    private companion object {
        private const val BUFFER_SIZE: Int = 8_192
        private const val PIPE_SIZE: Int = 64 * 1024

        private fun closeQuietly(closeable: AutoCloseable) {
            try {
                closeable.close()
            } catch (_: Exception) {
                Unit
            }
        }
    }
}

/**
 * 把闭区间写成 Cronet 使用的 `Range` 头。
 *
 * 终点是 [Long.MAX_VALUE] 时不写终点，表示一直读到资源末尾。
 *
 * @param range 要请求的闭区间。
 * @return `bytes=起点-终点`。
 */
internal fun rangeHeader(range: LongRange): String {
    val end = if (range.last == Long.MAX_VALUE) "" else range.last.toString()
    return "bytes=${range.first}-$end"
}

private class ErrorInputStream(
    private val source: InputStream,
    private val failure: AtomicReference<IOException>,
) : InputStream() {
    override fun read(): Int {
        failure.get()?.let { throw it }
        val value = source.read()
        if (value < 0) failure.get()?.let { throw it }
        return value
    }

    override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
        failure.get()?.let { throw it }
        val count = source.read(buffer, offset, length)
        if (count < 0) failure.get()?.let { throw it }
        return count
    }

    override fun close() {
        source.close()
    }
}
