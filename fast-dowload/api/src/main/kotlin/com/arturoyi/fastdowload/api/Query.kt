/**
 * 查询任务。读取在后台执行，Java 回调回到主线程。
 *
 * @file
 */
package com.arturoyi.fastdowload.api

import com.arturoyi.fastdowload.core.model.Defaults
import com.arturoyi.fastdowload.core.model.DownloadRecord
import com.arturoyi.fastdowload.core.model.DownloadRequest
import com.arturoyi.fastdowload.core.model.HttpRequest
import com.arturoyi.fastdowload.core.spi.DownloadFilter
import com.arturoyi.fastdowload.core.spi.DownloadRepository
import com.arturoyi.fastdowload.core.spi.HttpClient
import com.arturoyi.fastdowload.core.state.Status
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.coroutines.resume

/**
 * 主线程上收到一次查询结果。
 */
public fun interface ResultCallback<T> {
    /**
     * 查询已经完成。
     *
     * @param value 结果。没有任务时可以是 null。
     */
    public fun onResult(value: T)
}

/**
 * [FastDownloader.query] 上的读操作。
 *
 * @param repository 任务仓库。
 * @param httpClient 用来做 `HEAD`。
 * @param scope 把 Java 回调切回主线程的作用域。
 * @param events 用来等待队列空闲。
 */
public class Query internal constructor(
    private val repository: DownloadRepository,
    private val httpClient: HttpClient,
    private val scope: CoroutineScope,
    private val events: MainThreadEventDispatcher,
) {
    private val active = setOf(Status.QUEUED, Status.DOWNLOADING, Status.WAITING_NETWORK)

    /**
     * 按标识读取一条任务。
     *
     * @param id 任务标识。
     * @return 没有这条任务时为 null。
     */
    public suspend fun get(id: String): DownloadRecord? = withContext(Dispatchers.IO) {
        repository.get(id)
    }

    /**
     * 在主线程收到 [get] 的结果。
     *
     * @param id 任务标识。
     * @param callback 主线程回调。
     */
    public fun get(id: String, callback: ResultCallback<DownloadRecord?>) {
        scope.launch {
            val value = get(id)
            withContext(Dispatchers.Main) { callback.onResult(value) }
        }
    }

    /**
     * 按状态列出任务。
     *
     * @param filter 状态筛选。空集合表示全部。
     * @return 按优先级和创建时间排好的快照。
     */
    public suspend fun query(filter: DownloadFilter = DownloadFilter()): List<DownloadRecord> {
        return withContext(Dispatchers.IO) { repository.query(filter) }
    }

    /**
     * 在主线程收到 [query] 的结果。
     *
     * @param filter 状态筛选。
     * @param callback 主线程回调。
     */
    public fun query(filter: DownloadFilter, callback: ResultCallback<List<DownloadRecord>>) {
        scope.launch {
            val value = query(filter)
            withContext(Dispatchers.Main) { callback.onResult(value) }
        }
    }

    /**
     * 读取这些状态中的任务。
     *
     * @param status 要保留的状态。至少给一个。
     * @return 匹配的任务。
     */
    public suspend fun getByStatus(vararg status: Status): List<DownloadRecord> {
        return query(DownloadFilter(status.toSet()))
    }

    /**
     * 按标签读取任务。
     *
     * @param key 标签名。
     * @param value 标签值。
     * @return 同时带有这一对标签的任务。
     */
    public suspend fun getByTag(key: String, value: String): List<DownloadRecord> {
        return withContext(Dispatchers.IO) {
            repository.query().filter { record -> record.request.tags[key] == value }
        }
    }

    /**
     * 用 `HEAD` 读取资源长度。
     *
     * @param request 只使用地址、请求头和超时。
     * @return 字节数。未知时为 `-1`。
     */
    public suspend fun getContentLength(request: DownloadRequest): Long = withContext(Dispatchers.IO) {
        httpClient.getContentLength(
            HttpRequest(
                url = request.url,
                method = "HEAD",
                headers = request.headers,
                connectTimeoutMs = Defaults.CONNECT_TIMEOUT_MS,
                readTimeoutMs = Defaults.READ_TIMEOUT_MS,
            ),
        )
    }

    /**
     * 是否还有排队、下载中或等待网络的任务。
     *
     * @return 至少有一条这样的任务时为 true。
     */
    public suspend fun hasActiveDownloads(): Boolean = withContext(Dispatchers.IO) {
        repository.query().any { record -> record.status in active }
    }

    /**
     * 挂起到没有排队、下载中或等待网络的任务。
     *
     * 不阻塞线程。已暂停和已登记的任务不算还在下载。
     */
    public suspend fun awaitFinish() {
        suspendCancellableCoroutine { continuation ->
            val finished = AtomicBoolean(false)
            lateinit var listener: SnapshotListener
            listener = SnapshotListener { records ->
                if (records.any { it.status in active }) return@SnapshotListener
                if (continuation.isActive && finished.compareAndSet(false, true)) {
                    events.removeSnapshot(listener)
                    continuation.resume(Unit)
                }
            }
            events.addSnapshot(listener)
            continuation.invokeOnCancellation { events.removeSnapshot(listener) }
            scope.launch {
                val busy = hasActiveDownloads()
                if (!busy && continuation.isActive && finished.compareAndSet(false, true)) {
                    events.removeSnapshot(listener)
                    continuation.resume(Unit)
                }
            }
        }
    }
}
