/**
 * 校验单连接下载能写完整文件，并且 HTTP 错误不会留下目标文件。
 *
 * @file
 */
package com.arturoyi.fastprism.engine

import com.arturoyi.fastprism.core.model.Destination
import com.arturoyi.fastprism.core.model.DownloadRequest
import com.arturoyi.fastprism.testing.TEN_MIB
import com.arturoyi.fastprism.testing.MockDownloadServerRule
import com.arturoyi.fastprism.testing.patternedBody
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.RegisterExtension
import java.io.File
import java.nio.file.Files
import java.security.MessageDigest

/**
 * [NaiveDownloadCall] 对本地 MockWebServer 的端到端行为。
 */
class NaiveDownloadCallTest {
    /** 每个测试方法单独启动并关闭的本地服务器。 */
    @JvmField
    @RegisterExtension
    val server = MockDownloadServerRule()

    /**
     * 服务器不返回 `Accept-Ranges` 时，10 MiB 正文仍能按顺序写完。
     *
     * 校验写入字节数、文件长度、SHA-256，以及进度只增不减并停在文件末尾。
     */
    @Test
    fun scenario1CompletesWhenServerOmitsAcceptRanges() {
        val body = patternedBody(TEN_MIB)
        server.enqueueFixed(body)
        val root = Files.createTempDirectory("fast-prism-scenario1").toFile()
        val request = DownloadRequest(
            url = server.url("/payload.bin"),
            destination = Destination("downloads/payload.bin"),
        )
        val progress = mutableListOf<Long>()
        val written = NaiveDownloadCall(request, root) { downloaded, _ ->
            progress += downloaded
        }.execute()

        val target = File(root, "downloads/payload.bin")
        assertEquals(body.size.toLong(), written)
        assertEquals(body.size.toLong(), target.length())
        assertEquals(sha256(body), sha256(target.readBytes()))
        assertTrue(progress.isNotEmpty())
        assertEquals(body.size.toLong(), progress.last())
        assertTrue(progress.zipWithNext().all { (previous, next) -> next >= previous })
        assertFalse(target.path.contains(".."))
    }

    /**
     * HTTP 404 时抛出 [IllegalStateException]，并且不创建目标文件。
     */
    @Test
    fun httpErrorDoesNotCreateTheFile() {
        server.enqueueFixed("missing".encodeToByteArray(), code = 404)
        val root = Files.createTempDirectory("fast-prism-404").toFile()
        val request = DownloadRequest(
            url = server.url("/missing.bin"),
            destination = Destination("missing.bin"),
        )
        assertThrows(IllegalStateException::class.java) {
            NaiveDownloadCall(request, root).execute()
        }
        assertFalse(File(root, "missing.bin").exists())
    }
}

/**
 * 计算 [bytes] 的 SHA-256，结果是 64 位小写十六进制。
 *
 * @param bytes 要摘要的内容。
 * @return 64 个十六进制字符。
 */
private fun sha256(bytes: ByteArray): String {
    return MessageDigest.getInstance("SHA-256").digest(bytes).joinToString(separator = "") { byte ->
        "%02x".format(byte.toInt() and 0xff)
    }
}
