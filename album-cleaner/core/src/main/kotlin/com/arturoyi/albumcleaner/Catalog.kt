/**
 * 把索引里的分组、候选和回收站读成公开类型。
 *
 * @file
 */
package com.arturoyi.albumcleaner

import android.net.Uri
import com.arturoyi.albumcleaner.storage.AlbumIndex
import com.arturoyi.albumcleaner.storage.CandidateRecord

internal fun readGroups(index: AlbumIndex, query: GroupQuery): PagedResult<CleanGroup> {
    val limit = checkedLimit(query.limit)
    val cursor = query.cursor ?: ""
    val type = query.type?.name
    val rows = index.groupsAfter(cursor, limit, type)
    val items = rows.mapNotNull { group ->
        val members = index.groupMembers(group.groupId)
        if (members.isEmpty()) return@mapNotNull null
        val keep = group.keepAssetId ?: members.minOf { member -> member.assetId }
        val groupType = runCatching { GroupType.valueOf(group.type) }.getOrNull() ?: return@mapNotNull null
        CleanGroup(
            groupId = group.groupId,
            type = groupType,
            keepAssetId = keep,
            memberCount = members.size,
            totalBytes = members.sumOf { member -> index.asset(member.assetId)?.sizeBytes ?: 0L },
        )
    }
    val next = if (rows.size < limit) null else rows.last().groupId
    return PagedResult(items, next, index.countGroups(type))
}

internal fun readCandidates(index: AlbumIndex, query: CandidateQuery): PagedResult<CleanCandidate> {
    val limit = checkedLimit(query.limit)
    val cursor = query.cursor?.toLongOrNull() ?: -1L
    val confidence = query.confidence?.name
    val reason = query.reason?.name
    val rows = index.candidatesMatching(cursor, limit, confidence, reason)
    val keeps = index.allGroups().associate { group -> group.groupId to group.keepAssetId }
    val items = rows.mapNotNull { record -> record.toCandidate(index, keeps) }
    val next = if (rows.size < limit) null else rows.last().assetId.toString()
    return PagedResult(items, next, index.countCandidates(confidence, reason))
}

internal fun readRecycle(index: AlbumIndex, query: PageQuery): PagedResult<RecycleEntry> {
    val limit = checkedLimit(query.limit)
    val (actionId, assetId) = parseRecycleCursor(query.cursor)
    val rows = index.recycleAfter(actionId, assetId, limit)
    val items = rows.map { record ->
        RecycleEntry(
            actionId = record.actionId,
            assetId = record.assetId,
            uriSnapshot = record.uriSnapshot?.let { uri -> runCatching { Uri.parse(uri) }.getOrNull() },
            expiresAt = record.expiresAt ?: 0L,
            restored = record.restored,
        )
    }
    val next = if (rows.size < limit) {
        null
    } else {
        val last = rows.last()
        "${last.actionId}\t${last.assetId}"
    }
    return PagedResult(items, next, null)
}

internal fun readSpace(index: AlbumIndex): SpaceReport {
    val byReason = mutableMapOf<ReasonCode, Long>()
    var estimated = 0L
    var cursor = -1L
    while (true) {
        val page = index.candidatesAfter(cursor, PAGE)
        if (page.isEmpty()) break
        page.forEach { record ->
            estimated += record.estimatedBytes
            val reason = parseReasons(record.reasonsJson).firstOrNull() ?: return@forEach
            byReason[reason] = (byReason[reason] ?: 0L) + record.estimatedBytes
        }
        cursor = page.last().assetId
    }
    return SpaceReport(
        totalEstimatedBytes = estimated,
        byReason = byReason,
        appliedBytes = index.appliedBytes(),
    )
}

private fun CandidateRecord.toCandidate(index: AlbumIndex, keeps: Map<String, Long?>): CleanCandidate? {
    val asset = index.asset(assetId) ?: return null
    val keep = keeps[groupId] ?: assetId
    val confidenceLevel = runCatching { ConfidenceLevel.valueOf(confidence) }.getOrDefault(ConfidenceLevel.MEDIUM)
    val uri = runCatching { Uri.parse(asset.uri) }.getOrNull() ?: return null
    return CleanCandidate(
        assetId = assetId,
        uri = uri,
        groupId = groupId,
        confidence = confidenceLevel,
        reasonCodes = parseReasons(reasonsJson),
        estimatedBytes = estimatedBytes,
        keepAssetId = keep,
        protectionReasons = emptyList(),
    )
}

internal fun parseReasons(json: String): List<ReasonCode> {
    return REASON.findAll(json).mapNotNull { match ->
        runCatching { ReasonCode.valueOf(match.groupValues[1]) }.getOrNull()
    }.toList()
}

private fun parseRecycleCursor(cursor: String?): Pair<String, Long> {
    if (cursor.isNullOrEmpty()) return "" to -1L
    val parts = cursor.split('\t', limit = 2)
    if (parts.size < 2) return "" to -1L
    return parts[0] to (parts[1].toLongOrNull() ?: -1L)
}

private fun checkedLimit(limit: Int): Int {
    require(limit in 1..PAGE) { "limit 必须在 1 到 $PAGE 之间" }
    return limit
}

private const val PAGE: Int = 400
private val REASON: Regex = Regex("\"([A-Z_]+)\"")
