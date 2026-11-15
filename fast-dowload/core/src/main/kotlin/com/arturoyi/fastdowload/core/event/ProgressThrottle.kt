/**
 * 决定这一次进度要不要通知出去。
 *
 * 时间间隔和字节间隔只要有一个达到就通知。完成时由调用方强制通知。
 *
 * @file
 */
package com.arturoyi.fastdowload.core.event

import com.arturoyi.fastdowload.core.model.Defaults

/**
 * 按时间和字节两条线节流。
 *
 * 每个任务使用自己的一个实例。第一次调用一定会放行。
 *
 * @param intervalMs 两次通知之间的最短间隔，单位毫秒。0 表示不按时间拦住。
 * @param minBytes 两次通知之间至少新增的字节数。0 表示不按字节拦住。
 * @param clock 当前时间，单位毫秒。测试可以传入固定时钟。
 */
public class ProgressThrottle(
    private val intervalMs: Long = Defaults.PROGRESS_INTERVAL.inWholeMilliseconds,
    private val minBytes: Long = Defaults.PROGRESS_MIN_BYTES,
    private val clock: () -> Long = System::currentTimeMillis,
) {
    private var lastEmitMs: Long = Long.MIN_VALUE
    private var lastBytes: Long = -1L

    /**
     * 是否应该把 [downloadedBytes] 通知出去。
     *
     * [force] 为 true 时总是放行，用来保证完成前的最后一次进度不会被丢掉。
     *
     * @param downloadedBytes 当前已同步的字节数。
     * @param force 为 true 时忽略间隔。
     * @return 应该通知时为 true。
     */
    public fun shouldEmit(downloadedBytes: Long, force: Boolean = false): Boolean {
        val now = clock()
        val first = lastBytes < 0L
        val bytesAdvanced = downloadedBytes - lastBytes >= minBytes
        val timeAdvanced = lastEmitMs != Long.MIN_VALUE && now - lastEmitMs >= intervalMs
        if (!force && !first && !bytesAdvanced && !timeAdvanced) return false
        lastEmitMs = now
        lastBytes = downloadedBytes
        return true
    }
}
