/**
 * 先把字节同步到文件，再允许记录偏移。
 *
 * @file
 */
package com.arturoyi.fastdowload.engine.io

import com.arturoyi.fastdowload.core.model.Defaults
import com.arturoyi.fastdowload.engine.store.InMemoryBreakpointStore
import com.arturoyi.fastdowload.engine.store.OffsetCommit
import com.arturoyi.fastdowload.engine.store.RemitSyncExecutor

/**
 * 一个分块的写入和提交。
 *
 * [writeAndCommit] 先写字节，再 `sync`，成功之后才把偏移交给合并器。
 * 合并器真正写入存储的动作也定义在这里，避免其他类直接推进偏移。
 *
 * @param sink 这个分块专用的文件。
 * @param publicId 任务标识。
 * @param remit 多个分块共用的合并器。
 */
public class FlushOrderGuard(
    private val sink: SyncSink,
    private val publicId: String,
    private val remit: RemitSyncExecutor,
) {
    /**
     * 写入 [len] 字节，同步成功后再记入断点。
     *
     * [len] 为 0 时直接返回。同步抛错时，这批字节不会进入合并器。
     *
     * @param blockIndex 分块序号。
     * @param bytes 源数组。
     * @param off [bytes] 中的起始下标。
     * @param len 要写入的字节数。
     * @throws java.io.IOException 写入或同步失败。
     */
    public fun writeAndCommit(blockIndex: Int, bytes: ByteArray, off: Int, len: Int) {
        if (len == 0) return
        sink.write(bytes, off, len)
        sink.flushAndSync()
        remit.record(publicId, blockIndex, len.toLong())
    }

    /**
     * 文件里的字节已经写完时，只做同步并记下 [byteCount]。
     *
     * 用于事先不知道总长度、最后才能确定分块大小的写入。
     *
     * @param blockIndex 分块序号。
     * @param byteCount 已经写入文件的字节数。0 时只同步，不记偏移。
     * @throws java.io.IOException 同步失败。此时不会记偏移。
     */
    public fun commitSynced(blockIndex: Int, byteCount: Long) {
        sink.flushAndSync()
        if (byteCount > 0L) remit.record(publicId, blockIndex, byteCount)
    }

    public companion object {
        /**
         * 创建把合并结果写进 [store] 的合并器。
         *
         * 存储上的偏移增加只发生在这个方法给出的提交动作里。
         *
         * @param store 内存断点。
         * @param delayMs 合并窗口，默认 [Defaults.REMIT_DELAY_MS]。
         * @return 尚未启动写入的合并器。使用完要关闭。
         */
        public fun openRemit(
            store: InMemoryBreakpointStore,
            delayMs: Long = Defaults.REMIT_DELAY_MS,
        ): RemitSyncExecutor {
            return RemitSyncExecutor(delayMs, OffsetCommit { id, index, delta ->
                store.onFlushSuccess(id, index, delta)
            })
        }
    }
}
