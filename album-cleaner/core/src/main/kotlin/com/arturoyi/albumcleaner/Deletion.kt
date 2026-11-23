/**
 * 系统删除确认，以及确认之后改索引。
 *
 * @file
 */
package com.arturoyi.albumcleaner

import android.app.Activity
import android.content.ContentResolver
import android.content.Context
import android.content.IntentSender
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import com.arturoyi.albumcleaner.storage.ActionRecord
import com.arturoyi.albumcleaner.storage.AlbumIndex
import com.arturoyi.albumcleaner.storage.RecycleRecord

/**
 * 向系统要删除或恢复用的发送器，低版本则直接删。
 */
internal interface DeleteGateway {
    /**
     * @param trash 为 true 时放进系统回收站，为 false 时从回收站恢复。
     */
    fun createSystemRequest(uris: List<Uri>, trash: Boolean): IntentSender

    /** API 26–28 没有系统确认框，直接删掉这些地址。 */
    fun deleteDirect(uris: List<Uri>)
}

/**
 * 用 MediaStore 的删除和回收站请求。
 *
 * @param context 任意上下文，内部会换成应用上下文。
 */
internal class MediaStoreDeletes(
    context: Context,
) : DeleteGateway {
    private val resolver: ContentResolver = context.applicationContext.contentResolver

    override fun createSystemRequest(uris: List<Uri>, trash: Boolean): IntentSender {
        val pending = if (Build.VERSION.SDK_INT >= 30) {
            MediaStore.createTrashRequest(resolver, uris, trash)
        } else {
            MediaStore.createDeleteRequest(resolver, uris)
        }
        return pending.intentSender
    }

    override fun deleteDirect(uris: List<Uri>) {
        uris.forEach { uri -> resolver.delete(uri, null, null) }
    }
}

/**
 * 准备删除请求，或在用户确认后改索引。
 *
 * @param index 已经打开的索引。
 * @param sdkInt 系统版本，用来决定有没有确认框。
 * @param gateway 系统删除。
 * @param now 当前时间，Unix 毫秒。
 */
internal class Deletion(
    private val index: AlbumIndex,
    private val sdkInt: Int,
    private val gateway: DeleteGateway,
    private val now: () -> Long = { System.currentTimeMillis() },
) {
    /**
     * 准备一次删除。还不会改索引。
     *
     * @param assetIds 要删除的照片。
     */
    fun request(assetIds: List<Long>): DeleteRequest {
        val ids = assetIds.distinct()
        require(ids.isNotEmpty()) { "至少要有一张照片" }
        val uris = ids.map { id -> uriOf(id) }
        val recoverable = sdkInt >= 30
        val sender = if (sdkInt >= 29) gateway.createSystemRequest(uris, trash = true) else null
        requests[requestKey(ids)] = recoverable
        return DeleteRequest(
            intentSender = sender,
            assetIds = ids,
            requiresExplicitWarning = sender == null,
        )
    }

    /**
     * 用户确认后更新索引。取消时什么都不改。
     */
    fun commit(result: DeleteResult): ApplyProgress {
        if (result.resultCode != Activity.RESULT_OK) {
            throw CleanerException(ErrorCode.DELETE_REJECTED_BY_USER, "用户取消了删除")
        }
        val ids = result.request.assetIds.distinct()
        val recoverable = requests[requestKey(ids)] ?: (sdkInt >= 30)
        if (sdkInt < 29) {
            gateway.deleteDirect(ids.map { id -> uriOf(id) })
        }
        val createdAt = now()
        var succeeded = 0
        var released = 0L
        val removed = mutableListOf<Pair<Long, String>>()
        ids.forEach { id ->
            val asset = index.asset(id)
            if (asset == null) return@forEach
            released += asset.sizeBytes
            removed += id to asset.uri
            index.detachFromGroups(id)
            index.deleteAsset(id)
            succeeded += 1
        }
        if (succeeded > 0) {
            val actionId = "delete:$createdAt:${ids.first()}"
            index.saveAction(
                ActionRecord(
                    actionId = actionId,
                    type = "DELETE",
                    assetIdsJson = ids.joinToString(prefix = "[", postfix = "]"),
                    status = "APPLIED",
                    createdAt = createdAt,
                    recoverable = recoverable,
                ),
            )
            if (recoverable) {
                val expires = createdAt + RETENTION_MS
                removed.forEach { (id, uri) ->
                    index.saveRecycle(
                        RecycleRecord(
                            actionId = actionId,
                            assetId = id,
                            uriSnapshot = uri,
                            thumbPath = null,
                            expiresAt = expires,
                            restored = false,
                        ),
                    )
                }
            }
            index.addAppliedBytes(released)
        }
        return ApplyProgress(succeeded = succeeded, failed = ids.size - succeeded, total = ids.size)
    }

    /**
     * 为可恢复的删除准备系统恢复请求。
     */
    fun restoreRequest(actionId: String): DeleteRequest {
        val action = index.action(actionId) ?: throw CleanerException(ErrorCode.ASSET_GONE, "没有这次删除")
        if (!action.recoverable || sdkInt < 30) {
            throw CleanerException(ErrorCode.DELETE_UNSUPPORTED_ON_API, "这次删除不能恢复")
        }
        val rows = index.recyclesFor(actionId).filter { row -> !row.restored }
        val uris = rows.mapNotNull { row -> row.uriSnapshot?.let { snapshot -> Uri.parse(snapshot) } }
        if (uris.isEmpty()) throw CleanerException(ErrorCode.ASSET_GONE, "没有可恢复的照片")
        val sender = gateway.createSystemRequest(uris, trash = false)
        restores[requestKey(rows.map { row -> row.assetId })] = actionId
        return DeleteRequest(
            intentSender = sender,
            assetIds = rows.map { row -> row.assetId },
            requiresExplicitWarning = false,
        )
    }

    /**
     * 系统恢复确认结束后，记下回收站状态。
     */
    fun commitRestore(result: DeleteResult) {
        if (result.resultCode != Activity.RESULT_OK) {
            throw CleanerException(ErrorCode.DELETE_REJECTED_BY_USER, "用户取消了恢复")
        }
        val actionId = restores[requestKey(result.request.assetIds)]
            ?: throw CleanerException(ErrorCode.ASSET_GONE, "没有这次删除")
        val action = index.action(actionId) ?: throw CleanerException(ErrorCode.ASSET_GONE, "没有这次删除")
        if (!action.recoverable) {
            throw CleanerException(ErrorCode.DELETE_UNSUPPORTED_ON_API, "这次删除不能恢复")
        }
        index.recyclesFor(action.actionId).forEach { row ->
            index.saveRecycle(row.copy(restored = true))
        }
        index.saveAction(action.copy(status = "RESTORED"))
    }

    private fun uriOf(assetId: Long): Uri {
        val asset = index.asset(assetId) ?: throw CleanerException(ErrorCode.ASSET_GONE, "照片已经不在索引里")
        return Uri.parse(asset.uri)
    }

    private fun requestKey(ids: List<Long>): String = ids.sorted().joinToString(",")

    private val requests = HashMap<String, Boolean>()
    private val restores = HashMap<String, String>()

    private companion object {
        const val RETENTION_MS: Long = 30L * 86_400_000L
    }
}
