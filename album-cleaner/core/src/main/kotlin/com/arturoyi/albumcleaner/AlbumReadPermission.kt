/**
 * 读取相册所需的运行时权限。
 *
 * @file
 */
package com.arturoyi.albumcleaner

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat

/**
 * 没有读取权限时抛出 [ErrorCode.PERMISSION_DENIED]。
 *
 * Android 14 只授权部分照片时也允许继续，媒体库只会返回已授权的子集。
 *
 * @param context 用来检查权限。
 */
internal fun ensureAlbumReadPermission(context: Context) {
    ensureAlbumReadPermission(Build.VERSION.SDK_INT) { permission ->
        ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED
    }
}

/**
 * 按系统版本判断读取权限是否够用。
 *
 * @param sdkInt [android.os.Build.VERSION.SDK_INT]。
 * @param granted 某个权限是否已经授予。
 */
internal fun ensureAlbumReadPermission(sdkInt: Int, granted: (String) -> Boolean) {
    val allowed = when {
        sdkInt >= 34 -> {
            granted(Manifest.permission.READ_MEDIA_IMAGES) ||
                granted(Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED)
        }
        sdkInt >= 33 -> granted(Manifest.permission.READ_MEDIA_IMAGES)
        else -> granted(Manifest.permission.READ_EXTERNAL_STORAGE)
    }
    if (!allowed) {
        throw CleanerException(ErrorCode.PERMISSION_DENIED, "没有读取相册的权限")
    }
}
