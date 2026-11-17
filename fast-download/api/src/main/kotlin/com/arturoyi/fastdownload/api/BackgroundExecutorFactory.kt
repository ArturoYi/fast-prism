/**
 * 在下载器建好调度器之前，提供保活实现。
 *
 * 暂停回调要等调度器存在之后才能接到任务上，所以工厂在 [com.arturoyi.fastdownload.api.FastDownloader.create] 里调用。
 *
 * @file
 */
package com.arturoyi.fastdownload.api

import android.content.Context
import com.arturoyi.fastdownload.core.spi.BackgroundExecutor

/**
 * 按暂停回调创建保活。
 */
public fun interface BackgroundExecutorFactory {
    /**
     * 创建这一次下载器要使用的保活。
     *
     * @param context 下载器的 applicationContext。
     * @param onPause 通知里的暂停按钮被按下时调用，参数是任务标识。
     * @return 会在下载器关闭时一起关闭的保活。
     */
    public fun create(context: Context, onPause: (String) -> Unit): BackgroundExecutor
}
