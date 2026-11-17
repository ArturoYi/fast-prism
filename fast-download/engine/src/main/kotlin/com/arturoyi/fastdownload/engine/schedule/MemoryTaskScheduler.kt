/**
 * 只在内存里排队，不自己启动下载。
 *
 * @file
 */
package com.arturoyi.fastdownload.engine.schedule

import com.arturoyi.fastdownload.core.model.Defaults
import com.arturoyi.fastdownload.core.model.DownloadRecord
import com.arturoyi.fastdownload.core.model.DownloadRequest
import com.arturoyi.fastdownload.core.model.PublicIds
import com.arturoyi.fastdownload.core.spi.DownloadRepository
import com.arturoyi.fastdownload.core.spi.TaskScheduler
import com.arturoyi.fastdownload.core.state.Status
import com.arturoyi.fastdownload.engine.store.InMemoryBreakpointStore

/**
 * 用仓库保存队列，不占用下载线程。
 *
 * 适合契约测试，也适合调用方自己决定何时调用下载。
 *
 * @param repository 任务仓库。默认是一份新的内存仓库。
 * @param namespace 参与任务标识的名字。
 */
public class MemoryTaskScheduler(
    private val repository: DownloadRepository = InMemoryBreakpointStore(),
    private val namespace: String = Defaults.GROUP,
) : TaskScheduler {
    private var limit: Int = Defaults.CONCURRENT_LIMIT

    init {
        require(namespace.isNotBlank()) { "namespace 不能为空" }
    }

    /**
     * 登记任务。已有同一标识时直接返回，不覆盖请求。
     *
     * @param request 下载请求。
     * @return 由地址、相对路径和 [namespace] 算出的标识。
     */
    public override fun enqueue(request: DownloadRequest): String {
        val id = PublicIds.generate(request.url, request.destination, namespace)
        if (repository.get(id) != null) return id
        val now = System.currentTimeMillis()
        repository.upsert(
            DownloadRecord(
                id = id,
                request = request,
                status = Status.ADDED,
                createdAt = now,
                updatedAt = now,
            ),
        )
        if (request.downloadOnEnqueue) {
            repository.transition(id, Status.QUEUED)
        }
        return id
    }

    /**
     * 把还在队列里的任务改成取消。
     *
     * 没有这条任务，或当前状态不能取消时忽略。
     *
     * @param publicId 任务标识。
     */
    public override fun cancel(publicId: String) {
        if (repository.get(publicId) == null) return
        runCatching { repository.transition(publicId, Status.CANCELLED) }
    }

    /**
     * 把并发数限制在 1 到 16。
     *
     * 这个实现不启动下载，数值只留给调用方读取 [concurrencyLimit]。
     *
     * @param limit 希望的并发数。
     */
    public override fun setConcurrentLimit(limit: Int) {
        this.limit = limit.coerceIn(1, LIMIT_CAP)
    }

    /**
     * 当前记下的并发上限。
     *
     * @return 1 到 16。
     */
    public fun concurrencyLimit(): Int = limit

    private companion object {
        private const val LIMIT_CAP: Int = 16
    }
}
