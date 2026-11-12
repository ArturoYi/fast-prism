/**
 * 用 OkHttp 执行单次下载请求。
 *
 * @file
 */
package com.arturoyi.fastprism.engine.http

import com.arturoyi.fastprism.core.model.Defaults
import com.arturoyi.fastprism.core.model.HttpRequest
import com.arturoyi.fastprism.core.model.HttpResponse
import okhttp3.Dispatcher
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import java.io.IOException
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

/**
 * 阻塞执行一次 HTTP 请求，并把 OkHttp 响应转成 [HttpResponse]。
 *
 * 客户端进程内共享，连接池可以复用。每次调用仍按 [HttpRequest] 覆盖连接和读取超时。
 * 不安装缓存，不在这一层重试；跟随重定向。调度线程是守护线程，避免短命进程在空闲回收前退不出。
 * 未显式指定 `Accept-Encoding` 时发送 `identity`，关闭 OkHttp 的透明 gzip，使落盘字节与响应体一致。
 *
 * 调用会一直阻塞到响应头返回。返回后正文仍未读完，调用方必须关闭 [HttpResponse]。
 */
public class OkHttpDownloadClient {
    /**
     * 发送 [request] 并返回尚未读完的响应。
     *
     * 非 2xx 同样返回，正文是错误响应体。连接或读超时失败时取消本次调用并抛出 [IOException]。
     *
     * @param request 方法、地址、请求头、可选字节区间和超时。本客户端不发送请求体，因此只适用于 `GET` 与 `HEAD`。
     * @return 状态码、响应头和正文流。无 `Content-Length` 或无法解析时，[HttpResponse.contentLength] 为 `-1`。
     * @throws IOException 连接失败、超时或协议错误。此时连接已释放。
     * @throws IllegalArgumentException [request] 的方法需要请求体，或地址不被 OkHttp 接受。
     */
    public fun execute(request: HttpRequest): HttpResponse {
        val call = clientFor(request).newCall(toOkHttpRequest(request))
        val response = try {
            call.execute()
        } catch (error: IOException) {
            call.cancel()
            throw error
        }
        return try {
            toHttpResponse(response)
        } catch (error: RuntimeException) {
            response.close()
            throw error
        }
    }

    /**
     * 复制共享客户端并套用这一次请求的超时。
     *
     * `newBuilder` 继续使用同一连接池和调度器。
     *
     * @param request 提供连接超时和读取超时，单位毫秒。
     * @return 只用于这一次调用的客户端。
     */
    private fun clientFor(request: HttpRequest): OkHttpClient {
        return sharedClient.newBuilder()
            .connectTimeout(request.connectTimeoutMs.toLong(), TimeUnit.MILLISECONDS)
            .readTimeout(request.readTimeoutMs.toLong(), TimeUnit.MILLISECONDS)
            .writeTimeout(Defaults.WRITE_TIMEOUT_MS.toLong(), TimeUnit.MILLISECONDS)
            .build()
    }

    /**
     * 把 [HttpRequest] 转成 OkHttp 请求。
     *
     * `Range` 在用户请求头之后写入，因此区间头覆盖同名头。终点为 [Long.MAX_VALUE] 时不写结束字节，表示直到资源末尾。
     *
     * @param request 已经过 [HttpRequest] 校验的请求。
     * @return 不带请求体的 OkHttp 请求。
     */
    private fun toOkHttpRequest(request: HttpRequest): Request {
        val builder = Request.Builder()
            .url(request.url)
            .method(request.method, body = null)
        mergedHeaders(request).forEach { (name, value) ->
            builder.header(name, value)
        }
        request.range?.let { range ->
            val end = if (range.last == Long.MAX_VALUE) "" else range.last.toString()
            builder.header("Range", "bytes=${range.first}-$end")
        }
        return builder.build()
    }

    /**
     * 把已经收到响应头的 OkHttp 响应映射为 [HttpResponse]。
     *
     * 不缓冲正文。关闭返回的 [HttpResponse] 时会关闭正文流并归还连接。
     * 发生过重定向时，[HttpResponse.redirectUrl] 是最终地址；否则为 null。
     *
     * @param response 由本次 `Call.execute` 返回、尚未关闭的响应。
     * @return 调用方拥有的响应。不要在返回前关闭 [response]。
     */
    private fun toHttpResponse(response: Response): HttpResponse {
        val headers = response.headers.toMultimap()
        val contentRange = headers.entries
            .firstOrNull { (name, _) -> name.equals("Content-Range", ignoreCase = true) }
            ?.value
            ?.firstOrNull()
            ?.let(::parseContentRange)
        val redirectUrl = if (response.priorResponse != null) {
            response.request.url.toString()
        } else {
            null
        }
        return HttpResponse(
            code = response.code,
            headers = headers,
            body = response.body.byteStream(),
            contentLength = contentLengthBytes(response),
            contentRange = contentRange,
            redirectUrl = redirectUrl,
            release = { response.close() },
        )
    }

    /**
     * 读取 `Content-Length`。
     *
     * 头缺失或不是整数时返回 `-1`，与分块传输时长度未知的约定一致。
     *
     * @param response 已收到响应头的 OkHttp 响应。
     * @return 声明的正文字节数；未知时为 `-1`。
     */
    private fun contentLengthBytes(response: Response): Long {
        return response.header("Content-Length")?.toLongOrNull() ?: -1L
    }

    /**
     * 合并默认头和调用方请求头。
     *
     * 先放 `User-Agent` 和 `Accept-Encoding: identity`，再放入 [HttpRequest.headers]，因此调用方可以覆盖这两项。
     *
     * @param request 提供调用方请求头。
     * @return 实际要写到链路上的单值请求头。
     */
    private fun mergedHeaders(request: HttpRequest): Map<String, String> {
        return buildMap {
            put("User-Agent", Defaults.USER_AGENT)
            put("Accept-Encoding", "identity")
            putAll(request.headers)
        }
    }

    private companion object {
        /**
         * 进程内共享的 OkHttp 客户端。
         *
         * 跟随 HTTP 与 HTTPS 重定向，不缓存，连接失败不在这里重试。线程池与 OkHttp 默认调度器相同，
         * 但线程设为守护线程。
         */
        private val sharedClient: OkHttpClient = OkHttpClient.Builder()
            .dispatcher(daemonDispatcher())
            .followRedirects(true)
            .followSslRedirects(true)
            .retryOnConnectionFailure(false)
            .cache(null)
            .build()

        /**
         * 创建与 OkHttp 默认调度器同等规模的守护线程池。
         *
         * 核心线程数为 0，空闲 60 秒后回收。守护线程不会拖住测试进程或短命进程退出。
         *
         * @return 交给 [Dispatcher] 的执行器。
         */
        private fun daemonDispatcher(): Dispatcher {
            val executor = Executors.newCachedThreadPool { runnable ->
                Thread(runnable, "fast-prism-okhttp").apply { isDaemon = true }
            }
            return Dispatcher(executor)
        }
    }
}
