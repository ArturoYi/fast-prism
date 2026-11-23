/**
 * 索引表的阻塞访问。写入由 [AlbumIndex] 包进事务。
 *
 * @file
 */
package com.arturoyi.albumcleaner.storage

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update

/**
 * 十张表的语句。冲突时忽略再更新，避免替换主键把特征和候选级联删掉。
 */
@Dao
internal interface IndexDao {
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    public fun insertAssetIgnore(entity: AssetEntity)

    @Update
    public fun updateAsset(entity: AssetEntity)

    @Query("DELETE FROM assets WHERE asset_id = :assetId")
    public fun deleteAsset(assetId: Long)

    @Query("DELETE FROM assets")
    public fun deleteAllAssets()

    @Query("SELECT * FROM assets WHERE asset_id = :assetId")
    public fun asset(assetId: Long): AssetEntity?

    @Query("SELECT * FROM assets WHERE asset_id > :cursorId ORDER BY asset_id ASC LIMIT :limit")
    public fun assetsAfter(cursorId: Long, limit: Int): List<AssetEntity>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    public fun insertFeatureIgnore(entity: FeatureEntity)

    @Update
    public fun updateFeature(entity: FeatureEntity)

    @Query("DELETE FROM features")
    public fun deleteAllFeatures()

    @Query("SELECT * FROM features WHERE asset_id = :assetId")
    public fun feature(assetId: Long): FeatureEntity?

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    public fun insertEmbeddingIgnore(entity: EmbeddingEntity)

    @Update
    public fun updateEmbedding(entity: EmbeddingEntity)

    @Query("DELETE FROM embeddings")
    public fun deleteAllEmbeddings()

    @Query("SELECT * FROM embeddings WHERE asset_id = :assetId")
    public fun embedding(assetId: Long): EmbeddingEntity?

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    public fun insertGroupIgnore(entity: GroupEntity)

    @Update
    public fun updateGroup(entity: GroupEntity)

    @Insert
    public fun insertGroups(items: List<GroupEntity>)

    @Query("DELETE FROM groups")
    public fun deleteAllGroups()

    @Query("DELETE FROM groups WHERE group_id = :groupId")
    public fun deleteGroup(groupId: String)

    @Query("SELECT * FROM groups ORDER BY group_id ASC")
    public fun allGroups(): List<GroupEntity>

    @Query(
        """
        SELECT * FROM groups
        WHERE group_id > :cursorId AND (:type IS NULL OR type = :type)
        ORDER BY group_id ASC
        LIMIT :limit
        """,
    )
    public fun groupsAfter(cursorId: String, limit: Int, type: String?): List<GroupEntity>

    @Query("SELECT COUNT(*) FROM groups WHERE (:type IS NULL OR type = :type)")
    public fun countGroups(type: String?): Int

    @Query("SELECT keep_asset_id FROM groups WHERE group_id = :groupId")
    public fun keepAssetId(groupId: String): Long?

    @Query("UPDATE groups SET keep_asset_id = :assetId WHERE group_id = :groupId")
    public fun updateKeep(groupId: String, assetId: Long)

    @Insert
    public fun insertMembers(items: List<GroupMemberEntity>)

    @Query("DELETE FROM group_members")
    public fun deleteAllMembers()

    @Query("DELETE FROM group_members WHERE group_id = :groupId")
    public fun deleteMembersByGroup(groupId: String)

    @Query("DELETE FROM group_members WHERE asset_id = :assetId")
    public fun deleteMembersByAsset(assetId: Long)

    @Query("SELECT * FROM group_members WHERE group_id = :groupId ORDER BY asset_id ASC")
    public fun groupMembers(groupId: String): List<GroupMemberEntity>

    @Query("SELECT group_id FROM group_members WHERE asset_id = :assetId")
    public fun groupIdsOf(assetId: Long): List<String>

    @Query("SELECT COUNT(*) FROM group_members WHERE group_id = :groupId")
    public fun countMembers(groupId: String): Long

    @Query("SELECT MIN(asset_id) FROM group_members WHERE group_id = :groupId")
    public fun minMember(groupId: String): Long?

    @Insert
    public fun insertCandidates(items: List<CandidateEntity>)

    @Query("DELETE FROM candidates")
    public fun deleteAllCandidates()

    @Query("SELECT * FROM candidates WHERE asset_id > :cursorId ORDER BY asset_id ASC LIMIT :limit")
    public fun candidatesAfter(cursorId: Long, limit: Int): List<CandidateEntity>

    @Query(
        """
        SELECT * FROM candidates
        WHERE asset_id > :cursorId
            AND (:confidence IS NULL OR confidence = :confidence)
            AND (:reasonLike IS NULL OR reasons_json LIKE :reasonLike)
        ORDER BY asset_id ASC
        LIMIT :limit
        """,
    )
    public fun candidatesMatching(
        cursorId: Long,
        limit: Int,
        confidence: String?,
        reasonLike: String?,
    ): List<CandidateEntity>

    @Query(
        """
        SELECT COUNT(*) FROM candidates
        WHERE (:confidence IS NULL OR confidence = :confidence)
            AND (:reasonLike IS NULL OR reasons_json LIKE :reasonLike)
        """,
    )
    public fun countCandidates(confidence: String?, reasonLike: String?): Int

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    public fun insertSessionIgnore(entity: ScanSessionEntity)

    @Update
    public fun updateSession(entity: ScanSessionEntity)

    @Query("DELETE FROM scan_sessions")
    public fun deleteAllSessions()

    @Query("SELECT * FROM scan_sessions WHERE session_id = :sessionId")
    public fun session(sessionId: String): ScanSessionEntity?

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    public fun insertActionIgnore(entity: ActionEntity)

    @Update
    public fun updateAction(entity: ActionEntity)

    @Query("SELECT * FROM actions WHERE action_id = :actionId")
    public fun action(actionId: String): ActionEntity?

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    public fun insertRecycleIgnore(entity: RecycleEntity)

    @Update
    public fun updateRecycle(entity: RecycleEntity)

    @Query(
        """
        SELECT * FROM recycle_bin
        WHERE action_id > :actionId OR (action_id = :actionId AND asset_id > :assetId)
        ORDER BY action_id ASC, asset_id ASC
        LIMIT :limit
        """,
    )
    public fun recycleAfter(actionId: String, assetId: Long, limit: Int): List<RecycleEntity>

    @Query("SELECT * FROM recycle_bin WHERE action_id = :actionId ORDER BY asset_id ASC")
    public fun recyclesFor(actionId: String): List<RecycleEntity>

    @Query("SELECT value FROM meta WHERE `key` = :key")
    public fun meta(key: String): String?

    @Query("INSERT OR REPLACE INTO meta(`key`, value) VALUES (:key, :value)")
    public fun upsertMeta(key: String, value: String)
}
