/**
 * 分片数变化时，偏移写回次数不能随着读缓冲线性膨胀。
 *
 * 日常门禁用 2MiB。100MiB 和 1GiB 用 `-Dbenchmark.profile=full` 或 `huge` 单独跑，
 * 避免每次提交都在内存里放下整份样本。
 *
 * @file
 */
package com.arturoyi.fastdowload.benchmark

import com.arturoyi.fastdowload.core.model.Destination
import com.arturoyi.fastdowload.core.model.DownloadRequest
import com.arturoyi.fastdowload.engine.BlockDownloadCall
import com.arturoyi.fastdowload.engine.DownloadResult
import com.arturoyi.fastdowload.engine.store.InMemoryBreakpointStore
import com.arturoyi.fastdowload.testing.MockDownloadServerRule
import com.arturoyi.fastdowload.testing.RangedResourceDispatcher
import com.arturoyi.fastdowload.testing.patternedBody
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.condition.EnabledIfSystemProperty
import org.junit.jupiter.api.extension.RegisterExtension
import java.nio.file.Files
import java.util.concurrent.atomic.AtomicInteger
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.round

/**
 * 1、2、4、8 个分片下的写回次数相对分片数偏差不超过 10%。
 */
class DownloadBenchmarkTest {
    /** 每个测试方法一台本地服务器。 */
    @JvmField
    @RegisterExtension
    val server = MockDownloadServerRule()

    /**
     * 2MiB 在四个分片档位上，成功提交偏移的次数接近分片数，而不是每个读缓冲一次。
     */
    @Test
    fun smokeWriteCountStaysWithinTenPercent() {
        assertWriteCount(2 * 1024 * 1024)
    }

    /**
     * 100MiB。只在明确要求全量基准时运行。
     */
    @Test
    @EnabledIfSystemProperty(named = "benchmark.profile", matches = "full|huge")
    fun fullWriteCountStaysWithinTenPercent() {
        assertWriteCount(100 * 1024 * 1024)
    }

    private fun assertWriteCount(size: Int) {
        val body = patternedBody(size)
        server.server.dispatcher = RangedResourceDispatcher(body)
        listOf(1, 2, 4, 8).forEach { blocks ->
            val writes = AtomicInteger()
            val store = InMemoryBreakpointStore()
            store.setFlushObserver { writes.incrementAndGet() }
            val root = Files.createTempDirectory("fast-dowload-bench").toFile()
            val started = System.nanoTime()
            val result = BlockDownloadCall(
                request = DownloadRequest(
                    url = server.url("/file.bin"),
                    destination = Destination("downloads/file.bin"),
                    connectionCount = blocks,
                ),
                storageRoot = root,
                store = store,
                remitDelayMs = 60_000,
            ).execute()
            val elapsedMs = (System.nanoTime() - started) / 1_000_000L
            assertInstanceOf(DownloadResult.Success::class.java, result)
            val expected = blocks
            val tolerance = max(1, round(expected * 0.10).toInt())
            val actual = writes.get()
            assertTrue(abs(actual - expected) <= tolerance) {
                "size=$size blocks=$blocks writes=$actual elapsedMs=$elapsedMs"
            }
        }
    }
}
