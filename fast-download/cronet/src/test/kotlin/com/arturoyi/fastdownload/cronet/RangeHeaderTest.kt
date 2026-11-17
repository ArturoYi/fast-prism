/**
 * `Range` 头的写法。
 *
 * @file
 */
package com.arturoyi.fastdownload.cronet

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * 不启动原生 Cronet 也能核对区间头。
 */
class RangeHeaderTest {
    /** 闭区间带上终点。开放到末尾时不写终点。 */
    @Test
    fun formatsClosedAndOpenRanges() {
        assertEquals("bytes=0-3", rangeHeader(0L..3L))
        assertEquals("bytes=8-", rangeHeader(8L..Long.MAX_VALUE))
    }
}
