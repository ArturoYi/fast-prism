/**
 * 默认保活：只记住活跃任务数，不启动服务。
 *
 * @file
 */
package com.arturoyi.fastdowload.api

import com.arturoyi.fastdowload.core.spi.BackgroundExecutor
import java.util.concurrent.atomic.AtomicInteger

/**
 * 进程内计数。没有通知，也没有前台服务。
 */
internal class InProcessBackground : BackgroundExecutor {
    private val active = AtomicInteger()

    override fun onActiveCount(activeCount: Int) {
        active.set(activeCount)
    }

    override fun activeCount(): Int = active.get()

    override fun close() {
        active.set(0)
    }
}
