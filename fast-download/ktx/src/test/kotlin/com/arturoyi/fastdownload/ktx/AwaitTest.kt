/**
 * await 在入队后挂起，不把调用协程卡死。
 *
 * @file
 */
package com.arturoyi.fastdownload.ktx

import com.arturoyi.fastdownload.api.DownloadRequestBuilder
import com.arturoyi.fastdownload.api.FastDownloader
import com.arturoyi.fastdownload.api.DownloaderConfig
import com.arturoyi.fastdownload.engine.store.InMemoryBreakpointStore
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/**
 * [await] 会让出线程。
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class AwaitTest {
    /**
     * 入队后等待结束时，外层协程仍然可以继续执行。
     */
    @Test
    fun awaitSuspendsInsteadOfBlockingTheCaller() = runBlocking {
        val downloader = FastDownloader.create(
            DownloaderConfig.Builder(RuntimeEnvironment.getApplication())
                .namespace("await")
                .repository(InMemoryBreakpointStore())
                .autoStart(false)
                .build(),
        )
        try {
            val request = DownloadRequestBuilder("https://example.com/a.bin")
                .saveTo("downloads", "a.bin")
                .build()
            val job = launch { request.await(downloader) }
            delay(200)
            assertTrue(job.isActive)
            job.cancel()
        } finally {
            downloader.close()
        }
    }
}
