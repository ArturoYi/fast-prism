/**
 * 为不会覆盖已有文件的目标起名。
 *
 * @file
 */
package com.arturoyi.fastdownload.core.model

/**
 * 选出一个尚未被占用的内部相对路径。
 *
 * [base] 空闲时原样返回。被占用时在最后一个扩展名之前插入 ` (1)`、` (2)`。
 * `a.bin` 变成 `a (1).bin`，`downloads/a.tar.gz` 变成 `downloads/a.tar (1).gz`。
 *
 * @param base 调用方请求的路径。
 * @param isTaken 这个相对路径是否已经有文件或任务。
 * @return 可以写入的路径。
 * @throws IllegalStateException 试过 10000 个后缀仍然都被占用。
 */
public fun nextFreeDestination(base: Destination, isTaken: (String) -> Boolean): Destination {
    if (!isTaken(base.relativePath)) return base
    val slash = base.relativePath.lastIndexOf('/')
    val parent = if (slash < 0) "" else base.relativePath.substring(0, slash)
    val fileName = if (slash < 0) base.relativePath else base.relativePath.substring(slash + 1)
    val dot = fileName.lastIndexOf('.')
    val stem = if (dot > 0) fileName.substring(0, dot) else fileName
    val extension = if (dot > 0) fileName.substring(dot) else ""
    for (index in 1..10_000) {
        val candidateName = "$stem ($index)$extension"
        val relative = if (parent.isEmpty()) candidateName else "$parent/$candidateName"
        if (!isTaken(relative)) return Destination(relative)
    }
    throw IllegalStateException("没有可用的文件名")
}
