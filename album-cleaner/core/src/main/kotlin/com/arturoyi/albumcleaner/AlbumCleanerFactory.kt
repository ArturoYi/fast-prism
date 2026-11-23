/**
 * 创建 [AlbumCleaner]。
 *
 * @file
 */
package com.arturoyi.albumcleaner

import android.content.Context

/**
 * 相册清理的工厂。一个进程里可以有多份，彼此不共享索引。
 */
public object AlbumCleanerFactory {
    /**
     * 用应用上下文创建一份还没初始化的清理器。
     *
     * @param context 任意上下文，内部会换成应用上下文。
     * @return 状态为 [CleanerState.UNINITIALIZED] 的入口。
     */
    public fun create(context: Context): AlbumCleaner {
        return AlbumCleanerImpl.create(context.applicationContext)
    }
}
