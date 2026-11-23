/**
 * 分组写入索引，以及取消时保留旧分组。
 *
 * @file
 */
package com.arturoyi.albumcleaner.scan

import android.os.CancellationSignal
import com.arturoyi.albumcleaner.storage.AlbumIndex
import com.arturoyi.albumcleaner.storage.AssetRecord
import com.arturoyi.albumcleaner.storage.FeatureRecord
import com.arturoyi.albumcleaner.storage.GroupMemberRecord
import com.arturoyi.albumcleaner.storage.GroupRecord
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/**
 * 用真实索引。
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class DedupPassTest {
    /**
     * 完全重复会留下主键更小的一张，另一张成为候选。旧分组会被换掉。
     */
    @Test
    fun writesKeepAndOneCandidate() = runBlocking {
        AlbumIndex.open(context(), "groups.db").use { index ->
            index.upsertAssets(listOf(asset(1L), asset(2L), asset(3L, takenSeconds = 86_400L)))
            index.upsertFeatures(
                listOf(
                    feature(1L, byteHash = "aa", phash = 0L),
                    feature(2L, byteHash = "aa", phash = 0L),
                    feature(3L, byteHash = "bb", phash = 0L),
                ),
            )
            index.saveGroup(GroupRecord("old", "EXACT_DUPLICATE", keepAssetId = 9L, createdAt = 1L))
            index.replaceGroupMembers(
                "old",
                listOf(GroupMemberRecord("old", 9L, score = null, reasonCode = "EXACT_DUPLICATE")),
            )
            DedupPass(index, DedupSettings(), now = { 30L * 86_400_000L }).progress(CancellationSignal()).collect { }
            val groups = index.allGroups()
            assertEquals(listOf("EXACT_DUPLICATE"), groups.map { it.type })
            assertEquals(1L, groups.single().keepAssetId)
            assertEquals(setOf(1L, 2L), index.groupMembers(groups.single().groupId).map { it.assetId }.toSet())
            val candidates = index.candidatesAfter(-1L, 10)
            assertEquals(listOf(2L), candidates.map { it.assetId })
            assertEquals("HIGH", candidates.single().confidence)
            assertEquals("[\"EXACT_DUPLICATE\"]", candidates.single().reasonsJson)
            assertEquals(SESSION_DONE, index.session(METADATA_SESSION_ID)?.status)
            assertTrue(groups.none { it.groupId == "old" })
        }
    }

    /**
     * 取消发生在替换之前。原来的组还在。
     */
    @Test
    fun cancelKeepsExistingGroups() = runBlocking {
        AlbumIndex.open(context(), "keep.db").use { index ->
            index.upsertAssets(listOf(asset(1L), asset(2L)))
            index.upsertFeatures(listOf(feature(1L, byteHash = "aa"), feature(2L, byteHash = "aa")))
            index.saveGroup(GroupRecord("old", "NEAR_DUPLICATE", keepAssetId = null, createdAt = 1L))
            index.replaceGroupMembers(
                "old",
                listOf(
                    GroupMemberRecord("old", 1L, score = null, reasonCode = "NEAR_DUPLICATE"),
                    GroupMemberRecord("old", 2L, score = null, reasonCode = "NEAR_DUPLICATE"),
                ),
            )
            val signal = CancellationSignal()
            signal.cancel()
            val error = runCatching {
                DedupPass(index, DedupSettings()).progress(signal).collect { }
            }.exceptionOrNull()
            assertTrue(error is ScanCancelledException)
            assertEquals(listOf("old"), index.allGroups().map { it.groupId })
        }
    }
}

private fun context() = RuntimeEnvironment.getApplication()

private fun asset(id: Long, takenSeconds: Long = 0L): AssetRecord {
    return AssetRecord(
        assetId = id,
        uri = "content://media/external/images/media/$id",
        sizeBytes = 10L,
        width = 2000,
        height = 2000,
        dateAdded = takenSeconds,
        dateModified = 1L,
        dateTaken = takenSeconds,
        bucketId = "camera",
        relativePath = null,
        isFavorite = false,
        mediaVersion = null,
        indexedAt = 1L,
    )
}

private fun feature(id: Long, byteHash: String? = null, phash: Long? = null): FeatureRecord {
    return FeatureRecord(
        assetId = id,
        byteHash = byteHash,
        phash = phash?.toHashText(),
        dhash = null,
        blurScore = null,
        contentFingerprint = "fp-$id",
        algorithmVersion = 1,
        computedAt = 1L,
    )
}
