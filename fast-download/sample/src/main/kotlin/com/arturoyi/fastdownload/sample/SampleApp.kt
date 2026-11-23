/**
 * 进程启动时准备下载器。
 *
 * [SampleApp.onCreate] 把启动配置写全。名字仍是 default，界面用 [com.arturoyi.fastdownload.api.FastDownloader.getDefault] 取回。
 * 任务列表挂在 [SampleApp.downloads] 上，三个页面的 ViewModel 都读它。
 *
 * @file
 */
package com.arturoyi.fastdownload.sample

import android.app.Application
import com.arturoyi.fastdownload.android.db.RoomDownloadRepository
import com.arturoyi.fastdownload.api.FastDownloader
import com.arturoyi.fastdownload.core.model.Defaults
import com.arturoyi.fastdownload.core.strategy.AdaptiveBreakpointStrategy
import com.arturoyi.fastdownload.engine.file.DefaultFilePolicy
import com.arturoyi.fastdownload.engine.monitor.LoggingDownloadMonitor
import com.arturoyi.fastdownload.engine.verify.DefaultIntegrityVerifier
import com.arturoyi.fastdownload.notify.foregroundService
import com.arturoyi.fastdownload.okhttp.OkHttpHttpClient
import com.arturoyi.fastdownload.sample.model.DownloadStore
import com.arturoyi.fastdownload.sample.settings.AppSettings

/** 示例文件写入的内部相对目录。 */
internal const val DOWNLOAD_DIRECTORY: String = "downloads"

/** 同时下载的任务数。多出来的会排队。 */
internal const val CONCURRENT_DOWNLOADS: Int = 3

/**
 * 创建这份演示使用的下载器，并准备三个页面共用的任务列表。
 */
class SampleApp : Application() {
    /** 主题和语言。三个页面都读它。 */
    internal lateinit var settings: AppSettings
        private set

    /** 三个页面共用的任务列表。 */
    internal lateinit var downloads: DownloadStore
        private set

    /**
     * 写全这份演示的启动配置。
     *
     * 不调用 `networkType`，表示不额外限制网络，只看每条任务自己的要求。
     * 不调用 `outputTarget`，文件仍写到应用内部目录，路径由 `saveTo` 决定。
     * `foregroundService` 打开前台通知，系统拒绝时改走 WorkManager。
     */
    override fun onCreate() {
        super.onCreate()
        settings = AppSettings(this)
        val namespace = Defaults.GROUP
        FastDownloader.init(this) {
            namespace(namespace)
            httpClient(OkHttpHttpClient())
            repository(
                RoomDownloadRepository(
                    this@SampleApp,
                    FastDownloader.databaseName(namespace),
                ),
            )
            concurrentLimit(CONCURRENT_DOWNLOADS)
            autoStart(true)
            globalSpeedLimit(null)
            logging(true)
            breakpointStrategy(AdaptiveBreakpointStrategy)
            filePolicy(DefaultFilePolicy())
            integrityVerifier(DefaultIntegrityVerifier)
            monitor(LoggingDownloadMonitor())
            foregroundService()
        }
        downloads = DownloadStore(FastDownloader.getDefault())
    }
}
