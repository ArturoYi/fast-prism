/**
 * 索引的建表、批量写入、游标翻页、级联删除、重建和损坏。
 *
 * @file
 */
package com.arturoyi.albumcleaner.storage

import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/**
 * [AlbumIndex] 打开真实的索引库。
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class AlbumIndexTest {
    /**
     * 新建库带齐表，并记下 schema_version。
     */
    @Test
    fun createsSchemaVersion() {
        val index = AlbumIndex.open(context(), "fresh.db")
        index.use {
            assertEquals(IndexSchema.VERSION.toString(), it.schemaVersion())
        }
    }

    /**
     * 450 张跨过 400 行事务边界后，仍能按主键逐页读完。更新元数据不会删掉特征。
     */
    @Test
    fun pagesAssetsAndKeepsFeaturesWhenMetadataChanges() {
        val index = AlbumIndex.open(context(), "pages.db")
        index.use {
            val assets = (1..450).map { id -> asset(id.toLong(), size = id.toLong()) }
            it.upsertAssets(assets)
            it.upsertFeatures(listOf(feature(1L)))
            val seen = mutableListOf<Long>()
            var cursor = -1L
            while (true) {
                val page = it.assetsAfter(cursor, limit = 100)
                if (page.isEmpty()) break
                seen += page.map { record -> record.assetId }
                cursor = page.last().assetId
            }
            assertEquals((1L..450L).toList(), seen)
            it.upsertAssets(listOf(asset(1L, size = 9L)))
            assertEquals(9L, it.asset(1L)?.sizeBytes)
            assertEquals("finger-1", it.feature(1L)?.contentFingerprint)
            it.deleteAsset(1L)
            assertNull(it.asset(1L))
            assertNull(it.feature(1L))
        }
    }

    /**
     * 候选按主键向后翻。替换会清掉上一批。
     */
    @Test
    fun replacesAndPagesCandidates() {
        val index = AlbumIndex.open(context(), "candidates.db")
        index.use {
            it.upsertAssets(listOf(asset(1L), asset(2L), asset(3L)))
            it.replaceCandidates(
                listOf(candidate(1L), candidate(2L)),
            )
            it.replaceCandidates(listOf(candidate(3L, confidence = "LOW")))
            assertEquals(listOf(3L), it.candidatesAfter(-1L, limit = 10).map { record -> record.assetId })
            assertEquals("LOW", it.candidatesAfter(-1L, limit = 10).single().confidence)
        }
    }

    /**
     * 断点、分组、删除记录和向量都能写回原值。
     */
    @Test
    fun storesSessionGroupActionAndEmbedding() {
        val index = AlbumIndex.open(context(), "rows.db")
        index.use {
            it.upsertAssets(listOf(asset(7L)))
            it.saveSession(
                ScanSessionRecord(
                    sessionId = "scan-1",
                    cursorId = 7L,
                    phase = "QUERY",
                    status = "RUNNING",
                    updatedAt = 20L,
                ),
            )
            it.saveGroup(GroupRecord("g1", "EXACT_DUPLICATE", keepAssetId = 7L, createdAt = 3L))
            it.replaceGroupMembers(
                "g1",
                listOf(GroupMemberRecord("g1", 7L, score = 0.5, reasonCode = "EXACT_DUPLICATE")),
            )
            it.saveAction(
                ActionRecord(
                    actionId = "a1",
                    type = "DELETE",
                    assetIdsJson = "[7]",
                    status = "APPLIED",
                    createdAt = 4L,
                    recoverable = true,
                ),
            )
            it.saveEmbedding(EmbeddingRecord(7L, byteArrayOf(1, 2, 3), dim = 3, modelVersion = "m1"))
            assertEquals(3, it.embedding(7L)?.dim)
            assertEquals("m1", it.embedding(7L)?.modelVersion)
            val session = it.session("scan-1")
            assertEquals(7L, session?.cursorId)
            assertEquals("QUERY", session?.phase)
            assertEquals(listOf(7L), it.groupMembers("g1").map { member -> member.assetId })
            assertEquals(true, it.action("a1")?.recoverable)
        }
    }

    /**
     * 重建清掉照片和断点，留下删除记录和回收站。
     */
    @Test
    fun rebuildKeepsHistory() {
        val index = AlbumIndex.open(context(), "rebuild.db")
        index.use {
            it.upsertAssets(listOf(asset(4L)))
            it.saveSession(
                ScanSessionRecord("scan-1", cursorId = 4L, phase = "QUERY", status = "RUNNING", updatedAt = 1L),
            )
            it.saveAction(
                ActionRecord("a1", "DELETE", "[4]", "APPLIED", createdAt = 2L, recoverable = false),
            )
            it.saveRecycle(
                RecycleRecord(
                    actionId = "a1",
                    assetId = 4L,
                    uriSnapshot = "content://photo/4",
                    thumbPath = null,
                    expiresAt = 99L,
                    restored = false,
                ),
            )
            it.rebuild()
            assertNull(it.asset(4L))
            assertNull(it.session("scan-1"))
            assertEquals("APPLIED", it.action("a1")?.status)
            val recycle = it.recycleAfter("", -1L, limit = 10)
            assertEquals(1, recycle.size)
            assertEquals(4L, recycle.single().assetId)
            assertEquals(99L, recycle.single().expiresAt)
        }
    }

    /**
     * 缺表的库报损坏。系统如果删掉坏文件并重建，不把它当成成功。
     */
    @Test
    fun missingTablesAreCorrupted() {
        val context = context()
        AlbumIndex.open(context, "drop.db").close()
        val opened = IndexDatabase.build(context, "drop.db")
        try {
            opened.openHelper.writableDatabase.execSQL("DROP TABLE assets")
        } finally {
            opened.close()
        }
        assertThrows(IndexCorruptedException::class.java) {
            AlbumIndex.open(context, "drop.db")
        }

        val callback = object : SupportSQLiteOpenHelper.Callback(IndexSchema.VERSION) {
            override fun onCreate(db: SupportSQLiteDatabase) = Unit
            override fun onUpgrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit
        }
        val configuration = SupportSQLiteOpenHelper.Configuration.builder(context)
            .name("empty.db")
            .callback(callback)
            .build()
        FrameworkSQLiteOpenHelperFactory().create(configuration).use { helper ->
            helper.writableDatabase.close()
        }
        assertThrows(IndexCorruptedException::class.java) {
            AlbumIndex.open(context, "empty.db")
        }
    }

    /**
     * 迁移语句不能删掉回收站或删除记录。普通删除照片的语句可以。
     */
    @Test
    fun migrationCannotDropHistory() {
        assertThrows(IllegalStateException::class.java) {
            IndexSchema.ensureKeepsHistory("DROP TABLE recycle_bin")
        }
        assertThrows(IllegalStateException::class.java) {
            IndexSchema.ensureKeepsHistory("drop   table   if exists actions")
        }
        IndexSchema.ensureKeepsHistory("DELETE FROM assets")
    }

    /**
     * 同一条记录再写一遍，内容保持最后一次的值。
     */
    @Test
    fun writingTheSameAssetAgainKeepsTheLatestValues() {
        val index = AlbumIndex.open(context(), "repeat.db")
        index.use {
            it.upsertAssets(listOf(asset(6L, size = 1L)))
            it.upsertAssets(listOf(asset(6L, size = 1L)))
            it.upsertAssets(listOf(asset(6L, size = 2L)))
            assertEquals(2L, it.asset(6L)?.sizeBytes)
            assertEquals(listOf(6L), it.assetsAfter(-1L, limit = 10).map { record -> record.assetId })
        }
    }
}

private fun context() = RuntimeEnvironment.getApplication()

private fun asset(id: Long, size: Long = id): AssetRecord {
    return AssetRecord(
        assetId = id,
        uri = "content://photo/$id",
        sizeBytes = size,
        width = 10,
        height = 20,
        dateAdded = 1L,
        dateModified = 2L,
        dateTaken = null,
        bucketId = "bucket",
        relativePath = "DCIM/$id.jpg",
        isFavorite = id == 1L,
        mediaVersion = null,
        indexedAt = 3L,
    )
}

private fun feature(id: Long): FeatureRecord {
    return FeatureRecord(
        assetId = id,
        byteHash = "byte-$id",
        phash = null,
        dhash = null,
        blurScore = 12.0,
        contentFingerprint = "finger-$id",
        algorithmVersion = 1,
        computedAt = 4L,
    )
}

private fun candidate(id: Long, confidence: String = "HIGH"): CandidateRecord {
    return CandidateRecord(
        assetId = id,
        groupId = "g",
        confidence = confidence,
        reasonsJson = "[]",
        estimatedBytes = 10L,
        computedAt = 5L,
    )
}
