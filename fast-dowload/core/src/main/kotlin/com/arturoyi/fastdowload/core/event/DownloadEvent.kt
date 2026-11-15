/**
 * 下载线程上报给命令线程的事实。上报本身不改状态。
 *
 * @file
 */
package com.arturoyi.fastdowload.core.event

import com.arturoyi.fastdowload.core.error.DownloadError

/**
 * 一次下载执行过程中发生的事。
 */
public sealed interface DownloadEvent {
    /** 任务标识。 */
    public val publicId: String

    /**
     * 下载线程已经开始写文件。
     *
     * @property publicId 任务标识。
     */
    public data class Started(
        override val publicId: String,
    ) : DownloadEvent

    /**
     * 已同步的字节数。
     *
     * @property publicId 任务标识。
     * @property downloadedBytes 已提交字节。
     * @property totalBytes 资源总长度。未知时为 `-1`。
     */
    public data class Progress(
        override val publicId: String,
        public val downloadedBytes: Long,
        public val totalBytes: Long,
    ) : DownloadEvent

    /**
     * 文件已经写完。
     *
     * @property publicId 任务标识。
     * @property bytesWritten 最终字节数。
     */
    public data class Completed(
        override val publicId: String,
        public val bytesWritten: Long,
    ) : DownloadEvent

    /**
     * 下载没有完成。
     *
     * @property publicId 任务标识。
     * @property error 失败内容。
     */
    public data class Failed(
        override val publicId: String,
        public val error: DownloadError,
    ) : DownloadEvent
}
