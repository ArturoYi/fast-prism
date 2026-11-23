/**
 * 进程启动时静默准备清理器。
 *
 * 这里只调用 [com.arturoyi.albumcleaner.AlbumCleaner.initialize]。
 * 扫描要等用户在清理页点开始，首次进入不会自己扫。
 *
 * @file
 */
package com.arturoyi.albumcleaner.sample

import android.app.Application
import android.util.Log
import com.arturoyi.albumcleaner.AlbumCleanerFactory
import com.arturoyi.albumcleaner.CleanerConfig
import com.arturoyi.albumcleaner.sample.model.AlbumStore
import com.arturoyi.albumcleaner.sample.settings.AppSettings

/**
 * 创建这份演示使用的清理器。三个页面都读 [album]。
 */
class SampleApp : Application() {
    /** 主题、语言和扫描里程。 */
    internal lateinit var settings: AppSettings
        private set

    /** 三个页面共用的相册状态。扫描也挂在这里，离开页面不会中断。 */
    internal lateinit var album: AlbumStore
        private set

    /**
     * 打开索引并进入就绪。
     *
     * 不调用 [com.arturoyi.albumcleaner.AlbumCleaner.scan]。
     * 索引打不开时仍把仓库交给界面，由界面说明失败。
     */
    override fun onCreate() {
        super.onCreate()
        settings = AppSettings(this)
        val cleaner = AlbumCleanerFactory.create(this)
        var opened = true
        try {
            cleaner.initialize(CleanerConfig())
        } catch (error: RuntimeException) {
            opened = false
            Log.e(TAG, "initialize failed", error)
        }
        album = AlbumStore(cleaner, settings, opened)
    }

    private companion object {
        const val TAG: String = "AlbumSample"
    }
}
