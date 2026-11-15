/**
 * 下载进行中的通知，以及暂停广播。
 *
 * 注册和注销使用同一个接收器实例。
 *
 * @file
 */
package com.arturoyi.fastdowload.android.notification

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.graphics.drawable.Icon
import android.os.Build

/**
 * 收到暂停广播时取出任务标识。
 *
 * @param onPause 暂停这一条任务。
 */
public class NotificationActionReceiver(
    private val onPause: (String) -> Unit,
) : BroadcastReceiver() {
    /**
     * 处理 [ACTION_PAUSE]。没有任务标识时忽略。
     *
     * @param context 收到广播的上下文。
     * @param intent 广播内容。
     */
    public override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION_PAUSE) return
        val id = intent.getStringExtra(EXTRA_ID) ?: return
        onPause(id)
    }

    public companion object {
        /** 暂停一条任务。 */
        public const val ACTION_PAUSE: String = "com.arturoyi.fastdowload.action.PAUSE_DOWNLOAD"

        /** 任务标识。 */
        public const val EXTRA_ID: String = "id"
    }
}

/**
 * 持有唯一的 [NotificationActionReceiver]。
 *
 * @param onPause 暂停广播的处理。
 */
public class NotificationReceiverSlot(
    onPause: (String) -> Unit,
) {
    /** 注册和注销共用的接收器。 */
    public val receiver: NotificationActionReceiver = NotificationActionReceiver(onPause)

    private var registered = false

    /**
     * 注册 [receiver]。已经注册时不再注册。
     *
     * @param context 用来注册的上下文。
     */
    public fun register(context: Context) {
        if (registered) return
        val filter = IntentFilter(NotificationActionReceiver.ACTION_PAUSE)
        if (Build.VERSION.SDK_INT >= 33) {
            context.registerReceiver(receiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            context.registerReceiver(receiver, filter)
        }
        registered = true
    }

    /**
     * 注销 [receiver]。没有注册时什么也不做。
     *
     * @param context 当时注册用的上下文。
     */
    public fun unregister(context: Context) {
        if (!registered) return
        context.unregisterReceiver(receiver)
        registered = false
    }
}

/**
 * 前台服务和 WorkManager 共用的通知。
 *
 * @param context 用来建渠道和读取权限。会使用 applicationContext。
 */
public class DownloadNotifications(
    context: Context,
) {
    private val appContext = context.applicationContext

    /**
     * 当前能不能显示通知。
     *
     * Android 13 之前不需要运行时权限。没有权限时调用方应继续下载，只是不显示通知。
     *
     * @return 可以交给 [foreground] 时为 true。
     */
    public fun canPost(): Boolean {
        if (Build.VERSION.SDK_INT < 33) return true
        return appContext.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED
    }

    /**
     * 建一条常驻下载通知。
     *
     * 暂停按钮发给 [NotificationActionReceiver]，并且限定在本应用包名内。
     *
     * @param text 通知正文。
     * @param publicId 暂停按钮要带上的任务标识。null 时按钮不带标识，接收方会忽略。
     * @return 可以交给前台服务的通知。
     */
    public fun foreground(text: String, publicId: String?): Notification {
        ensureChannel()
        val pause = Intent(NotificationActionReceiver.ACTION_PAUSE)
            .setPackage(appContext.packageName)
            .putExtra(NotificationActionReceiver.EXTRA_ID, publicId)
        val pending = PendingIntent.getBroadcast(
            appContext,
            0,
            pause,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val action = Notification.Action.Builder(
            Icon.createWithResource(appContext, android.R.drawable.ic_media_pause),
            "暂停",
            pending,
        ).build()
        return Notification.Builder(appContext, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setContentTitle("正在下载")
            .setContentText(text)
            .setOngoing(true)
            .addAction(action)
            .build()
    }

    private fun ensureChannel() {
        val manager = appContext.getSystemService(NotificationManager::class.java) ?: return
        if (manager.getNotificationChannel(CHANNEL_ID) != null) return
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL_ID, "下载", NotificationManager.IMPORTANCE_LOW),
        )
    }

    public companion object {
        /** 前台服务和通知使用的同一个编号。 */
        public const val ID: Int = 0x4644

        /** 通知渠道。 */
        public const val CHANNEL_ID: String = "fast_dowload_android_downloads"
    }
}
