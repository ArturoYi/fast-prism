/**
 * 演示页。底部三个页签：下载、文件、设置。
 *
 * 开始下载时请求通知权限。用户拒绝过就不再问。
 * 状态栏跟着主题走，默认是深色。
 *
 * @file
 */
package com.arturoyi.fastdownload.sample

import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.arturoyi.fastdownload.sample.futures.home.DemoScreen
import com.arturoyi.fastdownload.sample.settings.AppTheme
import com.arturoyi.fastdownload.sample.theme.SampleTheme
import com.arturoyi.fastdownload.sample.theme.syncSampleBars

/**
 * 用一个 OkHttp 下载器演示入队、查看配置和已完成文件。
 */
class MainActivity : ComponentActivity() {
    /**
     * 画出三个页，并接上通知权限弹窗。
     *
     * @param savedInstanceState 系统保存的页签。任务来自数据库。
     */
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val dark = (application as SampleApp).settings.theme == AppTheme.Dark
        syncSampleBars(dark)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            window.decorView.isForceDarkAllowed = false
        }
        val notifications = NotificationPrompt(applicationContext)
        setContent {
            var permissionRevision by remember { mutableIntStateOf(0) }
            val launcher = rememberLauncherForActivityResult(
                ActivityResultContracts.RequestPermission(),
            ) { granted ->
                notifications.onResult(granted)
            }
            val activity = this@MainActivity
            DisposableEffect(activity) {
                notifications.launch = {
                    launcher.launch(android.Manifest.permission.POST_NOTIFICATIONS)
                }
                notifications.onChanged = { permissionRevision++ }
                val observer = LifecycleEventObserver { _, event ->
                    if (event == Lifecycle.Event.ON_RESUME) notifications.refresh()
                }
                activity.lifecycle.addObserver(observer)
                onDispose {
                    activity.lifecycle.removeObserver(observer)
                    notifications.launch = null
                    notifications.onChanged = null
                }
            }
            SampleTheme {
                DemoScreen(
                    notifications = notifications,
                    permissionRevision = permissionRevision,
                )
            }
        }
    }
}
