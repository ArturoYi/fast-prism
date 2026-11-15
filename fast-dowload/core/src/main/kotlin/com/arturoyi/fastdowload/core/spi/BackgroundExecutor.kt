/**
 * 有下载任务时尽量让进程留下来。
 *
 * 任何实现都不保证进程被杀掉之后自动接着下。可靠的恢复是下次启动时的断点。
 *
 * @file
 */
package com.arturoyi.fastdowload.core.spi

/**
 * 按活跃任务数启动或停止保活。
 *
 * 活跃任务是排队中和正在下载的任务。实现要能被命令线程调用。
 */
public interface BackgroundExecutor : AutoCloseable {
    /**
     * 按 [activeCount] 调整保活。
     *
     * 0 表示没有需要保活的任务。
     *
     * @param activeCount 排队中和正在下载的任务数，不能为负。
     */
    public fun onActiveCount(activeCount: Int)

    /**
     * 最近一次 [onActiveCount] 记下的数量。
     *
     * 关闭之后为 0。
     *
     * @return 当前认为还活着的任务数。
     */
    public fun activeCount(): Int
}
