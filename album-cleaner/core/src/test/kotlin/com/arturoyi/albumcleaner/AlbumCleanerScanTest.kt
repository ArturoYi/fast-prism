/**
 * 扫描进度、取消和权限失败。不打开真实媒体库。
 *
 * @file
 */
package com.arturoyi.albumcleaner

import com.arturoyi.albumcleaner.scan.ScanCancelledException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.supervisorScope
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.coroutines.resume

/**
 * [AlbumCleanerEngine] 收集扫描时的状态。
 */
class AlbumCleanerScanTest {
    /**
     * 收集期间是扫描中，发完进度回到就绪。
     */
    @Test
    fun scanEmitsProgressAndReturnsToReady() = runBlocking {
        lateinit var engine: AlbumCleanerEngine
        engine = AlbumCleanerEngine(
            scans = MetadataScanRunner { _, _, _ ->
                flow {
                    assertEquals(CleanerState.SCANNING, engine.state().value)
                    emit(ScanProgress(1, 2, ScanPhase.QUERY))
                    emit(ScanProgress(2, 2, ScanPhase.DONE))
                }
            },
            scanDispatcher = Dispatchers.Unconfined,
        )
        engine.initialize()
        val phases = mutableListOf<ScanPhase>()
        engine.scan().collect { phases += it.phase }
        assertEquals(listOf(ScanPhase.QUERY, ScanPhase.DONE), phases)
        assertEquals(CleanerState.READY, engine.state().value)
    }

    /**
     * 取消后进度流带着取消错误结束，状态回到就绪。
     */
    @Test
    fun cancelScanFailsTheCollector() = runBlocking {
        val started = CompletableDeferred<Unit>()
        val engine = AlbumCleanerEngine(
            scans = MetadataScanRunner { _, _, gate ->
                flow {
                    emit(ScanProgress(1, 2, ScanPhase.QUERY))
                    started.complete(Unit)
                    suspendCancellableCoroutine { cont ->
                        gate.onCancel {
                            if (cont.isActive) cont.resume(Unit)
                        }
                    }
                    throw ScanCancelledException()
                }
            },
            scanDispatcher = Dispatchers.Unconfined,
        )
        engine.initialize()
        supervisorScope {
            val job = launch {
                val error = runCatching { engine.scan().collect { } }.exceptionOrNull()
                assertTrue(error is CleanerException)
                assertEquals(ErrorCode.TASK_CANCELLED, (error as CleanerException).code)
            }
            withTimeout(2_000) { started.await() }
            assertEquals(CleanerState.SCANNING, engine.state().value)
            val rejected = assertThrows(CleanerException::class.java) { engine.scan() }
            assertEquals(ErrorCode.INVALID_STATE, rejected.code)
            engine.cancelScan()
            job.join()
        }
        assertEquals(CleanerState.READY, engine.state().value)
    }

    /**
     * 释放会停掉扫描，收集方看到的是取消，不是任务失败。
     */
    @Test
    fun releaseCancelsTheRunningScan() = runBlocking {
        val started = CompletableDeferred<Unit>()
        val engine = AlbumCleanerEngine(
            scans = MetadataScanRunner { _, _, gate ->
                flow {
                    emit(ScanProgress(1, 1, ScanPhase.QUERY))
                    started.complete(Unit)
                    suspendCancellableCoroutine { cont ->
                        gate.onCancel {
                            if (cont.isActive) cont.resume(Unit)
                        }
                    }
                    throw ScanCancelledException()
                }
            },
            scanDispatcher = Dispatchers.Unconfined,
        )
        engine.initialize()
        supervisorScope {
            val job = launch {
                val error = runCatching { engine.scan().collect { } }.exceptionOrNull()
                assertTrue(error is CancellationException)
            }
            withTimeout(2_000) { started.await() }
            engine.release()
            job.join()
        }
        assertEquals(CleanerState.RELEASED, engine.state().value)
    }

    /**
     * 没有读取权限时不进入扫描中。
     */
    @Test
    fun missingPermissionStaysReady() {
        val engine = AlbumCleanerEngine(
            checkPermission = {
                throw CleanerException(ErrorCode.PERMISSION_DENIED, "没有读取相册的权限")
            },
        )
        engine.initialize()
        val error = assertThrows(CleanerException::class.java) { engine.scan() }
        assertEquals(ErrorCode.PERMISSION_DENIED, error.code)
        assertEquals(CleanerState.READY, engine.state().value)
    }
}

/**
 * [ScanGate] 在取消前和取消后都会通知监听。
 */
class ScanGateTest {
    /**
     * 先登记再取消，回调只走一次。
     */
    @Test
    fun cancelNotifiesTheListener() {
        val gate = ScanGate()
        var calls = 0
        gate.onCancel { calls += 1 }
        gate.cancel()
        gate.cancel()
        assertEquals(1, calls)
        assertTrue(gate.isCancelled())
    }

    /**
     * 已经取消后再登记，马上调用。
     */
    @Test
    fun lateListenerRunsImmediately() {
        val gate = ScanGate()
        gate.cancel()
        var calls = 0
        gate.onCancel { calls += 1 }
        assertEquals(1, calls)
    }
}

/**
 * 读取权限按系统版本分支。
 */
class AlbumReadPermissionTest {
    /**
     * Android 12 及以下看外部存储权限。
     */
    @Test
    fun legacySdkUsesStoragePermission() {
        ensureAlbumReadPermission(32) { it == android.Manifest.permission.READ_EXTERNAL_STORAGE }
        val error = assertThrows(CleanerException::class.java) {
            ensureAlbumReadPermission(32) { false }
        }
        assertEquals(ErrorCode.PERMISSION_DENIED, error.code)
    }

    /**
     * Android 13 看图片权限。部分授权从 14 才算数。
     */
    @Test
    fun android13UsesImagePermission() {
        ensureAlbumReadPermission(33) { it == android.Manifest.permission.READ_MEDIA_IMAGES }
        val error = assertThrows(CleanerException::class.java) {
            ensureAlbumReadPermission(33) { it == android.Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED }
        }
        assertEquals(ErrorCode.PERMISSION_DENIED, error.code)
    }

    /**
     * Android 14 完整授权或部分授权都可以开始扫描。
     */
    @Test
    fun android14AllowsPartialAccess() {
        ensureAlbumReadPermission(34) { it == android.Manifest.permission.READ_MEDIA_IMAGES }
        ensureAlbumReadPermission(34) { it == android.Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED }
        val error = assertThrows(CleanerException::class.java) {
            ensureAlbumReadPermission(34) { false }
        }
        assertEquals(ErrorCode.PERMISSION_DENIED, error.code)
    }
}
