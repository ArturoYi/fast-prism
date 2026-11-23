/**
 * 索引里的一行。字段对应表列，不使用入口模块的类型。
 *
 * @file
 */
package com.arturoyi.albumcleaner.storage

/**
 * `assets` 的一行。
 *
 * @property assetId 媒体库 `_id`。
 * @property uri 内容地址原文。
 * @property sizeBytes 文件字节数。
 * @property width 像素宽。未知时为 null。
 * @property height 像素高。未知时为 null。
 * @property dateAdded 加入媒体库的时间，Unix 秒。未知时为 null。
 * @property dateModified 文件修改时间，Unix 秒。未知时为 null。
 * @property dateTaken 拍摄时间，Unix 秒。未知时为 null。
 * @property bucketId 相册桶。未知时为 null。
 * @property relativePath 相对路径。未知时为 null。
 * @property isFavorite 是否收藏。
 * @property mediaVersion 媒体版本。未知时为 null。
 * @property indexedAt 写入索引的时间，Unix 毫秒。
 */
public data class AssetRecord(
    public val assetId: Long,
    public val uri: String,
    public val sizeBytes: Long,
    public val width: Int?,
    public val height: Int?,
    public val dateAdded: Long?,
    public val dateModified: Long?,
    public val dateTaken: Long?,
    public val bucketId: String?,
    public val relativePath: String?,
    public val isFavorite: Boolean,
    public val mediaVersion: Long?,
    public val indexedAt: Long,
)

/**
 * `features` 的一行。
 *
 * @property assetId 对应的 [AssetRecord.assetId]。
 * @property byteHash 头尾局部哈希。同一大小里局部哈希撞车且全文不同时，改为全文 SHA-256。还没算时为 null。
 * @property phash 感知哈希的文本形式。还没算时为 null。
 * @property dhash 差分哈希的文本形式。还没算时为 null。
 * @property blurScore 清晰度。还没算时为 null。
 * @property contentFingerprint 内容指纹，用来判断要不要重算。
 * @property algorithmVersion 算出这些特征的算法版本。
 * @property computedAt 计算时间，Unix 毫秒。
 */
public data class FeatureRecord(
    public val assetId: Long,
    public val byteHash: String?,
    public val phash: String?,
    public val dhash: String?,
    public val blurScore: Double?,
    public val contentFingerprint: String,
    public val algorithmVersion: Int,
    public val computedAt: Long,
)

/**
 * `groups` 的一行。
 *
 * @property groupId 组标识。
 * @property type 分组依据的原文。
 * @property keepAssetId 建议留下的照片。还没选出时为 null。
 * @property createdAt 建组时间，Unix 毫秒。
 */
public data class GroupRecord(
    public val groupId: String,
    public val type: String,
    public val keepAssetId: Long?,
    public val createdAt: Long,
)

/**
 * `group_members` 的一行。
 *
 * @property groupId 所属组。
 * @property assetId 组员。
 * @property score 组内分数。还没算时为 null。
 * @property reasonCode 入组原因原文。没有时为 null。
 */
public data class GroupMemberRecord(
    public val groupId: String,
    public val assetId: Long,
    public val score: Double?,
    public val reasonCode: String?,
)

/**
 * `candidates` 的一行。
 *
 * @property assetId 候选照片。
 * @property groupId 所属组。
 * @property confidence 把握等级原文。
 * @property reasonsJson 原因列表的 JSON 原文。
 * @property estimatedBytes 预计可释放字节数。
 * @property computedAt 计算时间，Unix 毫秒。
 */
public data class CandidateRecord(
    public val assetId: Long,
    public val groupId: String,
    public val confidence: String,
    public val reasonsJson: String,
    public val estimatedBytes: Long,
    public val computedAt: Long,
)

/**
 * `scan_sessions` 的一行。
 *
 * @property sessionId 这一轮扫描的标识。
 * @property cursorId 已经处理到的照片。还没开始时为 null。
 * @property phase 阶段原文。未知时为 null。
 * @property status 会话状态原文。未知时为 null。
 * @property updatedAt 最近写入时间，Unix 毫秒。
 */
public data class ScanSessionRecord(
    public val sessionId: String,
    public val cursorId: Long?,
    public val phase: String?,
    public val status: String?,
    public val updatedAt: Long,
)

/**
 * `actions` 的一行。
 *
 * @property actionId 删除动作标识。
 * @property type 动作类型原文。
 * @property assetIdsJson 涉及照片的 JSON 原文。
 * @property status 动作状态原文。
 * @property createdAt 创建时间，Unix 毫秒。
 * @property recoverable 是否还能恢复。
 */
public data class ActionRecord(
    public val actionId: String,
    public val type: String,
    public val assetIdsJson: String,
    public val status: String,
    public val createdAt: Long,
    public val recoverable: Boolean,
)

/**
 * `recycle_bin` 的一行。
 *
 * @property actionId 对应的删除动作。
 * @property assetId 被移走的照片。
 * @property uriSnapshot 删除时的内容地址。没有时为 null。
 * @property thumbPath 缩略图快照路径。没有时为 null。
 * @property expiresAt 过期时间，Unix 毫秒。没有时为 null。
 * @property restored 是否已经恢复。
 */
public data class RecycleRecord(
    public val actionId: String,
    public val assetId: Long,
    public val uriSnapshot: String?,
    public val thumbPath: String?,
    public val expiresAt: Long?,
    public val restored: Boolean,
)

/**
 * `embeddings` 的一行。语义聚类默认不用这张表。
 *
 * @property assetId 对应的照片。
 * @property vector 向量字节。
 * @property dim 维度。
 * @property modelVersion 模型版本。
 */
public class EmbeddingRecord(
    public val assetId: Long,
    public val vector: ByteArray,
    public val dim: Int,
    public val modelVersion: String,
)
