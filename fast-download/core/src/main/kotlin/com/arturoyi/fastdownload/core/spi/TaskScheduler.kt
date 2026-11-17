/**
 * 任务入队、取消和并发上限。
 *
 * @file
 */
package com.arturoyi.fastdownload.core.spi

import com.arturoyi.fastdownload.core.model.DownloadRequest

/**
 * 决定任务何时开始。
 *
 * 真正改状态的实现必须把写入留在自己的串行线程上。这里的方法可以被任意线程调用。
 */
public interface TaskScheduler {
    /**
     * 登记 [request] 并返回任务标识。
     *
     * 相同地址、相对路径和命名空间再次入队时，返回已有标识。
     *
     * @param request 下载请求。
     * @return 任务标识。
     */
    public fun enqueue(request: DownloadRequest): String

    /**
     * 取消 [publicId]。
     *
     * 没有这条任务时什么也不做，不抛异常。
     *
     * @param publicId 任务标识。
     */
    public fun cancel(publicId: String)

    /**
     * 修改同时下载的任务数。
     *
     * 小于 1 的值按 1 处理，大于实现上限的值按该上限处理。不要抛异常。
     *
     * @param limit 希望的并发数。
     */
    public fun setConcurrentLimit(limit: Int)
}
