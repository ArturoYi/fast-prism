/**
 * 下载引擎使用的 HTTP 请求和响应。
 *
 * 这一层不绑定具体客户端。正文以流的形式交给调用方，关闭响应时归还连接。
 *
 * @file
 */
package com.arturoyi.fastprism.core.model

import java.io.Closeable
import java.io.InputStream
import java.util.concurrent.atomic.AtomicBoolean

/**
 * `Content-Range` 中解析出的字节区间。
 *
 * 起点和终点都是闭区间，单位是字节。服务器用 `*` 表示未知的那一段时，对应属性为 null。
 *
 * @property start 区间起点。未知时为 null。
 * @property endInclusive 区间终点，包含该字节。未知时为 null。
 * @property total 整个资源的长度。未知时为 null。
 */
public data class ContentRange(
    public val start: Long?,
    public val endInclusive: Long?,
    public val total: Long?,
)

/**
 * 一次不带请求体的 HTTP 调用。
 *
 * [range] 为闭区间。终点等于 [Long.MAX_VALUE] 时，客户端应把它写成开放结尾的 `Range`。
 *
 * @property url 绝对地址。本类型不校验协议，调用方在更外层限制为 `http` 或 `https`。
 * @property method HTTP 方法，不能为空白。当前下载只使用 `GET`。
 * @property headers 调用方附加的请求头。名字和值都不由本类型检查是否保留。
 * @property range 要请求的字节闭区间。null 表示不发送 `Range`。
 * @property connectTimeoutMs 建立连接的超时，单位毫秒，必须大于 0。
 * @property readTimeoutMs 两次读取之间的超时，单位毫秒，必须大于 0。
 * @throws IllegalArgumentException 方法为空白、超时不大于 0，或区间起点为负、终点小于起点。
 */
public data class HttpRequest(
    public val url: String,
    public val method: String,
    public val headers: Map<String, String>,
    public val range: LongRange? = null,
    public val connectTimeoutMs: Int,
    public val readTimeoutMs: Int,
) {
    init {
        require(method.isNotBlank()) { "method 不能为空" }
        require(connectTimeoutMs > 0) { "connectTimeoutMs 必须大于 0" }
        require(readTimeoutMs > 0) { "readTimeoutMs 必须大于 0" }
        if (range != null) {
            require(range.first >= 0L) { "range 起点不能为负" }
            require(range.last >= range.first) { "range 不合法" }
        }
    }
}

/**
 * 一次 HTTP 响应。正文可能还没读完。
 *
 * 调用方读完或放弃后必须关闭响应。关闭是幂等的：先关正文，再执行 [release]。
 *
 * @property code HTTP 状态码。
 * @property headers 响应头。同一个名字可以有多个值，名字保留服务器原来的大小写。
 * @property body 正文字节流。没有正文时为 null。
 * @property contentLength `Content-Length` 的字节数。缺失或无法解析时为 `-1`。
 * @property contentRange 可解析的 `Content-Range`。没有该头或格式不符时为 null。
 * @property redirectUrl 发生重定向后的最终地址。没有重定向时为 null。
 * @param release 归还连接的动作。只在关闭时调用一次。
 */
public class HttpResponse(
    public val code: Int,
    public val headers: Map<String, List<String>>,
    public val body: InputStream?,
    public val contentLength: Long,
    public val contentRange: ContentRange?,
    public val redirectUrl: String?,
    private val release: () -> Unit,
) : Closeable {
    private val closed = AtomicBoolean(false)

    /**
     * 关闭正文并归还连接。
     *
     * 多个线程同时关闭时只有第一次生效。正文关闭抛错时仍会执行释放动作。
     */
    public override fun close() {
        if (!closed.compareAndSet(false, true)) return
        try {
            body?.close()
        } finally {
            release()
        }
    }
}
