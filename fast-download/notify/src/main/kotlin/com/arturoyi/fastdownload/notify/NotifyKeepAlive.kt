@file:JvmName("NotifyKeepAlive")

/**
 * 把前台保活注册到下载器配置。
 *
 * 暂停按钮由 [com.arturoyi.fastdownload.api.FastDownloader.create] 接到调度器，调用方不用自己实现保活。
 *
 * @file
 */
package com.arturoyi.fastdownload.notify

import android.content.Context
import com.arturoyi.fastdownload.android.background.BackgroundMode
import com.arturoyi.fastdownload.api.BackgroundExecutorFactory
import com.arturoyi.fastdownload.api.DownloaderConfig
import com.arturoyi.fastdownload.core.spi.BackgroundExecutor
import com.arturoyi.fastdownload.notify.background.BackgroundExecutor as NotifyExecutor

/**
 * 有任务时启动 dataSync 前台服务，系统拒绝时改走 WorkManager。
 *
 * 同时记下 [BackgroundMode.FOREGROUND_SERVICE]。进程被杀掉后不会自动续跑。
 *
 * @return 这个建造器。
 */
public fun DownloaderConfig.Builder.foregroundService(): DownloaderConfig.Builder {
    return backgroundMode(BackgroundMode.FOREGROUND_SERVICE)
        .backgroundExecutor(ForegroundKeepAliveFactory)
}

/**
 * 在下载器创建调度器时构造前台保活。
 */
internal object ForegroundKeepAliveFactory : BackgroundExecutorFactory {
    override fun create(context: Context, onPause: (String) -> Unit): BackgroundExecutor {
        return NotifyExecutor(context, BackgroundMode.FOREGROUND_SERVICE, onPause)
    }
}
