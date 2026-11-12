/**
 * `Content-Disposition` 的文件名检查。
 *
 * @file
 */
package com.arturoyi.fastprism.engine.file

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

/**
 * [dispositionVerdict] 对缺失、普通文件名和路径穿越的判断。
 */
class ContentDispositionTest {
    /**
     * 没有文件名时放过。普通文件名可以接受。
     */
    @Test
    fun missingOrPlainNameIsNotRejected() {
        assertEquals(DispositionVerdict.NONE, dispositionVerdict(null))
        assertEquals(DispositionVerdict.SAFE, dispositionVerdict("attachment; filename=\"ok.bin\""))
    }

    /**
     * `../`、百分号编码的斜杠和反斜杠都拒绝。
     */
    @Test
    fun traversalNamesAreRejected() {
        assertEquals(DispositionVerdict.REJECT, dispositionVerdict("attachment; filename=\"../secret.bin\""))
        assertEquals(
            DispositionVerdict.REJECT,
            dispositionVerdict("attachment; filename*=UTF-8''..%2Fsecret.bin"),
        )
        assertEquals(DispositionVerdict.REJECT, dispositionVerdict("attachment; filename=a\\b.bin"))
    }
}
