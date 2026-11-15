/**
 * 网络快照是否满足任务要求。
 *
 * @file
 */
package com.arturoyi.fastdowload.engine.schedule

import com.arturoyi.fastdowload.core.model.NetworkType
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * [allows] 对无网、Wi-Fi 和不计流量的判断。
 */
class NetworkGateTest {
    /**
     * 没有连接时任何要求都不通过。Wi-Fi 任务还要当前传输是 Wi-Fi。
     */
    @Test
    fun wifiAndDisconnected() {
        val offline = NetworkSnapshot(connected = false, wifi = false, unmetered = false)
        assertFalse(offline.allows(NetworkType.ALL))
        val cellular = NetworkSnapshot(connected = true, wifi = false, unmetered = false)
        assertTrue(cellular.allows(NetworkType.ALL))
        assertFalse(cellular.allows(NetworkType.WIFI_ONLY))
        val wifi = NetworkSnapshot(connected = true, wifi = true, unmetered = true)
        assertTrue(wifi.allows(NetworkType.WIFI_ONLY))
        assertTrue(wifi.allows(NetworkType.UNMETERED))
    }
}
