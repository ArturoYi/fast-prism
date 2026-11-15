/**
 * 下载任务的生命周期状态。
 *
 * @file
 */
package com.arturoyi.fastdowload.core.state

/**
 * 任务在队列里的位置。
 *
 * [value] 一旦发布就不再改，也不把旧值挪给别的状态。
 *
 * @property value 稳定的数字码。
 */
public enum class Status(
    public val value: Int,
) {
    /** 已登记，但入队时没有要求立即下载。 */
    ADDED(0),

    /** 等待调度。 */
    QUEUED(1),

    /** 当前网络不满足任务要求，等网络变化后再排队。 */
    WAITING_NETWORK(2),

    /** 正在读取响应并写入文件。 */
    DOWNLOADING(3),

    /** 调用方暂停。断点保留。 */
    PAUSED(4),

    /** 字节已经写完并同步到存储。 */
    COMPLETED(5),

    /** 网络、协议或写盘失败。断点保留，可以再排队。 */
    FAILED(6),

    /** 调用方取消。断点和文件都保留，可以再排队。 */
    CANCELLED(7),

    /** 移出队列。文件保留，记录不再继续。 */
    REMOVED(8),

    /** 记录和目标文件都删除。 */
    DELETED(9),
}
