/**
 * 保留哪一张，以及哪些照片不能进候选。
 *
 * @file
 */
package com.arturoyi.albumcleaner.scan

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 不碰数据库。
 */
class ScoreTest {
    /**
     * 更清晰的那张留下。模糊的候选会带上模糊原因。
     */
    @Test
    fun keepsTheSharperPhoto() {
        val score = scoreGroup(
            listOf(
                member(1L, blur = 10.0),
                member(2L, blur = 400.0),
            ),
            groupType = "EXACT_DUPLICATE",
            settings = ScoreSettings(protectRecentDays = 0, protectLargestInGroup = false, protectFavorites = false),
            nowMs = 0L,
        )
        assertEquals(2L, score.keepAssetId)
        assertEquals(listOf(1L), score.candidates.map { it.assetId })
        assertEquals(listOf("EXACT_DUPLICATE", "BLURRY"), score.candidates.single().reasons)
        assertEquals("HIGH", score.candidates.single().confidence)
    }

    /**
     * 收藏、最近拍摄、原始格式和唯一的最大文件不进入候选。
     */
    @Test
    fun protectedPhotosAreNotCandidates() {
        val now = 10L * 86_400_000L
        val favorite = scoreGroup(
            listOf(member(1L, blur = 400.0), member(2L, blur = 10.0, favorite = true)),
            "NEAR_DUPLICATE",
            ScoreSettings(protectRecentDays = 0, protectLargestInGroup = false, favoriteProtectDays = 0),
            now,
        )
        assertTrue(favorite.candidates.none { it.assetId == 2L })

        val recent = scoreGroup(
            listOf(member(1L, blur = 400.0, taken = 0L), member(2L, blur = 10.0, taken = now)),
            "NEAR_DUPLICATE",
            ScoreSettings(protectFavorites = false, protectRecentDays = 7, protectLargestInGroup = false),
            now,
        )
        assertTrue(recent.candidates.none { it.assetId == 2L })

        val raw = scoreGroup(
            listOf(member(1L, blur = 400.0), member(2L, blur = 10.0, path = "DCIM/a.DNG")),
            "EXACT_DUPLICATE",
            ScoreSettings(protectRecentDays = 0, protectFavorites = false, protectLargestInGroup = false),
            now,
        )
        assertTrue(raw.candidates.none { it.assetId == 2L })

        val largest = scoreGroup(
            listOf(member(1L, blur = 400.0, size = 10L), member(2L, blur = 10.0, size = 500L)),
            "EXACT_DUPLICATE",
            ScoreSettings(protectRecentDays = 0, protectFavorites = false, protectLargestInGroup = true),
            now,
        )
        assertEquals(1L, largest.keepAssetId)
        assertTrue(largest.candidates.isEmpty())
    }
}

private fun member(
    id: Long,
    blur: Double?,
    size: Long = 100L,
    favorite: Boolean = false,
    path: String? = "DCIM/a.jpg",
    taken: Long? = 0L,
): ScoreMember {
    return ScoreMember(
        assetId = id,
        sizeBytes = size,
        width = 1000,
        height = 1000,
        blurScore = blur,
        relativePath = path,
        uri = "content://media/external/images/media/$id",
        bucketId = "camera",
        favorite = favorite,
        takenAtMs = taken,
    )
}
