/**
 * 前台服务在有任务时启动，系统拒绝时改走保活接口。
 *
 * @file
 */
package com.arturoyi.fastdowload.notify.background

import android.app.ForegroundServiceStartNotAllowedException
import com.arturoyi.fastdowload.android.background.BackgroundMode
import com.arturoyi.fastdowload.notify.notification.DownloadNotifications
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/**
 * [BackgroundExecutor] 不在没有通知权限时启动服务，也不会在后台限制下崩溃。
 */
@RunWith(RobolectricTestRunner::class)
class BackgroundExecutorTest {
    /**
     * Android 8 有任务时发出前台服务意图，任务清空后停止。
     */
    @Test
    @Config(sdk = [34])
    fun startsAndStopsForegroundService() {
        val context = RuntimeEnvironment.getApplication()
        shadowOf(context).grantPermissions(android.Manifest.permission.POST_NOTIFICATIONS)
        val executor = BackgroundExecutor(context, BackgroundMode.FOREGROUND_SERVICE) {}
        executor.onActiveCount(1)
        val started = shadowOf(context).nextStartedService
        assertEquals(DownloadForegroundService::class.java.name, started?.component?.className)
        val controller = Robolectric.buildService(DownloadForegroundService::class.java, started).create()
        controller.startCommand(0, 1)
        assertNotNull(shadowOf(controller.get()).lastForegroundNotification)
        controller.destroy()
        executor.onActiveCount(0)
        val stopped = shadowOf(context).nextStoppedService
        assertEquals(DownloadForegroundService::class.java.name, stopped?.component?.className)
        executor.close()
    }

    /**
     * Android 13 没有通知权限时不启动服务，也不抛异常。
     */
    @Test
    @Config(sdk = [33])
    fun missingNotificationPermissionDoesNotStartService() {
        val context = RuntimeEnvironment.getApplication()
        val executor = BackgroundExecutor(context, BackgroundMode.FOREGROUND_SERVICE) {}
        executor.onActiveCount(1)
        assertNull(shadowOf(context).nextStartedService)
        executor.close()
    }

    /**
     * 系统拒绝启动前台服务时，改调用保活接口。
     */
    @Test
    @Config(sdk = [31])
    fun degradesToKeepAliveWhenForegroundStartIsBlocked() {
        val context = RuntimeEnvironment.getApplication()
        val keepAlive = RecordingKeepAlive()
        val executor = BackgroundExecutor(
            context = context,
            mode = BackgroundMode.FOREGROUND_SERVICE,
            onPause = {},
            keepAlive = keepAlive,
            startService = { throw ForegroundServiceStartNotAllowedException("background") },
            stopService = {},
            notifications = DownloadNotifications(context),
            sdkInt = 31,
        )
        executor.onActiveCount(1)
        assertEquals(1, keepAlive.started)
        executor.close()
    }

    /**
     * 进程内模式不启动服务。
     */
    @Test
    @Config(sdk = [34])
    fun inProcessDoesNotStartService() {
        val context = RuntimeEnvironment.getApplication()
        val executor = BackgroundExecutor(context, BackgroundMode.IN_PROCESS) {}
        executor.onActiveCount(2)
        assertNull(shadowOf(context).nextStartedService)
        executor.close()
    }

    /**
     * 进程内模式只记录活跃任务数，不启动服务。
     */
    @Test
    @Config(sdk = [34])
    fun inProcessModeTracksActiveCount() {
        val context = RuntimeEnvironment.getApplication()
        val executor = BackgroundExecutor(context, BackgroundMode.IN_PROCESS) {}
        com.arturoyi.fastdowload.testing.BackgroundExecutorChecks.verify(executor)
    }
}

/**
 * 记录保活被调用的次数。
 */
private class RecordingKeepAlive : ProcessKeepAlive {
    var started: Int = 0

    override fun ensureRunning() {
        started += 1
    }

    override fun cancel() = Unit
}
