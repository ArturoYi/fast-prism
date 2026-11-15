/**
 * 任务、分块、脏标记和响应指纹的查询。
 *
 * @file
 */
package com.arturoyi.fastdowload.android.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

/**
 * 四张表的阻塞访问。调用方不要在界面线程使用。
 */
@Dao
public interface DownloadDao {
    /**
     * 读取一条任务。
     *
     * @param publicId 任务标识。
     * @return 没有时为 null。
     */
    @Query("SELECT * FROM download WHERE public_id = :publicId")
    public fun find(publicId: String): DownloadEntity?

    /**
     * 读取全部任务。
     *
     * @return 表里的每一行。
     */
    @Query("SELECT * FROM download")
    public fun all(): List<DownloadEntity>

    /**
     * 插入或替换任务。
     *
     * @param entity 要保存的行。
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    public fun insert(entity: DownloadEntity)

    /**
     * 读取一个任务的分块。
     *
     * @param publicId 任务标识。
     * @return 按序号排列的分块。
     */
    @Query("SELECT * FROM block WHERE public_id = :publicId ORDER BY block_index ASC")
    public fun blocks(publicId: String): List<BlockEntity>

    /**
     * 删掉一个任务的全部分块。
     *
     * @param publicId 任务标识。
     */
    @Query("DELETE FROM block WHERE public_id = :publicId")
    public fun deleteBlocks(publicId: String)

    /**
     * 插入或替换分块。
     *
     * @param blocks 要保存的分块。
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    public fun insertBlocks(blocks: List<BlockEntity>)

    /**
     * 读取一个分块。
     *
     * @param publicId 任务标识。
     * @param blockIndex 序号。
     * @return 没有时为 null。
     */
    @Query("SELECT * FROM block WHERE public_id = :publicId AND block_index = :blockIndex")
    public fun block(publicId: String, blockIndex: Int): BlockEntity?

    /**
     * 更新一个分块的已写偏移。
     *
     * @param publicId 任务标识。
     * @param blockIndex 序号。
     * @param currentOffset 新的偏移。
     */
    @Query(
        "UPDATE block SET current_offset = :currentOffset " +
            "WHERE public_id = :publicId AND block_index = :blockIndex",
    )
    public fun updateOffset(publicId: String, blockIndex: Int, currentOffset: Long)

    /**
     * 是否标成脏文件。
     *
     * @param publicId 任务标识。
     * @return 有标记时为 1。
     */
    @Query("SELECT COUNT(*) FROM dirty_file WHERE public_id = :publicId")
    public fun dirtyCount(publicId: String): Int

    /**
     * 记下脏文件。
     *
     * @param entity 标记。
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    public fun insertDirty(entity: DirtyEntity)

    /**
     * 去掉脏标记。
     *
     * @param publicId 任务标识。
     */
    @Query("DELETE FROM dirty_file WHERE public_id = :publicId")
    public fun deleteDirty(publicId: String)

    /**
     * 记住这个地址的响应指纹。
     *
     * @param entity 指纹行。
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    public fun insertMeta(entity: ResponseMetaEntity)

    /**
     * 只改状态和错误，不动已写字节。
     *
     * @param publicId 任务标识。
     * @param status 新状态名。
     * @param errorCode 失败数字码。离开失败状态时为 null。
     * @param errorMessage 失败说明。离开失败状态时为 null。
     * @param updatedAt 更新时间。
     */
    @Query(
        "UPDATE download SET status = :status, error_code = :errorCode, " +
            "error_message = :errorMessage, updated_at = :updatedAt WHERE public_id = :publicId",
    )
    public fun updateStatus(
        publicId: String,
        status: String,
        errorCode: Int?,
        errorMessage: String?,
        updatedAt: Long,
    )

    /**
     * 只改已写字节，不动状态。
     *
     * @param publicId 任务标识。
     * @param downloadedBytes 各分块偏移之和。
     * @param updatedAt 更新时间。
     */
    @Query(
        "UPDATE download SET downloaded_bytes = :downloadedBytes, updated_at = :updatedAt " +
            "WHERE public_id = :publicId",
    )
    public fun updateDownloaded(publicId: String, downloadedBytes: Long, updatedAt: Long)

    /**
     * 只改试连指纹，不动状态。
     *
     * @param publicId 任务标识。
     * @param totalBytes 资源总长度。
     * @param etag 实体标签。
     * @param lastModified 最后修改时间。
     * @param acceptRanges 是否接受区间。
     * @param chunked 是否没有可靠总长度。
     * @param updatedAt 更新时间。
     */
    @Query(
        "UPDATE download SET total_bytes = :totalBytes, etag = :etag, last_modified = :lastModified, " +
            "accept_ranges = :acceptRanges, chunked = :chunked, updated_at = :updatedAt " +
            "WHERE public_id = :publicId",
    )
    public fun updateFingerprint(
        publicId: String,
        totalBytes: Long,
        etag: String?,
        lastModified: String?,
        acceptRanges: Boolean,
        chunked: Boolean,
        updatedAt: Long,
    )

    /**
     * 清掉进度和指纹，不动状态。
     *
     * @param publicId 任务标识。
     * @param updatedAt 更新时间。
     */
    @Query(
        "UPDATE download SET downloaded_bytes = 0, total_bytes = -1, etag = NULL, " +
            "last_modified = NULL, updated_at = :updatedAt WHERE public_id = :publicId",
    )
    public fun clearProgress(publicId: String, updatedAt: Long)
}
