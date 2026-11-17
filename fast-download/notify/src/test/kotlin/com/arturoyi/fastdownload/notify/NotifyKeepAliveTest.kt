/**
 * 建造器上的一行注册会带上前台保活工厂。
 *
 * @file
 */
package com.arturoyi.fastdownload.notify

import com.arturoyi.fastdownload.android.background.BackgroundMode
import com.arturoyi.fastdownload.api.DownloaderConfig
import com.arturoyi.fastdownload.notify.background.DownloadForegroundService
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/**
 * [foregroundService] 写入前台服务模式和工厂。
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class NotifyKeepAliveTest {
    /**
     * 工厂创建的保活会在有任务时启动前台服务。
     */
    @Test
    fun foregroundServiceStartsForegroundExecutor() {
        val context = RuntimeEnvironment.getApplication()
        shadowOf(context).grantPermissions(android.Manifest.permission.POST_NOTIFICATIONS)
        val config = DownloaderConfig.Builder(context).foregroundService().build()
        assertEquals(BackgroundMode.FOREGROUND_SERVICE, config.backgroundMode)
        val executor = checkNotNull(config.backgroundExecutorFactory).create(context) {}
        try {
            executor.onActiveCount(1)
            val started = shadowOf(context).nextStartedService
            assertEquals(DownloadForegroundService::class.java.name, started?.component?.className)
        } finally {
            executor.close()
        }
    }
}
