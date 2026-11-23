/**
 * 把字节数写成界面上的数字和单位。
 *
 * @file
 */
package com.arturoyi.albumcleaner.sample.utils

import java.time.Instant
import java.time.ZoneId
import java.util.Locale

/**
 * 字节数拆成数字和单位。数字用大号字，单位单独放着。
 *
 * @property number 不含单位的数字，例如 `1.2`。还没有结果时是破折号。
 * @property unit 单位，例如 `MB`。没有单位时是空字符串。
 */
internal data class ByteAmount(
    val number: String,
    val unit: String,
)

/**
 * 把字节数拆开。
 *
 * @param value 字节数。负数表示还没有结果。
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
 * 把字节数写成带单位的短句。
 *
 * @param value 字节数。负数时只有破折号。
 */
internal fun formatBytes(value: Long): String {
    val amount = byteAmount(value)
    return if (amount.unit.isEmpty()) amount.number else "${amount.number} ${amount.unit}"
}

/**
 * 过期日，写成 `yyyy-MM-dd`。没有时间时是破折号。
 */
internal fun formatDay(millis: Long): String {
    if (millis <= 0L) return "—"
    return Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault()).toLocalDate().toString()
}
