/**
 * 用文件通道计算摘要。
 *
 * @file
 */
package com.arturoyi.fastdownload.engine.verify

import com.arturoyi.fastdownload.core.model.IntegritySpec
import com.arturoyi.fastdownload.core.spi.IntegrityVerifier
import java.io.File
import java.nio.ByteBuffer
import java.nio.channels.FileChannel
import java.nio.file.StandardOpenOption
import java.security.MessageDigest
import java.util.Base64

/**
 * 默认校验。读取方式与历史实现相同，走普通输入流。
 */
public object DefaultIntegrityVerifier : IntegrityVerifier {
    /**
     * 委托给现有的摘要比较。
     *
     * @param file 已经写完的文件。
     * @param spec 调用方要求的校验。
     * @param contentMd5 响应头 `Content-MD5`。
     * @return 摘要一致时为 true。
     */
    public override fun verify(file: File, spec: IntegritySpec, contentMd5: String?): Boolean {
        return FileIntegrity.matches(file, spec, contentMd5)
    }
}

/**
 * 用 [FileChannel] 读取文件后再计算摘要。
 *
 * 和 [DefaultIntegrityVerifier] 接受同一套 SHA-256、MD5 和 `Content-MD5` 规则，可以互相替换。
 */
public object ChannelIntegrityVerifier : IntegrityVerifier {
    /**
     * 核对 [file]。
     *
     * `Content-MD5` 接受 Base64，也接受 32 位十六进制。头为空或无法解码时返回 false。
     *
     * @param file 已经写完的文件。
     * @param spec 调用方要求的校验。
     * @param contentMd5 响应头 `Content-MD5`。
     * @return 摘要一致时为 true。
     */
    public override fun verify(file: File, spec: IntegritySpec, contentMd5: String?): Boolean {
        return when (spec) {
            is IntegritySpec.Sha256 -> hex(file, "SHA-256").equals(spec.hex, ignoreCase = true)
            is IntegritySpec.Md5 -> hex(file, "MD5").equals(spec.hex, ignoreCase = true)
            IntegritySpec.ContentMd5 -> {
                val header = contentMd5?.trim().orEmpty()
                val expected = decodeContentMd5(header) ?: return false
                header.isNotEmpty() && MessageDigest.isEqual(digest(file, "MD5"), expected)
            }
        }
    }

    private fun hex(file: File, algorithm: String): String {
        return digest(file, algorithm).joinToString(separator = "") { byte ->
            "%02x".format(byte.toInt() and 0xff)
        }
    }

    private fun digest(file: File, algorithm: String): ByteArray {
        val message = MessageDigest.getInstance(algorithm)
        FileChannel.open(file.toPath(), StandardOpenOption.READ).use { channel ->
            val buffer = ByteBuffer.allocate(8_192)
            while (true) {
                val read = channel.read(buffer)
                if (read < 0) break
                buffer.flip()
                message.update(buffer)
                buffer.clear()
            }
        }
        return message.digest()
    }

    private fun decodeContentMd5(header: String): ByteArray? {
        if (header.isEmpty()) return null
        return try {
            if (HEX_MD5.matches(header)) {
                ByteArray(header.length / 2) { index ->
                    header.substring(index * 2, index * 2 + 2).toInt(16).toByte()
                }
            } else {
                Base64.getDecoder().decode(header)
            }
        } catch (_: IllegalArgumentException) {
            null
        }
    }

    private val HEX_MD5: Regex = Regex("[0-9a-fA-F]{32}")
}
