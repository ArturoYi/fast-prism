/**
 * 把任务变化和进度送到主线程。
 *
 * 监听器只在 [poster] 里被调用。进度先按时间和字节节流，再合并成主线程上的最后一笔，避免慢界面把队列撑大。
 *
 * @file
 */
package com.arturoyi.fastdownload.api

import android.os.Handler
import android.os.Looper
import com.arturoyi.fastdownload.core.error.DownloadError
import com.arturoyi.fastdownload.core.error.ErrorCode
import com.arturoyi.fastdownload.core.error.ResumeFailedCause
import com.arturoyi.fastdownload.core.event.ProgressThrottle
import com.arturoyi.fastdownload.core.model.BlockProgress
import com.arturoyi.fastdownload.core.model.ContentFingerprint
import com.arturoyi.fastdownload.core.model.DownloadBlock
import com.arturoyi.fastdownload.core.model.DownloadRecord
import com.arturoyi.fastdownload.core.model.Progress
import com.arturoyi.fastdownload.core.state.Status
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicBoolean

/**
 * 收到一份任务快照时调用。回调在主线程。
 */
public fun interface SnapshotListener {
    /**
     * 队列里的最新任务。
     *
     * @param records 不可变快照。
     */
    public fun onSnapshot(records: List<DownloadRecord>)
}

/**
 * 监听器的主线程出口。
 *
 * @param poster 把一段回调排到主线程。测试可以换成记录线程名的实现。
 */
public class MainThreadEventDispatcher internal constructor(
    private val poster: (Runnable) -> Unit,
) {
    public constructor() : this({ runnable -> Handler(Looper.getMainLooper()).post(runnable) })

    private val listeners = CopyOnWriteArrayList<DownloadListener>()
    private val snapshots = CopyOnWriteArrayList<SnapshotListener>()
    private val records = ConcurrentHashMap<String, DownloadRecord>()
    private val throttles = ConcurrentHashMap<String, ProgressThrottle>()
    private val pending = ConcurrentHashMap<String, Progress>()
    private val delivering = AtomicBoolean(false)

    /**
     * 登记 [listener]。之后的回调都会经过主线程。
     *
     * 同一个实例重复登记只会留一份。
     *
     * @param listener 要接收事件的对象。
     */
    public fun add(listener: DownloadListener) {
        if (!listeners.contains(listener)) listeners.add(listener)
    }

    /**
     * 去掉当时登记的那一个 [listener]。
     *
     * @param listener 必须是 [add] 时的同一个实例。
     */
    public fun remove(listener: DownloadListener) {
        listeners.remove(listener)
    }

    /**
     * 登记任务列表回调。
     *
     * @param listener 列表变化时调用。
     */
    public fun addSnapshot(listener: SnapshotListener) {
        if (!snapshots.contains(listener)) snapshots.add(listener)
    }

    /**
     * 去掉任务列表回调。
     *
     * @param listener [addSnapshot] 时的同一个实例。
     */
    public fun removeSnapshot(listener: SnapshotListener) {
        snapshots.remove(listener)
    }

    /** 当前登记的监听器个数。测试用来确认注销后没有残留。 */
    public fun listenerCount(): Int = listeners.size

    /**
     * 用新的任务列表做状态对比，并通知监听器。
     *
     * @param next 仓库里的最新任务，按调度顺序。
     * @param blocks 正在下载的任务的分块。其他任务可以不提供。
     */
    public fun publish(next: List<DownloadRecord>, blocks: Map<String, List<DownloadBlock>> = emptyMap()) {
        val previous = records.toMap()
        records.clear()
        next.forEach { record -> records[record.id] = record }
        val listenersNow = listeners.toList()
        val snapshotsNow = snapshots.toList()
        poster {
            next.forEach { record ->
                dispatchStatus(listenersNow, previous[record.id], record, blocks[record.id].orEmpty())
            }
            snapshotsNow.forEach { listener -> listener.onSnapshot(next) }
        }
    }

    /**
     * 提交一次已同步的进度。
     *
     * 没跨过节流阈值时直接丢掉。主线程忙时只保留每个任务的最后一笔。
     *
     * @param id 任务标识。
     * @param downloadedBytes 已同步字节。
     * @param totalBytes 资源总长度。未知时为 `-1`。
     * @param speedBps 估算速度。
     * @param etaMillis 估算剩余时间。未知时为 `-1`。
     */
    public fun progress(
        id: String,
        downloadedBytes: Long,
        totalBytes: Long,
        speedBps: Long,
        etaMillis: Long,
    ) {
        val throttle = throttles.getOrPut(id) { ProgressThrottle() }
        if (!throttle.shouldEmit(downloadedBytes)) return
        pending[id] = Progress(
            recordId = id,
            downloadedBytes = downloadedBytes,
            totalBytes = totalBytes,
            speedBps = speedBps,
            etaMillis = etaMillis,
        )
        scheduleProgress()
    }

    /**
     * 通知不能续传。回调在主线程。
     *
     * @param id 任务标识。
     * @param cause 重下原因。
     */
    public fun resumeFailed(id: String, cause: ResumeFailedCause) {
        val listenersNow = listeners.toList()
        poster {
            val record = records[id] ?: return@poster
            listenersNow.forEach { listener -> listener.onResumeFailed(record, cause) }
        }
    }

    /**
     * 通知某个分块已连接。回调在主线程。
     *
     * @param id 任务标识。
     * @param blockIndex 分块序号。
     * @param fingerprint 这次连接的指纹。
     */
    public fun connected(id: String, blockIndex: Int, fingerprint: ContentFingerprint) {
        val listenersNow = listeners.toList()
        poster {
            val record = records[id] ?: return@poster
            listenersNow.forEach { listener -> listener.onConnected(record, blockIndex, fingerprint) }
        }
    }

    /**
     * 通知单个分块的进度。回调在主线程。
     *
     * @param id 任务标识。
     * @param block 这一分块的进度。
     */
    public fun blockProgress(id: String, block: BlockProgress) {
        val listenersNow = listeners.toList()
        poster {
            val record = records[id] ?: return@poster
            listenersNow.forEach { listener -> listener.onBlockProgress(record, block) }
        }
    }

    private fun scheduleProgress() {
        if (!delivering.compareAndSet(false, true)) return
        val listenersNow = listeners.toList()
        val snapshotsNow = snapshots.toList()
        poster {
            val batch = pending.keys.toList().mapNotNull { id ->
                pending.remove(id)?.let { id to it }
            }
            delivering.set(false)
            batch.forEach { (id, progress) ->
                val record = records[id]?.copy(
                    downloadedBytes = progress.downloadedBytes,
                    totalBytes = if (progress.totalBytes >= 0) progress.totalBytes else records[id]?.totalBytes ?: -1L,
                    speedBps = progress.speedBps,
                    etaMillis = progress.etaMillis,
                ) ?: return@forEach
                listenersNow.forEach { listener -> listener.onProgress(record, progress) }
            }
            if (batch.isNotEmpty()) {
                val snapshot = records.values.toList()
                snapshotsNow.forEach { listener -> listener.onSnapshot(snapshot) }
            }
            if (pending.isNotEmpty()) scheduleProgress()
        }
    }

    private fun dispatchStatus(
        listenersNow: List<DownloadListener>,
        previous: DownloadRecord?,
        next: DownloadRecord,
        blocks: List<DownloadBlock>,
    ) {
        if (previous == null) {
            listenersNow.forEach { listener -> listener.onAdded(next) }
        }
        if (previous?.status == next.status) return
        when (next.status) {
            Status.ADDED -> Unit
            Status.QUEUED -> if (previous?.status == Status.PAUSED) {
                listenersNow.forEach { listener -> listener.onResumed(next) }
            } else {
                listenersNow.forEach { listener -> listener.onQueued(next, false) }
            }
            Status.WAITING_NETWORK -> {
                listenersNow.forEach { listener -> listener.onWaitingNetwork(next) }
                listenersNow.forEach { listener -> listener.onQueued(next, true) }
            }
            Status.DOWNLOADING -> listenersNow.forEach { listener -> listener.onStarted(next, blocks) }
            Status.PAUSED -> listenersNow.forEach { listener -> listener.onPaused(next) }
            Status.COMPLETED -> listenersNow.forEach { listener -> listener.onCompleted(next) }
            Status.FAILED -> {
                val error = next.error ?: DownloadError(ErrorCode.UNKNOWN, "下载失败", next.id)
                listenersNow.forEach { listener -> listener.onError(next, error) }
            }
            Status.CANCELLED -> listenersNow.forEach { listener -> listener.onCancelled(next) }
            Status.REMOVED -> listenersNow.forEach { listener -> listener.onRemoved(next) }
            Status.DELETED -> listenersNow.forEach { listener -> listener.onDeleted(next) }
        }
    }
}
