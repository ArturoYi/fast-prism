/**
 * 下载前的试连。
 *
 * 用一个字节的区间请求确认资源长度、是否接受续传，以及资源有没有变。
 *
 * @file
 */
package com.arturoyi.fastprism.engine.trial

import com.arturoyi.fastprism.core.error.ErrorCode
import com.arturoyi.fastprism.core.error.ResumeFailedCause
import com.arturoyi.fastprism.core.model.ContentFingerprint
import com.arturoyi.fastprism.core.model.Defaults
import com.arturoyi.fastprism.core.model.HttpRequest
import com.arturoyi.fastprism.core.model.HttpResponse
import com.arturoyi.fastprism.engine.http.OkHttpDownloadClient
import java.io.IOException
import java.util.Locale
import java.util.logging.Logger

/**
 * 发送 `Range: bytes=0-0`，必要时再补一次 `HEAD`，并给出 [TrialDecision]。
 *
 * 本地有 `ETag` 时加上 `If-Match`。本地有 `ETag` 或 `Last-Modified` 时加上 `If-Range`，有标签时优先用标签。
 * 总长度先看 `Content-Range` 的 `/total`。206 的 `Content-Length` 只是这一个字节的正文长度，不能当作资源长度。
 * 2xx 仍读不到总长度时，再发不带区间、不带条件头的 `HEAD`。`HEAD` 失败、超时或不是 2xx 时保留未知长度，不把试连判失败。
 *
 * 本类不创建文件，也不修改已保存的偏移。
 *
 * @param client 执行 GET 和 HEAD 的客户端。
 */
public class ConnectTrial(
    private val client: OkHttpDownloadClient = OkHttpDownloadClient(),
) {
    /**
     * 最近一次 [execute] 里试连响应的 `Content-Disposition`。
     *
     * 没有这个头时为 null。同一次调用返回后读取；下一次 [execute] 会先清掉它。
     */
    public val contentDisposition: String?
        get() = dispositionHeader

    @Volatile
    private var dispositionHeader: String? = null
    /**
     * 对 [url] 做一次试连。
     *
     * 调用线程会阻塞到试连结束。响应正文在读完响应头后关闭，不写入文件。
     *
     * @param url 资源绝对地址。协议由更外层限制为 `http` 或 `https`。
     * @param headers 调用方附加的请求头。`Range`、`If-Match`、`If-Range` 和 `Host` 会被去掉，改由试连自己写。
     * @param baseline 已有进度和上次保存的验证信息。第一次下载用默认值。
     * @return 续传、从头下载、带原因重下、已经完成，或试连失败。
     * @throws IllegalArgumentException [url] 为空白。
     * @throws IOException 区间请求连接失败、超时或协议错误。补长度的 `HEAD` 失败时不抛出。
     */
    public fun execute(
        url: String,
        headers: Map<String, String> = emptyMap(),
        baseline: TrialBaseline = TrialBaseline(),
    ): TrialDecision {
        require(url.isNotBlank()) { "url 不能为空" }
        dispositionHeader = null
        val captured = client.execute(rangedRequest(url, headers, baseline)).use(::capture)
        dispositionHeader = captured.disposition
        val probe = if (captured.declaredLength == null && captured.code in 200..299) {
            probeLength(url, headers)
        } else {
            null
        }
        val decision = decide(captured.code, fingerprint(captured, probe), baseline)
        log(decision)
        return decision
    }

    /**
     * 构造带一个字节区间的 GET。
     *
     * 条件头按 [baseline] 附加。用户头里的区间和条件头不会进入这次请求。
     *
     * @param url 资源地址。
     * @param headers 调用方请求头。
     * @param baseline 提供 `ETag` 和 `Last-Modified`。
     * @return 方法为 GET、区间为 `0..0` 的请求。
     */
    private fun rangedRequest(
        url: String,
        headers: Map<String, String>,
        baseline: TrialBaseline,
    ): HttpRequest {
        return HttpRequest(
            url = url,
            method = "GET",
            headers = conditionalHeaders(headers, baseline),
            range = 0L..0L,
            connectTimeoutMs = Defaults.CONNECT_TIMEOUT_MS,
            readTimeoutMs = Defaults.READ_TIMEOUT_MS,
        )
    }

    /**
     * 在关闭响应前提取试连需要的头。
     *
     * 只读状态码、长度和验证头，不读正文。
     *
     * @param response 已经返回响应头、尚未关闭的响应。
     * @return 与连接无关的快照。
     */
    private fun capture(response: HttpResponse): CapturedTrial {
        return CapturedTrial(
            code = response.code,
            declaredLength = declaredResourceLength(response),
            eTag = response.header("ETag"),
            lastModified = response.header("Last-Modified"),
            acceptRanges = response.acceptsByteRanges(),
            disposition = response.header("Content-Disposition"),
        )
    }

    /**
     * 用 `HEAD` 补资源的 `Content-Length`。
     *
     * 不发送 `Range`、`If-Match` 和 `If-Range`，避免条件失败把长度探测变成 412。
     *
     * @param url 与试连相同的地址。
     * @param headers 调用方请求头，仍会去掉保留头。
     * @return 状态码为 2xx 时返回探测结果，长度未知则 [LengthProbe.contentLength] 为 null。连接失败或状态不是 2xx 时返回 null。
     */
    private fun probeLength(url: String, headers: Map<String, String>): LengthProbe? {
        val response = try {
            client.execute(headRequest(url, headers))
        } catch (_: IOException) {
            return null
        }
        return response.use { head ->
            if (head.code !in 200..299) {
                null
            } else {
                LengthProbe(
                    contentLength = head.contentLength.takeIf { length -> length >= 0L },
                    acceptRanges = head.acceptsByteRanges(),
                    eTag = head.header("ETag"),
                    lastModified = head.header("Last-Modified"),
                )
            }
        }
    }

    /**
     * 构造不带区间的 `HEAD`。
     *
     * @param url 资源地址。
     * @param headers 调用方请求头。
     * @return 方法为 HEAD、没有区间的请求。
     */
    private fun headRequest(url: String, headers: Map<String, String>): HttpRequest {
        return HttpRequest(
            url = url,
            method = "HEAD",
            headers = userHeaders(headers),
            range = null,
            connectTimeoutMs = Defaults.CONNECT_TIMEOUT_MS,
            readTimeoutMs = Defaults.READ_TIMEOUT_MS,
        )
    }

    /**
     * 合并试连快照和 `HEAD` 探测，得到 [ContentFingerprint]。
     *
     * 试连头优先。试连没有标签或修改时间时，用 `HEAD` 补上。
     * `Accept-Ranges` 只要任一次响应声明了 `bytes` 就为 true，状态码 206 也视为接受区间。
     * 2xx 且两边都没有非负总长度时，[ContentFingerprint.isChunked] 为 true。
     *
     * @param captured 区间请求的快照。
     * @param probe `HEAD` 的结果。没有补探测时为 null。
     * @return 给决策和调用方保存的指纹。
     */
    private fun fingerprint(captured: CapturedTrial, probe: LengthProbe?): ContentFingerprint {
        val length = captured.declaredLength ?: probe?.contentLength ?: -1L
        return ContentFingerprint(
            eTag = captured.eTag ?: probe?.eTag,
            lastModified = captured.lastModified ?: probe?.lastModified,
            contentLength = length,
            acceptRanges = captured.code == 206 || captured.acceptRanges || probe?.acceptRanges == true,
            isChunked = length < 0L && captured.code in 200..299,
        )
    }

    /**
     * 按状态码和本地进度选择一个 [TrialDecision]。
     *
     * 412 和 416 先处理。201、205 只在已经写过字节时要求重下。
     * 标签、修改时间或总长度对不上时重下。206 且总长度已知时可以续传。
     * 其余 2xx 在已有进度时视为服务器忽略了区间；没有进度时从头下载。
     *
     * @param code 试连状态码。
     * @param fingerprint 已经合并过 `HEAD` 的指纹。
     * @param baseline 本地进度。
     * @return 调用方接下来要执行的一种结论。
     */
    private fun decide(
        code: Int,
        fingerprint: ContentFingerprint,
        baseline: TrialBaseline,
    ): TrialDecision {
        if (code == 412) {
            return TrialDecision.Restart(
                httpCode = code,
                fingerprint = fingerprint,
                cause = ResumeFailedCause.RESPONSE_PRECONDITION_FAILED,
            )
        }
        if (code == 416) {
            val total = fingerprint.contentLength
            if (total >= 0L && baseline.downloadedBytes == total) {
                return TrialDecision.AlreadyComplete(code, fingerprint, total)
            }
            return TrialDecision.Restart(
                httpCode = code,
                fingerprint = fingerprint,
                cause = ResumeFailedCause.RANGE_NOT_SATISFIABLE,
            )
        }
        if (code == 201 && baseline.downloadedBytes > 0L) {
            return TrialDecision.Restart(
                httpCode = code,
                fingerprint = fingerprint,
                cause = ResumeFailedCause.RESPONSE_CREATED_RANGE_NOT_FROM_0,
            )
        }
        if (code == 205 && baseline.downloadedBytes > 0L) {
            return TrialDecision.Restart(
                httpCode = code,
                fingerprint = fingerprint,
                cause = ResumeFailedCause.RESPONSE_RESET_RANGE_NOT_FROM_0,
            )
        }
        identityCause(fingerprint, baseline)?.let { cause ->
            return TrialDecision.Restart(code, fingerprint, cause)
        }
        if (code !in 200..299) {
            return TrialDecision.Failed(code, fingerprint, errorCodeFor(code))
        }
        if (code == 206 && !fingerprint.isChunked) {
            return TrialDecision.Resumable(code, fingerprint)
        }
        if (baseline.downloadedBytes > 0L) {
            return TrialDecision.ServerIgnoredRange(code, fingerprint)
        }
        return TrialDecision.FromStart(code, fingerprint)
    }

    /**
     * 判断已有字节是否还对得上这次响应。
     *
     * 还没有写入时返回 null。只在本地和响应两边都有值，且原文不同时视为变化。
     * 本地曾经有总长度、这次变成未知，同样视为长度变化。
     *
     * @param fingerprint 本次指纹。
     * @param baseline 本地进度。已写字节为 0 时直接返回 null。
     * @return 需要重下的原因。可以继续时返回 null。
     */
    private fun identityCause(
        fingerprint: ContentFingerprint,
        baseline: TrialBaseline,
    ): ResumeFailedCause? {
        if (baseline.downloadedBytes == 0L) return null
        val remoteTag = fingerprint.eTag
        if (baseline.eTag != null && remoteTag != null && baseline.eTag != remoteTag) {
            return ResumeFailedCause.RESPONSE_ENTITY_TAG_CHANGED
        }
        val remoteModified = fingerprint.lastModified
        if (baseline.lastModified != null && remoteModified != null && baseline.lastModified != remoteModified) {
            return ResumeFailedCause.LAST_MODIFIED_CHANGED
        }
        val remoteLength = fingerprint.contentLength
        if (baseline.totalBytes >= 0L && remoteLength >= 0L && baseline.totalBytes != remoteLength) {
            return ResumeFailedCause.CONTENT_LENGTH_CHANGED
        }
        if (baseline.totalBytes >= 0L && remoteLength < 0L) {
            return ResumeFailedCause.CONTENT_LENGTH_CHANGED
        }
        return null
    }

    /**
     * 把不能开始下载的 HTTP 状态码映射成稳定错误码。
     *
     * 只处理试连里会进入 [TrialDecision.Failed] 的码。412 和 416 不走这里。
     *
     * @param httpCode 响应状态码。
     * @return 403、404 有专门的码，其余为 [ErrorCode.UNKNOWN]。
     */
    private fun errorCodeFor(httpCode: Int): ErrorCode {
        return when (httpCode) {
            403 -> ErrorCode.HTTP_FORBIDDEN
            404 -> ErrorCode.HTTP_NOT_FOUND
            else -> ErrorCode.UNKNOWN
        }
    }

    /**
     * 记下试连结论里的状态码和原因名。
     *
     * 不记录完整地址。
     *
     * @param decision 即将返回给调用方的结论。
     */
    private fun log(decision: TrialDecision) {
        val outcome = when (decision) {
            is TrialDecision.Resumable -> "可续传"
            is TrialDecision.FromStart -> "从头下载"
            is TrialDecision.Restart -> "重下 ${decision.cause.name}"
            is TrialDecision.AlreadyComplete -> "已完成 ${decision.totalBytes}"
            is TrialDecision.ServerIgnoredRange -> "重下 ${decision.errorCode.name}"
            is TrialDecision.Failed -> "失败 ${decision.errorCode.name}"
        }
        logger.info("试连 HTTP ${decision.httpCode}，$outcome")
    }

    private companion object {
        private val logger: Logger = Logger.getLogger("com.arturoyi.fastprism.engine.trial.ConnectTrial")
    }
}

/**
 * 区间响应里、关闭连接后仍然需要的字段。
 *
 * @property code HTTP 状态码。
 * @property declaredLength 从这一份响应就能确定的资源总长度。还不能确定时为 null。
 * @property eTag `ETag` 原文。没有时为 null。
 * @property lastModified `Last-Modified` 原文。没有时为 null。
 * @property acceptRanges 响应是否声明接受 `bytes` 区间。
 * @property disposition `Content-Disposition` 原文。没有时为 null。
 */
private data class CapturedTrial(
    val code: Int,
    val declaredLength: Long?,
    val eTag: String?,
    val lastModified: String?,
    val acceptRanges: Boolean,
    val disposition: String?,
)

/**
 * `HEAD` 补到的长度和验证头。
 *
 * @property contentLength `Content-Length`。没有或无法解析时为 null。
 * @property acceptRanges `HEAD` 是否声明接受字节区间。
 * @property eTag `HEAD` 的 `ETag`。没有时为 null。
 * @property lastModified `HEAD` 的 `Last-Modified`。没有时为 null。
 */
private data class LengthProbe(
    val contentLength: Long?,
    val acceptRanges: Boolean,
    val eTag: String?,
    val lastModified: String?,
)

/**
 * 从这一份响应确定资源总长度。
 *
 * `Content-Range` 的 `/total` 优先。204 没有正文时长度为 0。
 * 206 的 `Content-Length` 是区间正文的长度，不采用。非 2xx 只接受 `Content-Range`。
 *
 * @param response 试连或错误响应。
 * @return 非负总长度。这一份响应给不出时返回 null。
 */
private fun declaredResourceLength(response: HttpResponse): Long? {
    if (response.code == 204) {
        return response.contentLength.takeIf { length -> length >= 0L } ?: 0L
    }
    response.contentRange?.total?.takeIf { total -> total >= 0L }?.let { return it }
    if (response.code == 206 || response.code !in 200..299) return null
    return response.contentLength.takeIf { length -> length >= 0L }
}

/**
 * 去掉保留头，并按 [baseline] 写上条件头。
 *
 * `If-Range` 在有 `ETag` 时用标签，否则用 `Last-Modified`。两个都没有时不发送。
 *
 * @param headers 调用方请求头。
 * @param baseline 本地验证信息。
 * @return 试连 GET 实际携带的请求头，不含 `Range`。
 */
internal fun conditionalHeaders(
    headers: Map<String, String>,
    baseline: TrialBaseline,
): Map<String, String> {
    return buildMap {
        putAll(userHeaders(headers))
        baseline.eTag?.let { tag -> put("If-Match", tag) }
        val validator = baseline.eTag ?: baseline.lastModified
        if (validator != null) put("If-Range", validator)
    }
}

/**
 * 复制调用方请求头，并去掉试连保留的四个名字。
 *
 * 名字比较不区分大小写。
 *
 * @param headers 调用方请求头。
 * @return 可以转交给客户端的头。原来没有可保留的头时为空映射。
 */
private fun userHeaders(headers: Map<String, String>): Map<String, String> {
    return headers.filterKeys { name -> name.lowercase(Locale.ROOT) !in managedHeaderNames }
}

/**
 * 读取第一个非空的响应头。
 *
 * 名字不区分大小写。同名有多个值时取第一个。
 *
 * @param name 头字段名，例如 `ETag`。
 * @return 头字段值。没有或为空字符串时返回 null。
 */
private fun HttpResponse.header(name: String): String? {
    return headers.entries
        .firstOrNull { (key, _) -> key.equals(name, ignoreCase = true) }
        ?.value
        ?.firstOrNull()
        ?.takeIf { value -> value.isNotEmpty() }
}

/**
 * 判断 `Accept-Ranges` 是否包含 `bytes`。
 *
 * 多个值用逗号分隔。`none` 不算支持。状态码本身不在这里判断。
 *
 * @return 声明接受字节区间时为 true。
 */
private fun HttpResponse.acceptsByteRanges(): Boolean {
    val raw = header("Accept-Ranges") ?: return false
    return raw.split(',').any { token -> token.trim().equals("bytes", ignoreCase = true) }
}

/**
 * 试连自己填写的请求头名字，已经是小写。
 *
 * 调用方传入同名头时丢弃，避免和 `Range`、`If-Match`、`If-Range`、`Host` 冲突。
 */
private val managedHeaderNames: Set<String> = setOf("range", "if-match", "if-range", "host")
