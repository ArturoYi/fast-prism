/**
 * 下载请求和重试策略共用的默认值。
 *
 * 同一项默认值只写在这里。请求、重试和 HTTP 客户端都引用这些常量。
 *
 * @file
 */
package com.arturoyi.fastdowload.core.model

import com.arturoyi.fastdowload.core.error.ErrorCode
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

/**
 * 默认值只放在这里。请求和重试策略引用这些常量，不要在别处再写一份。
 */
public object Defaults {
    /** 未指定分组时使用的分组名。 */
    public const val GROUP: String = "default"

    /** 从网络读入内存时的默认缓冲区大小，单位字节。 */
    public const val BUFFER_SIZE: Int = 8_192

    /** 累积多少字节后触发一次刷盘，单位字节。 */
    public const val FLUSH_BUFFER_BYTES: Int = 16_384

    /** 累积多少字节后触发一次 `sync`，单位字节。 */
    public const val SYNC_BUFFER_BYTES: Int = 65_536

    /** 两次 `sync` 之间的最短间隔，单位毫秒。 */
    public const val SYNC_INTERVAL_MS: Long = 2_000L

    /** 任务结束后延迟清理临时状态的时间，单位毫秒。 */
    public const val REMIT_DELAY_MS: Long = 1_500L

    /** 同时进行的下载任务数上限。 */
    public const val CONCURRENT_LIMIT: Int = 4

    /** 一条任务默认拆成的最大分块数。 */
    public const val MAX_BLOCKS_PER_TASK: Int = 5

    /** 调用方可以请求的最大分块数。 */
    public const val MAX_BLOCKS_CAP: Int = 8

    /** 默认额外重试次数，不含第一次尝试。 */
    public const val MAX_RETRY_ATTEMPTS: Int = 3

    /** 建立连接的默认超时，单位毫秒。 */
    public const val CONNECT_TIMEOUT_MS: Int = 15_000

    /** 两次读取之间的默认超时，单位毫秒。 */
    public const val READ_TIMEOUT_MS: Int = 30_000

    /** 写请求体的默认超时，单位毫秒。当前 GET 不发送请求体。 */
    public const val WRITE_TIMEOUT_MS: Int = 30_000

    /** 重试等待的指数倍数。 */
    public const val RETRY_MULTIPLIER: Double = 2.0

    /** 重试等待的抖动比例，0.2 表示最多上下浮动 20%。 */
    public const val RETRY_JITTER: Double = 0.2

    /** 网络从不可用变为可用时，是否允许提前重试。 */
    public const val RETRY_ON_NETWORK_GAIN: Boolean = true

    /** 入队后是否立刻开始下载。 */
    public const val DOWNLOAD_ON_ENQUEUE: Boolean = true

    /** 未由调用方覆盖时发送的 `User-Agent`。 */
    public const val USER_AGENT: String = "FastDowload/0.1.0"

    /** 进度通知的默认最小间隔。 */
    public val PROGRESS_INTERVAL: Duration = 300.milliseconds

    /** 第 1 次重试前的默认基础等待。 */
    public val RETRY_INITIAL_DELAY: Duration = 1.seconds

    /** 单次重试等待的默认上限。 */
    public val RETRY_MAX_DELAY: Duration = 30.seconds

    /** 未指定时的任务优先级。 */
    public val PRIORITY: Priority = Priority.NORMAL

    /** 未指定时允许的网络范围。 */
    public val NETWORK_TYPE: NetworkType = NetworkType.ALL

    /** 未指定时，队列里已有相同任务的处理方式。 */
    public val ENQUEUE_ACTION: EnqueueAction = EnqueueAction.UPDATE_ACCORDINGLY

    /** [ErrorCode.retryable] 为 true 的错误码。集合在首次读取时按枚举声明生成。 */
    public val retryableCodes: Set<ErrorCode> = ErrorCode.entries.filter { it.retryable }.toSet()
}
