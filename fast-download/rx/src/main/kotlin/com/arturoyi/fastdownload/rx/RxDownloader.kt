/**
 * 用 RxJava 3 订阅下载。
 *
 * @file
 */
package com.arturoyi.fastdownload.rx

import com.arturoyi.fastdownload.api.FastDownloader
import com.arturoyi.fastdownload.core.event.DownloadEvent
import com.arturoyi.fastdownload.core.model.DownloadRequest
import com.arturoyi.fastdownload.core.model.Progress
import com.arturoyi.fastdownload.engine.DownloadResult
import com.arturoyi.fastdownload.ktx.await
import com.arturoyi.fastdownload.ktx.events
import com.arturoyi.fastdownload.ktx.groupProgress
import com.arturoyi.fastdownload.ktx.progressFlow
import io.reactivex.rxjava3.core.Flowable
import io.reactivex.rxjava3.core.Single
import kotlinx.coroutines.rx3.asFlowable
import kotlinx.coroutines.rx3.rxSingle

/**
 * 入队并在任务结束时发出结果。
 *
 * 订阅被取消时会取消这条下载。已经结束的任务直接发出结果。
 *
 * @receiver 要下载的请求。
 * @param downloader 负责入队的下载器。
 * @return 成功或失败。取消和删除也算失败。
 */
public fun DownloadRequest.awaitSingle(downloader: FastDownloader): Single<DownloadResult> {
    return rxSingle { await(downloader) }
}

/**
 * 一条任务的进度。
 *
 * 订阅取消时摘掉监听器。
 *
 * @param id 任务标识。
 * @return 每次跨过节流阈值后的进度。
 */
public fun FastDownloader.progressFlowable(id: String): Flowable<Progress> = progressFlow(id).asFlowable()

/**
 * 全局任务事件。
 *
 * @return 冷流。每个订阅者有自己的监听器。
 */
public fun FastDownloader.eventsFlowable(): Flowable<DownloadEvent> = events().asFlowable()

/**
 * 一个分组里已记录字节的合计。
 *
 * @param groupId 分组名。
 * @return 每次任务列表变化时的合计进度。
 */
public fun FastDownloader.groupProgressFlowable(groupId: String): Flowable<Progress> {
    return groupProgress(groupId).asFlowable()
}
