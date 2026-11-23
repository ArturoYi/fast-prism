/**
 * 状态机：未初始化、就绪、重复初始化、释放，以及还没实现的能力。
 *
 * @file
 */
package com.arturoyi.albumcleaner

import com.arturoyi.albumcleaner.storage.IndexCorruptedException
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

/**
 * [AlbumCleanerEngine] 只迁移状态，不假装扫描或删除已经完成。
 */
class AlbumCleanerEngineTest {
    /**
     * 还没初始化时，能力调用失败，状态停在未初始化。
     */
    @Test
    fun capabilitiesFailBeforeInitialize() {
        val engine = AlbumCleanerEngine()
        assertEquals(CleanerState.UNINITIALIZED, engine.state().value)
        guarded(engine).forEach { call ->
            val error = assertThrows(CleanerException::class.java) { exercise(call) }
            assertEquals(ErrorCode.INVALID_STATE, error.code)
        }
        assertEquals(CleanerState.UNINITIALIZED, engine.state().value)
    }

    /**
     * 初始化后进入就绪。页大小或并发不是正数时，状态不变。
     */
    @Test
    fun initializeMovesToReady() {
        val engine = AlbumCleanerEngine()
        assertThrows(IllegalArgumentException::class.java) {
            engine.initialize(CleanerConfig(pageSize = 0))
        }
        assertThrows(IllegalArgumentException::class.java) {
            engine.initialize(CleanerConfig(maxConcurrency = 0))
        }
        assertEquals(CleanerState.UNINITIALIZED, engine.state().value)

        engine.initialize()
        assertEquals(CleanerState.READY, engine.state().value)
    }

    /**
     * 就绪之后不能再初始化。
     */
    @Test
    fun secondInitializeIsRejected() {
        val engine = AlbumCleanerEngine()
        engine.initialize()
        val error = assertThrows(CleanerException::class.java) {
            engine.initialize()
        }
        assertEquals(ErrorCode.INVALID_STATE, error.code)
        assertEquals(CleanerState.READY, engine.state().value)
    }

    /**
     * 就绪后，还没实现的能力只说明未实现，不进入扫描或删除。
     */
    @Test
    fun unimplementedCapabilitiesStayReady() {
        val engine = AlbumCleanerEngine()
        engine.initialize()
        unimplemented(engine).forEach { call ->
            assertThrows(UnsupportedOperationException::class.java) { exercise(call) }
        }
        val idle = assertThrows(CleanerException::class.java) { engine.cancelScan() }
        assertEquals(ErrorCode.INVALID_STATE, idle.code)
        assertEquals(CleanerState.READY, engine.state().value)
    }

    /**
     * 释放是终态。之后再初始化或调用能力，都报已经释放。
     */
    @Test
    fun callsAfterReleaseStayReleased() {
        val engine = AlbumCleanerEngine()
        engine.initialize()
        engine.release()
        assertEquals(CleanerState.RELEASED, engine.state().value)

        engine.release()
        assertEquals(CleanerState.RELEASED, engine.state().value)

        val initialize = assertThrows(CleanerException::class.java) {
            engine.initialize()
        }
        assertEquals(ErrorCode.SDK_RELEASED, initialize.code)
        guarded(engine).forEach { call ->
            val error = assertThrows(CleanerException::class.java) { exercise(call) }
            assertEquals(ErrorCode.SDK_RELEASED, error.code)
        }
        assertEquals(CleanerState.RELEASED, engine.state().value)
    }

    /**
     * 索引损坏时保持未初始化，并把异常转成 [ErrorCode.INDEX_CORRUPTED]。
     */
    @Test
    fun corruptedIndexStaysUninitialized() {
        val engine = AlbumCleanerEngine(openIndex = { throw IndexCorruptedException("索引损坏") })
        val error = assertThrows(CleanerException::class.java) {
            engine.initialize()
        }
        assertEquals(ErrorCode.INDEX_CORRUPTED, error.code)
        assertEquals(CleanerState.UNINITIALIZED, engine.state().value)
    }

    /**
     * 还没初始化也可以释放，释放后同样不能再初始化。
     */
    @Test
    fun releaseBeforeInitializeIsTerminal() {
        val engine = AlbumCleanerEngine()
        engine.release()
        val error = assertThrows(CleanerException::class.java) {
            engine.initialize()
        }
        assertEquals(ErrorCode.SDK_RELEASED, error.code)
        assertEquals(CleanerState.RELEASED, engine.state().value)
    }
}

private fun exercise(call: () -> Any?) {
    call()
}

private fun guarded(engine: AlbumCleanerEngine): List<() -> Any?> {
    return listOf(
        { engine.scan() },
        { engine.cancelScan() },
        { engine.candidates(CandidateQuery()) },
        { engine.groups(GroupQuery()) },
        { runBlocking { engine.thumbnail(1L) } },
        { engine.recycleBin(PageQuery()) },
        { engine.spaceReport() },
    ) + unimplemented(engine)
}

private fun unimplemented(engine: AlbumCleanerEngine): List<() -> Any?> {
    val request = DeleteRequest(
        intentSender = null,
        assetIds = listOf(1L),
        requiresExplicitWarning = true,
    )
    val result = DeleteResult(resultCode = 0, data = null, request = request)
    return listOf(
        { engine.observeChanges() },
        { engine.requestDeleteIntent(listOf(1L)) },
        { engine.commitDeletion(result) },
        { engine.requestRestoreIntent("action") },
        { engine.commitRestore(result) },
    )
}
