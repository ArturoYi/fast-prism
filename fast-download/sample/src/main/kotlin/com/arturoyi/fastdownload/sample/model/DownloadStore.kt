/**
 * 进程内的任务列表。
 *
 * 快照负责任务进出，进度回调负责字节和速度。三个页面的 ViewModel 都读这一份。
 *
 * @file
 */
package com.arturoyi.fastdownload.sample.model

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.arturoyi.fastdownload.api.DownloadListener
import com.arturoyi.fastdownload.api.FastDownloader
import com.arturoyi.fastdownload.api.SnapshotListener
import com.arturoyi.fastdownload.api.downloadRequest
import com.arturoyi.fastdownload.core.model.DownloadRecord
import com.arturoyi.fastdownload.core.model.EnqueueAction
import com.arturoyi.fastdownload.core.model.Progress
import com.arturoyi.fastdownload.core.spi.DownloadFilter
import com.arturoyi.fastdownload.core.state.Status
import com.arturoyi.fastdownload.sample.DOWNLOAD_DIRECTORY

/**
 * 持有当前任务，并把暂停、继续、删除转给下载器。
 *
 * 在 [com.arturoyi.fastdownload.sample.SampleApp] 里创建一次。监听跟着进程走。
 *
 * @param downloader 已经初始化好的默认下载器。
 */
internal class DownloadStore(
    private val downloader: FastDownloader,
) {
    private var sawSnapshot = false
    private var latest = emptyList<DownloadRecord>()

    /** 还没删除的任务，新的在前。 */
    var records by mutableStateOf<List<DownloadRecord>>(emptyList())
        private set

    /**
     * 有新的下载开始时加一。
     *
     * 界面看到它变大，就去请求通知权限。
     */
    var downloadAttempts by mutableIntStateOf(0)
        private set

    private val progressListener = object : DownloadListener {
        override fun onProgress(record: DownloadRecord, progress: Progress) {
            val total = if (progress.totalBytes >= 0L) progress.totalBytes else record.totalBytes
            upsert(
                record.copy(
                    downloadedBytes = progress.downloadedBytes,
                    totalBytes = total,
                    speedBps = progress.speedBps,
                    etaMillis = progress.etaMillis,
                ),
            )
        }
    }

    private val snapshotListener = SnapshotListener { incoming ->
        sawSnapshot = true
        replace(incoming)
    }

    init {
        downloader.addListener(progressListener)
        downloader.addSnapshotListener(listener = snapshotListener)
        downloader.query.query(DownloadFilter()) { list ->
            if (!sawSnapshot) replace(list)
            if (list.any { it.status in ACTIVE }) markDownload()
        }
    }

    /**
     * 登记一条下载。同一条链接已经有任务时不再入队。
     *
     * 地址和保存路径都相同、而且任务还在时，下载器会留在原来那条上。
     * 这里再按地址看一遍。删掉之后可以重新添加。
     *
     * @param targetUrl 绝对地址。
     * @param fileName 内部目录里的文件名。
     * @return 新登记了一条时为 true。这条链接已经有任务时为 false。
     */
    fun enqueue(targetUrl: String, fileName: String): Boolean {
        if (hasLink(targetUrl)) return false
        val request = downloadRequest(targetUrl) {
            saveTo(DOWNLOAD_DIRECTORY, fileName)
            enqueueAction(EnqueueAction.DO_NOT_ENQUEUE_IF_EXISTING)
        }
        downloader.tasks.enqueue(request)
        markDownload()
        return true
    }

    /** 暂停这一条。断点还在。 */
    fun pause(id: String) {
        downloader.tasks.pause(id)
    }

    /** 继续暂停的任务，或开始只登记还没下的任务。 */
    fun resume(id: String) {
        val status = latest.firstOrNull { it.id == id }?.status
        if (status == Status.ADDED) downloader.tasks.start(id) else downloader.tasks.resume(id)
        markDownload()
    }

    /** 失败或取消之后再下一次。 */
    fun retry(id: String) {
        downloader.tasks.retry(id)
        markDownload()
    }

    /** 去掉记录，并删除目标文件。 */
    fun delete(id: String) {
        downloader.tasks.delete(id)
    }

    private fun markDownload() {
        downloadAttempts++
    }

    /** 列表里还有这条地址。已删除的任务不会留在列表里。 */
    private fun hasLink(targetUrl: String): Boolean {
        return latest.any { record -> record.request.url == targetUrl }
    }

    private fun replace(incoming: List<DownloadRecord>) {
        val previous = latest.associateBy { it.id }
        publish(
            incoming
                .filter { it.status != Status.REMOVED && it.status != Status.DELETED }
                .map { preserve(previous[it.id], it) }
                .sortedByDescending { it.createdAt },
        )
    }

    private fun upsert(record: DownloadRecord) {
        if (record.status == Status.REMOVED || record.status == Status.DELETED) {
            publish(latest.filter { it.id != record.id })
            return
        }
        val merged = preserve(latest.firstOrNull { it.id == record.id }, record)
        publish(
            (latest.filter { it.id != record.id } + merged)
                .sortedByDescending { it.createdAt },
        )
    }

    private fun publish(next: List<DownloadRecord>) {
        latest = next
        records = next
    }

    private companion object {
        val ACTIVE: Set<Status> = setOf(Status.QUEUED, Status.DOWNLOADING, Status.WAITING_NETWORK)

        /**
         * 快照有时比进度回调慢，字节数不能往回跳。
         *
         * 状态变了就用新的那份，暂停之后不再留着旧速度。
         */
        fun preserve(current: DownloadRecord?, incoming: DownloadRecord): DownloadRecord {
            if (current == null || current.status != incoming.status) return incoming
            val downloaded = maxOf(current.downloadedBytes, incoming.downloadedBytes)
            val total = if (incoming.totalBytes >= 0L) incoming.totalBytes else current.totalBytes
            val speed = if (incoming.speedBps > 0L) incoming.speedBps else current.speedBps
            val eta = when {
                incoming.speedBps > 0L -> incoming.etaMillis
                incoming.etaMillis >= 0L -> incoming.etaMillis
                else -> current.etaMillis
            }
            if (
                downloaded == incoming.downloadedBytes &&
                total == incoming.totalBytes &&
                speed == incoming.speedBps &&
                eta == incoming.etaMillis
            ) {
                return incoming
            }
            return incoming.copy(
                downloadedBytes = downloaded,
                totalBytes = total,
                speedBps = speed,
                etaMillis = eta,
            )
        }
    }
}
