/**
 * 相册清理。底部三个页签：清理、分组、设置。
 *
 * 清理器在进程启动时已经初始化。这里不扫描。
 * 用户点开始时才申请读取权限，允许之后才开始第一次扫描。
 *
 * @file
 */
package com.arturoyi.albumcleaner.sample

import android.app.Activity
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.arturoyi.albumcleaner.sample.futures.home.HomeScreen
import com.arturoyi.albumcleaner.sample.settings.AppTheme
import com.arturoyi.albumcleaner.sample.theme.SampleTheme
import com.arturoyi.albumcleaner.sample.theme.syncSampleBars

/**
 * 画出三个页，并接上读取权限和系统删除确认。
 */
class MainActivity : ComponentActivity() {
    /**
     * 接上权限和删除回调。
     *
     * 不在这里扫描。权限变化只更新界面上的那一行。
     *
     * @param savedInstanceState 系统保存的页签。扫描结果来自索引。
     */
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val app = application as SampleApp
        val dark = app.settings.theme == AppTheme.Dark
        syncSampleBars(dark)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            window.decorView.isForceDarkAllowed = false
        }
        val store = app.album
        setContent {
            val activity = this@MainActivity
            var granted by remember { mutableStateOf(activity.hasAlbumPermission()) }
            var scanAfterGrant by remember { mutableStateOf(false) }
            val permissions = rememberLauncherForActivityResult(
                ActivityResultContracts.RequestPermission(),
            ) { _ ->
                val allowed = activity.hasAlbumPermission()
                granted = allowed
                if (allowed && scanAfterGrant) store.startScan()
                scanAfterGrant = false
            }
            val sender = rememberLauncherForActivityResult(
                ActivityResultContracts.StartIntentSenderForResult(),
            ) { result ->
                store.finishSystemRequest(result.resultCode, result.data)
            }
            val ticket = store.launchTicket
            LaunchedEffect(ticket) {
                val pending = store.takeSender() ?: return@LaunchedEffect
                val request = IntentSenderRequest.Builder(pending).build()
                try {
                    sender.launch(request)
                } catch (_: Exception) {
                    store.finishSystemRequest(Activity.RESULT_CANCELED, null)
                }
            }
            DisposableEffect(activity) {
                val observer = LifecycleEventObserver { _, event ->
                    if (event == Lifecycle.Event.ON_RESUME) {
                        granted = activity.hasAlbumPermission()
                    }
                }
                activity.lifecycle.addObserver(observer)
                onDispose { activity.lifecycle.removeObserver(observer) }
            }
            SampleTheme {
                HomeScreen(
                    permissionGranted = granted,
                    onStartScan = {
                        if (activity.hasAlbumPermission()) {
                            granted = true
                            store.startScan()
                        } else {
                            scanAfterGrant = true
                            permissions.launch(albumReadPermission())
                        }
                    },
                    onRequestPermission = {
                        scanAfterGrant = false
                        permissions.launch(albumReadPermission())
                    },
                )
            }
        }
    }
}
