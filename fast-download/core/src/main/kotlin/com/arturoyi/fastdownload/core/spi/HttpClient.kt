/**
 * 下载使用的 HTTP 客户端。
 *
 * 实现可以是内置的 `HttpURLConnection`，也可以是调用方注册的 OkHttp。
 * 方法会阻塞到响应头返回，不要在界面线程调用。
 *
 * @file
 */
package com.arturoyi.fastdownload.core.spi

import com.arturoyi.fastdownload.core.model.HttpRequest
import com.arturoyi.fastdownload.core.model.HttpResponse
import java.io.Closeable

/**
 * 执行一次不带请求体的 HTTP 调用。
 */
public interface HttpClient : Closeable {
    /**
     * 发送 [request] 并返回尚未读完的响应。
     *
     * 非 2xx 也要返回，由调用方决定是否当失败。调用方必须关闭响应。
     *
     * @param request 方法、地址、请求头、可选区间和超时。
     * @return 状态码、响应头和正文流。
     */
    public fun execute(request: HttpRequest): HttpResponse

    /**
     * 用 `HEAD` 读取资源长度。
     *
     * 失败、超时或不是 2xx 时返回 `-1`，不抛给调用方。
     *
     * @param request 只使用地址、请求头和超时。方法会被改成 `HEAD`。
     * @return `Content-Length` 的字节数。未知时为 `-1`。
     */
    public fun getContentLength(request: HttpRequest): Long
}
