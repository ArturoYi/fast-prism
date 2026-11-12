/**
 * 单连接、单块下载。
 *
 * 没有暂停、取消或停止。一次调用会读到流结束或抛错。
 *
 * @file
 */
package com.arturoyi.fastdowload.engine

import com.arturoyi.fastdowload.core.model.Defaults
import com.arturoyi.fastdowload.core.model.DownloadRequest
import com.arturoyi.fastdowload.core.model.HttpRequest
import com.arturoyi.fastdowload.core.model.HttpResponse
import com.arturoyi.fastdowload.engine.http.OkHttpDownloadClient
import com.arturoyi.fastdowload.engine.io.FileSink
import java.io.File

/**
 * 单连接、单块、从头写入的下载。
 *
 * 调用线程会一直阻塞到下载结束。文件只允许落在 [storageRoot] 里面；样本里这个根目录是应用内部的 `filesDir`。
 * 非 2xx 时不创建目标文件。进度回调与 [execute] 在同一线程。
 *
 * @param request 资源地址和内部相对路径。连接数、重试和校验字段在这一层不生效。
 * @param storageRoot 允许写入的根目录。目标路径必须解析到它下面。
 * @param client 执行 GET 的 OkHttp 客户端。
 * @param onProgress 每写入一块缓冲区后的进度。`totalBytes` 未知时为 `-1`。
 */
public class NaiveDownloadCall(
    private val request: DownloadRequest,
    private val storageRoot: File,
    private val client: OkHttpDownloadClient = OkHttpDownloadClient(),
    private val onProgress: ProgressCallback = ProgressCallback { _, _ -> },
) {
    /**
     * 下载到 [DownloadRequest.destination]，并返回实际写入的字节数。
     *
     * 成功时先把目标文件截成空文件，再按读取顺序写入，最后 `flush` 并 `sync`。
     *
     * @return 写入目标文件的字节数。空响应为 `0`。
     * @throws IllegalStateException HTTP 状态不是 2xx、父目录创建失败，或响应声明了长度但没有正文。
     * @throws IllegalArgumentException 目标路径越出 [storageRoot]。
     * @throws java.io.IOException 网络或写盘失败。失败前可能已经创建了空文件。
     */
    public fun execute(): Long {
        val target = request.destination.resolveInside(storageRoot)
        return client.execute(toHttpRequest(request)).use { response ->
            check(response.code in 200..299) { "HTTP ${response.code}" }
            prepareParent(target)
            write(target, response)
        }
    }

    /**
     * 把响应正文写入 [target]。
     *
     * 写入前把文件长度设为 0。没有正文时只接受长度未知或 0 的响应。
     *
     * @param target 已经解析到存储根目录内的文件。父目录必须已经存在。
     * @param response 状态码已确认为 2xx 的响应。
     * @return 从正文读出并写入的字节数。
     * @throws IllegalStateException 响应没有正文，但 [HttpResponse.contentLength] 大于 0。
     * @throws java.io.IOException 读取正文或同步文件失败。
     */
    private fun write(target: File, response: HttpResponse): Long {
        val body = response.body
        return FileSink(target).use { sink ->
            sink.setLength(0L)
            val total = response.contentLength
            if (body == null) {
                check(total <= 0L) { "响应没有正文" }
                sink.flushAndSync()
                onProgress.onProgress(0L, total)
                return@use 0L
            }
            val buffer = ByteArray(request.bufferSize)
            var written = 0L
            while (true) {
                val read = body.read(buffer)
                if (read < 0) break
                sink.write(buffer, 0, read)
                written += read
                onProgress.onProgress(written, total)
            }
            sink.flushAndSync()
            written
        }
    }

    /**
     * 把下载请求转成不带区间的 GET。
     *
     * 超时使用 [Defaults.CONNECT_TIMEOUT_MS] 和 [Defaults.READ_TIMEOUT_MS]。
     *
     * @param request 调用方给出的下载请求。
     * @return 交给 [OkHttpDownloadClient] 的 HTTP 请求。
     */
    private fun toHttpRequest(request: DownloadRequest): HttpRequest {
        return HttpRequest(
            url = request.url,
            method = "GET",
            headers = request.headers,
            range = null,
            connectTimeoutMs = Defaults.CONNECT_TIMEOUT_MS,
            readTimeoutMs = Defaults.READ_TIMEOUT_MS,
        )
    }
}

/**
 * 确保 [target] 的父目录存在。
 *
 * 父目录为 null 时直接返回。目录已存在时不重复创建。
 *
 * @param target 即将写入的文件，本身可以尚不存在。
 * @throws IllegalStateException 父目录不存在且 `mkdirs` 失败。
 */
private fun prepareParent(target: File) {
    val parent = target.parentFile ?: return
    if (!parent.isDirectory && !parent.mkdirs()) {
        error("无法创建内部目录")
    }
}

/**
 * 下载过程中的字节进度。
 *
 * 回调发生在调用 [NaiveDownloadCall.execute] 的线程上，不要在里面做耗时工作。
 */
public fun interface ProgressCallback {
    /**
     * 报告当前已写入的字节数。
     *
     * @param downloadedBytes 已经写入目标文件的字节数，从 0 开始，只增不减。
     * @param totalBytes 响应声明的 `Content-Length`。服务器没有给出时为 `-1`。
     */
    public fun onProgress(downloadedBytes: Long, totalBytes: Long)
}
