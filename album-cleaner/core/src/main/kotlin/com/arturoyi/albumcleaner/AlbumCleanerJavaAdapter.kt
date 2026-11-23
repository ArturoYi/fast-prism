/**
 * 给 Java 用的回调适配。状态和异常仍由 [AlbumCleaner] 决定。
 *
 * @file
 */
package com.arturoyi.albumcleaner

import android.graphics.Bitmap
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch
import java.io.Closeable
import java.util.function.Consumer

/**
 * 把 [Flow] 和挂起函数收成回调。
 *
 * 进度、缩略图和状态回调发生在主线程。同步方法直接转给 [cleaner]，异常也在调用线程抛出。
 * 用完调用 [close]，未完成的收集会取消。[close] 不会调用 [AlbumCleaner.release]。
 *
 * @param cleaner 同一份 Kotlin 入口。
 */
public class AlbumCleanerJavaAdapter(
    private val cleaner: AlbumCleaner,
) : Closeable {
    private val job = SupervisorJob()
    private val scope by lazy { CoroutineScope(job + Dispatchers.Main.immediate) }

    /**
     * 用默认配置初始化。
     *
     * @throws CleanerException 状态不对。
     */
    public fun initialize() {
        initialize(CleanerConfig())
    }

    /**
     * 初始化。
     *
     * @param config 阈值、保护和分页。
     * @throws CleanerException 状态不对。
     */
    public fun initialize(config: CleanerConfig) {
        cleaner.initialize(config)
    }

    /**
     * 收集扫描进度。
     *
     * @param onProgress 每一段进度。
     * @param onError 状态不对、没有读取权限，或扫描被取消。
     * @param onComplete 进度发完。
     */
    public fun scan(
        onProgress: Consumer<ScanProgress>,
        onError: Consumer<Throwable>,
        onComplete: Runnable,
    ) {
        collect({ cleaner.scan() }, onProgress, onError, onComplete)
    }

    /**
     * 取消扫描。
     *
     * @throws CleanerException 状态不对。
     */
    public fun cancelScan() {
        cleaner.cancelScan()
    }

    /**
     * 收集媒体库变化。
     *
     * @param onEvent 一条变化。
     * @param onError 状态不对或未实现。
     * @param onComplete 收集正常结束。
     */
    public fun observeChanges(
        onEvent: Consumer<ChangeEvent>,
        onError: Consumer<Throwable>,
        onComplete: Runnable,
    ) {
        collect({ cleaner.observeChanges() }, onEvent, onError, onComplete)
    }

    /**
     * 读取一页候选。
     *
     * @param query 过滤和游标。
     * @return 这一页。
     * @throws CleanerException 状态不对。
     */
    public fun candidates(query: CandidateQuery): PagedResult<CleanCandidate> {
        return cleaner.candidates(query)
    }

    /**
     * 读取一页分组。
     *
     * @param query 过滤和游标。
     * @return 这一页。
     * @throws CleanerException 状态不对。
     */
    public fun groups(query: GroupQuery): PagedResult<CleanGroup> {
        return cleaner.groups(query)
    }

    /**
     * 用 256dp 读取缩略图。
     *
     * @param assetId 媒体库 `_id`。
     * @param onResult 位图。照片不在时收到 null。
     * @param onError 状态不对或未实现。
     */
    public fun thumbnail(
        assetId: Long,
        onResult: Consumer<Bitmap?>,
        onError: Consumer<Throwable>,
    ) {
        thumbnail(assetId, DEFAULT_THUMBNAIL_DP, onResult, onError)
    }

    /**
     * 读取缩略图。
     *
     * @param assetId 媒体库 `_id`。
     * @param sizeDp 期望边长，单位 dp。
     * @param onResult 位图。照片不在时收到 null。
     * @param onError 状态不对或未实现。
     */
    public fun thumbnail(
        assetId: Long,
        sizeDp: Int,
        onResult: Consumer<Bitmap?>,
        onError: Consumer<Throwable>,
    ) {
        launch(onError) {
            onResult.accept(cleaner.thumbnail(assetId, sizeDp))
        }
    }

    /**
     * 准备系统删除请求。
     *
     * @param assetIds 要删除的照片。
     * @return 给宿主启动的请求。
     * @throws CleanerException 状态不对。
     */
    public fun requestDeleteIntent(assetIds: List<Long>): DeleteRequest {
        return cleaner.requestDeleteIntent(assetIds)
    }

    /**
     * 收集删除提交进度。
     *
     * @param result 系统确认框的结果。
     * @param onProgress 一批里的成功和失败张数。
     * @param onError 状态不对、用户取消，或未实现。
     * @param onComplete 这一批结束。
     */
    public fun commitDeletion(
        result: DeleteResult,
        onProgress: Consumer<ApplyProgress>,
        onError: Consumer<Throwable>,
        onComplete: Runnable,
    ) {
        collect({ cleaner.commitDeletion(result) }, onProgress, onError, onComplete)
    }

    /**
     * 准备系统恢复请求。
     *
     * @param actionId 删除动作。
     * @return 给宿主启动的请求。
     * @throws CleanerException 状态不对，或不能恢复。
     */
    public fun requestRestoreIntent(actionId: String): DeleteRequest {
        return cleaner.requestRestoreIntent(actionId)
    }

    /**
     * 提交恢复结果。失败时抛出 [Result] 里的异常。
     *
     * @param result 系统确认框的结果。
     * @throws CleanerException 状态不对或不能恢复。
     */
    public fun commitRestore(result: DeleteResult) {
        cleaner.commitRestore(result).getOrThrow()
    }

    /**
     * 读取一页回收站。
     *
     * @param query 游标和页大小。
     * @return 这一页。
     * @throws CleanerException 状态不对。
     */
    public fun recycleBin(query: PageQuery): PagedResult<RecycleEntry> {
        return cleaner.recycleBin(query)
    }

    /**
     * 读取空间估算。
     *
     * @return 预计可释放和已经释放的字节数。
     * @throws CleanerException 状态不对。
     */
    public fun spaceReport(): SpaceReport {
        return cleaner.spaceReport()
    }

    /**
     * 当前状态。
     *
     * @return 调用这一刻的状态。
     */
    public fun currentState(): CleanerState {
        return cleaner.state().value
    }

    /**
     * 观察后续状态。马上会收到当前值。
     *
     * 返回的 [Closeable] 只取消这次观察，不关闭整个适配器。
     *
     * @param onState 新状态。
     * @return 取消这次观察。
     */
    public fun observeState(onState: Consumer<CleanerState>): Closeable {
        ensureOpen()
        val child = scope.launch {
            cleaner.state().collect { state -> onState.accept(state) }
        }
        return Closeable { child.cancel() }
    }

    /** 释放底层清理器。 */
    public fun release() {
        cleaner.release()
    }

    /** 取消还没结束的回调。不释放清理器。 */
    public override fun close() {
        job.cancel()
    }

    private fun <T> collect(
        source: () -> Flow<T>,
        onEach: Consumer<T>,
        onError: Consumer<Throwable>,
        onComplete: Runnable,
    ) {
        launch(onError) {
            source().collect { value -> onEach.accept(value) }
            onComplete.run()
        }
    }

    private fun launch(onError: Consumer<Throwable>, block: suspend () -> Unit) {
        ensureOpen()
        scope.launch {
            try {
                block()
            } catch (error: CancellationException) {
                throw error
            } catch (error: Throwable) {
                onError.accept(error)
            }
        }
    }

    private fun ensureOpen() {
        if (!job.isActive) {
            throw IllegalStateException("适配器已经关闭")
        }
    }

    private companion object {
        const val DEFAULT_THUMBNAIL_DP: Int = 256
    }
}
