/**
 * 用已经算好的哈希分组。不读文件，也不解码。
 *
 * @file
 */
package com.arturoyi.albumcleaner.scan

/**
 * 参与分组的一条记录。
 *
 * @property assetId 媒体库主键。
 * @property byteHash 头尾或全文哈希。没有时为 null。
 * @property phash 感知哈希。没有时为 null。
 * @property dhash 差分哈希。没有时为 null。
 * @property width 像素宽。未知时为 null。
 * @property height 像素高。未知时为 null。
 * @property takenAtMs 拍摄或入库时间，Unix 毫秒。未知时为 null。
 * @property video 视频不参与近似分组。
 */
internal data class DedupItem(
    val assetId: Long,
    val byteHash: String?,
    val phash: Long?,
    val dhash: Long?,
    val width: Int?,
    val height: Int?,
    val takenAtMs: Long?,
    val video: Boolean,
)

/**
 * 一组重复照片。还没有选出保留哪一张。
 *
 * @property groupId 稳定的组标识。
 * @property type [com.arturoyi.albumcleaner.GroupType] 的名字。
 * @property memberIds 成员，至少两张。
 */
internal data class DedupGroupDraft(
    val groupId: String,
    val type: String,
    val memberIds: List<Long>,
)

/**
 * 分组用的阈值。语义聚类不在这里。
 *
 * @property phashHammingMax 感知哈希或差分哈希的汉明距离上限。
 * @property burstWindowMs 整组都落在这个时间窗里时标成连拍，单位毫秒。
 * @property sizeTolerancePercent 宽和高允许的相对差，百分比。
 * @property maxGroupSize 一次比较里向后看多少张。一组超出后再按时间切开。
 */
public data class DedupSettings(
    public val phashHammingMax: Int = 8,
    public val burstWindowMs: Long = 60_000L,
    public val sizeTolerancePercent: Int = 20,
    public val maxGroupSize: Int = 20,
)

/**
 * 先按字节哈希收完全重复，再在同一天的相邻感知哈希桶里收近似和连拍。
 *
 * 每张只和本桶时间上靠后的有限几张、以及相邻桶里时间接近的几张比较。
 *
 * @param items 索引里的照片和视频。
 * @param settings 阈值。
 * @return 至少两张的组。
 */
internal fun deduplicate(items: List<DedupItem>, settings: DedupSettings): List<DedupGroupDraft> {
    val unique = items.distinctBy { item -> item.assetId }
    val limit = settings.maxGroupSize.coerceAtLeast(2)
    val distance = settings.phashHammingMax.coerceAtLeast(0)
    val burstWindow = settings.burstWindowMs.coerceAtLeast(0L)
    val exactIds = HashSet<Long>()
    val groups = mutableListOf<DedupGroupDraft>()
    unique
        .filter { item -> !item.byteHash.isNullOrBlank() }
        .groupBy { item -> item.byteHash.orEmpty() }
        .forEach { (hash, members) ->
            if (members.size < 2) return@forEach
            exactIds += members.map { item -> item.assetId }
            groups += splitByTime(members, TYPE_EXACT, "exact:$hash", limit)
        }
    val visual = unique.filter { item ->
        item.assetId !in exactIds && !item.video && (item.phash != null || item.dhash != null)
    }
    if (visual.isNotEmpty()) {
        groups += clusterVisual(visual, settings, limit, distance, burstWindow)
    }
    return groups
}

private fun clusterVisual(
    items: List<DedupItem>,
    settings: DedupSettings,
    limit: Int,
    distance: Int,
    burstWindow: Long,
): List<DedupGroupDraft> {
    val buckets = items.groupBy { item -> BucketKey(dayOf(item.takenAtMs), prefixOf(item)) }
        .mapValues { (_, members) ->
            members.sortedWith(compareBy({ item -> item.takenAtMs ?: Long.MAX_VALUE }, { item -> item.assetId }))
        }
    val union = UnionFind(items.map { item -> item.assetId })
    fun consider(left: DedupItem, right: DedupItem) {
        if (left.assetId == right.assetId) return
        if (!withinSizeTolerance(left, right, settings.sizeTolerancePercent)) return
        if (!visuallyNear(left, right, distance)) return
        union.union(left.assetId, right.assetId)
    }
    buckets.forEach { (key, members) ->
        compareWindow(members, limit, ::consider)
        prefixNeighbors(key.prefix).forEach { neighbor ->
            if (neighbor <= key.prefix) return@forEach
            val others = buckets[BucketKey(key.day, neighbor)] ?: return@forEach
            compareNearby(members, others, limit, ::consider)
        }
    }
    val byId = items.associateBy { item -> item.assetId }
    return union.roots(items.map { item -> item.assetId })
        .map { ids -> ids.map { id -> byId.getValue(id) } }
        .filter { members -> members.size >= 2 }
        .flatMap { members ->
            val type = if (isBurst(members, burstWindow)) TYPE_BURST else TYPE_NEAR
            val anchor = members.minOf { item -> item.assetId }
            splitByTime(members, type, "$type:$anchor", limit)
        }
}

private fun compareWindow(
    members: List<DedupItem>,
    limit: Int,
    consider: (DedupItem, DedupItem) -> Unit,
) {
    for (index in members.indices) {
        val last = minOf(members.size, index + limit)
        for (next in index + 1 until last) {
            consider(members[index], members[next])
        }
    }
}

private fun compareNearby(
    left: List<DedupItem>,
    right: List<DedupItem>,
    limit: Int,
    consider: (DedupItem, DedupItem) -> Unit,
) {
    for (item in left) {
        val time = item.takenAtMs ?: Long.MAX_VALUE
        var index = right.binarySearchBy(time) { candidate -> candidate.takenAtMs ?: Long.MAX_VALUE }
        if (index < 0) index = -index - 1
        val from = (index - limit / 2).coerceAtLeast(0)
        val to = (from + limit).coerceAtMost(right.size)
        for (cursor in from until to) {
            consider(item, right[cursor])
        }
    }
}

private fun splitByTime(
    members: List<DedupItem>,
    type: String,
    idPrefix: String,
    limit: Int,
): List<DedupGroupDraft> {
    val ordered = members.sortedWith(compareBy({ item -> item.takenAtMs ?: Long.MAX_VALUE }, { item -> item.assetId }))
    if (ordered.size < 2) return emptyList()
    val chunks = ordered.chunked(limit).toMutableList()
    if (chunks.size >= 2 && chunks.last().size == 1) {
        val only = chunks.removeAt(chunks.lastIndex).single()
        val previous = chunks.removeAt(chunks.lastIndex).toMutableList()
        previous += only
        chunks += previous
    }
    return chunks.mapIndexed { index, chunk ->
        DedupGroupDraft(
            groupId = "$idPrefix:$index",
            type = type,
            memberIds = chunk.map { item -> item.assetId },
        )
    }
}

private fun isBurst(members: List<DedupItem>, windowMs: Long): Boolean {
    val times = members.map { item -> item.takenAtMs ?: return false }
    return times.max() - times.min() <= windowMs
}

private fun visuallyNear(left: DedupItem, right: DedupItem, distance: Int): Boolean {
    val leftHash = left.phash
    val rightHash = right.phash
    if (leftHash != null && rightHash != null) {
        return hamming(leftHash, rightHash) <= distance
    }
    val leftDiff = left.dhash
    val rightDiff = right.dhash
    if (leftDiff != null && rightDiff != null) {
        return hamming(leftDiff, rightDiff) <= distance
    }
    return false
}

internal fun withinSizeTolerance(left: DedupItem, right: DedupItem, percent: Int): Boolean {
    val leftWidth = left.width
    val leftHeight = left.height
    val rightWidth = right.width
    val rightHeight = right.height
    if (leftWidth == null || leftHeight == null || rightWidth == null || rightHeight == null) return true
    if (leftWidth <= 0 || leftHeight <= 0 || rightWidth <= 0 || rightHeight <= 0) return true
    val allowance = percent.coerceAtLeast(0)
    return closeEnough(leftWidth, rightWidth, allowance) && closeEnough(leftHeight, rightHeight, allowance)
}

private fun closeEnough(left: Int, right: Int, percent: Int): Boolean {
    val base = maxOf(left, right)
    if (base == 0) return true
    return kotlin.math.abs(left - right).toLong() * 100 <= base.toLong() * percent
}

internal fun hamming(left: Long, right: Long): Int = java.lang.Long.bitCount(left xor right)

internal fun hashPrefix(hash: Long): Int = ((hash ushr 48) and 0xFFFF).toInt()

private fun prefixOf(item: DedupItem): Int = hashPrefix(item.phash ?: item.dhash ?: 0L)

private fun prefixNeighbors(prefix: Int): List<Int> {
    val value = prefix and 0xFFFF
    return List(16) { bit -> value xor (1 shl bit) }
}

private fun dayOf(takenAtMs: Long?): Long {
    if (takenAtMs == null) return Long.MIN_VALUE
    return Math.floorDiv(takenAtMs, DAY_MS)
}

private data class BucketKey(val day: Long, val prefix: Int)

private class UnionFind(ids: Collection<Long>) {
    private val parent = HashMap<Long, Long>(ids.size)

    init {
        ids.forEach { id -> parent[id] = id }
    }

    fun find(id: Long): Long {
        var node = id
        while (parent[node] != node) {
            node = parent.getValue(node)
        }
        var cursor = id
        while (cursor != node) {
            val next = parent.getValue(cursor)
            parent[cursor] = node
            cursor = next
        }
        return node
    }

    fun union(left: Long, right: Long) {
        val leftRoot = find(left)
        val rightRoot = find(right)
        if (leftRoot == rightRoot) return
        if (leftRoot < rightRoot) parent[rightRoot] = leftRoot else parent[leftRoot] = rightRoot
    }

    fun roots(ids: Collection<Long>): Collection<List<Long>> {
        return ids.groupBy { id -> find(id) }.values
    }
}

private const val DAY_MS: Long = 86_400_000L
private const val TYPE_EXACT: String = "EXACT_DUPLICATE"
private const val TYPE_NEAR: String = "NEAR_DUPLICATE"
private const val TYPE_BURST: String = "BURST"
