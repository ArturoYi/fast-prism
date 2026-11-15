/**
 * 读取当前网络，供调度器决定要不要等待。
 *
 * @file
 */
package com.arturoyi.fastdowload.android.net

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import com.arturoyi.fastdowload.core.model.NetworkType
import com.arturoyi.fastdowload.engine.schedule.NetworkGate
import com.arturoyi.fastdowload.engine.schedule.NetworkSnapshot
import com.arturoyi.fastdowload.engine.schedule.allows

/**
 * 用系统当前网络能力回答 [NetworkGate]。
 *
 * 没有网络状态权限时视为允许，避免样本在只申请了网络权限时被卡住。
 * [listen] 在默认网络变化时通知调度器。没有这条回调时，没有网络的任务会一直停在等待。
 *
 * @param context 用来取得 [ConnectivityManager]。
 */
public class NetworkMonitor(
    context: Context,
) : NetworkGate, AutoCloseable {
    private val connectivity = context.applicationContext.getSystemService(ConnectivityManager::class.java)
    private var callback: ConnectivityManager.NetworkCallback? = null

    /**
     * 当前网络是否满足 [type]。
     *
     * @param type 任务要求的网络。
     * @return 可以开始下载时为 true。查不到网络状态时也返回 true。
     */
    public override fun isAllowed(type: NetworkType): Boolean {
        val snapshot = try {
            val network = connectivity.activeNetwork ?: return false
            val capabilities = connectivity.getNetworkCapabilities(network) ?: return false
            NetworkSnapshot(
                connected = capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET),
                wifi = capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI),
                unmetered = capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_METERED),
            )
        } catch (_: SecurityException) {
            return true
        }
        return snapshot.allows(type)
    }

    /**
     * 默认网络出现、消失或能力变化时调用 [onChanged]。
     *
     * 重复调用只保留第一次注册。回调在系统线程上，只应往命令队列投递。
     *
     * @param onChanged 网络变化时的动作。
     */
    public fun listen(onChanged: () -> Unit) {
        if (callback != null) return
        val registered = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) = onChanged()

            override fun onLost(network: Network) = onChanged()

            override fun onCapabilitiesChanged(network: Network, networkCapabilities: NetworkCapabilities) {
                onChanged()
            }
        }
        try {
            connectivity.registerDefaultNetworkCallback(registered)
            callback = registered
        } catch (_: RuntimeException) {
            Unit
        }
    }

    /**
     * 注销网络回调。
     */
    public override fun close() {
        val registered = callback ?: return
        callback = null
        try {
            connectivity.unregisterNetworkCallback(registered)
        } catch (_: RuntimeException) {
            Unit
        }
    }
}
