/**
 * 多次在提交窗口中断后继续下载，最终哈希保持一致。
 *
 * @file
 */
package com.arturoyi.fastprism.engine

import com.arturoyi.fastprism.core.model.Destination
import com.arturoyi.fastprism.core.model.DownloadRequest
import com.arturoyi.fastprism.engine.store.InMemoryBreakpointStore
import com.arturoyi.fastprism.testing.MockDownloadServerRule
import com.arturoyi.fastprism.testing.RangedResourceDispatcher
import com.arturoyi.fastprism.testing.patternedBody
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.RegisterExtension
import java.io.File
import java.nio.file.Files
import java.security.MessageDigest
import kotlin.random.Random

/**
 * 20 次模拟退出后继续下载，文件都与源字节一致。
 */
class ChaosResumeTest {
    /** 整个测试类共用一台服务器，正文不变。 */
    @JvmField
    @RegisterExtension
    val server = MockDownloadServerRule()

    /**
     * 每次先在随机进度模拟退出，确认已提交偏移不超过文件长度，再下载到结束。
     */
    @Test
    fun twentyInterruptionsStillMatchHash() {
        val body = patternedBody(128 * 1024)
        server.server.dispatcher = RangedResourceDispatcher(body)
        val random = Random(20261112)
        repeat(20) { round ->
            val root = Files.createTempDirectory("fast-prism-chaos-$round").toFile()
            val request = DownloadRequest(
                url = server.url("/file.bin"),
                destination = Destination("downloads/file.bin"),
                connectionCount = 4,
            )
            val store = InMemoryBreakpointStore()
            val stopAt = random.nextLong(48_000L, 100_000L)
            val crashing = BlockDownloadCall(request, root, store)
            crashing.afterCommit = { committed, _ ->
                if (committed >= stopAt) throw CrashInjection()
            }
            var crashed = false
            try {
                crashing.execute()
            } catch (_: CrashInjection) {
                crashed = true
            }
            assertTrue(crashed)
            val part = partFile(root, request, store)
            if (part.exists()) {
                assertTrue(store.downloaded(store.open(request).id) <= part.length())
            }
            val resumed = BlockDownloadCall(request, root, store).execute()
            assertInstanceOf(DownloadResult.Success::class.java, resumed)
            assertTrue(MessageDigest.isEqual(sha(body), sha(file(root).readBytes())))
        }
    }
}

/**
 * 找到这一轮的临时文件。
 *
 * 标识来自存储里已经打开的任务。文件可能因为退出太早尚不存在。
 *
 * @param root 存储根目录。
 * @param request 与下载相同的请求。
 * @param store 本轮使用的存储。
 * @return 临时文件路径。
 */
private fun partFile(root: File, request: DownloadRequest, store: InMemoryBreakpointStore): File {
    val id = store.open(request).id
    val target = File(root, request.destination.relativePath)
    return File(target.parentFile, target.name + "." + id + ".part")
}

/**
 * 最终文件。
 *
 * @param root 存储根目录。
 * @return `downloads/file.bin`。
 */
private fun file(root: File): File = File(root, "downloads/file.bin")

/**
 * 计算 SHA-256。
 *
 * @param bytes 原始字节。
 * @return 摘要字节。
 */
private fun sha(bytes: ByteArray): ByteArray = MessageDigest.getInstance("SHA-256").digest(bytes)
