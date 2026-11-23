/**
 * 带内容地址或系统删除请求的公开类型。
 *
 * @file
 */
package com.arturoyi.albumcleaner

import android.content.Intent
import android.content.IntentSender
import android.net.Uri

/**
 * 一张建议删除的照片。
 *
 * @property assetId 媒体库 `_id`。
 * @property uri 当前可读的内容地址。
 * @property groupId 它所属的组。
 * @property confidence 这次判断有多把握。
 * @property reasonCodes 推荐删除的原因，靠前的更主要。
 * @property estimatedBytes 删掉大约能释放的字节数。
 * @property keepAssetId 这一组建议留下的照片。
 * @property protectionReasons 它同时命中的保护原因。为空表示可以删除。
 */
public data class CleanCandidate(
    public val assetId: Long,
    public val uri: Uri,
    public val groupId: String,
    public val confidence: ConfidenceLevel,
    public val reasonCodes: List<ReasonCode>,
    public val estimatedBytes: Long,
    public val keepAssetId: Long,
    public val protectionReasons: List<ProtectionReason>,
)

/**
 * 交给宿主去启动的系统删除或恢复请求。
 *
 * [intentSender] 为 null 时，系统不会再弹确认框。API 26–28 就是这样，删除不可恢复。
 * 这时 [requiresExplicitWarning] 为 true，宿主必须在自己的界面上写清楚。
 *
 * @property intentSender 系统确认用的发送器。不需要系统确认时为 null。
 * @property assetIds 这一次涉及的照片。
 * @property requiresExplicitWarning 为 true 时，宿主要提示用户删除后找不回来。
 */
public data class DeleteRequest(
    public val intentSender: IntentSender?,
    public val assetIds: List<Long>,
    public val requiresExplicitWarning: Boolean,
)

/**
 * 宿主从系统确认框带回的结果。
 *
 * @property resultCode 系统 `Activity` 结果码。
 * @property data 系统带回的 Intent。没有时为 null。
 * @property request 对应的 [DeleteRequest]。
 */
public data class DeleteResult(
    public val resultCode: Int,
    public val data: Intent?,
    public val request: DeleteRequest,
)

/**
 * 回收站里的一条记录。
 *
 * @property actionId 对应的删除动作。
 * @property assetId 被移走的照片。
 * @property uriSnapshot 删除时的内容地址。已经失效时为 null。
 * @property expiresAt 过期时间，Unix 毫秒。
 * @property restored 是否已经恢复。
 */
public data class RecycleEntry(
    public val actionId: String,
    public val assetId: Long,
    public val uriSnapshot: Uri?,
    public val expiresAt: Long,
    public val restored: Boolean,
)
