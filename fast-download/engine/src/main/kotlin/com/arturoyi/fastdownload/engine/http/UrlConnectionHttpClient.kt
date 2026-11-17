/**
 * 用 JDK 的 `HttpURLConnection` 执行下载请求。
 *
 * 没有注册其他客户端时使用它。不依赖 OkHttp。
 *
 * @file
 */
package com.arturoyi.fastdownload.engine.http

import com.arturoyi.fastdownload.core.model.Defaults
import com.arturoyi.fastdownload.core.model.HttpRequest
import com.arturoyi.fastdownload.core.model.HttpResponse
import com.arturoyi.fastdownload.core.spi.HttpClient
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

/**
 * 阻塞执行一次 HTTP 请求。
 *
 * 跟随重定向。未显式指定 `Accept-Encoding` 时发送 `identity`，避免中间层改写正文。
 */
public class UrlConnectionHttpClient : HttpClient {
    /**
     * 发送 [request] 并返回尚未读完的响应。
     *
     * @param request 方法、地址、请求头、可选区间和超时。
     * @return 状态码、响应头和正文流。
     * @throws IOException 连接失败或读响应头失败。
     */
    public override fun execute(request: HttpRequest): HttpResponse {
        val connection = URL(request.url).openConnection() as HttpURLConnection
        connection.requestMethod = request.method
        connection.instanceFollowRedirects = true
        connection.connectTimeout = request.connectTimeoutMs
        connection.readTimeout = request.readTimeoutMs
        connection.setRequestProperty("User-Agent", Defaults.USER_AGENT)
        connection.setRequestProperty("Accept-Encoding", "identity")
        request.headers.forEach { (name, value) -> connection.setRequestProperty(name, value) }
        request.range?.let { range ->
            val end = if (range.last == Long.MAX_VALUE) "" else range.last.toString()
            connection.setRequestProperty("Range", "bytes=${range.first}-$end")
        }
        val code = connection.responseCode
        val stream = try {
            if (code >= 400) connection.errorStream else connection.inputStream
        } catch (_: IOException) {
            connection.errorStream
        }
        val headers = buildMap {
            connection.headerFields.forEach { (name, values) ->
                if (name != null) put(name, values.toList())
            }
        }
        val contentRange = headers.entries
            .firstOrNull { (name, _) -> name.equals("Content-Range", ignoreCase = true) }
            ?.value
            ?.firstOrNull()
            ?.let(::parseContentRange)
        val finalUrl = connection.url?.toString()
        val redirectUrl = if (finalUrl != null && finalUrl != request.url) finalUrl else null
        return HttpResponse(
            code = code,
            headers = headers,
            body = stream,
            contentLength = connection.getHeaderField("Content-Length")?.toLongOrNull() ?: -1L,
            contentRange = contentRange,
            redirectUrl = redirectUrl,
            release = { connection.disconnect() },
        )
    }

    /**
     * 发送 `HEAD` 并读取长度。失败时返回 `-1`。
     *
     * @param request 地址、请求头和超时。
     * @return 声明的字节数。未知时为 `-1`。
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
     * 连接在每次响应关闭时释放，这里没有共享池要关。
     */
    public override fun close(): Unit = Unit
}
