/**
 * 索引打不开或校验失败。
 *
 * 调用方把它转成自己的错误码。这个模块不依赖入口模块。
 *
 * @file
 */
package com.arturoyi.albumcleaner.storage

/**
 * 本地索引损坏。重建可以留下回收站和删除记录。
 *
 * @param message 校验失败的原因。
 * @param cause 底层数据库异常。没有时为 null。
 */
public class IndexCorruptedException(
    message: String,
    cause: Throwable? = null,
) : RuntimeException(message, cause)
