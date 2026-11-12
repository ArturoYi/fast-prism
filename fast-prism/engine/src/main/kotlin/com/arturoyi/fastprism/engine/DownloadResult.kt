/**
 * 一次分块下载的结果，以及进度和重下通知。
 *
 * @file
 */
package com.arturoyi.fastprism.engine

import com.arturoyi.fastprism.core.error.DownloadError
import com.arturoyi.fastprism.core.error.ErrorCode
import com.arturoyi.fastprism.core.error.ResumeFailedCause
import com.arturoyi.fastprism.core.error.TerminalCause

/**
 * [BlockDownloadCall] 的结束状态。
 */
public sealed interface DownloadResult {
    /**
     * 目标文件已经写完。
     *
     * @property bytesWritten 最终文件的字节数。
     */
    public data class Success(
        public val bytesWritten: Long,
    ) : DownloadResult

    /**
     * 下载失败。断点保留，除非失败原因是资源已经变化并被清掉。
     *
     * @property error 稳定错误码和说明。
     */
    public data class Failure(
        public val error: DownloadError,
    ) : DownloadResult

    /**
     * 同一个目标文件正在被别的下载写入。
     *
     * @property terminal 目前只会是 [TerminalCause.FILE_BUSY]。
     */
    public data class Busy(
        public val terminal: TerminalCause,
    ) : DownloadResult
}

/**
 * 分块下载过程中的进度和重下原因。
 *
 * 回调发生在下载线程上。慢回调会拖慢对应分块，回调本身不会被排进队列。
 */
public interface BlockDownloadListener {
    /**
     * 已经同步并记入断点的字节数。
     *
     * 总长度未知时 [totalBytes] 为 `-1`。
     *
     * @param downloadedBytes 各分块已提交字节之和。
     * @param totalBytes 资源总长度。未知时为 `-1`。
     */
    public fun onProgress(downloadedBytes: Long, totalBytes: Long) {}

    /**
     * 已有字节不能沿用，接下来会从头再写。
     *
     * @param cause 不能续传的原因。
     */
    public fun onResumeFailed(cause: ResumeFailedCause) {}

    /**
     * 服务器没有按区间返回，已有进度会被丢弃后重下。
     *
     * @param errorCode 说明服务器忽略了区间，通常是 [ErrorCode.SERVER_CANCELED]。
     */
    public fun onRangeIgnored(errorCode: ErrorCode) {}

    public companion object {
        /** 不接收任何回调。 */
        public val NONE: BlockDownloadListener = object : BlockDownloadListener {}
    }
}
