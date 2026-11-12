/**
 * 分块数量和区间覆盖。
 *
 * @file
 */
package com.arturoyi.fastprism.core.strategy

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import kotlin.random.Random

/**
 * [BlockSplitter] 的档位、余数和大于 2 GiB 的长度。
 */
class BlockSplitterTest {
    /**
     * 小于 1 MiB 是 1 块，刚好 1 MiB 是 2 块，100 MiB 起是 5 块。
     */
    @Test
    fun countFollowsSizeBands() {
        val mib = 1024L * 1024L
        assertEquals(1, BlockSplitter.chooseBlockCount(0, hint = null, supportSeek = true))
        assertEquals(1, BlockSplitter.chooseBlockCount(1, hint = null, supportSeek = true))
        assertEquals(1, BlockSplitter.chooseBlockCount(mib - 1, hint = null, supportSeek = true))
        assertEquals(2, BlockSplitter.chooseBlockCount(mib, hint = null, supportSeek = true))
        assertEquals(2, BlockSplitter.chooseBlockCount(5 * mib - 1, hint = null, supportSeek = true))
        assertEquals(3, BlockSplitter.chooseBlockCount(5 * mib, hint = null, supportSeek = true))
        assertEquals(4, BlockSplitter.chooseBlockCount(100 * mib - 1, hint = null, supportSeek = true))
        assertEquals(5, BlockSplitter.chooseBlockCount(100 * mib, hint = null, supportSeek = true))
    }

    /**
     * 调用方给出的连接数优先，但不能随机写入时只有 1 块。
     */
    @Test
    fun hintAndSeekOverrideBands() {
        val total = 100L * 1024L * 1024L
        assertEquals(8, BlockSplitter.chooseBlockCount(total, hint = 8, supportSeek = true))
        assertEquals(1, BlockSplitter.chooseBlockCount(total, hint = 8, supportSeek = false))
    }

    /**
     * 余数进第 0 块，各块首尾相接，长度之和等于文件长度。
     */
    @Test
    fun remainderGoesToTheFirstBlockAndCoversTheFile() {
        val blocks = BlockSplitter.split("task", totalBytes = 10, hint = 3, supportSeek = true)
        assertEquals(listOf(4L, 3L, 3L), blocks.map { it.contentLength })
        assertEquals(0L, blocks[0].startOffset)
        assertEquals(4L, blocks[1].startOffset)
        assertEquals(7L, blocks[2].startOffset)
        assertEquals(10L, blocks.sumOf { it.contentLength })
    }

    /**
     * 超过 2 GiB 时偏移仍是非负 Long。
     */
    @Test
    fun largeFileDoesNotOverflow() {
        val total = 2_500_000_000L
        val blocks = BlockSplitter.split("task", total, hint = null, supportSeek = true)
        assertEquals(5, blocks.size)
        assertTrue(blocks.all { it.startOffset >= 0L && it.contentLength > 0L })
        assertEquals(total, blocks.sumOf { it.contentLength })
        assertEquals(total - 1, blocks.last().rangeRight)
    }

    /**
     * 随机长度下，分块不重叠并覆盖到最后一个字节。
     */
    @Test
    fun randomLengthsStayContiguous() {
        val random = Random(20261112)
        repeat(30) {
            val total = random.nextLong(1L, 5_000_000L)
            val hint = random.nextInt(1, 9)
            val blocks = BlockSplitter.split("task", total, hint, supportSeek = true)
            var cursor = 0L
            blocks.forEach { block ->
                assertEquals(cursor, block.startOffset)
                cursor += block.contentLength
            }
            assertEquals(total, cursor)
        }
    }
}
