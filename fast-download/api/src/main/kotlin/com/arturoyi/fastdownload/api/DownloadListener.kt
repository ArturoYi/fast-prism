/**
 * 任务生命周期回调。
 *
 * 所有方法都在主线程调用。默认什么也不做，调用方只覆盖自己关心的方法。
 *
 * @file
 */
package com.arturoyi.fastdownload.api

import com.arturoyi.fastdownload.core.error.DownloadError
import com.arturoyi.fastdownload.core.model.BlockProgress
import com.arturoyi.fastdownload.core.error.ResumeFailedCause
import com.arturoyi.fastdownload.core.model.ContentFingerprint
import com.arturoyi.fastdownload.core.model.DownloadBlock
import com.arturoyi.fastdownload.core.model.DownloadRecord
import com.arturoyi.fastdownload.core.model.Progress

/**
 * 一条任务从登记到结束的通知。
 */
public interface DownloadListener {
    /** 任务第一次出现在队列里。 */
    public fun onAdded(record: DownloadRecord) {}

    /**
     * 任务进入排队。
     *
     * @param waitingOnNetwork 为 true 时还在等网络，并会再收到 [onWaitingNetwork]。
     */
    public fun onQueued(record: DownloadRecord, waitingOnNetwork: Boolean) {}

    /** 网络不满足要求，任务停在等待。 */
    public fun onWaitingNetwork(record: DownloadRecord) {}

    /** 开始写文件。 */
    public fun onStarted(record: DownloadRecord, blocks: List<DownloadBlock>) {}

    /** 某个分块已经连上服务器。 */
    public fun onConnected(record: DownloadRecord, blockIndex: Int, fingerprint: ContentFingerprint) {}

    /** 已有字节不能沿用，接下来会重下。 */
    public fun onResumeFailed(record: DownloadRecord, cause: ResumeFailedCause) {}

    /** 已同步字节有变化。可能被时间和字节阈值合并过。 */
    public fun onProgress(record: DownloadRecord, progress: Progress) {}

    /** 单个分块的进度。同样在主线程。 */
    public fun onBlockProgress(record: DownloadRecord, block: BlockProgress) {}

    /** 调用方暂停了任务。 */
    public fun onPaused(record: DownloadRecord) {}

    /** 暂停后的任务重新排队。 */
    public fun onResumed(record: DownloadRecord) {}

    /** 文件已经写完。 */
    public fun onCompleted(record: DownloadRecord) {}

    /** 下载失败。 */
    public fun onError(record: DownloadRecord, error: DownloadError) {}

    /** 调用方取消了任务。文件还在。 */
    public fun onCancelled(record: DownloadRecord) {}

    /** 任务移出队列。目标文件还在。 */
    public fun onRemoved(record: DownloadRecord) {}

    /** 任务和目标文件都已删除。 */
    public fun onDeleted(record: DownloadRecord) {}
}

/**
 * 界面只关心进度、完成和失败时用的空实现。
 */
public abstract class SimpleDownloadListener : DownloadListener

/**
 * 任务级进度。覆盖 [DownloadListener.onProgress]、[DownloadListener.onCompleted] 和 [DownloadListener.onError]。
 */
public abstract class AggregateProgressListener : DownloadListener

/**
 * 分块进度。覆盖 [DownloadListener.onBlockProgress]。
 */
public abstract class BlockProgressListener : DownloadListener

/**
 * 速度和剩余时间。数值在 [Progress.speedBps] 和 [Progress.etaMillis]。
 */
public abstract class SpeedListener : DownloadListener

/**
 * 把同一次回调转给多个监听器。
 *
 * @param listeners 按给定顺序通知。列表在构造时复制。
 */
public class ListenerBunch(
    listeners: List<DownloadListener>,
) : DownloadListener {
    private val listeners = listeners.toList()

    /** 转发 [DownloadListener.onAdded]。 */
    public override fun onAdded(record: DownloadRecord): Unit = listeners.forEach { it.onAdded(record) }

    /** 转发 [DownloadListener.onQueued]。 */
    public override fun onQueued(record: DownloadRecord, waitingOnNetwork: Boolean) {
        listeners.forEach { it.onQueued(record, waitingOnNetwork) }
    }

    /** 转发 [DownloadListener.onWaitingNetwork]。 */
    public override fun onWaitingNetwork(record: DownloadRecord) {
        listeners.forEach { it.onWaitingNetwork(record) }
    }

    /** 转发 [DownloadListener.onStarted]。 */
    public override fun onStarted(record: DownloadRecord, blocks: List<DownloadBlock>) {
        listeners.forEach { it.onStarted(record, blocks) }
    }

    /** 转发 [DownloadListener.onConnected]。 */
    public override fun onConnected(record: DownloadRecord, blockIndex: Int, fingerprint: ContentFingerprint) {
        listeners.forEach { it.onConnected(record, blockIndex, fingerprint) }
    }

    /** 转发 [DownloadListener.onResumeFailed]。 */
    public override fun onResumeFailed(record: DownloadRecord, cause: ResumeFailedCause) {
        listeners.forEach { it.onResumeFailed(record, cause) }
    }

    /** 转发 [DownloadListener.onProgress]。 */
    public override fun onProgress(record: DownloadRecord, progress: Progress) {
        listeners.forEach { it.onProgress(record, progress) }
    }

    /** 转发 [DownloadListener.onBlockProgress]。 */
    public override fun onBlockProgress(record: DownloadRecord, block: BlockProgress) {
        listeners.forEach { it.onBlockProgress(record, block) }
    }

    /** 转发 [DownloadListener.onPaused]。 */
    public override fun onPaused(record: DownloadRecord): Unit = listeners.forEach { it.onPaused(record) }

    /** 转发 [DownloadListener.onResumed]。 */
    public override fun onResumed(record: DownloadRecord): Unit = listeners.forEach { it.onResumed(record) }

    /** 转发 [DownloadListener.onCompleted]。 */
    public override fun onCompleted(record: DownloadRecord): Unit = listeners.forEach { it.onCompleted(record) }

    /** 转发 [DownloadListener.onError]。 */
    public override fun onError(record: DownloadRecord, error: DownloadError) {
        listeners.forEach { it.onError(record, error) }
    }

    /** 转发 [DownloadListener.onCancelled]。 */
    public override fun onCancelled(record: DownloadRecord): Unit = listeners.forEach { it.onCancelled(record) }

    /** 转发 [DownloadListener.onRemoved]。 */
    public override fun onRemoved(record: DownloadRecord): Unit = listeners.forEach { it.onRemoved(record) }

    /** 转发 [DownloadListener.onDeleted]。 */
    public override fun onDeleted(record: DownloadRecord): Unit = listeners.forEach { it.onDeleted(record) }
}
