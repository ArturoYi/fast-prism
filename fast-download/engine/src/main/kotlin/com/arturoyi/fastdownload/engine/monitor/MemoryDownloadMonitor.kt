/**
 * 不写日志的指标累计。
 *
 * @file
 */
package com.arturoyi.fastdownload.engine.monitor

import com.arturoyi.fastdownload.core.error.ErrorCode
import com.arturoyi.fastdownload.core.monitor.DownloadMonitor
import com.arturoyi.fastdownload.core.monitor.MonitorSnapshot
import com.arturoyi.fastdownload.core.monitor.Stage

/**
 * 只把阶段、重试和终态留在内存里。
 *
 * 不写日志。计数规则与 [LoggingDownloadMonitor] 相同，方便替换。
 */
public class MemoryDownloadMonitor : DownloadMonitor {
    private val stageMillis = linkedMapOf<Stage, Long>()
    private val retries = linkedMapOf<ErrorCode, Int>()
    private val errors = linkedMapOf<ErrorCode, Int>()
    private var writes = 0L
    private var redownloads = 0L
    private var completed = 0L
    private var failed = 0L

    /**
     * 累加 [stage] 的毫秒数。
     *
     * @param publicId 不参与计数。
     * @param stage 刚结束的阶段。
     * @param elapsedMs 耗时，单位毫秒。
     */
    public override fun onStage(publicId: String, stage: Stage, elapsedMs: Long) {
        synchronized(this) {
            stageMillis[stage] = (stageMillis[stage] ?: 0L) + elapsedMs
        }
    }

    /**
     * 按错误码累加一次重试。
     *
     * @param publicId 不参与计数。
     * @param attempt 不参与计数。
     * @param delayMs 不参与计数。
     * @param code 这次重试的错误码。
     */
    public override fun onRetry(publicId: String, attempt: Int, delayMs: Long, code: ErrorCode) {
        synchronized(this) {
            retries[code] = (retries[code] ?: 0) + 1
        }
    }

    /**
     * 成功或失败各加一。失败时再按 [code] 累加。
     *
     * @param publicId 不参与计数。
     * @param success 文件是否已经提交。
     * @param code 失败时的错误码。
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
    }

    /**
     * 重下次数加一。
     *
     * @param publicId 不参与计数。
     * @param cause 不参与计数。
     */
    public override fun onRedownload(publicId: String, cause: String) {
        synchronized(this) { redownloads += 1 }
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
}
