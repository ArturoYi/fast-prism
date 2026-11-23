/**
 * 元数据写完之后计算指纹。不分组，也不选保留哪一张。
 *
 * @file
 */
package com.arturoyi.albumcleaner.scan

import android.content.ContentResolver
import android.os.CancellationSignal
import android.os.OperationCanceledException
import com.arturoyi.albumcleaner.storage.AlbumIndex
import com.arturoyi.albumcleaner.storage.AssetRecord
import com.arturoyi.albumcleaner.storage.FeatureRecord
import com.arturoyi.albumcleaner.storage.ScanSessionRecord
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.FlowCollector
import kotlinx.coroutines.flow.flow
import kotlin.coroutines.coroutineContext

/**
 * 指纹阶段的进度。
 *
 * @property scanned 已经算完的条数。跳过的不算。
 * @property total 需要计算的条数。
 */
public data class FingerprintProgress(
    public val scanned: Int,
    public val total: Int,
)

/**
 * 先给同大小文件算头尾哈希，再给图片解一张 64px。
 *
 * 视频不解码。已经算过且大小、修改时间都没变的行会跳过。
 * 计算在调用方的协程里进行，扫描入口会把它放在后台线程。
 *
 * @param index 已经打开的索引。
 * @param files 读头尾和全文。
 * @param thumbnails 64px 灰度图。
 * @param now 写入特征的时间，Unix 毫秒。
 * @param pacer 每 30 条或连续计算满 8 毫秒让出一次。
 * @param video 怎样判断一条记录是视频。
 */
internal class FingerprintPass(
    private val index: AlbumIndex,
    private val files: FileBytes,
    private val thumbnails: ThumbnailDecoder,
    private val now: () -> Long = { System.currentTimeMillis() },
    private val pacer: YieldPacer = YieldPacer(),
    private val video: (String) -> Boolean = ::isVideoUri,
) {
    /**
     * 冷流。正常结束时把扫描会话标成完成。
     *
     * @param signal 取消后不再读下一张，已经写入的特征保留。
     */
    fun progress(signal: CancellationSignal, closeSession: Boolean = true): Flow<FingerprintProgress> = flow {
        try {
            run(signal, closeSession)
        } catch (cancelled: OperationCanceledException) {
            coroutineContext.ensureActive()
            throw ScanCancelledException()
        }
    }

    private suspend fun FlowCollector<FingerprintProgress>.run(signal: CancellationSignal, closeSession: Boolean) {
        val plan = plan(signal)
        emit(FingerprintProgress(scanned = 0, total = plan.total))
        if (plan.total == 0) {
            markSession(closeSession)
            return
        }
        pacer.open()
        val partials = HashMap<Long, String>(plan.staleDuplicates.size)
        for (asset in plan.staleDuplicates) {
            throwIfStopped(signal)
            val hash = files.partial(asset.uri, asset.sizeBytes, signal)
            if (hash != null) partials[asset.assetId] = hash
            pacer.after(1)
        }
        val byteHashes = resolveByteHashes(partials) { assetId ->
            val asset = plan.duplicateById.getValue(assetId)
            files.full(asset.uri, signal)
        }
        val pending = ArrayList<FeatureRecord>(WRITE_BATCH)
        var scanned = 0
        try {
            forEachAsset(signal) { asset ->
                if (asset.assetId in plan.staleIds) {
                    val gray = if (video(asset.uri)) {
                        null
                    } else {
                        decode(asset.uri, signal)
                    }
                    pending += feature(asset, partials[asset.assetId], byteHashes[asset.assetId], gray)
                    scanned += 1
                    if (pending.size >= WRITE_BATCH) flush(pending)
                    if (scanned % PROGRESS_BATCH == 0) {
                        emit(FingerprintProgress(scanned, plan.total))
                    }
                    pacer.after(1)
                }
            }
        } finally {
            flush(pending)
        }
        if (scanned % PROGRESS_BATCH != 0) {
            emit(FingerprintProgress(scanned, plan.total))
        }
        markSession(closeSession)
    }

    private suspend fun plan(signal: CancellationSignal): FingerprintPlan {
        val counts = HashMap<Long, Int>()
        val staleIds = HashSet<Long>()
        forEachAsset(signal) { asset ->
            counts[asset.sizeBytes] = (counts[asset.sizeBytes] ?: 0) + 1
            if (!canReuseFeature(index.feature(asset.assetId), asset.sizeBytes, asset.dateModified)) {
                staleIds += asset.assetId
            }
        }
        val duplicateSizes = counts.filterValues { count -> count >= 2 }.keys
        val staleDuplicates = ArrayList<AssetRecord>()
        if (duplicateSizes.isNotEmpty() && staleIds.isNotEmpty()) {
            forEachAsset(signal) { asset ->
                if (asset.assetId in staleIds && asset.sizeBytes in duplicateSizes) {
                    staleDuplicates += asset
                }
            }
        }
        return FingerprintPlan(
            total = staleIds.size,
            staleIds = staleIds,
            staleDuplicates = staleDuplicates,
            duplicateById = staleDuplicates.associateBy { asset -> asset.assetId },
        )
    }

    private fun feature(
        asset: AssetRecord,
        sample: String?,
        byteHash: String?,
        gray: GrayImage?,
    ): FeatureRecord {
        return FeatureRecord(
            assetId = asset.assetId,
            byteHash = byteHash,
            phash = gray?.let { image -> pHash(image).toHashText() },
            dhash = gray?.let { image -> dHash(image).toHashText() },
            blurScore = gray?.let { image -> blurVariance(image) },
            contentFingerprint = contentFingerprint(asset.sizeBytes, asset.dateModified, sample),
            algorithmVersion = FINGERPRINT_ALGORITHM_VERSION,
            computedAt = now(),
        )
    }

    private fun decode(uri: String, signal: CancellationSignal): GrayImage? {
        return try {
            thumbnails.gray64(uri, signal)
        } catch (cancelled: OperationCanceledException) {
            throw cancelled
        } catch (security: SecurityException) {
            throw security
        } catch (_: Exception) {
            null
        }
    }

    private fun flush(pending: MutableList<FeatureRecord>) {
        if (pending.isEmpty()) return
        index.upsertFeatures(pending.toList())
        pending.clear()
    }

    private fun markSession(closeSession: Boolean) {
        val current = index.session(METADATA_SESSION_ID)
        index.saveSession(
            ScanSessionRecord(
                sessionId = METADATA_SESSION_ID,
                cursorId = current?.cursorId,
                phase = if (closeSession) MetadataPhase.DONE.name else "DEDUP",
                status = if (closeSession) SESSION_DONE else SESSION_RUNNING,
                updatedAt = now(),
            ),
        )
    }

    private suspend fun forEachAsset(signal: CancellationSignal, block: suspend (AssetRecord) -> Unit) {
        var cursor = CURSOR_START
        while (true) {
            throwIfStopped(signal)
            val page = index.assetsAfter(cursor, INDEX_PAGE)
            if (page.isEmpty()) break
            page.forEach { asset -> block(asset) }
            cursor = page.last().assetId
        }
    }

    private suspend fun throwIfStopped(signal: CancellationSignal) {
        coroutineContext.ensureActive()
        if (signal.isCanceled) throw ScanCancelledException()
    }

    private companion object {
        const val INDEX_PAGE: Int = 400
        const val WRITE_BATCH: Int = 50
        const val PROGRESS_BATCH: Int = 50
        const val CURSOR_START: Long = -1L
    }
}

private class FingerprintPlan(
    val total: Int,
    val staleIds: Set<Long>,
    val staleDuplicates: List<AssetRecord>,
    val duplicateById: Map<Long, AssetRecord>,
)

/** 媒体库视频地址。图片才会解码。 */
internal fun isVideoUri(uri: String): Boolean = uri.contains("/video/")

/**
 * 给索引里的照片和视频计算指纹。
 *
 * 调用方要把收集放在后台线程上。取消 [signal] 后不再解码下一张。
 *
 * @param resolver 应用的 [ContentResolver]。
 * @param index 已经打开的索引。
 * @param signal 查询和解码用的取消信号。
 * @param closeSession 为 false 时会话保持进行中，方便接着分组。
 */
public fun fingerprintIndexed(
    resolver: ContentResolver,
    index: AlbumIndex,
    signal: CancellationSignal,
    closeSession: Boolean = true,
): Flow<FingerprintProgress> {
    return FingerprintPass(
        index = index,
        files = ContentFileBytes(resolver),
        thumbnails = MediaStoreThumbnailDecoder(resolver),
    ).progress(signal, closeSession)
}
