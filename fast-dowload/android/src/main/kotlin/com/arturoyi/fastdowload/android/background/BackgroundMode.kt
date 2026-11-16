/**
 * 有下载任务时怎么对待当前进程。
 *
 * 三种模式都不保证进程被杀掉之后自动接着下。可靠的恢复是启动时的断点清洗。
 * 调用方通过 `foregroundService()` 进入 [FOREGROUND_SERVICE]。系统拒绝前台服务时，实现内部改走 WorkManager，不用单独再选一种模式。
 *
 * @file
 */
package com.arturoyi.fastdowload.android.background

/**
 * 进程被系统允许时，用前台服务或 WorkManager 保活。
 */
public enum class BackgroundMode {
    /** 只使用调用方已经在跑的线程，不启动服务。 */
    IN_PROCESS,

    /** 有排队或下载中的任务时启动 dataSync 前台服务。系统拒绝时改用 WorkManager。 */
    FOREGROUND_SERVICE,

    /** 把保活交给 WorkManager。 */
    WORK_MANAGER,
}
