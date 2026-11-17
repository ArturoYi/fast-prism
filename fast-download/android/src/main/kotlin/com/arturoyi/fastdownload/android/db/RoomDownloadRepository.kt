/**
 * 用 Room 保存任务和断点。
 *
 * @file
 */
package com.arturoyi.fastdownload.android.db

import android.content.Context
import com.arturoyi.fastdownload.core.error.DownloadError
import com.arturoyi.fastdownload.core.error.ErrorCode
import com.arturoyi.fastdownload.core.model.BytesPerSecond
import com.arturoyi.fastdownload.core.model.ContentFingerprint
import com.arturoyi.fastdownload.core.model.Destination
import com.arturoyi.fastdownload.core.model.DownloadBlock
import com.arturoyi.fastdownload.core.model.DownloadRecord
import com.arturoyi.fastdownload.core.model.DownloadRequest
import com.arturoyi.fastdownload.core.model.EnqueueAction
import com.arturoyi.fastdownload.core.model.NetworkType
import com.arturoyi.fastdownload.core.model.Priority
import com.arturoyi.fastdownload.core.model.PublicIds
import com.arturoyi.fastdownload.core.model.RetryPolicy
import com.arturoyi.fastdownload.core.model.decodeIntegrity
import com.arturoyi.fastdownload.core.model.encoded
import com.arturoyi.fastdownload.core.model.downloadPartFile
import com.arturoyi.fastdownload.core.model.inScheduleOrder
import com.arturoyi.fastdownload.core.spi.DownloadFilter
import com.arturoyi.fastdownload.core.state.SanitizePolicy
import com.arturoyi.fastdownload.core.state.SanitizeReport
import com.arturoyi.fastdownload.core.state.StateMachine
import com.arturoyi.fastdownload.core.state.Status
import com.arturoyi.fastdownload.engine.store.BreakpointStore
import com.arturoyi.fastdownload.engine.trial.TrialBaseline
import java.io.File

/**
 * [BreakpointStore] 的 Room 实现。
 *
 * 数据库文件在应用私有目录。方法会阻塞到事务结束。
 *
 * @param context 用来打开数据库。
 */
public class RoomDownloadRepository(
    context: Context,
    name: String = "fastdownload.db",
) : BreakpointStore {
    private val database = DownloadDatabase.create(context, name)
    private val dao = database.downloads()
    private var flushObserver: (() -> Unit)? = null

    /**
     * 整体替换一条任务。
     *
     * @param record 要保存的任务。
     */
    public override fun upsert(record: DownloadRecord) {
        val existing = dao.find(record.id)
        dao.insert(record.toEntity(existing))
    }

    /**
     * 读取任务。
     *
     * @param publicId 任务标识。
     * @return 没有时为 null。
     */
    public override fun get(publicId: String): DownloadRecord? = dao.find(publicId)?.toRecord()

    /**
     * 按筛选列出并排序。
     *
     * @param filter 状态筛选。
     * @return 调度顺序的任务。
     */
    public override fun query(filter: DownloadFilter): List<DownloadRecord> {
        return dao.all()
            .map { it.toRecord() }
            .filter { record -> filter.statuses.isEmpty() || record.status in filter.statuses }
            .inScheduleOrder()
    }

    /**
     * 取出或新建排队任务。
     *
     * @param request 下载请求。
     * @return 已有或新建的任务。
     */
    public override fun open(request: DownloadRequest): DownloadRecord {
        val id = PublicIds.generate(request.url, request.destination, request.groupId)
        dao.find(id)?.let { return it.toRecord() }
        val now = System.currentTimeMillis()
        val created = DownloadRecord(
            id = id,
            request = request,
            status = Status.QUEUED,
            createdAt = now,
            updatedAt = now,
        )
        dao.insert(created.toEntity(existing = null))
        return created
    }

    /**
     * 替换全部分块，并按偏移合计更新已写字节。
     *
     * @param publicId 任务标识。
     * @param blocks 新分块。
     */
    public override fun replaceBlocks(publicId: String, blocks: List<DownloadBlock>) {
        database.runInTransaction {
            dao.deleteBlocks(publicId)
            dao.insertBlocks(blocks.map { it.toEntity() })
            val entity = requireEntity(publicId)
            dao.updateDownloaded(
                publicId,
                blocks.sumOf { it.currentOffset },
                System.currentTimeMillis().coerceAtLeast(entity.updatedAt),
            )
        }
    }

    /**
     * 读取分块。
     *
     * @param publicId 任务标识。
     * @return 按序号排列的分块。
     */
    public override fun blocksOf(publicId: String): List<DownloadBlock> {
        return dao.blocks(publicId).map { it.toBlock() }
    }

    /**
     * 同步成功后增加偏移。
     *
     * @param publicId 任务标识。
     * @param blockIndex 分块序号。
     * @param delta 新字节数。
     */
    public override fun onFlushSuccess(publicId: String, blockIndex: Int, delta: Long) {
        require(delta > 0L) { "delta 必须大于 0" }
        database.runInTransaction {
            val block = dao.block(publicId, blockIndex) ?: error("分块不存在")
            val updated = block.currentOffset + delta
            require(updated <= block.contentLength) { "偏移超出分块" }
            dao.updateOffset(publicId, blockIndex, updated)
            val downloaded = dao.blocks(publicId).sumOf { it.currentOffset }
            val entity = requireEntity(publicId)
            dao.updateDownloaded(
                publicId,
                downloaded,
                System.currentTimeMillis().coerceAtLeast(entity.updatedAt),
            )
        }
        flushObserver?.invoke()
    }

    /**
     * 保存指纹。
     *
     * @param publicId 任务标识。
     * @param fingerprint 试连指纹。
     */
    public override fun saveFingerprint(publicId: String, fingerprint: ContentFingerprint) {
        database.runInTransaction {
            val entity = requireEntity(publicId)
            val total = fingerprint.contentLength.takeIf { it >= 0L } ?: -1L
            dao.updateFingerprint(
                publicId = publicId,
                totalBytes = total,
                etag = fingerprint.eTag,
                lastModified = fingerprint.lastModified,
                acceptRanges = fingerprint.acceptRanges,
                chunked = fingerprint.isChunked,
                updatedAt = System.currentTimeMillis().coerceAtLeast(entity.updatedAt),
            )
            dao.insertMeta(
                ResponseMetaEntity(
                    url = entity.url,
                    etag = fingerprint.eTag,
                    lastModified = fingerprint.lastModified,
                    contentLength = fingerprint.contentLength,
                    acceptRanges = fingerprint.acceptRanges,
                    chunked = fingerprint.isChunked,
                    updatedAt = System.currentTimeMillis(),
                ),
            )
        }
    }

    /**
     * 分块偏移之和。
     *
     * @param publicId 任务标识。
     * @return 没有分块时为 0。
     */
    public override fun downloaded(publicId: String): Long {
        return dao.blocks(publicId).sumOf { it.currentOffset }
    }

    /**
     * 标记脏文件。
     *
     * @param publicId 任务标识。
     */
    public override fun markDirty(publicId: String) {
        dao.insertDirty(DirtyEntity(publicId, System.currentTimeMillis()))
    }

    /**
     * 去掉脏标记。
     *
     * @param publicId 任务标识。
     */
    public override fun clearDirty(publicId: String) {
        dao.deleteDirty(publicId)
    }

    /**
     * 是否为脏文件。
     *
     * @param publicId 任务标识。
     * @return 有标记时为 true。
     */
    public override fun isDirty(publicId: String): Boolean = dao.dirtyCount(publicId) > 0

    /**
     * 清掉分块并归零进度，同时标成脏文件。
     *
     * @param publicId 任务标识。
     */
    public override fun clearBlocks(publicId: String) {
        database.runInTransaction {
            dao.deleteBlocks(publicId)
            val entity = dao.find(publicId) ?: return@runInTransaction
            dao.clearProgress(publicId, System.currentTimeMillis().coerceAtLeast(entity.updatedAt))
            dao.insertDirty(DirtyEntity(publicId, System.currentTimeMillis()))
        }
    }

    /**
     * 按白名单改状态。
     *
     * @param publicId 任务标识。
     * @param to 目标状态。
     * @param error 失败时的错误。
     * @return 更新后的任务。
     */
    public override fun transition(publicId: String, to: Status, error: DownloadError?): DownloadRecord {
        val entity = requireEntity(publicId)
        val from = Status.valueOf(entity.status)
        StateMachine.requireTransition(from, to)
        val updatedAt = System.currentTimeMillis().coerceAtLeast(entity.updatedAt)
        dao.updateStatus(
            publicId = publicId,
            status = to.name,
            errorCode = if (to == Status.FAILED) error?.code?.code else null,
            errorMessage = if (to == Status.FAILED) error?.message else null,
            updatedAt = updatedAt,
        )
        return requireEntity(publicId).toRecord()
    }

    /**
     * 修正残留任务。
     *
     * @param storageRoot 内部存储根目录。
     * @return 改动计数。
     */
    public override fun sanitizeOnStartup(storageRoot: File): SanitizeReport {
        return transaction {
            var requeued = 0
            var cleared = 0
            var zeroed = 0
            dao.all().forEach { entity ->
                val record = entity.toRecord()
                val part = downloadPartFile(storageRoot, record.request.destination, record.id)
                val target = record.request.destination.resolveInside(storageRoot)
                val decision = SanitizePolicy.decide(
                    status = record.status,
                    dirty = isDirty(record.id),
                    downloadedBytes = downloaded(record.id),
                    targetExists = target.isFile,
                    partExists = part.isFile,
                )
                if (decision.deletePart) part.delete()
                if (decision.clearBlocks) clearBlocks(record.id)
                val current = requireEntity(record.id)
                var next = current
                if (decision.zeroProgress) {
                    next = next.copy(downloadedBytes = 0L, totalBytes = -1L)
                    zeroed += 1
                }
                if (decision.status.name != next.status) {
                    next = next.copy(status = decision.status.name)
                }
                if (next != current) {
                    dao.insert(next.copy(updatedAt = System.currentTimeMillis().coerceAtLeast(current.updatedAt)))
                }
                if (decision.clearBlocks) {
                    clearDirty(record.id)
                    cleared += 1
                }
                if (decision.status == Status.QUEUED &&
                    (record.status == Status.DOWNLOADING || record.status == Status.WAITING_NETWORK || decision.clearBlocks)
                ) {
                    requeued += 1
                }
            }
            SanitizeReport(requeued, cleared, zeroed)
        }
    }

    /**
     * 在一个数据库事务里运行 [block]。
     *
     * @param block 要一起成功的写入。
     * @return [block] 的结果。
     */
    public override fun <T> transaction(block: () -> T): T = database.runInTransaction(block)

    /**
     * 偏移提交成功后通知 [observer]。
     *
     * @param observer 新的回调。null 表示取消。
     */
    public override fun setFlushObserver(observer: (() -> Unit)?) {
        flushObserver = observer
    }

    /**
     * 拼出试连用的本地进度。
     *
     * @param publicId 任务标识。
     * @return 没有任务时进度为 0。
     */
    /**
     * 直接标成正在下载。
     *
     * @param publicId 任务标识。
     */
    public override fun markDownloading(publicId: String) {
        setStatus(publicId, Status.DOWNLOADING, error = null)
    }

    /**
     * 直接标成完成。
     *
     * @param publicId 任务标识。
     */
    public override fun markCompleted(publicId: String) {
        setStatus(publicId, Status.COMPLETED, error = null)
    }

    /**
     * 直接标成失败。
     *
     * @param publicId 任务标识。
     * @param error 失败内容。
     */
    public override fun markFailed(publicId: String, error: DownloadError) {
        setStatus(publicId, Status.FAILED, error)
    }

    public override fun baseline(publicId: String): TrialBaseline {
        val entity = dao.find(publicId)
        return TrialBaseline(
            downloadedBytes = downloaded(publicId),
            totalBytes = entity?.totalBytes ?: -1L,
            eTag = entity?.etag,
            lastModified = entity?.lastModified,
        )
    }

    private fun setStatus(publicId: String, status: Status, error: DownloadError?) {
        val entity = requireEntity(publicId)
        dao.updateStatus(
            publicId = publicId,
            status = status.name,
            errorCode = error?.code?.code,
            errorMessage = error?.message,
            updatedAt = System.currentTimeMillis().coerceAtLeast(entity.updatedAt),
        )
    }

    private fun requireEntity(publicId: String): DownloadEntity {
        return dao.find(publicId) ?: error("任务不存在")
    }
}

private fun DownloadRecord.toEntity(existing: DownloadEntity?): DownloadEntity {
    return DownloadEntity(
        publicId = id,
        url = request.url,
        destination = request.destination.relativePath,
        status = status.name,
        priority = request.priority.value,
        groupId = request.groupId,
        networkType = request.networkType.name,
        enqueueAction = request.enqueueAction.name,
        headers = encodeMap(request.headers),
        tags = encodeMap(request.tags),
        connectionCount = request.connectionCount ?: -1,
        bufferSize = request.bufferSize,
        downloadOnEnqueue = request.downloadOnEnqueue,
        totalBytes = totalBytes,
        downloadedBytes = downloadedBytes,
        etag = existing?.etag,
        lastModified = existing?.lastModified,
        acceptRanges = existing?.acceptRanges ?: false,
        chunked = existing?.chunked ?: false,
        errorCode = error?.code?.code,
        errorMessage = error?.message,
        retryAttempts = retryAttempts,
        maxRetryAttempts = request.retryPolicy.maxAttempts,
        retryOnNetwork = request.retryPolicy.retryOnNetworkGain,
        speedLimit = request.speedLimit?.value ?: -1L,
        integrity = request.integrity?.encoded().orEmpty(),
        createdAt = createdAt,
        updatedAt = updatedAt,
    )
}

private fun DownloadEntity.toRecord(): DownloadRecord {
    val code = errorCode?.let { stored -> ErrorCode.entries.firstOrNull { it.code == stored } }
    return DownloadRecord(
        id = publicId,
        request = DownloadRequest(
            url = url,
            destination = Destination(destination),
            headers = decodeMap(headers),
            priority = Priority.entries.first { it.value == priority },
            groupId = groupId,
            tags = decodeMap(tags),
            networkType = NetworkType.valueOf(networkType),
            enqueueAction = EnqueueAction.valueOf(enqueueAction),
            connectionCount = connectionCount.takeIf { it > 0 },
            bufferSize = bufferSize,
            downloadOnEnqueue = downloadOnEnqueue,
            speedLimit = speedLimit.takeIf { it > 0L }?.let(::BytesPerSecond),
            integrity = decodeIntegrity(integrity),
            retryPolicy = RetryPolicy(
                maxAttempts = maxRetryAttempts,
                retryOnNetworkGain = retryOnNetwork,
            ),
        ),
        status = Status.valueOf(status),
        createdAt = createdAt,
        updatedAt = updatedAt,
        totalBytes = totalBytes,
        downloadedBytes = downloadedBytes,
        error = if (code != null && errorMessage != null) {
            DownloadError(code = code, message = errorMessage, traceId = publicId)
        } else {
            null
        },
        retryAttempts = retryAttempts,
    )
}

private fun DownloadBlock.toEntity(): BlockEntity {
    return BlockEntity(
        publicId = recordId,
        blockIndex = index,
        startOffset = startOffset,
        contentLength = contentLength,
        currentOffset = currentOffset,
    )
}

private fun BlockEntity.toBlock(): DownloadBlock {
    return DownloadBlock(
        recordId = publicId,
        index = blockIndex,
        startOffset = startOffset,
        contentLength = contentLength,
        currentOffset = currentOffset,
    )
}

private fun encodeMap(values: Map<String, String>): String {
    return values.entries.joinToString("\u0001") { (key, value) ->
        escape(key) + "\u0002" + escape(value)
    }
}

private fun decodeMap(encoded: String): Map<String, String> {
    if (encoded.isEmpty()) return emptyMap()
    return encoded.split('\u0001').associate { pair ->
        val parts = pair.split('\u0002', limit = 2)
        unescape(parts[0]) to unescape(parts.getOrElse(1) { "" })
    }
}

private fun escape(text: String): String {
    return text.replace("\\", "\\\\").replace("\u0001", "\\1").replace("\u0002", "\\2")
}

private fun unescape(text: String): String {
    val out = StringBuilder()
    var index = 0
    while (index < text.length) {
        if (text[index] == '\\' && index + 1 < text.length) {
            when (text[index + 1]) {
                '\\' -> out.append('\\')
                '1' -> out.append('\u0001')
                '2' -> out.append('\u0002')
                else -> out.append(text[index + 1])
            }
            index += 2
        } else {
            out.append(text[index])
            index += 1
        }
    }
    return out.toString()
}
