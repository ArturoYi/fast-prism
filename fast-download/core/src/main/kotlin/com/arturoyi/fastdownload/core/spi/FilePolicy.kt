/**
 * 临时文件的位置、预分配和完成后的替换。
 *
 * @file
 */
package com.arturoyi.fastdownload.core.spi

import com.arturoyi.fastdownload.core.model.Destination
import java.io.File

/**
 * 决定字节先写到哪里，以及怎样变成最终文件。
 *
 * 实现要能被多条任务同时调用。预分配失败时抛出 [java.io.IOException]。
 */
public interface FilePolicy {
    /**
     * 返回这次下载使用的临时文件。
     *
     * 文件可以还不存在。路径必须落在 [root] 里面。
     *
     * @param root 允许写入的根目录。
     * @param destination 最终相对路径。
     * @param publicId 任务标识。
     * @return 临时文件。
     */
    public fun partFile(root: File, destination: Destination, publicId: String): File

    /**
     * 创建 [file] 的父目录。
     *
     * 父目录已经存在时直接返回。
     *
     * @param file 即将写入的文件。
     */
    public fun ensureParent(file: File)

    /**
     * 准备 [file]，使它至少能容纳 [length] 字节。
     *
     * 空间不够时抛出 [java.io.IOException]。长度为 0 时只保证父目录存在。
     *
     * @param file 临时文件。
     * @param length 需要的字节数。
     */
    public fun preallocate(file: File, length: Long)

    /**
     * 用临时文件替换最终文件。
     *
     * 成功后 [part] 不再作为临时文件留在原处，[target] 的内容与提交前的临时文件一致。
     *
     * @param part 已经写完的临时文件。
     * @param target 最终文件。
     */
    public fun commit(part: File, target: File)

    /**
     * 删掉临时文件和最终文件。
     *
     * 文件不存在时忽略。
     *
     * @param part 临时文件。
     * @param target 最终文件。
     */
    public fun discard(part: File, target: File)
}
