/**
 * Room 里的索引表。列名和空值与原来的表一致，公开记录仍是 [AssetRecord] 这一组类型。
 *
 * @file
 */
package com.arturoyi.albumcleaner.storage

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * `meta` 的一行。
 *
 * @property key 键。
 * @property value 值。
 */
@Entity(tableName = "meta")
internal data class MetaEntity(
    @PrimaryKey @ColumnInfo(name = "key") public val key: String,
    @ColumnInfo(name = "value") public val value: String,
)

/**
 * `assets` 的一行。
 *
 * `is_favorite` 允许为空并默认 0，和已有库的列定义一致。
 */
@Entity(
    tableName = "assets",
    indices = [
        Index(value = ["date_added"], name = "idx_assets_date"),
        Index(value = ["bucket_id"], name = "idx_assets_bucket"),
        Index(value = ["size_bytes"], name = "idx_assets_size"),
    ],
)
internal data class AssetEntity(
    @PrimaryKey @ColumnInfo(name = "asset_id") public val assetId: Long,
    @ColumnInfo(name = "uri") public val uri: String,
    @ColumnInfo(name = "size_bytes") public val sizeBytes: Long,
    @ColumnInfo(name = "width") public val width: Int?,
    @ColumnInfo(name = "height") public val height: Int?,
    @ColumnInfo(name = "date_added") public val dateAdded: Long?,
    @ColumnInfo(name = "date_modified") public val dateModified: Long?,
    @ColumnInfo(name = "date_taken") public val dateTaken: Long?,
    @ColumnInfo(name = "bucket_id") public val bucketId: String?,
    @ColumnInfo(name = "relative_path") public val relativePath: String?,
    @ColumnInfo(name = "is_favorite", defaultValue = "0") public val isFavorite: Int?,
    @ColumnInfo(name = "media_version") public val mediaVersion: Long?,
    @ColumnInfo(name = "indexed_at") public val indexedAt: Long,
)

/**
 * `features` 的一行。照片删掉时这一行跟着删。
 */
@Entity(
    tableName = "features",
    foreignKeys = [
        ForeignKey(
            entity = AssetEntity::class,
            parentColumns = ["asset_id"],
            childColumns = ["asset_id"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [
        Index(value = ["byte_hash"], name = "idx_features_byte_hash"),
        Index(value = ["phash"], name = "idx_features_phash"),
        Index(value = ["content_fingerprint"], name = "idx_features_fingerprint"),
    ],
)
internal data class FeatureEntity(
    @PrimaryKey @ColumnInfo(name = "asset_id") public val assetId: Long,
    @ColumnInfo(name = "byte_hash") public val byteHash: String?,
    @ColumnInfo(name = "phash") public val phash: String?,
    @ColumnInfo(name = "dhash") public val dhash: String?,
    @ColumnInfo(name = "blur_score") public val blurScore: Double?,
    @ColumnInfo(name = "content_fingerprint") public val contentFingerprint: String,
    @ColumnInfo(name = "algorithm_version", defaultValue = "1") public val algorithmVersion: Int,
    @ColumnInfo(name = "computed_at") public val computedAt: Long,
)

/**
 * `embeddings` 的一行。照片删掉时这一行跟着删。
 */
@Entity(
    tableName = "embeddings",
    foreignKeys = [
        ForeignKey(
            entity = AssetEntity::class,
            parentColumns = ["asset_id"],
            childColumns = ["asset_id"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
)
internal data class EmbeddingEntity(
    @PrimaryKey @ColumnInfo(name = "asset_id") public val assetId: Long,
    @ColumnInfo(name = "vector_blob") public val vector: ByteArray,
    @ColumnInfo(name = "dim") public val dim: Int,
    @ColumnInfo(name = "model_version") public val modelVersion: String,
)

/**
 * `groups` 的一行。
 */
@Entity(tableName = "groups")
internal data class GroupEntity(
    @PrimaryKey @ColumnInfo(name = "group_id") public val groupId: String,
    @ColumnInfo(name = "type") public val type: String,
    @ColumnInfo(name = "keep_asset_id") public val keepAssetId: Long?,
    @ColumnInfo(name = "created_at") public val createdAt: Long,
)

/**
 * `group_members` 的一行。组删掉时成员跟着删。
 */
@Entity(
    tableName = "group_members",
    primaryKeys = ["group_id", "asset_id"],
    foreignKeys = [
        ForeignKey(
            entity = GroupEntity::class,
            parentColumns = ["group_id"],
            childColumns = ["group_id"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [
        Index(value = ["group_id"], name = "idx_group_members_group"),
    ],
)
internal data class GroupMemberEntity(
    @ColumnInfo(name = "group_id") public val groupId: String,
    @ColumnInfo(name = "asset_id") public val assetId: Long,
    @ColumnInfo(name = "score") public val score: Double?,
    @ColumnInfo(name = "reason_code") public val reasonCode: String?,
)

/**
 * `candidates` 的一行。照片删掉时这一行跟着删。
 */
@Entity(
    tableName = "candidates",
    foreignKeys = [
        ForeignKey(
            entity = AssetEntity::class,
            parentColumns = ["asset_id"],
            childColumns = ["asset_id"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [
        Index(
            value = ["confidence", "est_bytes", "asset_id"],
            orders = [Index.Order.ASC, Index.Order.DESC, Index.Order.ASC],
            name = "idx_candidates_rank",
        ),
    ],
)
internal data class CandidateEntity(
    @PrimaryKey @ColumnInfo(name = "asset_id") public val assetId: Long,
    @ColumnInfo(name = "group_id") public val groupId: String,
    @ColumnInfo(name = "confidence") public val confidence: String,
    @ColumnInfo(name = "reasons_json") public val reasonsJson: String,
    @ColumnInfo(name = "est_bytes") public val estimatedBytes: Long,
    @ColumnInfo(name = "computed_at") public val computedAt: Long,
)

/**
 * `scan_sessions` 的一行。
 */
@Entity(tableName = "scan_sessions")
internal data class ScanSessionEntity(
    @PrimaryKey @ColumnInfo(name = "session_id") public val sessionId: String,
    @ColumnInfo(name = "cursor_id") public val cursorId: Long?,
    @ColumnInfo(name = "phase") public val phase: String?,
    @ColumnInfo(name = "status") public val status: String?,
    @ColumnInfo(name = "updated_at") public val updatedAt: Long,
)

/**
 * `actions` 的一行。
 */
@Entity(tableName = "actions")
internal data class ActionEntity(
    @PrimaryKey @ColumnInfo(name = "action_id") public val actionId: String,
    @ColumnInfo(name = "type") public val type: String,
    @ColumnInfo(name = "asset_ids_json") public val assetIdsJson: String,
    @ColumnInfo(name = "status") public val status: String,
    @ColumnInfo(name = "created_at") public val createdAt: Long,
    @ColumnInfo(name = "recoverable") public val recoverable: Boolean,
)

/**
 * `recycle_bin` 的一行。`restored` 允许为空并默认 0。
 */
@Entity(
    tableName = "recycle_bin",
    primaryKeys = ["action_id", "asset_id"],
    indices = [
        Index(value = ["expires_at"], name = "idx_recycle_expiry"),
    ],
)
internal data class RecycleEntity(
    @ColumnInfo(name = "action_id") public val actionId: String,
    @ColumnInfo(name = "asset_id") public val assetId: Long,
    @ColumnInfo(name = "uri_snapshot") public val uriSnapshot: String?,
    @ColumnInfo(name = "thumb_path") public val thumbPath: String?,
    @ColumnInfo(name = "expires_at") public val expiresAt: Long?,
    @ColumnInfo(name = "restored", defaultValue = "0") public val restored: Int?,
)

internal fun AssetRecord.toEntity(): AssetEntity {
    return AssetEntity(
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
        isFavorite = if (isFavorite) 1 else 0,
        mediaVersion = mediaVersion,
        indexedAt = indexedAt,
    )
}

internal fun AssetEntity.toRecord(): AssetRecord {
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
        isFavorite = (isFavorite ?: 0) != 0,
        mediaVersion = mediaVersion,
        indexedAt = indexedAt,
    )
}

internal fun FeatureRecord.toEntity(): FeatureEntity {
    return FeatureEntity(
        assetId = assetId,
        byteHash = byteHash,
        phash = phash,
        dhash = dhash,
        blurScore = blurScore,
        contentFingerprint = contentFingerprint,
        algorithmVersion = algorithmVersion,
        computedAt = computedAt,
    )
}

internal fun FeatureEntity.toRecord(): FeatureRecord {
    return FeatureRecord(
        assetId = assetId,
        byteHash = byteHash,
        phash = phash,
        dhash = dhash,
        blurScore = blurScore,
        contentFingerprint = contentFingerprint,
        algorithmVersion = algorithmVersion,
        computedAt = computedAt,
    )
}

internal fun GroupRecord.toEntity(): GroupEntity {
    return GroupEntity(
        groupId = groupId,
        type = type,
        keepAssetId = keepAssetId,
        createdAt = createdAt,
    )
}

internal fun GroupEntity.toRecord(): GroupRecord {
    return GroupRecord(
        groupId = groupId,
        type = type,
        keepAssetId = keepAssetId,
        createdAt = createdAt,
    )
}

internal fun GroupMemberRecord.toEntity(): GroupMemberEntity {
    return GroupMemberEntity(
        groupId = groupId,
        assetId = assetId,
        score = score,
        reasonCode = reasonCode,
    )
}

internal fun GroupMemberEntity.toRecord(): GroupMemberRecord {
    return GroupMemberRecord(
        groupId = groupId,
        assetId = assetId,
        score = score,
        reasonCode = reasonCode,
    )
}

internal fun CandidateRecord.toEntity(): CandidateEntity {
    return CandidateEntity(
        assetId = assetId,
        groupId = groupId,
        confidence = confidence,
        reasonsJson = reasonsJson,
        estimatedBytes = estimatedBytes,
        computedAt = computedAt,
    )
}

internal fun CandidateEntity.toRecord(): CandidateRecord {
    return CandidateRecord(
        assetId = assetId,
        groupId = groupId,
        confidence = confidence,
        reasonsJson = reasonsJson,
        estimatedBytes = estimatedBytes,
        computedAt = computedAt,
    )
}

internal fun ScanSessionRecord.toEntity(): ScanSessionEntity {
    return ScanSessionEntity(
        sessionId = sessionId,
        cursorId = cursorId,
        phase = phase,
        status = status,
        updatedAt = updatedAt,
    )
}

internal fun ScanSessionEntity.toRecord(): ScanSessionRecord {
    return ScanSessionRecord(
        sessionId = sessionId,
        cursorId = cursorId,
        phase = phase,
        status = status,
        updatedAt = updatedAt,
    )
}

internal fun ActionRecord.toEntity(): ActionEntity {
    return ActionEntity(
        actionId = actionId,
        type = type,
        assetIdsJson = assetIdsJson,
        status = status,
        createdAt = createdAt,
        recoverable = recoverable,
    )
}

internal fun ActionEntity.toRecord(): ActionRecord {
    return ActionRecord(
        actionId = actionId,
        type = type,
        assetIdsJson = assetIdsJson,
        status = status,
        createdAt = createdAt,
        recoverable = recoverable,
    )
}

internal fun RecycleRecord.toEntity(): RecycleEntity {
    return RecycleEntity(
        actionId = actionId,
        assetId = assetId,
        uriSnapshot = uriSnapshot,
        thumbPath = thumbPath,
        expiresAt = expiresAt,
        restored = if (restored) 1 else 0,
    )
}

internal fun RecycleEntity.toRecord(): RecycleRecord {
    return RecycleRecord(
        actionId = actionId,
        assetId = assetId,
        uriSnapshot = uriSnapshot,
        thumbPath = thumbPath,
        expiresAt = expiresAt,
        restored = (restored ?: 0) != 0,
    )
}

internal fun EmbeddingRecord.toEntity(): EmbeddingEntity {
    return EmbeddingEntity(
        assetId = assetId,
        vector = vector,
        dim = dim,
        modelVersion = modelVersion,
    )
}

internal fun EmbeddingEntity.toRecord(): EmbeddingRecord {
    return EmbeddingRecord(
        assetId = assetId,
        vector = vector,
        dim = dim,
        modelVersion = modelVersion,
    )
}
