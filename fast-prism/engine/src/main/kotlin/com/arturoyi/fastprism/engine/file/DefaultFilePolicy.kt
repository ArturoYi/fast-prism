/**
 * 临时文件、预分配和完成后的改名。
 *
 * @file
 */
package com.arturoyi.fastprism.engine.file

import com.arturoyi.fastprism.core.model.Destination
import com.arturoyi.fastprism.engine.io.FileSink
import java.io.File
import java.io.IOException
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.StandardCopyOption

/**
 * 预分配空间不足时抛出的异常。
 *
 * @property requiredBytes 这次下载还需要的字节数。
 * @property freeBytes 父目录所在卷报告的可用字节数。
 */
public class FileAllocationException(
    public val requiredBytes: Long,
    public val freeBytes: Long,
) : IOException("需要 $requiredBytes 字节，可用 $freeBytes 字节")

/**
 * 在内部目录里写临时文件，完成后再改成目标名。
 *
 * 可用空间用 [File.getUsableSpace] 判断。这里没有 Android 的 `StatFs`。
 */
public class DefaultFilePolicy {
    /**
     * 返回和目标文件同目录的临时文件。
     *
     * 名字是目标文件名加上任务标识和 `.part`。标识是十六进制，不会引入路径分隔符。
     *
     * @param root 允许写入的根目录。
     * @param destination 最终相对路径。
     * @param publicId 任务标识。
     * @return 尚不一定存在的临时文件。
     */
    public fun partFile(root: File, destination: Destination, publicId: String): File {
        val target = destination.resolveInside(root)
        return File(target.parentFile, target.name + "." + publicId + ".part")
    }

    /**
     * 创建 [file] 的父目录。
     *
     * 父目录已经存在时直接返回。
     *
     * @param file 即将写入的文件。
     * @throws IOException 父目录无法创建。
     */
    public fun ensureParent(file: File) {
        val parent = file.parentFile ?: return
        if (!parent.isDirectory && !parent.mkdirs()) {
            throw IOException("无法创建内部目录")
        }
    }

    /**
     * 创建父目录，并在空间足够时把 [file] 扩展到 [length]。
     *
     * 文件已经不短于 [length] 时不缩短它。长度为 0 时只保证文件存在。
     *
     * @param file 临时文件。
     * @param length 资源总长度，单位字节。
     * @throws FileAllocationException 还需要写入的字节数大于可用空间。
     * @throws IOException 父目录或文件长度无法创建。
     */
    public fun preallocate(file: File, length: Long) {
        ensureParent(file)
        val parent = file.parentFile
        val existing = if (file.exists()) file.length() else 0L
        val needed = (length - existing).coerceAtLeast(0L)
        if (needed > 0L) {
            val free = parent?.usableSpace ?: 0L
            if (free < needed) throw FileAllocationException(needed, free)
        }
        FileSink(file).use { sink ->
            if (file.length() < length) sink.setLength(length)
        }
    }

    /**
     * 用临时文件替换最终目标。
     *
     * 先尝试原子改名。文件系统不支持时再普通移动。目标已存在时覆盖。
     *
     * @param part 已经写完的临时文件。
     * @param target 最终文件。
     * @throws IOException 改名失败。
     */
    public fun commit(part: File, target: File) {
        val source = part.toPath()
        val destination = target.toPath()
        try {
            Files.move(
                source,
                destination,
                StandardCopyOption.ATOMIC_MOVE,
                StandardCopyOption.REPLACE_EXISTING,
            )
        } catch (_: AtomicMoveNotSupportedException) {
            Files.move(source, destination, StandardCopyOption.REPLACE_EXISTING)
        }
    }

    /**
     * 删掉临时文件和最终文件。
     *
     * 文件不存在时忽略。用于确认资源已经变化、必须从头再写的情况。
     *
     * @param part 临时文件。
     * @param target 最终文件。
     */
    public fun discard(part: File, target: File) {
        part.delete()
        target.delete()
    }
}

/**
 * 判断可用空间够不够这次分配。
 *
 * 下载前用父目录的可用空间调用它。测试可以直接传入较小的数字。
 *
 * @param freeBytes 可用字节数。
 * @param requiredBytes 需要的字节数。0 或负数不检查。
 * @throws FileAllocationException [requiredBytes] 大于 [freeBytes]。
 */
public fun ensureSpace(freeBytes: Long, requiredBytes: Long) {
    if (requiredBytes > 0L && freeBytes < requiredBytes) {
        throw FileAllocationException(requiredBytes, freeBytes)
    }
}
