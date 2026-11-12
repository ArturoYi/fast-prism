/**
 * `Content-Range` 解析的正例和拒绝条件。
 *
 * @file
 */
package com.arturoyi.fastdowload.engine.http

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

/**
 * [parseContentRange] 对满足区间、未知区间和非法文本的结果。
 */
class ContentRangeHeaderTest {
    /**
     * `bytes 0-0/128` 得到闭区间，`bytes * /128` 这种未知区间只保留总长度。
     */
    @Test
    fun parsesSatisfiedAndUnsatisfiedRanges() {
        val partial = parseContentRange("bytes 0-0/128")
        assertEquals(0L, partial?.start)
        assertEquals(0L, partial?.endInclusive)
        assertEquals(128L, partial?.total)

        val unsatisfied = parseContentRange("bytes */128")
        assertNull(unsatisfied?.start)
        assertNull(unsatisfied?.endInclusive)
        assertEquals(128L, unsatisfied?.total)
    }

    /**
     * null、缺少区间，以及非 `bytes` 单位都返回 null。
     */
    @Test
    fun rejectsMalformedHeader() {
        assertNull(parseContentRange(null))
        assertNull(parseContentRange("bytes"))
        assertNull(parseContentRange("items 0-1/2"))
    }
}
