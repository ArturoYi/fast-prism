/**
 * 按 `Range` 返回同一份资源的一段。
 *
 * 试连的 `bytes=0-0` 得到 1 个字节和总长度。其他区间得到对应切片。
 * `If-Match` 和当前标签不一致时返回 412。
 *
 * @file
 */
package com.arturoyi.fastprism.testing

import mockwebserver3.Dispatcher
import mockwebserver3.MockResponse
import mockwebserver3.RecordedRequest
import okio.Buffer

/**
 * 用一份固定字节响应区间请求。
 *
 * @param body 完整资源。
 * @param etag 当前 `ETag`。请求里的 `If-Match` 不同时返回 412。
 * @param lastModified 可选的 `Last-Modified`。
 * @param contentDisposition 可选的 `Content-Disposition`。
 * @param alwaysUnsatisfied 为 true 时，任何带 `Range` 的 GET 都返回 416。
 */
public class RangedResourceDispatcher(
    private val body: ByteArray,
    private val etag: String = "\"v1\"",
    private val lastModified: String? = null,
    private val contentDisposition: String? = null,
    private val alwaysUnsatisfied: Boolean = false,
) : Dispatcher() {
    /**
     * 在返回非试连切片之前调用。
     *
     * 试连的 `bytes=0-0` 不会触发。测试可以在这里阻塞，让另一个下载碰到文件锁。
     */
    public var beforeSlice: (RecordedRequest) -> Unit = {}

    /**
     * 按方法和区间返回 200、206、412 或 416。
     *
     * @param request 服务器收到的请求。
     * @return 带标签和可选文件名的响应。
     */
    public override fun dispatch(request: RecordedRequest): MockResponse {
        if (request.headers["If-Match"] != null && request.headers["If-Match"] != etag) {
            return status(412)
        }
        if (request.method == "HEAD") return head()
        val range = request.headers["Range"]
        if (range == null) return full()
        if (alwaysUnsatisfied) return unsatisfied()
        val parsed = parseRange(range) ?: return unsatisfied()
        if (parsed.first != 0L || parsed.last != 0L) beforeSlice(request)
        return slice(parsed.first, parsed.last)
    }

    private fun head(): MockResponse {
        return decorate(
            MockResponse.Builder()
                .code(200)
                .setHeader("Content-Length", body.size.toString())
                .setHeader("Accept-Ranges", "bytes")
                .setHeader("ETag", etag),
        ).build()
    }

    private fun full(): MockResponse {
        return decorate(
            MockResponse.Builder()
                .code(200)
                .setHeader("Accept-Ranges", "bytes")
                .setHeader("ETag", etag)
                .body(Buffer().write(body)),
        ).build()
    }

    private fun slice(start: Long, endInclusive: Long): MockResponse {
        if (body.isEmpty() || start < 0L || start >= body.size || endInclusive < start) {
            return unsatisfied()
        }
        val end = minOf(endInclusive, body.size - 1L)
        val bytes = body.copyOfRange(start.toInt(), end.toInt() + 1)
        return decorate(
            MockResponse.Builder()
                .code(206)
                .setHeader("Accept-Ranges", "bytes")
                .setHeader("ETag", etag)
                .setHeader("Content-Range", "bytes $start-$end/${body.size}")
                .body(Buffer().write(bytes)),
        ).build()
    }

    private fun unsatisfied(): MockResponse {
        return decorate(
            MockResponse.Builder()
                .code(416)
                .setHeader("Content-Range", "bytes */${body.size}")
                .setHeader("ETag", etag),
        ).build()
    }

    private fun status(code: Int): MockResponse {
        return decorate(MockResponse.Builder().code(code).setHeader("ETag", etag)).build()
    }

    private fun decorate(builder: MockResponse.Builder): MockResponse.Builder {
        if (lastModified != null) builder.setHeader("Last-Modified", lastModified)
        if (contentDisposition != null) builder.setHeader("Content-Disposition", contentDisposition)
        return builder
    }

    private fun parseRange(header: String): LongRange? {
        val match = RANGE.matchEntire(header.trim()) ?: return null
        val start = match.groupValues[1].toLongOrNull() ?: return null
        val endText = match.groupValues[2]
        val end = if (endText.isEmpty()) {
            if (body.isEmpty()) return null
            body.size - 1L
        } else {
            endText.toLongOrNull() ?: return null
        }
        if (end < start) return null
        return start..end
    }

    private companion object {
        private val RANGE: Regex = Regex("""bytes=(\d+)-(\d*)""")
    }
}

/**
 * 不声明长度的分块响应。`HEAD` 不带正文，也不写 `Content-Length`。
 *
 * @param body 完整资源。GET 按分块发送这份字节。
 */
public class ChunkedResourceDispatcher(
    private val body: ByteArray,
) : Dispatcher() {
    /**
     * GET 返回分块正文。HEAD 不带正文，也不带 `Content-Length`。
     *
     * HEAD 如果也带分块正文，客户端不会读完，下一次 GET 会接到残留字节。
     *
     * @param request 服务器收到的请求。
     * @return 200 响应。
     */
    public override fun dispatch(request: RecordedRequest): MockResponse {
        if (request.method == "HEAD") {
            return MockResponse.Builder()
                .code(200)
                .removeHeader("Content-Length")
                .build()
        }
        return MockResponse.Builder()
            .code(200)
            .chunkedBody(Buffer().write(body), maxChunkSize = 8 * 1024)
            .build()
    }
}
