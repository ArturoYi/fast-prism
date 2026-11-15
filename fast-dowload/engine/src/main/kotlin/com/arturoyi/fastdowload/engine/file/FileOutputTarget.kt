/**
 * 把普通文件当作下载目标。
 *
 * @file
 */
package com.arturoyi.fastdowload.engine.file

import com.arturoyi.fastdowload.core.spi.OutputTarget
import com.arturoyi.fastdowload.core.spi.SeekableSink
import com.arturoyi.fastdowload.engine.io.FileSink
import java.io.File

/**
 * 支持定位的本地文件目标。
 *
 * 写入直接落在 [file] 上。调用方如果要先写临时文件再改名，应把临时文件传进来，
 * 完成后再调用 [com.arturoyi.fastdowload.engine.file.DefaultFilePolicy.commit]。
 *
 * @param file 要写入的文件。父目录会在预分配时创建。
 */
public class FileOutputTarget(
    private val file: File,
) : OutputTarget {
    private val files = DefaultFilePolicy()

    /**
     * 普通文件可以按偏移写入。
     *
     * @return 总是 true。
     */
    public override fun supportSeek(): Boolean = true

    /**
     * 打开 [file]。
     *
     * @return 可定位的文件写入端。
     */
    public override fun openSink(): SeekableSink {
        files.ensureParent(file)
        return FileSink(file)
    }

    /**
     * 把 [file] 扩展到 [length]。
     *
     * @param length 需要的字节数。
     */
    public override fun preAllocate(length: Long) {
        files.preallocate(file, length)
    }

    /**
     * 字节已经写在 [file] 上，没有另一次改名。
     */
    public override fun atomicCommit(): Unit = Unit
}
