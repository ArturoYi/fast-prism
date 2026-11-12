/**
 * 下载任务的请求和当前记录。
 *
 * 请求只描述调用方给出的条件。记录把状态、进度和错误放在同一条任务上。
 *
 * @file
 */
package com.arturoyi.fastdowload.core.model

import com.arturoyi.fastdowload.core.error.DownloadError
import com.arturoyi.fastdowload.core.state.Status
import java.net.URI
import java.net.URISyntaxException
import java.util.Locale
import kotlin.time.Duration

/**
 * 创建一条下载任务所需的参数。
 *
 * 地址只允许 `http` 和 `https`，且不能带用户信息。目标必须是应用内部相对路径。
 * `Range`、`If-Match`、`If-Range` 和 `Host` 由引擎管理，调用方不能放进 [headers]。
 *
 * @property url 资源绝对地址。
 * @property destination 相对应用内部根目录的落盘位置。
 * @property headers 附加请求头。名字不能为空白，名字和值都不能含换行。
 * @property priority 调度优先级。当前单连接实现不读取它。
 * @property groupId 任务分组，不能为空白。
 * @property tags 调用方自定义标签，不参与请求。
 * @property networkType 允许下载的网络。当前单连接实现不读取它。
 * @property enqueueAction 队列里已有相同任务时的处理方式。当前单连接实现不读取它。
 * @property connectionCount 希望使用的连接数。null 表示用默认值；给出时必须在 1 到 [Defaults.MAX_BLOCKS_CAP]。
 * @property bufferSize 单次从网络读入内存的字节数，必须大于 0。
 * @property progressInterval 进度回调的最小间隔。null 表示由调用方自行决定；负数非法。
 * @property speedLimit 速度上限，单位是字节每秒。null 表示不限速。
 * @property integrity 完成后的校验方式。null 表示不校验。
 * @property retryPolicy 失败后的重试间隔和次数。
 * @property downloadOnEnqueue 入队后是否立即开始。
 * @property extras 不参与协议的额外字符串。
 * @throws IllegalArgumentException 地址、请求头、分组、连接数、缓冲区或进度间隔不合法。
 */
public data class DownloadRequest(
    public val url: String,
    public val destination: Destination,
    public val headers: Map<String, String> = emptyMap(),
    public val priority: Priority = Defaults.PRIORITY,
    public val groupId: String = Defaults.GROUP,
    public val tags: Map<String, String> = emptyMap(),
    public val networkType: NetworkType = Defaults.NETWORK_TYPE,
    public val enqueueAction: EnqueueAction = Defaults.ENQUEUE_ACTION,
    public val connectionCount: Int? = null,
    public val bufferSize: Int = Defaults.BUFFER_SIZE,
    public val progressInterval: Duration? = null,
    public val speedLimit: BytesPerSecond? = null,
    public val integrity: IntegritySpec? = null,
    public val retryPolicy: RetryPolicy = RetryPolicy(),
    public val downloadOnEnqueue: Boolean = Defaults.DOWNLOAD_ON_ENQUEUE,
    public val extras: BundleLike = emptyExtras(),
) {
    init {
        validateUrl(url)
        validateHeaders(headers)
        require(groupId.isNotBlank()) { "groupId 不能为空" }
        require(connectionCount == null || connectionCount in 1..Defaults.MAX_BLOCKS_CAP) {
            "connectionCount 必须在 1 到 ${Defaults.MAX_BLOCKS_CAP} 之间"
        }
        require(bufferSize > 0) { "bufferSize 必须大于 0" }
        require(progressInterval == null || progressInterval.inWholeMilliseconds >= 0L) {
            "progressInterval 不能为负"
        }
    }
}

/**
 * 不允许调用方自行设置的请求头名字，比较时使用小写。
 *
 * 这些头会改变续传或主机路由，必须由引擎生成。
 */
private val reservedHeaders: Set<String> = setOf("range", "if-match", "if-range", "host")

/**
 * 检查 [url] 是不带用户信息的 HTTP(S) 绝对地址。
 *
 * @param url 调用方传入的地址。
 * @throws IllegalArgumentException 语法非法、协议不是 `http`/`https`、缺少主机，或带有用户信息。
 */
private fun validateUrl(url: String) {
    val uri = try {
        URI(url)
    } catch (error: URISyntaxException) {
        throw IllegalArgumentException("URL 不合法", error)
    }
    require(uri.scheme == "http" || uri.scheme == "https") { "只允许 http 或 https" }
    require(!uri.host.isNullOrBlank()) { "URL 缺少主机" }
    require(uri.userInfo == null) { "URL 不能携带用户信息" }
}

/**
 * 检查调用方请求头没有空名、换行和保留名。
 *
 * @param headers 请求头名字到单个值的映射。
 * @throws IllegalArgumentException 名字为空白、名字或值含 CR/LF，或名字是保留头。
 */
private fun validateHeaders(headers: Map<String, String>) {
    headers.forEach { (name, value) ->
        require(name.isNotBlank()) { "header 名不能为空" }
        require('\r' !in name && '\n' !in name && '\r' !in value && '\n' !in value) {
            "header 不能包含换行"
        }
        require(name.lowercase(Locale.ROOT) !in reservedHeaders) {
            "不能自行设置 $name"
        }
    }
}

/**
 * 一条下载任务在某一时刻的快照。
 *
 * 字节计数里，`-1` 表示未知：[totalBytes] 未知、[etaMillis] 未知都使用这个约定。
 * [downloadedBytes] 为 0 表示还没有写入。本类型不检查字节数是否超过总长度。
 *
 * @property id 任务标识，不能为空白。
 * @property request 创建任务时的请求。
 * @property status 当前状态。
 * @property createdAt 创建时间，Unix 纪元毫秒，不能为负。
 * @property updatedAt 最近一次更新时间，Unix 纪元毫秒，不能早于 [createdAt]。
 * @property totalBytes 资源总字节数。未知时为 `-1`。
 * @property downloadedBytes 已落盘字节数，不能为负。
 * @property error 最近一次失败。没有失败时为 null。
 * @property retryAttempts 已经发生的重试次数，不能为负。
 * @property speedBps 当前速度，单位字节每秒，不能为负。
 * @property etaMillis 估计剩余时间，单位毫秒。未知时为 `-1`。
 * @throws IllegalArgumentException 标识为空白，或时间、字节、次数、速度超出上面的范围。
 */
public data class DownloadRecord(
    public val id: String,
    public val request: DownloadRequest,
    public val status: Status,
    public val createdAt: Long,
    public val updatedAt: Long,
    public val totalBytes: Long = -1L,
    public val downloadedBytes: Long = 0L,
    public val error: DownloadError? = null,
    public val retryAttempts: Int = 0,
    public val speedBps: Long = 0L,
    public val etaMillis: Long = -1L,
) {
    init {
        require(id.isNotBlank()) { "id 不能为空" }
        require(createdAt >= 0L) { "createdAt 不能为负" }
        require(updatedAt >= createdAt) { "updatedAt 不能早于 createdAt" }
        require(totalBytes >= -1L) { "totalBytes 不合法" }
        require(downloadedBytes >= 0L) { "downloadedBytes 不能为负" }
        require(retryAttempts >= 0) { "retryAttempts 不能为负" }
        require(speedBps >= 0L) { "speedBps 不能为负" }
        require(etaMillis >= -1L) { "etaMillis 不合法" }
    }

    /**
     * 用各分块已写入的字节数替换 [downloadedBytes]。
     *
     * 只接受属于本记录的分块。新进度是 [DownloadBlock.currentOffset] 之和，不修改其他字段。
     *
     * @param blocks 这条任务的分块。可以是空列表，此时进度为 0。
     * @return 进度已替换的副本。
     * @throws IllegalArgumentException 任一区块的 [DownloadBlock.recordId] 不是 [id]。
     */
    public fun withBlocks(blocks: List<DownloadBlock>): DownloadRecord {
        require(blocks.all { it.recordId == id }) { "分块不属于该任务" }
        return copy(downloadedBytes = blocks.sumOf { it.currentOffset })
    }
}
