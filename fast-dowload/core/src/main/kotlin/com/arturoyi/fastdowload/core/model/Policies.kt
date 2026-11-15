/**
 * 优先级、网络约束、入队动作、重试和完整性校验。
 *
 * @file
 */
package com.arturoyi.fastdowload.core.model

import com.arturoyi.fastdowload.core.error.ErrorCode
import kotlin.math.min
import kotlin.math.pow
import kotlin.random.Random
import kotlin.time.Duration

/**
 * 任务调度优先级。
 *
 * 数值越大越优先。[value] 只用于排序，不直接传给系统调度器。
 *
 * @property value 排序权重。
 */
public enum class Priority(
    public val value: Int,
) {
    /** 优先于普通任务。 */
    HIGH(1),

    /** 默认优先级。 */
    NORMAL(0),

    /** 让路给更高优先级的任务。 */
    LOW(-1),
}

/**
 * 允许开始下载的网络范围。
 *
 * 当前单连接实现不读取这个值。
 */
public enum class NetworkType {
    /** 任何可用网络都可以下载。 */
    ALL,

    /** 只在不计流量的网络上下载。 */
    UNMETERED,

    /** 只在 Wi-Fi 上下载。 */
    WIFI_ONLY,
}

/**
 * 队列中已经存在相同任务时如何处理新的入队请求。
 */
public enum class EnqueueAction {
    /** 丢掉已有任务，用新请求替换。 */
    REPLACE_EXISTING,

    /** 按已有任务的状态决定更新哪些字段。 */
    UPDATE_ACCORDINGLY,

    /** 已有任务时拒绝新的入队。 */
    DO_NOT_ENQUEUE_IF_EXISTING,

    /** 保留已有文件，为新任务生成不冲突的文件名。 */
    INCREMENT_FILE_NAME,
}

/**
 * 大于 0 的速度上限。
 *
 * @property value 每秒最多写入的字节数。
 * @throws IllegalArgumentException [value] 不大于 0。
 */
@JvmInline
public value class BytesPerSecond(
    public val value: Long,
) {
    init {
        require(value > 0L) { "速度必须大于 0" }
    }
}

/**
 * 一组不参与 HTTP 的字符串附加数据。
 *
 * 用字符串映射代替 Android `Bundle`，使模型可以留在纯 JVM 模块里。
 *
 * @property entries 键到值的映射。空映射表示没有附加数据。
 */
public data class BundleLike(
    public val entries: Map<String, String> = emptyMap(),
)

/**
 * 没有附加数据的 [BundleLike]。
 *
 * @return 条目为空的实例。每次调用都是新对象，内容相同。
 */
public fun emptyExtras(): BundleLike = BundleLike()

/**
 * 失败后等待多久再试，以及最多试几次。
 *
 * [delayFor] 使用指数退避，并用 [jitter] 按比例上下浮动。等待时间限制在 [initialDelay] 和 [maxDelay] 之间。
 *
 * @property maxAttempts 额外重试次数，不含第一次尝试。0 表示失败后不重试。
 * @property initialDelay 第 1 次重试前的基础等待。
 * @property multiplier 每次重试把基础等待乘上的倍数，必须大于等于 1。
 * @property maxDelay 单次等待的上限，不能小于 [initialDelay]。
 * @property jitter 抖动比例，取值 0 到 1。0 表示不抖动。
 * @property retryOnNetworkGain 网络恢复时是否提前重试。
 * @property retryableCodes 允许套用本策略的错误码。不在集合中的错误应直接失败。
 * @throws IllegalArgumentException 次数为负、倍数小于 1、抖动超出 0 到 1，或延迟为负、上限小于起点。
 */
public data class RetryPolicy(
    public val maxAttempts: Int = Defaults.MAX_RETRY_ATTEMPTS,
    public val initialDelay: Duration = Defaults.RETRY_INITIAL_DELAY,
    public val multiplier: Double = Defaults.RETRY_MULTIPLIER,
    public val maxDelay: Duration = Defaults.RETRY_MAX_DELAY,
    public val jitter: Double = Defaults.RETRY_JITTER,
    public val retryOnNetworkGain: Boolean = Defaults.RETRY_ON_NETWORK_GAIN,
    public val retryableCodes: Set<ErrorCode> = Defaults.retryableCodes,
) {
    init {
        require(maxAttempts >= 0) { "maxAttempts 不能为负" }
        require(multiplier >= 1.0) { "multiplier 必须大于等于 1" }
        require(jitter in 0.0..1.0) { "jitter 必须在 0 到 1 之间" }
        require(initialDelay.inWholeMilliseconds >= 0L) { "initialDelay 不能为负" }
        require(maxDelay >= initialDelay) { "maxDelay 不能小于 initialDelay" }
    }

    /**
     * 计算第 [attempt] 次重试前要等待的毫秒数。
     *
     * [attempt] 从 1 开始。基础等待是 `initialDelay * multiplier^(attempt - 1)`，再限制到 [maxDelay]。
     * 抖动取自 [random]：`nextDouble()` 为 0.5 时乘子为 1，结果与不抖动相同。
     *
     * @param attempt 第几次重试，从 1 开始。
     * @param random 抖动的随机源。测试可以传入固定序列。
     * @return 等待毫秒数，不小于 0。
     * @throws IllegalArgumentException [attempt] 小于 1。
     */
    public fun delayFor(attempt: Int, random: Random = Random.Default): Long {
        require(attempt >= 1) { "attempt 从 1 开始" }
        val growth = multiplier.pow((attempt - 1).toDouble())
        val capped = min(
            initialDelay.inWholeMilliseconds.toDouble() * growth,
            maxDelay.inWholeMilliseconds.toDouble(),
        )
        val delta = (random.nextDouble() * 2.0 - 1.0) * jitter
        return (capped * (1.0 + delta)).toLong().coerceAtLeast(0L)
    }
}

/**
 * 下载完成后如何确认文件没有损坏。
 *
 * 校验发生在把临时文件改成最终文件之前。对不上时最终文件不会出现。
 */
public sealed interface IntegritySpec {
    /**
     * 用调用方提供的 SHA-256 校验完整文件。
     *
     * @property hex 64 位十六进制，大小写都可以。
     * @throws IllegalArgumentException [hex] 不是 64 位十六进制。
     */
    public data class Sha256(public val hex: String) : IntegritySpec {
        init {
            require(SHA256_HEX.matches(hex)) { "SHA-256 必须是 64 位十六进制" }
        }
    }

    /**
     * 用调用方提供的 MD5 校验完整文件。
     *
     * @property hex 32 位十六进制，大小写都可以。
     * @throws IllegalArgumentException [hex] 不是 32 位十六进制。
     */
    public data class Md5(public val hex: String) : IntegritySpec {
        init {
            require(MD5_HEX.matches(hex)) { "MD5 必须是 32 位十六进制" }
        }
    }

    /**
     * 用响应头 `Content-MD5` 校验完整文件。
     *
     * 摘要值来自响应，而不是调用方。
     */
    public data object ContentMd5 : IntegritySpec
}

/**
 * 把校验条件写成可以放进数据库的短文本。
 *
 * 空条件不要调用。结果只有 `sha256:`、`md5:` 和 `content-md5` 三种。
 *
 * @return 持久化用的文本。
 */
public fun IntegritySpec.encoded(): String {
    return when (this) {
        is IntegritySpec.Sha256 -> "sha256:$hex"
        is IntegritySpec.Md5 -> "md5:$hex"
        IntegritySpec.ContentMd5 -> "content-md5"
    }
}

/**
 * 从数据库里的短文本还原校验条件。
 *
 * 空白、无法识别或摘要长度不对时返回 null，避免一条坏记录让整个队列读不出来。
 *
 * @param encoded [encoded] 的结果，或空字符串。
 * @return 还原出的条件。不能用时为 null。
 */
public fun decodeIntegrity(encoded: String): IntegritySpec? {
    if (encoded.isEmpty()) return null
    return when {
        encoded == "content-md5" -> IntegritySpec.ContentMd5
        encoded.startsWith("sha256:") -> encoded.removePrefix("sha256:").toIntegrityOrNull(::sha256OrNull)
        encoded.startsWith("md5:") -> encoded.removePrefix("md5:").toIntegrityOrNull(::md5OrNull)
        else -> null
    }
}

private fun sha256OrNull(hex: String): IntegritySpec? {
    return if (SHA256_HEX.matches(hex)) IntegritySpec.Sha256(hex) else null
}

private fun md5OrNull(hex: String): IntegritySpec? {
    return if (MD5_HEX.matches(hex)) IntegritySpec.Md5(hex) else null
}

private fun String.toIntegrityOrNull(parse: (String) -> IntegritySpec?): IntegritySpec? = parse(this)

private val SHA256_HEX: Regex = Regex("[0-9a-fA-F]{64}")

/**
 * MD5 十六进制文本：正好 32 个 `0-9` 或 `a-f`，大小写不敏感。
 */
private val MD5_HEX: Regex = Regex("[0-9a-fA-F]{32}")
