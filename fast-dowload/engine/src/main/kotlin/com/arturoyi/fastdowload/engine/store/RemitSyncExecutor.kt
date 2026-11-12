/**
 * 把多次偏移合并成较少的存储写入。
 *
 * @file
 */
package com.arturoyi.fastdowload.engine.store

import com.arturoyi.fastdowload.core.model.Defaults
import java.io.Closeable
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

/**
 * 接收一次已经同步成功的偏移。
 */
public fun interface OffsetCommit {
    /**
     * 把 [delta] 字节记到 [publicId] 的 [blockIndex] 分块上。
     *
     * 调用前这些字节必须已经同步到存储设备。
     *
     * @param publicId 任务标识。
     * @param blockIndex 分块序号。
     * @param delta 本次要增加的字节数。
     */
    public fun onCommitted(publicId: String, blockIndex: Int, delta: Long)
}

/**
 * 在内存里按分块累加偏移，到时间或被要求时一次性提交。
 *
 * 默认间隔是 [Defaults.REMIT_DELAY_MS]。任务结束时要调用 [forceFlush]，否则最后一段还在内存里。
 * 关闭时不自动提交，用来模拟进程在合并窗口内退出。
 *
 * @param delayMs 自动提交的间隔，单位毫秒，必须大于 0。
 * @param commit 真正写入存储的动作。
 */
public class RemitSyncExecutor(
    private val delayMs: Long = Defaults.REMIT_DELAY_MS,
    private val commit: OffsetCommit,
) : Closeable {
    private val monitor = Any()
    private val pending = HashMap<BlockKey, Long>()
    private val worker = Executors.newSingleThreadScheduledExecutor { runnable ->
        Thread(runnable, "fast-dowload-remit").apply { isDaemon = true }
    }

    init {
        require(delayMs > 0L) { "delayMs 必须大于 0" }
        worker.scheduleWithFixedDelay(
            { drain() },
            delayMs,
            delayMs,
            TimeUnit.MILLISECONDS,
        )
    }

    /**
     * 累加一段已经同步的字节。
     *
     * 同一分块在窗口内的多次调用会合成一个总数。
     *
     * @param publicId 任务标识。
     * @param blockIndex 分块序号。
     * @param delta 字节数，必须大于 0。
     */
    public fun record(publicId: String, blockIndex: Int, delta: Long) {
        require(delta > 0L) { "delta 必须大于 0" }
        synchronized(monitor) {
            val key = BlockKey(publicId, blockIndex)
            pending[key] = (pending[key] ?: 0L) + delta
        }
    }

    /**
     * 立刻把内存中的偏移写进存储。
     *
     * 没有待写数据时直接返回。写入失败时把这批数据放回内存。
     */
    public fun forceFlush() {
        drain()
    }

    /**
     * 停止后台线程。
     *
     * 不调用 [forceFlush]。尚未提交的偏移会丢掉。
     */
    public override fun close() {
        worker.shutdownNow()
    }

    private fun drain() {
        val batch = synchronized(monitor) {
            if (pending.isEmpty()) return
            val copy = pending.toMap()
            pending.clear()
            copy
        }
        val applied = HashSet<BlockKey>()
        try {
            batch.forEach { (key, delta) ->
                commit.onCommitted(key.publicId, key.blockIndex, delta)
                applied.add(key)
            }
        } catch (error: RuntimeException) {
            synchronized(monitor) {
                batch.forEach { (key, delta) ->
                    if (key !in applied) pending[key] = (pending[key] ?: 0L) + delta
                }
            }
            throw error
        }
    }
}

/**
 * 内存合并表的键。
 *
 * @property publicId 任务标识。
 * @property blockIndex 分块序号。
 */
private data class BlockKey(
    val publicId: String,
    val blockIndex: Int,
)
