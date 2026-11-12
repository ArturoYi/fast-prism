/**
 * 一个下载里的分块共用的失败信号。
 *
 * @file
 */
package com.arturoyi.fastdowload.engine

import java.util.concurrent.CopyOnWriteArrayList

/**
 * 某个分块失败后，让其余分块停止读取。
 *
 * 这不是暂停或取消入口。只有写入失败时才置位。已经读进内存、尚未同步的字节由调用方决定是否提交。
 */
public class InterruptToken {
    private val tripped = java.util.concurrent.atomic.AtomicBoolean(false)
    private val closers = CopyOnWriteArrayList<() -> Unit>()

    /**
     * 是否已经要求其他分块退出。
     *
     * @return 已经失败并广播时为 true。
     */
    public fun isInterrupted(): Boolean = tripped.get()

    /**
     * 标记失败，并立刻执行已经登记的关闭动作。
     *
     * 多次调用只有第一次会运行关闭动作。
     */
    public fun interrupt() {
        if (!tripped.compareAndSet(false, true)) return
        closers.forEach { closer ->
            try {
                closer()
            } catch (_: RuntimeException) {
                // 关闭连接失败不影响已经置位的信号。
            }
        }
    }

    /**
     * 登记在 [interrupt] 时调用的关闭动作。
     *
     * 如果信号已经置位，[closer] 会立刻执行。
     *
     * @param closer 通常用来关闭当前分块的响应体，使阻塞中的读取返回。
     */
    public fun onInterrupt(closer: () -> Unit) {
        closers.add(closer)
        if (tripped.get()) {
            try {
                closer()
            } catch (_: RuntimeException) {
                // 同上，关闭失败不改变信号。
            }
        }
    }
}
