/**
 * 临时文件的位置。内存仓库和 Room 仓库用同一条规则。
 *
 * @file
 */
package com.arturoyi.fastdownload.core.model

import java.io.File

/**
 * 返回和最终文件同目录的临时文件。
 *
 * 名字是目标文件名、任务标识和 `.part`。标识是十六进制，不会变成另一个目录。
 *
 * @param root 允许写入的根目录。
 * @param destination 最终相对路径。
 * @param publicId 任务标识。
 * @return 临时文件。它可能还不存在。
 */
public fun downloadPartFile(root: File, destination: Destination, publicId: String): File {
    val target = destination.resolveInside(root)
    return File(target.parentFile, target.name + "." + publicId + ".part")
}

/**
 * 按调度顺序排列任务。
 *
 * 优先级数值大的在前。优先级相同则创建时间早的在前。
 *
 * @receiver 未排序的任务。
 * @return 新的列表，不修改原列表。
 */
public fun List<DownloadRecord>.inScheduleOrder(): List<DownloadRecord> {
    return sortedWith(
        compareByDescending<DownloadRecord> { record -> record.request.priority.value }
            .thenBy { record -> record.createdAt },
    )
}
