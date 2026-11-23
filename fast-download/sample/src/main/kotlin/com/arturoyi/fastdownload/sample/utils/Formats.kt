/**
 * 把字节、速度和状态写成界面上的短句。
 *
 * @file
 */
package com.arturoyi.fastdownload.sample.utils

import android.content.Context
import com.arturoyi.fastdownload.core.model.DownloadRecord
import com.arturoyi.fastdownload.core.state.Status
import com.arturoyi.fastdownload.sample.R
import java.util.Locale

/**
 * 文件名，不含目录。
 *
 * @return 相对路径的最后一段。
 */
internal fun DownloadRecord.displayName(): String {
    val path = request.destination.relativePath
    val slash = path.lastIndexOf('/')
    return if (slash < 0 || slash == path.lastIndex) path else path.substring(slash + 1)
}

/**
 * 从链接最后一段得到文件名。得不到可用名字时用 `download.bin`。
 *
 * @param url 用户输入的地址。
 * @return 不含路径分隔符的文件名。
 */
internal fun fileNameFromUrl(url: String): String {
    val path = url.substringBefore('#').substringBefore('?')
    val slash = path.lastIndexOf('/')
    val raw = if (slash < 0) "" else path.substring(slash + 1)
    val cleaned = buildString {
        raw.forEach { char ->
            if (char.isLetterOrDigit() || char == '.' || char == '-' || char == '_') append(char)
        }
    }.trim('.')
    if (cleaned.isEmpty() || cleaned.length > 120) return "download.bin"
    return cleaned
}

/**
 * 字节数拆成数字和单位。数字可以按位做动画，单位单独放着。
 *
 * 未知长度时数字是破折号，单位是空的。
 *
 * @property number 不含单位的数字，例如 `1.2`。
 * @property unit 单位，例如 `MB`。没有单位时是空字符串。
 */
internal data class ByteAmount(
    val number: String,
    val unit: String,
)

/**
 * 把字节数拆开。规则和 [formatBytes] 相同。
 *
 * @param value 字节数。负数表示未知。
 */
internal fun byteAmount(value: Long): ByteAmount {
    if (value < 0L) return ByteAmount(number = "—", unit = "")
    if (value < 1024L) return ByteAmount(number = value.toString(), unit = "B")
    val kilobytes = value / 1024.0
    if (kilobytes < 1024.0) {
        return ByteAmount(number = String.format(Locale.US, "%.1f", kilobytes), unit = "KB")
    }
    val megabytes = kilobytes / 1024.0
    if (megabytes < 1024.0) {
        return ByteAmount(number = String.format(Locale.US, "%.1f", megabytes), unit = "MB")
    }
    return ByteAmount(number = String.format(Locale.US, "%.1f", megabytes / 1024.0), unit = "GB")
}

/**
 * 把字节数写成带单位的短句。未知长度用破折号，不带语言。
 *
 * @param value 字节数。负数表示未知。
 * @return 界面上的短语。
 */
internal fun formatBytes(value: Long): String {
    val amount = byteAmount(value)
    if (amount.unit.isEmpty()) return amount.number
    return "${amount.number} ${amount.unit}"
}

/**
 * 把瞬时速度写成每秒字节数。还没测到时不显示。
 *
 * @param bps 字节每秒。
 * @return 界面上的短语。没有速度时为 null。
 */
internal fun formatSpeed(bps: Long): String? {
    if (bps <= 0L) return null
    return "${formatBytes(bps)}/s"
}

/**
 * 把剩余时间写成短句。未知时不显示。
 *
 * @param millis 剩余毫秒。负数表示未知。
 * @return 界面上的短语。未知时为 null。
 */
internal fun Context.formatEta(millis: Long): String? {
    if (millis < 0L) return null
    val seconds = millis / 1000L
    if (seconds < 60L) return getString(R.string.eta_seconds, seconds.toInt())
    val minutes = seconds / 60L
    val rest = seconds % 60L
    return getString(R.string.eta_minutes, minutes.toInt(), rest.toInt())
}

/**
 * 已知总长度时换成 0 到 1 的比例。总长度未知时当作 0。
 *
 * @param downloaded 已写入字节。
 * @param total 总字节。未知或不是正数时进度为 0。
 * @return 进度条和百分比使用的比例。
 */
internal fun progressFraction(downloaded: Long, total: Long): Float {
    if (total <= 0L) return 0f
    return (downloaded.coerceAtLeast(0L).toFloat() / total.toFloat()).coerceIn(0f, 1f)
}

/**
 * 状态的短句。失败时带上错误说明。
 *
 * @param record 当前任务。
 * @return 一行状态。
 */
internal fun Context.statusText(record: DownloadRecord): String {
    val failed = record.error?.message?.takeIf { it.isNotBlank() }
    return when (record.status) {
        Status.ADDED -> getString(R.string.status_added)
        Status.QUEUED -> getString(R.string.status_queued)
        Status.WAITING_NETWORK -> getString(R.string.status_waiting_network)
        Status.DOWNLOADING -> getString(R.string.status_downloading)
        Status.PAUSED -> getString(R.string.status_paused)
        Status.COMPLETED -> getString(R.string.status_completed)
        Status.FAILED -> if (failed == null) {
            getString(R.string.status_failed)
        } else {
            getString(R.string.status_failed_detail, failed)
        }
        Status.CANCELLED -> getString(R.string.status_cancelled)
        Status.REMOVED -> getString(R.string.status_removed)
        Status.DELETED -> getString(R.string.status_deleted)
    }
}
