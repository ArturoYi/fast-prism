/**
 * 决定一次失败是等待网络、退避后重试，还是直接失败。
 *
 * 链路内的立即重试不走这里。这里只处理整次任务失败之后的决定。
 *
 * @file
 */
package com.arturoyi.fastdownload.engine.retry

import com.arturoyi.fastdownload.core.error.DownloadError
import com.arturoyi.fastdownload.core.error.ErrorCode
import com.arturoyi.fastdownload.core.model.RetryPolicy
import kotlin.random.Random

/**
 * 任务级失败之后的下一步。
 */
public enum class TaskRetry {
    /** 错误不能重试，或次数已经用完。 */
    FAIL,

    /** 没有网络。不消耗重试次数，转到等待网络。 */
    WAIT_FOR_NETWORK,

    /** 等待 [TaskRetryPlan.delayMs] 后重新排队。 */
    BACKOFF,
}

/**
 * [planRetry] 的结果。
 *
 * @property kind 下一步。
 * @property delayMs 退避等待。不等待时为 0。
 * @property nextAttempts 决定之后应保存的重试次数。等待网络时不增加。
 */
public data class TaskRetryPlan(
    public val kind: TaskRetry,
    public val delayMs: Long,
    public val nextAttempts: Int,
)

/**
 * 按 [policy] 决定 [error] 之后怎么做。
 *
 * 没有网络且允许在网络恢复后续传时，不增加 [attemptsSoFar]。
 * 其他可重试错误在次数未用完时按指数退避等待。取消和校验失败直接失败。
 *
 * @param policy 这条任务的重试策略。
 * @param error 这一次失败。
 * @param attemptsSoFar 已经发生过的重试次数，不含这一次。
 * @param random 计算抖动的随机源。
 * @return 等待、重试或失败。
 */
public fun planRetry(
    policy: RetryPolicy,
    error: DownloadError,
    attemptsSoFar: Int,
    random: Random = Random.Default,
): TaskRetryPlan {
    if (error.code == ErrorCode.NO_NETWORK && policy.retryOnNetworkGain) {
        return TaskRetryPlan(TaskRetry.WAIT_FOR_NETWORK, delayMs = 0L, nextAttempts = attemptsSoFar)
    }
    val allowed = error.retryable &&
        error.code in policy.retryableCodes &&
        attemptsSoFar < policy.maxAttempts
    if (!allowed) {
        return TaskRetryPlan(TaskRetry.FAIL, delayMs = 0L, nextAttempts = attemptsSoFar)
    }
    val next = attemptsSoFar + 1
    return TaskRetryPlan(TaskRetry.BACKOFF, policy.delayFor(next, random), next)
}
