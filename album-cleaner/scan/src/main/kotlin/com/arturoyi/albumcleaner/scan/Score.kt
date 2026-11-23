/**
 * 选出每组留下的照片，并给出可以删除的候选。
 *
 * @file
 */
package com.arturoyi.albumcleaner.scan

/**
 * 打分和保护用的一张照片。
 *
 * @property assetId 媒体库主键。
 * @property sizeBytes 文件字节数。
 * @property width 像素宽。未知时为 null。
 * @property height 像素高。未知时为 null。
 * @property blurScore 清晰度。未知时为 null，打分时当成 0。
 * @property relativePath 相对路径，用来看扩展名。
 * @property uri 内容地址，路径没有扩展名时用它。
 * @property bucketId 相册桶。
 * @property favorite 是否收藏。
 * @property takenAtMs 拍摄或入库时间，Unix 毫秒。
 */
internal data class ScoreMember(
    val assetId: Long,
    val sizeBytes: Long,
    val width: Int?,
    val height: Int?,
    val blurScore: Double?,
    val relativePath: String?,
    val uri: String,
    val bucketId: String?,
    val favorite: Boolean,
    val takenAtMs: Long?,
)

/**
 * 保护规则。命中的照片不进入候选。
 *
 * @property protectFavorites 收藏不进入候选。
 * @property favoriteProtectDays 收藏后继续保护的天数。0 表示只要收藏就保护。
 * @property protectRecentDays 最近这么多天内拍摄的不进入候选。0 表示不按时间保护。
 * @property excludedBucketIds 这些相册桶不进入候选。
 * @property protectedExtensions 这些扩展名不进入候选。
 * @property protectLargestInGroup 一组里体积最大的不进入候选。
 * @property blurVarianceMin 清晰度低于这个值时，候选原因里加上模糊。
 */
public data class ScoreSettings(
    public val protectFavorites: Boolean = true,
    public val favoriteProtectDays: Int = 7,
    public val protectRecentDays: Int = 7,
    public val excludedBucketIds: Set<String> = emptySet(),
    public val protectedExtensions: Set<String> = setOf("raw", "dng", "heic"),
    public val protectLargestInGroup: Boolean = true,
    public val blurVarianceMin: Double = 100.0,
)

/**
 * 一组的保留项和可删除候选。
 *
 * @property keepAssetId 建议留下的照片。
 * @property candidates 可以删除的成员。受保护的不在里面。
 */
internal data class GroupScore(
    val keepAssetId: Long,
    val candidates: List<DeletionCandidate>,
)

/**
 * 一张可以删除的照片。
 *
 * @property assetId 媒体库主键。
 * @property reasons 推荐删除的原因，靠前的更主要。
 * @property estimatedBytes 删掉大约能释放的字节数。
 * @property confidence `HIGH`、`MEDIUM` 或 `LOW`。
 */
internal data class DeletionCandidate(
    val assetId: Long,
    val reasons: List<String>,
    val estimatedBytes: Long,
    val confidence: String,
)

/**
 * 按清晰度、分辨率和拍摄顺序打分。分数相同则留更大的文件，再留更小的主键。
 *
 * @param members 同一组的成员，至少一张。
 * @param groupType 分组类型原文。
 * @param settings 保护和模糊线。
 * @param nowMs 用来判断最近拍摄，Unix 毫秒。
 */
internal fun scoreGroup(
    members: List<ScoreMember>,
    groupType: String,
    settings: ScoreSettings,
    nowMs: Long,
): GroupScore {
    require(members.isNotEmpty()) { "组里至少要有一张照片" }
    val sharpness = normalize(members.map { member -> member.blurScore ?: 0.0 })
    val resolution = normalize(members.map { member -> pixelCount(member).toDouble() })
    val order = members.sortedWith(compareBy({ it.takenAtMs ?: Long.MAX_VALUE }, { it.assetId }))
    val position = order.withIndex().associate { (index, member) -> member.assetId to index }
    val center = (members.size - 1) / 2.0
    val scored = members.mapIndexed { index, member ->
        val place = position.getValue(member.assetId)
        val score = 0.30 * sharpness[index] +
            0.20 * resolution[index] +
            0.15 * encodingScore(member, settings) +
            0.15 * metadataScore(member) +
            0.10 * SUBJECT_SCORE +
            0.10 * (1.0 - kotlin.math.abs(place - center) / members.size)
        member to score
    }
    val keep = scored.maxWith(
        compareBy<Pair<ScoreMember, Double>> { pair -> pair.second }
            .thenBy { pair -> pair.first.sizeBytes }
            .thenByDescending { pair -> pair.first.assetId },
    ).first
    val largest = members.maxOf { member -> member.sizeBytes }
    val uniqueLargest = members.count { member -> member.sizeBytes == largest } == 1
    val candidates = members.mapNotNull { member ->
        if (member.assetId == keep.assetId) return@mapNotNull null
        if (isProtected(member, largest, uniqueLargest, settings, nowMs)) return@mapNotNull null
        DeletionCandidate(
            assetId = member.assetId,
            reasons = reasonsFor(member, groupType, settings),
            estimatedBytes = member.sizeBytes,
            confidence = confidenceFor(groupType),
        )
    }
    return GroupScore(keep.assetId, candidates)
}

private fun isProtected(
    member: ScoreMember,
    largestBytes: Long,
    uniqueLargest: Boolean,
    settings: ScoreSettings,
    nowMs: Long,
): Boolean {
    if (settings.protectFavorites && member.favorite && favoriteStillProtected(member, settings, nowMs)) {
        return true
    }
    val taken = member.takenAtMs
    if (settings.protectRecentDays > 0 && taken != null) {
        val window = settings.protectRecentDays.toLong() * DAY_MS
        if (nowMs - taken in 0..window) return true
    }
    val bucket = member.bucketId
    if (bucket != null && bucket in settings.excludedBucketIds) return true
    val extension = fileExtension(member.relativePath, member.uri)
    if (extension.isNotEmpty() && extension in settings.protectedExtensions.map { it.lowercase() }) return true
    return settings.protectLargestInGroup && uniqueLargest && member.sizeBytes == largestBytes && largestBytes > 0L
}

private fun favoriteStillProtected(member: ScoreMember, settings: ScoreSettings, nowMs: Long): Boolean {
    if (settings.favoriteProtectDays <= 0) return true
    val taken = member.takenAtMs ?: return true
    val window = settings.favoriteProtectDays.toLong() * DAY_MS
    return nowMs - taken in 0..window
}

private fun reasonsFor(member: ScoreMember, groupType: String, settings: ScoreSettings): List<String> {
    val reasons = mutableListOf<String>()
    when (groupType) {
        "EXACT_DUPLICATE" -> reasons += "EXACT_DUPLICATE"
        "NEAR_DUPLICATE", "BURST" -> reasons += "NEAR_DUPLICATE"
    }
    val blur = member.blurScore
    if (blur != null && blur < settings.blurVarianceMin) reasons += "BLURRY"
    val width = member.width
    val height = member.height
    if (width != null && height != null && width > 0 && height > 0 && width.toLong() * height < SMALL_PIXELS) {
        reasons += "TOO_SMALL"
    }
    if (reasons.isEmpty()) reasons += "NEAR_DUPLICATE"
    return reasons.take(2)
}

private fun confidenceFor(groupType: String): String {
    return if (groupType == "EXACT_DUPLICATE") "HIGH" else "MEDIUM"
}

private fun encodingScore(member: ScoreMember, settings: ScoreSettings): Double {
    val extension = fileExtension(member.relativePath, member.uri)
    val preferred = settings.protectedExtensions.map { it.lowercase() }.toSet()
    return if (extension.isNotEmpty() && extension in preferred) 1.0 else 0.6
}

private fun metadataScore(member: ScoreMember): Double {
    return if (member.takenAtMs != null && member.width != null && member.height != null) 1.0 else 0.4
}

private fun pixelCount(member: ScoreMember): Long {
    val width = member.width ?: return 0L
    val height = member.height ?: return 0L
    if (width <= 0 || height <= 0) return 0L
    return width.toLong() * height
}

private fun normalize(values: List<Double>): List<Double> {
    val min = values.min()
    val max = values.max()
    if (max == min) return List(values.size) { 1.0 }
    return values.map { value -> (value - min) / (max - min) }
}

internal fun fileExtension(relativePath: String?, uri: String): String {
    val source = relativePath?.takeIf { it.isNotBlank() } ?: uri
    val name = source.substringAfterLast('/').substringBefore('?')
    val dot = name.lastIndexOf('.')
    if (dot < 0 || dot == name.lastIndex) return ""
    return name.substring(dot + 1).lowercase()
}

internal fun reasonJson(reasons: List<String>): String {
    return reasons.joinToString(prefix = "[", postfix = "]") { reason -> "\"$reason\"" }
}

private const val SUBJECT_SCORE: Double = 0.5
private const val SMALL_PIXELS: Long = 320L * 240L
private const val DAY_MS: Long = 86_400_000L
