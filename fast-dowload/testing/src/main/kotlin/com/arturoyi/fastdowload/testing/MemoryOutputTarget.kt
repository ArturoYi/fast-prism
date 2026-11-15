/**
 * 内存中的写出目标，供契约测试替换真实文件。
 *
 * @file
 */
package com.arturoyi.fastdowload.testing

import com.arturoyi.fastdowload.core.spi.OutputTarget
import com.arturoyi.fastdowload.core.spi.SeekableSink

/**
 * 把字节留在内存数组里。
 *
 * 可以按偏移写入。[atomicCommit] 只做一个标记，不复制数据。
 */
public class MemoryOutputTarget : OutputTarget {
    private val data = ArrayList<Byte>()
    private var committed = false

    /**
     * 当前已经写入的字节。
     *
     * @return 从位置 0 到当前长度的副本。
     */
    public fun bytes(): ByteArray = ByteArray(data.size) { index -> data[index] }

    /**
     * [atomicCommit] 是否已经调用过。
     *
     * @return 调用过为 true。
     */
    public fun isCommitted(): Boolean = committed

    /**
     * 内存目标可以按任意偏移写入。
     *
     * @return 总是 true。
     */
    public override fun supportSeek(): Boolean = true

    /**
     * 打开一个指向同一块内存的写入端。
     *
     * @return 新的写入端。多个写入端共享 [bytes]。
     */
    public override fun openSink(): SeekableSink = Sink()

    /**
     * 把内存长度至少扩到 [length]，不足的位置补 0。
     *
     * @param length 需要的字节数。
     */
    public override fun preAllocate(length: Long) {
        require(length >= 0L) { "length 不能为负" }
        grow(length.toInt())
    }

    /**
     * 标记提交完成。
     */
    public override fun atomicCommit() {
        committed = true
    }

    private fun grow(size: Int) {
        while (data.size < size) data.add(0)
    }

    private inner class Sink : SeekableSink {
        private var position = 0

        override fun seek(offset: Long) {
            require(offset >= 0L) { "offset 不能为负" }
            position = offset.toInt()
        }

        override fun write(bytes: ByteArray, off: Int, len: Int) {
            grow(position + len)
            for (index in 0 until len) {
                data[position + index] = bytes[off + index]
            }
            position += len
        }

        override fun flushAndSync() = Unit

        override fun setLength(length: Long) {
            require(length >= 0L) { "length 不能为负" }
            val size = length.toInt()
            grow(size)
            while (data.size > size) data.removeAt(data.lastIndex)
        }

        override fun close() = Unit
    }
}
