/**
 * 不经过原子改名的文件提交。
 *
 * @file
 */
package com.arturoyi.fastdowload.engine.file

import com.arturoyi.fastdowload.core.model.Destination
import com.arturoyi.fastdowload.core.spi.FilePolicy
import java.io.File
import java.io.FileOutputStream
import java.io.IOException

/**
 * 先把临时文件复制到目标，再删除临时文件。
 *
 * 预分配和路径规则与 [DefaultFilePolicy] 相同。提交本身不是一次原子改名，适合不能 `ATOMIC_MOVE` 的目标。
 */
public class CopyOnCommitFilePolicy : FilePolicy {
    private val files = DefaultFilePolicy()

    /**
     * 与 [DefaultFilePolicy.partFile] 相同。
     *
     * @param root 允许写入的根目录。
     * @param destination 最终相对路径。
     * @param publicId 任务标识。
     * @return 临时文件。
     */
    public override fun partFile(root: File, destination: Destination, publicId: String): File {
        return files.partFile(root, destination, publicId)
    }

    /**
     * 创建父目录。
     *
     * @param file 即将写入的文件。
     */
    public override fun ensureParent(file: File) {
        files.ensureParent(file)
    }

    /**
     * 在空间足够时扩展文件。
     *
     * @param file 临时文件。
     * @param length 需要的字节数。
     */
    public override fun preallocate(file: File, length: Long) {
        files.preallocate(file, length)
    }

    /**
     * 把 [part] 的字节复制到 [target]，然后删除 [part]。
     *
     * @param part 已经写完的临时文件。
     * @param target 最终文件。
     * @throws IOException 复制或删除失败。
     */
    public override fun commit(part: File, target: File) {
        ensureParent(target)
        part.inputStream().use { input ->
            FileOutputStream(target).use { output ->
                input.copyTo(output)
                output.channel.force(true)
            }
        }
        if (!part.delete()) {
            throw IOException("无法删除临时文件")
        }
    }

    /**
     * 删掉两个文件。
     *
     * @param part 临时文件。
     * @param target 最终文件。
     */
    public override fun discard(part: File, target: File) {
        files.discard(part, target)
    }
}
