/**
 * 在任务自己的网络要求之外，再加一条全局要求。
 *
 * @file
 */
package com.arturoyi.fastdowload.engine.schedule

import com.arturoyi.fastdowload.core.model.NetworkType

/**
 * 全局网络和任务网络都允许时才放行。
 *
 * [required] 为 null 时只看任务自己的 [NetworkType]。
 *
 * @param inner 读取当前网络的门。
 */
public class AdjustableNetworkGate(
    private val inner: NetworkGate,
) : NetworkGate {
    /** 调用方额外要求的网络。null 表示不额外限制。 */
    @Volatile
    public var required: NetworkType? = null

    /**
     * 当前是否允许 [type] 这种下载。
     *
     * @param type 任务要求的网络。
     * @return 全局要求和任务要求都满足时为 true。
     */
    public override fun isAllowed(type: NetworkType): Boolean {
        val global = required
        if (global != null && !inner.isAllowed(global)) return false
        return inner.isAllowed(type)
    }
}
