/**
 * 进程内的相册状态。
 *
 * 清理器在 [com.arturoyi.albumcleaner.sample.SampleApp] 里已经初始化。
 * 这里不主动扫描。用户点开始之后才收集 [com.arturoyi.albumcleaner.AlbumCleaner.scan]。
 * 扫过一次之后，下次打开只读索引里已经写下的结果。
 *
 * @file
 */
package com.arturoyi.albumcleaner.sample.model

import android.app.Activity
import android.content.Intent
import android.content.IntentSender
import android.graphics.Bitmap
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import com.arturoyi.albumcleaner.AlbumCleaner
import com.arturoyi.albumcleaner.CandidateQuery
import com.arturoyi.albumcleaner.CleanCandidate
import com.arturoyi.albumcleaner.CleanGroup
import com.arturoyi.albumcleaner.CleanerException
import com.arturoyi.albumcleaner.CleanerState
import com.arturoyi.albumcleaner.DeleteRequest
import com.arturoyi.albumcleaner.DeleteResult
import com.arturoyi.albumcleaner.ErrorCode
import com.arturoyi.albumcleaner.GroupQuery
import com.arturoyi.albumcleaner.PageQuery
import com.arturoyi.albumcleaner.PagedResult
import com.arturoyi.albumcleaner.RecycleEntry
import com.arturoyi.albumcleaner.ScanPhase
import com.arturoyi.albumcleaner.SpaceReport
import com.arturoyi.albumcleaner.sample.settings.AppSettings
import com.arturoyi.albumcleaner.sample.settings.ScanMilestone
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/** 一页最多多少条。索引允许的上限是 400。 */
private const val PAGE: Int = 40

/** 列表里缩略图的边长，单位 dp。 */
private const val THUMB_DP: Int = 64

/**
 * 持有候选、分组、回收站和扫描进度。三个页面都读这一份。
 *
 * @param cleaner 已经尝试过初始化的清理器。
 * @param settings 用来记住扫描里程。
 * @param opened 初始化成功时为 true。失败时查询和扫描都不会调用。
 */
internal class AlbumStore(
    private val cleaner: AlbumCleaner,
    private val settings: AppSettings,
    val opened: Boolean,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val jobs = Mutex()
    private val calls = Mutex()
    private val images = HashMap<Long, ImageBitmap>()
    private var scanJob: Job? = null
    private var candidateGeneration = 0
    private var groupGeneration = 0
    private var pendingRequest: DeleteRequest? = null
    private var pendingKind = PendingKind.None
    private var pendingSender: IntentSender? = null

    /** 用户有没有自己开始过扫描。 */
    var milestone by mutableStateOf(settings.scan)
        private set

    /** 清理器现在的状态。 */
    var cleanerState by mutableStateOf(cleaner.state().value)
        private set

    /** 这一轮扫描的阶段。没在扫时为 null。 */
    var phase by mutableStateOf<ScanPhase?>(null)
        private set

    /** 当前阶段已经处理的张数。 */
    var scanned by mutableIntStateOf(0)
        private set

    /** 当前阶段的总数。还不知道时为 null。 */
    var scanTotal by mutableStateOf<Int?>(null)
        private set

    /** 正在收集扫描进度。整理结果时也会保持为 true，避免按钮闪回。 */
    var scanning by mutableStateOf(false)
        private set

    /** 正在读索引。 */
    var refreshing by mutableStateOf(false)
        private set

    /** 正在翻下一页。 */
    var loadingMore by mutableStateOf(false)
        private set

    /** 最近一次空间估算。还没读过索引时为 null。 */
    var space by mutableStateOf<SpaceReport?>(null)
        private set

    /** 已经装进列表的候选。 */
    var candidates by mutableStateOf<List<CleanCandidate>>(emptyList())
        private set

    /** 候选总数。还没读过时为 null。 */
    var candidateTotal by mutableStateOf<Int?>(null)
        private set

    /** 下一页候选的游标。没有下一页时为 null。 */
    var candidateCursor by mutableStateOf<String?>(null)
        private set

    /** 已经装进列表的分组。 */
    var groups by mutableStateOf<List<CleanGroup>>(emptyList())
        private set

    /** 下一页分组的游标。没有下一页时为 null。 */
    var groupCursor by mutableStateOf<String?>(null)
        private set

    /** 回收站里已经装进列表的记录。 */
    var recycle by mutableStateOf<List<RecycleEntry>>(emptyList())
        private set

    /** 下一页回收站的游标。没有下一页时为 null。 */
    var recycleCursor by mutableStateOf<String?>(null)
        private set

    /** 候选缩略图，键是媒体库 id。 */
    var candidateImages by mutableStateOf<Map<Long, ImageBitmap>>(emptyMap())
        private set

    /** 分组里建议留下的那张缩略图，键是媒体库 id。 */
    var groupImages by mutableStateOf<Map<Long, ImageBitmap>>(emptyMap())
        private set

    /** 最近一次失败。没有时为 null。 */
    var failure by mutableStateOf<ErrorCode?>(null)
        private set

    /** [failure] 对不上文案时给界面的说明。 */
    var failureDetail by mutableStateOf<String?>(null)
        private set

    /**
     * 有系统确认框要打开时加一。
     *
     * 界面看到它变化，就取走 [takeSender]。
     */
    var launchTicket by mutableIntStateOf(0)
        private set

    init {
        if (!opened) {
            failure = ErrorCode.INDEX_CORRUPTED
        } else {
            scope.launch {
                cleaner.state().collect { cleanerState = it }
            }
            if (milestone != ScanMilestone.Never) {
                scope.launch { refreshAll() }
            }
        }
    }

    /**
     * 开始或继续扫描。
     *
     * 调用方要先确认已经有读取权限。这里不会在没有权限时弹系统框。
     */
    fun startScan() {
        if (!opened || refreshing || scanJob?.isActive == true) return
        if (cleaner.state().value != CleanerState.READY) return
        val flow = try {
            cleaner.scan()
        } catch (error: CleanerException) {
            failure = error.code
            failureDetail = error.message
            return
        }
        clearFailure()
        saveScan(ScanMilestone.Partial)
        phase = null
        scanned = 0
        scanTotal = null
        scanning = true
        scanJob = scope.launch {
            try {
                flow.collect { progress ->
                    phase = progress.phase
                    scanned = progress.scanned
                    scanTotal = progress.total
                    if (progress.phase == ScanPhase.DONE) saveScan(ScanMilestone.Done)
                }
            } catch (error: CancellationException) {
                throw error
            } catch (error: CleanerException) {
                failure = error.code
                failureDetail = error.message
            } finally {
                scanning = false
            }
            refreshAll()
        }
    }

    /** 停掉正在进行的扫描，并留下断点。 */
    fun cancelScan() {
        if (cleaner.state().value != CleanerState.SCANNING) return
        try {
            cleaner.cancelScan()
        } catch (error: CleanerException) {
            failure = error.code
            failureDetail = error.message
        }
    }

    /** 再读一页候选。 */
    fun loadMoreCandidates() {
        loadMore(candidateCursor, forGroups = false) { cursor ->
            if (candidateCursor != cursor) {
                emptyList()
            } else {
                val page = readPage { cleaner.candidates(CandidateQuery(cursor = cursor, limit = PAGE)) }
                val seen = candidates.map { item -> item.assetId }.toSet()
                candidates = candidates + page.items.filter { item -> item.assetId !in seen }
                candidateCursor = page.nextCursor
                candidateTotal = page.total ?: candidates.size
                candidates.map { item -> item.assetId }
            }
        }
    }

    /** 再读一页分组。 */
    fun loadMoreGroups() {
        loadMore(groupCursor, forGroups = true) { cursor ->
            if (groupCursor != cursor) {
                emptyList()
            } else {
                val page = readPage { cleaner.groups(GroupQuery(cursor = cursor, limit = PAGE)) }
                val seen = groups.map { item -> item.groupId }.toSet()
                groups = groups + page.items.filter { item -> item.groupId !in seen }
                groupCursor = page.nextCursor
                groups.map { item -> item.keepAssetId }
            }
        }
    }

    /** 再读一页回收站。 */
    fun loadMoreRecycle() {
        loadMore(recycleCursor, forGroups = false) { cursor ->
            if (recycleCursor != cursor) {
                emptyList()
            } else {
                val page = readPage { cleaner.recycleBin(PageQuery(cursor = cursor, limit = PAGE)) }
                recycle = recycle + page.items
                recycleCursor = page.nextCursor
                emptyList()
            }
        }
    }

    /**
     * 删除一张候选。
     *
     * 系统需要确认时，通过 [launchTicket] 把发送器交给界面。
     * 老版本没有系统框，这里直接提交。调用前界面已经问过用户。
     */
    fun delete(assetId: Long) {
        if (cleaner.state().value != CleanerState.READY) return
        val request = try {
            cleaner.requestDeleteIntent(listOf(assetId))
        } catch (error: CleanerException) {
            failure = error.code
            failureDetail = error.message
            return
        }
        beginRequest(request, PendingKind.Delete)
    }

    /** 恢复一次删除。系统确认框由界面打开。 */
    fun restore(actionId: String) {
        if (cleaner.state().value != CleanerState.READY) return
        val request = try {
            cleaner.requestRestoreIntent(actionId)
        } catch (error: CleanerException) {
            failure = error.code
            failureDetail = error.message
            return
        }
        beginRequest(request, PendingKind.Restore)
    }

    /**
     * 取走还没打开的系统发送器。
     *
     * 取走之后再旋转屏幕不会把它打开第二次。
     */
    fun takeSender(): IntentSender? {
        val sender = pendingSender
        pendingSender = null
        return sender
    }

    /** 系统确认框关掉之后，按结果改索引。 */
    fun finishSystemRequest(resultCode: Int, data: Intent?) {
        scope.launch { finishBody(resultCode, data) }
    }

    private fun beginRequest(request: DeleteRequest, kind: PendingKind) {
        clearFailure()
        pendingRequest = request
        pendingKind = kind
        val sender = request.intentSender
        if (sender == null) {
            scope.launch { finishBody(Activity.RESULT_OK, null) }
        } else {
            pendingSender = sender
            launchTicket += 1
        }
    }

    private suspend fun finishBody(resultCode: Int, data: Intent?) {
        val request = pendingRequest ?: return
        val kind = pendingKind
        pendingRequest = null
        pendingKind = PendingKind.None
        try {
            when (kind) {
                PendingKind.Delete -> commitDelete(DeleteResult(resultCode, data, request))
                PendingKind.Restore -> commitRestore(DeleteResult(resultCode, data, request))
                PendingKind.None -> return
            }
            refreshAll()
        } catch (error: CancellationException) {
            throw error
        } catch (error: CleanerException) {
            if (error.code != ErrorCode.DELETE_REJECTED_BY_USER) {
                failure = error.code
                failureDetail = error.message
            }
        }
    }

    private suspend fun commitDelete(result: DeleteResult) {
        withContext(Dispatchers.IO) {
            calls.withLock {
                cleaner.commitDeletion(result).collect { }
            }
        }
    }

    private suspend fun commitRestore(result: DeleteResult) {
        val outcome = withContext(Dispatchers.IO) {
            calls.withLock { cleaner.commitRestore(result) }
        }
        val error = outcome.exceptionOrNull() ?: return
        if (error is CleanerException) throw error
        throw CleanerException(ErrorCode.INVALID_STATE, error.message ?: "restore failed", error)
    }

    private suspend fun refreshAll() {
        if (!opened) return
        val thumbs = jobs.withLock {
            if (cleaner.state().value != CleanerState.READY) return@withLock null
            refreshing = true
            try {
                val snapshot = withContext(Dispatchers.IO) {
                    calls.withLock { readSnapshot() }
                }
                apply(snapshot)
                candidates.map { item -> item.assetId } to groups.map { item -> item.keepAssetId }
            } catch (error: CancellationException) {
                throw error
            } catch (error: CleanerException) {
                failure = error.code
                failureDetail = error.message
                null
            } catch (error: Exception) {
                failure = ErrorCode.INVALID_STATE
                failureDetail = error.message
                null
            } finally {
                refreshing = false
            }
        } ?: return
        loadImages(thumbs.first, forGroups = false)
        loadImages(thumbs.second, forGroups = true)
    }

    private fun apply(snapshot: Snapshot) {
        space = snapshot.space
        candidates = snapshot.candidates.items
        candidateCursor = snapshot.candidates.nextCursor
        candidateTotal = snapshot.candidates.total ?: snapshot.candidates.items.size
        groups = snapshot.groups.items
        groupCursor = snapshot.groups.nextCursor
        recycle = snapshot.recycle.items
        recycleCursor = snapshot.recycle.nextCursor
    }

    private fun loadMore(
        cursor: String?,
        forGroups: Boolean,
        block: suspend (String) -> List<Long>,
    ) {
        if (cursor == null || loadingMore || refreshing || scanning) return
        if (cleaner.state().value != CleanerState.READY) return
        loadingMore = true
        scope.launch {
            val ids = try {
                jobs.withLock {
                    if (cleaner.state().value != CleanerState.READY) return@withLock emptyList()
                    try {
                        block(cursor)
                    } catch (error: CancellationException) {
                        throw error
                    } catch (error: CleanerException) {
                        failure = error.code
                        failureDetail = error.message
                        emptyList()
                    }
                }
            } finally {
                loadingMore = false
            }
            if (ids.isNotEmpty()) loadImages(ids, forGroups)
        }
    }

    private fun loadImages(ids: List<Long>, forGroups: Boolean) {
        val unique = ids.distinct()
        val generation = if (forGroups) {
            groupGeneration += 1
            groupGeneration
        } else {
            candidateGeneration += 1
            candidateGeneration
        }
        if (unique.isEmpty()) {
            publish(forGroups, emptyMap(), generation)
            return
        }
        scope.launch {
            val loaded = LinkedHashMap<Long, ImageBitmap>()
            for (id in unique) {
                if (!isCurrent(forGroups, generation)) return@launch
                val cached = images[id]
                if (cached != null) {
                    loaded[id] = cached
                } else if (cleaner.state().value == CleanerState.READY) {
                    val bitmap = decode(id)
                    if (bitmap != null) {
                        val image = bitmap.asImageBitmap()
                        images[id] = image
                        loaded[id] = image
                    }
                }
                if (!isCurrent(forGroups, generation)) return@launch
                publish(forGroups, loaded.toMap(), generation)
            }
        }
    }

    private suspend fun decode(id: Long): Bitmap? {
        return try {
            withContext(Dispatchers.IO) {
                calls.withLock { cleaner.thumbnail(id, THUMB_DP) }
            }
        } catch (error: CancellationException) {
            throw error
        } catch (_: CleanerException) {
            null
        }
    }

    private fun isCurrent(forGroups: Boolean, generation: Int): Boolean {
        return generation == if (forGroups) groupGeneration else candidateGeneration
    }

    private fun publish(forGroups: Boolean, value: Map<Long, ImageBitmap>, generation: Int) {
        if (!isCurrent(forGroups, generation)) return
        if (forGroups) groupImages = value else candidateImages = value
    }

    private fun readSnapshot(): Snapshot {
        return Snapshot(
            space = cleaner.spaceReport(),
            candidates = cleaner.candidates(CandidateQuery(limit = PAGE)),
            groups = cleaner.groups(GroupQuery(limit = PAGE)),
            recycle = cleaner.recycleBin(PageQuery(limit = PAGE)),
        )
    }

    private suspend fun <T> readPage(block: () -> PagedResult<T>): PagedResult<T> {
        return withContext(Dispatchers.IO) {
            calls.withLock { block() }
        }
    }

    private fun saveScan(value: ScanMilestone) {
        milestone = value
        settings.saveScan(value)
    }

    private fun clearFailure() {
        failure = null
        failureDetail = null
    }

    private enum class PendingKind {
        None,
        Delete,
        Restore,
    }

    private data class Snapshot(
        val space: SpaceReport,
        val candidates: PagedResult<CleanCandidate>,
        val groups: PagedResult<CleanGroup>,
        val recycle: PagedResult<RecycleEntry>,
    )
}
