/**
 * 试连开始前的本地进度，以及试连结束后的决策。
 *
 * 这里只比较响应和已有记录，不改文件，也不推进已写入的偏移。
 *
 * @file
 */
package com.arturoyi.fastdowload.engine.trial

import com.arturoyi.fastdowload.core.error.ErrorCode
import com.arturoyi.fastdowload.core.error.ResumeFailedCause
import com.arturoyi.fastdowload.core.model.ContentFingerprint

/**
 * 试连时拿来和响应比对的本地进度。
 *
 * 还没有下过时用默认值：已写字节为 0，总长度未知。字符串保留上次响应头的原文。
 *
 * @property downloadedBytes 已经写入目标文件的字节数，不能为负。
 * @property totalBytes 上次确认的资源总长度。未知时为 `-1`。
 * @property eTag 上次响应的 `ETag`。没有时为 null。
 * @property lastModified 上次响应的 `Last-Modified`。没有时为 null。
 * @throws IllegalArgumentException 已写字节为负、总长度小于 `-1`，或标签、修改时间是空字符串。
 */
public data class TrialBaseline(
    public val downloadedBytes: Long = 0L,
    public val totalBytes: Long = -1L,
    public val eTag: String? = null,
    public val lastModified: String? = null,
) {
    init {
        require(downloadedBytes >= 0L) { "downloadedBytes 不能为负" }
        require(totalBytes >= -1L) { "totalBytes 不能小于 -1" }
        require(eTag == null || eTag.isNotEmpty()) { "eTag 不能为空字符串" }
        require(lastModified == null || lastModified.isNotEmpty()) { "lastModified 不能为空字符串" }
    }
}

/**
 * 一次试连对「能不能沿用已有字节」给出的结论。
 *
 * [fingerprint] 来自最终响应；总长度优先取 `Content-Range` 的 `/total`。
 * 试连本身读不到总长度时，会再发一次不带区间的 `HEAD`。
 */
public sealed interface TrialDecision {
    /** 试连看到的 HTTP 状态码。客户端已经跟随过重定向。 */
    public val httpCode: Int

    /** 这次探测得到的资源指纹。 */
    public val fingerprint: ContentFingerprint

    /**
     * 服务器接受字节区间，可以从已有偏移继续。
     *
     * [ContentFingerprint.contentLength] 为 `-1` 时总长度仍未知，后续只能单块写，不能按总长度切分。
     *
     * @property httpCode 预期是 206。
     * @property fingerprint 区间响应上的指纹。`acceptRanges` 为 true。
     */
    public data class Resumable(
        public override val httpCode: Int,
        public override val fingerprint: ContentFingerprint,
    ) : TrialDecision

    /**
     * 本地还没有已写字节，这次按单块从文件开头下载。
     *
     * 服务器返回完整正文，或者没有给出可用的资源长度时，用这个结论。
     *
     * @property httpCode 常见值是 200。
     * @property fingerprint 完整响应或补过 `HEAD` 之后的指纹。
     */
    public data class FromStart(
        public override val httpCode: Int,
        public override val fingerprint: ContentFingerprint,
    ) : TrialDecision

    /**
     * 已有字节不能沿用，必须带上 [cause] 从头再下。
     *
     * @property httpCode 触发这个结论的状态码，例如 412 或 416。
     * @property fingerprint 本次响应里仍能读到的指纹。长度未知时为 `-1`。
     * @property cause 不能续传的原因。
     */
    public data class Restart(
        public override val httpCode: Int,
        public override val fingerprint: ContentFingerprint,
        public val cause: ResumeFailedCause,
    ) : TrialDecision

    /**
     * 服务器返回 416，且本地已写字节已经等于资源总长度。
     *
     * 这是完成，不是失败。
     *
     * @property httpCode 预期是 416。
     * @property fingerprint 其中的总长度与 [totalBytes] 相同。
     * @property totalBytes `Content-Range` 里的资源总长度。
     */
    public data class AlreadyComplete(
        public override val httpCode: Int,
        public override val fingerprint: ContentFingerprint,
        public val totalBytes: Long,
    ) : TrialDecision

    /**
     * 本地已经有进度，但服务器忽略了区间请求并返回整份资源。
     *
     * [errorCode] 固定是 [ErrorCode.SERVER_CANCELED]。调用方应丢弃旧进度后重下，不能把这次 200 当成续传成功。
     *
     * @property httpCode 服务器返回的状态码，常见值是 200。
     * @property fingerprint 这份完整响应的指纹。
     * @property errorCode 说明服务器没有按区间响应。
     */
    public data class ServerIgnoredRange(
        public override val httpCode: Int,
        public override val fingerprint: ContentFingerprint,
        public val errorCode: ErrorCode = ErrorCode.SERVER_CANCELED,
    ) : TrialDecision

    /**
     * 试连得到的状态码不能开始写文件。
     *
     * @property httpCode 4xx 或 5xx，不含已经单独处理的 412 和 416。
     * @property fingerprint 错误响应上能读到的头。资源长度记为 `-1`。
     * @property errorCode 与 [httpCode] 对应的稳定错误码。没有专门码时为 [ErrorCode.UNKNOWN]。
     */
    public data class Failed(
        public override val httpCode: Int,
        public override val fingerprint: ContentFingerprint,
        public val errorCode: ErrorCode,
    ) : TrialDecision
}
