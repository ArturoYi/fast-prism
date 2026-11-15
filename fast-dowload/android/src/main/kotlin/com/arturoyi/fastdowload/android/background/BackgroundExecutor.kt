/**
 * 有下载任务时尽量让进程留在前台。
 *
 * 三种模式都不保证进程被杀掉之后自动接着下。可靠的恢复是启动时的断点清洗。
 *
 * @file
 */
package com.arturoyi.fastdowload.android.background

import android.content.Context
import android.content.Intent
import android.os.Build
import com.arturoyi.fastdowload.android.notification.DownloadNotifications
import com.arturoyi.fastdowload.android.notification.NotificationReceiverSlot
import java.util.concurrent.atomic.AtomicInteger
import java.util.logging.Level
import java.util.logging.Logger

/**
 * 进程被系统允许时，用前台服务或 WorkManager 保活。
 */
public enum class BackgroundMode {
    /** 只使用调用方已经在跑的线程，不启动服务。 */
    IN_PROCESS,

    /** 有排队或下载中的任务时启动 dataSync 前台服务。系统拒绝时改用 WorkManager。 */
    FOREGROUND_SERVICE,

    /** 把保活交给 WorkManager。 */
    WORK_MANAGER,
}

/**
 * 启动或取消保活工作。
 */
internal interface ProcessKeepAlive {
    /** 保证有一份保活工作在跑。 */
    fun ensureRunning()

    /** 没有活跃任务时取消保活。 */
    fun cancel()
}

/**
 * 按活跃任务数启动或停止后台保活。
 *
 * 没有通知权限时不启动前台服务，下载仍在进程内继续。
 *
 * @param context 用来启动服务。会使用 applicationContext。
 * @param mode 保活方式。
 * @param onPause 通知里的暂停按钮被按下时调用，参数是任务标识。
 */
public class BackgroundExecutor internal constructor(
    private val context: Context,
    private val mode: BackgroundMode,
    onPause: (String) -> Unit,
    private val keepAlive: ProcessKeepAlive,
    private val startService: () -> Unit,
    private val stopService: () -> Unit,
    private val notifications: DownloadNotifications,
    private val sdkInt: Int,
) : com.arturoyi.fastdowload.core.spi.BackgroundExecutor {
    /**
     * 使用真实的前台服务和 WorkManager。
     *
     * @param context 任意上下文。
     * @param mode 保活方式。
     * @param onPause 暂停按钮的处理。
     */
    public constructor(
        context: Context,
        mode: BackgroundMode,
        onPause: (String) -> Unit,
    ) : this(
        context = context.applicationContext,
        mode = mode,
        onPause = onPause,
        keepAlive = LazyWorkKeepAlive(context.applicationContext),
        startService = {
            val appContext = context.applicationContext
            appContext.startForegroundService(Intent(appContext, DownloadForegroundService::class.java))
        },
        stopService = {
            val appContext = context.applicationContext
            appContext.stopService(Intent(appContext, DownloadForegroundService::class.java))
        },
        notifications = DownloadNotifications(context),
        sdkInt = Build.VERSION.SDK_INT,
    )

    private val slot = NotificationReceiverSlot(onPause)
    private var started = false
    private var foregroundDenied = false

    /**
     * 按当前还没结束的任务数调整保活。
     *
     * 0 表示停掉前台服务或 WorkManager。大于 0 时按 [mode] 启动。
     *
     * @param activeCount 排队中和正在下载的任务数。
     */
    public override fun onActiveCount(activeCount: Int) {
        KeepAliveFlag.active.set(activeCount)
        if (mode == BackgroundMode.IN_PROCESS) return
        if (activeCount <= 0) {
            stop()
            return
        }
        if (!slotRegistered) {
            slot.register(context)
            slotRegistered = true
        }
        when (mode) {
            BackgroundMode.IN_PROCESS -> Unit
            BackgroundMode.FOREGROUND_SERVICE -> if (!started) startForeground()
            BackgroundMode.WORK_MANAGER -> keepAlive.ensureRunning()
        }
    }

    /**
     * 最近一次 [onActiveCount] 记下的任务数。
     *
     * 关闭之后为 0。进程里只有这一份计数，后创建的执行器会覆盖它。
     *
     * @return 当前认为还活着的任务数。
     */
    public override fun activeCount(): Int = KeepAliveFlag.active.get()

    /**
     * 注销接收器并停掉保活。
     */
    public override fun close() {
        KeepAliveFlag.active.set(0)
        stop()
        if (slotRegistered) {
            slot.unregister(context)
            slotRegistered = false
        }
    }

    private var slotRegistered = false

    private fun startForeground() {
        if (foregroundDenied) return
        if (!notifications.canPost()) {
            foregroundDenied = true
            logger.log(Level.WARNING, "没有通知权限，前台服务不启动，下载继续在进程内进行")
            return
        }
        try {
            startService()
            started = true
        } catch (error: Exception) {
            if (shouldDegrade(error, sdkInt)) {
                foregroundDenied = true
                logger.log(Level.WARNING, "当前不能启动前台服务，改用 WorkManager。进程被杀掉后不会自动续跑")
                keepAlive.ensureRunning()
            } else {
                throw error
            }
        }
    }

    private fun stop() {
        foregroundDenied = false
        if (started) {
            stopService()
            started = false
        }
        keepAlive.cancel()
    }

    public companion object {
        private val logger: Logger = Logger.getLogger("com.arturoyi.fastdowload.android.background")

        /**
         * Android 12 及以后，后台不允许启动前台服务时改走 WorkManager。
         *
         * @param error 启动服务时抛出的异常。
         * @param sdkInt 当前系统 API。
         * @return 应该降级时为 true。
         */
        public fun shouldDegrade(error: Exception, sdkInt: Int): Boolean {
            return sdkInt >= 31 &&
                error.javaClass.name == "android.app.ForegroundServiceStartNotAllowedException"
        }
    }
}

/**
 * WorkManager 用来判断还有没有下载。
 */
internal object KeepAliveFlag {
    val active: AtomicInteger = AtomicInteger()
}

/**
 * 第一次真正保活时才加载 WorkManager，避免只在进程内下载时初始化它。
 */
private class LazyWorkKeepAlive(
    private val context: Context,
) : ProcessKeepAlive {
    private val real by lazy { WorkManagerKeepAlive(context) }

    override fun ensureRunning() = real.ensureRunning()

    override fun cancel() = real.cancel()
}
