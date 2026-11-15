/**
 * 内存仓库和 Room 仓库都要通过的行为。
 *
 * @file
 */
package com.arturoyi.fastdowload.testing

import com.arturoyi.fastdowload.core.model.ContentFingerprint
import com.arturoyi.fastdowload.core.model.Destination
import com.arturoyi.fastdowload.core.model.DownloadBlock
import com.arturoyi.fastdowload.core.model.DownloadRecord
import com.arturoyi.fastdowload.core.model.DownloadRequest
import com.arturoyi.fastdowload.core.model.Priority
import com.arturoyi.fastdowload.core.spi.DownloadFilter
import com.arturoyi.fastdowload.core.spi.DownloadRepository
import com.arturoyi.fastdowload.core.state.Status
import java.io.File

/**
 * 检查仓库的读写、偏移、排序和启动清洗。
 */
public object RepositoryChecks {
    /**
     * 跑一遍两条实现必须一致的行为。
     *
     * @param repository 待检查的仓库。调用前应是空的。
     * @param root 临时的内部存储根目录。
     */
    public fun verify(repository: DownloadRepository, root: File) {
        val low = request("https://example.com/low.bin", "low.bin", Priority.LOW)
        val high = request("https://example.com/high.bin", "high.bin", Priority.HIGH)
        val lowId = repository.open(low).id
        Thread.sleep(2)
        val highId = repository.open(high).id
        val ordered = repository.query().map { it.id }
        check(ordered == listOf(highId, lowId)) { "排序应为高优先级在前，实际 $ordered" }

        repository.replaceBlocks(
            highId,
            listOf(DownloadBlock(highId, 0, 0, 100)),
        )
        repository.onFlushSuccess(highId, 0, 40)
        check(repository.downloaded(highId) == 40L) { "偏移应是 40" }
        check(repository.get(highId)?.status == Status.QUEUED) { "提交偏移不能改状态" }
        check(repository.blocksOf(highId).single().currentOffset == 40L)

        repository.saveFingerprint(
            highId,
            ContentFingerprint(eTag = "\"v1\"", contentLength = 100, acceptRanges = true),
        )
        check(repository.get(highId)?.totalBytes == 100L)

        repository.markDirty(highId)
        val downloading = repository.get(highId)!!.copy(status = Status.DOWNLOADING)
        repository.upsert(downloading)
        val report = repository.sanitizeOnStartup(root)
        check(report.cleared >= 1) { "脏任务应被清掉" }
        check(repository.get(highId)?.status == Status.QUEUED)
        check(repository.blocksOf(highId).isEmpty())
        check(!repository.isDirty(highId))

        val queued = repository.query(DownloadFilter(setOf(Status.QUEUED)))
        check(queued.all { it.status == Status.QUEUED })

        repository.transition(lowId, Status.REMOVED)
        check(repository.get(lowId)?.status == Status.REMOVED)
        val illegal = runCatching { repository.transition(lowId, Status.DOWNLOADING) }
        check(illegal.exceptionOrNull() is IllegalStateException) { "移除后的任务不能直接下载" }

        val persisted = repository.get(highId)!!
        repository.upsert(
            persisted.copy(
                retryAttempts = 2,
                request = persisted.request.copy(speedLimit = com.arturoyi.fastdowload.core.model.BytesPerSecond(1_000)),
            ),
        )
        val again = repository.get(highId)
        check(again?.retryAttempts == 2) { "重试次数应写回仓库" }
        check(again?.request?.speedLimit?.value == 1_000L) { "速度上限应写回仓库" }
    }

    private fun request(url: String, name: String, priority: Priority): DownloadRequest {
        return DownloadRequest(
            url = url,
            destination = Destination(name),
            priority = priority,
        )
    }
}
