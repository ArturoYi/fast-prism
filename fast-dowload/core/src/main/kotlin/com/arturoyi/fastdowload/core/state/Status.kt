/**
 * 下载任务的生命周期状态。
 *
 * @file
 */
package com.arturoyi.fastdowload.core.state

/**
 * 任务状态。名称与最终模型一致，当前只保留这个子集。
 *
 * 不包含暂停、取消或等待网络：这个 SDK 不提供暂停、取消或停止。
 * 进程结束下载即停止，也不为了保活去申请前台服务。
 */
public enum class Status {
    /** 任务已记录，尚未进入队列。 */
    ADDED,

    /** 已进入队列，等待连接空闲。 */
    QUEUED,

    /** 正在读取响应并写入文件。 */
    DOWNLOADING,

    /** 字节已经写完并同步到存储。 */
    COMPLETED,

    /** 网络、协议或写盘失败，任务停在错误状态。 */
    FAILED,

    /** 任务从队列中移除，已写文件仍保留。 */
    REMOVED,

    /** 任务记录和已写文件都已删除。 */
    DELETED,
}
