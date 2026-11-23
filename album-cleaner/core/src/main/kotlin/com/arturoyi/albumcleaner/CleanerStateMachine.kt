/**
 * 状态迁移。不碰媒体库，也不改索引。
 *
 * @file
 */
package com.arturoyi.albumcleaner

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * [AlbumCleaner] 允许的状态变化。不碰媒体库，也不改索引。
 *
 * 扫描会进入 [CleanerState.SCANNING]。提交删除会进入 [CleanerState.APPLYING]。
 */
public class CleanerStateMachine {
    private val gate = Any()
    private val states = MutableStateFlow(CleanerState.UNINITIALIZED)

    /** 当前状态。收集时先收到现在的值。 */
    public val state: StateFlow<CleanerState> = states.asStateFlow()

    /**
     * 从 [CleanerState.UNINITIALIZED] 进入 [CleanerState.READY]。
     *
     * @throws CleanerException 已经释放，或并不处在未初始化。
     */
    public fun initialize() {
        synchronized(gate) {
            when (val current = states.value) {
                CleanerState.RELEASED -> throw released()
                CleanerState.UNINITIALIZED -> states.value = CleanerState.READY
                else -> throw invalid(current, "不能再 initialize()")
            }
        }
    }

    /**
     * 从 [CleanerState.READY] 进入 [CleanerState.SCANNING]。
     *
     * @throws CleanerException 已经释放，或不在就绪。
     */
    internal fun beginScan() {
        synchronized(gate) {
            when (val current = states.value) {
                CleanerState.READY -> states.value = CleanerState.SCANNING
                CleanerState.RELEASED -> throw released()
                else -> throw invalid(current, "不能开始扫描")
            }
        }
    }

    /**
     * 扫描结束或取消后，从 [CleanerState.SCANNING] 回到 [CleanerState.READY]。
     *
     * 已经释放或并不在扫描中时保持现状。
     */
    internal fun finishScan() {
        synchronized(gate) {
            if (states.value == CleanerState.SCANNING) {
                states.value = CleanerState.READY
            }
        }
    }

    /**
     * 从 [CleanerState.READY] 进入 [CleanerState.APPLYING]。
     *
     * @throws CleanerException 已经释放，或不在就绪。
     */
    internal fun beginApply() {
        synchronized(gate) {
            when (val current = states.value) {
                CleanerState.READY -> states.value = CleanerState.APPLYING
                CleanerState.RELEASED -> throw released()
                else -> throw invalid(current, "不能提交删除")
            }
        }
    }

    /**
     * 删除提交结束或失败后，从 [CleanerState.APPLYING] 回到 [CleanerState.READY]。
     *
     * 已经释放时保持终态。
     */
    internal fun finishApply() {
        synchronized(gate) {
            if (states.value == CleanerState.APPLYING) {
                states.value = CleanerState.READY
            }
        }
    }

    /**
     * 只有 [CleanerState.SCANNING] 才能取消扫描。
     *
     * @throws CleanerException 还没开始扫描，或已经释放。
     */
    internal fun requireScanning() {
        synchronized(gate) {
            when (val current = states.value) {
                CleanerState.SCANNING -> Unit
                CleanerState.RELEASED -> throw released()
                else -> throw invalid(current, "没有正在进行的扫描")
            }
        }
    }

    /** 进入 [CleanerState.RELEASED]。已经是终态时保持不变。 */
    public fun release() {
        synchronized(gate) {
            states.value = CleanerState.RELEASED
        }
    }

    /**
     * 只有 [CleanerState.READY] 才能继续做扫描、查询或删除。
     *
     * @throws CleanerException 还没初始化、已经释放，或处在别的状态。
     */
    public fun requireReady() {
        synchronized(gate) {
            when (val current = states.value) {
                CleanerState.READY -> Unit
                CleanerState.RELEASED -> throw released()
                else -> throw invalid(current, "先 initialize()")
            }
        }
    }

    private fun released(): CleanerException {
        return CleanerException(ErrorCode.SDK_RELEASED, "已经 release()")
    }

    private fun invalid(current: CleanerState, action: String): CleanerException {
        return CleanerException(ErrorCode.INVALID_STATE, "当前是 $current，$action")
    }
}
