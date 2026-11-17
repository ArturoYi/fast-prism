/**
 * 主线程初始化不能在界面线程上开 Room 事务。
 *
 * @file
 */
package com.arturoyi.fastdownload.api

import android.os.Looper
import com.arturoyi.fastdownload.android.db.RoomDownloadRepository
import com.arturoyi.fastdownload.core.model.Destination
import com.arturoyi.fastdownload.core.model.DownloadRequest
import com.arturoyi.fastdownload.core.state.Status
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.util.concurrent.atomic.AtomicReference

/**
 * [FastDownloader.create] 在主线程调用时仍然完成启动清洗。
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class DownloaderInitTest {
    /**
     * 残留的正在下载任务会在返回前回到队列，且不会触发 Room 的主线程检查。
     */
    @Test
    fun createFromMainThreadSanitizesRoom() {
        assertSame(Looper.getMainLooper().thread, Thread.currentThread())
        val context = RuntimeEnvironment.getApplication()
        val repository = RoomDownloadRepository(context, "main-thread-init.db")
        val id = offMain {
            val opened = repository.open(
                DownloadRequest(
                    url = "https://example.com/a.bin",
                    destination = Destination("a.bin"),
                ),
            )
            repository.upsert(opened.copy(status = Status.DOWNLOADING))
            opened.id
        }
        val downloader = FastDownloader.create(
            DownloaderConfig.Builder(context)
                .namespace("main-thread-init")
                .repository(repository)
                .autoStart(false)
                .build(),
        )
        try {
            val status = offMain { repository.get(id)?.status }
            assertEquals(Status.QUEUED, status)
        } finally {
            downloader.close()
        }
    }

    private fun <T> offMain(block: () -> T): T {
        val box = AtomicReference<Result<T>>()
        val worker = Thread {
            box.set(runCatching(block))
        }
        worker.start()
        worker.join()
        return box.get().getOrThrow()
    }
}
