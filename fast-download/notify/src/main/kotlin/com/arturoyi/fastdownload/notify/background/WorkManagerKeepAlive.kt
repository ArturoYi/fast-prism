/**
 * 用 WorkManager 在系统允许的窗口里继续保活。
 *
 * 工作本身不下载字节。下载仍由进程内的调度器执行。活跃任务变为 0 后工作结束。
 *
 * @file
 */
package com.arturoyi.fastdownload.notify.background

import android.content.Context
import android.os.Build
import androidx.work.ExistingWorkPolicy
import androidx.work.ForegroundInfo
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.OutOfQuotaPolicy
import androidx.work.WorkManager
import androidx.work.Worker
import androidx.work.WorkerParameters
import com.arturoyi.fastdownload.notify.notification.DownloadNotifications
import java.util.logging.Level
import java.util.logging.Logger

/**
 * 排队一份唯一的保活工作，或在没有任务时取消它。
 *
 * WorkManager 还没初始化、或当前进程不能排队时只记一条警告。
 *
 * @param context 用来取得 WorkManager。
 */
internal class WorkManagerKeepAlive(
    context: Context,
) : ProcessKeepAlive {
    private val appContext = context.applicationContext

    override fun ensureRunning() {
        try {
            val request = OneTimeWorkRequestBuilder<DownloadKeepAliveWorker>()
                .setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
                .build()
            WorkManager.getInstance(appContext).enqueueUniqueWork(
                UNIQUE_NAME,
                ExistingWorkPolicy.KEEP,
                request,
            )
        } catch (error: IllegalStateException) {
            logger.log(Level.WARNING, "WorkManager 不可用，下载继续在进程内进行", error)
        }
    }

    override fun cancel() {
        try {
            WorkManager.getInstance(appContext).cancelUniqueWork(UNIQUE_NAME)
        } catch (_: IllegalStateException) {
            Unit
        }
    }

    private companion object {
        private const val UNIQUE_NAME: String = "fastdownload-keepalive"
        private val logger: Logger = Logger.getLogger("com.arturoyi.fastdownload.notify.background.work")
    }
}

/**
 * 在有活跃任务时保持一条前台信息，任务清空后结束。
 *
 * @param context 系统传入的上下文。
 * @param params 工作参数。
 */
public class DownloadKeepAliveWorker(
    context: Context,
    params: WorkerParameters,
) : Worker(context, params) {
    /**
     * 挂上前台通知，然后等到没有活跃任务或被取消。
     *
     * @return 正常结束时为成功。
     */
    override fun doWork(): Result {
        val notification = DownloadNotifications(applicationContext).foreground("下载进行中", publicId = null)
        val info = if (Build.VERSION.SDK_INT >= 29) {
            ForegroundInfo(DownloadNotifications.ID, notification, Api29Foreground.typeDataSync())
        } else {
            ForegroundInfo(DownloadNotifications.ID, notification)
        }
        setForegroundAsync(info)
        while (!isStopped && KeepAliveFlag.active.get() > 0) {
            try {
                Thread.sleep(250)
            } catch (_: InterruptedException) {
                return Result.success()
            }
        }
        return Result.success()
    }
}
