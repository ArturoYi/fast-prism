/**
 * 三行接入：初始化在 [SampleApp]，这里组请求并入队。
 *
 * @file
 */
package com.arturoyi.fastdowload.sample

import android.os.Bundle
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import com.arturoyi.fastdowload.api.Downloader
import com.arturoyi.fastdowload.api.SimpleDownloadListener
import com.arturoyi.fastdowload.api.SnapshotListener
import com.arturoyi.fastdowload.api.downloadRequest
import com.arturoyi.fastdowload.core.model.DownloadRecord
import com.arturoyi.fastdowload.core.model.Progress
import com.arturoyi.fastdowload.core.state.Status
import java.io.File

/**
 * 样本页：开始下载，并列出已经保存在数据库里的任务。
 *
 * 文件写到 `filesDir`。界面只用黑白灰，按钮高度 48dp。
 */
class MainActivity : AppCompatActivity() {
    private var records: List<DownloadRecord> = emptyList()
    private val live = HashMap<String, Progress>()
    private var snapshots: SnapshotListener? = null
    private var progressListener: SimpleDownloadListener? = null

    /**
     * 画出按钮、进度和任务列表。
     *
     * @param savedInstanceState 系统保存的视图状态。任务列表来自数据库。
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
        findViewById<Button>(R.id.download).setOnClickListener { enqueue() }
    }

    /**
     * 开始在主线程接收任务列表和进度。
     */
    override fun onStart() {
        super.onStart()
        val downloader = Downloader.getDefault()
        val snapshotListener = SnapshotListener { latest ->
            records = latest
            render()
        }
        val progress = object : SimpleDownloadListener() {
            override fun onProgress(record: DownloadRecord, progress: Progress) {
                live[record.id] = progress
                render()
            }
        }
        snapshots = snapshotListener
        progressListener = progress
        downloader.addSnapshotListener(this, snapshotListener)
        downloader.addListener(progress, this)
    }

    /**
     * 页面不可见时摘掉监听，避免同一次页面生命周期里重复登记。
     */
    override fun onStop() {
        val downloader = Downloader.getDefault()
        snapshots?.let(downloader::removeSnapshotListener)
        progressListener?.let(downloader::removeListener)
        snapshots = null
        progressListener = null
        super.onStop()
    }

    /**
     * 把示例地址放进队列。
     */
    private fun enqueue() {
        val id = Downloader.getDefault().tasks.enqueue(
            downloadRequest(getString(R.string.demo_url)) {
                saveTo("downloads", "fast-dowload-10m.bin")
            },
        )
        findViewById<TextView>(R.id.path).text = id
    }

    /**
     * 用最新任务刷新进度，并重画列表。
     */
    private fun render() {
        val current = records.firstOrNull { it.status == Status.DOWNLOADING }
            ?: records.maxByOrNull { it.updatedAt }
        val progress = findViewById<ProgressBar>(R.id.progress)
        val status = findViewById<TextView>(R.id.status)
        val path = findViewById<TextView>(R.id.path)
        if (current == null) {
            progress.progress = 0
            status.setText(R.string.status_idle)
            path.setText(R.string.path_idle)
        } else {
            val shown = live[current.id]
            val downloaded = shown?.downloadedBytes ?: current.downloadedBytes
            val total = shown?.totalBytes ?: current.totalBytes
            progress.progress = if (total > 0L) ((downloaded * 1000L) / total).toInt() else 0
            status.text = if (current.status == Status.COMPLETED) {
                getString(R.string.status_done, downloaded.toInt())
            } else if (current.status == Status.FAILED) {
                getString(R.string.status_failed, current.error?.message ?: "")
            } else if (total > 0L) {
                getString(R.string.progress_known, downloaded.toInt(), total.toInt())
            } else {
                getString(R.string.progress_unknown, downloaded.toInt())
            }
            path.text = File(filesDir, current.request.destination.relativePath).absolutePath
        }
        val list = findViewById<LinearLayout>(R.id.tasks)
        list.removeAllViews()
        if (records.isEmpty()) {
            list.addView(label(getString(R.string.tasks_empty), muted = true))
            return
        }
        records.forEach { record ->
            list.addView(
                label(getString(R.string.task_row, statusName(record.status), record.request.url), muted = false),
            )
        }
    }

    /**
     * 做一行列表文字。
     *
     * @param text 要显示的内容。
     * @param muted 为 true 时用次要灰色。
     * @return 至少 48dp 高的文本行。
     */
    private fun label(text: String, muted: Boolean): TextView {
        return TextView(this).apply {
            this.text = text
            textSize = 16f
            minHeight = (48 * resources.displayMetrics.density).toInt()
            setTextColor(ContextCompat.getColor(this@MainActivity, if (muted) R.color.muted else R.color.ink))
            setPadding(0, (8 * resources.displayMetrics.density).toInt(), 0, (8 * resources.displayMetrics.density).toInt())
        }
    }

    /**
     * 把状态显示成中文。
     *
     * @param status 任务状态。
     * @return 界面上的短名称。
     */
    private fun statusName(status: Status): String {
        return when (status) {
            Status.ADDED -> "已登记"
            Status.QUEUED -> "排队"
            Status.WAITING_NETWORK -> "等网络"
            Status.DOWNLOADING -> "下载中"
            Status.PAUSED -> "已暂停"
            Status.COMPLETED -> "完成"
            Status.FAILED -> "失败"
            Status.CANCELLED -> "已取消"
            Status.REMOVED -> "已移除"
            Status.DELETED -> "已删除"
        }
    }
}
