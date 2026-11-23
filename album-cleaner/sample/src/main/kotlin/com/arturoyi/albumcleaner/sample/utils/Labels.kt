/**
 * 把 SDK 的枚举写成当前语言的短句。
 *
 * 用 [android.content.Context.getString]，这样列表里可以在普通循环中取文案。
 * 语言切换时 [com.arturoyi.albumcleaner.sample.theme.SampleTheme] 会换掉 Context，句子跟着变。
 *
 * @file
 */
package com.arturoyi.albumcleaner.sample.utils

import android.content.Context
import com.arturoyi.albumcleaner.ErrorCode
import com.arturoyi.albumcleaner.GroupType
import com.arturoyi.albumcleaner.ReasonCode
import com.arturoyi.albumcleaner.ScanPhase
import com.arturoyi.albumcleaner.sample.R

/** 推荐删除的原因。 */
internal fun ReasonCode.label(context: Context): String = when (this) {
    ReasonCode.EXACT_DUPLICATE -> context.getString(R.string.reason_exact)
    ReasonCode.NEAR_DUPLICATE -> context.getString(R.string.reason_near)
    ReasonCode.BLURRY -> context.getString(R.string.reason_blurry)
    ReasonCode.TOO_SMALL -> context.getString(R.string.reason_small)
    ReasonCode.OVER_EXPOSED -> context.getString(R.string.reason_over)
    ReasonCode.UNDER_EXPOSED -> context.getString(R.string.reason_under)
    ReasonCode.SCREENSHOT -> context.getString(R.string.reason_screenshot)
    ReasonCode.CACHE_IMAGE -> context.getString(R.string.reason_cache)
    ReasonCode.SEMANTIC_SIMILAR -> context.getString(R.string.reason_semantic)
}

/** 一组照片为什么放在一起。 */
internal fun GroupType.label(context: Context): String = when (this) {
    GroupType.EXACT_DUPLICATE -> context.getString(R.string.group_exact)
    GroupType.NEAR_DUPLICATE -> context.getString(R.string.group_near)
    GroupType.BURST -> context.getString(R.string.group_burst)
    GroupType.LOW_QUALITY -> context.getString(R.string.group_quality)
    GroupType.SCREENSHOT -> context.getString(R.string.group_screenshot)
    GroupType.SEMANTIC -> context.getString(R.string.group_semantic)
}

/** 扫描进行到哪一段。 */
internal fun ScanPhase.label(context: Context): String = when (this) {
    ScanPhase.QUERY -> context.getString(R.string.phase_query)
    ScanPhase.INDEX -> context.getString(R.string.phase_index)
    ScanPhase.FINGERPRINT -> context.getString(R.string.phase_fingerprint)
    ScanPhase.DEDUP -> context.getString(R.string.phase_dedup)
    ScanPhase.SCORE -> context.getString(R.string.phase_score)
    ScanPhase.DONE -> context.getString(R.string.phase_done)
}

/**
 * 失败时给用户看的一句。用户取消删除时返回 null。
 *
 * @param code 失败原因。没有失败时为 null。
 * @param detail SDK 附带的说明。能对上文案时不用它。
 */
internal fun failureText(context: Context, code: ErrorCode?, detail: String?): String? {
    code ?: return null
    return when (code) {
        ErrorCode.PERMISSION_DENIED -> context.getString(R.string.permission_needed)
        ErrorCode.TASK_CANCELLED -> context.getString(R.string.scan_cancelled)
        ErrorCode.INDEX_CORRUPTED -> context.getString(R.string.index_broken)
        ErrorCode.ASSET_GONE -> context.getString(R.string.photo_missing)
        ErrorCode.DELETE_UNSUPPORTED_ON_API -> context.getString(R.string.restore_unavailable)
        ErrorCode.DELETE_REJECTED_BY_USER -> null
        else -> detail?.takeIf { it.isNotBlank() } ?: context.getString(R.string.action_failed)
    }
}
