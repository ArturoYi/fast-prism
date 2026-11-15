/**
 * 四种入队动作，重点是重名时真的换一个文件名。
 *
 * @file
 */
package com.arturoyi.fastdowload.engine.dispatch

import com.arturoyi.fastdowload.core.model.Destination
import com.arturoyi.fastdowload.core.model.DownloadBlock
import com.arturoyi.fastdowload.core.model.DownloadRequest
import com.arturoyi.fastdowload.core.model.EnqueueAction
import com.arturoyi.fastdowload.core.model.Priority
import com.arturoyi.fastdowload.core.state.Status
import com.arturoyi.fastdowload.engine.DownloadResult
import com.arturoyi.fastdowload.engine.store.InMemoryBreakpointStore
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.nio.file.Files

/**
 * [CommandDispatcher.enqueue] 对已有任务和已有文件的处理。
 */
class EnqueueActionTest {
    /**
     * 目标文件已经存在时，新任务使用 `a (1).bin`，原来的字节保持不变。
     */
    @Test
    fun incrementFileNameDoesNotOverwrite() {
        val root = Files.createTempDirectory("fastdowload-enqueue").toFile()
        val original = Destination("downloads/a.bin").resolveInside(root)
        original.parentFile?.mkdirs()
        original.writeBytes(byteArrayOf(1, 2, 3, 4))
        val store = InMemoryBreakpointStore()
        val dispatcher = dispatcher(store, root)
        try {
            val first = dispatcher.enqueue(request(EnqueueAction.INCREMENT_FILE_NAME))
            val created = store.get(first)
            assertEquals("downloads/a (1).bin", created?.request?.destination?.relativePath)
            dispatcher.drain()
            assertEquals(4, original.readBytes().size)
            assertEquals(1, original.readBytes()[0])
            val second = dispatcher.enqueue(request(EnqueueAction.INCREMENT_FILE_NAME))
            assertEquals("downloads/a (2).bin", store.get(second)?.request?.destination?.relativePath)
            assertFalse(first == second)
        } finally {
            dispatcher.close()
        }
    }

    /**
     * 已有任务时直接返回原标识，不改优先级。
     */
    @Test
    fun doNotEnqueueLeavesTheExistingTask() {
        val store = InMemoryBreakpointStore()
        val dispatcher = dispatcher(store, root = null)
        try {
            val first = dispatcher.enqueue(
                request(EnqueueAction.DO_NOT_ENQUEUE_IF_EXISTING, Priority.LOW, downloadOnEnqueue = false),
            )
            val second = dispatcher.enqueue(
                request(EnqueueAction.DO_NOT_ENQUEUE_IF_EXISTING, Priority.HIGH, downloadOnEnqueue = false),
            )
            assertEquals(first, second)
            assertEquals(Priority.LOW, store.get(first)?.request?.priority)
            assertEquals(Status.ADDED, store.get(first)?.status)
        } finally {
            dispatcher.close()
        }
    }

    /**
     * 更新请求头和优先级，断点还在。
     */
    @Test
    fun updateAccordinglyKeepsBlocks() {
        val store = InMemoryBreakpointStore()
        val dispatcher = dispatcher(store, root = null)
        try {
            val id = dispatcher.enqueue(request(EnqueueAction.UPDATE_ACCORDINGLY, downloadOnEnqueue = false))
            store.replaceBlocks(id, listOf(DownloadBlock(id, 0, 0, 10, currentOffset = 4)))
            val again = dispatcher.enqueue(
                request(EnqueueAction.UPDATE_ACCORDINGLY, Priority.HIGH).copy(
                    headers = mapOf("Authorization" to "Bearer t"),
                ),
            )
            assertEquals(id, again)
            val saved = store.get(id)
            assertEquals(Priority.HIGH, saved?.request?.priority)
            assertEquals("Bearer t", saved?.request?.headers?.get("Authorization"))
            assertEquals(4L, store.blocksOf(id).single().currentOffset)
            assertEquals(Status.ADDED, saved?.status)
        } finally {
            dispatcher.close()
        }
    }

    /**
     * 替换会清掉断点，并删除已经存在的目标文件。
     */
    @Test
    fun replaceExistingDeletesTheFileAndBlocks() {
        val root = Files.createTempDirectory("fastdowload-replace").toFile()
        val target = Destination("downloads/a.bin").resolveInside(root)
        target.parentFile?.mkdirs()
        target.writeBytes(byteArrayOf(9, 9))
        val store = InMemoryBreakpointStore()
        val dispatcher = dispatcher(store, root)
        try {
            val id = dispatcher.enqueue(request(EnqueueAction.REPLACE_EXISTING, downloadOnEnqueue = false))
            store.replaceBlocks(id, listOf(DownloadBlock(id, 0, 0, 10, currentOffset = 2)))
            dispatcher.enqueue(request(EnqueueAction.REPLACE_EXISTING, downloadOnEnqueue = false))
            assertTrue(store.blocksOf(id).isEmpty())
            assertFalse(target.exists())
            assertEquals(Status.ADDED, store.get(id)?.status)
        } finally {
            dispatcher.close()
        }
    }
}

private fun dispatcher(store: InMemoryBreakpointStore, root: java.io.File?): CommandDispatcher {
    return CommandDispatcher(
        repository = store,
        runner = DownloadRunner { _, _ -> DownloadResult.Success(1) },
        storageRoot = root,
        backoff = IdleBackoff(initialMs = 60_000),
    )
}

private fun request(
    action: EnqueueAction,
    priority: Priority = Priority.NORMAL,
    downloadOnEnqueue: Boolean = true,
): DownloadRequest {
    return DownloadRequest(
        url = "https://example.com/a.bin",
        destination = Destination("downloads/a.bin"),
        enqueueAction = action,
        priority = priority,
        downloadOnEnqueue = downloadOnEnqueue,
    )
}
