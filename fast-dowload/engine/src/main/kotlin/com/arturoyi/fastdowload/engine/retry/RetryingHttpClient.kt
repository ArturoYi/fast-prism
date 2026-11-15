/**
 * 连接建立失败时再试一次，然后把错误交回去。
 *
 * 不做指数退避，也不无限循环。次数上限在任务级。
 *
 * @file
 */
package com.arturoyi.fastdowload.engine.retry

import com.arturoyi.fastdowload.core.model.HttpRequest
import com.arturoyi.fastdowload.core.model.HttpResponse
import com.arturoyi.fastdowload.core.spi.HttpClient
import java.io.IOException

/**
 * 包住另一个客户端，连接失败时立刻再调用固定次数。
 *
 * [inlineAttempts] 是额外次数。1 表示最多调用底层两次。已经返回的响应不会因为状态码再试。
 *
 * @param delegate 真正发起连接的客户端。
 * @param inlineAttempts 失败后额外尝试的次数，不能为负。
 */
public class RetryingHttpClient(
    private val delegate: HttpClient,
    private val inlineAttempts: Int = 1,
) : HttpClient {
    init {
        require(inlineAttempts >= 0) { "inlineAttempts 不能为负" }
    }

    /**
     * 发送 [request]。连接失败时最多再试 [inlineAttempts] 次。
     *
     * @param request 交给底层的请求。
     * @return 底层返回的响应，包括非 2xx。
     * @throws IOException 额外次数用完仍然连接失败。
     */
    public override fun execute(request: HttpRequest): HttpResponse {
        var attempt = 0
        while (true) {
            try {
                return delegate.execute(request)
            } catch (error: IOException) {
                if (attempt >= inlineAttempts || Thread.currentThread().isInterrupted) throw error
                attempt += 1
            }
        }
    }

    /**
     * 交给底层读取长度，不再额外重试。
     *
     * @param request 只使用地址、请求头和超时。
     * @return 底层的结果。失败时为 `-1`。
     */
    public override fun getContentLength(request: HttpRequest): Long = delegate.getContentLength(request)

    /**
     * 关闭底层客户端。
     */
    public override fun close() {
        delegate.close()
    }
}
