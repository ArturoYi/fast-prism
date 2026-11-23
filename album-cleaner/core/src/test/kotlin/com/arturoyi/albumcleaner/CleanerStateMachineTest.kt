/**
 * [CleanerStateMachine] 的迁移：初始化、重复初始化、释放。
 *
 * @file
 */
package com.arturoyi.albumcleaner

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

/**
 * 状态迁移。
 */
class CleanerStateMachineTest {
    /**
     * 初始化进入就绪，再初始化会被拒绝。
     */
    @Test
    fun initializeOnce() {
        val machine = CleanerStateMachine()
        assertEquals(CleanerState.UNINITIALIZED, machine.state.value)
        machine.initialize()
        assertEquals(CleanerState.READY, machine.state.value)
        val error = assertThrows(CleanerException::class.java) { machine.initialize() }
        assertEquals(ErrorCode.INVALID_STATE, error.code)
        assertEquals(CleanerState.READY, machine.state.value)
    }

    /**
     * 只有就绪可以开始扫描。扫完回到就绪。
     */
    @Test
    fun scanRunsFromReadyAndReturns() {
        val machine = CleanerStateMachine()
        val tooEarly = assertThrows(CleanerException::class.java) { machine.beginScan() }
        assertEquals(ErrorCode.INVALID_STATE, tooEarly.code)
        machine.initialize()
        machine.beginScan()
        assertEquals(CleanerState.SCANNING, machine.state.value)
        val again = assertThrows(CleanerException::class.java) { machine.beginScan() }
        assertEquals(ErrorCode.INVALID_STATE, again.code)
        machine.finishScan()
        assertEquals(CleanerState.READY, machine.state.value)
        machine.finishScan()
        assertEquals(CleanerState.READY, machine.state.value)
    }

    /**
     * 释放是终态。之后不能初始化，也不能要求就绪。
     */
    @Test
    fun releaseIsTerminal() {
        val machine = CleanerStateMachine()
        machine.initialize()
        machine.release()
        machine.release()
        assertEquals(CleanerState.RELEASED, machine.state.value)
        val initialize = assertThrows(CleanerException::class.java) { machine.initialize() }
        val ready = assertThrows(CleanerException::class.java) { machine.requireReady() }
        assertEquals(ErrorCode.SDK_RELEASED, initialize.code)
        assertEquals(ErrorCode.SDK_RELEASED, ready.code)
        machine.finishScan()
        val scanning = assertThrows(CleanerException::class.java) { machine.requireScanning() }
        assertEquals(ErrorCode.SDK_RELEASED, scanning.code)
        assertEquals(CleanerState.RELEASED, machine.state.value)
    }
}
