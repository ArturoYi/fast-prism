/**
 * 配置页的状态：把当前下载器的配置按用途分成几块说明。
 *
 * 同时下载、速度和网络可以在这一页改。改动只在这次打开里有效。
 * 文案按传入的 [android.content.Context] 取，好让语言切换后立刻换成对应的句子。
 *
 * @file
 */
package com.arturoyi.fastdownload.sample.futures.config

import android.app.Application
import android.content.Context
import androidx.annotation.StringRes
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import com.arturoyi.fastdownload.android.background.BackgroundMode
import com.arturoyi.fastdownload.android.db.RoomDownloadRepository
import com.arturoyi.fastdownload.api.FastDownloader
import com.arturoyi.fastdownload.core.model.NetworkType
import com.arturoyi.fastdownload.core.monitor.DownloadMonitor
import com.arturoyi.fastdownload.core.spi.BackgroundExecutor
import com.arturoyi.fastdownload.core.spi.BreakpointStrategy
import com.arturoyi.fastdownload.core.spi.FilePolicy
import com.arturoyi.fastdownload.core.spi.HttpClient
import com.arturoyi.fastdownload.core.spi.IntegrityVerifier
import com.arturoyi.fastdownload.core.spi.OutputTarget
import com.arturoyi.fastdownload.core.strategy.AdaptiveBreakpointStrategy
import com.arturoyi.fastdownload.core.strategy.SingleBlockStrategy
import com.arturoyi.fastdownload.engine.file.DefaultFilePolicy
import com.arturoyi.fastdownload.engine.monitor.LoggingDownloadMonitor
import com.arturoyi.fastdownload.engine.monitor.MemoryDownloadMonitor
import com.arturoyi.fastdownload.engine.store.BreakpointStore
import com.arturoyi.fastdownload.engine.verify.ChannelIntegrityVerifier
import com.arturoyi.fastdownload.engine.verify.DefaultIntegrityVerifier
import com.arturoyi.fastdownload.notify.background.BackgroundExecutor as NotifyBackground
import com.arturoyi.fastdownload.sample.DOWNLOAD_DIRECTORY
import com.arturoyi.fastdownload.sample.R
import com.arturoyi.fastdownload.sample.utils.formatBytes
import java.io.File

/**
 * 一个可以点的选项。
 *
 * @property id 交给 [ConfigViewModel.choose] 的标识。
 * @property label 按钮上的字。
 */
internal data class SettingChoice(
    val id: String,
    val label: String,
)

/**
 * 这一行改的是哪一项。
 */
internal enum class SettingKey {
    /** 同时下载的任务数。 */
    Concurrent,

    /** 全局速度上限。 */
    Speed,

    /** 额外的全局网络要求。 */
    Network,
}

/**
 * 一行上的修改控件。
 */
internal sealed interface SettingControl {
    /** 这一项写回下载器时用的键。 */
    val key: SettingKey

    /**
     * 三个及以内的固定状态，排成一条切换。窄屏排不下一行时，每段单独占一行。
     *
     * 没有任何一段选中时，当前值写在名字旁边；一行放不下时写在名字下面。
     *
     * @property choices 从左到右的状态。竖排时变成从上到下。
     * @property selectedId 当前选中的 [SettingChoice.id]。还没选过时为 null。
     */
    data class Segments(
        override val key: SettingKey,
        val choices: List<SettingChoice>,
        val selectedId: String?,
    ) : SettingControl

    /**
     * 动态数值的固定档位。界面给出这些档，不接受手输。
     *
     * @property choices 四个档位。
     * @property selectedId 当前档。对不上任何一档时为 null。
     */
    data class Options(
        override val key: SettingKey,
        val choices: List<SettingChoice>,
        val selectedId: String?,
    ) : SettingControl
}

/**
 * 配置里的一行：名字，以及现在的值。
 *
 * 值尽量跟在名字右边。右边不够时换到下一行，用整行宽把句子读完。
 *
 * @property label 名字。
 * @property value 现在的值。
 * @property control 可以改的时候带上控件。只读时为 null。
 */
internal data class SettingRow(
    val label: String,
    val value: String,
    val control: SettingControl? = null,
)

/**
 * 配置页上的一块说明。
 *
 * @property id 稳定标识。语言切换时标题会变，列表和弹窗仍认这个。
 * @property title 这块在说什么。
 * @property body 用一两句人话说明。卡片上不直接画出，点标题旁的标记后在弹窗里看。
 * @property rows 当前值。一行放不下时，值换到名字下面。
 * @property stack 希望整段可以选中复制的长内容，例如目录。
 */
internal data class SettingSection(
    val id: String,
    val title: String,
    val body: String,
    val rows: List<SettingRow>,
    val stack: SettingRow? = null,
)

/**
 * 配置页在界面上要画的内容。
 *
 * @property sections 从上到下的分栏。
 */
internal data class ConfigUiState(
    val sections: List<SettingSection>,
)

/**
 * 读出当前下载器的配置，并写成几块说明。
 *
 * 同时下载、速度和网络改完会立刻交给下载器。其余项仍是启动时定下的。
 *
 * @param application 用来拼内部目录。
 */
internal class ConfigViewModel(
    application: Application,
) : AndroidViewModel(application) {
    private val downloader = FastDownloader.getDefault()
    private var concurrentLimit by mutableIntStateOf(downloader.config.concurrentLimit)
    private var speedLimitBytes by mutableStateOf(downloader.config.speedLimitBytes)
    private var networkType by mutableStateOf(downloader.config.networkType)

    /**
     * 按现在的通知权限说明，拼出配置页。
     *
     * 在组合里读取。上面三项或 [strings] 的语言变化时会刷新。
     *
     * @param strings 用来取当前语言的文案。目录仍从应用自己的存储读。
     * @param notificationLabel 通知权限现在怎么描述。
     */
    fun state(strings: Context, notificationLabel: String): ConfigUiState {
        val live = LiveSettings(
            concurrentLimit = concurrentLimit,
            speedLimitBytes = speedLimitBytes,
            networkType = networkType,
        )
        val directory = File(getApplication<Application>().filesDir, DOWNLOAD_DIRECTORY).absolutePath
        return ConfigUiState(
            sections = strings.configSections(directory, notificationLabel, live),
        )
    }

    /**
     * 把选中的档位写回下载器。
     *
     * 未知的标识会忽略。
     *
     * @param key 哪一项。
     * @param choiceId [SettingChoice.id]。
     */
    fun choose(key: SettingKey, choiceId: String) {
        when (key) {
            SettingKey.Concurrent -> applyConcurrent(choiceId)
            SettingKey.Speed -> applySpeed(choiceId)
            SettingKey.Network -> applyNetwork(choiceId)
        }
    }

    private fun applyConcurrent(choiceId: String) {
        val limit = choiceId.toIntOrNull() ?: return
        if (limit !in concurrentLimits) return
        downloader.setConcurrentLimit(limit)
        concurrentLimit = limit
    }

    private fun applySpeed(choiceId: String) {
        val option = speedOptions.find { it.id == choiceId } ?: return
        downloader.setGlobalSpeedLimit(option.bytes)
        speedLimitBytes = option.bytes
    }

    private fun applyNetwork(choiceId: String) {
        val option = networkOptions.find { it.id == choiceId } ?: return
        downloader.setGlobalNetworkType(option.type)
        networkType = option.type
    }
}

/**
 * 界面上要跟着刷新的三项。其余配置仍从下载器快照读。
 *
 * @property concurrentLimit 同时下载的任务数。
 * @property speedLimitBytes 全局速度上限。null 表示不限。
 * @property networkType 额外的全局网络要求。null 表示不额外限制。
 */
private data class LiveSettings(
    val concurrentLimit: Int,
    val speedLimitBytes: Long?,
    val networkType: NetworkType?,
)

private data class SpeedOption(
    val id: String,
    @StringRes val labelRes: Int,
    val bytes: Long?,
)

private data class NetworkOption(
    val id: String,
    @StringRes val labelRes: Int,
    val type: NetworkType,
)

/** 同时下载的四档。启动值是 3，包含在里面。 */
private val concurrentLimits: List<Int> = listOf(1, 2, 3, 4)

/** 速度的四档。不接受手输。 */
private val speedOptions: List<SpeedOption> = listOf(
    SpeedOption(id = "none", labelRes = R.string.speed_unlimited, bytes = null),
    SpeedOption(id = "128kb", labelRes = R.string.speed_128, bytes = 128L * 1024),
    SpeedOption(id = "512kb", labelRes = R.string.speed_512, bytes = 512L * 1024),
    SpeedOption(id = "2mb", labelRes = R.string.speed_2mb, bytes = 2L * 1024 * 1024),
)

/**
 * 运行中可以设上的三种网络。
 *
 * 启动时的「不额外限制」不能再设回去，所以不放进这三条里。
 */
private val networkOptions: List<NetworkOption> = listOf(
    NetworkOption(id = "all", labelRes = R.string.network_any, type = NetworkType.ALL),
    NetworkOption(id = "unmetered", labelRes = R.string.network_unmetered, type = NetworkType.UNMETERED),
    NetworkOption(id = "wifi", labelRes = R.string.network_wifi, type = NetworkType.WIFI_ONLY),
)

/** 没注册保活时使用的进程内计数。这个类不公开。 */
private const val IN_PROCESS_BACKGROUND: String = "com.arturoyi.fastdownload.api.InProcessBackground"

private fun Context.configSections(
    directory: String,
    notificationLabel: String,
    live: LiveSettings,
): List<SettingSection> {
    val config = FastDownloader.getDefault().config
    return listOf(
        SettingSection(
            id = "client",
            title = getString(R.string.section_client),
            body = getString(R.string.section_client_body),
            rows = listOf(
                SettingRow(getString(R.string.label_client), clientLabel(config.httpClient)),
            ),
        ),
        SettingSection(
            id = "queue",
            title = getString(R.string.section_queue),
            body = queueBody(live.concurrentLimit, config.autoStart),
            rows = listOf(
                SettingRow(getString(R.string.label_namespace), config.namespace),
                SettingRow(
                    label = getString(R.string.label_concurrent),
                    value = getString(R.string.task_count, live.concurrentLimit),
                    control = concurrentControl(live.concurrentLimit),
                ),
                SettingRow(getString(R.string.label_auto_start), onOff(config.autoStart)),
                SettingRow(
                    getString(R.string.label_database),
                    repositoryLabel(config.repository, config.namespace),
                ),
            ),
        ),
        SettingSection(
            id = "network",
            title = getString(R.string.section_network),
            body = getString(R.string.section_network_body),
            rows = listOf(
                SettingRow(
                    label = getString(R.string.label_speed),
                    value = speedValue(live.speedLimitBytes),
                    control = speedControl(live.speedLimitBytes),
                ),
                SettingRow(
                    label = getString(R.string.label_network),
                    value = networkLabel(live.networkType),
                    control = networkControl(live.networkType),
                ),
            ),
        ),
        SettingSection(
            id = "files",
            title = getString(R.string.section_files),
            body = getString(R.string.section_files_body),
            rows = listOf(
                SettingRow(getString(R.string.label_blocks), blockLabel(config.breakpointStrategy)),
                SettingRow(getString(R.string.label_temp), fileLabel(config.filePolicy)),
                SettingRow(getString(R.string.label_check), checkLabel(config.integrityVerifier)),
                SettingRow(getString(R.string.label_output), outputLabel(config.outputTarget)),
            ),
            stack = SettingRow(getString(R.string.label_directory), directory),
        ),
        SettingSection(
            id = "background",
            title = getString(R.string.section_background),
            body = keepBody(config.backgroundMode),
            rows = listOf(
                SettingRow(getString(R.string.label_mode), backgroundLabel(config.backgroundMode)),
                SettingRow(
                    getString(R.string.label_keepalive),
                    keepAliveLabel(config.backgroundExecutor, config.backgroundMode),
                ),
                SettingRow(getString(R.string.label_notification), notificationLabel),
                SettingRow(getString(R.string.label_monitor), monitorLabel(config.monitor)),
            ),
        ),
    )
}

private fun Context.concurrentControl(limit: Int): SettingControl.Options {
    return SettingControl.Options(
        key = SettingKey.Concurrent,
        choices = concurrentLimits.map { value ->
            SettingChoice(value.toString(), getString(R.string.task_count, value))
        },
        selectedId = concurrentLimits.find { it == limit }?.toString(),
    )
}

private fun Context.speedControl(bytes: Long?): SettingControl.Options {
    return SettingControl.Options(
        key = SettingKey.Speed,
        choices = speedOptions.map { SettingChoice(it.id, getString(it.labelRes)) },
        selectedId = speedOptions.find { it.bytes == bytes }?.id,
    )
}

private fun Context.networkControl(type: NetworkType?): SettingControl.Segments {
    return SettingControl.Segments(
        key = SettingKey.Network,
        choices = networkOptions.map { SettingChoice(it.id, getString(it.labelRes)) },
        selectedId = networkOptions.find { it.type == type }?.id,
    )
}

private fun Context.clientLabel(client: HttpClient?): String {
    val name = client?.javaClass?.simpleName ?: return getString(R.string.client_platform)
    return when (name) {
        "OkHttpHttpClient" -> getString(R.string.client_okhttp)
        else -> getString(R.string.value_custom)
    }
}

private fun Context.queueBody(limit: Int, autoStart: Boolean): String {
    val next = getString(
        if (autoStart) R.string.queue_auto_continue else R.string.queue_manual_continue,
    )
    return getString(R.string.section_queue_body, limit, next)
}

private fun Context.speedValue(bytes: Long?): String {
    speedOptions.find { it.bytes == bytes }?.let { return getString(it.labelRes) }
    val amount = formatBytes(bytes ?: return getString(R.string.speed_unlimited))
    return getString(R.string.speed_value, amount)
}

private fun Context.networkLabel(type: NetworkType?): String {
    if (type == null) return getString(R.string.network_unrestricted)
    return networkOptions.find { it.type == type }?.let { getString(it.labelRes) }
        ?: getString(R.string.value_custom)
}

private fun Context.repositoryLabel(store: BreakpointStore?, namespace: String): String {
    return if (store == null || store is RoomDownloadRepository) {
        FastDownloader.databaseName(namespace)
    } else {
        getString(R.string.value_custom)
    }
}

private fun Context.blockLabel(strategy: BreakpointStrategy?): String = when (strategy) {
    null, AdaptiveBreakpointStrategy -> getString(R.string.blocks_adaptive)
    SingleBlockStrategy -> getString(R.string.blocks_single)
    else -> getString(R.string.value_custom)
}

private fun Context.fileLabel(policy: FilePolicy?): String {
    return if (policy == null || policy is DefaultFilePolicy) {
        getString(R.string.temp_rename)
    } else {
        getString(R.string.value_custom)
    }
}

private fun Context.checkLabel(verifier: IntegrityVerifier?): String = when (verifier) {
    null, DefaultIntegrityVerifier, ChannelIntegrityVerifier -> getString(R.string.check_hash)
    else -> getString(R.string.value_custom)
}

private fun Context.outputLabel(target: OutputTarget?): String {
    return if (target == null) getString(R.string.output_internal) else getString(R.string.value_custom)
}

private fun Context.monitorLabel(monitor: DownloadMonitor?): String = when (monitor) {
    null, is LoggingDownloadMonitor -> getString(R.string.monitor_log)
    is MemoryDownloadMonitor -> getString(R.string.monitor_memory)
    else -> getString(R.string.value_custom)
}

/**
 * 这次真正接上的保活。
 *
 * 前台保活认通知模块里的实现。没注册时下载器使用进程内计数，那个类不公开，只能按名字认出。
 */
private fun Context.keepAliveLabel(executor: BackgroundExecutor?, mode: BackgroundMode): String {
    if (executor is NotifyBackground) {
        return when (mode) {
            BackgroundMode.FOREGROUND_SERVICE -> getString(R.string.keepalive_foreground)
            BackgroundMode.WORK_MANAGER -> getString(R.string.background_work)
            BackgroundMode.IN_PROCESS -> getString(R.string.value_custom)
        }
    }
    if (executor == null || executor.javaClass.name == IN_PROCESS_BACKGROUND) {
        return getString(R.string.keepalive_in_process)
    }
    return getString(R.string.value_custom)
}

private fun Context.backgroundLabel(mode: BackgroundMode): String = when (mode) {
    BackgroundMode.FOREGROUND_SERVICE -> getString(R.string.background_foreground)
    BackgroundMode.WORK_MANAGER -> getString(R.string.background_work)
    BackgroundMode.IN_PROCESS -> getString(R.string.background_in_process)
}

private fun Context.keepBody(mode: BackgroundMode): String = when (mode) {
    BackgroundMode.FOREGROUND_SERVICE -> getString(R.string.keep_body_foreground)
    BackgroundMode.WORK_MANAGER -> getString(R.string.keep_body_work)
    BackgroundMode.IN_PROCESS -> getString(R.string.keep_body_in_process)
}

private fun Context.onOff(enabled: Boolean): String {
    return getString(if (enabled) R.string.value_on else R.string.value_off)
}
