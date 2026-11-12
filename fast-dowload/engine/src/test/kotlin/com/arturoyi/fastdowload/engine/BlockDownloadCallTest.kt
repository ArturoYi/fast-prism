/**
 * 分块下载、断点重下、文件锁和未知长度。
 *
 * @file
 */
package com.arturoyi.fastdowload.engine

import com.arturoyi.fastdowload.core.error.ErrorCode
import com.arturoyi.fastdowload.core.error.ResumeFailedCause
import com.arturoyi.fastdowload.core.error.TerminalCause
import com.arturoyi.fastdowload.core.model.ContentFingerprint
import com.arturoyi.fastdowload.core.model.Destination
import com.arturoyi.fastdowload.core.model.DownloadBlock
import com.arturoyi.fastdowload.core.model.DownloadRequest
import com.arturoyi.fastdowload.core.model.PublicIds
import com.arturoyi.fastdowload.core.strategy.BlockSplitter
import com.arturoyi.fastdowload.engine.store.InMemoryBreakpointStore
import com.arturoyi.fastdowload.testing.ChunkedResourceDispatcher
import com.arturoyi.fastdowload.testing.MockDownloadServerRule
import com.arturoyi.fastdowload.testing.RangedResourceDispatcher
import com.arturoyi.fastdowload.testing.patternedBody
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.RegisterExtension
import java.io.File
import java.nio.file.Files
import java.security.MessageDigest
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference

/**
 * [BlockDownloadCall] 对本地服务器的分块、续传和拒绝行为。
 */
class BlockDownloadCallTest {
    /** 每个测试方法单独启动并关闭的本地服务器。 */
    @JvmField
    @RegisterExtension
    val server = MockDownloadServerRule()

    /**
     * 206 且声明接受区间时，4 块并行写完，文件哈希与源一致。
     */
    @Test
    fun scenario2ParallelBlocksMatchHash() {
        val body = patternedBody(1024 * 1024)
        server.server.dispatcher = RangedResourceDispatcher(body)
        val root = tempRoot()
        val request = request(root, connectionCount = 4)
        val progress = java.util.Collections.synchronizedList(mutableListOf<Long>())
        val result = BlockDownloadCall(
            request,
            root,
            InMemoryBreakpointStore(),
            listener = object : BlockDownloadListener {
                override fun onProgress(downloadedBytes: Long, totalBytes: Long) {
                    progress += downloadedBytes
                }
            },
        ).execute()

        val success = assertInstanceOf(DownloadResult.Success::class.java, result)
        assertEquals(body.size.toLong(), success.bytesWritten)
        assertEquals(sha256(body), sha256(file(root).readBytes()))
        assertTrue(progress.zipWithNext().all { (previous, next) -> next >= previous })
        val ranged = (0 until server.server.requestCount).map { server.server.takeRequest() }
        assertEquals(4, ranged.count { it.headers["Range"] != null && it.headers["Range"] != "bytes=0-0" })
    }

    /**
     * 已有进度时 `ETag` 对不上，会带原因重下，最终文件是新内容。
     */
    @Test
    fun scenario3EtagChangeRestarts() {
        val first = patternedBody(32 * 1024)
        val second = patternedBody(40 * 1024)
        val root = tempRoot()
        val request = request(root, connectionCount = 1)
        val store = InMemoryBreakpointStore()
        server.server.dispatcher = RangedResourceDispatcher(first, etag = "\"v1\"")
        assertInstanceOf(DownloadResult.Success::class.java, download(request, root, store))

        server.server.dispatcher = RangedResourceDispatcher(second, etag = "\"v2\"")
        val causes = mutableListOf<ResumeFailedCause>()
        val result = BlockDownloadCall(
            request,
            root,
            store,
            listener = object : BlockDownloadListener {
                override fun onResumeFailed(cause: ResumeFailedCause) {
                    causes += cause
                }
            },
        ).execute()

        assertInstanceOf(DownloadResult.Success::class.java, result)
        assertEquals(listOf(ResumeFailedCause.RESPONSE_PRECONDITION_FAILED), causes)
        assertEquals(sha256(second), sha256(file(root).readBytes()))
    }

    /**
     * 416 且本地字节已经等于总长度时，视为完成，不再重下。
     */
    @Test
    fun scenario4UnsatisfiedRangeCompletesWhenLocalFileIsFull() {
        val body = patternedBody(8 * 1024)
        val root = tempRoot()
        val request = request(root, connectionCount = 1)
        val store = seedFull(request, root, body, etag = "\"v1\"")
        server.server.dispatcher = RangedResourceDispatcher(body, alwaysUnsatisfied = true)
        val causes = mutableListOf<ResumeFailedCause>()
        val result = BlockDownloadCall(
            request,
            root,
            store,
            listener = object : BlockDownloadListener {
                override fun onResumeFailed(cause: ResumeFailedCause) {
                    causes += cause
                }
            },
        ).execute()

        assertInstanceOf(DownloadResult.Success::class.java, result)
        assertTrue(causes.isEmpty())
        assertEquals(sha256(body), sha256(file(root).readBytes()))
    }

    /**
     * 资源变长后重下，原因是长度变化，最终哈希是新内容。
     */
    @Test
    fun scenario5ContentLengthChangeRestarts() {
        val first = patternedBody(16 * 1024)
        val second = patternedBody(24 * 1024)
        val root = tempRoot()
        val request = request(root, connectionCount = 1)
        val store = InMemoryBreakpointStore()
        server.server.dispatcher = RangedResourceDispatcher(first)
        assertInstanceOf(DownloadResult.Success::class.java, download(request, root, store))
        server.server.dispatcher = RangedResourceDispatcher(second)
        val causes = mutableListOf<ResumeFailedCause>()
        val result = BlockDownloadCall(
            request,
            root,
            store,
            listener = object : BlockDownloadListener {
                override fun onResumeFailed(cause: ResumeFailedCause) {
                    causes += cause
                }
            },
        ).execute()

        assertInstanceOf(DownloadResult.Success::class.java, result)
        assertEquals(listOf(ResumeFailedCause.CONTENT_LENGTH_CHANGED), causes)
        assertEquals(sha256(second), sha256(file(root).readBytes()))
    }

    /**
     * 分块且没有长度时顺序写完，进度总长度保持未知。
     */
    @Test
    fun scenario6ChunkedDownloadHasUnknownTotal() {
        val body = patternedBody(20 * 1024)
        server.server.dispatcher = ChunkedResourceDispatcher(body)
        val root = tempRoot()
        val totals = mutableListOf<Long>()
        val result = BlockDownloadCall(
            request(root, connectionCount = 4),
            root,
            InMemoryBreakpointStore(),
            listener = object : BlockDownloadListener {
                override fun onProgress(downloadedBytes: Long, totalBytes: Long) {
                    totals += totalBytes
                }
            },
        ).execute()

        assertInstanceOf(DownloadResult.Success::class.java, result)
        assertEquals(sha256(body), sha256(file(root).readBytes()))
        assertTrue(totals.isNotEmpty())
        assertTrue(totals.all { it == -1L })
    }

    /**
     * 文件名含有 `../` 时拒绝，并且不创建目标文件。
     */
    @Test
    fun scenario8RejectsTraversalInContentDisposition() {
        val body = patternedBody(1024)
        server.server.dispatcher = RangedResourceDispatcher(
            body,
            contentDisposition = "attachment; filename=\"../secret.bin\"",
        )
        val root = tempRoot()
        val result = download(request(root), root, InMemoryBreakpointStore())
        val failure = assertInstanceOf(DownloadResult.Failure::class.java, result)
        assertEquals(ErrorCode.SECURITY_PATH_TRAVERSAL, failure.error.code)
        assertFalse(file(root).exists())
    }

    /**
     * 本地只持久化了前半段时，从那个偏移继续，最终哈希一致。
     */
    @Test
    fun resumeFromPersistedOffsetMatchesHash() {
        val body = patternedBody(64 * 1024)
        val root = tempRoot()
        val request = request(root, connectionCount = 1)
        val store = InMemoryBreakpointStore()
        val id = store.open(request).id
        val kept = 20 * 1024
        store.saveFingerprint(
            id,
            ContentFingerprint(
                eTag = "\"v1\"",
                contentLength = body.size.toLong(),
                acceptRanges = true,
            ),
        )
        store.replaceBlocks(
            id,
            BlockSplitter.split(id, body.size.toLong(), hint = 1, supportSeek = true).map { block ->
                block.copy(currentOffset = kept.toLong())
            },
        )
        part(root, request, id).apply {
            parentFile?.mkdirs()
            writeBytes(body.copyOf(kept))
        }
        server.server.dispatcher = RangedResourceDispatcher(body)
        val result = download(request, root, store)

        assertInstanceOf(DownloadResult.Success::class.java, result)
        assertEquals(sha256(body), sha256(file(root).readBytes()))
        val resume = server.server.takeRequest()
        assertEquals("bytes=0-0", resume.headers["Range"])
        val data = server.server.takeRequest()
        assertEquals("bytes=$kept-${body.size - 1}", data.headers["Range"])
    }

    /**
     * 修改时间变化时重下，原因是 [ResumeFailedCause.LAST_MODIFIED_CHANGED]。
     */
    @Test
    fun changedLastModifiedRestarts() {
        val body = patternedBody(8 * 1024)
        val root = tempRoot()
        val request = request(root, connectionCount = 1)
        val store = InMemoryBreakpointStore()
        val id = store.open(request).id
        store.saveFingerprint(
            id,
            ContentFingerprint(
                eTag = "\"v1\"",
                lastModified = "Wed, 21 Oct 2015 07:28:00 GMT",
                contentLength = body.size.toLong(),
                acceptRanges = true,
            ),
        )
        store.replaceBlocks(
            id,
            listOf(DownloadBlock(id, 0, 0, body.size.toLong(), currentOffset = 100)),
        )
        part(root, request, id).apply {
            parentFile?.mkdirs()
            writeBytes(body.copyOf(100))
        }
        server.server.dispatcher = RangedResourceDispatcher(
            body,
            lastModified = "Thu, 22 Oct 2015 07:28:00 GMT",
        )
        val causes = mutableListOf<ResumeFailedCause>()
        val result = BlockDownloadCall(
            request,
            root,
            store,
            listener = object : BlockDownloadListener {
                override fun onResumeFailed(cause: ResumeFailedCause) {
                    causes += cause
                }
            },
        ).execute()

        assertInstanceOf(DownloadResult.Success::class.java, result)
        assertEquals(listOf(ResumeFailedCause.LAST_MODIFIED_CHANGED), causes)
        assertEquals(sha256(body), sha256(file(root).readBytes()))
    }

    /**
     * 同一个目标上第二个下载立刻得到文件忙，不会覆盖第一个。
     */
    @Test
    fun scenario11SecondCallGetsFileBusy() {
        val body = patternedBody(256 * 1024)
        val entered = CountDownLatch(1)
        val release = CountDownLatch(1)
        val dispatcher = RangedResourceDispatcher(body)
        dispatcher.beforeSlice = {
            entered.countDown()
            release.await(5, TimeUnit.SECONDS)
        }
        server.server.dispatcher = dispatcher
        val root = tempRoot()
        val request = request(root, connectionCount = 1)
        val store = InMemoryBreakpointStore()
        val first = AtomicReference<DownloadResult>()
        val worker = Thread {
            first.set(download(request, root, store))
        }
        worker.start()
        assertTrue(entered.await(5, TimeUnit.SECONDS))
        val second = download(request, root, InMemoryBreakpointStore())
        release.countDown()
        worker.join(5_000)
        val busy = assertInstanceOf(DownloadResult.Busy::class.java, second)
        assertEquals(TerminalCause.FILE_BUSY, busy.terminal)
        assertInstanceOf(DownloadResult.Success::class.java, first.get())
    }

    /**
     * 404 不创建目标文件。
     */
    @Test
    fun notFoundDoesNotCreateTheFile() {
        server.enqueueFixed("missing".encodeToByteArray(), code = 404)
        val root = tempRoot()
        val result = download(request(root), root, InMemoryBreakpointStore())
        val failure = assertInstanceOf(DownloadResult.Failure::class.java, result)
        assertEquals(ErrorCode.HTTP_NOT_FOUND, failure.error.code)
        assertFalse(file(root).exists())
    }

    /**
     * 慢回调不会让 4 块下载失败，文件仍然完整。
     */
    @Test
    fun slowProgressListenerStillCompletes() {
        val body = patternedBody(256 * 1024)
        server.server.dispatcher = RangedResourceDispatcher(body)
        val root = tempRoot()
        val result = BlockDownloadCall(
            request(root, connectionCount = 4),
            root,
            InMemoryBreakpointStore(),
            listener = object : BlockDownloadListener {
                override fun onProgress(downloadedBytes: Long, totalBytes: Long) {
                    Thread.sleep(2)
                }
            },
        ).execute()
        assertInstanceOf(DownloadResult.Success::class.java, result)
        assertEquals(sha256(body), sha256(file(root).readBytes()))
    }

    private fun download(
        request: DownloadRequest,
        root: File,
        store: InMemoryBreakpointStore,
    ): DownloadResult {
        return BlockDownloadCall(request, root, store).execute()
    }

    private fun request(root: File, connectionCount: Int? = null): DownloadRequest {
        return DownloadRequest(
            url = server.url("/file.bin"),
            destination = Destination("downloads/file.bin"),
            connectionCount = connectionCount,
        )
    }

    private fun tempRoot(): File = Files.createTempDirectory("fast-dowload-block").toFile()

    private fun file(root: File): File = File(root, "downloads/file.bin")

    private fun part(root: File, request: DownloadRequest, id: String): File {
        val target = file(root)
        return File(target.parentFile, target.name + "." + id + ".part")
    }

    private fun seedFull(
        request: DownloadRequest,
        root: File,
        body: ByteArray,
        etag: String,
    ): InMemoryBreakpointStore {
        val store = InMemoryBreakpointStore()
        val id = store.open(request).id
        check(id == PublicIds.generate(request.url, request.destination, request.groupId))
        store.saveFingerprint(
            id,
            ContentFingerprint(eTag = etag, contentLength = body.size.toLong(), acceptRanges = true),
        )
        store.replaceBlocks(
            id,
            BlockSplitter.split(id, body.size.toLong(), hint = 1, supportSeek = true).map { block ->
                block.copy(currentOffset = block.contentLength)
            },
        )
        part(root, request, id).apply {
            parentFile?.mkdirs()
            writeBytes(body)
        }
        return store
    }
}

/**
 * 计算 [bytes] 的 SHA-256，结果是 64 位小写十六进制。
 *
 * @param bytes 原始字节。
 * @return 哈希文本。
 */
private fun sha256(bytes: ByteArray): String {
    val digest = MessageDigest.getInstance("SHA-256").digest(bytes)
    return digest.joinToString(separator = "") { byte -> "%02x".format(byte.toInt() and 0xff) }
}
