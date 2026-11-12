/**
 * 把下载文件限制在应用内部目录中。
 *
 * @file
 */
package com.arturoyi.fastdowload.core.model

import java.io.File

/**
 * 应用内部目录中的相对路径。
 *
 * 不接受绝对路径、`content://` 或外部存储。调用方传入 `context.filesDir` 作为根目录。
 * 空段、`.` 和 `..` 都会拒绝，路径分隔符可以是 `/` 或 `\`。
 *
 * @property relativePath 相对存储根目录的路径，不能以分隔符开头。每一段最长 255 个字符。
 * @throws IllegalArgumentException 路径为空白、是绝对路径、含空字节、含空段或 `.` / `..`，或某一段超过 255 个字符。
 */
public data class Destination(
    public val relativePath: String,
) {
    init {
        require(relativePath.isNotBlank()) { "destination 不能为空" }
        require(!relativePath.startsWith('/') && !relativePath.startsWith('\\')) {
            "只允许应用内部相对路径"
        }
        require('\u0000' !in relativePath) { "路径含空字节" }
        val parts = relativePath.split('/', '\\')
        require(parts.none { it.isEmpty() || it == "." || it == ".." }) {
            "拒绝路径穿越或空路径段"
        }
        require(parts.all { it.length <= 255 }) { "路径段不能超过 255 个字符" }
    }

    /**
     * 把相对路径解析到 [root] 之下，并拒绝逃出该目录的结果。
     *
     * 比较使用规范化后的绝对路径。符号链接如果指向 [root] 外面，也会被拒绝。
     *
     * @param root 允许写入的根目录，通常是应用内部的 `filesDir`。
     * @return 位于 [root] 内部的目标文件。文件本身可以尚不存在。
     * @throws IllegalArgumentException 规范化之后的目标不在 [root] 里面。
     * @throws java.io.IOException 规范化路径时无法访问文件系统。
     */
    public fun resolveInside(root: File): File {
        val base = root.canonicalFile
        val target = File(base, relativePath).canonicalFile
        val prefix = base.path + File.separator
        require(target.path.startsWith(prefix)) { "目标必须位于应用内部目录" }
        return target
    }
}
