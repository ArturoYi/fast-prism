/**
 * 按文件长度把一次下载切成互不重叠的分块。
 *
 * @file
 */
package com.arturoyi.fastdowload.core.strategy

import com.arturoyi.fastdowload.core.model.Defaults
import com.arturoyi.fastdowload.core.model.DownloadBlock

/**
 * 决定分块数量，并生成从 0 覆盖到文件末尾的分块。
 *
 * 数量先看 [hint]：调用方给出连接数时用它，上限是 [Defaults.MAX_BLOCKS_CAP]。
 * 没有给出时按长度分档，并且不超过 [Defaults.MAX_BLOCKS_PER_TASK]。
 * 不能随机写入时只有 1 块。余数并入第 0 块，避免最后一块越界或漏字节。
 */
public object BlockSplitter {
    /**
     * 计算应该使用的分块数。
     *
     * 长度未知或小于等于 0 时返回 1。分块数不会大于 [totalBytes]，因此不会出现空的尾块。
     *
     * @param totalBytes 资源总字节数。未知时为 `-1`。
     * @param hint 调用方希望的连接数。null 表示按长度分档。
     * @param supportSeek 目标是否能按绝对偏移写入。
     * @return 至少为 1 的分块数。
     */
    public fun chooseBlockCount(totalBytes: Long, hint: Int?, supportSeek: Boolean): Int {
        if (totalBytes <= 0L || !supportSeek) return 1
        val adaptive = when {
            totalBytes < ONE_MIB -> 1
            totalBytes < FIVE_MIB -> 2
            totalBytes < FIFTY_MIB -> 3
            totalBytes < HUNDRED_MIB -> 4
            else -> Defaults.MAX_BLOCKS_PER_TASK
        }
        val chosen = if (hint != null) {
            hint.coerceIn(1, Defaults.MAX_BLOCKS_CAP)
        } else {
            adaptive.coerceAtMost(Defaults.MAX_BLOCKS_PER_TASK)
        }
        return chosen.toLong().coerceAtMost(totalBytes).toInt()
    }

    /**
     * 生成覆盖 `[0, totalBytes)` 的分块，已写偏移都是 0。
     *
     * 第 0 块的长度是 `total / count + total % count`，其余块是 `total / count`。
     *
     * @param recordId 所属任务标识，不能为空白。
     * @param totalBytes 资源总字节数，不能为负。0 得到一个已经完成的空块。
     * @param hint 调用方希望的连接数。null 表示按长度分档。
     * @param supportSeek 目标是否能按绝对偏移写入。
     * @return 按序号从 0 排好的分块。长度之和等于 [totalBytes]。
     * @throws IllegalArgumentException [totalBytes] 为负，或 [recordId] 不能用来构造分块。
     */
    public fun split(
        recordId: String,
        totalBytes: Long,
        hint: Int?,
        supportSeek: Boolean,
    ): List<DownloadBlock> {
        require(totalBytes >= 0L) { "totalBytes 不能为负" }
        val count = chooseBlockCount(totalBytes, hint, supportSeek)
        val each = totalBytes / count
        val remainder = totalBytes % count
        var start = 0L
        return List(count) { index ->
            val length = if (index == 0) each + remainder else each
            val block = DownloadBlock(
                recordId = recordId,
                index = index,
                startOffset = start,
                contentLength = length,
            )
            start += length
            block
        }
    }

    private const val ONE_MIB: Long = 1024L * 1024L
    private const val FIVE_MIB: Long = 5L * ONE_MIB
    private const val FIFTY_MIB: Long = 50L * ONE_MIB
    private const val HUNDRED_MIB: Long = 100L * ONE_MIB
}
