/**
 * 把分组写入索引。一次替换，取消发生在写入之前时旧分组还在。
 *
 * @file
 */
package com.arturoyi.albumcleaner.scan

import android.os.CancellationSignal
import com.arturoyi.albumcleaner.storage.AlbumIndex
import com.arturoyi.albumcleaner.storage.AssetRecord
import com.arturoyi.albumcleaner.storage.CandidateRecord
import com.arturoyi.albumcleaner.storage.FeatureRecord
import com.arturoyi.albumcleaner.storage.GroupMemberRecord
import com.arturoyi.albumcleaner.storage.GroupRecord
import com.arturoyi.albumcleaner.storage.ScanSessionRecord
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlin.coroutines.coroutineContext

/**
 * 分组阶段的进度。
 *
 * @property scanned 已经看过的条数。
 * @property total 索引里的条数。
 */
public data class DedupProgress(
    public val scanned: Int,
    public val total: Int,
)

/**
 * 读取索引并替换全部分组。不选出保留的照片。
 *
 * @param index 已经打开的索引。
 * @param settings 汉明距离、连拍时间窗、尺寸档和组大小。
 * @param scoring 保护和打分。
 * @param now 建组时间，Unix 毫秒。
 */
internal class DedupPass(
    private val index: AlbumIndex,
    private val settings: DedupSettings,
    private val scoring: ScoreSettings = ScoreSettings(),
    private val now: () -> Long = { System.currentTimeMillis() },
) {
    /**
     * 冷流。正常结束时把扫描会话标成完成。
     *
     * @param signal 取消后不替换分组。
     */
    fun progress(signal: CancellationSignal): Flow<DedupProgress> = flow {
        val loaded = load(signal)
        throwIfStopped(signal)
        emit(DedupProgress(scanned = 0, total = loaded.items.size))
        val drafts = deduplicate(loaded.items, settings)
        throwIfStopped(signal)
        val createdAt = now()
        val groups = mutableListOf<GroupRecord>()
        val members = mutableListOf<GroupMemberRecord>()
        val candidates = mutableListOf<CandidateRecord>()
        drafts.forEach { draft ->
            val scoreMembers = draft.memberIds.mapNotNull { assetId ->
                val asset = loaded.assets[assetId] ?: return@mapNotNull null
                asset.toScoreMember(loaded.features[assetId])
            }
            if (scoreMembers.isEmpty()) return@forEach
            val score = scoreGroup(scoreMembers, draft.type, scoring, createdAt)
            groups += GroupRecord(
                groupId = draft.groupId,
                type = draft.type,
                keepAssetId = score.keepAssetId,
                createdAt = createdAt,
            )
            draft.memberIds.forEach { assetId ->
                members += GroupMemberRecord(
                    groupId = draft.groupId,
                    assetId = assetId,
                    score = null,
                    reasonCode = draft.type,
                )
            }
            score.candidates.forEach { candidate ->
                candidates += CandidateRecord(
                    assetId = candidate.assetId,
                    groupId = draft.groupId,
                    confidence = candidate.confidence,
                    reasonsJson = reasonJson(candidate.reasons),
                    estimatedBytes = candidate.estimatedBytes,
                    computedAt = createdAt,
                )
            }
        }
        index.replaceAllGroups(groups, members)
        index.replaceCandidates(candidates)
        emit(DedupProgress(loaded.items.size, loaded.items.size))
        val current = index.session(METADATA_SESSION_ID)
        index.saveSession(
            ScanSessionRecord(
                sessionId = METADATA_SESSION_ID,
                cursorId = current?.cursorId,
                phase = MetadataPhase.DONE.name,
                status = SESSION_DONE,
                updatedAt = createdAt,
            ),
        )
    }

    private suspend fun load(signal: CancellationSignal): LoadedLibrary {
        val items = mutableListOf<DedupItem>()
        val assets = LinkedHashMap<Long, AssetRecord>()
        val features = LinkedHashMap<Long, FeatureRecord>()
        var cursor = -1L
        while (true) {
            throwIfStopped(signal)
            val page = index.assetsAfter(cursor, PAGE)
            if (page.isEmpty()) break
            page.forEach { asset ->
                val feature = index.feature(asset.assetId)
                assets[asset.assetId] = asset
                if (feature != null) features[asset.assetId] = feature
                items += asset.toDedupItem(feature)
            }
            cursor = page.last().assetId
        }
        return LoadedLibrary(items, assets, features)
    }

    private suspend fun throwIfStopped(signal: CancellationSignal) {
        coroutineContext.ensureActive()
        if (signal.isCanceled) throw ScanCancelledException()
    }

    private companion object {
        const val PAGE: Int = 400
    }
}

private class LoadedLibrary(
    val items: List<DedupItem>,
    val assets: Map<Long, AssetRecord>,
    val features: Map<Long, FeatureRecord>,
)

private fun AssetRecord.toScoreMember(feature: FeatureRecord?): ScoreMember {
    val seconds = dateTaken ?: dateAdded
    return ScoreMember(
        assetId = assetId,
        sizeBytes = sizeBytes,
        width = width,
        height = height,
        blurScore = feature?.blurScore,
        relativePath = relativePath,
        uri = uri,
        bucketId = bucketId,
        favorite = isFavorite,
        takenAtMs = seconds?.times(1_000L),
    )
}
private fun AssetRecord.toDedupItem(feature: FeatureRecord?): DedupItem {
    val seconds = dateTaken ?: dateAdded
    return DedupItem(
        assetId = assetId,
        byteHash = feature?.byteHash,
        phash = parseHash(feature?.phash),
        dhash = parseHash(feature?.dhash),
        width = width,
        height = height,
        takenAtMs = seconds?.times(1_000L),
        video = isVideoUri(uri),
    )
}

internal fun parseHash(text: String?): Long? {
    if (text.isNullOrBlank() || text.length > 16) return null
    return text.toULongOrNull(16)?.toLong()
}

/**
 * 按索引里的哈希替换分组。
 *
 * 调用方要把收集放在后台线程上。取消 [signal] 时不改已经写好的分组。
 *
 * @param index 已经打开的索引。
 * @param settings 阈值。
 * @param signal 取消信号。
 */
public fun dedupIndexed(
    index: AlbumIndex,
    settings: DedupSettings,
    signal: CancellationSignal,
    scoring: ScoreSettings = ScoreSettings(),
): Flow<DedupProgress> {
    return DedupPass(index, settings, scoring).progress(signal)
}
