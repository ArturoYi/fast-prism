/**
 * 启动清洗的计数。
 *
 * @file
 */
package com.arturoyi.fastdowload.core.state

/**
 * [com.arturoyi.fastdowload.core.spi.DownloadRepository.sanitizeOnStartup] 改动了多少条任务。
 *
 * @property requeued 从下载中或等待网络回到队列的条数。
 * @property cleared 因脏标记清掉断点的条数。
 * @property zeroed 因文件丢失把进度归零的条数。
 */
public data class SanitizeReport(
    public val requeued: Int = 0,
    public val cleared: Int = 0,
    public val zeroed: Int = 0,
)
