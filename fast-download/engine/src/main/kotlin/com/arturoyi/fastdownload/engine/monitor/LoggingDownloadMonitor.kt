/**
 * 把阶段、重试和终态写成结构化日志，并累计指标。
 *
 * 调试日志默认关闭。状态和失败使用 INFO、WARNING 和 SEVERE，不依赖调试开关。
 *
 * @file
 */
package com.arturoyi.fastdownload.engine.monitor

import com.arturoyi.fastdownload.core.error.ErrorCode
import com.arturoyi.fastdownload.core.monitor.DownloadMonitor
import com.arturoyi.fastdownload.core.monitor.MonitorSnapshot
import com.arturoyi.fastdownload.core.monitor.Stage
import java.util.logging.Level
import java.util.logging.Logger

/**
 * 线程安全的指标和日志。
 *
 * 日志里只有任务标识、阶段和错误码，不写完整地址。
 *
 * @param debug 为 true 时额外写 FINE 级别的链路日志。
 * @param log 接收日志的记录器。测试可以换成自己的记录器。
 */
public class LoggingDownloadMonitor(
    private val debug: Boolean = false,
    log: Logger = Logger.getLogger(LOGGER_NAME),
) : DownloadMonitor {
    private val logger = log
    private val stageMillis = linkedMapOf<Stage, Long>()
    private val retries = linkedMapOf<ErrorCode, Int>()
    private val errors = linkedMapOf<ErrorCode, Int>()
    private var writes = 0L
    private var redownloads = 0L
    private var completed = 0L
    private var failed = 0L

    /**
     * 累加阶段耗时。调试打开时写一条 FINE 日志。
     *
     * @param publicId 任务标识。
     * @param stage 刚结束的阶段。
     * @param elapsedMs 耗时，单位毫秒。
     */
    public override fun onStage(publicId: String, stage: Stage, elapsedMs: Long) {
        synchronized(this) {
            stageMillis[stage] = (stageMillis[stage] ?: 0L) + elapsedMs
        }
        if (debug) {
            logger.log(Level.FINE, "stage=${stage.name} publicId=$publicId elapsedMs=$elapsedMs")
        }
    }

    /**
     * 记一次重试或等待网络，并写 WARNING。
     *
     * @param publicId 任务标识。
     * @param attempt 已经用掉的重试次数。
     * @param delayMs 计划等待的毫秒数。
     * @param code 触发这次等待的错误码。
     */
    public override fun onRetry(publicId: String, attempt: Int, delayMs: Long, code: ErrorCode) {
        synchronized(this) {
            retries[code] = (retries[code] ?: 0) + 1
        }
        logger.log(
            Level.WARNING,
            "stage=RETRY publicId=$publicId attempt=$attempt delayMs=$delayMs code=${code.name}",
        )
    }

    /**
     * 记一次结束。失败写 SEVERE，成功在调试打开时写 FINE。
     *
     * @param publicId 任务标识。
     * @param success 是否提交了文件。
     * @param code 失败错误码。成功时为 null。
     */
    public override fun onTerminal(publicId: String, success: Boolean, code: ErrorCode?) {
        synchronized(this) {
            if (success) {
                completed += 1
            } else {
                failed += 1
                if (code != null) errors[code] = (errors[code] ?: 0) + 1
            }
        }
        if (success) {
            if (debug) logger.log(Level.FINE, "stage=TERMINAL publicId=$publicId code=NONE")
            return
        }
        logger.log(Level.SEVERE, "stage=TERMINAL publicId=$publicId code=${code?.name ?: "UNKNOWN"}")
    }

    /**
     * 记一次从头再写，并写 WARNING。
     *
     * @param publicId 任务标识。
     * @param cause 原因名。
     */
    public override fun onRedownload(publicId: String, cause: String) {
        synchronized(this) { redownloads += 1 }
        logger.log(Level.WARNING, "stage=TRIAL publicId=$publicId code=$cause")
    }

    /**
     * 偏移提交次数加一。
     */
    public override fun onRepositoryWrite() {
        synchronized(this) { writes += 1 }
    }

    /**
     * 复制当前计数。
     *
     * @return 调用瞬间的指标。
     */
    public override fun snapshot(): MonitorSnapshot {
        return synchronized(this) {
            MonitorSnapshot(
                stageMillis = stageMillis.toMap(),
                repositoryWrites = writes,
                retryCounts = retries.toMap(),
                errorCounts = errors.toMap(),
                redownloadCount = redownloads,
                completedCount = completed,
                failedCount = failed,
            )
        }
    }

    private companion object {
        private const val LOGGER_NAME: String = "com.arturoyi.fastdownload.monitor"
    }
}
