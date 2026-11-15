/**
 * 命令线程串行改状态，并按优先级启动。
 *
 * @file
 */
package com.arturoyi.fastdowload.engine.dispatch

import com.arturoyi.fastdowload.core.model.Destination
import com.arturoyi.fastdowload.core.model.DownloadRequest
import com.arturoyi.fastdowload.core.model.NetworkType
import com.arturoyi.fastdowload.core.model.Priority
import com.arturoyi.fastdowload.core.spi.DownloadRepository
import com.arturoyi.fastdowload.core.state.Status
import com.arturoyi.fastdowload.engine.DownloadResult
import com.arturoyi.fastdowload.engine.schedule.NetworkGate
import com.arturoyi.fastdowload.engine.store.InMemoryBreakpointStore
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

/**
 * [CommandDispatcher] 的排队、优先级和空闲等待。
 */
class CommandDispatcherTest {
    /**
     * 入队后的状态迁移都发生在命令线程，完成后状态是完成。
     */
    @Test
    fun enqueueCompletesOnTheCommandThread() {
        val store = InMemoryBreakpointStore()
        val threads = CopyOnWriteArrayList<String>()
        val repository = RecordingRepository(store, threads)
        val dispatcher = CommandDispatcher(
            repository = repository,
            runner = DownloadRunner { _, _ -> DownloadResult.Success(4) },
            backoff = IdleBackoff(initialMs = 60_000),
        )
        try {
            val done = CountDownLatch(1)
            dispatcher.addListener { records ->
                if (records.any { it.status == Status.COMPLETED }) done.countDown()
            }
            val id = dispatcher.enqueue(request("https://example.com/a.bin", "a.bin"))
            assertTrue(done.await(5, TimeUnit.SECONDS))
            assertEquals(Status.COMPLETED, repository.get(id)?.status)
            assertTrue(threads.isNotEmpty())
            assertTrue(threads.all { it == CommandDispatcher.COMMAND_THREAD })
        } finally {
            dispatcher.close()
        }
    }

    /**
     * 网络恢复前不下载。恢复后先启动高优先级任务。
     */
    @Test
    fun higherPriorityStartsFirstWhenNetworkReturns() {
        val store = InMemoryBreakpointStore()
        val allow = AtomicBoolean(false)
        val started = CopyOnWriteArrayList<String>()
        val first = CountDownLatch(1)
        val dispatcher = CommandDispatcher(
            repository = store,
            runner = DownloadRunner { record, _ ->
                started.add(record.id)
                first.countDown()
                DownloadResult.Success(1)
            },
            network = NetworkGate { allow.get() },
            concurrentLimit = 1,
            backoff = IdleBackoff(initialMs = 60_000),
        )
        try {
            val low = dispatcher.enqueue(
                request("https://example.com/low.bin", "low.bin", Priority.LOW),
            )
            val high = dispatcher.enqueue(
                request("https://example.com/high.bin", "high.bin", Priority.HIGH),
            )
            dispatcher.drain()
            assertEquals(Status.WAITING_NETWORK, store.get(low)?.status)
            assertEquals(Status.WAITING_NETWORK, store.get(high)?.status)
            allow.set(true)
            dispatcher.wake()
            assertTrue(first.await(5, TimeUnit.SECONDS))
            assertEquals(high, started.first())
        } finally {
            dispatcher.close()
        }
    }

    /**
     * 没有任务时的等待从 1 秒开始，而不是 500 毫秒。
     */
    @Test
    fun idleWaitStartsAtOneSecond() {
        val seen = CountDownLatch(1)
        val waits = CopyOnWriteArrayList<Long>()
        val dispatcher = CommandDispatcher(
            repository = InMemoryBreakpointStore(),
            runner = DownloadRunner { _, _ -> DownloadResult.Success(0) },
            backoff = IdleBackoff(),
            idleWaitListener = { millis ->
                waits.add(millis)
                seen.countDown()
            },
        )
        try {
            assertTrue(seen.await(3, TimeUnit.SECONDS))
            assertEquals(1_000L, waits.first())
        } finally {
            dispatcher.close()
        }
    }

    /**
     * 多个线程同时提交结果时，状态写入仍在命令线程，最终状态合法。
     */
    @Test
    fun concurrentResultsStayOnTheCommandThread() {
        val store = InMemoryBreakpointStore()
        val threads = CopyOnWriteArrayList<String>()
        val repository = RecordingRepository(store, threads)
        val gate = CountDownLatch(1)
        val dispatcher = CommandDispatcher(
            repository = repository,
            runner = DownloadRunner { _, _ ->
                gate.await(5, TimeUnit.SECONDS)
                DownloadResult.Success(1)
            },
            concurrentLimit = 4,
            backoff = IdleBackoff(initialMs = 60_000),
        )
        try {
            val ids = List(4) { index ->
                dispatcher.enqueue(request("https://example.com/$index.bin", "$index.bin"))
            }
            dispatcher.drain()
            val settled = CountDownLatch(1)
            dispatcher.addListener {
                if (ids.all { id ->
                        val status = repository.get(id)?.status
                        status != Status.DOWNLOADING && status != Status.QUEUED
                    }
                ) {
                    settled.countDown()
                }
            }
            repeat(8) { index ->
                val id = ids[index % ids.size]
                Thread {
                    if (index % 2 == 0) dispatcher.pause(id) else dispatcher.cancel(id)
                }.start()
            }
            gate.countDown()
            dispatcher.wake()
            assertTrue(settled.await(5, TimeUnit.SECONDS))
            assertTrue(threads.all { it == CommandDispatcher.COMMAND_THREAD })
        } finally {
            dispatcher.close()
        }
    }
}

/**
 * 记下每次状态迁移所在的线程。
 *
 * @param delegate 真正的仓库。
 * @param threads 调用 [transition] 时的线程名。
 */
private class RecordingRepository(
    private val delegate: DownloadRepository,
    private val threads: MutableList<String>,
) : DownloadRepository by delegate {
    override fun transition(
        publicId: String,
        to: Status,
        error: com.arturoyi.fastdowload.core.error.DownloadError?,
    ): com.arturoyi.fastdowload.core.model.DownloadRecord {
        threads.add(Thread.currentThread().name)
        return delegate.transition(publicId, to, error)
    }
}

/**
 * 构造一条只含地址和文件名的请求。
 *
 * @param url 资源地址。
 * @param name 内部文件名。
 * @param priority 调度优先级。
 * @return 会立即入队的请求。
 */
private fun request(
    url: String,
    name: String,
    priority: Priority = Priority.NORMAL,
): DownloadRequest {
    return DownloadRequest(
        url = url,
        destination = Destination(name),
        priority = priority,
        networkType = NetworkType.ALL,
    )
}
