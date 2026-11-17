/**
 * 状态白名单和启动清洗。
 *
 * @file
 */
package com.arturoyi.fastdownload.core.state

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.MethodSource

/**
 * [StateMachine] 与状态表一致，非法迁移会抛错。
 */
class StateMachineTest {
    /**
     * 每一对状态都按白名单判断。
     */
    @ParameterizedTest
    @MethodSource("pairs")
    fun matchesWhitelist(from: Status, to: Status, legal: Boolean) {
        assertEquals(legal, StateMachine.isLegal(from, to))
    }

    /**
     * 已登记的任务不能直接变成正在下载。
     */
    @Test
    fun illegalTransitionThrows() {
        val error = assertThrows(IllegalStateException::class.java) {
            StateMachine.requireTransition(Status.ADDED, Status.DOWNLOADING)
        }
        assertTrue(error.message!!.contains("ADDED"))
        assertTrue(error.message!!.contains("DOWNLOADING"))
    }

    companion object {
        @JvmStatic
        fun pairs(): List<Array<Any>> {
            val legal = setOf(
                Status.ADDED to Status.QUEUED,
                Status.ADDED to Status.REMOVED,
                Status.ADDED to Status.DELETED,
                Status.QUEUED to Status.QUEUED,
                Status.QUEUED to Status.WAITING_NETWORK,
                Status.QUEUED to Status.DOWNLOADING,
                Status.QUEUED to Status.PAUSED,
                Status.QUEUED to Status.CANCELLED,
                Status.QUEUED to Status.REMOVED,
                Status.QUEUED to Status.DELETED,
                Status.WAITING_NETWORK to Status.QUEUED,
                Status.WAITING_NETWORK to Status.WAITING_NETWORK,
                Status.WAITING_NETWORK to Status.DOWNLOADING,
                Status.WAITING_NETWORK to Status.PAUSED,
                Status.WAITING_NETWORK to Status.CANCELLED,
                Status.WAITING_NETWORK to Status.REMOVED,
                Status.WAITING_NETWORK to Status.DELETED,
                Status.DOWNLOADING to Status.QUEUED,
                Status.DOWNLOADING to Status.WAITING_NETWORK,
                Status.DOWNLOADING to Status.PAUSED,
                Status.DOWNLOADING to Status.COMPLETED,
                Status.DOWNLOADING to Status.FAILED,
                Status.DOWNLOADING to Status.CANCELLED,
                Status.DOWNLOADING to Status.REMOVED,
                Status.DOWNLOADING to Status.DELETED,
                Status.PAUSED to Status.QUEUED,
                Status.PAUSED to Status.WAITING_NETWORK,
                Status.PAUSED to Status.CANCELLED,
                Status.PAUSED to Status.REMOVED,
                Status.PAUSED to Status.DELETED,
                Status.FAILED to Status.QUEUED,
                Status.FAILED to Status.WAITING_NETWORK,
                Status.FAILED to Status.CANCELLED,
                Status.FAILED to Status.REMOVED,
                Status.FAILED to Status.DELETED,
                Status.CANCELLED to Status.QUEUED,
                Status.CANCELLED to Status.REMOVED,
                Status.CANCELLED to Status.DELETED,
                Status.COMPLETED to Status.REMOVED,
                Status.COMPLETED to Status.DELETED,
            )
            return Status.entries.flatMap { from ->
                Status.entries.map { to ->
                    arrayOf(from, to, (from to to) in legal)
                }
            }
        }
    }
}

/**
 * [SanitizePolicy] 不把“字节数已经够了”当成完成。
 */
class SanitizePolicyTest {
    /**
     * 脏的下载中任务回到队列，并清掉断点。
     */
    @Test
    fun dirtyDownloadIsQueuedAndCleared() {
        val decision = SanitizePolicy.decide(
            status = Status.DOWNLOADING,
            dirty = true,
            downloadedBytes = 10,
            targetExists = true,
            partExists = true,
        )
        assertEquals(Status.QUEUED, decision.status)
        assertTrue(decision.clearBlocks)
        assertTrue(decision.zeroProgress)
        assertTrue(decision.deletePart)
    }

    /**
     * 没有脏标记的下载中任务只回到队列，断点保留。
     */
    @Test
    fun cleanDownloadKeepsBlocks() {
        val decision = SanitizePolicy.decide(
            status = Status.DOWNLOADING,
            dirty = false,
            downloadedBytes = 10,
            targetExists = false,
            partExists = true,
        )
        assertEquals(Status.QUEUED, decision.status)
        assertEquals(false, decision.clearBlocks)
    }

    /**
     * 已写字节等于总长度时，仍然不会在这里改成完成。
     */
    @Test
    fun byteCountDoesNotImplyCompleted() {
        val decision = SanitizePolicy.decide(
            status = Status.DOWNLOADING,
            dirty = false,
            downloadedBytes = 100,
            targetExists = true,
            partExists = false,
        )
        assertEquals(Status.QUEUED, decision.status)
        assertEquals(false, decision.clearBlocks)
    }

    /**
     * 文件丢失且记过进度时，进度归零。完成态回到队列。
     */
    @Test
    fun missingFileZerosProgress() {
        val decision = SanitizePolicy.decide(
            status = Status.COMPLETED,
            dirty = false,
            downloadedBytes = 20,
            targetExists = false,
            partExists = false,
        )
        assertEquals(Status.QUEUED, decision.status)
        assertTrue(decision.zeroProgress)
        assertTrue(decision.clearBlocks)
    }
}
