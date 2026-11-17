/**
 * 决定一条任务切成几块，以及每块的字节区间。
 *
 * 引擎只要求切出来的块从 0 无缝覆盖到文件末尾。具体用几块由实现决定。
 *
 * @file
 */
package com.arturoyi.fastdownload.core.spi

import com.arturoyi.fastdownload.core.model.DownloadBlock

/**
 * 分块策略。
 *
 * 实现要能被多条任务同时调用。
 */
public interface BreakpointStrategy {
    /**
     * 计算分块数。
     *
     * @param totalBytes 资源总字节数。未知或不是正数时应当返回 1。
     * @param hint 调用方希望的连接数。null 表示由策略自己决定。
     * @param supportSeek 目标能否按绝对偏移写入。不能时应当返回 1。
     * @return 至少为 1。
     */
    public fun blockCount(totalBytes: Long, hint: Int?, supportSeek: Boolean): Int

    /**
     * 生成覆盖 `[0, totalBytes)` 的分块。
     *
     * 块与块之间不能重叠，也不能留下空隙。长度之和必须等于 [totalBytes]。
     *
     * @param recordId 任务标识。
     * @param totalBytes 资源总字节数，不能为负。
     * @param hint 调用方希望的连接数。
     * @param supportSeek 目标能否按绝对偏移写入。
     * @return 按序号从 0 排列的分块。已写偏移都是 0。
     */
    public fun split(
        recordId: String,
        totalBytes: Long,
        hint: Int?,
        supportSeek: Boolean,
    ): List<DownloadBlock>
}
