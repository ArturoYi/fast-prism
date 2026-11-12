/**
 * 模型不变量：分块区间、路径限制、请求校验、进度汇总、稳定标识、重试等待和状态名。
 *
 * @file
 */
package com.arturoyi.fastprism.core.model

import com.arturoyi.fastprism.core.state.Status
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.nio.file.Files
import kotlin.random.Random

/**
 * [DownloadBlock] 的绝对区间和完成判断。
 */
class DownloadBlockTest {
    /**
     * 未写入时左边界等于起点；写满后左边界移到终点之后，并且 [DownloadBlock.isDone] 为 true。
     */
    @Test
    fun rangeAndDoneFollowPersistedOffset() {
        val fresh = DownloadBlock(recordId = "a", index = 0, startOffset = 100, contentLength = 40)
        assertEquals(100L, fresh.rangeLeft)
        assertEquals(139L, fresh.rangeRight)
        assertFalse(fresh.isDone)

        val done = fresh.copy(currentOffset = 40)
        assertEquals(140L, done.rangeLeft)
        assertTrue(done.isDone)
    }

    /**
     * 空块一开始就完成，右边界是起点减 1。接近 2 GiB 的偏移仍用 Long 计算，不溢出成负数。
     */
    @Test
    fun emptyBlockIsDoneAndDoesNotOverflowNearTwoGib() {
        val empty = DownloadBlock(recordId = "a", index = 1, startOffset = 0, contentLength = 0)
        assertTrue(empty.isDone)
        assertEquals(-1L, empty.rangeRight)

        val large = DownloadBlock(
            recordId = "a",
            index = 0,
            startOffset = 3_000_000_000L,
            contentLength = 100,
            currentOffset = 40,
        )
        assertEquals(3_000_000_040L, large.rangeLeft)
        assertEquals(3_000_000_099L, large.rangeRight)
    }

    /**
     * 已写入字节数超过分块长度时拒绝构造。
     */
    @Test
    fun rejectsOffsetPastBlock() {
        assertThrows(IllegalArgumentException::class.java) {
            DownloadBlock(recordId = "a", index = 0, startOffset = 0, contentLength = 4, currentOffset = 5)
        }
    }
}

/**
 * [Destination] 拒绝路径穿越，并只解析到给定根目录内部。
 */
class DestinationTest {
    /**
     * 父目录、绝对路径、中间 `..` 和空路径段都不能构造。
     */
    @Test
    fun rejectsTraversalAndAbsolutePaths() {
        assertThrows(IllegalArgumentException::class.java) { Destination("../secret.bin") }
        assertThrows(IllegalArgumentException::class.java) { Destination("/tmp/secret.bin") }
        assertThrows(IllegalArgumentException::class.java) { Destination("downloads/../secret.bin") }
        assertThrows(IllegalArgumentException::class.java) { Destination("downloads//secret.bin") }
    }

    /**
     * 相对路径解析后仍以根目录加分隔符开头，并保留 `downloads/asset.bin` 这两段。
     */
    @Test
    fun resolvesUnderRoot() {
        val root = Files.createTempDirectory("fast-prism-root").toFile()
        val target = Destination("downloads/asset.bin").resolveInside(root)
        assertTrue(target.path.startsWith(root.canonicalPath + java.io.File.separator))
        assertTrue(target.path.endsWith("downloads${java.io.File.separator}asset.bin"))
    }

    /**
     * 根目录内的符号链接指向外部目录时，解析必须失败。
     */
    @Test
    fun rejectsSymlinkThatLeavesRoot() {
        val root = Files.createTempDirectory("fast-prism-root")
        val outside = Files.createTempDirectory("fast-prism-outside")
        Files.createSymbolicLink(root.resolve("link"), outside)
        assertThrows(IllegalArgumentException::class.java) {
            Destination("link/file.bin").resolveInside(root.toFile())
        }
    }
}

/**
 * [DownloadRequest] 接受合法的 HTTPS 内部路径，并拒绝保留头和非 HTTP 地址。
 */
class DownloadRequestTest {
    /**
     * 只给地址和目标时，缓冲区、分组和重试次数使用 [Defaults]。
     */
    @Test
    fun acceptsHttpsAndInternalDestination() {
        val request = sampleRequest()
        assertEquals(Defaults.BUFFER_SIZE, request.bufferSize)
        assertEquals(Defaults.GROUP, request.groupId)
        assertEquals(3, request.retryPolicy.maxAttempts)
    }

    /**
     * 调用方不能设置 `Range`，也不能使用 `ftp` 或带用户信息的地址。
     */
    @Test
    fun rejectsReservedHeadersAndNonHttpUrl() {
        assertThrows(IllegalArgumentException::class.java) {
            sampleRequest(headers = mapOf("Range" to "bytes=0-1"))
        }
        assertThrows(IllegalArgumentException::class.java) {
            sampleRequest(url = "ftp://example.com/a.bin")
        }
        assertThrows(IllegalArgumentException::class.java) {
            sampleRequest(url = "https://user:password@example.com/a.bin")
        }
    }
}

/**
 * [DownloadRecord.withBlocks] 用分块偏移之和替换已下载字节数。
 */
class DownloadRecordTest {
    /**
     * 两个分块的 [DownloadBlock.currentOffset] 相加得到记录上的已下载字节数。
     */
    @Test
    fun downloadedBytesAreSumOfBlocks() {
        val record = DownloadRecord(
            id = "abc",
            request = sampleRequest(),
            status = Status.DOWNLOADING,
            createdAt = 10,
            updatedAt = 12,
        )
        val updated = record.withBlocks(
            listOf(
                DownloadBlock("abc", 0, 0, 10, 4),
                DownloadBlock("abc", 1, 10, 10, 3),
            ),
        )
        assertEquals(7L, updated.downloadedBytes)
    }
}

/**
 * [PublicIds.generate] 对相同输入稳定，并随目标路径变化。
 */
class PublicIdsTest {
    /**
     * 相同输入得到 32 位小写十六进制；只改相对路径时标识必须不同。
     */
    @Test
    fun idIsStable16ByteHex() {
        val destination = Destination("downloads/a.bin")
        val first = PublicIds.generate("https://example.com/a.bin", destination, "default")
        val second = PublicIds.generate("https://example.com/a.bin", destination, "default")
        assertEquals(first, second)
        assertEquals(32, first.length)
        assertTrue(first.all { it in '0'..'9' || it in 'a'..'f' })
        val other = PublicIds.generate("https://example.com/a.bin", Destination("downloads/b.bin"), "default")
        assertFalse(first == other)
    }
}

/**
 * [RetryPolicy.delayFor] 在没有抖动时按 1 秒、2 秒、4 秒增长，并停在 30 秒。
 */
class RetryPolicyTest {
    /**
     * 随机源固定返回 0.5，使抖动乘子为 1，从而只观察指数退避和上限。
     */
    @Test
    fun delayGrowsThenCapsWithoutJitter() {
        val policy = RetryPolicy()
        val random = FixedRandom(0.5)
        assertEquals(1_000L, policy.delayFor(1, random))
        assertEquals(2_000L, policy.delayFor(2, random))
        assertEquals(4_000L, policy.delayFor(3, random))
        assertEquals(30_000L, policy.delayFor(20, random))
    }
}

/**
 * [Status] 保持约定的七个名字，不包含暂停或取消。
 */
class StatusTest {
    /**
     * 枚举声明顺序就是对外名称顺序。
     */
    @Test
    fun subsetKeepsFinalNamesAndOmitsStopStates() {
        assertEquals(
            listOf("ADDED", "QUEUED", "DOWNLOADING", "COMPLETED", "FAILED", "REMOVED", "DELETED"),
            Status.entries.map { it.name },
        )
    }
}

/**
 * 构造一条只填地址和内部路径的请求，其余字段用默认值。
 *
 * @param url 资源地址。
 * @param headers 附加请求头。
 * @return 可用于多数模型测试的请求。
 */
private fun sampleRequest(
    url: String = "https://example.com/a.bin",
    headers: Map<String, String> = emptyMap(),
): DownloadRequest {
    return DownloadRequest(
        url = url,
        destination = Destination("downloads/a.bin"),
        headers = headers,
    )
}

/**
 * 每次 [nextDouble] 都返回同一个值，用来去掉重试抖动。
 *
 * @param value [nextDouble] 的固定结果，测试里使用 0.5。
 */
private class FixedRandom(private val value: Double) : Random() {
    /**
     * 本测试不使用位生成。返回 0，避免意外依赖默认实现。
     *
     * @param bitCount 请求的位数。被忽略。
     * @return 始终为 0。
     */
    override fun nextBits(bitCount: Int): Int = 0

    /**
     * 返回构造时给定的固定小数。
     *
     * @return [value]。
     */
    override fun nextDouble(): Double = value
}
