/**
 * 当前网络是否允许开始下载。
 *
 * @file
 */
package com.arturoyi.fastdowload.core.spi

import com.arturoyi.fastdowload.core.model.NetworkType

/**
 * 按任务要求的网络类型回答能不能下载。
 *
 * 实现要能被调度线程反复调用。
 */
public fun interface NetworkPolicy {
    /**
     * 当前是否允许 [type] 这种下载。
     *
     * 没有连接时返回 false。[NetworkType.ALL] 在已连接时返回 true。
     *
     * @param type 任务要求的网络。
     * @return 可以开始时为 true。
     */
    public fun isAllowed(type: NetworkType): Boolean
}

/**
 * 用一次观察到的连通情况回答 [NetworkPolicy]。
 *
 * @param connected 是否有可用网络。
 * @param wifi 是否走 Wi-Fi。
 * @param unmetered 是否不计流量。
 */
public class FixedNetworkPolicy(
    private val connected: Boolean,
    private val wifi: Boolean,
    private val unmetered: Boolean,
) : NetworkPolicy {
    /**
     * 没有连接时一律不允许。Wi-Fi 和计费限制都在已连接的前提下判断。
     *
     * @param type 任务要求的网络。
     * @return 可以开始时为 true。
     */
    public override fun isAllowed(type: NetworkType): Boolean {
        if (!connected) return false
        return when (type) {
            NetworkType.ALL -> true
            NetworkType.WIFI_ONLY -> wifi
            NetworkType.UNMETERED -> unmetered
        }
    }
}
