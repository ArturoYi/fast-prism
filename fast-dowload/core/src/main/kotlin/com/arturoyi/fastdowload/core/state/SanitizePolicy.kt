/**
 * 进程启动时如何修正残留任务。
 *
 * 不用“已写字节不少于总长度”判断文件已经完整。
 *
 * @file
 */
package com.arturoyi.fastdowload.core.state

/**
 * 一次清洗要对一条任务做的修改。
 *
 * @property status 清洗后的状态。
 * @property clearBlocks 是否丢掉全部分块和指纹。
 * @property zeroProgress 是否把已写字节归零。
 * @property deletePart 是否删除临时文件。
 */
public data class SanitizeDecision(
    public val status: Status,
    public val clearBlocks: Boolean,
    public val zeroProgress: Boolean,
    public val deletePart: Boolean,
)

/**
 * 根据脏标记和文件是否还在，决定启动后的任务状态。
 *
 * 正在下载或等待网络的任务回到队列。脏文件一律清掉断点，避免把半截文件当成完成。
 * 目标文件和临时文件都不见了，且已经记过进度，则把进度归零。
 */
public object SanitizePolicy {
    /**
     * 计算一条任务的清洗结果。
     *
     * @param status 存储里的状态。
     * @param dirty 上次写入是否还没完成提交。
     * @param downloadedBytes 已记录的字节数。
     * @param targetExists 最终文件是否存在。
     * @param partExists 临时文件是否存在。
     * @return 要写回存储的决定。
     */
    public fun decide(
        status: Status,
        dirty: Boolean,
        downloadedBytes: Long,
        targetExists: Boolean,
        partExists: Boolean,
    ): SanitizeDecision {
        if (dirty && status != Status.REMOVED && status != Status.DELETED) {
            val next = if (status == Status.COMPLETED || status == Status.DOWNLOADING || status == Status.WAITING_NETWORK) {
                Status.QUEUED
            } else {
                status
            }
            return SanitizeDecision(
                status = next,
                clearBlocks = true,
                zeroProgress = true,
                deletePart = true,
            )
        }
        if (status == Status.DOWNLOADING || status == Status.WAITING_NETWORK) {
            return SanitizeDecision(
                status = Status.QUEUED,
                clearBlocks = false,
                zeroProgress = false,
                deletePart = false,
            )
        }
        if (downloadedBytes > 0L && !targetExists && !partExists && status != Status.REMOVED && status != Status.DELETED) {
            val next = if (status == Status.COMPLETED) Status.QUEUED else status
            return SanitizeDecision(
                status = next,
                clearBlocks = true,
                zeroProgress = true,
                deletePart = true,
            )
        }
        return SanitizeDecision(status, clearBlocks = false, zeroProgress = false, deletePart = false)
    }
}
