/**
 * 确认 [com.arturoyi.fastprism.core.error.ErrorCode] 的数字码没有重复，且重试标记符合约定。
 *
 * @file
 */
package com.arturoyi.fastprism.core.error

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

/**
 * 错误码数值唯一，以及若干代表性条目的重试标记。
 */
class ErrorCodeTest {
    /**
     * 全部 [ErrorCode.code] 互不相同，并核对未知、无网络、路径穿越、404 和写盘错误的重试标记。
     */
    @Test
    fun numericCodesStayUnique() {
        val codes = ErrorCode.entries.map { it.code }
        assertEquals(codes.toSet().size, codes.size)
        assertEquals(0, ErrorCode.UNKNOWN.code)
        assertEquals(7, ErrorCode.NO_NETWORK.code)
        assertEquals(21, ErrorCode.SECURITY_PATH_TRAVERSAL.code)
        assertEquals(false, ErrorCode.HTTP_NOT_FOUND.retryable)
        assertEquals(true, ErrorCode.WRITE_IO_ERROR.retryable)
    }
}
