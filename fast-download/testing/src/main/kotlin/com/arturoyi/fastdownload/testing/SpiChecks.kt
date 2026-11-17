/**
 * 两份实现都要满足的行为。
 *
 * @file
 */
package com.arturoyi.fastdownload.testing

import com.arturoyi.fastdownload.core.error.ErrorCode
import com.arturoyi.fastdownload.core.event.DownloadEvent
import com.arturoyi.fastdownload.core.model.Defaults
import com.arturoyi.fastdownload.core.model.Destination
import com.arturoyi.fastdownload.core.model.DownloadRequest
import com.arturoyi.fastdownload.core.model.HttpRequest
import com.arturoyi.fastdownload.core.model.IntegritySpec
import com.arturoyi.fastdownload.core.model.NetworkType
import com.arturoyi.fastdownload.core.model.PublicIds
import com.arturoyi.fastdownload.core.monitor.DownloadMonitor
import com.arturoyi.fastdownload.core.monitor.Stage
import com.arturoyi.fastdownload.core.spi.BackgroundExecutor
import com.arturoyi.fastdownload.core.spi.BreakpointStrategy
import com.arturoyi.fastdownload.core.spi.DownloadEventListener
import com.arturoyi.fastdownload.core.spi.EventDispatcher
import com.arturoyi.fastdownload.core.spi.FilePolicy
import com.arturoyi.fastdownload.core.spi.HttpClient
import com.arturoyi.fastdownload.core.spi.IntegrityVerifier
import com.arturoyi.fastdownload.core.spi.NetworkPolicy
import com.arturoyi.fastdownload.core.spi.OutputTarget
import com.arturoyi.fastdownload.core.spi.RateLimiter
import com.arturoyi.fastdownload.core.spi.TaskScheduler
import java.io.Closeable
import java.io.File
import java.security.MessageDigest
import java.util.Base64

/**
 * 分块必须从 0 无缝覆盖到文件末尾。不能定位时只有一块。
 */
public object BreakpointStrategyChecks {
    /**
     * 跑覆盖、单块回退和块数自洽。
     *
     * @param strategy 待检查的策略。
     */
    public fun verify(strategy: BreakpointStrategy) {
        val total = 10L * 1024L * 1024L
        check(strategy.blockCount(-1L, null, true) == 1) { "未知长度应为 1 块" }
        check(strategy.blockCount(total, 4, false) == 1) { "不能定位时应为 1 块" }
        val blocks = strategy.split("task", total, hint = 4, supportSeek = true)
        check(blocks.isNotEmpty())
        check(blocks.size == strategy.blockCount(total, 4, true))
        check(blocks.sumOf { it.contentLength } == total) { "块长度之和应等于资源长度" }
        var cursor = 0L
        blocks.forEachIndexed { index, block ->
            check(block.index == index)
            check(block.startOffset == cursor) { "块之间不能有空隙" }
            check(block.currentOffset == 0L)
            cursor += block.contentLength
        }
    }
}

/**
 * 临时文件提交后，目标内容与临时文件一致，临时文件不再留在原处。
 */
public object FilePolicyChecks {
    /**
     * 预分配、提交和删除都作用在 [root] 里面。
     *
     * @param policy 待检查的策略。
     * @param root 允许写入的根目录。
     */
    public fun verify(policy: FilePolicy, root: File) {
        val destination = Destination("nested/out.bin")
        val part = policy.partFile(root, destination, "abc")
        policy.preallocate(part, 3L)
        part.writeBytes(byteArrayOf(1, 2, 3))
        val target = destination.resolveInside(root)
        policy.commit(part, target)
        check(target.readBytes().contentEquals(byteArrayOf(1, 2, 3))) { "提交后的内容应与临时文件一致" }
        check(!part.exists()) { "提交后临时文件不应留在原处" }
        policy.discard(part, target)
        check(!target.exists()) { "丢弃后目标文件不应存在" }
    }
}

/**
 * 入队返回稳定标识，取消未知任务不抛异常。
 */
public object TaskSchedulerChecks {
    /**
     * 用一条新请求检查 [scheduler]。
     *
     * 如果它实现了 [Closeable]，检查结束后会关闭。
     *
     * @param scheduler 待检查的调度器。
     * @param namespace 计算任务标识时使用的名字，必须和调度器自己的命名空间一致。
     */
    public fun verify(scheduler: TaskScheduler, namespace: String = Defaults.GROUP) {
        try {
            val request = DownloadRequest(
                url = "https://example.com/queue.bin",
                destination = Destination("queue.bin"),
            )
            val id = scheduler.enqueue(request)
            val expected = PublicIds.generate(request.url, request.destination, namespace)
            check(id == expected) { "标识应为 $expected，实际 $id" }
            scheduler.cancel("missing")
            scheduler.cancel(id)
            scheduler.setConcurrentLimit(0)
            scheduler.setConcurrentLimit(2)
        } finally {
            (scheduler as? Closeable)?.close()
        }
    }
}

/**
 * 登记的监听器能收到事件，摘掉之后不再收到。
 */
public object EventDispatcherChecks {
    /**
     * 投递一条开始事件，再确认摘除生效。
     *
     * 异步实现最多等一秒。
     *
     * @param dispatcher 待检查的投递器。
     */
    public fun verify(dispatcher: EventDispatcher) {
        val seen = mutableListOf<String>()
        val listener = DownloadEventListener { event ->
            synchronized(seen) { seen += event.publicId }
        }
        dispatcher.addListener(listener)
        dispatcher.dispatch(DownloadEvent.Started("a"))
        waitUntil { synchronized(seen) { seen.contains("a") } }
        dispatcher.removeListener(listener)
        dispatcher.dispatch(DownloadEvent.Started("b"))
        Thread.sleep(100)
        val copy = synchronized(seen) { seen.toList() }
        check(copy == listOf("a")) { "摘除后不应再收到事件，实际 $copy" }
        (dispatcher as? Closeable)?.close()
    }

    private fun waitUntil(condition: () -> Boolean) {
        val deadline = System.nanoTime() + 1_000_000_000L
        while (!condition()) {
            check(System.nanoTime() < deadline) { "一秒内没有收到事件" }
            Thread.sleep(10)
        }
    }
}

/**
 * 三种网络要求在同一组连通情况下结论一致。
 */
public object NetworkPolicyChecks {
    /**
     * 把 [actual] 和 [expected] 对全部网络类型比较。
     *
     * 两份实现应给出相同答案。
     *
     * @param actual 待检查的策略。
     * @param expected 对照策略。
     */
    public fun verify(actual: NetworkPolicy, expected: NetworkPolicy) {
        NetworkType.entries.forEach { type ->
            check(actual.isAllowed(type) == expected.isAllowed(type)) {
                "$type 的结论不一致"
            }
        }
    }
}

/**
 * 不限速的实现应立刻返回。
 */
public object RateLimiterChecks {
    /**
     * [acquire] 领取 0 字节和一块缓冲区都应在 200 毫秒内返回。
     *
     * @param limiter 待检查的限速器。
     */
    public fun verifyUnlimited(limiter: RateLimiter) {
        val started = System.nanoTime()
        limiter.acquire(0)
        limiter.acquire(Defaults.BUFFER_SIZE)
        val elapsedMs = (System.nanoTime() - started) / 1_000_000L
        check(elapsedMs < 200L) { "不限速时不应等待，实际 ${elapsedMs}ms" }
    }
}

/**
 * 阶段、重试、重下、写库和成功各记一次之后，快照里的数字一致。
 */
public object DownloadMonitorChecks {
    /**
     * 写入一组固定事件并核对 [com.arturoyi.fastdownload.core.monitor.MonitorSnapshot]。
     *
     * @param monitor 待检查的监控。
     */
    public fun verify(monitor: DownloadMonitor) {
        monitor.onStage("task", Stage.TRIAL, 5L)
        monitor.onRetry("task", 1, 10L, ErrorCode.CONNECTION_TIMEOUT)
        monitor.onRedownload("task", "ETAG_CHANGED")
        monitor.onRepositoryWrite()
        monitor.onTerminal("task", true, null)
        val snapshot = monitor.snapshot()
        check(snapshot.stageMillis[Stage.TRIAL] == 5L)
        check(snapshot.retryCounts[ErrorCode.CONNECTION_TIMEOUT] == 1)
        check(snapshot.redownloadCount == 1L)
        check(snapshot.repositoryWrites == 1L)
        check(snapshot.completedCount == 1L)
        check(snapshot.failedCount == 0L)
    }
}

/**
 * 摘要一致时通过，不一致或空的 `Content-MD5` 时拒绝。文件内容不被修改。
 */
public object IntegrityVerifierChecks {
    /**
     * 用一份 4 字节文件检查 SHA-256、MD5 和 `Content-MD5`。
     *
     * @param verifier 待检查的校验器。
     */
    public fun verify(verifier: IntegrityVerifier) {
        val file = File.createTempFile("integrity", ".bin")
        try {
            val body = byteArrayOf(1, 2, 3, 4)
            file.writeBytes(body)
            val sha = hex(body, "SHA-256")
            val md5 = hex(body, "MD5")
            check(verifier.verify(file, IntegritySpec.Sha256(sha), null))
            check(!verifier.verify(file, IntegritySpec.Sha256("0".repeat(64)), null))
            check(verifier.verify(file, IntegritySpec.Md5(md5), null))
            val contentMd5 = Base64.getEncoder().encodeToString(digest(body, "MD5"))
            check(verifier.verify(file, IntegritySpec.ContentMd5, contentMd5))
            check(!verifier.verify(file, IntegritySpec.ContentMd5, null))
            check(file.readBytes().contentEquals(body)) { "校验不应改文件" }
        } finally {
            file.delete()
        }
    }

    private fun hex(body: ByteArray, algorithm: String): String {
        return digest(body, algorithm).joinToString(separator = "") { byte ->
            "%02x".format(byte.toInt() and 0xff)
        }
    }

    private fun digest(body: ByteArray, algorithm: String): ByteArray {
        return MessageDigest.getInstance(algorithm).digest(body)
    }
}

/**
 * 能定位的目标写入指定偏移后，读回的字节就是写入的那个值。
 */
public object OutputTargetChecks {
    /**
     * 在偏移 1 写入一个字节，再提交。
     *
     * @param target 待检查的目标。
     * @param readBack 读取当前内容。文件实现读文件，内存实现读自己的数组。
     */
    public fun verify(target: OutputTarget, readBack: () -> ByteArray) {
        check(target.supportSeek()) { "这份契约只覆盖可定位目标" }
        target.preAllocate(4L)
        target.openSink().use { sink ->
            sink.seek(1L)
            sink.write(byteArrayOf(7), 0, 1)
            sink.flushAndSync()
        }
        val bytes = readBack()
        check(bytes.size >= 2 && bytes[1] == 7.toByte()) { "偏移 1 应为 7，实际 ${bytes.toList()}" }
        target.atomicCommit()
    }
}

/**
 * 同一份资源上，GET、区间和 HEAD 的结果一致。
 */
public object HttpClientChecks {
    /**
     * 下载整份 [body]，再取前 4 个字节，并核对 HEAD 长度。
     *
     * 服务器必须按区间返回 206。检查结束时关闭 [client]。
     *
     * @param client 待检查的客户端。
     * @param url 资源绝对地址。
     * @param body 服务器上的完整正文。至少 4 字节。
     */
    public fun verify(client: HttpClient, url: String, body: ByteArray) {
        check(body.size >= 4) { "正文至少 4 字节" }
        try {
            client.execute(request(url)).use { response ->
                check(response.code == 200) { "整包状态应为 200，实际 ${response.code}" }
                val read = response.body?.readBytes() ?: ByteArray(0)
                check(read.contentEquals(body)) { "整包正文不一致" }
            }
            client.execute(request(url, range = 0L..3L)).use { response ->
                check(response.code == 206) { "区间状态应为 206，实际 ${response.code}" }
                val read = response.body?.readBytes() ?: ByteArray(0)
                check(read.contentEquals(body.copyOfRange(0, 4))) { "区间正文不一致" }
            }
            check(client.getContentLength(request(url)) == body.size.toLong()) {
                "HEAD 长度应为 ${body.size}"
            }
        } finally {
            client.close()
        }
    }

    private fun request(url: String, range: LongRange? = null): HttpRequest {
        return HttpRequest(
            url = url,
            method = "GET",
            headers = emptyMap(),
            range = range,
            connectTimeoutMs = Defaults.CONNECT_TIMEOUT_MS,
            readTimeoutMs = Defaults.READ_TIMEOUT_MS,
        )
    }
}

/**
 * 活跃数跟着 [BackgroundExecutor.onActiveCount] 变化，关闭后归零。
 */
public object BackgroundExecutorChecks {
    /**
     * 先记 2，再记 0，然后关闭。
     *
     * @param executor 待检查的保活。
     */
    public fun verify(executor: BackgroundExecutor) {
        executor.onActiveCount(2)
        check(executor.activeCount() == 2)
        executor.onActiveCount(0)
        check(executor.activeCount() == 0)
        executor.close()
        check(executor.activeCount() == 0)
    }
}
