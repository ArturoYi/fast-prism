/**
 * 相册清理的公开入口。
 *
 * @file
 */
package com.arturoyi.albumcleaner

import android.graphics.Bitmap
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

/**
 * 相册清理的唯一入口。没有界面，也不联网。
 *
 * 宿主自己申请读取权限，自己画列表，并决定在哪个协程或前台服务里调用。
 * [scan] 先把媒体库元数据写入索引。同大小的文件再读头尾做局部哈希，局部哈希撞上才读全文。
 * 图片用一张 64px 缩略图同时算出感知哈希、dHash 和模糊分。视频不解码。
 * 扫描结束前按这些哈希分组，选出建议留下的那张，其余可删的写成候选。
 * 宿主确认后可以删除。Android 11 及以上删除可以恢复。
 * `observeChanges` 还没有实现。
 */
public interface AlbumCleaner {
    /**
     * 准备索引。只能在 [CleanerState.UNINITIALIZED] 时调用。
     *
     * @param config 阈值、保护和分页。
     * @throws IllegalArgumentException [CleanerConfig.pageSize] 或 [CleanerConfig.maxConcurrency] 不是正数。
     * @throws CleanerException [ErrorCode.INVALID_STATE] 或 [ErrorCode.SDK_RELEASED]。
     */
    public fun initialize(config: CleanerConfig = CleanerConfig())

    /**
     * 扫描相册并刷新候选。
     *
     * 冷流。收集才开始扫描，并进入 [CleanerState.SCANNING]。
     * 扫完、取消或收集被取消后回到 [CleanerState.READY]，已经写入的行保留。
     * 上一轮被取消时从断点继续，断点之前的元数据不会重写。指纹没变的行不会重算。
     * 分组在最后一次写入。取消发生在这之前时，原来的组还在。
     * 上一轮已经完成时从头再读元数据。
     *
     * 计算在后台线程。进度在收集方所在的协程交出，界面要在主线程收集。
     *
     * @return 每个阶段的进度。
     * @throws CleanerException 状态不对、没有读取权限，或收集过程中扫描被取消。
     */
    public fun scan(): Flow<ScanProgress>

    /**
     * 取消正在进行的扫描，并留下断点。
     *
     * @throws CleanerException 状态不对。
     */
    public fun cancelScan()

    /**
     * 观察媒体库变化。
     *
     * 只在有人收集时注册，取消收集后解除注册。收到事件后不会自己再扫描。
     *
     * @return 去抖之后的变化。
     * @throws CleanerException 状态不对。
     */
    public fun observeChanges(): Flow<ChangeEvent>

    /**
     * 按页读取删除候选。
     *
     * @param query 过滤和游标。
     * @return 这一页候选。
     * @throws CleanerException 状态不对。
     */
    public fun candidates(query: CandidateQuery): PagedResult<CleanCandidate>

    /**
     * 按页读取分组。
     *
     * @param query 过滤和游标。
     * @return 这一页分组。
     * @throws CleanerException 状态不对。
     */
    public fun groups(query: GroupQuery): PagedResult<CleanGroup>

    /**
     * 读取一张照片的缩略图。
     *
     * @param assetId 媒体库 `_id`。
     * @param sizeDp 期望边长，单位 dp。
     * @return 解码后的位图。照片已经不在时返回 null。
     * @throws CleanerException 状态不对。
     */
    public suspend fun thumbnail(assetId: Long, sizeDp: Int = 256): Bitmap?

    /**
     * 为这批照片准备系统删除请求。
     *
     * 不会改本地索引。宿主启动 [DeleteRequest.intentSender] 后，把结果交给 [commitDeletion]。
     * 发送器为 null 时没有系统确认框，宿主先看 [DeleteRequest.requiresExplicitWarning]。
     *
     * @param assetIds 要删除的照片。
     * @return 给宿主启动的请求。
     * @throws CleanerException 状态不对。
     */
    public fun requestDeleteIntent(assetIds: List<Long>): DeleteRequest

    /**
     * 系统确认结束后，再改本地索引。
     *
     * 用户取消时本地什么都不改。
     *
     * @param result 宿主从系统确认框带回的结果。
     * @return 这一批的处理进度。
     * @throws CleanerException 状态不对，或用户取消。
     */
    public fun commitDeletion(result: DeleteResult): Flow<ApplyProgress>

    /**
     * 为一次可恢复的删除准备系统恢复请求。
     *
     * API 30 及以上要从系统回收站恢复，宿主需要再启动一次 [DeleteRequest.intentSender]。
     * 不可恢复的删除会抛出 [ErrorCode.DELETE_UNSUPPORTED_ON_API]。
     *
     * @param actionId 当初删除时记下的动作。
     * @return 给宿主启动的恢复请求。
     * @throws CleanerException 状态不对，或这次删除不能恢复。
     */
    public fun requestRestoreIntent(actionId: String): DeleteRequest

    /**
     * 系统恢复确认结束后，更新本地回收站记录。
     *
     * @param result 宿主从系统确认框带回的结果。
     * @return 成功，或带着 [CleanerException] 的失败。
     * @throws CleanerException 状态不对。
     */
    public fun commitRestore(result: DeleteResult): Result<Unit>

    /**
     * 按页读取回收站。
     *
     * @param query 游标和页大小。
     * @return 这一页记录。
     * @throws CleanerException 状态不对。
     */
    public fun recycleBin(query: PageQuery): PagedResult<RecycleEntry>

    /**
     * 读取当前的空间估算。
     *
     * @return 预计可释放和已经释放的字节数。
     * @throws CleanerException 状态不对。
     */
    public fun spaceReport(): SpaceReport

    /**
     * 当前状态。释放之后仍然可以读。
     *
     * @return 后续状态都会发到这个流里，收集时先收到当前值。
     */
    public fun state(): StateFlow<CleanerState>

    /**
     * 释放索引、监听和正在跑的任务。
     *
     * 进入 [CleanerState.RELEASED] 之后不能再 [initialize]。重复调用保持终态。
     */
    public fun release()
}
