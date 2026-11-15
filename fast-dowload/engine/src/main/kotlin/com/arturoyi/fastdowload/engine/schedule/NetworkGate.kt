/**
 * 当前网络是否允许某类下载。
 *
 * @file
 */
package com.arturoyi.fastdowload.engine.schedule

import com.arturoyi.fastdowload.core.model.NetworkType
import com.arturoyi.fastdowload.core.spi.NetworkPolicy

/**
 * 一次观察到的网络情况。
 *
 * @property connected 是否有可用网络。
 * @property wifi 是否走 Wi-Fi。
 * @property unmetered 是否不计流量。
 */
public data class NetworkSnapshot(
    public val connected: Boolean,
    public val wifi: Boolean,
    public val unmetered: Boolean,
)

/**
 * 判断 [type] 在这个快照下能不能下载。
 *
 * 没有连接时一律不允许。Wi-Fi 和计费限制都在已连接的前提下判断。
 *
 * @param type 任务要求的网络。
 * @return 可以开始下载时为 true。
 */
public fun NetworkSnapshot.allows(type: NetworkType): Boolean {
    if (!connected) return false
    return when (type) {
        NetworkType.ALL -> true
        NetworkType.WIFI_ONLY -> wifi
        NetworkType.UNMETERED -> unmetered
    }
}

/**
 * 调度器用来询问当前网络。
 */
public fun interface NetworkGate : NetworkPolicy {
    /**
     * 当前是否允许 [type] 这种下载。
     *
     * @param type 任务的网络要求。
     * @return 允许开始时为 true。
     */
    public override fun isAllowed(type: NetworkType): Boolean
}
