/**
 * 完全重复、近似和连拍的分组规则。
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
class DedupTest {
    /**
     * 相同字节哈希合成完全重复。没有哈希的不进组，已经进组的不再参与近似。
     */
    @Test
    fun exactHashGroupsAndLeavesSinglesOut() {
        val groups = deduplicate(
            listOf(
                item(1L, byteHash = "aa", phash = 0L),
                item(2L, byteHash = "aa", phash = 0L),
                item(3L, byteHash = "bb"),
                item(4L, byteHash = null),
                item(5L, byteHash = ""),
            ),
            DedupSettings(),
        )
        assertEquals(listOf("EXACT_DUPLICATE"), groups.map { it.type })
        assertEquals(setOf(1L, 2L), groups.single().memberIds.toSet())
    }

    /**
     * 同一天、汉明距离够近、时间落在窗里，标成连拍。
     */
    @Test
    fun closePhotosOnTheSameDayAreBursts() {
        val groups = deduplicate(
            listOf(
                item(1L, phash = 0L, takenAtMs = 0L),
                item(2L, phash = 1L, takenAtMs = 30_000L),
            ),
            DedupSettings(phashHammingMax = 8, burstWindowMs = 60_000L),
        )
        assertEquals("BURST", groups.single().type)
        assertEquals(setOf(1L, 2L), groups.single().memberIds.toSet())
    }

    /**
     * 同一天但超出连拍时间窗，标成近似。隔天即使哈希相同也不比较。
     */
    @Test
    fun sameDayOutsideTheBurstWindowIsNearAndAnotherDayStaysSeparate() {
        val near = deduplicate(
            listOf(
                item(1L, phash = 0L, takenAtMs = 0L),
                item(2L, phash = 0L, takenAtMs = 3_600_000L),
            ),
            DedupSettings(burstWindowMs = 60_000L),
        )
        assertEquals("NEAR_DUPLICATE", near.single().type)

        val otherDay = deduplicate(
            listOf(
                item(1L, phash = 0L, takenAtMs = 0L),
                item(2L, phash = 0L, takenAtMs = 86_400_000L),
            ),
            DedupSettings(),
        )
        assertTrue(otherDay.isEmpty())
    }

    /**
     * 汉明距离太远，或尺寸差出档，都不成组。相邻高位桶里够近的可以。
     */
    @Test
    fun rejectsFarHashesAndDistantSizesButAcceptsAnAdjacentBucket() {
        val far = deduplicate(
            listOf(item(1L, phash = 0L), item(2L, phash = 0xFFL)),
            DedupSettings(phashHammingMax = 4),
        )
        assertTrue(far.isEmpty())

        val differentSize = deduplicate(
            listOf(
                item(1L, phash = 0L, width = 100, height = 100),
                item(2L, phash = 0L, width = 400, height = 400),
            ),
            DedupSettings(sizeTolerancePercent = 20),
        )
        assertTrue(differentSize.isEmpty())

        val adjacent = deduplicate(
            listOf(
                item(1L, phash = 0L, takenAtMs = 0L),
                item(2L, phash = 1L shl 48, takenAtMs = 1_000L),
            ),
            DedupSettings(phashHammingMax = 8, burstWindowMs = 60_000L),
        )
        assertEquals("BURST", adjacent.single().type)
    }

    /**
     * 视频可以和照片组成完全重复，但不因为感知哈希进入近似组。
     */
    @Test
    fun videosJoinExactGroupsButNotVisualGroups() {
        val exact = deduplicate(
            listOf(
                item(1L, byteHash = "aa"),
                item(2L, byteHash = "aa", video = true),
            ),
            DedupSettings(),
        )
        assertEquals(setOf(1L, 2L), exact.single().memberIds.toSet())

        val visual = deduplicate(
            listOf(
                item(1L, phash = 0L),
                item(2L, phash = 0L, video = true),
            ),
            DedupSettings(),
        )
        assertTrue(visual.isEmpty())
    }

    /**
     * 超出组大小后按时间切开。剩下单独一张时并进上一组，避免它从重复里消失。
     */
    @Test
    fun splitsLargeGroupsByTime() {
        val items = (1L..5L).map { id -> item(id, byteHash = "aa", takenAtMs = id * 1_000L) }
        val groups = deduplicate(items, DedupSettings(maxGroupSize = 2))
        assertEquals(listOf(2, 3), groups.map { it.memberIds.size })
        assertEquals((1L..5L).toSet(), groups.flatMap { it.memberIds }.toSet())
    }
}

private fun item(
    id: Long,
    byteHash: String? = null,
    phash: Long? = null,
    width: Int? = 100,
    height: Int? = 100,
    takenAtMs: Long? = 0L,
    video: Boolean = false,
): DedupItem {
    return DedupItem(
        assetId = id,
        byteHash = byteHash,
        phash = phash,
        dhash = null,
        width = width,
        height = height,
        takenAtMs = takenAtMs,
        video = video,
    )
}
