/**
 * 合并窗口里的多次偏移合成一次提交。
 *
 * @file
 */
package com.arturoyi.fastdowload.engine.store

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

/**
 * [RemitSyncExecutor] 按分块累加，并在强制提交时一次写出。
 */
class RemitSyncExecutorTest {
    /**
     * 同一分块的三次记录在强制提交前不写存储，提交时只有一个合计值。
     */
    @Test
    fun recordsCoalesceUntilForceFlush() {
        val commits = mutableListOf<Long>()
        val remit = RemitSyncExecutor(delayMs = 60_000) { _, _, delta -> commits += delta }
        try {
            remit.record("task", 0, 10)
            remit.record("task", 0, 15)
            remit.record("task", 1, 7)
            assertEquals(emptyList<Long>(), commits.toList())
            remit.forceFlush()
            assertEquals(listOf(7L, 25L), commits.sorted())
        } finally {
            remit.close()
        }
    }
}
