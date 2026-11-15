/**
 * 在下载线程里执行一次分块下载，不改任务状态。
 *
 * @file
 */
package com.arturoyi.fastdowload.engine.dispatch

import com.arturoyi.fastdowload.core.error.ResumeFailedCause
import com.arturoyi.fastdowload.core.model.DownloadRecord
import com.arturoyi.fastdowload.core.monitor.DownloadMonitor
import com.arturoyi.fastdowload.core.spi.BreakpointStrategy
import com.arturoyi.fastdowload.core.spi.FilePolicy
import com.arturoyi.fastdowload.core.spi.HttpClient
import com.arturoyi.fastdowload.core.spi.IntegrityVerifier
import com.arturoyi.fastdowload.engine.BlockDownloadCall
import com.arturoyi.fastdowload.engine.BlockDownloadListener
import com.arturoyi.fastdowload.engine.DownloadResult
import com.arturoyi.fastdowload.engine.InterruptToken
import com.arturoyi.fastdowload.core.strategy.AdaptiveBreakpointStrategy
import com.arturoyi.fastdowload.engine.file.DefaultFilePolicy
import com.arturoyi.fastdowload.engine.http.OkHttpDownloadClient
import com.arturoyi.fastdowload.engine.limit.GlobalRateLimiter
import com.arturoyi.fastdowload.engine.store.BreakpointStore
import com.arturoyi.fastdowload.engine.verify.DefaultIntegrityVerifier
import java.io.File

/**
 * 真正去写文件的动作。命令线程只投递，不在自己的线程里读网络。
 */
public fun interface DownloadRunner {
    /**
     * 下载 [record] 直到成功、失败、取消或文件忙。
     *
     * [stop] 被置位后应尽快返回，不要再把任务标成完成。
     *
     * @param record 已经处于下载中的任务。
     * @param stop 暂停、取消、移除或删除时由命令线程置位。
     * @return 这次写入的结果。
     */
    public fun run(record: DownloadRecord, stop: InterruptToken): DownloadResult
}

/**
 * 用 [BlockDownloadCall] 执行一条任务。
 *
 * 状态留给命令线程。这里只写文件和断点偏移。
 *
 * @param storageRoot 应用内部存储根目录。
 * @param store 断点仓库。
 * @param client 试连和分块请求使用的客户端。
 * @param onProgress 已同步字节变化时调用，发生在下载线程。
 * @param onResumeFailed 不能沿用已有字节时调用，发生在下载线程。
 * @param rates 全局速度上限。单任务上限来自请求。
 * @param monitor 阶段耗时。默认不记录。
 * @param strategy 分块方式。默认按文件长度分档。
 * @param files 临时文件和预分配。
 * @param verifier 完成前的摘要核对。
 */
public class EngineDownloadRunner(
    private val storageRoot: File,
    private val store: BreakpointStore,
    private val client: HttpClient = OkHttpDownloadClient(),
    private val onProgress: (publicId: String, downloadedBytes: Long, totalBytes: Long) -> Unit = { _, _, _ -> },
    private val onResumeFailed: (publicId: String, cause: ResumeFailedCause) -> Unit = { _, _ -> },
    private val rates: GlobalRateLimiter = GlobalRateLimiter(),
    private val monitor: DownloadMonitor = DownloadMonitor.NONE,
    private val strategy: BreakpointStrategy = AdaptiveBreakpointStrategy,
    private val files: FilePolicy = DefaultFilePolicy(),
    private val verifier: IntegrityVerifier = DefaultIntegrityVerifier,
) : DownloadRunner {
    /**
     * 下载这一条记录。
     *
     * @param record 任务。目标路径来自它的请求。
     * @param stop 命令线程要求停止时置位。
     * @return 分块下载的结果。
     */
    public override fun run(record: DownloadRecord, stop: InterruptToken): DownloadResult {
        return BlockDownloadCall(
            request = record.request,
            storageRoot = storageRoot,
            store = store,
            client = client,
            listener = object : BlockDownloadListener {
                override fun onProgress(downloadedBytes: Long, totalBytes: Long) {
                    onProgress(record.id, downloadedBytes, totalBytes)
                }

                override fun onResumeFailed(cause: ResumeFailedCause) {
                    onResumeFailed(record.id, cause)
                }
            },
            delegateStatus = true,
            stop = stop,
            publicId = record.id,
            rateLimiter = rates.forTask(record.request.speedLimit?.value),
            monitor = monitor,
            strategy = strategy,
            files = files,
            verifier = verifier,
        ).execute()
    }
}
