/**
 * 用一次网络快照回答能不能下载。
 *
 * @file
 */
package com.arturoyi.fastdowload.engine.schedule

import com.arturoyi.fastdowload.core.model.NetworkType
import com.arturoyi.fastdowload.core.spi.NetworkPolicy

/**
 * 把 [NetworkSnapshot] 当成 [NetworkPolicy]。
 *
 * 判断规则与 [allows] 相同。
 *
 * @param snapshot 一次观察到的网络情况。
 */
public class SnapshotNetworkPolicy(
    private val snapshot: NetworkSnapshot,
) : NetworkPolicy {
    /**
     * 按快照回答 [type]。
     *
     * @param type 任务要求的网络。
     * @return 可以开始时为 true。
     */
    public override fun isAllowed(type: NetworkType): Boolean = snapshot.allows(type)
}
