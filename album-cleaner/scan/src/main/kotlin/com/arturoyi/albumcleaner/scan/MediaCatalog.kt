/**
 * 媒体库里的一行元数据，以及按游标翻页的读取接口。
 *
 * @file
 */
package com.arturoyi.albumcleaner.scan

import android.database.Cursor
import android.net.Uri
import android.os.Bundle
import android.os.CancellationSignal
import com.arturoyi.albumcleaner.storage.AssetRecord

/**
 * 还没写入索引的一条媒体。
 *
 * 字段对应 [AssetRecord]，但没有 [AssetRecord.indexedAt]。写入时再盖时间戳。
 */
internal data class MediaRow(
    val assetId: Long,
    val uri: String,
    val sizeBytes: Long,
    val width: Int?,
    val height: Int?,
    val dateAdded: Long?,
    val dateModified: Long?,
    val dateTaken: Long?,
    val bucketId: String?,
    val relativePath: String?,
    val isFavorite: Boolean,
    val mediaVersion: Long?,
)

/**
 * 按主键向后翻媒体库。不解码。
 *
 * [pageAfter] 返回的是 `_id` 大于游标的下一页，按主键升序，并且已经按页大小截断。
 */
internal interface MediaCatalog {
    /**
     * 读取下一页。
     *
     * @param cursorId 已经处理过的最大主键。第一页传 `-1`。
     * @param limit 这一页最多多少条。
     * @param signal 取消时查询要停下来。
     * @return 这一页。没有更多时为空。
     */
    fun pageAfter(cursorId: Long, limit: Int, signal: CancellationSignal): List<MediaRow>

    /**
     * 媒体库里一共有多少条。
     *
     * @param signal 取消时查询要停下来。
     */
    fun total(signal: CancellationSignal): Int

    /**
     * 主键小于等于 [cursorId] 的有多少条。
     *
     * 游标还没开始时返回 0。
     *
     * @param signal 取消时查询要停下来。
     */
    fun countThrough(cursorId: Long, signal: CancellationSignal): Int
}

/**
 * 把一行媒体收成索引记录。
 *
 * @param indexedAt 写入索引的时间，Unix 毫秒。
 */
internal fun MediaRow.toAsset(indexedAt: Long): AssetRecord {
    return AssetRecord(
        assetId = assetId,
        uri = uri,
        sizeBytes = sizeBytes,
        width = width,
        height = height,
        dateAdded = dateAdded,
        dateModified = dateModified,
        dateTaken = dateTaken,
        bucketId = bucketId,
        relativePath = relativePath,
        isFavorite = isFavorite,
        mediaVersion = mediaVersion,
        indexedAt = indexedAt,
    )
}

/**
 * 媒体库查询没有返回游标。调用方不应把这一轮标成完成。
 */
internal class MediaStoreQueryException : RuntimeException("媒体库没有返回结果")

/**
 * 执行一次带取消信号的查询。测试可以换成内存里的游标。
 */
internal fun interface MediaQuery {
    /**
     * @param uri 图片或视频集合。
     * @param projection 要读的列。
     * @param args 过滤、排序和条数。
     * @param signal 取消信号。
     * @return 查询失败时为 null。
     */
    fun query(uri: Uri, projection: Array<String>, args: Bundle, signal: CancellationSignal): Cursor?
}
