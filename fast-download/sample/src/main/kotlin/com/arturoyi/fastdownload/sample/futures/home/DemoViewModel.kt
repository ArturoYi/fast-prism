/**
 * 演示壳上要看的状态。
 *
 * 三个页面各自有 ViewModel。这里只把「有没有开始下载」交给壳，用来询问通知权限。
 *
 * @file
 */
package com.arturoyi.fastdownload.sample.futures.home

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import com.arturoyi.fastdownload.sample.SampleApp

/**
 * 读任务列表上的下载次数。
 *
 * @param application 用来取 [SampleApp.downloads]。
 */
internal class DemoViewModel(
    application: Application,
) : AndroidViewModel(application) {
    private val downloads = (application as SampleApp).downloads

    /**
     * 有新的下载开始时加一。
     *
     * 界面看到它变大，就去请求通知权限。
     */
    val downloadAttempts: Int
        get() = downloads.downloadAttempts
}
