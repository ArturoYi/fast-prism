/**
 * 读取相册所需的运行时权限。
 *
 * 和 SDK 的判断一致：Android 14 只授权部分照片时也算可以读。
 *
 * @file
 */
package com.arturoyi.albumcleaner.sample

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat

/** 当前系统版本上要申请的那一个读取权限。 */
internal fun albumReadPermission(): String {
    return if (Build.VERSION.SDK_INT >= 33) {
        Manifest.permission.READ_MEDIA_IMAGES
    } else {
        Manifest.permission.READ_EXTERNAL_STORAGE
    }
}

/** 已经能读至少一部分照片。 */
internal fun Context.hasAlbumPermission(): Boolean {
    if (granted(albumReadPermission())) return true
    return Build.VERSION.SDK_INT >= 34 &&
        granted(Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED)
}

private fun Context.granted(permission: String): Boolean {
    return ContextCompat.checkSelfPermission(this, permission) == PackageManager.PERMISSION_GRANTED
}
