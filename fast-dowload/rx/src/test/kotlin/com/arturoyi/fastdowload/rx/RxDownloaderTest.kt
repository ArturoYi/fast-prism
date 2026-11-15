/**
 * RxJava 订阅会挂起，不把调用线程占死。
 *
 * @file
 */
package com.arturoyi.fastdowload.rx

import com.arturoyi.fastdowload.api.DownloadRequestBuilder
import com.arturoyi.fastdowload.api.FastDownloader
import com.arturoyi.fastdowload.api.DownloaderConfig
import com.arturoyi.fastdowload.engine.store.InMemoryBreakpointStore
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/**
 * [awaitSingle] 在下载结束前保持未完成。
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class RxDownloaderTest {
    /**
     * 不自动开始时，订阅不会立刻结束。取消订阅后观察者不再等待。
     */
    @Test
    fun awaitSingleStaysOpenUntilDisposed() {
        val downloader = FastDownloader.create(
            DownloaderConfig.Builder(RuntimeEnvironment.getApplication())
                .namespace("rx")
                .repository(InMemoryBreakpointStore())
                .autoStart(false)
                .build(),
        )
        try {
            val request = DownloadRequestBuilder("https://example.com/a.bin")
                .saveTo("downloads", "a.bin")
                .build()
            val observer = request.awaitSingle(downloader).test()
            Thread.sleep(200)
            observer.assertNotComplete()
            observer.dispose()
            observer.assertNotComplete()
        } finally {
            downloader.close()
        }
    }
}
