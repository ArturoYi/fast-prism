/**
 * 重试次数有上界，等待随次数变长。没有网络时不消耗次数。
 *
 * @file
 */
package com.arturoyi.fastdownload.engine.dispatch

import com.arturoyi.fastdownload.core.error.DownloadError
import com.arturoyi.fastdownload.core.error.ErrorCode
import com.arturoyi.fastdownload.core.model.Destination
import com.arturoyi.fastdownload.core.model.DownloadRequest
import com.arturoyi.fastdownload.core.model.HttpRequest
import com.arturoyi.fastdownload.core.model.HttpResponse
import com.arturoyi.fastdownload.core.model.RetryPolicy
import com.arturoyi.fastdownload.core.spi.HttpClient
import com.arturoyi.fastdownload.core.state.Status
import com.arturoyi.fastdownload.engine.DownloadResult
import com.arturoyi.fastdownload.engine.store.InMemoryBreakpointStore
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.io.IOException
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong
import kotlin.time.Duration.Companion.milliseconds

/**
 * 场景 16：退避递增，达到上限后失败；弱网下的连接次数有上界。
 */
class RetryDispatchTest {
    /**
     * 每次都超时。三次额外重试的等待是 10、20、40 毫秒，然后失败。
     */
    @Test
    fun backoffGrowsThenFails() {
        val store = InMemoryBreakpointStore()
        val delays = CopyOnWriteArrayList<Long>()
        val calls = AtomicInteger()
        val now = AtomicLong(1_000)
        val failed = CountDownLatch(1)
        val dispatcher = dispatcher(
            store = store,
            now = now,
            delays = delays,
            runner = DownloadRunner { _, _ ->
                calls.incrementAndGet()
                DownloadResult.Failure(DownloadError(ErrorCode.CONNECTION_TIMEOUT, "超时", "trace"))
            },
        )
        try {
            dispatcher.addListener { records ->
                if (records.any { it.status == Status.FAILED }) failed.countDown()
            }
            val id = dispatcher.enqueue(request(maxAttempts = 3))
            assertTrue(failed.await(5, TimeUnit.SECONDS))
            assertEquals(4, calls.get())
            assertEquals(listOf(10L, 20L, 40L), delays.toList())
            assertEquals(3, store.get(id)?.retryAttempts)
            assertEquals(Status.FAILED, store.get(id)?.status)
        } finally {
            dispatcher.close()
        }
    }

    /**
     * 没有网络时停在等待网络，不增加重试次数，也不再发起下一次下载。
     */
    @Test
    fun noNetworkWaitsWithoutConsumingAttempts() {
        val store = InMemoryBreakpointStore()
        val calls = AtomicInteger()
        val waiting = CountDownLatch(1)
        val dispatcher = dispatcher(
            store = store,
            now = AtomicLong(1_000),
            delays = CopyOnWriteArrayList(),
            runner = DownloadRunner { _, _ ->
                calls.incrementAndGet()
                DownloadResult.Failure(DownloadError(ErrorCode.NO_NETWORK, "没有网络", "trace"))
            },
        )
        try {
            dispatcher.addListener { records ->
                if (records.any { it.status == Status.WAITING_NETWORK }) waiting.countDown()
            }
            val id = dispatcher.enqueue(request(maxAttempts = 3))
            assertTrue(waiting.await(5, TimeUnit.SECONDS))
            Thread.sleep(50)
            assertEquals(1, calls.get())
            assertEquals(0, store.get(id)?.retryAttempts)
            assertEquals(Status.WAITING_NETWORK, store.get(id)?.status)
        } finally {
            dispatcher.close()
        }
    }

    /**
     * 连接一直失败时，链路内只再试一次，任务级也到上限就停。
     */
    @Test
    fun connectionAttemptsStayBounded() {
        val store = InMemoryBreakpointStore()
        val client = ThrowingClient()
        val root = java.nio.file.Files.createTempDirectory("retry-bound").toFile()
        val now = AtomicLong(1_000)
        val failed = CountDownLatch(1)
        val dispatcher = CommandDispatcher(
            repository = store,
            runner = EngineDownloadRunner(storageRoot = root, store = store, client = client),
            backoff = IdleBackoff(initialMs = 60_000),
            storageRoot = root,
            clock = { now.get() },
            retryWake = { delay -> now.addAndGet(delay) },
        )
        try {
            dispatcher.addListener { records ->
                if (records.any { it.status == Status.FAILED }) failed.countDown()
            }
            dispatcher.enqueue(request(maxAttempts = 2))
            assertTrue(failed.await(5, TimeUnit.SECONDS))
            assertTrue(client.calls.get() <= 6)
            assertEquals(6, client.calls.get())
        } finally {
            dispatcher.close()
        }
    }

    private fun dispatcher(
        store: InMemoryBreakpointStore,
        now: AtomicLong,
        delays: CopyOnWriteArrayList<Long>,
        runner: DownloadRunner,
    ): CommandDispatcher {
        return CommandDispatcher(
            repository = store,
            runner = runner,
            backoff = IdleBackoff(initialMs = 60_000),
            clock = { now.get() },
            retryWake = { delay ->
                delays += delay
                now.addAndGet(delay)
            },
        )
    }

    private fun request(maxAttempts: Int): DownloadRequest {
        return DownloadRequest(
            url = "https://example.com/a.bin",
            destination = Destination("a.bin"),
            retryPolicy = RetryPolicy(
                maxAttempts = maxAttempts,
                initialDelay = 10.milliseconds,
                maxDelay = 1_000.milliseconds,
                jitter = 0.0,
            ),
            downloadOnEnqueue = true,
        )
    }
}

/**
 * 每次连接都失败。
 */
private class ThrowingClient : HttpClient {
    val calls: AtomicInteger = AtomicInteger()

    override fun execute(request: HttpRequest): HttpResponse {
        calls.incrementAndGet()
        throw IOException("连接被重置")
    }

    override fun getContentLength(request: HttpRequest): Long = -1L

    override fun close() = Unit
}
