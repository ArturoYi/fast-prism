/**
 * 显式注册的 OkHttp 客户端。
 *
 * 不想用内置 `HttpURLConnection` 时，把它交给 `DownloaderConfig.Builder.httpClient`。
 *
 * @file
 */
package com.arturoyi.fastdowload.okhttp

import com.arturoyi.fastdowload.core.model.HttpRequest
import com.arturoyi.fastdowload.core.model.HttpResponse
import com.arturoyi.fastdowload.core.spi.HttpClient
import com.arturoyi.fastdowload.engine.http.OkHttpDownloadClient

/**
 * 把下载请求交给 OkHttp。
 *
 * 不通过反射查找。调用方要使用它时，在初始化时自己传进来。
 */
public class OkHttpHttpClient : HttpClient {
    private val delegate = OkHttpDownloadClient()

    /**
     * 发送 [request]。
     *
     * @param request 方法、地址、请求头、可选区间和超时。
     * @return 尚未读完的响应。
     */
    public override fun execute(request: HttpRequest): HttpResponse = delegate.execute(request)

    /**
     * 用 `HEAD` 读取长度。
     *
     * @param request 地址和超时。
     * @return 字节数。未知时为 `-1`。
     */
    public override fun getContentLength(request: HttpRequest): Long = delegate.getContentLength(request)

    /**
     * 连接池留给进程，这里不关闭。
     */
    public override fun close(): Unit {
        delegate.close()
    }
}
