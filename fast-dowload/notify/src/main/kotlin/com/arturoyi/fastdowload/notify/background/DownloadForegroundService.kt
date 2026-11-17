/**
 * 有活跃下载时显示的前台服务。
 *
 * 服务被系统销毁后不会自己再启动。未完成的任务等下次进程启动后从断点继续。
 *
 * @file
 */
package com.arturoyi.fastdowload.notify.background

import android.app.Service
import android.content.Intent
import android.os.Build
import android.os.IBinder
import androidx.annotation.RequiresApi
import com.arturoyi.fastdowload.notify.notification.DownloadNotifications

/**
 * dataSync 前台服务。通知在 [onStartCommand] 里立刻挂上。
 */
public class DownloadForegroundService : Service() {
    /**
     * 不提供绑定。
     *
     * @param intent 启动意图。
     * @return 始终为 null。
     */
    override fun onBind(intent: Intent?): IBinder? = null

    /**
     * 立刻调用 [startForeground]，避免系统在超时后杀掉进程。
     *
     * 返回 [START_NOT_STICKY]，进程被杀后不让系统重启这个服务。
     *
     * @param intent 启动意图。
     * @param flags 系统标志。
     * @param startId 这一次启动的编号。
     * @return [START_NOT_STICKY]。
     */
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val notification = DownloadNotifications(this).foreground("下载进行中", publicId = null)
        if (Build.VERSION.SDK_INT >= 29) {
            Api29Foreground.start(this, DownloadNotifications.ID, notification)
        } else {
            startForeground(DownloadNotifications.ID, notification)
        }
        return START_NOT_STICKY
    }

    /**
     * 去掉前台通知。
     */
    override fun onDestroy() {
        stopForeground(STOP_FOREGROUND_REMOVE)
        super.onDestroy()
    }
}

/**
 * 只在 Android 10 及以上调用。注解让 lint 按 API 29 检查，而不是按 minSdk 26。
 */
@RequiresApi(29)
internal object Api29Foreground {
    /**
     * 带 dataSync 类型启动前台服务。
     *
     * @param service 已经创建的服务。
     * @param id 通知编号。
     * @param notification 要展示的通知。
     */
    fun start(service: android.app.Service, id: Int, notification: android.app.Notification) {
        service.startForeground(id, notification, android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
    }

    /**
     * dataSync 的数字类型，给 WorkManager 的前台信息使用。
     *
     * @return 系统定义的类型值。
     */
    fun typeDataSync(): Int = android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
}
