/**
 * 查询分组和候选，以及删除后索引怎么变。
 *
 * @file
 */
package com.arturoyi.albumcleaner

import android.app.Activity
import android.app.PendingIntent
import android.content.Intent
import android.content.IntentSender
import android.net.Uri
import com.arturoyi.albumcleaner.storage.AlbumIndex
import com.arturoyi.albumcleaner.storage.AssetRecord
import com.arturoyi.albumcleaner.storage.CandidateRecord
import com.arturoyi.albumcleaner.storage.GroupMemberRecord
import com.arturoyi.albumcleaner.storage.GroupRecord
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/**
 * 用真实索引和假的系统删除。
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [30])
class DeletionAndQueryTest {
    /**
     * 分组和候选能按页读出来。缩略图交给注入的解码器。
     */
    @Test
    fun readsGroupsCandidatesAndThumbnail() = runBlocking {
        val index = AlbumIndex.open(context(), "query.db")
        index.upsertAssets(listOf(photo(1L), photo(2L)))
        index.saveGroup(GroupRecord("g", "EXACT_DUPLICATE", keepAssetId = 1L, createdAt = 1L))
        index.replaceGroupMembers(
            "g",
            listOf(
                GroupMemberRecord("g", 1L, score = null, reasonCode = "EXACT_DUPLICATE"),
                GroupMemberRecord("g", 2L, score = null, reasonCode = "EXACT_DUPLICATE"),
            ),
        )
        index.replaceCandidates(
            listOf(
                CandidateRecord(2L, "g", "HIGH", "[\"EXACT_DUPLICATE\"]", 20L, 1L),
            ),
        )
        val engine = engine(index, sdkInt = 30, gateway = unusedGateway())
        engine.initialize()
        val groups = engine.groups(GroupQuery())
        assertEquals(1, groups.total)
        assertEquals(1L, groups.items.single().keepAssetId)
        assertEquals(2, groups.items.single().memberCount)
        val candidates = engine.candidates(CandidateQuery(confidence = ConfidenceLevel.HIGH))
        assertEquals(2L, candidates.items.single().assetId)
        assertEquals(ReasonCode.EXACT_DUPLICATE, candidates.items.single().reasonCodes.single())
        val bitmap = engine.thumbnail(1L, 32)
        assertNotNull(bitmap)
        assertEquals(32, bitmap?.width)
        engine.release()
    }

    /**
     * Android 10 及以上走系统确认。确认后索引去掉照片，并留下可恢复记录。
     */
    @Test
    fun confirmedTrashUpdatesTheIndexWithoutDeletingAgain() {
        val index = AlbumIndex.open(context(), "trash.db")
        index.upsertAssets(listOf(photo(1L), photo(2L)))
        index.saveGroup(GroupRecord("g", "EXACT_DUPLICATE", keepAssetId = 1L, createdAt = 1L))
        index.replaceGroupMembers(
            "g",
            listOf(
                GroupMemberRecord("g", 1L, null, "EXACT_DUPLICATE"),
                GroupMemberRecord("g", 2L, null, "EXACT_DUPLICATE"),
            ),
        )
        val gateway = RecordingGateway()
        val deletion = Deletion(index, sdkInt = 30, gateway = gateway, now = { 100L })
        val request = deletion.request(listOf(2L))
        assertNotNull(request.intentSender)
        assertFalse(request.requiresExplicitWarning)
        val progress = deletion.commit(DeleteResult(Activity.RESULT_OK, null, request))
        assertEquals(1, progress.succeeded)
        assertEquals(0, gateway.directDeletes)
        assertNull(index.asset(2L))
        assertTrue(index.allGroups().isEmpty())
        assertEquals(20L, index.appliedBytes())
        val actionId = "delete:100:2"
        assertEquals(true, index.action(actionId)?.recoverable)
        val restore = deletion.restoreRequest(actionId)
        deletion.commitRestore(DeleteResult(Activity.RESULT_OK, null, restore))
        assertTrue(index.recyclesFor(actionId).single().restored)
        assertEquals("RESTORED", index.action(actionId)?.status)
    }

    /**
     * 没有系统确认框时直接删除。用户取消则索引不动。
     */
    @Test
    fun legacyDeleteWarnsAndUserCancelKeepsThePhoto() {
        val index = AlbumIndex.open(context(), "legacy.db")
        index.upsertAssets(listOf(photo(4L)))
        val gateway = RecordingGateway()
        val deletion = Deletion(index, sdkInt = 28, gateway = gateway, now = { 5L })
        val request = deletion.request(listOf(4L))
        assertNull(request.intentSender)
        assertTrue(request.requiresExplicitWarning)
        val cancelled = assertThrows(CleanerException::class.java) {
            deletion.commit(DeleteResult(Activity.RESULT_CANCELED, null, request))
        }
        assertEquals(ErrorCode.DELETE_REJECTED_BY_USER, cancelled.code)
        assertNotNull(index.asset(4L))
        val progress = deletion.commit(DeleteResult(Activity.RESULT_OK, null, request))
        assertEquals(1, progress.succeeded)
        assertEquals(1, gateway.directDeletes)
        assertNull(index.asset(4L))
        assertEquals(false, index.action("delete:5:4")?.recoverable)
    }

    /**
     * 不可恢复的删除不能再要恢复请求。
     */
    @Test
    fun unrestorableDeleteCannotBeRestored() {
        val index = AlbumIndex.open(context(), "norestore.db")
        index.upsertAssets(listOf(photo(7L)))
        val deletion = Deletion(index, sdkInt = 28, gateway = RecordingGateway(), now = { 9L })
        val request = deletion.request(listOf(7L))
        deletion.commit(DeleteResult(Activity.RESULT_OK, null, request))
        val error = assertThrows(CleanerException::class.java) {
            deletion.restoreRequest("delete:9:7")
        }
        assertEquals(ErrorCode.DELETE_UNSUPPORTED_ON_API, error.code)
    }
}

private fun context() = RuntimeEnvironment.getApplication()

private fun engine(index: AlbumIndex, sdkInt: Int, gateway: DeleteGateway): AlbumCleanerEngine {
    return AlbumCleanerEngine(
        openIndex = { index },
        thumbnails = ThumbnailSource { _, sizePx ->
            android.graphics.Bitmap.createBitmap(sizePx, sizePx, android.graphics.Bitmap.Config.ARGB_8888)
        },
        deletes = gateway,
        sdkInt = sdkInt,
        density = 1f,
    )
}

private fun photo(id: Long): AssetRecord {
    return AssetRecord(
        assetId = id,
        uri = "content://photo/$id",
        sizeBytes = 20L,
        width = 10,
        height = 10,
        dateAdded = 1L,
        dateModified = 1L,
        dateTaken = 1L,
        bucketId = "camera",
        relativePath = "DCIM/$id.jpg",
        isFavorite = false,
        mediaVersion = null,
        indexedAt = 1L,
    )
}

private fun unusedGateway(): DeleteGateway = RecordingGateway()

private class RecordingGateway : DeleteGateway {
    var directDeletes: Int = 0

    override fun createSystemRequest(uris: List<Uri>, trash: Boolean): IntentSender {
        val context = context()
        return PendingIntent.getActivity(
            context,
            0,
            Intent(),
            PendingIntent.FLAG_IMMUTABLE,
        ).intentSender
    }

    override fun deleteDirect(uris: List<Uri>) {
        directDeletes += 1
    }
}
