/**
 * 元数据扫描：按游标把媒体库写入索引，并留下断点。
 *
 * @file
 */
package com.arturoyi.albumcleaner.scan

import android.content.ContentResolver
import android.os.CancellationSignal
import android.os.OperationCanceledException
import com.arturoyi.albumcleaner.storage.AlbumIndex
import com.arturoyi.albumcleaner.storage.ScanSessionRecord
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.yield
import kotlin.coroutines.coroutineContext

/**
 * 元数据阶段的进度。
 *
 * @property scanned 这一阶段已经处理的条数，包含断点之前的。
 * @property total 媒体库总数。还没数出来时不会出现在这个类型里，查询失败会直接抛出。
 * @property phase 读到哪一段。
 */
public data class MetadataProgress(
    public val scanned: Int,
    public val total: Int,
    public val phase: MetadataPhase,
)

/** 元数据扫描的阶段。不算指纹。 */
public enum class MetadataPhase {
    /** 正在读媒体库。 */
    QUERY,

    /** 这一批已经写入索引。 */
    INDEX,

    /** 这一轮元数据结束。 */
    DONE,
}

/** 调用方取消了扫描。入口把它收成任务取消。 */
public class ScanCancelledException : RuntimeException("扫描已取消")

/**
 * 从断点继续把图片和视频的元数据写入索引。
 *
 * 不解码，不算哈希。调用方负责把收集放在后台线程上。
 * 上一轮状态是 `RUNNING` 时从游标继续，游标之前的行不会再写。
 * 上一轮已经 `DONE` 时从头再读。
 *
 * @param catalog 媒体库。
 * @param index 已经打开的索引。
 * @param now 当前时间，Unix 毫秒。
 * @param pacer 每 30 条或连续计算满 8 毫秒让出一次。
 */
internal class MetadataScanner(
    private val catalog: MediaCatalog,
    private val index: AlbumIndex,
    private val now: () -> Long = { System.currentTimeMillis() },
    private val pacer: YieldPacer = YieldPacer(),
) {
    /**
     * 冷流。收集才开始读。
     *
     * @param pageSize 每批条数，必须大于 0。
     * @param signal 取消后不再读下一页。
     * @param closeSession 为 false 时不把会话标成完成，留给随后的指纹阶段。
     */
    fun progress(
        pageSize: Int,
        signal: CancellationSignal,
        closeSession: Boolean = true,
    ): Flow<MetadataProgress> = flow {
        require(pageSize > 0) { "pageSize 必须大于 0" }
        var cursor = resumeCursor()
        save(cursor, MetadataPhase.QUERY.name, SESSION_RUNNING)
        pacer.open()
        val total = readCatalog(signal) { catalog.total(signal) }
        var scanned = readCatalog(signal) { catalog.countThrough(cursor, signal) }
        emit(MetadataProgress(scanned, total, MetadataPhase.QUERY))
        while (true) {
            val page = readCatalog(signal) { catalog.pageAfter(cursor, pageSize, signal) }
            if (page.isEmpty()) break
            val ordered = page.sortedBy { row -> row.assetId }.distinctBy { row -> row.assetId }
            val indexedAt = now()
            index.upsertAssets(ordered.map { row -> row.toAsset(indexedAt) })
            cursor = ordered.last().assetId
            scanned += ordered.size
            save(cursor, MetadataPhase.INDEX.name, SESSION_RUNNING)
            emit(MetadataProgress(scanned, total, MetadataPhase.INDEX))
            pacer.after(ordered.size)
        }
        if (closeSession) {
            save(cursor, MetadataPhase.DONE.name, SESSION_DONE)
            emit(MetadataProgress(scanned, total, MetadataPhase.DONE))
        } else {
            save(cursor, PHASE_FINGERPRINT, SESSION_RUNNING)
        }
    }

    private fun resumeCursor(): Long {
        val existing = index.session(METADATA_SESSION_ID) ?: return CURSOR_START
        if (existing.status != SESSION_RUNNING) return CURSOR_START
        return existing.cursorId ?: CURSOR_START
    }

    private fun save(cursor: Long, phase: String, status: String) {
        index.saveSession(
            ScanSessionRecord(
                sessionId = METADATA_SESSION_ID,
                cursorId = cursor.takeIf { it >= 0L },
                phase = phase,
                status = status,
                updatedAt = now(),
            ),
        )
    }

    private suspend fun <T> readCatalog(signal: CancellationSignal, block: () -> T): T {
        throwIfStopped(signal)
        return try {
            block()
        } catch (cancelled: OperationCanceledException) {
            coroutineContext.ensureActive()
            throw ScanCancelledException()
        }
    }

    private suspend fun throwIfStopped(signal: CancellationSignal) {
        coroutineContext.ensureActive()
        if (signal.isCanceled) throw ScanCancelledException()
    }
}

/**
 * 每处理 [itemBudget] 条，或这一段连续计算超过 [timeBudgetNs]，就让出线程。
 *
 * @param itemBudget 条数上限。
 * @param timeBudgetNs 时间上限，纳秒。
 * @param nanoTime 单调时钟。
 * @param pause 让出。默认 [yield]。
 */
internal class YieldPacer(
    private val itemBudget: Int = ITEM_BUDGET,
    private val timeBudgetNs: Long = TIME_BUDGET_NS,
    private val nanoTime: () -> Long = { System.nanoTime() },
    private val pause: suspend () -> Unit = { yield() },
) {
    private var items: Int = 0
    private var sliceStartNs: Long = UNOPENED

    /** 开始计这一段的时间。查询要算在这一段里，所以要在查询之前调用。 */
    fun open() {
        if (sliceStartNs == UNOPENED) {
            sliceStartNs = nanoTime()
        }
    }

    /**
     * 这一批已经处理完。
     *
     * @param count 这一批的条数。
     */
    suspend fun after(count: Int) {
        open()
        items += count
        val elapsed = nanoTime() - sliceStartNs
        if (items >= itemBudget || elapsed >= timeBudgetNs) {
            pause()
            items = 0
            sliceStartNs = nanoTime()
        }
    }

    private companion object {
        const val ITEM_BUDGET: Int = 30
        const val TIME_BUDGET_NS: Long = 8_000_000L
        const val UNOPENED: Long = Long.MIN_VALUE
    }
}

/**
 * 读取媒体库元数据并写入索引。
 *
 * 调用方要把收集放在后台线程上。取消 [signal] 后不再读下一页。
 *
 * @param resolver 应用的 [ContentResolver]。
 * @param index 已经打开的索引。
 * @param pageSize 每批条数，必须大于 0。
 * @param signal 查询用的取消信号。
 * @param closeSession 为 false 时会话保持进行中，方便接着算指纹。
 * @return 元数据阶段的进度。没有 [MetadataPhase.DONE]，除非 [closeSession] 为 true。
 */
public fun scanMetadata(
    resolver: ContentResolver,
    index: AlbumIndex,
    pageSize: Int,
    signal: CancellationSignal,
    closeSession: Boolean = true,
): Flow<MetadataProgress> {
    return MetadataScanner(MediaStoreCatalog(resolver), index).progress(pageSize, signal, closeSession)
}

/** 元数据这一轮在索引里的会话标识。 */
internal const val METADATA_SESSION_ID: String = "metadata"

/** 还没扫完，下次从游标继续。 */
internal const val SESSION_RUNNING: String = "RUNNING"

/** 这一轮扫描已经结束。 */
internal const val SESSION_DONE: String = "DONE"

private const val CURSOR_START: Long = -1L
private const val PHASE_FINGERPRINT: String = "FINGERPRINT"
