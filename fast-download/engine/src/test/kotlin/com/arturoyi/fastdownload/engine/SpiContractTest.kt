/**
 * 每一种可替换实现都走同一份契约。
 *
 * @file
 */
package com.arturoyi.fastdownload.engine

import com.arturoyi.fastdownload.core.model.NetworkType
import com.arturoyi.fastdownload.core.spi.FixedNetworkPolicy
import com.arturoyi.fastdownload.core.spi.RateLimiter
import com.arturoyi.fastdownload.core.strategy.AdaptiveBreakpointStrategy
import com.arturoyi.fastdownload.core.strategy.SingleBlockStrategy
import com.arturoyi.fastdownload.engine.background.RecordingBackgroundExecutor
import com.arturoyi.fastdownload.engine.dispatch.CommandDispatcher
import com.arturoyi.fastdownload.engine.dispatch.DownloadRunner
import com.arturoyi.fastdownload.engine.event.ImmediateEventDispatcher
import com.arturoyi.fastdownload.engine.event.QueuedEventDispatcher
import com.arturoyi.fastdownload.engine.file.CopyOnCommitFilePolicy
import com.arturoyi.fastdownload.engine.file.DefaultFilePolicy
import com.arturoyi.fastdownload.engine.file.FileOutputTarget
import com.arturoyi.fastdownload.engine.http.UrlConnectionHttpClient
import com.arturoyi.fastdownload.engine.limit.GlobalRateLimiter
import com.arturoyi.fastdownload.engine.monitor.LoggingDownloadMonitor
import com.arturoyi.fastdownload.engine.monitor.MemoryDownloadMonitor
import com.arturoyi.fastdownload.engine.schedule.MemoryTaskScheduler
import com.arturoyi.fastdownload.engine.schedule.NetworkSnapshot
import com.arturoyi.fastdownload.engine.schedule.SnapshotNetworkPolicy
import com.arturoyi.fastdownload.engine.store.InMemoryBreakpointStore
import com.arturoyi.fastdownload.engine.verify.ChannelIntegrityVerifier
import com.arturoyi.fastdownload.engine.verify.DefaultIntegrityVerifier
import com.arturoyi.fastdownload.testing.BackgroundExecutorChecks
import com.arturoyi.fastdownload.testing.BreakpointStrategyChecks
import com.arturoyi.fastdownload.testing.DownloadMonitorChecks
import com.arturoyi.fastdownload.testing.EventDispatcherChecks
import com.arturoyi.fastdownload.testing.FilePolicyChecks
import com.arturoyi.fastdownload.testing.HttpClientChecks
import com.arturoyi.fastdownload.testing.IntegrityVerifierChecks
import com.arturoyi.fastdownload.testing.MemoryOutputTarget
import com.arturoyi.fastdownload.testing.MockDownloadServerRule
import com.arturoyi.fastdownload.testing.NetworkPolicyChecks
import com.arturoyi.fastdownload.testing.OutputTargetChecks
import com.arturoyi.fastdownload.testing.RangedResourceDispatcher
import com.arturoyi.fastdownload.testing.RateLimiterChecks
import com.arturoyi.fastdownload.testing.TaskSchedulerChecks
import com.arturoyi.fastdownload.testing.patternedBody
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.RegisterExtension
import java.io.File
import java.nio.file.Files

/**
 * 默认实现和替身实现满足同一份行为。
 */
class SpiContractTest {
    /** 每个测试方法单独启动并关闭的本地服务器。 */
    @JvmField
    @RegisterExtension
    val server = MockDownloadServerRule()

    /** 自适应和单块都覆盖完整区间。 */
    @Test
    fun breakpointStrategiesCoverTheFile() {
        BreakpointStrategyChecks.verify(AdaptiveBreakpointStrategy)
        BreakpointStrategyChecks.verify(SingleBlockStrategy)
    }

    /** 原子改名和复制提交都留下相同的目标内容。 */
    @Test
    fun filePoliciesCommitTheSameBytes() {
        FilePolicyChecks.verify(DefaultFilePolicy(), tempRoot("atomic"))
        FilePolicyChecks.verify(CopyOnCommitFilePolicy(), tempRoot("copy"))
    }

    /** 内存队列和命令调度器都返回稳定标识。 */
    @Test
    fun schedulersEnqueueAndIgnoreMissingCancel() {
        TaskSchedulerChecks.verify(MemoryTaskScheduler())
        TaskSchedulerChecks.verify(
            CommandDispatcher(
                repository = InMemoryBreakpointStore(),
                runner = DownloadRunner { _, _ -> DownloadResult.Success(0) },
                startFrozen = true,
            ),
        )
    }

    /** 同步投递和排队投递都会在摘除后停止。 */
    @Test
    fun eventDispatchersDeliverThenStop() {
        EventDispatcherChecks.verify(ImmediateEventDispatcher())
        EventDispatcherChecks.verify(QueuedEventDispatcher())
    }

    /** 固定策略和快照策略对每种网络给出相同答案。 */
    @Test
    fun networkPoliciesAgree() {
        val cases = listOf(
            Triple(false, false, false),
            Triple(true, false, false),
            Triple(true, true, false),
            Triple(true, false, true),
            Triple(true, true, true),
        )
        cases.forEach { (connected, wifi, unmetered) ->
            NetworkPolicyChecks.verify(
                FixedNetworkPolicy(connected, wifi, unmetered),
                SnapshotNetworkPolicy(NetworkSnapshot(connected, wifi, unmetered)),
            )
        }
        check(!FixedNetworkPolicy(connected = false, wifi = true, unmetered = true).isAllowed(NetworkType.ALL))
    }

    /** 没有上限时，空限制器和令牌桶都不等待。 */
    @Test
    fun unlimitedRateLimitersReturnImmediately() {
        RateLimiterChecks.verifyUnlimited(RateLimiter.UNLIMITED)
        RateLimiterChecks.verifyUnlimited(GlobalRateLimiter().forTask(null))
    }

    /** 写日志的监控和只记内存的监控，快照数字相同。 */
    @Test
    fun monitorsRecordTheSameCounts() {
        DownloadMonitorChecks.verify(MemoryDownloadMonitor())
        DownloadMonitorChecks.verify(LoggingDownloadMonitor(debug = false))
    }

    /** 输入流和文件通道对同一份摘要给出相同结论。 */
    @Test
    fun integrityVerifiersAgree() {
        IntegrityVerifierChecks.verify(DefaultIntegrityVerifier)
        IntegrityVerifierChecks.verify(ChannelIntegrityVerifier)
    }

    /** 普通文件和内存目标都能在指定偏移写入。 */
    @Test
    fun outputTargetsSeekAndWrite() {
        val file = File(tempRoot("file"), "out.bin")
        val fileTarget = FileOutputTarget(file)
        OutputTargetChecks.verify(fileTarget) { file.readBytes() }
        val memory = MemoryOutputTarget()
        OutputTargetChecks.verify(memory) { memory.bytes() }
        check(memory.isCommitted())
    }

    /** `HttpURLConnection` 能按区间取回固定正文。 */
    @Test
    fun urlConnectionSatisfiesHttpContract() {
        val body = patternedBody(64)
        server.server.dispatcher = RangedResourceDispatcher(body)
        HttpClientChecks.verify(UrlConnectionHttpClient(), server.url("/file"), body)
    }

    /** 进程内保活只记任务数。 */
    @Test
    fun recordingBackgroundTracksActiveCount() {
        BackgroundExecutorChecks.verify(RecordingBackgroundExecutor())
    }

    private fun tempRoot(name: String): File {
        return Files.createTempDirectory("spi-$name").toFile().apply { deleteOnExit() }
    }
}
