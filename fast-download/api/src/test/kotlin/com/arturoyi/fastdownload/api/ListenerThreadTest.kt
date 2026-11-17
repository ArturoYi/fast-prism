/**
 * 每一种监听回调都发生在投递线程上，进度不会为每次字节都排队。
 *
 * @file
 */
package com.arturoyi.fastdownload.api

import com.arturoyi.fastdownload.core.error.DownloadError
import com.arturoyi.fastdownload.core.error.ErrorCode
import com.arturoyi.fastdownload.core.error.ResumeFailedCause
import com.arturoyi.fastdownload.core.model.BlockProgress
import com.arturoyi.fastdownload.core.model.ContentFingerprint
import com.arturoyi.fastdownload.core.model.Destination
import com.arturoyi.fastdownload.core.model.DownloadRecord
import com.arturoyi.fastdownload.core.model.DownloadRequest
import com.arturoyi.fastdownload.core.model.Progress
import com.arturoyi.fastdownload.core.state.Status
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.CopyOnWriteArrayList

/**
 * [MainThreadEventDispatcher] 的线程和合并行为。
 */
class ListenerThreadTest {
    /**
     * 状态、进度、分块、续传失败和连接回调都在同一个投递线程。
     */
    @Test
    fun everyCallbackUsesThePosterThread() {
        val threads = CopyOnWriteArrayList<String>()
        val dispatcher = dispatcher(threads)
        val seen = CopyOnWriteArrayList<String>()
        dispatcher.add(recordingListener(threads, seen))
        var record = sample(Status.ADDED)
        dispatcher.publish(listOf(record))
        record = record.copy(status = Status.QUEUED, updatedAt = record.updatedAt + 1)
        dispatcher.publish(listOf(record))
        record = record.copy(status = Status.WAITING_NETWORK, updatedAt = record.updatedAt + 1)
        dispatcher.publish(listOf(record))
        record = record.copy(status = Status.DOWNLOADING, updatedAt = record.updatedAt + 1)
        dispatcher.publish(listOf(record))
        record = record.copy(status = Status.PAUSED, updatedAt = record.updatedAt + 1)
        dispatcher.publish(listOf(record))
        record = record.copy(status = Status.QUEUED, updatedAt = record.updatedAt + 1)
        dispatcher.publish(listOf(record))
        record = record.copy(
            status = Status.FAILED,
            updatedAt = record.updatedAt + 1,
            error = DownloadError(ErrorCode.UNKNOWN, "失败", record.id),
        )
        dispatcher.publish(listOf(record))
        record = record.copy(status = Status.COMPLETED, updatedAt = record.updatedAt + 1, error = null)
        dispatcher.publish(listOf(record))
        record = record.copy(status = Status.CANCELLED, updatedAt = record.updatedAt + 1)
        dispatcher.publish(listOf(record))
        record = record.copy(status = Status.REMOVED, updatedAt = record.updatedAt + 1)
        dispatcher.publish(listOf(record))
        record = record.copy(status = Status.DELETED, updatedAt = record.updatedAt + 1)
        dispatcher.publish(listOf(record))
        dispatcher.connected(record.id, 0, ContentFingerprint(contentLength = 4))
        dispatcher.resumeFailed(record.id, ResumeFailedCause.INFO_DIRTY)
        dispatcher.blockProgress(record.id, BlockProgress(0, 1, 4))
        dispatcher.progress(record.id, 4, 4, 1, 0)
        assertTrue(threads.isNotEmpty())
        assertTrue(threads.all { it == POSTER })
        assertEquals(
            listOf(
                "added",
                "queued",
                "waiting",
                "queued",
                "started",
                "paused",
                "resumed",
                "error",
                "completed",
                "cancelled",
                "removed",
                "deleted",
                "connected",
                "resumeFailed",
                "block",
                "progress",
            ),
            seen,
        )
    }

    /**
     * 主线程还没取走上一次进度时，新的进度不会再排一串任务。
     */
    @Test
    fun progressPostsStayBounded() {
        val queued = mutableListOf<Runnable>()
        val dispatcher = MainThreadEventDispatcher { runnable -> queued += runnable }
        var bytes = 0L
        repeat(30) {
            bytes += 70_000L
            dispatcher.progress("task", bytes, 3_000_000L, 0L, -1L)
        }
        assertEquals(1, queued.size)
    }

    /**
     * 注销必须用登记时的同一个实例。
     */
    @Test
    fun removeUsesTheSameInstance() {
        val dispatcher = MainThreadEventDispatcher { it.run() }
        val listener = object : DownloadListener {}
        dispatcher.add(listener)
        dispatcher.add(listener)
        assertEquals(1, dispatcher.listenerCount())
        dispatcher.remove(listener)
        assertEquals(0, dispatcher.listenerCount())
    }
}

private const val POSTER: String = "fastdownload-main"

private fun dispatcher(threads: MutableList<String>): MainThreadEventDispatcher {
    return MainThreadEventDispatcher { runnable ->
        val thread = Thread({
            threads += Thread.currentThread().name
            runnable.run()
        }, POSTER)
        thread.start()
        thread.join()
    }
}

private fun recordingListener(
    threads: MutableList<String>,
    seen: MutableList<String>,
): DownloadListener {
    fun mark(name: String) {
        threads += Thread.currentThread().name
        seen += name
    }
    return object : DownloadListener {
        override fun onAdded(record: DownloadRecord) = mark("added")
        override fun onQueued(record: DownloadRecord, waitingOnNetwork: Boolean) = mark("queued")
        override fun onWaitingNetwork(record: DownloadRecord) = mark("waiting")
        override fun onStarted(record: DownloadRecord, blocks: List<com.arturoyi.fastdownload.core.model.DownloadBlock>) =
            mark("started")
        override fun onConnected(
            record: DownloadRecord,
            blockIndex: Int,
            fingerprint: ContentFingerprint,
        ) = mark("connected")
        override fun onResumeFailed(record: DownloadRecord, cause: ResumeFailedCause) = mark("resumeFailed")
        override fun onProgress(record: DownloadRecord, progress: Progress) = mark("progress")
        override fun onBlockProgress(record: DownloadRecord, block: BlockProgress) = mark("block")
        override fun onPaused(record: DownloadRecord) = mark("paused")
        override fun onResumed(record: DownloadRecord) = mark("resumed")
        override fun onCompleted(record: DownloadRecord) = mark("completed")
        override fun onError(record: DownloadRecord, error: DownloadError) = mark("error")
        override fun onCancelled(record: DownloadRecord) = mark("cancelled")
        override fun onRemoved(record: DownloadRecord) = mark("removed")
        override fun onDeleted(record: DownloadRecord) = mark("deleted")
    }
}

private fun sample(status: Status): DownloadRecord {
    return DownloadRecord(
        id = "task",
        request = DownloadRequest(url = "https://example.com/a.bin", destination = Destination("a.bin")),
        status = status,
        createdAt = 10L,
        updatedAt = 10L,
    )
}
