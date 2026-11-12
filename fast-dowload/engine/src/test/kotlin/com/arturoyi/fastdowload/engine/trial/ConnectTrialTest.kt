/**
 * 试连对 200、206、412、416 和分块响应的结论。
 *
 * @file
 */
package com.arturoyi.fastdowload.engine.trial

import com.arturoyi.fastdowload.core.error.ErrorCode
import com.arturoyi.fastdowload.core.error.ResumeFailedCause
import com.arturoyi.fastdowload.core.model.ContentFingerprint
import com.arturoyi.fastdowload.engine.trial.TrialDecision.AlreadyComplete
import com.arturoyi.fastdowload.engine.trial.TrialDecision.Failed
import com.arturoyi.fastdowload.engine.trial.TrialDecision.FromStart
import com.arturoyi.fastdowload.engine.trial.TrialDecision.Resumable
import com.arturoyi.fastdowload.engine.trial.TrialDecision.Restart
import com.arturoyi.fastdowload.engine.trial.TrialDecision.ServerIgnoredRange
import com.arturoyi.fastdowload.testing.MockDownloadServerRule
import mockwebserver3.MockResponse
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.RegisterExtension

/**
 * [ConnectTrial] 对本地服务器的请求头和决策。
 */
class ConnectTrialTest {
    /** 每个测试方法单独启动并关闭的本地服务器。 */
    @JvmField
    @RegisterExtension
    val server = MockDownloadServerRule()

    /**
     * 200 且带 `Content-Length` 时只发区间 GET，结论是从头下载。
     *
     * 指纹长度取这份响应的 `Content-Length`，并且不再补 `HEAD`。
     */
    @Test
    fun okWithoutRangeSupportStartsFromZero() {
        enqueue(textBody(code = 200, body = "abcd"))
        val decision = assertInstanceOf(FromStart::class.java, execute())

        assertEquals(200, decision.httpCode)
        assertEquals(
            ContentFingerprint(contentLength = 4L, acceptRanges = false, isChunked = false),
            decision.fingerprint,
        )
        assertEquals(1, server.server.requestCount)
        val recorded = server.server.takeRequest()
        assertEquals("GET", recorded.method)
        assertEquals("bytes=0-0", recorded.headers["Range"])
    }

    /**
     * 200 同时声明 `Accept-Ranges: bytes` 时，指纹记下这个能力，决策仍是从头下载。
     *
     * 这次响应是整份正文，不是 206。
     */
    @Test
    fun fullResponseRecordsAcceptRangesButStartsFromZero() {
        enqueue(textBody(200, "abcd", mapOf("Accept-Ranges" to "bytes")))
        val decision = assertInstanceOf(FromStart::class.java, execute())

        assertEquals(true, decision.fingerprint.acceptRanges)
        assertEquals(4L, decision.fingerprint.contentLength)
        assertEquals(1, server.server.requestCount)
    }

    /**
     * 206 的资源长度取 `Content-Range` 的总长度，不取这一个字节的 `Content-Length`。
     *
     * 本地有标签时，请求带上 `If-Match` 和 `If-Range`，并且不补 `HEAD`。
     */
    @Test
    fun partialResponseUsesContentRangeTotal() {
        enqueue(
            textBody(
                206,
                "x",
                mapOf(
                    "Content-Range" to "bytes 0-0/128",
                    "ETag" to "\"v1\"",
                    "Last-Modified" to "Wed, 21 Oct 2015 07:28:00 GMT",
                    "Accept-Ranges" to "bytes",
                ),
            ),
        )
        val decision = assertInstanceOf(
            Resumable::class.java,
            execute(
                TrialBaseline(
                    downloadedBytes = 40L,
                    totalBytes = 128L,
                    eTag = "\"v1\"",
                    lastModified = "Wed, 21 Oct 2015 07:28:00 GMT",
                ),
            ),
        )

        assertEquals(206, decision.httpCode)
        assertEquals(
            ContentFingerprint(
                eTag = "\"v1\"",
                lastModified = "Wed, 21 Oct 2015 07:28:00 GMT",
                contentLength = 128L,
                acceptRanges = true,
                isChunked = false,
            ),
            decision.fingerprint,
        )
        assertEquals(1, server.server.requestCount)
        val recorded = server.server.takeRequest()
        assertEquals("bytes=0-0", recorded.headers["Range"])
        assertEquals("\"v1\"", recorded.headers["If-Match"])
        assertEquals("\"v1\"", recorded.headers["If-Range"])
    }

    /**
     * 没有验证头时，206 且总长度一致仍然可以续传。
     */
    @Test
    fun partialResponseWithoutValidatorCanResume() {
        enqueue(textBody(206, "x", mapOf("Content-Range" to "bytes 0-0/128")))
        val decision = assertInstanceOf(
            Resumable::class.java,
            execute(TrialBaseline(downloadedBytes = 50L, totalBytes = 128L)),
        )

        assertEquals(128L, decision.fingerprint.contentLength)
        assertEquals(true, decision.fingerprint.acceptRanges)
    }

    /**
     * 只有 `Last-Modified` 时，`If-Range` 用修改时间，并且不发送 `If-Match`。
     */
    @Test
    fun ifRangeUsesLastModifiedWhenEtagIsAbsent() {
        enqueue(
            textBody(
                206,
                "x",
                mapOf(
                    "Content-Range" to "bytes 0-0/8",
                    "Last-Modified" to "Wed, 21 Oct 2015 07:28:00 GMT",
                ),
            ),
        )
        execute(TrialBaseline(lastModified = "Wed, 21 Oct 2015 07:28:00 GMT"))
        val recorded = server.server.takeRequest()

        assertEquals("Wed, 21 Oct 2015 07:28:00 GMT", recorded.headers["If-Range"])
        assertNull(recorded.headers["If-Match"])
    }

    /**
     * 已有进度且 `ETag` 不同，结论是按标签变化重下。
     */
    @Test
    fun changedEtagRestarts() {
        enqueue(
            textBody(
                206,
                "x",
                mapOf(
                    "Content-Range" to "bytes 0-0/128",
                    "ETag" to "\"v2\"",
                ),
            ),
        )
        val decision = assertInstanceOf(
            Restart::class.java,
            execute(TrialBaseline(downloadedBytes = 40L, totalBytes = 128L, eTag = "\"v1\"")),
        )

        assertEquals(ResumeFailedCause.RESPONSE_ENTITY_TAG_CHANGED, decision.cause)
        assertEquals("\"v2\"", decision.fingerprint.eTag)
    }

    /**
     * 标签相同、修改时间不同，结论是按修改时间重下。
     */
    @Test
    fun changedLastModifiedRestarts() {
        enqueue(
            textBody(
                206,
                "x",
                mapOf(
                    "Content-Range" to "bytes 0-0/128",
                    "ETag" to "\"v1\"",
                    "Last-Modified" to "Thu, 22 Oct 2015 07:28:00 GMT",
                ),
            ),
        )
        val decision = assertInstanceOf(
            Restart::class.java,
            execute(
                TrialBaseline(
                    downloadedBytes = 40L,
                    totalBytes = 128L,
                    eTag = "\"v1\"",
                    lastModified = "Wed, 21 Oct 2015 07:28:00 GMT",
                ),
            ),
        )

        assertEquals(ResumeFailedCause.LAST_MODIFIED_CHANGED, decision.cause)
    }

    /**
     * 206 给出的总长度和本地记录不同，结论是按长度变化重下。
     */
    @Test
    fun changedContentLengthRestarts() {
        enqueue(
            textBody(
                206,
                "x",
                mapOf(
                    "Content-Range" to "bytes 0-0/200",
                    "ETag" to "\"v1\"",
                ),
            ),
        )
        val decision = assertInstanceOf(
            Restart::class.java,
            execute(TrialBaseline(downloadedBytes = 40L, totalBytes = 128L, eTag = "\"v1\"")),
        )

        assertEquals(ResumeFailedCause.CONTENT_LENGTH_CHANGED, decision.cause)
        assertEquals(200L, decision.fingerprint.contentLength)
    }

    /**
     * 412 是前置条件失败，请求里带有本地 `ETag`。
     */
    @Test
    fun preconditionFailedRestarts() {
        enqueue(textBody(412, headers = mapOf("ETag" to "\"v2\"")))
        val decision = assertInstanceOf(
            Restart::class.java,
            execute(TrialBaseline(downloadedBytes = 10L, eTag = "\"v1\"")),
        )

        assertEquals(412, decision.httpCode)
        assertEquals(ResumeFailedCause.RESPONSE_PRECONDITION_FAILED, decision.cause)
        assertEquals("\"v2\"", decision.fingerprint.eTag)
        assertEquals("\"v1\"", server.server.takeRequest().headers["If-Match"])
    }

    /**
     * 416 且 `Content-Range` 的总长度等于已写字节时，视为已经完成。
     */
    @Test
    fun rangeNotSatisfiableCompletesWhenLocalBytesMatchTotal() {
        enqueue(textBody(416, headers = mapOf("Content-Range" to "bytes */128")))
        val decision = assertInstanceOf(
            AlreadyComplete::class.java,
            execute(TrialBaseline(downloadedBytes = 128L, totalBytes = 128L)),
        )

        assertEquals(416, decision.httpCode)
        assertEquals(128L, decision.totalBytes)
        assertEquals(128L, decision.fingerprint.contentLength)
    }

    /**
     * 416 但本地字节还没写满，结论是区间无法满足，要重下。
     */
    @Test
    fun rangeNotSatisfiableRestartsWhenLocalBytesDiffer() {
        enqueue(textBody(416, headers = mapOf("Content-Range" to "bytes */128")))
        val decision = assertInstanceOf(
            Restart::class.java,
            execute(TrialBaseline(downloadedBytes = 40L, totalBytes = 128L)),
        )

        assertEquals(ResumeFailedCause.RANGE_NOT_SATISFIABLE, decision.cause)
        assertEquals(128L, decision.fingerprint.contentLength)
    }

    /**
     * 空资源的 416 总长度为 0，已写字节也是 0 时视为完成。
     */
    @Test
    fun emptyResourceCanAlreadyBeComplete() {
        enqueue(textBody(416, headers = mapOf("Content-Range" to "bytes */0")))
        val decision = assertInstanceOf(AlreadyComplete::class.java, execute())

        assertEquals(0L, decision.totalBytes)
    }

    /**
     * 已经写过字节又收到 200，说明服务器没有按区间响应。
     */
    @Test
    fun okAfterProgressMeansServerIgnoredRange() {
        enqueue(textBody(200, "abcd", mapOf("ETag" to "\"v1\"")))
        val decision = assertInstanceOf(
            ServerIgnoredRange::class.java,
            execute(TrialBaseline(downloadedBytes = 2L, totalBytes = 4L, eTag = "\"v1\"")),
        )

        assertEquals(ErrorCode.SERVER_CANCELED, decision.errorCode)
        assertEquals(4L, decision.fingerprint.contentLength)
    }

    /**
     * 分块的 200 读不到总长度时补一次 `HEAD`，指纹采用 `HEAD` 的长度和标签。
     *
     * `HEAD` 不带 `Range` 和 `If-Match`。
     */
    @Test
    fun chunkedTrialFallsBackToHead() {
        enqueue(chunked())
        enqueue(
            textBody(
                200,
                "0123456789",
                mapOf(
                    "ETag" to "\"from-head\"",
                    "Accept-Ranges" to "bytes",
                ),
            ),
        )
        val decision = assertInstanceOf(
            FromStart::class.java,
            execute(headers = mapOf("X-Request-Id" to "trace-1", "If-Match" to "\"caller\"")),
        )

        assertEquals(
            ContentFingerprint(
                eTag = "\"from-head\"",
                contentLength = 10L,
                acceptRanges = true,
                isChunked = false,
            ),
            decision.fingerprint,
        )
        assertEquals(2, server.server.requestCount)
        val ranged = server.server.takeRequest()
        val head = server.server.takeRequest()
        assertEquals("GET", ranged.method)
        assertEquals("bytes=0-0", ranged.headers["Range"])
        assertEquals("trace-1", ranged.headers["X-Request-Id"])
        assertNull(ranged.headers["If-Match"])
        assertEquals("HEAD", head.method)
        assertNull(head.headers["Range"])
        assertNull(head.headers["If-Match"])
        assertEquals("trace-1", head.headers["X-Request-Id"])
    }

    /**
     * 区间响应和 `HEAD` 都是分块时，总长度保持未知，并记为分块。
     */
    @Test
    fun chunkedHeadKeepsLengthUnknown() {
        enqueue(chunked())
        enqueue(chunked())
        val decision = assertInstanceOf(FromStart::class.java, execute())

        assertEquals(-1L, decision.fingerprint.contentLength)
        assertEquals(true, decision.fingerprint.isChunked)
        assertEquals(2, server.server.requestCount)
        assertEquals("GET", server.server.takeRequest().method)
        assertEquals("HEAD", server.server.takeRequest().method)
    }

    /**
     * 本地有总长度，这次却读不到长度时，按长度变化重下。
     */
    @Test
    fun knownLengthBecomesUnknownRestarts() {
        enqueue(chunked())
        enqueue(chunked())
        val decision = assertInstanceOf(
            Restart::class.java,
            execute(TrialBaseline(downloadedBytes = 10L, totalBytes = 100L)),
        )

        assertEquals(ResumeFailedCause.CONTENT_LENGTH_CHANGED, decision.cause)
        assertEquals(true, decision.fingerprint.isChunked)
    }

    /**
     * 201 且已经有进度时，区间不是从 0 重新开始，要重下。
     */
    @Test
    fun createdResponseWithProgressRestarts() {
        enqueue(textBody(code = 201, body = "new"))
        val decision = assertInstanceOf(
            Restart::class.java,
            execute(TrialBaseline(downloadedBytes = 3L)),
        )

        assertEquals(ResumeFailedCause.RESPONSE_CREATED_RANGE_NOT_FROM_0, decision.cause)
    }

    /**
     * 205 且已经有进度时，同样不能沿用旧区间。
     */
    @Test
    fun resetResponseWithProgressRestarts() {
        enqueue(textBody(code = 205))
        val decision = assertInstanceOf(
            Restart::class.java,
            execute(TrialBaseline(downloadedBytes = 3L)),
        )

        assertEquals(ResumeFailedCause.RESPONSE_RESET_RANGE_NOT_FROM_0, decision.cause)
    }

    /**
     * 404 不创建下载，错误码是未找到，并且不补 `HEAD`。
     */
    @Test
    fun notFoundFailsWithoutHead() {
        enqueue(textBody(code = 404, body = "missing"))
        val decision = assertInstanceOf(Failed::class.java, execute())

        assertEquals(404, decision.httpCode)
        assertEquals(ErrorCode.HTTP_NOT_FOUND, decision.errorCode)
        assertEquals(-1L, decision.fingerprint.contentLength)
        assertEquals(1, server.server.requestCount)
    }

    /**
     * 403 映射成禁止访问。
     */
    @Test
    fun forbiddenFails() {
        enqueue(textBody(code = 403, body = "no"))
        val decision = assertInstanceOf(Failed::class.java, execute())

        assertEquals(ErrorCode.HTTP_FORBIDDEN, decision.errorCode)
    }

    /**
     * 没有专门错误码的 5xx 记为未知错误，并保留状态码。
     */
    @Test
    fun otherStatusFailsAsUnknown() {
        enqueue(textBody(code = 500, body = "down"))
        val decision = assertInstanceOf(Failed::class.java, execute())

        assertEquals(500, decision.httpCode)
        assertEquals(ErrorCode.UNKNOWN, decision.errorCode)
    }

    /**
     * 调用方自己写的 `Range` 不会覆盖试连的 `bytes=0-0`。
     */
    @Test
    fun callerRangeIsReplaced() {
        enqueue(textBody(code = 200, body = "abcd"))
        execute(headers = mapOf("Range" to "bytes=5-9", "X-Request-Id" to "trace-2"))
        val recorded = server.server.takeRequest()

        assertEquals("bytes=0-0", recorded.headers["Range"])
        assertEquals("trace-2", recorded.headers["X-Request-Id"])
    }

    /**
     * 向 [server] 排入一个按队列返回的响应。
     *
     * @param response 下一次请求要返回的响应。
     */
    private fun enqueue(response: MockResponse) {
        server.server.enqueue(response)
    }

    /**
     * 对当前服务器上的 `/file.bin` 执行试连。
     *
     * @param baseline 本地进度，默认是还没写过。
     * @param headers 附加请求头。
     * @return 试连结论。
     */
    private fun execute(
        baseline: TrialBaseline = TrialBaseline(),
        headers: Map<String, String> = emptyMap(),
    ): TrialDecision {
        return ConnectTrial().execute(server.url("/file.bin"), headers, baseline)
    }
}

/**
 * 构造带正文的脚本响应，并附上 [headers]。
 *
 * 先写正文，再写头，这样 `Content-Range` 不会被正文字节数覆盖。
 *
 * @param code HTTP 状态码。
 * @param body UTF-8 正文。空字符串保留构建器默认的长度 0。
 * @param headers 响应头。同名头以后写的值为准。
 * @return 可以交给 MockWebServer 的响应。
 */
private fun textBody(
    code: Int,
    body: String = "",
    headers: Map<String, String> = emptyMap(),
): MockResponse {
    val builder = MockResponse.Builder().code(code)
    if (body.isNotEmpty()) builder.body(body)
    headers.forEach { (name, value) -> builder.setHeader(name, value) }
    return builder.build()
}

/**
 * 构造没有 `Content-Length` 的分块响应。
 *
 * @return 正文为 `partial`、每块最多 4 字节的 200 响应。
 */
private fun chunked(): MockResponse {
    return MockResponse.Builder()
        .chunkedBody(body = "partial", maxChunkSize = 4)
        .build()
}
