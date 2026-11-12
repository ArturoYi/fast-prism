/**
 * 解析 HTTP `Content-Range` 头。
 *
 * @file
 */
package com.arturoyi.fastprism.engine.http

import com.arturoyi.fastprism.core.model.ContentRange

/**
 * 解析 `bytes` 单位的 `Content-Range`。
 *
 * 接受 `bytes 0-0/128`，也接受区间未知、只给出总长度的形式。
 * 单位不是 `bytes`、缺少 `/`、数字无法解析，或起点大于终点时返回 null，不抛异常。
 * 总长度为 `*` 时 [ContentRange.total] 为 null。
 *
 * @param header 原始头字段值。null 或空白返回 null。
 * @return 解析出的区间；格式不符合时为 null。
 */
internal fun parseContentRange(header: String?): ContentRange? {
    if (header.isNullOrBlank()) return null
    val value = header.trim()
    if (!value.startsWith("bytes", ignoreCase = true)) return null
    val spec = value.substringAfter(' ', missingDelimiterValue = "")
    if (!spec.contains('/')) return null
    val rangePart = spec.substringBefore('/').trim()
    val total = when (val totalPart = spec.substringAfter('/').trim()) {
        "*" -> null
        else -> totalPart.toLongOrNull() ?: return null
    }
    if (rangePart == "*") {
        return ContentRange(start = null, endInclusive = null, total = total)
    }
    val startText = rangePart.substringBefore('-', missingDelimiterValue = "")
    val endText = rangePart.substringAfter('-', missingDelimiterValue = "")
    val start = startText.toLongOrNull() ?: return null
    val end = endText.toLongOrNull() ?: return null
    if (start !in 0L..end) return null
    return ContentRange(start = start, endInclusive = end, total = total)
}
