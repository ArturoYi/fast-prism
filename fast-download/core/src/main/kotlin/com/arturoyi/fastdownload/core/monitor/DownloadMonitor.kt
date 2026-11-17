/**
 * 一次下载各阶段的耗时和结果，供日志和指标使用。
 *
 * @file
 */
package com.arturoyi.fastdownload.core.monitor

import com.arturoyi.fastdownload.core.error.ErrorCode

/**
 * 下载执行过程中的阶段。
 *
 * 名称稳定，日志里直接写这些名字。
 */
public enum class Stage {
    /** 准备任务。 */
    INIT,

    /** 试连并判断能否续传。 */
    TRIAL,

    /** 解析目标路径。 */
    RESOLVE,

    /** 等待同一路径上的其他写入结束。 */
    LOCK,

    /** 预分配文件。 */
    ALLOCATE,

    /** 读取响应并写入临时文件。 */
    DOWNLOAD,

    /** 核对摘要。 */
    VERIFY,

    /** 把临时文件改成最终文件。 */
    COMMIT,

    /** 任务结束。 */
    TERMINAL,
}

/**
 * 到目前为止累计的指标。
 *
 * 次数和耗时都从进程启动后开始累加，不是单次任务的快照。
 *
 * @property stageMillis 各阶段耗时之和，单位毫秒。
 * @property repositoryWrites 成功提交偏移的次数。
 * @property retryCounts 按错误码统计的重试次数。
 * @property errorCounts 按错误码统计的最终失败次数。
 * @property redownloadCount 因为断点不能沿用而从头再写的次数。
 * @property completedCount 成功次数。
 * @property failedCount 最终失败次数。
 */
public data class MonitorSnapshot(
    public val stageMillis: Map<Stage, Long> = emptyMap(),
    public val repositoryWrites: Long = 0L,
    public val retryCounts: Map<ErrorCode, Int> = emptyMap(),
    public val errorCounts: Map<ErrorCode, Int> = emptyMap(),
    public val redownloadCount: Long = 0L,
    public val completedCount: Long = 0L,
    public val failedCount: Long = 0L,
)

/**
 * 接收阶段耗时、重试和终态。
 *
 * 实现要能被下载线程和命令线程同时调用。默认的 [NONE] 什么也不记录。
 */
public interface DownloadMonitor {
    /**
     * 记录 [stage] 花了多少毫秒。
     *
     * @param publicId 任务标识。
     * @param stage 刚结束的阶段。
     * @param elapsedMs 该阶段耗时，单位毫秒。
     */
    public fun onStage(publicId: String, stage: Stage, elapsedMs: Long)

    /**
     * 记录一次即将发生的重试或等待网络。
     *
     * @param publicId 任务标识。
     * @param attempt 这次之后已经用掉的重试次数。等待网络时可以与之前相同。
     * @param delayMs 计划等待的毫秒数。等待网络时为 0。
     * @param code 触发这次等待的错误码。
     */
    public fun onRetry(publicId: String, attempt: Int, delayMs: Long, code: ErrorCode)

    /**
     * 记录任务结束。
     *
     * @param publicId 任务标识。
     * @param success 文件是否已经提交。
     * @param code 失败时的错误码。成功时为 null。
     */
    public fun onTerminal(publicId: String, success: Boolean, code: ErrorCode?)

    /**
     * 记录一次断点不能沿用、准备从头再写。
     *
     * @param publicId 任务标识。
     * @param cause 原因名。
     */
    public fun onRedownload(publicId: String, cause: String)

    /**
     * 记录一次偏移已经写进存储。
     */
    public fun onRepositoryWrite()

    /**
     * 当前累计指标。
     *
     * @return 调用瞬间的副本。
     */
    public fun snapshot(): MonitorSnapshot

    public companion object {
        /** 不记录任何指标。 */
        public val NONE: DownloadMonitor = object : DownloadMonitor {
            override fun onStage(publicId: String, stage: Stage, elapsedMs: Long) = Unit

            override fun onRetry(publicId: String, attempt: Int, delayMs: Long, code: ErrorCode) = Unit

            override fun onTerminal(publicId: String, success: Boolean, code: ErrorCode?) = Unit

            override fun onRedownload(publicId: String, cause: String) = Unit

            override fun onRepositoryWrite() = Unit

            override fun snapshot(): MonitorSnapshot = MonitorSnapshot()
        }
    }
}
