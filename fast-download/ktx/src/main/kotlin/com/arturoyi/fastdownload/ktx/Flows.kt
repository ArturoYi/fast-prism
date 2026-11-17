/**
 * 用协程等待下载，或把进度收成 Flow。
 *
 * 挂起不会占住调用线程。取消等待协程时会取消对应任务。
 *
 * @file
 */
package com.arturoyi.fastdownload.ktx

import com.arturoyi.fastdownload.api.DownloadListener
import com.arturoyi.fastdownload.api.FastDownloader
import com.arturoyi.fastdownload.api.SnapshotListener
import com.arturoyi.fastdownload.core.error.DownloadError
import com.arturoyi.fastdownload.core.error.ErrorCode
import com.arturoyi.fastdownload.core.event.DownloadEvent
import com.arturoyi.fastdownload.core.model.DownloadBlock
import com.arturoyi.fastdownload.core.model.DownloadRecord
import com.arturoyi.fastdownload.core.model.DownloadRequest
import com.arturoyi.fastdownload.core.model.Progress
import com.arturoyi.fastdownload.engine.DownloadResult
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.suspendCancellableCoroutine
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.coroutines.resume

/**
 * 入队并挂起到任务结束。
 *
 * 协程取消时会取消这条下载。已经结束的任务直接返回结果。
 *
 * @receiver 要下载的请求。
 * @param downloader 负责入队的下载器。
 * @return 成功或失败。取消和删除也算失败，错误码是 [ErrorCode.CANCELED]。
 */
public suspend fun DownloadRequest.await(downloader: FastDownloader): DownloadResult {
    val id = downloader.tasks.enqueue(this)
    return suspendCancellableCoroutine { continuation ->
        val once = AtomicBoolean(false)
        lateinit var listener: DownloadListener
        fun finish(result: DownloadResult) {
            if (!continuation.isActive || !once.compareAndSet(false, true)) return
            downloader.removeListener(listener)
            continuation.resume(result)
        }
        listener = object : DownloadListener {
            override fun onCompleted(record: DownloadRecord) {
                if (record.id == id) finish(DownloadResult.Success(record.downloadedBytes))
            }

            override fun onError(record: DownloadRecord, error: DownloadError) {
                if (record.id == id) finish(DownloadResult.Failure(error))
            }

            override fun onCancelled(record: DownloadRecord) {
                if (record.id == id) finish(canceled(id))
            }

            override fun onDeleted(record: DownloadRecord) {
                if (record.id == id) finish(canceled(id))
            }
        }
        downloader.addListener(listener)
        continuation.invokeOnCancellation {
            downloader.removeListener(listener)
            downloader.tasks.cancel(id)
        }
    }
}

/**
 * 一条任务的进度。
 *
 * 收集取消时摘掉监听器。
 *
 * @param id 任务标识。
 * @return 每次跨过节流阈值后的进度。
 */
public fun FastDownloader.progressFlow(id: String): Flow<Progress> = callbackFlow {
    val listener = object : DownloadListener {
        override fun onProgress(record: DownloadRecord, progress: Progress) {
            if (record.id == id) trySend(progress)
        }
    }
    addListener(listener)
    awaitClose { removeListener(listener) }
}

/**
 * 全局任务事件。
 *
 * 只包含开始、进度、完成和失败。其他生命周期走 [DownloadListener]。
 *
 * @return 冷流。每个收集者有自己的监听器。
 */
public fun FastDownloader.events(): Flow<DownloadEvent> = callbackFlow {
    val listener = object : DownloadListener {
        override fun onStarted(record: DownloadRecord, blocks: List<DownloadBlock>) {
            trySend(DownloadEvent.Started(record.id))
        }

        override fun onProgress(record: DownloadRecord, progress: Progress) {
            trySend(DownloadEvent.Progress(record.id, progress.downloadedBytes, progress.totalBytes))
        }

        override fun onCompleted(record: DownloadRecord) {
            trySend(DownloadEvent.Completed(record.id, record.downloadedBytes))
        }

        override fun onError(record: DownloadRecord, error: DownloadError) {
            trySend(DownloadEvent.Failed(record.id, error))
        }
    }
    addListener(listener)
    awaitClose { removeListener(listener) }
}

/**
 * 一个分组里已记录字节的合计。
 *
 * 组里还有未知总长度时，合计总长度是 `-1`。
 *
 * @param groupId 分组名。
 * @return 每次任务列表变化时的合计进度。
 */
public fun FastDownloader.groupProgress(groupId: String): Flow<Progress> = callbackFlow {
    val listener = SnapshotListener { records ->
        val group = records.filter { record -> record.request.groupId == groupId }
        val downloaded = group.sumOf { record -> record.downloadedBytes }
        val known = group.all { record -> record.totalBytes >= 0L }
        val total = if (known) group.sumOf { record -> record.totalBytes } else -1L
        trySend(
            Progress(
                recordId = groupId,
                downloadedBytes = downloaded,
                totalBytes = total,
                speedBps = 0L,
                etaMillis = -1L,
            ),
        )
    }
    addSnapshotListener(listener = listener)
    awaitClose { removeSnapshotListener(listener) }
}

private fun canceled(id: String): DownloadResult.Failure {
    return DownloadResult.Failure(DownloadError(ErrorCode.CANCELED, "已取消", id))
}
