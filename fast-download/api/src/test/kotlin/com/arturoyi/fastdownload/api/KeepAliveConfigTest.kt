/**
 * 没接前台保活模块时，前台模式在创建下载器时失败。
 *
 * @file
 */
package com.arturoyi.fastdownload.api

import com.arturoyi.fastdownload.android.background.BackgroundMode
import com.arturoyi.fastdownload.core.spi.BackgroundExecutor
import com.arturoyi.fastdownload.testing.BackgroundExecutorChecks
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/**
 * 默认保活留在进程内。前台模式要有工厂或实例。
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class KeepAliveConfigTest {
    /**
     * 只改枚举、不注册工厂时，不打开数据库。
     */
    @Test
    fun foregroundModeWithoutFactoryFails() {
        val context = RuntimeEnvironment.getApplication()
        val error = runCatching {
            FastDownloader.create(
                DownloaderConfig.Builder(context)
                    .namespace("missing-notify")
                    .backgroundMode(BackgroundMode.FOREGROUND_SERVICE)
                    .build(),
            )
        }.exceptionOrNull()
        assertEquals(IllegalStateException::class.java, error?.javaClass)
    }

    /**
     * 调用方自己的保活可以配前台模式，不必依赖 notify。
     */
    @Test
    fun customExecutorSatisfiesForegroundMode() {
        val context = RuntimeEnvironment.getApplication()
        val executor = CountingBackground()
        val downloader = FastDownloader.create(
            DownloaderConfig.Builder(context)
                .namespace("custom-keepalive")
                .autoStart(false)
                .backgroundMode(BackgroundMode.FOREGROUND_SERVICE)
                .backgroundExecutor(executor)
                .build(),
        )
        try {
            assertEquals(0, executor.activeCount())
        } finally {
            downloader.close()
        }
    }

    /**
     * 进程内实现只跟踪活跃数。
     */
    @Test
    fun inProcessTracksActiveCount() {
        BackgroundExecutorChecks.verify(InProcessBackground())
    }
}

/**
 * 记下最近一次活跃任务数。
 */
private class CountingBackground : BackgroundExecutor {
    private var active = 0

    override fun onActiveCount(activeCount: Int) {
        active = activeCount
    }

    override fun activeCount(): Int = active

    override fun close() {
        active = 0
    }
}
