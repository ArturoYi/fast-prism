/**
 * 可按偏移写入并同步到存储设备的目标。
 *
 * @file
 */
package com.arturoyi.fastdowload.engine.io

import com.arturoyi.fastdowload.core.spi.SeekableSink

/**
 * 分块写入使用的文件能力。
 *
 * [FileSink] 是普通文件上的实现。调用顺序是先 [write]，[flushAndSync] 成功之后才允许记录偏移。
 */
public interface SyncSink : SeekableSink {
    /**
     * 把下一次写入的位置移到 [offset]。
     *
     * @param offset 距离文件起点的字节数，从 0 开始。
     */
    public override fun seek(offset: Long)

    /**
     * 在当前偏移写入一段字节，并前移写入位置。
     *
     * @param bytes 源数组。
     * @param off [bytes] 中的起始下标。
     * @param len 要写入的字节数。
     */
    public override fun write(bytes: ByteArray, off: Int, len: Int)

    /**
     * 把已经写入的数据同步到存储设备。
     *
     * 失败时，这一段字节还不能记入断点。
     */
    public override fun flushAndSync()

    /**
     * 把文件长度改为 [length]。
     *
     * @param length 新的文件长度，单位字节。
     */
    public override fun setLength(length: Long)
}
