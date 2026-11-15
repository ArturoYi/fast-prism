/**
 * 令牌桶把实测速度限制在目标值附近。
 *
 * @file
 */
package com.arturoyi.fastdowload.engine.limit

import com.arturoyi.fastdowload.core.model.Defaults
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import kotlin.math.abs

/**
 * 场景 17：每秒 1MB 时，实测速率偏差小于 15%。
 */
class RateLimiterTest {
    /**
     * 按读缓冲的大小连续领取 1MB。全局上限是每秒 1MB。
     */
    @Test
    fun oneMegabytePerSecondStaysWithinFifteenPercent() {
        val bytes = 1_000_000
        val limit = 1_000_000L
        val limiter = GlobalRateLimiter(limit).forTask(null)
        val started = System.nanoTime()
        var left = bytes
        while (left > 0) {
            val take = minOf(Defaults.BUFFER_SIZE, left)
            limiter.acquire(take)
            left -= take
        }
        val elapsedSeconds = (System.nanoTime() - started) / 1_000_000_000.0
        val rate = bytes / elapsedSeconds
        val deviation = abs(rate - limit) / limit
        assertTrue(deviation < 0.15) { "实测 $rate 字节/秒，偏差 $deviation" }
    }
}
