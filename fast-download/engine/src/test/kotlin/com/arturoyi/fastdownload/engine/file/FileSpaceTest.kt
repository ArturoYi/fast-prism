/**
 * 可用空间不足时拒绝预分配。
 *
 * @file
 */
package com.arturoyi.fastdownload.engine.file

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import java.io.File
import java.nio.file.Files

/**
 * [ensureSpace] 把需要的字节和可用字节写进异常。
 */
class FileSpaceTest {
    /**
     * 可用 10 字节、需要 100 字节时抛出分配异常。
     */
    @Test
    fun insufficientSpaceNamesRequiredAndFree() {
        val error = assertThrows(FileAllocationException::class.java) {
            ensureSpace(freeBytes = 10, requiredBytes = 100)
        }
        assertEquals(100L, error.requiredBytes)
        assertEquals(10L, error.freeBytes)
    }

    /**
     * 父目录有足够空间时，临时文件被扩展到指定长度。
     */
    @Test
    fun preallocateExtendsFileToRequestedLength() {
        val root = Files.createTempDirectory("fastdownload-space").toFile()
        try {
            val file = File(root, "a.bin")
            DefaultFilePolicy().preallocate(file, 32)
            assertEquals(32L, file.length())
        } finally {
            root.deleteRecursively()
        }
    }
}
