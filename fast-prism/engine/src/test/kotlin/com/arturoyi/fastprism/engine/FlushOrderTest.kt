/**
 * 同步失败时断点不得前进，并且只有写入守卫会提交偏移。
 *
 * @file
 */
package com.arturoyi.fastprism.engine

import com.arturoyi.fastprism.core.model.Destination
import com.arturoyi.fastprism.core.model.DownloadBlock
import com.arturoyi.fastprism.core.model.DownloadRequest
import com.arturoyi.fastprism.engine.io.FileSink
import com.arturoyi.fastprism.engine.io.FlushOrderGuard
import com.arturoyi.fastprism.engine.io.SyncSink
import com.arturoyi.fastprism.engine.store.InMemoryBreakpointStore
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.io.File
import java.io.SyncFailedException
import java.nio.file.Files

/**
 * [FlushOrderGuard] 在 `sync` 失败时保持原偏移。
 */
class FlushOrderTest {
    /**
     * 同步抛错后，存储偏移仍是 0，并且不大于文件里已经出现的字节数。
     */
    @Test
    fun flushAndSyncFailureDoesNotAdvancePersistedOffset() {
        val store = storeWithBlock()
        val file = Files.createTempFile("fast-prism-flush", ".bin").toFile()
        val sink = ThrowingSync(file)
        val remit = FlushOrderGuard.openRemit(store, delayMs = 60_000)
        try {
            val guard = FlushOrderGuard(sink, storeId(store), remit)
            assertThrows(SyncFailedException::class.java) {
                guard.writeAndCommit(0, byteArrayOf(1, 2, 3, 4), 0, 4)
            }
            remit.forceFlush()
            assertEquals(0L, store.downloaded(storeId(store)))
            assertTrue(store.downloaded(storeId(store)) <= file.length())
            assertEquals(4L, file.length())
        } finally {
            remit.close()
            sink.close()
        }
    }

    /**
     * 同步成功并强制提交后，偏移等于写出的字节数。
     */
    @Test
    fun successfulSyncAdvancesOffset() {
        val store = storeWithBlock()
        val file = Files.createTempFile("fast-prism-flush-ok", ".bin").toFile()
        val sink = FileSink(file)
        val remit = FlushOrderGuard.openRemit(store, delayMs = 60_000)
        try {
            FlushOrderGuard(sink, storeId(store), remit)
                .writeAndCommit(0, byteArrayOf(1, 2, 3, 4), 0, 4)
            assertEquals(0L, store.downloaded(storeId(store)))
            remit.forceFlush()
            assertEquals(4L, store.downloaded(storeId(store)))
            assertTrue(store.downloaded(storeId(store)) <= file.length())
        } finally {
            remit.close()
            sink.close()
        }
    }

    /**
     * 源码里带点的提交调用只出现在写入守卫中。
     */
    @Test
    fun offsetCommitIsOnlyReferencedByTheGuard() {
        val root = listOf(File("src/main/kotlin"), File("fast-prism/engine/src/main/kotlin"))
            .first { it.isDirectory }
        val pattern = Regex("""\.onFlushSuccess\(""")
        val hits = root.walkTopDown()
            .filter { it.isFile && it.extension == "kt" }
            .filter { pattern.containsMatchIn(it.readText()) }
            .map { it.name }
            .sorted()
            .toList()
        assertEquals(listOf("FlushOrderGuard.kt"), hits)
    }
}

/**
 * 写入成功，但同步总是失败。
 *
 * @param file 实际接收字节的文件。
 */
private class ThrowingSync(
    file: File,
) : SyncSink {
    private val inner = FileSink(file)

    override fun seek(offset: Long) = inner.seek(offset)

    override fun write(bytes: ByteArray, off: Int, len: Int) = inner.write(bytes, off, len)

    override fun flushAndSync() {
        throw SyncFailedException("sync failed")
    }

    override fun setLength(length: Long) = inner.setLength(length)

    override fun close() = inner.close()
}

/**
 * 打开一条有 100 字节分块、偏移为 0 的任务。
 *
 * @return 新的内存存储。
 */
private fun storeWithBlock(): InMemoryBreakpointStore {
    val store = InMemoryBreakpointStore()
    val record = store.open(
        DownloadRequest(url = "https://example.com/a.bin", destination = Destination("a.bin")),
    )
    store.replaceBlocks(
        record.id,
        listOf(DownloadBlock(record.id, index = 0, startOffset = 0, contentLength = 100)),
    )
    return store
}

/**
 * 读取存储里唯一任务的标识。
 *
 * @param store 只打开过一条任务的存储。
 * @return 该任务的标识。
 */
private fun storeId(store: InMemoryBreakpointStore): String {
    return store.open(
        DownloadRequest(url = "https://example.com/a.bin", destination = Destination("a.bin")),
    ).id
}
