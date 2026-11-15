/**
 * 创建 [Downloader] 时的配置。
 *
 * Java 使用 [Builder]。Kotlin 也可以对 [Builder] 调用 `apply`。
 *
 * @file
 */
package com.arturoyi.fastdowload.api

import android.content.Context
import com.arturoyi.fastdowload.android.background.BackgroundMode
import com.arturoyi.fastdowload.core.model.Defaults
import com.arturoyi.fastdowload.core.model.NetworkType
import com.arturoyi.fastdowload.core.monitor.DownloadMonitor
import com.arturoyi.fastdowload.core.spi.BackgroundExecutor
import com.arturoyi.fastdowload.core.spi.BreakpointStrategy
import com.arturoyi.fastdowload.core.spi.FilePolicy
import com.arturoyi.fastdowload.core.spi.HttpClient
import com.arturoyi.fastdowload.core.spi.IntegrityVerifier
import com.arturoyi.fastdowload.core.spi.OutputTarget
import com.arturoyi.fastdowload.engine.schedule.NetworkGate
import com.arturoyi.fastdowload.engine.store.BreakpointStore

/**
 * 一份不可变的启动配置。
 *
 * @property context 用来打开数据库和读取网络。会使用它的 applicationContext。
 * @property namespace 实例隔离用的名字，也参与任务标识。
 * @property concurrentLimit 同时下载的任务数。
 * @property httpClient 注册的 HTTP 客户端。null 时使用 `HttpURLConnection` 并记一条警告。
 * @property outputTarget 调用方提供的写出目标。null 时任务仍写入应用内部文件。
 * @property repository 任务仓库。null 时使用这个命名空间自己的 Room 数据库。
 * @property networkType 额外的全局网络要求。null 表示只看每条任务自己的要求。
 * @property autoStart 清洗残留任务后是否立刻开始排队中的下载。
 * @property speedLimitBytes 全局速度上限，单位是字节每秒。null 表示不限。下载读到字节后会按这个上限等待。
 * @property loggingEnabled 是否保留诊断日志。调试细节默认关闭。
 * @property backgroundMode 有任务时如何保活。默认只在当前进程内下载。三种模式都不保证进程被杀掉后自动续跑。
 * @property breakpointStrategy 分块方式。null 时按文件长度分档。
 * @property filePolicy 临时文件和预分配。null 时用内部目录里的默认策略。
 * @property integrityVerifier 完成前的摘要核对。null 时用内置的 SHA-256 / MD5 实现。
 * @property monitor 阶段耗时和错误分布。null 时写诊断日志。
 * @property backgroundExecutor 保活实现。null 时按 [backgroundMode] 创建。关闭下载器时会关闭它。
 */
public class DownloaderConfig internal constructor(
    public val context: Context,
    public val namespace: String,
    public val concurrentLimit: Int,
    public val httpClient: HttpClient?,
    public val outputTarget: OutputTarget?,
    public val repository: BreakpointStore?,
    public val networkType: NetworkType?,
    public val autoStart: Boolean,
    public val speedLimitBytes: Long?,
    public val loggingEnabled: Boolean,
    public val backgroundMode: BackgroundMode,
    public val breakpointStrategy: BreakpointStrategy? = null,
    public val filePolicy: FilePolicy? = null,
    public val integrityVerifier: IntegrityVerifier? = null,
    public val monitor: DownloadMonitor? = null,
    public val backgroundExecutor: BackgroundExecutor? = null,
    internal val networkGate: NetworkGate? = null,
) {
    /**
     * 组装 [DownloaderConfig]。
     *
     * @param context 任意上下文，会换成 applicationContext。
     */
    public class Builder(
        context: Context,
    ) {
        private val appContext = context.applicationContext
        private var namespace = Defaults.GROUP
        private var concurrentLimit = Defaults.CONCURRENT_LIMIT
        private var httpClient: HttpClient? = null
        private var outputTarget: OutputTarget? = null
        private var repository: BreakpointStore? = null
        private var networkType: NetworkType? = null
        private var autoStart = true
        private var speedLimitBytes: Long? = null
        private var loggingEnabled = true
        private var backgroundMode = BackgroundMode.IN_PROCESS
        private var breakpointStrategy: BreakpointStrategy? = null
        private var filePolicy: FilePolicy? = null
        private var integrityVerifier: IntegrityVerifier? = null
        private var monitor: DownloadMonitor? = null
        private var backgroundExecutor: BackgroundExecutor? = null
        private var networkGate: NetworkGate? = null

        /**
         * 设置命名空间。不同名字的下载器互不影响。
         *
         * @param value 不能为空白。
         * @return 这个建造器。
         */
        public fun namespace(value: String): Builder {
            namespace = value
            return this
        }

        /**
         * 设置同时下载的任务数。
         *
         * @param value 至少 1。
         * @return 这个建造器。
         */
        public fun concurrentLimit(value: Int): Builder {
            concurrentLimit = value
            return this
        }

        /**
         * 注册 HTTP 客户端。
         *
         * 不调用时，下载器使用内置的 `HttpURLConnection`。
         *
         * @param value 要使用的客户端。
         * @return 这个建造器。
         */
        public fun httpClient(value: HttpClient): Builder {
            httpClient = value
            return this
        }

        /**
         * 注册写出目标。
         *
         * @param value 文件或内容 Uri 的目标。
         * @return 这个建造器。
         */
        public fun outputTarget(value: OutputTarget): Builder {
            outputTarget = value
            return this
        }

        /**
         * 注册任务仓库。
         *
         * 不调用时使用 Room，数据库名是命名空间加 `.db`。
         *
         * @param value 内存仓库或 Room 仓库。
         * @return 这个建造器。
         */
        public fun repository(value: BreakpointStore): Builder {
            repository = value
            return this
        }

        /**
         * 设置全局网络要求。
         *
         * @param value 所有任务都要额外满足的网络。
         * @return 这个建造器。
         */
        public fun networkType(value: NetworkType): Builder {
            networkType = value
            return this
        }

        /**
         * 清洗之后是否自动开始。
         *
         * @param value false 时任务留在队列里，直到 [Tasks.unfreeze]。
         * @return 这个建造器。
         */
        public fun autoStart(value: Boolean): Builder {
            autoStart = value
            return this
        }

        /**
         * 设置全局速度上限。
         *
         * 蜂窝网络上可以调低；不计费网络上传入 null 恢复全速。已经在下载的任务会在下一次读到数据时生效。
         *
         * @param bytesPerSecond 每秒字节数。null 表示不限。
         * @return 这个建造器。
         */
        public fun globalSpeedLimit(bytesPerSecond: Long?): Builder {
            speedLimitBytes = bytesPerSecond
            return this
        }

        /**
         * 是否保留诊断日志。
         *
         * @param enabled false 时门面不再因为缺省客户端写警告以外的说明。
         * @return 这个建造器。
         */
        public fun logging(enabled: Boolean): Builder {
            loggingEnabled = enabled
            return this
        }

        /**
         * 选择有任务时如何保活。
         *
         * 默认 [BackgroundMode.IN_PROCESS]。前台服务和 WorkManager 只在进程还活着时有用，进程被杀掉后要等下次启动从断点继续。
         *
         * @param value 保活方式。
         * @return 这个建造器。
         */
        public fun backgroundMode(value: BackgroundMode): Builder {
            backgroundMode = value
            return this
        }

        /**
         * 替换分块策略。
         *
         * 不调用时按文件长度在 1 到 5 块之间选择。
         *
         * @param value 分块策略。
         * @return 这个建造器。
         */
        public fun breakpointStrategy(value: BreakpointStrategy): Builder {
            breakpointStrategy = value
            return this
        }

        /**
         * 替换临时文件策略。
         *
         * @param value 预分配和提交方式。
         * @return 这个建造器。
         */
        public fun filePolicy(value: FilePolicy): Builder {
            filePolicy = value
            return this
        }

        /**
         * 替换摘要核对。
         *
         * @param value 完成前调用的校验。
         * @return 这个建造器。
         */
        public fun integrityVerifier(value: IntegrityVerifier): Builder {
            integrityVerifier = value
            return this
        }

        /**
         * 替换指标记录。
         *
         * 不调用时使用会写诊断日志的实现。
         *
         * @param value 阶段耗时和错误分布。
         * @return 这个建造器。
         */
        public fun monitor(value: DownloadMonitor): Builder {
            monitor = value
            return this
        }

        /**
         * 替换保活实现。
         *
         * 不调用时按 [backgroundMode] 创建。传入的实例会在下载器关闭时一起关闭。
         *
         * @param value 活跃任务数变化时调用的保活。
         * @return 这个建造器。
         */
        public fun backgroundExecutor(value: BackgroundExecutor): Builder {
            backgroundExecutor = value
            return this
        }

        /**
         * 测试时替换网络判断。
         *
         * @param value 直接回答某种网络是否允许。
         * @return 这个建造器。
         */
        internal fun gate(value: NetworkGate): Builder {
            networkGate = value
            return this
        }

        /**
         * 生成配置。
         *
         * @return 不可变配置。
         */
        public fun build(): DownloaderConfig {
            require(namespace.isNotBlank()) { "namespace 不能为空" }
            require(concurrentLimit >= 1) { "concurrentLimit 必须大于 0" }
            return DownloaderConfig(
                context = appContext,
                namespace = namespace,
                concurrentLimit = concurrentLimit,
                httpClient = httpClient,
                outputTarget = outputTarget,
                repository = repository,
                networkType = networkType,
                autoStart = autoStart,
                speedLimitBytes = speedLimitBytes,
                loggingEnabled = loggingEnabled,
                backgroundMode = backgroundMode,
                breakpointStrategy = breakpointStrategy,
                filePolicy = filePolicy,
                integrityVerifier = integrityVerifier,
                monitor = monitor,
                backgroundExecutor = backgroundExecutor,
                networkGate = networkGate,
            )
        }
    }
}
