/**
 * 相册索引。批量写入按 400 行一个事务，翻页用主键游标。
 *
 * @file
 */
package com.arturoyi.albumcleaner.storage

import android.content.Context
import java.io.Closeable

/**
 * 一份本地索引。
 *
 * 调用串行进入。关闭之后不能再读写。打开失败或表不齐时抛 [IndexCorruptedException]。
 */
public class AlbumIndex private constructor(
    private val database: IndexDatabase,
) : Closeable {
    private val gate = Any()
    private val dao: IndexDao = database.index()
    private var closed: Boolean = false

    /**
     * 写入或更新照片。已有特征不会因为更新元数据被删掉。
     *
     * @param items 这一批照片。空列表直接返回。
     */
    public fun upsertAssets(items: List<AssetRecord>) {
        writeInBatches(items) { batch ->
            batch.forEach { asset ->
                require(asset.uri.isNotEmpty()) { "uri 不能为空" }
                val entity = asset.toEntity()
                dao.insertAssetIgnore(entity)
                dao.updateAsset(entity)
            }
        }
    }

    /**
     * 取主键大于 [cursorId] 的下一页，按主键升序。
     *
     * 第一页传 `-1`。
     *
     * @param cursorId 上一页最后一条的主键。
     * @param limit 这一页最多多少条，1 到 500。
     * @return 这一页照片。
     */
    public fun assetsAfter(cursorId: Long, limit: Int): List<AssetRecord> {
        checkLimit(limit)
        return read { dao.assetsAfter(cursorId, limit).map { entity -> entity.toRecord() } }
    }

    /**
     * 读取一张照片。
     *
     * @param assetId 主键。
     * @return 没有这条记录时为 null。
     */
    public fun asset(assetId: Long): AssetRecord? {
        return read { dao.asset(assetId)?.toRecord() }
    }

    /**
     * 删掉一张照片。特征和候选会跟着外键一起删掉。
     *
     * @param assetId 主键。
     */
    public fun deleteAsset(assetId: Long) {
        write { dao.deleteAsset(assetId) }
    }

    /**
     * 写入或更新特征。照片必须已经在 [upsertAssets] 里。
     *
     * @param items 这一批特征。
     */
    public fun upsertFeatures(items: List<FeatureRecord>) {
        writeInBatches(items) { batch ->
            batch.forEach { feature ->
                val entity = feature.toEntity()
                dao.insertFeatureIgnore(entity)
                dao.updateFeature(entity)
            }
        }
    }

    /**
     * 读取一张照片的特征。
     *
     * @param assetId 照片主键。
     * @return 还没计算时为 null。
     */
    public fun feature(assetId: Long): FeatureRecord? {
        return read { dao.feature(assetId)?.toRecord() }
    }

    /**
     * 写入或更新一个分组。
     *
     * @param group 分组。
     */
    public fun saveGroup(group: GroupRecord) {
        write {
            val entity = group.toEntity()
            dao.insertGroupIgnore(entity)
            dao.updateGroup(entity)
        }
    }

    /**
     * 用这一批分组换掉全部旧分组。
     *
     * 一次事务里先清空，再写入。成员必须属于 [groups]。
     *
     * @param groups 新的全部分组。
     * @param members 这些分组的成员。
     */
    public fun replaceAllGroups(groups: List<GroupRecord>, members: List<GroupMemberRecord>) {
        val ids = groups.map { group -> group.groupId }
        require(ids.distinct().size == ids.size) { "组标识重复" }
        require(members.all { member -> member.groupId in ids }) { "成员必须属于这次写入的组" }
        write {
            dao.deleteAllMembers()
            dao.deleteAllGroups()
            if (groups.isNotEmpty()) dao.insertGroups(groups.map { group -> group.toEntity() })
            if (members.isNotEmpty()) dao.insertMembers(members.map { member -> member.toEntity() })
        }
    }

    /**
     * 读取全部分组，按组标识升序。
     *
     * @return 当前的分组。
     */
    public fun allGroups(): List<GroupRecord> {
        return read { dao.allGroups().map { entity -> entity.toRecord() } }
    }

    /**
     * 用这一组的成员替换旧成员。
     *
     * @param groupId 组标识。
     * @param members 新的成员。组标识必须和 [groupId] 一致。
     */
    public fun replaceGroupMembers(groupId: String, members: List<GroupMemberRecord>) {
        require(members.all { it.groupId == groupId }) { "成员的 groupId 必须等于 $groupId" }
        write {
            dao.deleteMembersByGroup(groupId)
            if (members.isNotEmpty()) dao.insertMembers(members.map { member -> member.toEntity() })
        }
    }

    /**
     * 读取一组的成员，按照片主键升序。
     *
     * @param groupId 组标识。
     * @return 这一组的成员。
     */
    public fun groupMembers(groupId: String): List<GroupMemberRecord> {
        return read { dao.groupMembers(groupId).map { entity -> entity.toRecord() } }
    }

    /**
     * 用这一批候选换掉全部旧候选。
     *
     * @param items 新的全部候选。
     */
    public fun replaceCandidates(items: List<CandidateRecord>) {
        write {
            dao.deleteAllCandidates()
            if (items.isNotEmpty()) dao.insertCandidates(items.map { candidate -> candidate.toEntity() })
        }
    }

    /**
     * 取主键大于 [cursorId] 的下一页候选。
     *
     * 第一页传 `-1`。
     *
     * @param cursorId 上一页最后一条的主键。
     * @param limit 这一页最多多少条，1 到 500。
     * @return 这一页候选。
     */
    public fun candidatesAfter(cursorId: Long, limit: Int): List<CandidateRecord> {
        checkLimit(limit)
        return read { dao.candidatesAfter(cursorId, limit).map { entity -> entity.toRecord() } }
    }

    /**
     * 按把握和原因向后翻候选。
     *
     * [reasonSnippet] 会按 JSON 里的原因名匹配，例如 `EXACT_DUPLICATE`。
     *
     * @param cursorId 上一页最后一条的主键。第一页传 `-1`。
     * @param limit 这一页最多多少条，1 到 500。
     * @param confidence 只要这个把握。null 表示不限。
     * @param reasonSnippet 原因名。null 表示不限。
     */
    public fun candidatesMatching(
        cursorId: Long,
        limit: Int,
        confidence: String?,
        reasonSnippet: String?,
    ): List<CandidateRecord> {
        checkLimit(limit)
        return read {
            dao.candidatesMatching(cursorId, limit, confidence, reasonLike(reasonSnippet))
                .map { entity -> entity.toRecord() }
        }
    }

    /**
     * 符合条件的候选有多少条。
     */
    public fun countCandidates(confidence: String?, reasonSnippet: String?): Int {
        return read { dao.countCandidates(confidence, reasonLike(reasonSnippet)) }
    }

    /**
     * 按组标识向后翻分组。
     *
     * 第一页传空字符串。
     *
     * @param cursorId 上一页最后一条的组标识。
     * @param limit 这一页最多多少条，1 到 500。
     * @param type 只要这一种组。null 表示不限。
     */
    public fun groupsAfter(cursorId: String, limit: Int, type: String?): List<GroupRecord> {
        checkLimit(limit)
        return read { dao.groupsAfter(cursorId, limit, type).map { entity -> entity.toRecord() } }
    }

    /**
     * 符合条件的分组有多少个。
     */
    public fun countGroups(type: String?): Int {
        return read { dao.countGroups(type) }
    }

    /**
     * 把一张照片移出所有分组。组里不足两张时删掉整个组。
     *
     * 删掉的如果是建议留下的那张，改成剩下的成员里主键最小的。
     *
     * @param assetId 照片主键。
     */
    public fun detachFromGroups(assetId: Long) {
        write {
            val groupIds = dao.groupIdsOf(assetId)
            dao.deleteMembersByAsset(assetId)
            groupIds.forEach { groupId ->
                val count = dao.countMembers(groupId)
                if (count < 2L) {
                    dao.deleteMembersByGroup(groupId)
                    dao.deleteGroup(groupId)
                } else if (dao.keepAssetId(groupId) == assetId) {
                    dao.updateKeep(groupId, dao.minMember(groupId) ?: 0L)
                }
            }
        }
    }

    /**
     * 已经实际删除的字节数。
     */
    public fun appliedBytes(): Long {
        return read { metaLong(APPLIED_BYTES) }
    }

    /**
     * 把这次实际删除的字节数累加上去。
     *
     * @param bytes 增加的字节数。负数忽略。
     */
    public fun addAppliedBytes(bytes: Long) {
        if (bytes <= 0L) return
        write {
            val next = metaLong(APPLIED_BYTES) + bytes
            dao.upsertMeta(APPLIED_BYTES, next.toString())
        }
    }

    /**
     * 写入或更新扫描断点。
     *
     * @param session 断点。
     */
    public fun saveSession(session: ScanSessionRecord) {
        write {
            val entity = session.toEntity()
            dao.insertSessionIgnore(entity)
            dao.updateSession(entity)
        }
    }

    /**
     * 读取一轮扫描的断点。
     *
     * @param sessionId 扫描标识。
     * @return 没有这一轮时为 null。
     */
    public fun session(sessionId: String): ScanSessionRecord? {
        return read { dao.session(sessionId)?.toRecord() }
    }

    /**
     * 写入或更新一条删除动作。
     *
     * @param action 动作。
     */
    public fun saveAction(action: ActionRecord) {
        write {
            val entity = action.toEntity()
            dao.insertActionIgnore(entity)
            dao.updateAction(entity)
        }
    }

    /**
     * 读取一条删除动作。
     *
     * @param actionId 动作标识。
     * @return 没有这条动作时为 null。
     */
    public fun action(actionId: String): ActionRecord? {
        return read { dao.action(actionId)?.toRecord() }
    }

    /**
     * 写入或更新回收站的一行。
     *
     * @param entry 回收站记录。
     */
    public fun saveRecycle(entry: RecycleRecord) {
        write {
            val entity = entry.toEntity()
            dao.insertRecycleIgnore(entity)
            dao.updateRecycle(entity)
        }
    }

    /**
     * 按动作标识和照片主键向后翻回收站。
     *
     * 第一页传空字符串和 `-1`。
     *
     * @param actionId 上一页最后一条的动作标识。
     * @param assetId 上一页最后一条的照片主键。
     * @param limit 这一页最多多少条，1 到 500。
     * @return 这一页记录。
     */
    public fun recycleAfter(actionId: String, assetId: Long, limit: Int): List<RecycleRecord> {
        checkLimit(limit)
        return read { dao.recycleAfter(actionId, assetId, limit).map { entity -> entity.toRecord() } }
    }

    /**
     * 一次删除动作留在回收站里的记录。
     *
     * @param actionId 删除动作。
     */
    public fun recyclesFor(actionId: String): List<RecycleRecord> {
        return read { dao.recyclesFor(actionId).map { entity -> entity.toRecord() } }
    }

    /**
     * 写入或更新一条向量。语义聚类默认不用。
     *
     * @param embedding 向量。
     */
    public fun saveEmbedding(embedding: EmbeddingRecord) {
        write {
            val entity = embedding.toEntity()
            dao.insertEmbeddingIgnore(entity)
            dao.updateEmbedding(entity)
        }
    }

    /**
     * 读取一条向量。
     *
     * @param assetId 照片主键。
     * @return 还没有向量时为 null。
     */
    public fun embedding(assetId: Long): EmbeddingRecord? {
        return read { dao.embedding(assetId)?.toRecord() }
    }

    /**
     * 清掉照片、特征、分组、候选和扫描断点。
     *
     * `actions` 和 `recycle_bin` 保留。
     */
    public fun rebuild() {
        write {
            dao.deleteAllCandidates()
            dao.deleteAllMembers()
            dao.deleteAllGroups()
            dao.deleteAllEmbeddings()
            dao.deleteAllFeatures()
            dao.deleteAllAssets()
            dao.deleteAllSessions()
        }
    }

    /**
     * 读出 meta 里的 schema_version。
     *
     * @return 版本字符串。还没有记下时为 null。
     */
    public fun schemaVersion(): String? {
        return read { dao.meta(IndexSchema.SCHEMA_VERSION_KEY) }
    }

    /** 关闭数据库。可以重复调用。 */
    public override fun close() {
        synchronized(gate) {
            if (closed) return
            closed = true
            database.close()
        }
    }

    public companion object {
        /** 默认的数据库文件名。 */
        public const val DEFAULT_NAME: String = "album-cleaner.db"

        /**
         * 打开或创建索引。
         *
         * @param context 任意上下文，内部会换成应用上下文。
         * @param name 数据库文件名。
         * @return 已经通过校验的索引。
         * @throws IndexCorruptedException 文件不是合法库，或缺少表。
         */
        public fun open(context: Context, name: String = DEFAULT_NAME): AlbumIndex {
            val database = IndexDatabase.open(context.applicationContext, name)
            return try {
                AlbumIndex(database)
            } catch (error: Throwable) {
                database.close()
                throw error
            }
        }
    }

    private fun metaLong(key: String): Long {
        return dao.meta(key)?.toLongOrNull() ?: 0L
    }

    private fun <T> read(block: () -> T): T = access(block)

    private fun write(block: () -> Unit) {
        access { database.runInTransaction(block) }
    }

    private fun <T> writeInBatches(items: List<T>, writeBatch: (List<T>) -> Unit) {
        if (items.isEmpty()) return
        items.chunked(WRITE_BATCH).forEach { batch ->
            write { writeBatch(batch) }
        }
    }

    private fun <T> access(block: () -> T): T {
        synchronized(gate) {
            check(!closed) { "索引已经关闭" }
            return block()
        }
    }
}

private const val WRITE_BATCH: Int = 400
private const val APPLIED_BYTES: String = "applied_bytes"

private fun checkLimit(limit: Int) {
    require(limit in 1..WRITE_BATCH) { "limit 必须在 1 到 $WRITE_BATCH 之间" }
}

private fun reasonLike(snippet: String?): String? {
    if (snippet == null) return null
    return "%\"$snippet\"%"
}
