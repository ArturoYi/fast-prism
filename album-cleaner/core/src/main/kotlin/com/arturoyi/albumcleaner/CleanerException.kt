/**
 * 错误码，以及调用方可以捕获的异常。
 *
 * @file
 */
package com.arturoyi.albumcleaner

/**
 * 失败原因。宿主按这个分支，而不是解析文案。
 */
public enum class ErrorCode {
    /** 没有读取相册的权限。 */
    PERMISSION_DENIED,

    /** Android 14 只授权了部分照片，索引不完整。 */
    PARTIAL_MEDIA_ACCESS,

    /** 本地索引打不开或校验失败。 */
    INDEX_CORRUPTED,

    /** 语义模型没有就绪。基础清理不受影响。 */
    MODEL_UNAVAILABLE,

    /** 磁盘空间不够写入缓存。 */
    INSUFFICIENT_STORAGE,

    /** 用户在系统删除确认里取消了。 */
    DELETE_REJECTED_BY_USER,

    /** 当前系统版本没有可用的删除或恢复接口。 */
    DELETE_UNSUPPORTED_ON_API,

    /** 扫描或删除被取消。 */
    TASK_CANCELLED,

    /** 处理过程中照片已经不在相册里。 */
    ASSET_GONE,

    /** `release()` 之后又调用了别的方法。 */
    SDK_RELEASED,

    /** 当前状态不允许这个调用。 */
    INVALID_STATE,
}

/**
 * SDK 抛出的受检失败。文案给人看，[code] 给程序分支。
 *
 * @property code 失败原因。
 * @param message 给宿主日志或调试看的说明。
 * @param cause 底层异常。没有时为 null。
 */
public class CleanerException(
    public val code: ErrorCode,
    message: String,
    cause: Throwable? = null,
) : RuntimeException(message, cause)
