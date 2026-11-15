/**
 * 内置的两种分块策略。
 *
 * @file
 */
package com.arturoyi.fastdowload.core.strategy

import com.arturoyi.fastdowload.core.model.DownloadBlock
import com.arturoyi.fastdowload.core.spi.BreakpointStrategy

/**
 * 按文件长度分档，并尊重调用方给出的连接数。
 *
 * 不能随机写入、长度未知或长度不是正数时只有 1 块。
 */
public object AdaptiveBreakpointStrategy : BreakpointStrategy {
    /**
     * 使用 [BlockSplitter.chooseBlockCount] 的档位。
     *
     * @param totalBytes 资源总字节数。
     * @param hint 调用方希望的连接数。
     * @param supportSeek 目标能否按绝对偏移写入。
     * @return 至少为 1 的分块数。
     */
    public override fun blockCount(totalBytes: Long, hint: Int?, supportSeek: Boolean): Int {
        return BlockSplitter.chooseBlockCount(totalBytes, hint, supportSeek)
    }

    /**
     * 使用 [BlockSplitter.split] 生成互不重叠的块。
     *
     * @param recordId 任务标识。
     * @param totalBytes 资源总字节数。
     * @param hint 调用方希望的连接数。
     * @param supportSeek 目标能否按绝对偏移写入。
     * @return 覆盖整个资源的分块。
     */
    public override fun split(
        recordId: String,
        totalBytes: Long,
        hint: Int?,
        supportSeek: Boolean,
    ): List<DownloadBlock> {
        return BlockSplitter.split(recordId, totalBytes, hint, supportSeek)
    }
}

/**
 * 始终只用一块，从文件开头顺序写。
 *
 * 用来替换自适应策略，或在调用方明确不要并行连接时使用。
 */
public object SingleBlockStrategy : BreakpointStrategy {
    /**
     * 始终返回 1。
     *
     * @param totalBytes 不参与计算。
     * @param hint 不参与计算。
     * @param supportSeek 不参与计算。
     * @return 1。
     */
    public override fun blockCount(totalBytes: Long, hint: Int?, supportSeek: Boolean): Int = 1

    /**
     * 生成唯一一块，覆盖 `[0, totalBytes)`。
     *
     * @param recordId 任务标识。
     * @param totalBytes 资源总字节数。
     * @param hint 忽略。
     * @param supportSeek 忽略。传入 false 让切分器只产生一块。
     * @return 只有序号 0 的一块。
     */
    public override fun split(
        recordId: String,
        totalBytes: Long,
        hint: Int?,
        supportSeek: Boolean,
    ): List<DownloadBlock> {
        return BlockSplitter.split(recordId, totalBytes, hint = 1, supportSeek = false)
    }
}
