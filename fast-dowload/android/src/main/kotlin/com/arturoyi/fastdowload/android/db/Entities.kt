/**
 * Room 里的四张表。
 *
 * @file
 */
package com.arturoyi.fastdowload.android.db

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * 一条下载任务。
 *
 * @property publicId 稳定标识。
 * @property url 资源地址。
 * @property destination 内部相对路径。
 * @property status 状态名。
 * @property priority 优先级数值。
 * @property groupId 分组。
 * @property networkType 网络要求的名字。
 * @property enqueueAction 重复入队时的动作名。
 * @property headers 编码后的请求头。
 * @property tags 编码后的标签。
 * @property connectionCount 连接数。`-1` 表示没指定。
 * @property bufferSize 读缓冲字节数。
 * @property downloadOnEnqueue 入队后是否马上排队。
 * @property totalBytes 资源总长度。未知时为 `-1`。
 * @property downloadedBytes 已记录的字节数。
 * @property etag 实体标签。
 * @property lastModified 最后修改时间原文。
 * @property acceptRanges 是否接受区间。
 * @property chunked 是否没有可靠总长度。
 * @property errorCode 失败数字码。没有失败时为 null。
 * @property errorMessage 失败说明。
 * @property createdAt 创建时间。
 * @property updatedAt 最近更新时间。
 */
@Entity(tableName = "download")
public data class DownloadEntity(
    @PrimaryKey @ColumnInfo(name = "public_id") public val publicId: String,
    public val url: String,
    public val destination: String,
    public val status: String,
    public val priority: Int,
    @ColumnInfo(name = "group_id") public val groupId: String,
    @ColumnInfo(name = "network_type") public val networkType: String,
    @ColumnInfo(name = "enqueue_action") public val enqueueAction: String,
    public val headers: String,
    public val tags: String,
    @ColumnInfo(name = "connection_count") public val connectionCount: Int,
    @ColumnInfo(name = "buffer_size") public val bufferSize: Int,
    @ColumnInfo(name = "download_on_enqueue") public val downloadOnEnqueue: Boolean,
    @ColumnInfo(name = "total_bytes") public val totalBytes: Long,
    @ColumnInfo(name = "downloaded_bytes") public val downloadedBytes: Long,
    public val etag: String?,
    @ColumnInfo(name = "last_modified") public val lastModified: String?,
    @ColumnInfo(name = "accept_ranges") public val acceptRanges: Boolean,
    public val chunked: Boolean,
    @ColumnInfo(name = "error_code") public val errorCode: Int?,
    @ColumnInfo(name = "error_message") public val errorMessage: String?,
    @ColumnInfo(name = "retry_attempts", defaultValue = "0") public val retryAttempts: Int,
    @ColumnInfo(name = "max_retry_attempts", defaultValue = "3") public val maxRetryAttempts: Int,
    @ColumnInfo(name = "retry_on_network", defaultValue = "1") public val retryOnNetwork: Boolean,
    @ColumnInfo(name = "speed_limit", defaultValue = "-1") public val speedLimit: Long,
    @ColumnInfo(name = "integrity", defaultValue = "") public val integrity: String,
    @ColumnInfo(name = "created_at") public val createdAt: Long,
    @ColumnInfo(name = "updated_at") public val updatedAt: Long,
)

/**
 * 一个分块的已写偏移。
 *
 * @property publicId 所属任务。
 * @property blockIndex 序号。
 * @property startOffset 文件中的起点。
 * @property contentLength 这一块的字节数。
 * @property currentOffset 已同步的字节数。
 */
@Entity(tableName = "block", primaryKeys = ["public_id", "block_index"])
public data class BlockEntity(
    @ColumnInfo(name = "public_id") public val publicId: String,
    @ColumnInfo(name = "block_index") public val blockIndex: Int,
    @ColumnInfo(name = "start_offset") public val startOffset: Long,
    @ColumnInfo(name = "content_length") public val contentLength: Long,
    @ColumnInfo(name = "current_offset") public val currentOffset: Long,
)

/**
 * 还没安全提交的任务。
 *
 * @property publicId 任务标识。
 * @property markedAt 标记时间。
 */
@Entity(tableName = "dirty_file")
public data class DirtyEntity(
    @PrimaryKey @ColumnInfo(name = "public_id") public val publicId: String,
    @ColumnInfo(name = "marked_at") public val markedAt: Long,
)

/**
 * 按地址记住的响应指纹。
 *
 * @property url 资源地址。
 * @property etag 实体标签。
 * @property lastModified 最后修改时间。
 * @property contentLength 总长度。
 * @property acceptRanges 是否接受区间。
 * @property chunked 是否分块且长度未知。
 * @property updatedAt 更新时间。
 */
@Entity(tableName = "response_meta")
public data class ResponseMetaEntity(
    @PrimaryKey public val url: String,
    public val etag: String?,
    @ColumnInfo(name = "last_modified") public val lastModified: String?,
    @ColumnInfo(name = "content_length") public val contentLength: Long,
    @ColumnInfo(name = "accept_ranges") public val acceptRanges: Boolean,
    public val chunked: Boolean,
    @ColumnInfo(name = "updated_at") public val updatedAt: Long,
)
