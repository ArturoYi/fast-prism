/**
 * Kotlin 请求 DSL 和等价的 Java Builder。
 *
 * @file
 */
package com.arturoyi.fastdowload.api

import com.arturoyi.fastdowload.core.model.BytesPerSecond
import com.arturoyi.fastdowload.core.model.Defaults
import com.arturoyi.fastdowload.core.model.DownloadRequest
import com.arturoyi.fastdowload.core.model.EnqueueAction
import com.arturoyi.fastdowload.core.model.IntegritySpec
import com.arturoyi.fastdowload.core.model.NetworkType
import com.arturoyi.fastdowload.core.model.Priority
import com.arturoyi.fastdowload.core.model.RetryPolicy
import kotlin.time.Duration

/**
 * 标记 [DownloadRequestBuilder] 的嵌套配置，避免作用域串味。
 */
@DslMarker
public annotation class DownloadRequestDsl

/**
 * 用一段配置创建下载请求。
 *
 * 路径是应用内部目录下的相对路径，不是外部存储绝对路径。
 *
 * @param url 资源绝对地址，只允许 `http` 和 `https`。
 * @param block 填充优先级、路径和请求头。
 * @return 可以交给 [Tasks.enqueue] 的请求。
 */
public fun downloadRequest(url: String, block: DownloadRequestBuilder.() -> Unit = {}): DownloadRequest {
    return DownloadRequestBuilder(url).apply(block).build()
}

/**
 * 组装 [DownloadRequest]。Kotlin 用接收者调用，Java 用链式方法。
 *
 * @param url 资源绝对地址。
 */
@DownloadRequestDsl
public class DownloadRequestBuilder(
    private val url: String,
) {
    private var relativePath: String = "download.bin"
    private val headers = linkedMapOf<String, String>()
    private val tags = linkedMapOf<String, String>()
    private val extras = linkedMapOf<String, String>()
    private var priority = Defaults.PRIORITY
    private var groupId = Defaults.GROUP
    private var networkType = Defaults.NETWORK_TYPE
    private var enqueueAction = Defaults.ENQUEUE_ACTION
    private var connectionCount: Int? = null
    private var bufferSize = Defaults.BUFFER_SIZE
    private var progressInterval: Duration? = null
    private var speedLimit: BytesPerSecond? = null
    private var integrity: IntegritySpec? = null
    private var retryPolicy = RetryPolicy()
    private var downloadOnEnqueue = Defaults.DOWNLOAD_ON_ENQUEUE

    /**
     * 设置内部相对目录和文件名。
     *
     * `saveTo("downloads", "a.bin")` 得到 `downloads/a.bin`。已有同名文件时是否改名由 [enqueueAction] 决定。
     *
     * @param directory 相对应用内部根目录的目录。可以是空字符串，表示就在根目录。
     * @param fileName 文件名，不能含路径分隔符。
     * @return 这个建造器。
     */
    public fun saveTo(directory: String, fileName: String): DownloadRequestBuilder {
        require('/' !in fileName && '\\' !in fileName) { "fileName 不能含路径" }
        val dir = directory.trim().trim('/')
        relativePath = if (dir.isEmpty()) fileName else "$dir/$fileName"
        return this
    }

    /**
     * 增加一个请求头。
     *
     * @param name 头名称。不能是 `Range`、`If-Match`、`If-Range` 或 `Host`。
     * @param value 头的值。
     * @return 这个建造器。
     */
    public fun header(name: String, value: String): DownloadRequestBuilder {
        headers[name] = value
        return this
    }

    /**
     * 设置调度优先级。
     *
     * @param value 优先级。
     * @return 这个建造器。
     */
    public fun priority(value: Priority): DownloadRequestBuilder {
        priority = value
        return this
    }

    /**
     * 设置分组名。
     *
     * @param value 分组。不能为空白。
     * @return 这个建造器。
     */
    public fun group(value: String): DownloadRequestBuilder {
        groupId = value
        return this
    }

    /**
     * 增加一个不参与请求的标签。
     *
     * @param key 标签名。
     * @param value 标签值。
     * @return 这个建造器。
     */
    public fun tag(key: String, value: String): DownloadRequestBuilder {
        tags[key] = value
        return this
    }

    /**
     * 设置允许下载的网络。
     *
     * @param value 网络范围。
     * @return 这个建造器。
     */
    public fun networkType(value: NetworkType): DownloadRequestBuilder {
        networkType = value
        return this
    }

    /**
     * 设置队列里已有相同任务时怎么处理。
     *
     * @param value 入队动作。
     * @return 这个建造器。
     */
    public fun enqueueAction(value: EnqueueAction): DownloadRequestBuilder {
        enqueueAction = value
        return this
    }

    /**
     * 设置希望使用的连接数。
     *
     * @param value 1 到 [Defaults.MAX_BLOCKS_CAP]。
     * @return 这个建造器。
     */
    public fun connectionCount(value: Int): DownloadRequestBuilder {
        connectionCount = value
        return this
    }

    /**
     * 设置单次读取的缓冲区大小。
     *
     * @param bytes 必须大于 0。
     * @return 这个建造器。
     */
    public fun bufferSize(bytes: Int): DownloadRequestBuilder {
        bufferSize = bytes
        return this
    }

    /**
     * 设置这条任务自己的进度间隔。
     *
     * @param value 最小间隔。
     * @return 这个建造器。
     */
    public fun progressInterval(value: Duration): DownloadRequestBuilder {
        progressInterval = value
        return this
    }

    /**
     * 记下速度上限。
     *
     * @param bytesPerSecond 每秒最多写入的字节数，必须大于 0。
     * @return 这个建造器。
     */
    public fun speedLimit(bytesPerSecond: Long): DownloadRequestBuilder {
        speedLimit = BytesPerSecond(bytesPerSecond)
        return this
    }

    /**
     * 记下完成后的校验条件。
     *
     * @param value 摘要或响应头校验。
     * @return 这个建造器。
     */
    public fun integrity(value: IntegritySpec): DownloadRequestBuilder {
        integrity = value
        return this
    }

    /**
     * 设置失败后的重试策略。
     *
     * @param value 次数、退避和抖动。
     * @return 这个建造器。
     */
    public fun retryPolicy(value: RetryPolicy): DownloadRequestBuilder {
        retryPolicy = value
        return this
    }

    /**
     * 入队后是否立刻开始。
     *
     * @param enabled false 时任务停在已登记。
     * @return 这个建造器。
     */
    public fun downloadOnEnqueue(enabled: Boolean): DownloadRequestBuilder {
        downloadOnEnqueue = enabled
        return this
    }

    /**
     * 增加一条不参与协议的附加数据。
     *
     * @param key 键。
     * @param value 值。
     * @return 这个建造器。
     */
    public fun extra(key: String, value: String): DownloadRequestBuilder {
        extras[key] = value
        return this
    }

    /**
     * 生成请求。
     *
     * @return 校验过的下载请求。
     * @throws IllegalArgumentException 地址、路径或请求头不合法。
     */
    public fun build(): DownloadRequest {
        return DownloadRequest(
            url = url,
            destination = com.arturoyi.fastdowload.core.model.Destination(relativePath),
            headers = headers.toMap(),
            priority = priority,
            groupId = groupId,
            tags = tags.toMap(),
            networkType = networkType,
            enqueueAction = enqueueAction,
            connectionCount = connectionCount,
            bufferSize = bufferSize,
            progressInterval = progressInterval,
            speedLimit = speedLimit,
            integrity = integrity,
            retryPolicy = retryPolicy,
            downloadOnEnqueue = downloadOnEnqueue,
            extras = com.arturoyi.fastdowload.core.model.BundleLike(extras.toMap()),
        )
    }
}
