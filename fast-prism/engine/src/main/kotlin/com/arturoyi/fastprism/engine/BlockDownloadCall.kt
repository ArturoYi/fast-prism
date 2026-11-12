/**
 * 按试连结果做单块或分块下载。
 *
 * 不提供暂停、取消或停止。某个分块失败时，其余分块停止读取。
 *
 * @file
 */
package com.arturoyi.fastprism.engine

import com.arturoyi.fastprism.core.error.DownloadError
import com.arturoyi.fastprism.core.error.ErrorCode
import com.arturoyi.fastprism.core.error.ResumeFailedCause
import com.arturoyi.fastprism.core.error.TerminalCause
import com.arturoyi.fastprism.core.model.ContentFingerprint
import com.arturoyi.fastprism.core.model.Defaults
import com.arturoyi.fastprism.core.model.DownloadBlock
import com.arturoyi.fastprism.core.model.DownloadRequest
import com.arturoyi.fastprism.core.model.HttpRequest
import com.arturoyi.fastprism.core.strategy.BlockSplitter
import com.arturoyi.fastprism.engine.file.DefaultFilePolicy
import com.arturoyi.fastprism.engine.file.DispositionVerdict
import com.arturoyi.fastprism.engine.file.FileAllocationException
import com.arturoyi.fastprism.engine.file.dispositionVerdict
import com.arturoyi.fastprism.engine.http.OkHttpDownloadClient
import com.arturoyi.fastprism.engine.io.BlockWriter
import com.arturoyi.fastprism.engine.io.FileSink
import com.arturoyi.fastprism.engine.io.FlushOrderGuard
import com.arturoyi.fastprism.engine.io.PathFileLock
import com.arturoyi.fastprism.engine.store.InMemoryBreakpointStore
import com.arturoyi.fastprism.engine.store.RemitSyncExecutor
import com.arturoyi.fastprism.engine.trial.ConnectTrial
import com.arturoyi.fastprism.engine.trial.TrialBaseline
import com.arturoyi.fastprism.engine.trial.TrialDecision
import com.arturoyi.fastprism.engine.trial.conditionalHeaders
import java.io.File
import java.io.IOException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import java.util.concurrent.ExecutionException
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.atomic.AtomicReference
import java.util.logging.Logger

/**
 * 进程在同步之后、断点提交之前退出的模拟信号。
 *
 * 生产调用不会抛出它。测试在 [BlockDownloadCall.afterCommit] 里抛出，用来丢掉尚未提交的偏移。
 */
internal class CrashInjection : RuntimeException("模拟进程退出")

/**
 * 试连之后按分块写入内部目录。
 *
 * 同一个目标路径同时只能有一个调用。写入使用临时文件，全部同步完成后再改成最终名字。
 * 进度回调里的字节数不超过已经 `sync` 的字节数。
 *
 * @param request 资源地址和内部相对路径。
 * @param storageRoot 允许写入的根目录。
 * @param store 断点和指纹。同一个实例可以在失败或模拟崩溃后继续用。
 * @param client 试连和分块请求共用的客户端。
 * @param locks 按目标路径互斥。测试可以传入自己的实例。
 * @param listener 进度和重下原因。默认不接收。
 * @param remitDelayMs 偏移合并窗口，单位毫秒。
 * @param files 临时文件和预分配。
 */
public class BlockDownloadCall(
    private val request: DownloadRequest,
    private val storageRoot: File,
    private val store: InMemoryBreakpointStore,
    private val client: OkHttpDownloadClient = OkHttpDownloadClient(),
    private val locks: PathFileLock = Companion.sharedLocks,
    private val listener: BlockDownloadListener = BlockDownloadListener.NONE,
    private val remitDelayMs: Long = Defaults.REMIT_DELAY_MS,
    private val files: DefaultFilePolicy = DefaultFilePolicy(),
) {
    private val trial = ConnectTrial(client)
    private var taskId: String = ""
    private var remit: RemitSyncExecutor? = null

    /**
     * 每提交一段字节后调用。
     *
     * 参数是当前已提交字节数，以及立刻把合并器写进存储的动作。测试用来在中途刷一段再模拟退出。
     */
    @Volatile
    internal var afterCommit: ((committed: Long, flush: () -> Unit) -> Unit)? = null

    /**
     * 下载到 [DownloadRequest.destination]。
     *
     * 目标正在被别的调用写入时返回 [DownloadResult.Busy]。试连判定要重下时会通知监听器，清掉旧分块并再试一次。
     *
     * @return 成功、失败或文件忙。
     * @throws CrashInjection 测试用的崩溃点被触发。锁会释放，未提交的偏移不会写进存储。
     */
    public fun execute(): DownloadResult {
        val target = request.destination.resolveInside(storageRoot)
        val acquired = try {
            locks.tryAcquire(target)
        } catch (error: IOException) {
            return failure(ErrorCode.UNKNOWN, error.message ?: "路径无法解析")
        }
        if (!acquired) return DownloadResult.Busy(TerminalCause.FILE_BUSY)
        taskId = store.open(request).id
        val opened = FlushOrderGuard.openRemit(store, remitDelayMs)
        remit = opened
        var crashed = false
        try {
            return runDownload(target)
        } catch (crash: CrashInjection) {
            crashed = true
            throw crash
        } catch (failed: BlockFailed) {
            if (taskId.isNotEmpty()) store.markFailed(taskId, failed.error)
            return DownloadResult.Failure(failed.error)
        } catch (error: Throwable) {
            return failure(errorCode(error), error.message ?: errorCode(error).name, cause = error)
        } finally {
            if (!crashed) {
                opened.forceFlush()
                if (taskId.isNotEmpty()) store.clearDirty(taskId)
            }
            opened.close()
            remit = null
            locks.release(target)
        }
    }

    /**
     * 做本地检查、试连和写入。需要重下时再走一遍，且第二次不再自动重下。
     *
     * @param target 最终文件。
     * @return 这一次调用的结果。
     */
    private fun runDownload(target: File): DownloadResult {
        val part = files.partFile(storageRoot, request.destination, taskId)
        var restartAllowed = true
        while (true) {
            val finished = step(part, target, restartAllowed)
            if (finished != null) return finished
            restartAllowed = false
        }
    }

    /**
     * 执行一轮试连和写入。
     *
     * @param part 临时文件。
     * @param target 最终文件。
     * @param restartAllowed 是否还能因为断点无效而从头再来。
     * @return 结束结果。需要再试一轮时返回 null。
     */
    private fun step(part: File, target: File, restartAllowed: Boolean): DownloadResult? {
        localRestartCause(part, target)?.let { cause ->
            if (!restartAllowed) return failure(errorCode(cause), cause.name, resume = cause)
            restart(part, target, cause)
            return null
        }
        val decision = trial.execute(request.url, request.headers, store.baseline(taskId))
        if (dispositionVerdict(trial.contentDisposition) == DispositionVerdict.REJECT) {
            return failure(
                ErrorCode.SECURITY_PATH_TRAVERSAL,
                "Content-Disposition 文件名不安全",
                httpCode = decision.httpCode,
            )
        }
        return when (decision) {
            is TrialDecision.Failed -> failure(
                decision.errorCode,
                "HTTP ${decision.httpCode}",
                httpCode = decision.httpCode,
            )
            is TrialDecision.Restart -> {
                if (!restartAllowed) {
                    failure(errorCode(decision.cause), decision.cause.name, decision.httpCode, decision.cause)
                } else {
                    restart(part, target, decision.cause)
                    null
                }
            }
            is TrialDecision.ServerIgnoredRange -> {
                if (!restartAllowed) {
                    failure(decision.errorCode, decision.errorCode.name, decision.httpCode)
                } else {
                    logger.info("重下 ${decision.errorCode.name}")
                    listener.onRangeIgnored(decision.errorCode)
                    files.discard(part, target)
                    store.clearBlocks(taskId)
                    store.clearDirty(taskId)
                    null
                }
            }
            is TrialDecision.AlreadyComplete -> finish(part, target, decision.totalBytes)
            is TrialDecision.Resumable -> write(part, target, decision.fingerprint, useRanges = true)
            is TrialDecision.FromStart -> write(part, target, decision.fingerprint, useRanges = false)
        }
    }

    /**
     * 按指纹写入临时文件，写完后改成最终名字。
     *
     * 长度为已知且服务器接受区间时按分块并行。否则单连接顺序写。
     *
     * @param part 临时文件。
     * @param target 最终文件。
     * @param fingerprint 这次试连的指纹。
     * @param useRanges 是否按分块发送 `Range`。
     * @return 成功，或写入没有覆盖全部字节时的失败。
     */
    private fun write(
        part: File,
        target: File,
        fingerprint: ContentFingerprint,
        useRanges: Boolean,
    ): DownloadResult {
        store.saveFingerprint(taskId, fingerprint)
        store.markDirty(taskId)
        store.markDownloading(taskId)
        val total = fingerprint.contentLength
        if (total >= 0L && !fingerprint.isChunked) {
            val blocks = resolveBlocks(total, useRanges)
            store.replaceBlocks(taskId, blocks)
            if (alreadyOnDisk(part, target, total)) return finish(part, target, total)
            files.preallocate(part, total)
            writeBlocks(part, blocks, total, useRanges, fingerprint)
            remit().forceFlush()
            if (store.downloaded(taskId) != total) {
                return failure(ErrorCode.UNKNOWN, "写入字节和资源长度不一致")
            }
            return finish(part, target, total)
        }
        writeUnsized(part, fingerprint)
        return finish(part, target, part.length(), reportedTotal = fingerprint.contentLength)
    }

    /**
     * 写下每个未完成的分块。多个分块各用一个文件句柄。
     *
     * @param part 已经预分配的临时文件。
     * @param blocks 覆盖整个资源的分块。
     * @param total 资源总长度。
     * @param useRanges 是否发送区间请求。
     * @param fingerprint 用来生成条件头。
     */
    private fun writeBlocks(
        part: File,
        blocks: List<DownloadBlock>,
        total: Long,
        useRanges: Boolean,
        fingerprint: ContentFingerprint,
    ) {
        val token = InterruptToken()
        val failure = AtomicReference<Throwable>()
        val committed = AtomicLong(store.downloaded(taskId))
        val writer = BlockWriter(token, request.bufferSize)
        listener.onProgress(committed.get(), total)
        val pool = Executors.newFixedThreadPool(blocks.size.coerceAtLeast(1)) { runnable ->
            Thread(runnable, "fast-prism-block").apply { isDaemon = true }
        }
        try {
            val futures = blocks.map { block ->
                pool.submit {
                    try {
                        if (!block.isDone) {
                            writeOne(part, block, writer, token, committed, total, useRanges, fingerprint)
                        }
                    } catch (crash: CrashInjection) {
                        token.interrupt()
                        throw crash
                    } catch (error: Throwable) {
                        failure.compareAndSet(null, error)
                        token.interrupt()
                    }
                }
            }
            futures.forEach { future ->
                try {
                    future.get()
                } catch (wrapped: ExecutionException) {
                    val cause = wrapped.cause
                    if (cause is CrashInjection) throw cause
                }
            }
        } finally {
            pool.shutdownNow()
        }
        val thrown = failure.get() ?: return
        if (thrown is BlockFailed) throw thrown
        throw thrown
    }

    /**
     * 把一个分块的剩余区间写进 [part]。
     *
     * @param part 临时文件。
     * @param block 开始写入时的分块快照。
     * @param writer 读取和切段。
     * @param token 失败信号。
     * @param committed 所有分块已经提交的字节数。
     * @param total 资源总长度，用来报告进度。
     * @param useRanges 是否带 `Range`。
     * @param fingerprint 条件头用的标签和修改时间。
     */
    private fun writeOne(
        part: File,
        block: DownloadBlock,
        writer: BlockWriter,
        token: InterruptToken,
        committed: AtomicLong,
        total: Long,
        useRanges: Boolean,
        fingerprint: ContentFingerprint,
    ) {
        val baseline = TrialBaseline(
            downloadedBytes = block.currentOffset,
            totalBytes = total,
            eTag = fingerprint.eTag,
            lastModified = fingerprint.lastModified,
        )
        val range = if (useRanges) block.rangeLeft..block.rangeRight else null
        val response = client.execute(http(range, baseline))
        token.onInterrupt { response.close() }
        response.use { httpResponse ->
            if (useRanges && httpResponse.code != 206 && !(httpResponse.code == 200 && block.startOffset == 0L)) {
                throw BlockFailed(
                    downloadError(ErrorCode.SERVER_CANCELED, "HTTP ${httpResponse.code}", httpResponse.code),
                )
            }
            if (!useRanges && httpResponse.code !in 200..299) {
                throw BlockFailed(
                    downloadError(ErrorCode.UNKNOWN, "HTTP ${httpResponse.code}", httpResponse.code),
                )
            }
            val start = httpResponse.contentRange?.start
            if (start != null && start != block.rangeLeft) {
                throw BlockFailed(
                    downloadError(
                        ErrorCode.UNKNOWN,
                        "响应区间起点是 $start",
                        httpResponse.code,
                        ResumeFailedCause.RANGE_NOT_SATISFIABLE,
                    ),
                )
            }
            val body = httpResponse.body ?: return@use
            FileSink(part).use { sink ->
                sink.seek(block.rangeLeft)
                val writing = FlushOrderGuard(sink, taskId, remit())
                val remaining = block.contentLength - block.currentOffset
                try {
                    writer.pump(body, remaining) { bytes, off, len ->
                        writing.writeAndCommit(block.index, bytes, off, len)
                        val done = committed.addAndGet(len.toLong())
                        listener.onProgress(done, total)
                        afterCommit?.invoke(done) { remit().forceFlush() }
                    }
                } catch (crash: CrashInjection) {
                    throw crash
                } catch (error: IOException) {
                    if (token.isInterrupted()) return@use
                    throw error
                }
            }
        }
    }

    /**
     * 顺序写入长度未知的响应。
     *
     * 读的过程中同步文件。读完才建立分块并提交偏移，因此中途退出时存储偏移不会超前。
     *
     * @param part 临时文件。存储进度为 0 时会先把已有文件截断。
     * @param fingerprint 进度总长度，未知时为 `-1`。
     */
    private fun writeUnsized(part: File, fingerprint: ContentFingerprint) {
        files.ensureParent(part)
        if (store.downloaded(taskId) == 0L && part.exists()) part.delete()
        val response = client.execute(http(range = null, baseline = store.baseline(taskId)))
        response.use { httpResponse ->
            if (httpResponse.code !in 200..299) {
                throw BlockFailed(
                    downloadError(ErrorCode.UNKNOWN, "HTTP ${httpResponse.code}", httpResponse.code),
                )
            }
            val body = httpResponse.body ?: return@use
            var read = 0L
            FileSink(part).use { sink ->
                sink.setLength(0L)
                val writer = BlockWriter(InterruptToken(), request.bufferSize)
                writer.pump(body, remaining = null) { bytes, off, len ->
                    sink.seek(read)
                    sink.write(bytes, off, len)
                    sink.flushAndSync()
                    read += len
                    listener.onProgress(read, fingerprint.contentLength)
                }
                store.replaceBlocks(
                    taskId,
                    listOf(
                        DownloadBlock(
                            recordId = taskId,
                            index = 0,
                            startOffset = 0L,
                            contentLength = read,
                        ),
                    ),
                )
                FlushOrderGuard(sink, taskId, remit()).commitSynced(0, read)
            }
        }
    }

    /**
     * 把临时文件改成最终文件，并清掉脏标记。
     *
     * @param part 临时文件。不存在时要求最终文件已经就位。
     * @param target 最终文件。
     * @param total 期望的字节数。用来核对最终文件。
     * @param reportedTotal 进度回调里的总长度。未知时为 `-1`。
     * @return 文件长度符合时为成功。
     */
    private fun finish(
        part: File,
        target: File,
        total: Long,
        reportedTotal: Long = total,
    ): DownloadResult {
        remit().forceFlush()
        if (part.exists()) files.commit(part, target)
        if (!target.isFile && total == 0L) {
            target.parentFile?.mkdirs()
            target.createNewFile()
        }
        if (!target.isFile || (total >= 0L && target.length() != total)) {
            return failure(ErrorCode.UNKNOWN, "完成后的文件长度不符")
        }
        store.clearDirty(taskId)
        store.markCompleted(taskId)
        listener.onProgress(store.downloaded(taskId), reportedTotal)
        return DownloadResult.Success(target.length())
    }

    /**
     * 保留仍然衔接的旧分块，否则按本次长度重新切分。
     *
     * @param total 资源总长度。
     * @param useRanges 为 false 时只切 1 块。
     * @return 即将写入的分块。
     */
    private fun resolveBlocks(total: Long, useRanges: Boolean): List<DownloadBlock> {
        val existing = store.blocksOf(taskId)
        if (blocksFit(existing, total)) return existing
        return BlockSplitter.split(taskId, total, request.connectionCount, supportSeek = useRanges)
    }

    /**
     * 判断分块是否从 0 无重叠地覆盖 [total]。
     *
     * @param blocks 已保存的分块。
     * @param total 资源总长度。
     * @return 可以沿用这些分块时为 true。
     */
    private fun blocksFit(blocks: List<DownloadBlock>, total: Long): Boolean {
        if (blocks.isEmpty()) return false
        val sorted = blocks.sortedBy { it.index }
        if (sorted.map { it.index } != sorted.indices.toList()) return false
        if (sorted.sumOf { it.contentLength } != total) return false
        var cursor = 0L
        for (block in sorted) {
            if (block.startOffset != cursor) return false
            cursor += block.contentLength
        }
        return cursor == total
    }

    /**
     * 分块都已写满，并且临时文件或最终文件的长度就是 [total]。
     *
     * @param part 临时文件。
     * @param target 最终文件。
     * @param total 资源总长度。
     * @return 不需要再读网络时为 true。
     */
    private fun alreadyOnDisk(part: File, target: File, total: Long): Boolean {
        val blocks = store.blocksOf(taskId)
        if (blocks.isEmpty() || blocks.any { !it.isDone }) return false
        if (blocks.sumOf { it.contentLength } != total) return false
        return matches(target, total) || matches(part, total)
    }

    /**
     * 找出必须丢掉本地字节的原因。
     *
     * 脏标记、文件丢失、文件比记录更短，或文件比资源总长度更长，都不能续传。
     *
     * @param part 临时文件。
     * @param target 最终文件。
     * @return 可以继续试连时为 null。
     */
    private fun localRestartCause(part: File, target: File): ResumeFailedCause? {
        if (store.isDirty(taskId)) return ResumeFailedCause.INFO_DIRTY
        val downloaded = store.downloaded(taskId)
        val total = store.get(taskId)?.totalBytes ?: -1L
        val file = when {
            part.isFile -> part
            target.isFile -> target
            else -> null
        }
        if (downloaded > 0L && file == null) return ResumeFailedCause.FILE_NOT_EXIST
        if (file != null && total >= 0L && file.length() > total) return ResumeFailedCause.INFO_DIRTY
        if (file != null && file.length() < downloaded) return ResumeFailedCause.INFO_DIRTY
        return null
    }

    /**
     * 通知监听器，删掉两个文件，并清空分块。
     *
     * @param part 临时文件。
     * @param target 最终文件。
     * @param cause 写进日志和回调的原因。
     */
    private fun restart(part: File, target: File, cause: ResumeFailedCause) {
        logger.info("重下 ${cause.name}")
        listener.onResumeFailed(cause)
        files.discard(part, target)
        store.clearBlocks(taskId)
        store.clearDirty(taskId)
    }

    /**
     * 组装一个带条件头的 GET。
     *
     * @param range 闭区间。null 表示不发送 `Range`。
     * @param baseline 标签和修改时间。
     * @return 交给客户端的请求。
     */
    private fun http(range: LongRange?, baseline: TrialBaseline): HttpRequest {
        return HttpRequest(
            url = request.url,
            method = "GET",
            headers = conditionalHeaders(request.headers, baseline),
            range = range,
            connectTimeoutMs = Defaults.CONNECT_TIMEOUT_MS,
            readTimeoutMs = Defaults.READ_TIMEOUT_MS,
        )
    }

    private fun remit(): RemitSyncExecutor = checkNotNull(remit) { "合并器尚未打开" }

    private fun failure(
        code: ErrorCode,
        message: String,
        httpCode: Int? = null,
        resume: ResumeFailedCause? = null,
        cause: Throwable? = null,
    ): DownloadResult.Failure {
        val error = downloadError(code, message, httpCode, resume, cause)
        if (taskId.isNotEmpty()) store.markFailed(taskId, error)
        return DownloadResult.Failure(error)
    }

    private fun downloadError(
        code: ErrorCode,
        message: String,
        httpCode: Int? = null,
        resume: ResumeFailedCause? = null,
        cause: Throwable? = null,
    ): DownloadError {
        return DownloadError(
            code = code,
            message = message.ifBlank { code.name },
            traceId = taskId.ifBlank { "unknown" },
            httpCode = httpCode,
            resumeFailedCause = resume,
            cause = cause,
        )
    }

    private fun errorCode(cause: ResumeFailedCause): ErrorCode {
        return when (cause) {
            ResumeFailedCause.RESPONSE_PRECONDITION_FAILED -> ErrorCode.HTTP_PRECONDITION_FAILED
            ResumeFailedCause.FILE_NOT_EXIST -> ErrorCode.FILE_NOT_CREATED
            else -> ErrorCode.UNKNOWN
        }
    }

    private fun errorCode(error: Throwable): ErrorCode {
        return when (error) {
            is FileAllocationException -> ErrorCode.FILE_ALLOCATION_FAILED
            is SocketTimeoutException -> ErrorCode.CONNECTION_TIMEOUT
            is UnknownHostException -> ErrorCode.UNKNOWN_HOST
            is BlockFailed -> error.error.code
            else -> ErrorCode.UNKNOWN
        }
    }

    private companion object {
        private val logger: Logger = Logger.getLogger("com.arturoyi.fastprism.engine.BlockDownloadCall")
        private val sharedLocks: PathFileLock = PathFileLock()

        private fun matches(file: File, total: Long): Boolean = file.isFile && file.length() == total
    }
}

/**
 * 分块请求已经不能继续。
 *
 * @property error 要返回给调用方的失败。
 */
private class BlockFailed(
    val error: DownloadError,
) : IOException(error.message)
