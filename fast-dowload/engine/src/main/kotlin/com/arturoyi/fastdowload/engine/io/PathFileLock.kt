/**
 * 按目标路径做进程内互斥。
 *
 * @file
 */
package com.arturoyi.fastdowload.engine.io

import java.io.File
import java.io.IOException
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.locks.ReentrantLock

/**
 * 保证同一时刻只有一个下载在写同一个目标文件。
 *
 * 锁只在当前进程内有效。第二个下载会立刻失败，不会等待，也不会覆盖正在写的文件。
 */
public class PathFileLock {
    private val guards = ConcurrentHashMap<String, ReentrantLock>()

    /**
     * 尝试占用 [file] 的规范路径。
     *
     * 同一线程可以再次获取自己已经持有的锁。其他线程在锁被释放前获取失败。
     *
     * @param file 最终目标文件。文件本身可以尚不存在，父路径必须能解析。
     * @return 获得锁时为 true。锁已被其他线程持有时为 false。
     * @throws IOException 无法解析规范路径。
     */
    public fun tryAcquire(file: File): Boolean {
        val lock = guards.computeIfAbsent(file.canonicalPath) { ReentrantLock() }
        return lock.tryLock()
    }

    /**
     * 释放当前线程持有的 [file] 锁。
     *
     * 当前线程没有持有这把锁时什么也不做。
     *
     * @param file 与 [tryAcquire] 相同的目标文件。
     * @throws IOException 无法解析规范路径。
     */
    public fun release(file: File) {
        val lock = guards[file.canonicalPath] ?: return
        if (lock.isHeldByCurrentThread) lock.unlock()
    }
}
