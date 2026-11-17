/**
 * 内存里的分块下载事件投递。
 *
 * @file
 */
package com.arturoyi.fastdownload.engine.event

import com.arturoyi.fastdownload.core.event.DownloadEvent
import com.arturoyi.fastdownload.core.spi.DownloadEventListener
import com.arturoyi.fastdownload.core.spi.EventDispatcher
import java.io.Closeable
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.Executors

/**
 * 在调用 [dispatch] 的线程上立刻通知监听器。
 */
public class ImmediateEventDispatcher : EventDispatcher {
    private val listeners = CopyOnWriteArrayList<DownloadEventListener>()

    /**
     * 把 [event] 同步交给当前监听器。
     *
     * @param event 要投递的事件。
     */
    public override fun dispatch(event: DownloadEvent) {
        listeners.forEach { listener -> listener.onEvent(event) }
    }

    /**
     * 登记 [listener]。同一个实例只留一份。
     *
     * @param listener 之后的事件会交给它。
     */
    public override fun addListener(listener: DownloadEventListener) {
        if (!listeners.contains(listener)) listeners.add(listener)
    }

    /**
     * 去掉 [listener]。
     *
     * @param listener 登记时的同一个实例。
     */
    public override fun removeListener(listener: DownloadEventListener) {
        listeners.remove(listener)
    }
}

/**
 * 把事件排到单独的线程，调用 [dispatch] 的线程不会执行监听器。
 *
 * 用完调用 [close]，否则线程会留到进程结束。线程是守护线程。
 */
public class QueuedEventDispatcher : EventDispatcher, Closeable {
    private val listeners = CopyOnWriteArrayList<DownloadEventListener>()
    private val executor = Executors.newSingleThreadExecutor { runnable ->
        Thread(runnable, "fastdownload-events").apply { isDaemon = true }
    }

    /**
     * 把 [event] 排进事件线程。
     *
     * 排队时复制监听器名单，摘除只影响之后的事件。
     *
     * @param event 要投递的事件。
     */
    public override fun dispatch(event: DownloadEvent) {
        val snapshot = listeners.toList()
        executor.execute {
            snapshot.forEach { listener -> listener.onEvent(event) }
        }
    }

    /**
     * 登记 [listener]。
     *
     * @param listener 之后的事件会交给它。
     */
    public override fun addListener(listener: DownloadEventListener) {
        if (!listeners.contains(listener)) listeners.add(listener)
    }

    /**
     * 去掉 [listener]。
     *
     * @param listener 登记时的同一个实例。
     */
    public override fun removeListener(listener: DownloadEventListener) {
        listeners.remove(listener)
    }

    /**
     * 不再接收新的事件。已经排上的还会执行。
     */
    public override fun close() {
        executor.shutdown()
    }
}
