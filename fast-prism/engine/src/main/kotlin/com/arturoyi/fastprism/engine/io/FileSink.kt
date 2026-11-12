/**
 * 按绝对偏移写入本地文件。
 *
 * @file
 */
package com.arturoyi.fastprism.engine.io

import java.io.File
import java.io.RandomAccessFile

/**
 * 用 [RandomAccessFile] 写入单个目标文件。
 *
 * 实现 [SyncSink]。构造时即以读写方式打开 [file]；文件不存在时会创建。关闭时不额外做 `sync`。
 *
 * @param file 要写入的文件。父目录必须已经存在。
 * @throws java.io.FileNotFoundException 父目录不存在，或文件无法以读写方式打开。
 */
public class FileSink(
    file: File,
) : SyncSink {
    private val raf = RandomAccessFile(file, "rw")

    /**
     * 把下一次写入的位置移到 [offset]。
     *
     * @param offset 距离文件起点的字节数，从 0 开始。
     * @throws IllegalArgumentException [offset] 小于 0。
     * @throws java.io.IOException 底层文件定位失败。
     */
    public override fun seek(offset: Long) {
        require(offset >= 0L) { "offset 不能为负" }
        raf.seek(offset)
    }

    /**
     * 在当前偏移写入 [bytes] 的一段，并前移文件指针。
     *
     * @param bytes 源数组。
     * @param off [bytes] 中的起始下标。
     * @param len 要写入的字节数。
     * @throws IllegalArgumentException [off] 或 [len] 为负，或区间超出 [bytes]。
     * @throws java.io.IOException 写入失败。
     */
    public override fun write(bytes: ByteArray, off: Int, len: Int) {
        require(len in 0..bytes.size && off in 0..(bytes.size - len)) { "写入区间不合法" }
        raf.write(bytes, off, len)
    }

    /**
     * 把已写入的数据同步到存储设备。
     *
     * 调用 `FileDescriptor.sync()`。失败时文件指针和已写字节不会回滚。
     *
     * @throws java.io.SyncFailedException 存储设备拒绝同步。
     * @throws java.io.IOException 同步过程中的其他 I/O 错误。
     */
    public override fun flushAndSync() {
        raf.fd.sync()
    }

    /**
     * 把文件长度改为 [length]。
     *
     * 缩短时截掉尾部。伸长时新增区间的内容由文件系统决定。
     *
     * @param length 新的文件长度，单位字节。
     * @throws IllegalArgumentException [length] 小于 0。
     * @throws java.io.IOException 修改长度失败。
     */
    public override fun setLength(length: Long) {
        require(length >= 0L) { "length 不能为负" }
        raf.setLength(length)
    }

    /**
     * 关闭底层文件。
     *
     * 不调用 [flushAndSync]。已经关闭后再调用会直接返回。
     */
    public override fun close() {
        raf.close()
    }
}
