/**
 * 把下载事件交给监听器。
 *
 * @file
 */
package com.arturoyi.fastdownload.core.spi

import com.arturoyi.fastdownload.core.event.DownloadEvent

/**
 * 收到一条下载事件。
 */
public fun interface DownloadEventListener {
    /**
     * 处理 [event]。
     *
     * 调用线程由 [EventDispatcher] 决定。
     *
     * @param event 刚发生的事。
     */
    public fun onEvent(event: DownloadEvent)
}

/**
 * 监听器的投递方式。
 *
 * 同一个监听器实例重复登记只保留一份。摘除时必须传入登记时的那个实例。
 */
public interface EventDispatcher {
    /**
     * 把 [event] 交给当前已登记的监听器。
     *
     * @param event 要投递的事件。
     */
    public fun dispatch(event: DownloadEvent)

    /**
     * 登记 [listener]。
     *
     * @param listener 之后的事件会交给它。
     */
    public fun addListener(listener: DownloadEventListener)

    /**
     * 去掉 [listener]。
     *
     * @param listener [addListener] 时的同一个实例。
     */
    public fun removeListener(listener: DownloadEventListener)
}
