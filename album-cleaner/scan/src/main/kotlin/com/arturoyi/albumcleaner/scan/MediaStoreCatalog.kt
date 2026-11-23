/**
 * 从 MediaStore 按主键游标读取图片和视频的元数据。
 *
 * @file
 */
package com.arturoyi.albumcleaner.scan

import android.content.ContentResolver
import android.content.ContentUris
import android.database.Cursor
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.CancellationSignal
import android.os.OperationCanceledException
import android.provider.MediaStore

/**
 * 图片和视频两张表合并成一条主键序列。
 *
 * 只读元数据。不打开原图，也不抽视频帧。
 *
 * @param queries 实际执行查询的入口。
 */
internal class MediaStoreCatalog(
    private val queries: MediaQuery,
) : MediaCatalog {
    internal constructor(resolver: ContentResolver) : this(ContentResolverQuery(resolver))

    override fun pageAfter(cursorId: Long, limit: Int, signal: CancellationSignal): List<MediaRow> {
        throwIfCancelled(signal)
        val projection = projection()
        val args = queryArgs(idFilter(cursorId, atMost = false), limit)
        val images = queryRows(IMAGE_URI, projection, args, signal, limit)
        val videos = queryRows(VIDEO_URI, projection, args, signal, limit)
        return (images + videos)
            .sortedBy { row -> row.assetId }
            .distinctBy { row -> row.assetId }
            .take(limit)
    }

    override fun total(signal: CancellationSignal): Int {
        return count(cursorId = null, signal = signal)
    }

    override fun countThrough(cursorId: Long, signal: CancellationSignal): Int {
        if (cursorId < 0L) return 0
        return count(cursorId = cursorId, signal = signal)
    }

    private fun count(cursorId: Long?, signal: CancellationSignal): Int {
        throwIfCancelled(signal)
        val projection = arrayOf("COUNT(${MediaStore.MediaColumns._ID})")
        val args = queryArgs(idFilter(cursorId, atMost = true), limit = null)
        return countOne(IMAGE_URI, projection, args, signal) + countOne(VIDEO_URI, projection, args, signal)
    }

    private fun queryRows(
        uri: Uri,
        projection: Array<String>,
        args: Bundle,
        signal: CancellationSignal,
        limit: Int,
    ): List<MediaRow> {
        val cursor = queries.query(uri, projection, args, signal) ?: throw MediaStoreQueryException()
        return cursor.use { rows -> readLimited(rows, uri, limit) }
    }

    private fun countOne(uri: Uri, projection: Array<String>, args: Bundle, signal: CancellationSignal): Int {
        val cursor = queries.query(uri, projection, args, signal) ?: throw MediaStoreQueryException()
        return cursor.use { rows ->
            if (!rows.moveToFirst() || rows.isNull(0)) 0 else rows.getInt(0)
        }
    }

    private fun idFilter(cursorId: Long?, atMost: Boolean): Pair<String, Array<String>>? {
        if (cursorId == null || cursorId < 0L) return pendingClause()
        val operator = if (atMost) "<=" else ">"
        val idClause = "${MediaStore.MediaColumns._ID} $operator ?"
        val pending = pendingClause()
        if (pending == null) return idClause to arrayOf(cursorId.toString())
        return "$idClause AND ${pending.first}" to (arrayOf(cursorId.toString()) + pending.second)
    }

    private fun pendingClause(): Pair<String, Array<String>>? {
        if (Build.VERSION.SDK_INT < 29) return null
        val column = MediaStore.MediaColumns.IS_PENDING
        return "($column = 0 OR $column IS NULL)" to emptyArray()
    }

    private fun queryArgs(filter: Pair<String, Array<String>>?, limit: Int?): Bundle {
        return Bundle().apply {
            if (filter != null) {
                putString(ContentResolver.QUERY_ARG_SQL_SELECTION, filter.first)
                if (filter.second.isNotEmpty()) {
                    putStringArray(ContentResolver.QUERY_ARG_SQL_SELECTION_ARGS, filter.second)
                }
            }
            if (limit != null) {
                putInt(ContentResolver.QUERY_ARG_LIMIT, limit)
                putString(
                    ContentResolver.QUERY_ARG_SQL_SORT_ORDER,
                    "${MediaStore.MediaColumns._ID} ASC",
                )
            }
        }
    }

    private fun projection(): Array<String> {
        val columns = mutableListOf(
            MediaStore.MediaColumns._ID,
            MediaStore.MediaColumns.SIZE,
            MediaStore.MediaColumns.WIDTH,
            MediaStore.MediaColumns.HEIGHT,
            MediaStore.MediaColumns.DATE_ADDED,
            MediaStore.MediaColumns.DATE_MODIFIED,
            MediaStore.Images.Media.DATE_TAKEN,
            MediaStore.Images.Media.BUCKET_ID,
        )
        if (Build.VERSION.SDK_INT >= 29) {
            columns += MediaStore.MediaColumns.RELATIVE_PATH
        }
        if (Build.VERSION.SDK_INT >= 30) {
            columns += MediaStore.MediaColumns.IS_FAVORITE
            columns += MediaStore.MediaColumns.GENERATION_MODIFIED
        }
        return columns.toTypedArray()
    }

    private fun readLimited(cursor: Cursor, collection: Uri, limit: Int): List<MediaRow> {
        val rows = ArrayList<MediaRow>(limit)
        while (rows.size < limit && cursor.moveToNext()) {
            rows += cursor.toMediaRow(collection)
        }
        return rows
    }

    private fun throwIfCancelled(signal: CancellationSignal) {
        if (signal.isCanceled) throw OperationCanceledException()
    }

    private companion object {
        val IMAGE_URI: Uri = MediaStore.Images.Media.EXTERNAL_CONTENT_URI
        val VIDEO_URI: Uri = MediaStore.Video.Media.EXTERNAL_CONTENT_URI
    }
}

/**
 * 用 [ContentResolver] 执行查询。
 *
 * @param resolver 应用自己的解析器。
 */
private class ContentResolverQuery(
    private val resolver: ContentResolver,
) : MediaQuery {
    override fun query(uri: Uri, projection: Array<String>, args: Bundle, signal: CancellationSignal): Cursor? {
        return resolver.query(uri, projection, args, signal)
    }
}

private fun Cursor.toMediaRow(collection: Uri): MediaRow {
    val id = getLong(column(MediaStore.MediaColumns._ID))
    return MediaRow(
        assetId = id,
        uri = ContentUris.withAppendedId(collection, id).toString(),
        sizeBytes = longOrZero(MediaStore.MediaColumns.SIZE),
        width = optionalInt(MediaStore.MediaColumns.WIDTH),
        height = optionalInt(MediaStore.MediaColumns.HEIGHT),
        dateAdded = optionalLong(MediaStore.MediaColumns.DATE_ADDED),
        dateModified = optionalLong(MediaStore.MediaColumns.DATE_MODIFIED),
        dateTaken = optionalLong(MediaStore.Images.Media.DATE_TAKEN),
        bucketId = optionalString(MediaStore.Images.Media.BUCKET_ID),
        relativePath = optionalString(MediaStore.MediaColumns.RELATIVE_PATH),
        isFavorite = optionalInt(MediaStore.MediaColumns.IS_FAVORITE) == 1,
        mediaVersion = optionalLong(MediaStore.MediaColumns.GENERATION_MODIFIED),
    )
}

private fun Cursor.column(name: String): Int = getColumnIndexOrThrow(name)

private fun Cursor.optionalInt(name: String): Int? {
    val index = getColumnIndex(name)
    if (index < 0 || isNull(index)) return null
    return getInt(index)
}

private fun Cursor.optionalLong(name: String): Long? {
    val index = getColumnIndex(name)
    if (index < 0 || isNull(index)) return null
    return getLong(index)
}

private fun Cursor.optionalString(name: String): String? {
    val index = getColumnIndex(name)
    if (index < 0 || isNull(index)) return null
    return getString(index)
}

private fun Cursor.longOrZero(name: String): Long {
    return optionalLong(name) ?: 0L
}
