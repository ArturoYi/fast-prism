/**
 * 下载器门面。一个命名空间对应一个实例。
 *
 * 先 [init]，再用 [get] 或 [getDefault] 取回。任务、分组、查询和运行时配置分开放。
 *
 * @file
 */
package com.arturoyi.fastdowload.api

import android.content.Context
import android.os.Looper
import android.util.Log
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import com.arturoyi.fastdowload.android.background.BackgroundExecutor as AndroidBackground
import com.arturoyi.fastdowload.android.background.BackgroundMode
import com.arturoyi.fastdowload.android.db.RoomDownloadRepository
import com.arturoyi.fastdowload.android.net.NetworkMonitor
import com.arturoyi.fastdowload.core.model.Defaults
import com.arturoyi.fastdowload.core.model.NetworkType
import com.arturoyi.fastdowload.core.monitor.DownloadMonitor
import com.arturoyi.fastdowload.core.spi.BackgroundExecutor
import com.arturoyi.fastdowload.core.spi.BreakpointStrategy
import com.arturoyi.fastdowload.core.spi.FilePolicy
import com.arturoyi.fastdowload.core.spi.HttpClient
import com.arturoyi.fastdowload.core.spi.IntegrityVerifier
import com.arturoyi.fastdowload.core.spi.OutputTarget
import com.arturoyi.fastdowload.core.state.Status
import com.arturoyi.fastdowload.engine.dispatch.CommandDispatcher
import com.arturoyi.fastdowload.engine.dispatch.EngineDownloadRunner
import com.arturoyi.fastdowload.engine.file.DefaultFilePolicy
import com.arturoyi.fastdowload.engine.http.UrlConnectionHttpClient
import com.arturoyi.fastdowload.engine.limit.GlobalRateLimiter
import com.arturoyi.fastdowload.engine.monitor.LoggingDownloadMonitor
import com.arturoyi.fastdowload.engine.store.BreakpointStore
import com.arturoyi.fastdowload.engine.verify.DefaultIntegrityVerifier
import com.arturoyi.fastdowload.core.strategy.AdaptiveBreakpointStrategy
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import java.io.Closeable
import java.io.File
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.atomic.AtomicReference

/**
 * 对外的下载入口。
 *
 * 回调都在主线程。`enqueue` 在命令线程算出标识后返回，不把查库留在界面线程。
 */
public class Downloader private constructor(
    private val appContext: Context,
    private val namespace: String,
    private val repository: BreakpointStore,
    private val httpClient: HttpClient,
    private val outputTarget: OutputTarget?,
    private val dispatcher: CommandDispatcher,
    private val events: MainThreadEventDispatcher,
    private val scope: CoroutineScope,
    private val rates: GlobalRateLimiter,
    private val background: BackgroundExecutor,
    private val networkMonitor: NetworkMonitor?,
    private var speedLimitBytes: Long?,
    private var loggingEnabled: Boolean,
    private val backgroundMode: BackgroundMode,
    private val autoStart: Boolean,
    private val breakpointStrategy: BreakpointStrategy?,
    private val filePolicy: FilePolicy?,
    private val integrityVerifier: IntegrityVerifier?,
    private val monitor: DownloadMonitor?,
    concurrentLimit: Int,
    networkType: NetworkType?,
) : Closeable {
    private val limit = AtomicLong(concurrentLimit.toLong())
    private val network = AtomicReferenceBox(networkType)

    /** 入队、暂停、继续、取消、移除和删除。 */
    public val tasks: Tasks = Tasks(dispatcher)

    /** 按分组操作。 */
    public val groups: Groups = Groups(dispatcher, repository)

    /** 查询和等待。 */
    public val query: Query = Query(repository, httpClient, scope, events)

    /**
     * 当前生效的配置快照。
     *
     * 运行时改过并发、网络或速度上限后，这里会反映新值。
     */
    public val config: DownloaderConfig
        get() = DownloaderConfig(
            context = appContext,
            namespace = namespace,
            concurrentLimit = limit.get().toInt(),
            httpClient = httpClient,
            outputTarget = outputTarget,
            repository = repository,
            networkType = network.value,
            autoStart = autoStart,
            speedLimitBytes = speedLimitBytes,
            loggingEnabled = loggingEnabled,
            backgroundMode = backgroundMode,
            breakpointStrategy = breakpointStrategy,
            filePolicy = filePolicy,
            integrityVerifier = integrityVerifier,
            monitor = monitor,
            backgroundExecutor = background,
        )

    /**
     * 登记监听器。
     *
     * [owner] 销毁时用同一个实例摘掉，避免泄漏。
     *
     * @param listener 要接收主线程回调的对象。
     * @param owner 绑定的生命周期。null 时一直留到 [removeListener] 或 [close]。
     */
    @JvmOverloads
    public fun addListener(listener: DownloadListener, owner: LifecycleOwner? = null) {
        events.add(listener)
        if (owner == null) return
        owner.lifecycle.addObserver(
            object : DefaultLifecycleObserver {
                override fun onDestroy(owner: LifecycleOwner) {
                    events.remove(listener)
                    owner.lifecycle.removeObserver(this)
                }
            },
        )
    }

    /**
     * 去掉 [addListener] 登记的监听器。
     *
     * @param listener 当时传入的同一个实例。
     */
    public fun removeListener(listener: DownloadListener) {
        events.remove(listener)
    }

    /**
     * 在主线程接收整份任务列表。
     *
     * @param owner 销毁时摘掉。null 时一直保留。
     * @param listener 列表变化时调用。
     */
    @JvmOverloads
    public fun addSnapshotListener(owner: LifecycleOwner? = null, listener: SnapshotListener) {
        events.addSnapshot(listener)
        if (owner == null) return
        owner.lifecycle.addObserver(
            object : DefaultLifecycleObserver {
                override fun onDestroy(owner: LifecycleOwner) {
                    events.removeSnapshot(listener)
                    owner.lifecycle.removeObserver(this)
                }
            },
        )
    }

    /**
     * 去掉任务列表监听。
     *
     * @param listener 当时传入的同一个实例。
     */
    public fun removeSnapshotListener(listener: SnapshotListener) {
        events.removeSnapshot(listener)
    }

    /**
     * 给所有任务再加一条网络要求，并重新看一遍队列。
     *
     * @param type 全局网络要求。
     */
    public fun setGlobalNetworkType(type: NetworkType) {
        network.value = type
        dispatcher.setRequiredNetwork(type)
        dispatcher.wake()
    }

    /**
     * 修改同时下载的任务数。
     *
     * @param limit 至少 1。
     */
    public fun setConcurrentLimit(limit: Int) {
        this.limit.set(limit.toLong())
        dispatcher.setConcurrentLimit(limit)
    }

    /**
     * 修改全局速度上限。已经在读的任务会在下一次领到数据时生效。
     *
     * 蜂窝网络上可以把上限调低，不计费网络上传入 null 恢复全速。
     *
     * @param bytesPerSecond 每秒字节数。null 表示不限。
     */
    public fun setGlobalSpeedLimit(bytesPerSecond: Long?) {
        if (bytesPerSecond != null) require(bytesPerSecond > 0L) { "速度必须大于 0" }
        speedLimitBytes = bytesPerSecond
        rates.setLimit(bytesPerSecond)
    }

    /**
     * 打开或关掉门面上的说明日志。
     *
     * @param enabled false 时不再写缺省客户端以外的说明。
     */
    public fun enableLogging(enabled: Boolean) {
        loggingEnabled = enabled
    }

    /**
     * 停掉调度和回调，并允许用同一个命名空间重新 [init]。
     */
    public override fun close() {
        networkMonitor?.close()
        background.close()
        scope.cancel()
        dispatcher.close()
        httpClient.close()
        instances.remove(namespace, this)
    }

    /**
     * 导出队列、重试分布和错误分布。
     *
     * 不含完整地址。后台模式不会在进程被杀掉后自动续跑。
     *
     * @return 多行诊断文本。
     */
    public fun dumpState(): String = dispatcher.dumpState()

    public companion object {
        private const val TAG: String = "FastDowload"
        private val instances = ConcurrentHashMap<String, Downloader>()

        /**
         * 用默认配置初始化名为 `default` 的下载器。
         *
         * 没有注册 HTTP 客户端时使用 `HttpURLConnection`，并写一条警告。
         *
         * @param context 任意上下文。
         */
        @JvmStatic
        public fun init(context: Context) {
            init(DownloaderConfig.Builder(context).build())
        }

        /**
         * 用一段 Kotlin 配置初始化。
         *
         * @param context 任意上下文。
         * @param block 填充 [DownloaderConfig.Builder]。
         */
        @JvmStatic
        public fun init(context: Context, block: DownloaderConfig.Builder.() -> Unit) {
            init(DownloaderConfig.Builder(context).apply(block).build())
        }

        /**
         * 用 Java Builder 得到的配置初始化。
         *
         * 同一个命名空间再次调用会关掉上一份实例。可以在主线程调用。
         *
         * @param config 启动配置。
         */
        @JvmStatic
        public fun init(config: DownloaderConfig) {
            val created = create(config)
            val previous = instances.put(config.namespace, created)
            previous?.close()
        }

        /**
         * 取默认命名空间的下载器。
         *
         * @return 已经 [init] 的实例。
         * @throws IllegalStateException 还没有初始化。
         */
        @JvmStatic
        public fun getDefault(): Downloader = get(Defaults.GROUP)

        /**
         * 按命名空间取下载器。
         *
         * @param namespace [DownloaderConfig.Builder.namespace] 用过的名字。
         * @return 该名字对应的实例。
         * @throws IllegalStateException 这个名字还没有初始化。
         */
        @JvmStatic
        public fun get(namespace: String): Downloader {
            return instances[namespace] ?: throw IllegalStateException("先调用 Downloader.init")
        }

        /**
         * 创建一个不放进全局表的下载器。
         *
         * 测试或需要自己保管生命周期时使用。用完要 [close]。
         * 可以在主线程调用。启动清洗改到后台线程，这个方法会等到清洗结束才返回。
         *
         * @param config 启动配置。
         * @return 新的下载器。
         */
        @JvmStatic
        public fun create(config: DownloaderConfig): Downloader {
            val repository = config.repository ?: RoomDownloadRepository(
                config.context,
                databaseName(config.namespace),
            )
            sanitize(repository, config.context.filesDir)
            val client = config.httpClient ?: UrlConnectionHttpClient().also {
                Log.w(TAG, "未注册 HttpClient，使用 HttpURLConnection")
            }
            val events = MainThreadEventDispatcher()
            val marks = ConcurrentHashMap<String, Pair<Long, Long>>()
            val rates = GlobalRateLimiter(config.speedLimitBytes)
            val monitor = config.monitor ?: LoggingDownloadMonitor(debug = false)
            repository.setFlushObserver { monitor.onRepositoryWrite() }
            val ownedNetwork = if (config.networkGate == null) NetworkMonitor(config.context) else null
            val dispatcherBox = AtomicReference<CommandDispatcher>()
            val background = config.backgroundExecutor ?: AndroidBackground(
                context = config.context,
                mode = config.backgroundMode,
                onPause = { id -> dispatcherBox.get()?.pause(id) },
            )
            val dispatcher = CommandDispatcher(
                repository = repository,
                runner = EngineDownloadRunner(
                    storageRoot = config.context.filesDir,
                    store = repository,
                    client = client,
                    onProgress = { id, downloaded, total ->
                        val now = System.currentTimeMillis()
                        val previous = marks.put(id, now to downloaded)
                        val speed = if (previous == null || now <= previous.first) {
                            0L
                        } else {
                            ((downloaded - previous.second) * 1_000L / (now - previous.first)).coerceAtLeast(0L)
                        }
                        val eta = if (speed > 0L && total > downloaded) {
                            (total - downloaded) * 1_000L / speed
                        } else {
                            -1L
                        }
                        events.progress(id, downloaded, total, speed, eta)
                    },
                    onResumeFailed = { id, cause -> events.resumeFailed(id, cause) },
                    rates = rates,
                    monitor = monitor,
                    strategy = config.breakpointStrategy ?: AdaptiveBreakpointStrategy,
                    files = config.filePolicy ?: DefaultFilePolicy(),
                    verifier = config.integrityVerifier ?: DefaultIntegrityVerifier,
                ),
                network = config.networkGate ?: checkNotNull(ownedNetwork),
                concurrentLimit = config.concurrentLimit,
                namespace = config.namespace,
                storageRoot = config.context.filesDir,
                startFrozen = !config.autoStart,
                monitor = monitor,
            )
            dispatcherBox.set(dispatcher)
            ownedNetwork?.listen { dispatcher.wake() }
            if (config.networkType != null) dispatcher.setRequiredNetwork(config.networkType)
            dispatcher.addListener { records ->
                val blocks = records.filter { it.status == Status.DOWNLOADING }
                    .associate { record -> record.id to repository.blocksOf(record.id) }
                events.publish(records, blocks)
                val active = records.count { it.status == Status.QUEUED || it.status == Status.DOWNLOADING }
                background.onActiveCount(active)
            }
            dispatcher.wake()
            return Downloader(
                appContext = config.context.applicationContext,
                namespace = config.namespace,
                repository = repository,
                httpClient = client,
                outputTarget = config.outputTarget,
                dispatcher = dispatcher,
                events = events,
                scope = CoroutineScope(SupervisorJob() + Dispatchers.IO),
                rates = rates,
                background = background,
                networkMonitor = ownedNetwork,
                speedLimitBytes = config.speedLimitBytes,
                loggingEnabled = config.loggingEnabled,
                backgroundMode = config.backgroundMode,
                autoStart = config.autoStart,
                breakpointStrategy = config.breakpointStrategy,
                filePolicy = config.filePolicy,
                integrityVerifier = config.integrityVerifier,
                monitor = config.monitor,
                concurrentLimit = config.concurrentLimit,
                networkType = config.networkType,
            )
        }

        /**
         * 这个命名空间使用的数据库文件名。
         *
         * @param namespace 实例名字。
         * @return 以 `.db` 结尾的文件名。
         */
        @JvmStatic
        public fun databaseName(namespace: String): String = "$namespace.db"

        /**
         * 修正残留任务。
         *
         * 仓库方法会阻塞，Room 也不允许在主线程开事务。主线程调用时改到后台，并等到写完再返回，
         * 这样 [create] 返回后队列已经是清洗过的。
         */
        private fun sanitize(repository: BreakpointStore, storageRoot: File) {
            val main = Looper.getMainLooper()?.thread
            if (main == null || Thread.currentThread() != main) {
                repository.sanitizeOnStartup(storageRoot)
                return
            }
            val failure = AtomicReference<Throwable>()
            val worker = Thread(
                {
                    try {
                        repository.sanitizeOnStartup(storageRoot)
                    } catch (error: Throwable) {
                        failure.set(error)
                    }
                },
                "fastdowload-sanitize",
            ).apply { isDaemon = true }
            worker.start()
            try {
                worker.join()
            } catch (error: InterruptedException) {
                worker.interrupt()
                Thread.currentThread().interrupt()
                throw IllegalStateException("启动清洗被中断", error)
            }
            failure.get()?.let { throw it }
        }
    }
}

/**
 * 可替换的网络要求。配置快照读取它。
 */
private class AtomicReferenceBox<T>(
    @Volatile var value: T,
)
