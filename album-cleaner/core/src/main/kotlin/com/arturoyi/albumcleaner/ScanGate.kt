/**
 * 一次扫描的取消开关。不依赖 Android，方便在入口里替换实现。
 *
 * @file
 */
package com.arturoyi.albumcleaner

/**
 * 扫描进行中时，取消会通知正在等待的查询。
 *
 * 只能登记一个监听。重复取消不会再次通知。已经取消后再登记，会马上调用。
 */
internal class ScanGate {
    private val lock = Any()

    @Volatile
    private var cancelled: Boolean = false
    private var listener: (() -> Unit)? = null

    /** 已经取消。 */
    fun isCancelled(): Boolean = cancelled

    /** 第一次取消时通知监听。重复调用不再通知。 */
    fun cancel() {
        val callback = synchronized(lock) {
            if (cancelled) {
                null
            } else {
                cancelled = true
                listener
            }
        }
        callback?.invoke()
    }

    /**
     * 登记取消回调。
     *
     * 已经取消时立刻调用 [callback]。
     */
    fun onCancel(callback: () -> Unit) {
        val runNow = synchronized(lock) {
            listener = callback
            cancelled
        }
        if (runNow) callback()
    }
}
