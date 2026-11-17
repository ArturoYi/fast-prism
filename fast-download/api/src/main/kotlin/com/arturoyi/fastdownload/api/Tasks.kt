/**
 * 任务的入队和生命周期动作。
 *
 * @file
 */
package com.arturoyi.fastdownload.api

import com.arturoyi.fastdownload.core.model.DownloadRequest
import com.arturoyi.fastdownload.engine.dispatch.CommandDispatcher

/**
 * [FastDownloader.tasks] 上的写操作。
 *
 * 除 [enqueue] 和 [enqueueAll] 会等到标识算出以外，其他方法把命令放进队列后立即返回。
 *
 * @param dispatcher 串行命令队列。
 */
public class Tasks internal constructor(
    private val dispatcher: CommandDispatcher,
) {
    /**
     * 登记一条任务。
     *
     * 标识在本地由地址、路径和命名空间算出；文件名递增时返回的是实际选中的路径。
     *
     * @param request 下载请求。
     * @return 稳定的任务标识。
     */
    public fun enqueue(request: DownloadRequest): String = dispatcher.enqueue(request)

    /**
     * 在一个存储事务里登记多条任务。
     *
     * @param requests 按这个顺序入队。
     * @return 对应的标识。
     */
    public fun enqueueAll(requests: List<DownloadRequest>): List<String> = dispatcher.enqueueAll(requests)

    /**
     * 把已登记的任务放进队列。
     *
     * @param id 任务标识。
     */
    public fun start(id: String) {
        dispatcher.start(id)
    }

    /**
     * 暂停任务并停下正在写的线程。断点保留。
     *
     * @param id 任务标识。
     */
    public fun pause(id: String) {
        dispatcher.pause(id)
    }

    /**
     * 继续已暂停或已登记的任务。
     *
     * @param id 任务标识。
     */
    public fun resume(id: String) {
        dispatcher.resume(id)
    }

    /**
     * 把失败或取消的任务重新排队，并清掉已经用掉的自动重试次数。
     *
     * @param id 任务标识。
     */
    public fun retry(id: String) {
        dispatcher.retry(id)
    }

    /**
     * 把自动重试次数清零，不改变任务状态。
     *
     * @param id 任务标识。
     */
    public fun resetAutoRetryAttempts(id: String) {
        dispatcher.resetAutoRetryAttempts(id)
    }

    /**
     * 取消任务。文件和断点都保留。
     *
     * @param id 任务标识。
     */
    public fun cancel(id: String) {
        dispatcher.cancel(id)
    }

    /**
     * 移出队列并保留目标文件。
     *
     * @param id 任务标识。
     */
    public fun remove(id: String) {
        dispatcher.remove(id)
    }

    /**
     * 删除记录、断点和目标文件。
     *
     * @param id 任务标识。
     */
    public fun delete(id: String) {
        dispatcher.delete(id)
    }

    /** 暂停所有还没结束的任务。 */
    public fun pauseAll() {
        dispatcher.pauseAll()
    }

    /** 继续所有已暂停或已登记的任务。 */
    public fun resumeAll() {
        dispatcher.resumeAll()
    }

    /** 取消所有还没结束的任务。 */
    public fun cancelAll() {
        dispatcher.cancelAll()
    }

    /** 不再启动新的下载。已经在写的任务继续。 */
    public fun freeze() {
        dispatcher.freeze()
    }

    /** 允许再次启动排队中的任务。 */
    public fun unfreeze() {
        dispatcher.unfreeze()
    }
}
