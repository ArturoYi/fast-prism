/**
 * 连接失败只再试一次，不会停在无限循环里。
 *
 * @file
 */
package com.arturoyi.fastdownload.engine.retry

import com.arturoyi.fastdownload.core.model.HttpRequest
import com.arturoyi.fastdownload.core.model.HttpResponse
import com.arturoyi.fastdownload.core.spi.HttpClient
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import java.io.IOException
import java.util.concurrent.atomic.AtomicInteger

/**
 * [RetryingHttpClient] 的额外次数。
 */
class RetryingHttpClientTest {
    /**
     * 底层一直失败时，一次调用最多打到两次连接。
     */
    @Test
    fun stopsAfterOneExtraAttempt() {
        val calls = AtomicInteger()
        val client = object : HttpClient {
            override fun execute(request: HttpRequest): HttpResponse {
                calls.incrementAndGet()
                throw IOException("连接被重置")
            }

            override fun getContentLength(request: HttpRequest): Long = -1L

            override fun close() = Unit
        }
        val wrapped = RetryingHttpClient(client)
        assertThrows(IOException::class.java) {
            wrapped.execute(
                HttpRequest(
                    url = "https://example.com/a.bin",
                    method = "GET",
                    headers = emptyMap(),
                    connectTimeoutMs = 1_000,
                    readTimeoutMs = 1_000,
                ),
            )
        }
        assertEquals(2, calls.get())
    }
}
