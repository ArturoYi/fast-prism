/**
 * 下载进度和用于判断资源是否变化的指纹。
 *
 * @file
 */
package com.arturoyi.fastprism.core.model

/**
 * 一次探测得到的资源指纹。
 *
 * 续传前用它判断服务器上的资源是否还是同一个。字符串字段保留响应头原文，不做解析。
 *
 * @property eTag `ETag` 原文。响应没有该头时为 null。
 * @property lastModified `Last-Modified` 原文。响应没有该头时为 null。
 * @property contentLength `Content-Length` 的字节数。未知时为 `-1`。
 * @property acceptRanges 服务器是否声明接受字节区间请求。
 * @property isChunked 响应是否使用分块传输。分块时通常没有可靠的 [contentLength]。
 */
public data class ContentFingerprint(
    public val eTag: String? = null,
    public val lastModified: String? = null,
    public val contentLength: Long = -1L,
    public val acceptRanges: Boolean = false,
    public val isChunked: Boolean = false,
)

/**
 * 单个分块已经下载的字节数。
 *
 * @property index 分块序号，从 0 开始。
 * @property downloadedBytes 该分块已经写入的字节数。
 * @property totalBytes 该分块计划写入的字节数。未知时由调用方约定为 `-1`。
 */
public data class BlockProgress(
    public val index: Int,
    public val downloadedBytes: Long,
    public val totalBytes: Long,
)

/**
 * 一条任务对外报告的进度。
 *
 * [totalBytes] 和 [etaMillis] 为 `-1` 时表示未知。[speedBps] 为 0 表示当前没有测到速度。
 *
 * @property recordId 对应的 [DownloadRecord.id]。
 * @property downloadedBytes 所有分块已写入字节数之和。
 * @property totalBytes 资源总字节数。未知时为 `-1`。
 * @property speedBps 当前速度，单位字节每秒。
 * @property etaMillis 按当前速度估计的剩余毫秒数。未知时为 `-1`。
 * @property blocks 各分块进度。单连接下载可以是空列表。
 */
public data class Progress(
    public val recordId: String,
    public val downloadedBytes: Long,
    public val totalBytes: Long,
    public val speedBps: Long,
    public val etaMillis: Long,
    public val blocks: List<BlockProgress> = emptyList(),
)
