/**
 * [AlbumCleaner] 的实现。元数据扫描已接上，其余能力还没有实现。
 *
 * @file
 */
package com.arturoyi.albumcleaner

import android.content.Context
import android.graphics.Bitmap
import android.os.CancellationSignal
import android.os.Process
import com.arturoyi.albumcleaner.scan.DedupProgress
import com.arturoyi.albumcleaner.scan.DedupSettings
import com.arturoyi.albumcleaner.scan.MetadataPhase
import com.arturoyi.albumcleaner.scan.MetadataProgress
import com.arturoyi.albumcleaner.scan.ScanCancelledException
import com.arturoyi.albumcleaner.scan.dedupIndexed
import com.arturoyi.albumcleaner.scan.fingerprintIndexed
import com.arturoyi.albumcleaner.scan.scanMetadata
import com.arturoyi.albumcleaner.storage.AlbumIndex
import com.arturoyi.albumcleaner.storage.IndexCorruptedException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.job
import java.io.Closeable
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

/**
 * 把一次元数据扫描跑成进度流。
 *
 * 取消时抛出 [ScanCancelledException]。
 */
internal fun interface MetadataScanRunner {
    /**
 * @param index [initialize] 打开的索引。
 * @param config 分页和分组阈值。
 * @param gate 取消开关。查询要在它取消后停下来。
 */
fun run(index: Closeable, config: CleanerConfig, gate: ScanGate): Flow<ScanProgress>
}

/**
 * 状态机、索引和元数据扫描。
 *
 * 扫描工作在 [scanDispatcher] 上执行，不在调用线程上查询媒体库或写索引。
 *
 * @param openIndex 打开一份索引。测试可以换成不碰数据库的实现。
 * @param scans 元数据扫描。测试可以换成脚本。
 * @param checkPermission 读取权限。没有权限时抛出 [CleanerException]。
 * @param scanDispatcher 扫描用的调度器。生产环境是一条后台优先级的线程。
 * @param closeDispatcher 释放时关掉 [scanDispatcher] 持有的线程。
 * @param thumbnails 展示用缩略图。没有时 [thumbnail] 返回 null。
 * @param deletes 系统删除。没有时删除相关方法仍未实现。
 * @param sdkInt 用来决定删除走不走系统确认框。
 * @param density 把 dp 换成像素。
 */
internal class AlbumCleanerEngine(
    private val openIndex: () -> Closeable = { Closeable { } },
    private val scans: MetadataScanRunner = MetadataScanRunner { _, _, _ ->
        throw UnsupportedOperationException("这个能力还没有实现")
    },
    private val checkPermission: () -> Unit = {},
    private val scanDispatcher: CoroutineDispatcher = Dispatchers.Unconfined,
    private val closeDispatcher: () -> Unit = {},
    private val thumbnails: ThumbnailSource? = null,
    private val deletes: DeleteGateway? = null,
    private val sdkInt: Int = 28,
    private val density: Float = 1f,
) : AlbumCleaner {
    private val machine = CleanerStateMachine()
    private var index: Closeable? = null
    private var config: CleanerConfig? = null
    private var deletion: Deletion? = null

    @Volatile
    private var scanGate: ScanGate? = null

    @Volatile
    private var closing: Boolean = false

    override fun initialize(config: CleanerConfig) {
        require(config.pageSize > 0) { "pageSize 必须大于 0" }
        require(config.maxConcurrency > 0) { "maxConcurrency 必须大于 0" }
        when (val current = machine.state.value) {
            CleanerState.RELEASED -> throw CleanerException(ErrorCode.SDK_RELEASED, "已经 release()")
            CleanerState.UNINITIALIZED -> Unit
            else -> throw CleanerException(ErrorCode.INVALID_STATE, "当前是 $current，不能再 initialize()")
        }
        val opened = try {
            openIndex()
        } catch (error: IndexCorruptedException) {
            throw CleanerException(ErrorCode.INDEX_CORRUPTED, error.message ?: "索引损坏", error)
        }
        try {
            machine.initialize()
        } catch (error: Throwable) {
            opened.close()
            throw error
        }
        index?.close()
        index = opened
        deletion = null
        this.config = config
    }

    override fun scan(): Flow<ScanProgress> {
        machine.requireReady()
        checkPermission()
        val currentIndex = index ?: throw CleanerException(ErrorCode.INDEX_CORRUPTED, "索引没有打开")
        val currentConfig = config ?: throw CleanerException(ErrorCode.INVALID_STATE, "先 initialize()")
        return flow {
            val gate = ScanGate()
            scanGate = gate
            var started = false
            try {
                machine.beginScan()
                started = true
                currentCoroutineContext().job.invokeOnCompletion { gate.cancel() }
                scans.run(currentIndex, currentConfig, gate).collect { progress -> emit(progress) }
            } catch (cancelled: ScanCancelledException) {
                if (closing || machine.state.value == CleanerState.RELEASED) {
                    throw CancellationException("已经 release()")
                }
                throw CleanerException(ErrorCode.TASK_CANCELLED, "扫描已取消", cancelled)
            } catch (security: SecurityException) {
                throw CleanerException(ErrorCode.PERMISSION_DENIED, "没有读取相册的权限", security)
            } catch (closed: IllegalStateException) {
                if (closing || machine.state.value == CleanerState.RELEASED) {
                    throw CancellationException("已经 release()")
                }
                throw closed
            } finally {
                scanGate = null
                if (started) machine.finishScan()
            }
        }.flowOn(scanDispatcher)
    }

    override fun cancelScan() {
        machine.requireScanning()
        scanGate?.cancel()
    }

    override fun observeChanges(): Flow<ChangeEvent> = unavailable()

    override fun candidates(query: CandidateQuery): PagedResult<CleanCandidate> {
        machine.requireReady()
        return readCandidates(database(), query)
    }

    override fun groups(query: GroupQuery): PagedResult<CleanGroup> {
        machine.requireReady()
        return readGroups(database(), query)
    }

    override suspend fun thumbnail(assetId: Long, sizeDp: Int): Bitmap? {
        machine.requireReady()
        require(sizeDp > 0) { "sizeDp 必须大于 0" }
        val asset = database().asset(assetId) ?: return null
        val source = thumbnails ?: return null
        val pixels = (sizeDp * density).toInt().coerceIn(1, MAX_THUMB_PX)
        return source.load(asset.uri, pixels)
    }

    override fun requestDeleteIntent(assetIds: List<Long>): DeleteRequest {
        machine.requireReady()
        return deletion().request(assetIds)
    }

    override fun commitDeletion(result: DeleteResult): Flow<ApplyProgress> {
        machine.requireReady()
        val worker = deletion()
        return flow {
            machine.beginApply()
            try {
                emit(worker.commit(result))
            } finally {
                machine.finishApply()
            }
        }
    }

    override fun requestRestoreIntent(actionId: String): DeleteRequest {
        machine.requireReady()
        return deletion().restoreRequest(actionId)
    }

    override fun commitRestore(result: DeleteResult): Result<Unit> {
        machine.requireReady()
        return try {
            deletion().commitRestore(result)
            Result.success(Unit)
        } catch (error: CleanerException) {
            Result.failure(error)
        }
    }

    override fun recycleBin(query: PageQuery): PagedResult<RecycleEntry> {
        machine.requireReady()
        return readRecycle(database(), query)
    }

    override fun spaceReport(): SpaceReport {
        machine.requireReady()
        return readSpace(database())
    }

    override fun state(): StateFlow<CleanerState> = machine.state

    override fun release() {
        closing = true
        scanGate?.cancel()
        deletion = null
        val opened = index
        index = null
        machine.release()
        opened?.close()
        closeDispatcher()
    }

    private fun unavailable(): Nothing {
        machine.requireReady()
        throw UnsupportedOperationException("这个能力还没有实现")
    }

    private fun database(): AlbumIndex {
        return index as? AlbumIndex ?: throw CleanerException(ErrorCode.INDEX_CORRUPTED, "索引没有打开")
    }

    private fun deletion(): Deletion {
        val gateway = deletes ?: throw UnsupportedOperationException("这个能力还没有实现")
        deletion?.let { return it }
        val created = Deletion(
            index = database(),
            sdkInt = sdkInt,
            gateway = gateway,
        )
        deletion = created
        return created
    }

    private companion object {
        const val MAX_THUMB_PX: Int = 1024
    }
}

/**
 * 带应用上下文的入口。初始化时打开索引。扫描先写元数据，再算指纹。
 *
 * @param context 任意上下文，内部会换成应用上下文。
 * @param executor 扫描线程。释放时关掉。
 */
internal class AlbumCleanerImpl private constructor(
    context: Context,
    executor: ExecutorService,
) : AlbumCleaner by AlbumCleanerEngine(
    openIndex = { AlbumIndex.open(context.applicationContext) },
    scans = metadataScan(context.applicationContext),
    checkPermission = { ensureAlbumReadPermission(context.applicationContext) },
    scanDispatcher = executor.asCoroutineDispatcher(),
    closeDispatcher = { executor.shutdownNow() },
    thumbnails = MediaStoreThumbnails(context.applicationContext),
    deletes = MediaStoreDeletes(context.applicationContext),
    sdkInt = android.os.Build.VERSION.SDK_INT,
    density = context.applicationContext.resources.displayMetrics.density,
) {
    init {
        val appContext = context.applicationContext
        check(appContext.applicationContext === appContext)
    }

    internal companion object {
        /**
         * 创建一份还没初始化的清理器。
         *
         * @param context 任意上下文。
         */
        fun create(context: Context): AlbumCleanerImpl {
            return AlbumCleanerImpl(context, metadataScanExecutor())
        }
    }
}

/**
 * 一条后台优先级的扫描线程。
 *
 * 线程是守护线程，忘记 [AlbumCleaner.release] 时也不会卡住进程退出。
 */
internal fun metadataScanExecutor(): ExecutorService {
    return Executors.newSingleThreadExecutor { task ->
        object : Thread(task, "album-cleaner-scan") {
            init {
                isDaemon = true
            }

            override fun run() {
                try {
                    Process.setThreadPriority(Process.THREAD_PRIORITY_BACKGROUND)
                } catch (_: Exception) {
                    // 纯 JVM 上没有进程优先级。
                }
                super.run()
            }
        }
    }
}

private fun metadataScan(context: Context): MetadataScanRunner {
    val resolver = context.applicationContext.contentResolver
    return MetadataScanRunner { index, config, gate ->
        flow {
            val signal = CancellationSignal()
            gate.onCancel { signal.cancel() }
            val album = index as? AlbumIndex ?: throw IllegalStateException("索引没有打开")
            scanMetadata(resolver, album, config.pageSize, signal, closeSession = false).collect { progress ->
                emit(progress.toScanProgress())
            }
            fingerprintIndexed(resolver, album, signal, closeSession = false).collect { progress ->
                emit(ScanProgress(progress.scanned, progress.total, ScanPhase.FINGERPRINT))
            }
            val settings = DedupSettings(
                phashHammingMax = config.thresholds.phashHammingMax,
                burstWindowMs = config.thresholds.burstWindowMs,
                sizeTolerancePercent = config.thresholds.sizeTolerancePercent,
                maxGroupSize = config.thresholds.maxGroupSize,
            )
            val scoring = com.arturoyi.albumcleaner.scan.ScoreSettings(
                protectFavorites = config.protection.protectFavorites,
                favoriteProtectDays = config.thresholds.favoriteProtectDays,
                protectRecentDays = config.protection.protectRecentDays,
                excludedBucketIds = config.protection.excludedBucketIds,
                protectedExtensions = config.protection.protectedExtensions,
                protectLargestInGroup = config.protection.protectLargestInGroup,
                blurVarianceMin = config.thresholds.blurVarianceMin,
            )
            var last = DedupProgress(scanned = 0, total = 0)
            dedupIndexed(album, settings, signal, scoring).collect { progress ->
                last = progress
                emit(ScanProgress(progress.scanned, progress.total, ScanPhase.DEDUP))
            }
            val candidateCount = album.countCandidates(null, null)
            emit(ScanProgress(candidateCount, candidateCount, ScanPhase.SCORE))
            emit(ScanProgress(last.scanned, last.total, ScanPhase.DONE))
        }
    }
}

private fun MetadataProgress.toScanProgress(): ScanProgress {
    val mapped = when (phase) {
        MetadataPhase.QUERY -> ScanPhase.QUERY
        MetadataPhase.INDEX -> ScanPhase.INDEX
        MetadataPhase.DONE -> ScanPhase.DONE
    }
    return ScanProgress(scanned = scanned, total = total, phase = mapped)
}
