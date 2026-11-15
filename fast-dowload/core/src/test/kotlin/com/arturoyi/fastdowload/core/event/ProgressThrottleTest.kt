/**
 * 进度通知同时看时间和字节。
 *
 * @file
 */
package com.arturoyi.fastdowload.core.event

import com.arturoyi.fastdowload.core.model.Defaults
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * [ProgressThrottle] 不把大量小进度都放出去，并且默认值来自 [Defaults]。
 */
class ProgressThrottleTest {
    /**
     * 同一时刻的连续小增长只在跨过字节阈值时放行，顺序不倒退。
     */
    @Test
    fun byteThresholdKeepsOrderWithoutFlooding() {
        var now = 0L
        val throttle = ProgressThrottle(intervalMs = 300, minBytes = 100) { now }
        val emitted = mutableListOf<Long>()
        repeat(1_000) { index ->
            val bytes = index.toLong()
            if (throttle.shouldEmit(bytes)) emitted += bytes
        }
        assertEquals(listOf(0L, 100L, 200L, 300L, 400L, 500L, 600L, 700L, 800L, 900L), emitted)
        now = 300
        assertTrue(throttle.shouldEmit(901))
        assertTrue(throttle.shouldEmit(901, force = true))
        assertFalse(throttle.shouldEmit(902))
    }

    /**
     * 默认间隔和字节阈值就是 [Defaults] 里的那一份。
     */
    @Test
    fun defaultsMatchTheSingleSource() {
        assertEquals(300L, Defaults.PROGRESS_INTERVAL.inWholeMilliseconds)
        assertEquals(65_536L, Defaults.PROGRESS_MIN_BYTES)
    }
}
