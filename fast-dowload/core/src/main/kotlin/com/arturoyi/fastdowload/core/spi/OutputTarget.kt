/**
 * 下载字节的写出目标。
 *
 * 普通文件和内容 Uri 各自实现。不支持定位时，引擎应退化为单连接顺序写。
 *
 * @file
 */
package com.arturoyi.fastdowload.core.spi

import java.io.Closeable

/**
 * 可定位、可同步的写入端。
 *
 * 先 [write]，[flushAndSync] 成功之后才允许记录偏移。
 */
public interface SeekableSink : Closeable {
    /**
     * 把下一次写入的位置移到 [offset]。
     *
     * @param offset 距离起点的字节数，从 0 开始。
     */
    public fun seek(offset: Long)

    /**
     * 在当前位置写入一段字节。
     *
     * @param bytes 源数组。
     * @param off [bytes] 中的起始下标。
     * @param len 要写入的字节数。
     */
    public fun write(bytes: ByteArray, off: Int, len: Int)

    /**
     * 把已经写入的数据同步到存储设备。
     */
    public fun flushAndSync()

    /**
     * 把目标长度改为 [length]。
     *
     * @param length 新的长度，单位字节。
     */
    public fun setLength(length: Long)
}

/**
 * 一种可以打开的下载目标。
 */
public interface OutputTarget {
    /**
     * 这个目标能否按任意偏移写入。
     *
     * @return 可以 [SeekableSink.seek] 时为 true。
     */
    public fun supportSeek(): Boolean

    /**
     * 打开一个写入端。
     *
     * 调用方必须关闭它。
     *
     * @return 新的写入端。
     */
    public fun openSink(): SeekableSink

    /**
     * 在空间足够时把目标扩展到 [length]。
     *
     * @param length 需要的字节数。
     */
    public fun preAllocate(length: Long)

    /**
     * 让已写完的字节对调用方可见。
     *
     * 文件实现会把临时文件改成最终名字。内容 Uri 已经直接写在目标上，这里可以为空。
     */
    public fun atomicCommit()
}
