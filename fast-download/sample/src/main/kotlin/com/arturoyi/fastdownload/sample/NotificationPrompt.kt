/**
 * 开始下载时请求通知权限。用户拒绝过就不再问。
 *
 * @file
 */
package com.arturoyi.fastdownload.sample

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat
import com.arturoyi.fastdownload.sample.R
import com.arturoyi.fastdownload.sample.settings.SAMPLE_PREFS

/**
 * 记住通知权限是否已经被拒绝。
 *
 * 允许之后不再弹出。系统版本低于 33 时不需要这条权限。
 *
 * @param context 用来读取本应用的偏好。会使用 applicationContext。
 */
internal class NotificationPrompt(
    context: Context,
) {
    private val appContext = context.applicationContext
    private val prefs = appContext.getSharedPreferences(SAMPLE_PREFS, Context.MODE_PRIVATE)
    private var asking = false

    /** 由页面接上系统权限弹窗。还不能询问时不会调用。 */
    var launch: (() -> Unit)? = null

    /** 权限说明变化时通知界面刷新。 */
    var onChanged: (() -> Unit)? = null

    /**
     * 这次下载需要通知时，弹出一次系统请求。
     *
     * 已经允许、已经拒绝，或正在询问时直接返回。
     */
    fun askIfNeeded() {
        if (!canAsk()) return
        val start = launch ?: return
        asking = true
        start()
    }

    /**
     * 记下用户刚做出的选择。
     *
     * @param granted 系统是否给了通知权限。
     */
    fun onResult(granted: Boolean) {
        asking = false
        if (!granted) prefs.edit().putBoolean(KEY_DENIED, true).apply()
        onChanged?.invoke()
    }

    /** 从系统设置回来时，重新读一次当前权限。 */
    fun refresh() {
        onChanged?.invoke()
    }

    /**
     * 配置页上的短句。
     *
     * @param context 用来取当前语言的文案。
     * @return 当前权限状态。
     */
    fun label(context: Context): String {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
            return context.getString(R.string.notify_not_required)
        }
        if (isGranted()) return context.getString(R.string.notify_granted)
        if (prefs.getBoolean(KEY_DENIED, false)) return context.getString(R.string.notify_denied)
        return context.getString(R.string.notify_will_ask)
    }

    private fun canAsk(): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return false
        if (isGranted()) return false
        if (asking) return false
        return !prefs.getBoolean(KEY_DENIED, false)
    }

    private fun isGranted(): Boolean {
        return ContextCompat.checkSelfPermission(appContext, Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED
    }

    private companion object {
        const val KEY_DENIED: String = "notification_permission_denied"
    }
}
