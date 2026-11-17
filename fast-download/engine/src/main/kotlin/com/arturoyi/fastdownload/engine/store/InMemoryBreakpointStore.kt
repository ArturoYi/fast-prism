/**
 * 进程内的断点记录。
 *
 * 方法遵循 [com.arturoyi.fastdownload.core.spi.DownloadRepository]。Room 是另一份实现。
 *
 * @file
 */
package com.arturoyi.fastdownload.engine.store

import com.arturoyi.fastdownload.core.error.DownloadError
import com.arturoyi.fastdownload.core.model.ContentFingerprint
import com.arturoyi.fastdownload.core.model.Destination
import com.arturoyi.fastdownload.core.model.DownloadBlock
import com.arturoyi.fastdownload.core.model.DownloadRecord
import com.arturoyi.fastdownload.core.model.DownloadRequest
import com.arturoyi.fastdownload.core.model.PublicIds
import com.arturoyi.fastdownload.core.model.downloadPartFile
import com.arturoyi.fastdownload.core.model.inScheduleOrder
import com.arturoyi.fastdownload.core.spi.DownloadFilter
import com.arturoyi.fastdownload.core.state.SanitizePolicy
import com.arturoyi.fastdownload.core.state.SanitizeReport
import com.arturoyi.fastdownload.core.state.StateMachine
import com.arturoyi.fastdownload.core.state.Status
import com.arturoyi.fastdownload.engine.trial.TrialBaseline
import java.io.File

/**
 * 用内存保存任务、分块、指纹和脏标记。
 *
 * 进程结束后内容消失。调用方要模拟崩溃时，先取出快照再放进一个新实例。
 * 偏移只应在文件同步成功之后增加。
 */
public class InMemoryBreakpointStore : BreakpointStore {
    private val monitor = Any()
    private val records = HashMap<String, DownloadRecord>()
    private val blocks = HashMap<String, List<DownloadBlock>>()
    private val fingerprints = HashMap<String, ContentFingerprint>()
    private val dirty = HashSet<String>()
    private var flushObserver: (() -> Unit)? = null

    /**
     * 取出已有任务，没有则按 [request] 新建一条排队记录。
     *
     * 标识由地址、相对路径和分组算出来。分组在这里当作命名空间。
     *
     * @param request 下载请求。
     * @return 已有或新建的记录。
     */
    public override fun open(request: DownloadRequest): DownloadRecord {
        val id = PublicIds.generate(request.url, request.destination, request.groupId)
        return synchronized(monitor) {
            records[id] ?: DownloadRecord(
                id = id,
                request = request,
                status = Status.QUEUED,
                createdAt = now(),
                updatedAt = now(),
            ).also { records[id] = it }
        }
    }

    /**
     * 读取任务。
     *
     * @param publicId 任务标识。
     * @return 没有这条任务时为 null。
     */
    public override fun get(publicId: String): DownloadRecord? = synchronized(monitor) { records[publicId] }

    /**
     * 读取分块的副本。
     *
     * @param publicId 任务标识。
     * @return 按当前保存顺序排列的分块。没有分块时为空列表。
     */
    public override fun blocksOf(publicId: String): List<DownloadBlock> {
        return synchronized(monitor) { blocks[publicId].orEmpty().toList() }
    }

    /**
     * 各分块已写入字节之和。
     *
     * @param publicId 任务标识。
     * @return 没有分块时为 0。
     */
    public override fun downloaded(publicId: String): Long {
        return synchronized(monitor) { blocks[publicId].orEmpty().sumOf { it.currentOffset } }
    }

    /**
     * 用分块和指纹拼出试连要用的本地进度。
     *
     * @param publicId 任务标识。
     * @return 没有记录时，已写字节为 0、总长度未知。
     */
    public override fun baseline(publicId: String): TrialBaseline {
        return synchronized(monitor) {
            val fingerprint = fingerprints[publicId]
            TrialBaseline(
                downloadedBytes = blocks[publicId].orEmpty().sumOf { it.currentOffset },
                totalBytes = records[publicId]?.totalBytes ?: -1L,
                eTag = fingerprint?.eTag,
                lastModified = fingerprint?.lastModified,
            )
        }
    }

    /**
     * 保存试连得到的指纹，并记下资源总长度。
     *
     * 总长度为负时记成未知。
     *
     * @param publicId 任务标识。任务必须已经 [open]。
     * @param fingerprint 本次试连的指纹。
     */
    public override fun saveFingerprint(publicId: String, fingerprint: ContentFingerprint) {
        synchronized(monitor) {
            val record = records.getValue(publicId)
            fingerprints[publicId] = fingerprint
            val total = fingerprint.contentLength.takeIf { it >= 0L } ?: -1L
            records[publicId] = record.copy(totalBytes = total, updatedAt = touched(record))
        }
    }

    /**
     * 替换全部分块，并用它们的已写字节更新任务进度。
     *
     * @param publicId 任务标识。
     * @param blocks 新的分块。每一块的记录标识必须是 [publicId]。
     * @throws IllegalArgumentException 分块不属于这条任务，或任务不存在。
     */
    public override fun replaceBlocks(publicId: String, blocks: List<DownloadBlock>) {
        require(blocks.all { it.recordId == publicId }) { "分块不属于该任务" }
        synchronized(monitor) {
            val record = records.getValue(publicId)
            this.blocks[publicId] = blocks.sortedBy { it.index }
            records[publicId] = record.copy(
                downloadedBytes = blocks.sumOf { it.currentOffset },
                updatedAt = touched(record),
            )
        }
    }

    /**
     * 在文件同步成功之后，把一个分块的已写偏移增加 [delta]。
     *
     * 这是偏移前进的写入口。同步失败时不要调用。
     *
     * @param publicId 任务标识。
     * @param blockIndex 分块序号。
     * @param delta 这次同步成功的字节数，必须大于 0。
     * @throws IllegalArgumentException [delta] 不是正数，或增加后超出该分块长度。
     * @throws IllegalStateException 任务或分块不存在。
     */
    public override fun onFlushSuccess(publicId: String, blockIndex: Int, delta: Long) {
        require(delta > 0L) { "delta 必须大于 0" }
        synchronized(monitor) {
            val current = blocks[publicId] ?: error("没有分块")
            val position = current.indexOfFirst { it.index == blockIndex }
            check(position >= 0) { "分块不存在" }
            val updated = current[position].copy(currentOffset = current[position].currentOffset + delta)
            val next = current.toMutableList()
            next[position] = updated
            blocks[publicId] = next
            val record = records.getValue(publicId)
            records[publicId] = record.copy(
                downloadedBytes = next.sumOf { it.currentOffset },
                updatedAt = touched(record),
            )
        }
        flushObserver?.invoke()
    }

    /**
     * 偏移提交成功后通知 [observer]。
     *
     * @param observer 新的回调。null 表示取消。
     */
    public override fun setFlushObserver(observer: (() -> Unit)?) {
        flushObserver = observer
    }

    /**
     * 清空分块和指纹，并把已写字节归零。
     *
     * 同时标成脏文件。资源变化后必须从头写，不能沿用旧偏移。
     *
     * @param publicId 任务标识。
     */
    public override fun clearBlocks(publicId: String) {
        synchronized(monitor) {
            blocks[publicId] = emptyList()
            fingerprints.remove(publicId)
            dirty.add(publicId)
            val record = records[publicId] ?: return
            records[publicId] = record.copy(
                downloadedBytes = 0L,
                totalBytes = -1L,
                updatedAt = touched(record),
            )
        }
    }

    /**
     * 标记文件在完成前可能不完整。
     *
     * @param publicId 任务标识。
     */
    public override fun markDirty(publicId: String) {
        synchronized(monitor) { dirty.add(publicId) }
    }

    /**
     * 文件已经原子替换到最终路径后，去掉脏标记。
     *
     * @param publicId 任务标识。
     */
    public override fun clearDirty(publicId: String) {
        synchronized(monitor) { dirty.remove(publicId) }
    }

    /**
     * 这条任务是否被标成脏文件。
     *
     * @param publicId 任务标识。
     * @return 需要从头再写时为 true。
     */
    public override fun isDirty(publicId: String): Boolean = synchronized(monitor) { publicId in dirty }

    /**
     * 把状态改成正在下载。
     *
     * @param publicId 任务标识。
     */
    public override fun markDownloading(publicId: String) {
        update(publicId) { it.copy(status = Status.DOWNLOADING, error = null) }
    }

    /**
     * 把状态改成完成，并清掉错误。
     *
     * @param publicId 任务标识。
     */
    public override fun markCompleted(publicId: String) {
        update(publicId) { it.copy(status = Status.COMPLETED, error = null) }
    }

    /**
     * 把状态改成失败，并记下 [error]。分块保留，供下一次续传。
     *
     * @param publicId 任务标识。
     * @param error 这次失败。
     */
    public override fun markFailed(publicId: String, error: DownloadError) {
        update(publicId) { it.copy(status = Status.FAILED, error = error) }
    }

    /**
     * 复制当前内存状态。
     *
     * 新实例和原实例之后互不影响，用来表示进程退出前已经提交的断点。
     *
     * @return 含有相同记录、分块、指纹和脏标记的新存储。
     */
    public fun snapshot(): InMemoryBreakpointStore {
        val copy = InMemoryBreakpointStore()
        synchronized(monitor) {
            copy.records.putAll(records)
            blocks.forEach { (id, list) -> copy.blocks[id] = list.toList() }
            copy.fingerprints.putAll(fingerprints)
            copy.dirty.addAll(dirty)
        }
        return copy
    }

    /**
     * 任务最终要写入的相对路径。
     *
     * @param publicId 任务标识。
     * @return 请求里的目标。任务不存在时为 null。
     */
    public fun destination(publicId: String): Destination? {
        return synchronized(monitor) { records[publicId]?.request?.destination }
    }

    /**
     * 插入或整体替换任务，不走状态白名单。
     *
     * @param record 要保存的任务。
     */
    public override fun upsert(record: DownloadRecord) {
        synchronized(monitor) {
            records[record.id] = record
        }
    }

    /**
     * 列出任务并排序。
     *
     * @param filter 状态筛选。
     * @return 优先级高的在前，同优先级创建更早的在前。
     */
    public override fun query(filter: DownloadFilter): List<DownloadRecord> {
        return synchronized(monitor) {
            records.values
                .filter { record -> filter.statuses.isEmpty() || record.status in filter.statuses }
                .toList()
                .inScheduleOrder()
        }
    }

    /**
     * 按白名单修改状态。
     *
     * @param publicId 任务标识。
     * @param to 目标状态。
     * @param error 失败时附带的错误。离开失败状态时清掉错误。
     * @return 更新后的任务。
     */
    public override fun transition(publicId: String, to: Status, error: DownloadError?): DownloadRecord {
        return synchronized(monitor) {
            val record = records.getValue(publicId)
            StateMachine.requireTransition(record.status, to)
            val nextError = if (to == Status.FAILED) error else null
            val updated = record.copy(status = to, error = nextError, updatedAt = touched(record))
            records[publicId] = updated
            updated
        }
    }

    /**
     * 按文件和脏标记修正全部任务。
     *
     * @param storageRoot 内部存储根目录。
     * @return 改动计数。
     */
    public override fun sanitizeOnStartup(storageRoot: File): SanitizeReport {
        return transaction {
            var requeued = 0
            var cleared = 0
            var zeroed = 0
            records.values.toList().forEach { record ->
                val part = downloadPartFile(storageRoot, record.request.destination, record.id)
                val target = record.request.destination.resolveInside(storageRoot)
                val decision = SanitizePolicy.decide(
                    status = record.status,
                    dirty = record.id in dirty,
                    downloadedBytes = downloaded(record.id),
                    targetExists = target.isFile,
                    partExists = part.isFile,
                )
                if (decision.deletePart) part.delete()
                if (decision.clearBlocks) clearBlocks(record.id)
                if (decision.zeroProgress) {
                    val current = records.getValue(record.id)
                    records[record.id] = current.copy(downloadedBytes = 0L, totalBytes = -1L, updatedAt = touched(current))
                    zeroed += 1
                }
                if (decision.status != records.getValue(record.id).status) {
                    val current = records.getValue(record.id)
                    records[record.id] = current.copy(status = decision.status, updatedAt = touched(current))
                }
                if (decision.status == Status.QUEUED &&
                    (record.status == Status.DOWNLOADING || record.status == Status.WAITING_NETWORK || decision.clearBlocks)
                ) {
                    requeued += 1
                }
                if (decision.clearBlocks) {
                    dirty.remove(record.id)
                    cleared += 1
                }
            }
            SanitizeReport(requeued = requeued, cleared = cleared, zeroed = zeroed)
        }
    }

    /**
     * 用同一把锁包住 [block]。
     *
     * @param block 要一起完成的写入。
     * @return [block] 的结果。
     */
    public override fun <T> transaction(block: () -> T): T = synchronized(monitor) { block() }

    private fun update(publicId: String, transform: (DownloadRecord) -> DownloadRecord) {
        synchronized(monitor) {
            val record = records.getValue(publicId)
            records[publicId] = transform(record).copy(updatedAt = touched(record))
        }
    }

    private fun touched(record: DownloadRecord): Long = now().coerceAtLeast(record.updatedAt)

    private fun now(): Long = System.currentTimeMillis()
}
