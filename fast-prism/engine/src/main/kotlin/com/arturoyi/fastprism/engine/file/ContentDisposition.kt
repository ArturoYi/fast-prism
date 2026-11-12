/**
 * 判断响应给出的文件名会不会逃出下载目录。
 *
 * @file
 */
package com.arturoyi.fastprism.engine.file

/**
 * `Content-Disposition` 里的文件名是否能接受。
 */
public enum class DispositionVerdict {
    /** 响应没有给出文件名。 */
    NONE,

    /** 文件名只是一个不含路径的名字。 */
    SAFE,

    /** 文件名含有路径穿越、分隔符、空字节，或超过 255 个字符。 */
    REJECT,
}

/**
 * 检查 [header] 中的 `filename` 或 `filename*`。
 *
 * 没有文件名时返回 [DispositionVerdict.NONE]。`../`、绝对路径、斜杠、反斜杠和空字节都拒绝。
 * `filename*` 会先按百分号解码，再做同样的检查。
 *
 * @param header 原始头字段。null 或空白视为没有文件名。
 * @return 检查结论。无法识别出文件名时为 [DispositionVerdict.NONE]。
 */
public fun dispositionVerdict(header: String?): DispositionVerdict {
    if (header.isNullOrBlank()) return DispositionVerdict.NONE
    val name = filenameStar(header) ?: filenameQuoted(header) ?: filenamePlain(header)
    if (name.isNullOrBlank()) return DispositionVerdict.NONE
    val decoded = if (name.startsWith("UTF-8''") || name.startsWith("utf-8''")) {
        percentDecode(name.substringAfter("''"))
    } else {
        name
    }
    return if (isSafeFileName(decoded)) DispositionVerdict.SAFE else DispositionVerdict.REJECT
}

/**
 * 读取 `filename*` 的值。
 *
 * @param header 完整头字段。
 * @return 星号参数的值。没有时为 null。
 */
private fun filenameStar(header: String): String? {
    val marker = "filename*"
    val start = header.indexOf(marker, ignoreCase = true)
    if (start < 0) return null
    val after = header.substring(start + marker.length).trimStart()
    if (!after.startsWith("=")) return null
    return token(after.substring(1))
}

/**
 * 读取带引号的 `filename`。
 *
 * @param header 完整头字段。
 * @return 引号内的文本。没有时为 null。
 */
private fun filenameQuoted(header: String): String? {
    val marker = Regex("""filename\s*=\s*"([^"]*)"""", RegexOption.IGNORE_CASE)
    return marker.find(header)?.groupValues?.get(1)
}

/**
 * 读取不带引号的 `filename`。
 *
 * 遇到分号即结束。这个函数不匹配 `filename*`。
 *
 * @param header 完整头字段。
 * @return 文件名记号。没有时为 null。
 */
private fun filenamePlain(header: String): String? {
    val marker = Regex("""filename\s*=\s*([^";]+)""", RegexOption.IGNORE_CASE)
    return marker.find(header)?.groupValues?.get(1)?.trim()
}

/**
 * 取等号后面的一段，遇到分号结束，并去掉成对引号。
 *
 * @param value 等号之后的文本。
 * @return 去掉空白和引号后的值。
 */
private fun token(value: String): String {
    val end = value.indexOf(';').let { index -> if (index < 0) value.length else index }
    return value.substring(0, end).trim().removeSurrounding("\"")
}

/**
 * 把 `%HH` 解码成字符。
 *
 * 只处理 ASCII 十六进制。非法百分号原样保留，后面的路径检查仍会看到原文。
 *
 * @param value `filename*` 里编码过的文件名。
 * @return 解码后的文本。
 */
private fun percentDecode(value: String): String {
    val out = StringBuilder()
    var index = 0
    while (index < value.length) {
        val current = value[index]
        if (current == '%' && index + 2 < value.length) {
            val byte = value.substring(index + 1, index + 3).toIntOrNull(16)
            if (byte == null) {
                out.append(current)
                index += 1
            } else {
                out.append(byte.toChar())
                index += 3
            }
        } else {
            out.append(current)
            index += 1
        }
    }
    return out.toString()
}

/**
 * 判断 [name] 能不能当作单个路径段。
 *
 * @param name 已经解码的文件名。
 * @return 不含 `.`、`..`、分隔符、空字节且长度不超过 255 时为 true。
 */
private fun isSafeFileName(name: String): Boolean {
    if (name.isBlank() || name.length > 255) return false
    if (name == "." || name == "..") return false
    if ('\u0000' in name || '/' in name || '\\' in name) return false
    if ("../" in name || "..\\" in name) return false
    return true
}
