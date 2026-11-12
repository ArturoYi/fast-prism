/**
 * 一条下载任务中的连续字节分块。
 *
 * @file
 */
package com.arturoyi.fastprism.core.model

/**
 * 文件中的一段连续字节，以及这段里已经写入的数量。
 *
 * 文件区间是 `[startOffset, startOffset + contentLength)`。[currentOffset] 是这段内部已经写入的字节数，
 * 不是文件中的绝对位置。绝对写入位置是 [rangeLeft]。
 *
 * @property recordId 所属 [com.arturoyi.fastprism.core.model.DownloadRecord.id]，不能为空白。
 * @property index 同一任务内的序号，从 0 开始。
 * @property startOffset 这段在文件中的起点，单位字节，不能为负。
 * @property contentLength 这段的总字节数，不能为负。0 表示空块，并且已经完成。
 * @property currentOffset 这段里已经写入的字节数，范围是 0 到 [contentLength]。
 * @throws IllegalArgumentException 标识为空白，序号或偏移为负，或 [currentOffset] 超出这段长度。
 */
public data class DownloadBlock(
    public val recordId: String,
    public val index: Int,
    public val startOffset: Long,
    public val contentLength: Long,
    public val currentOffset: Long = 0L,
) {
    init {
        require(recordId.isNotBlank()) { "recordId 不能为空" }
        require(index >= 0) { "index 不能为负" }
        require(startOffset >= 0L) { "startOffset 不能为负" }
        require(contentLength >= 0L) { "contentLength 不能为负" }
        require(currentOffset in 0L..contentLength) { "currentOffset 超出本块范围" }
    }

    /**
     * 下一次写入在文件中的绝对偏移。
     *
     * 等于 [startOffset] 加 [currentOffset]。空块或已完成的块指向这段结束之后的位置。
     */
    public val rangeLeft: Long
        get() = startOffset + currentOffset

    /**
     * 这段最后一个字节在文件中的绝对偏移。
     *
     * 等于 `startOffset + contentLength - 1`。空块的结果是 [startOffset] 减 1。
     */
    public val rangeRight: Long
        get() = startOffset + contentLength - 1L

    /**
     * 这段是否已经写满。
     *
     * [currentOffset] 等于 [contentLength] 时为 true。空块一开始就是 true。
     */
    public val isDone: Boolean
        get() = currentOffset == contentLength
}
