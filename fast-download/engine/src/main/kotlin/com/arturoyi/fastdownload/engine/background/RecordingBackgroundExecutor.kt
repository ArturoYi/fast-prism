/**
 * 只记住活跃任务数，不启动服务。
 *
 * @file
 */
package com.arturoyi.fastdownload.engine.background

import com.arturoyi.fastdownload.core.spi.BackgroundExecutor
import java.util.concurrent.atomic.AtomicInteger

/**
 * 进程内保活的替身。
 *
 * 不创建线程，也不接触系统服务。下载调度自己负责执行。
 */
public class RecordingBackgroundExecutor : BackgroundExecutor {
    private val count = AtomicInteger()

    /**
     * 记下 [activeCount]。
     *
     * @param activeCount 排队中和正在下载的任务数。
     * @throws IllegalArgumentException [activeCount] 为负。
     */
    public override fun onActiveCount(activeCount: Int) {
        require(activeCount >= 0) { "activeCount 不能为负" }
        count.set(activeCount)
    }

    /**
     * 最近一次记下的数量。
     *
     * @return 关闭之后为 0。
     */
    public override fun activeCount(): Int = count.get()

    /**
     * 把记下的数量清零。
     */
    public override fun close() {
        count.set(0)
    }
}
