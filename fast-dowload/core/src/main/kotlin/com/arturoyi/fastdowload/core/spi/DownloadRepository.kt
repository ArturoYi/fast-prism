/**
 * 任务存储的契约。内存实现和 Room 实现都按这些方法工作。
 *
 * 方法是阻塞的，不要在界面线程调用。状态迁移走 [transition]，由命令线程串行执行。
 *
 * @file
 */
package com.arturoyi.fastdowload.core.spi

import com.arturoyi.fastdowload.core.error.DownloadError
import com.arturoyi.fastdowload.core.model.ContentFingerprint
import com.arturoyi.fastdowload.core.model.DownloadBlock
import com.arturoyi.fastdowload.core.model.DownloadRecord
import com.arturoyi.fastdowload.core.model.DownloadRequest
import com.arturoyi.fastdowload.core.state.SanitizeReport
import com.arturoyi.fastdowload.core.state.Status
import java.io.File

/**
 * 按状态筛选任务。
 *
 * 状态集合为空时不筛选。
 *
 * @property statuses 要保留的状态。
 */
public data class DownloadFilter(
    public val statuses: Set<Status> = emptySet(),
)

/**
 * 任务和断点的唯一读写入口。
 */
public interface DownloadRepository {
    /**
     * 插入或整体替换一条任务。
     *
     * 这是登记和修复用的，不检查状态白名单。日常迁移用 [transition]。
     *
     * @param record 要保存的任务。
     */
    public fun upsert(record: DownloadRecord)

    /**
     * 按标识读取任务。
     *
     * @param publicId 任务标识。
     * @return 没有这条任务时为 null。
     */
    public fun get(publicId: String): DownloadRecord?

    /**
     * 按筛选条件列出任务，并按优先级从高到低、创建时间从早到晚排序。
     *
     * @param filter 状态筛选。默认包含全部状态。
     * @return 排好序的任务。
     */
    public fun query(filter: DownloadFilter = DownloadFilter()): List<DownloadRecord>

    /**
     * 取出已有任务；没有则登记为排队。
     *
     * @param request 下载请求。
     * @return 已有或新建的任务。
     */
    public fun open(request: DownloadRequest): DownloadRecord

    /**
     * 替换全部分块。
     *
     * @param publicId 任务标识。
     * @param blocks 新的分块。
     */
    public fun replaceBlocks(publicId: String, blocks: List<DownloadBlock>)

    /**
     * 读取分块副本。
     *
     * @param publicId 任务标识。
     * @return 没有分块时为空列表。
     */
    public fun blocksOf(publicId: String): List<DownloadBlock>

    /**
     * 在文件同步成功之后增加一个分块的偏移。
     *
     * 只有写入守卫可以调用。
     *
     * @param publicId 任务标识。
     * @param blockIndex 分块序号。
     * @param delta 新同步的字节数，必须大于 0。
     */
    public fun onFlushSuccess(publicId: String, blockIndex: Int, delta: Long)

    /**
     * 保存试连指纹和资源总长度。
     *
     * @param publicId 任务标识。
     * @param fingerprint 本次试连得到的指纹。
     */
    public fun saveFingerprint(publicId: String, fingerprint: ContentFingerprint)

    /**
     * 各分块已写字节之和。
     *
     * @param publicId 任务标识。
     * @return 没有分块时为 0。
     */
    public fun downloaded(publicId: String): Long

    /**
     * 标记文件在完成前提交前可能不完整。
     *
     * @param publicId 任务标识。
     */
    public fun markDirty(publicId: String)

    /**
     * 去掉脏标记。
     *
     * @param publicId 任务标识。
     */
    public fun clearDirty(publicId: String)

    /**
     * 是否标成脏文件。
     *
     * @param publicId 任务标识。
     * @return 需要保守重下时为 true。
     */
    public fun isDirty(publicId: String): Boolean

    /**
     * 清掉分块和指纹，并把已写字节归零。
     *
     * @param publicId 任务标识。
     */
    public fun clearBlocks(publicId: String)

    /**
     * 按白名单修改状态。
     *
     * 不合法时抛出异常，不改存储。
     *
     * @param publicId 任务标识。
     * @param to 目标状态。
     * @param error 进入失败状态时附带的错误。其他状态会清掉错误。
     * @return 更新后的任务。
     */
    public fun transition(publicId: String, to: Status, error: DownloadError? = null): DownloadRecord

    /**
     * 启动时修正残留任务，并删掉需要丢弃的临时文件。
     *
     * @param storageRoot 应用内部存储根目录。
     * @return 这次改了多少条任务。
     */
    public fun sanitizeOnStartup(storageRoot: File): SanitizeReport

    /**
     * 在一个存储事务里运行 [block]。
     *
     * 内存实现用同一把锁包住。Room 实现用数据库事务。
     *
     * @param block 要一起成功或一起失败的写入。
     * @return [block] 的结果。
     */
    public fun <T> transaction(block: () -> T): T

    /**
     * 每次成功提交一段偏移后调用 [observer]。
     *
     * 用来统计存储写入次数。传 null 表示不再通知。默认实现忽略，旧的仓库实现不用改。
     *
     * @param observer 写入完成后的回调。可能在存储锁里面调用，不要再重入同一把锁之外的慢操作。
     */
    public fun setFlushObserver(observer: (() -> Unit)?) {}
}
