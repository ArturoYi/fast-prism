/**
 * 进程启动时准备好默认下载器。
 *
 * @file
 */
package com.arturoyi.fastdownload.sample

import android.app.Application
import com.arturoyi.fastdownload.api.FastDownloader
import com.arturoyi.fastdownload.notify.foregroundService

/**
 * 零配置初始化。没有单独注册 HTTP 客户端时，使用内置连接。
 */
class SampleApp : Application() {
    /**
     * 创建默认命名空间的下载器，并清洗上次残留的任务。
     */
    override fun onCreate() {
        super.onCreate()
        FastDownloader.init(this) {
            foregroundService()
        }
    }
}
