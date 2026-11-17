/**
 * 按字节限制下载速度。
 *
 * 在读到一段响应、写进文件之前调用。实现要能被多个分块同时调用。
 *
 * @file
 */
package com.arturoyi.fastdownload.core.spi

/**
 * 领取 [bytes] 个字节的发送额度。
 *
 * 额度不够时阻塞调用线程，直到可以继续或线程被中断。
 */
public fun interface RateLimiter {
    /**
     * 领取 [bytes] 个字节的额度。
     *
     * 线程被中断时可以提前返回，调用方随即停止写入。
     *
     * @param bytes 这次准备写入的字节数。0 或负数应立刻返回。
     */
    public fun acquire(bytes: Int)

    public companion object {
        /** 不限制速度。 */
        public val UNLIMITED: RateLimiter = RateLimiter {}
    }
}
