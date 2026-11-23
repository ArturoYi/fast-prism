/**
 * [MediaStoreCatalog] 的合并、列和取消。查询换成内存游标。
 *
 * @file
 */
package com.arturoyi.albumcleaner.scan

import android.content.ContentResolver
import android.content.ContentUris
import android.database.Cursor
import android.database.MatrixCursor
import android.net.Uri
import android.os.Bundle
import android.os.CancellationSignal
import android.os.OperationCanceledException
import android.provider.MediaStore
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * 不打开真实媒体库。
 */
@RunWith(RobolectricTestRunner::class)
class MediaStoreCatalogTest {
    /**
     * 图片和视频按主键合成一页。旧系统不读后来才有的列。
     */
    @Test
    @Config(sdk = [28])
    fun mergesImagesAndVideosOnOlderSdk() {
        val query = MemoryQuery(
            images = listOf(row(2L), row(4L)),
            videos = listOf(row(1L), row(3L)),
        )
        val catalog = MediaStoreCatalog(query)
        val first = catalog.pageAfter(-1L, 2, CancellationSignal())
        val second = catalog.pageAfter(first.last().assetId, 2, CancellationSignal())
        assertEquals(listOf(1L, 2L), first.map { it.assetId })
        assertEquals(listOf(3L, 4L), second.map { it.assetId })
        assertNull(first.first().relativePath)
        assertFalse(first.first().isFavorite)
        assertTrue(query.projections.flatten().none { it == MediaStore.MediaColumns.RELATIVE_PATH })
        assertTrue(query.selections.filterNotNull().none { it.contains("is_pending") })
    }

    /**
     * API 30 之后读相对路径、收藏和媒体版本，并跳过未完成的文件。
     */
    @Test
    @Config(sdk = [34])
    fun readsFavoriteAndSkipsPendingFlag() {
        val query = MemoryQuery(
            images = listOf(
                row(
                    7L,
                    MediaStore.MediaColumns.RELATIVE_PATH to "DCIM/",
                    MediaStore.MediaColumns.IS_FAVORITE to 1,
                    MediaStore.MediaColumns.GENERATION_MODIFIED to 4L,
                ),
            ),
            videos = emptyList(),
        )
        val catalog = MediaStoreCatalog(query)
        val page = catalog.pageAfter(-1L, 10, CancellationSignal())
        val item = page.single()
        assertEquals(ContentUris.withAppendedId(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, 7L).toString(), item.uri)
        assertEquals("DCIM/", item.relativePath)
        assertTrue(item.isFavorite)
        assertEquals(4L, item.mediaVersion)
        assertEquals(99L, item.sizeBytes)
        assertTrue(query.selections.filterNotNull().any { it.contains("is_pending") })
        assertEquals(1, catalog.total(CancellationSignal()))
        assertEquals(1, catalog.countThrough(7L, CancellationSignal()))
        assertEquals(0, catalog.countThrough(-1L, CancellationSignal()))
    }

    /**
     * 同一主键只留图片那条。
     */
    @Test
    @Config(sdk = [30])
    fun duplicateIdKeepsTheImage() {
        val query = MemoryQuery(images = listOf(row(5L)), videos = listOf(row(5L)))
        val page = MediaStoreCatalog(query).pageAfter(-1L, 10, CancellationSignal())
        assertEquals(1, page.size)
        assertEquals(
            ContentUris.withAppendedId(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, 5L).toString(),
            page.single().uri,
        )
    }

    /**
     * 提供方忽略条数限制时，仍然只取这一页。
     */
    @Test
    @Config(sdk = [30])
    fun capsRowsWhenTheProviderIgnoresLimit() {
        val query = MemoryQuery(
            images = (1L..8L).map { row(it) },
            videos = emptyList(),
            honorLimit = false,
        )
        val page = MediaStoreCatalog(query).pageAfter(-1L, 2, CancellationSignal())
        assertEquals(listOf(1L, 2L), page.map { it.assetId })
    }

    /**
     * 信号已经取消时不去查询。
     */
    @Test
    @Config(sdk = [30])
    fun cancelledSignalDoesNotQuery() {
        val query = MemoryQuery(images = listOf(row(1L)), videos = emptyList())
        val signal = CancellationSignal()
        signal.cancel()
        assertThrows(OperationCanceledException::class.java) {
            MediaStoreCatalog(query).pageAfter(-1L, 10, signal)
        }
        assertEquals(0, query.calls)
    }

    /**
     * 两个集合都没有游标时，不把这一轮当成已经扫完。
     */
    @Test
    @Config(sdk = [30])
    fun missingCursorIsAnError() {
        val catalog = MediaStoreCatalog(MediaQuery { _, _, _, _ -> null })
        assertThrows(MediaStoreQueryException::class.java) {
            catalog.pageAfter(-1L, 10, CancellationSignal())
        }
    }
}

private fun row(id: Long, vararg extra: Pair<String, Any?>): Map<String, Any?> {
    val values = mutableMapOf<String, Any?>(
        MediaStore.MediaColumns._ID to id,
        MediaStore.MediaColumns.SIZE to 99L,
        MediaStore.MediaColumns.WIDTH to 10,
        MediaStore.MediaColumns.HEIGHT to 20,
        MediaStore.MediaColumns.DATE_ADDED to 3L,
        MediaStore.MediaColumns.DATE_MODIFIED to 4L,
        MediaStore.Images.Media.DATE_TAKEN to 5L,
        MediaStore.Images.Media.BUCKET_ID to "camera",
    )
    extra.forEach { (key, value) -> values[key] = value }
    return values
}

private class MemoryQuery(
    private val images: List<Map<String, Any?>>,
    private val videos: List<Map<String, Any?>>,
    private val honorLimit: Boolean = true,
) : MediaQuery {
    val projections = mutableListOf<List<String>>()
    val selections = mutableListOf<String?>()
    var calls: Int = 0

    override fun query(uri: Uri, projection: Array<String>, args: Bundle, signal: CancellationSignal): Cursor? {
        calls += 1
        projections += projection.toList()
        val selection = args.getString(ContentResolver.QUERY_ARG_SQL_SELECTION)
        selections += selection
        val source = when (uri) {
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI -> images
            MediaStore.Video.Media.EXTERNAL_CONTENT_URI -> videos
            else -> return null
        }
        val bound = args.getStringArray(ContentResolver.QUERY_ARG_SQL_SELECTION_ARGS)?.firstOrNull()?.toLong()
        val filtered = filter(source, selection, bound)
        if (projection.size == 1 && projection[0].startsWith("COUNT")) {
            return MatrixCursor(projection).apply { addRow(arrayOf(filtered.size)) }
        }
        val limit = if (honorLimit) args.getInt(ContentResolver.QUERY_ARG_LIMIT, filtered.size) else filtered.size
        val page = filtered.sortedBy { it.getValue(MediaStore.MediaColumns._ID) as Long }.take(limit)
        val cursor = MatrixCursor(projection)
        page.forEach { item ->
            cursor.addRow(projection.map { column -> item[column] }.toTypedArray())
        }
        return cursor
    }

    private fun filter(source: List<Map<String, Any?>>, selection: String?, bound: Long?): List<Map<String, Any?>> {
        if (bound == null || selection == null) return source
        val id = MediaStore.MediaColumns._ID
        return if (selection.contains("<=")) {
            source.filter { (it.getValue(id) as Long) <= bound }
        } else if (selection.contains(">")) {
            source.filter { (it.getValue(id) as Long) > bound }
        } else {
            source
        }
    }
}
