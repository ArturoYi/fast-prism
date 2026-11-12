/**
 * 演示单连接下载的界面。
 *
 * 一次只跑一条下载。请求在后台线程执行，进度回到主线程更新界面。
 * 进程被系统回收后不恢复未完成的下载。
 *
 * @file
 */
package com.arturoyi.fastdowload.sample

import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.widget.Button
import android.widget.ProgressBar
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import com.arturoyi.fastdowload.core.model.Destination
import com.arturoyi.fastdowload.core.model.DownloadRequest
import com.arturoyi.fastdowload.engine.NaiveDownloadCall
import java.io.File
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean
import java.lang.ref.WeakReference

/**
 * 样本页：一个按钮、一条进度和一个状态文本。
 *
 * 文件写到 `filesDir` 下的字符串资源路径。界面颜色跟随窗口背景，状态栏和导航栏使用浅色图标。
 */
class MainActivity : AppCompatActivity() {
    /**
     * 创建界面并把已有下载状态画到新实例上。
     *
     * 旋转或重建 Activity 时，进行中的下载仍由 [Session] 持有，这里只重新绑定界面。
     *
     * @param savedInstanceState 系统保存的视图状态。下载进度不放在这里。
     */
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        val background = ContextCompat.getColor(this, R.color.background)
        window.statusBarColor = background
        window.navigationBarColor = background
        val insets = WindowCompat.getInsetsController(window, window.decorView)
        insets.isAppearanceLightStatusBars = true
        insets.isAppearanceLightNavigationBars = true
        Session.attach(this)
        findViewById<Button>(R.id.download).setOnClickListener { startDownload() }
    }

    /**
     * 解除界面绑定。
     *
     * 不取消后台下载。只有当前实例仍是 [Session] 的宿主时才清除引用。
     */
    override fun onDestroy() {
        Session.detach(this)
        super.onDestroy()
    }

    /**
     * 若当前没有下载，则在后台线程开始一条新的下载。
     *
     * 地址和相对路径来自字符串资源。进度至少每 256 KiB 记一次日志，结束时再记一次。
     * 已有下载在进行时直接返回。
     */
    private fun startDownload() {
        val relativePath = getString(R.string.demo_relative_path)
        val path = File(filesDir, relativePath).absolutePath
        if (!Session.tryStart(path)) return
        val root = filesDir
        val request = DownloadRequest(
            url = getString(R.string.demo_url),
            destination = Destination(relativePath),
        )
        Log.i(TAG, "start path=$path")
        Session.worker.execute {
            try {
                var lastLogged = -1L
                val written = NaiveDownloadCall(request, root) { downloaded, total ->
                    val crossed = lastLogged < 0L || downloaded - lastLogged >= LOG_STEP
                    val finished = total >= 0L && downloaded == total
                    if (crossed || finished) {
                        Log.d(TAG, "progress downloaded=$downloaded total=$total path=$path")
                        lastLogged = downloaded
                    }
                    Session.onProgress(downloaded, total, path)
                }.execute()
                Log.i(TAG, "done bytes=$written path=$path")
                Session.onComplete(written, path)
            } catch (error: Exception) {
                Log.e(TAG, "failed path=$path", error)
                Session.onFailed(error.message ?: error.javaClass.simpleName)
            }
        }
    }

    /**
     * 按 [phase] 更新按钮、进度条、状态和路径。
     *
     * 必须在主线程调用。总长度未知时进度条改为不确定模式。
     *
     * @param phase 当前要展示的阶段。
     */
    internal fun render(phase: Phase) {
        val button = findViewById<Button>(R.id.download)
        val progressBar = findViewById<ProgressBar>(R.id.progress)
        val statusView = findViewById<TextView>(R.id.status)
        val pathView = findViewById<TextView>(R.id.path)
        when (phase) {
            Phase.Idle -> {
                button.isEnabled = true
                button.setText(R.string.action_download)
                progressBar.isIndeterminate = false
                progressBar.progress = 0
                statusView.setText(R.string.status_idle)
                pathView.setText(R.string.path_idle)
            }
            is Phase.Running -> {
                button.isEnabled = false
                button.setText(R.string.action_downloading)
                pathView.text = getString(R.string.path_value, phase.path)
                if (phase.total <= 0L) {
                    progressBar.isIndeterminate = true
                    statusView.text = getString(R.string.progress_unknown, phase.downloaded)
                } else {
                    progressBar.isIndeterminate = false
                    progressBar.progress = ((phase.downloaded * 1000L) / phase.total).toInt().coerceIn(0, 1000)
                    statusView.text = getString(R.string.progress_known, phase.downloaded, phase.total)
                }
            }
            is Phase.Done -> {
                button.isEnabled = true
                button.setText(R.string.action_download)
                progressBar.isIndeterminate = false
                progressBar.progress = 1000
                statusView.text = getString(R.string.status_done, phase.bytes)
                pathView.text = getString(R.string.path_value, phase.path)
            }
            is Phase.Failed -> {
                button.isEnabled = true
                button.setText(R.string.action_download)
                progressBar.isIndeterminate = false
                statusView.text = getString(R.string.status_failed, phase.message)
                pathView.setText(R.string.path_idle)
            }
        }
    }

    private companion object {
        /** logcat 标签。 */
        const val TAG = "FastDowload"

        /** 两次进度日志之间至少增加的字节数，256 KiB。 */
        const val LOG_STEP = 256L * 1024L
    }
}

/**
 * 样本界面上的下载阶段。
 *
 * 界面重建后仍由 [Session] 保存最新值，再画到新的 Activity。
 */
internal sealed interface Phase {
    /** 还没有开始，或上一轮结果已被新的开始覆盖之前的空闲态。 */
    data object Idle : Phase

    /**
     * 下载进行中。
     *
     * @property downloaded 已写入字节数。
     * @property total 响应声明的总字节数。未知时为 `-1`。
     * @property path 目标文件的绝对路径，只用于展示。
     */
    data class Running(
        val downloaded: Long,
        val total: Long,
        val path: String,
    ) : Phase

    /**
     * 下载成功结束。
     *
     * @property bytes 实际写入的字节数。
     * @property path 目标文件的绝对路径。
     */
    data class Done(
        val bytes: Long,
        val path: String,
    ) : Phase

    /**
     * 下载失败。
     *
     * @property message 异常消息；异常没有消息时是异常类名。
     */
    data class Failed(
        val message: String,
    ) : Phase
}

/**
 * 跨 Activity 重建保持的一条下载。
 *
 * 使用弱引用指向当前界面，避免下载线程撑住已销毁的 Activity。
 * 状态发布到主线程后再读取，界面只观察最新阶段。
 */
private object Session {
    /** 串行执行下载的单线程池，线程名是 `fast-dowload-sample`。 */
    val worker: java.util.concurrent.ExecutorService = Executors.newSingleThreadExecutor { runnable ->
        Thread(runnable, "fast-dowload-sample")
    }
    private val main = Handler(Looper.getMainLooper())
    private val running = AtomicBoolean(false)
    private var host = WeakReference<MainActivity>(null)
    private var phase: Phase = Phase.Idle

    /**
     * 绑定当前界面并立刻画出已有阶段。
     *
     * @param activity 前台 Activity。后一次绑定替换前一次。
     */
    fun attach(activity: MainActivity) {
        host = WeakReference(activity)
        activity.render(phase)
    }

    /**
     * 若 [activity] 仍是当前宿主，则丢掉界面引用。
     *
     * 不改变 [phase]，也不中断下载。
     *
     * @param activity 正在销毁的 Activity。
     */
    fun detach(activity: MainActivity) {
        if (host.get() === activity) {
            host.clear()
        }
    }

    /**
     * 尝试把阶段切到进行中。
     *
     * 已经有下载在跑时返回 false，并且不修改阶段。
     *
     * @param path 展示用的绝对路径。
     * @return 成功占用下载槽时为 true。
     */
    fun tryStart(path: String): Boolean {
        if (!running.compareAndSet(false, true)) return false
        publish(Phase.Running(downloaded = 0L, total = -1L, path = path))
        return true
    }

    /**
     * 发布一次进行中的进度。
     *
     * @param downloaded 已写入字节数。
     * @param total 总字节数，未知时为 `-1`。
     * @param path 展示用的绝对路径。
     */
    fun onProgress(downloaded: Long, total: Long, path: String) {
        publish(Phase.Running(downloaded, total, path))
    }

    /**
     * 发布成功结果并释放下载槽。
     *
     * @param bytes 实际写入的字节数。
     * @param path 展示用的绝对路径。
     */
    fun onComplete(bytes: Long, path: String) {
        running.set(false)
        publish(Phase.Done(bytes, path))
    }

    /**
     * 发布失败结果并释放下载槽。
     *
     * @param message 展示给界面的失败说明。
     */
    fun onFailed(message: String) {
        running.set(false)
        publish(Phase.Failed(message))
    }

    /**
     * 把 [next] 切到主线程保存并画到当前界面。
     *
     * 界面已经销毁时只更新 [phase]，下次 [attach] 再画。
     *
     * @param next 新的阶段。后发布的阶段覆盖先发布的阶段。
     */
    private fun publish(next: Phase) {
        main.post {
            phase = next
            host.get()?.render(next)
        }
    }
}
