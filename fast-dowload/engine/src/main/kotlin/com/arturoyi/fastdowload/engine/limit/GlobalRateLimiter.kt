/**
 * 全局和单任务共用的令牌桶。
 *
 * 两个桶一起扣减，只等待较慢的那一个，避免把同一段字节的等待算两次。
 *
 * @file
 */
package com.arturoyi.fastdowload.engine.limit

import com.arturoyi.fastdowload.core.model.Defaults
import com.arturoyi.fastdowload.core.spi.RateLimiter
import java.util.concurrent.locks.LockSupport
import kotlin.math.ceil
import kotlin.math.min

/**
 * 进程内的速度上限。
 *
 * 全局上限对所有 [forTask] 返回的限制器生效，可以在下载过程中修改。
 * 单任务上限只约束这一条任务。两边都没有上限时 [acquire] 立即返回。
 *
 * 突发额度是 [burstBytes]，默认一个读缓冲。单次等待不超过 200 毫秒，方便取消及时生效。
 *
 * @param bytesPerSecond 初始全局上限。null 表示不限。
 * @param burstBytes 桶里最多囤积的字节数，必须大于 0。
 * @param clockNanos 单调时钟，单位纳秒。
 * @param sleepNanos 等待额度的动作，参数是纳秒。测试可以改成记录这段时间。
 */
public class GlobalRateLimiter(
    bytesPerSecond: Long? = null,
    private val burstBytes: Long = Defaults.BUFFER_SIZE.toLong(),
    private val clockNanos: () -> Long = System::nanoTime,
    private val sleepNanos: (Long) -> Unit = ::parkQuietly,
) {
    private val lock = Any()
    private val global = Bucket(limitOf(bytesPerSecond), tokensOf(bytesPerSecond), clockNanos())

    init {
        require(burstBytes > 0L) { "burstBytes 必须大于 0" }
        if (bytesPerSecond != null) require(bytesPerSecond > 0L) { "速度必须大于 0" }
    }

    /**
     * 修改全局上限。
     *
     * 已经在等待的下载会在下一次领取时看到新值。null 表示不再限制全局速度。
     *
     * @param bytesPerSecond 每秒字节数。
     */
    public fun setLimit(bytesPerSecond: Long?) {
        if (bytesPerSecond != null) require(bytesPerSecond > 0L) { "速度必须大于 0" }
        synchronized(lock) {
            global.limit = limitOf(bytesPerSecond)
            global.tokens = if (bytesPerSecond == null) {
                Double.POSITIVE_INFINITY
            } else {
                min(global.tokens, burstBytes.toDouble())
            }
        }
    }

    /**
     * 为一条任务创建限制器。
     *
     * 返回的对象和全局桶共用同一把锁。[bytesPerSecond] 为 null 时只受全局上限约束。
     *
     * @param bytesPerSecond 这条任务的上限。null 表示任务自己不限。
     * @return 写文件前要调用的限制器。
     */
    public fun forTask(bytesPerSecond: Long?): RateLimiter {
        if (bytesPerSecond != null) require(bytesPerSecond > 0L) { "速度必须大于 0" }
        return TaskLimiter(bytesPerSecond)
    }

    private inner class TaskLimiter(
        taskLimit: Long?,
    ) : RateLimiter {
        private val task = Bucket(limitOf(taskLimit), tokensOf(taskLimit), clockNanos())

        override fun acquire(bytes: Int) {
            if (bytes <= 0) return
            var left = bytes
            while (left > 0) {
                if (Thread.currentThread().isInterrupted) return
                val waitNanos = synchronized(lock) {
                    val now = clockNanos()
                    global.refill(now, burstBytes.toDouble())
                    task.refill(now, burstBytes.toDouble())
                    if (global.limit.isInfinite() && task.limit.isInfinite()) return
                    val available = min(global.tokens, task.tokens)
                    if (available >= 1.0) {
                        val take = min(left.toDouble(), available).toInt().coerceAtLeast(1)
                        global.deduct(take)
                        task.deduct(take)
                        left -= take
                        0L
                    } else {
                        val need = min(left.toDouble(), burstBytes.toDouble())
                        maxOf(global.waitNanos(need), task.waitNanos(need)).coerceAtMost(MAX_SLEEP_NANOS)
                    }
                }
                if (waitNanos > 0L) sleepNanos(waitNanos)
            }
        }
    }

    private class Bucket(
        var limit: Double,
        var tokens: Double,
        var updatedNanos: Long,
    ) {
        fun refill(now: Long, burst: Double) {
            if (limit.isInfinite()) {
                tokens = Double.POSITIVE_INFINITY
                updatedNanos = now
                return
            }
            val elapsed = (now - updatedNanos).coerceAtLeast(0L)
            tokens = min(burst, tokens + limit * elapsed / NANOS_PER_SECOND)
            updatedNanos = now
        }

        fun deduct(amount: Int) {
            if (limit.isInfinite()) return
            tokens -= amount
        }

        fun waitNanos(need: Double): Long {
            if (limit.isInfinite()) return 0L
            val missing = need - tokens
            if (missing <= 0.0) return 0L
            return ceil(missing / limit * NANOS_PER_SECOND).toLong().coerceAtLeast(1L)
        }
    }

    private companion object {
        private const val NANOS_PER_SECOND: Double = 1_000_000_000.0
        private const val MAX_SLEEP_NANOS: Long = 200_000_000L

        private fun limitOf(bytesPerSecond: Long?): Double {
            return bytesPerSecond?.toDouble() ?: Double.POSITIVE_INFINITY
        }

        private fun tokensOf(bytesPerSecond: Long?): Double {
            return if (bytesPerSecond == null) Double.POSITIVE_INFINITY else 0.0
        }

        private fun parkQuietly(nanos: Long) {
            if (nanos <= 0L) return
            LockSupport.parkNanos(nanos)
        }
    }
}
