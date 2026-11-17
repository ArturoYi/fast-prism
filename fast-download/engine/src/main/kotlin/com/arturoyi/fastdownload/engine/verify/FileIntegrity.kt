/**
 * 在临时文件改名之前核对摘要。
 *
 * @file
 */
package com.arturoyi.fastdownload.engine.verify

import com.arturoyi.fastdownload.core.model.IntegritySpec
import java.io.File
import java.security.MessageDigest
import java.util.Base64

/**
 * 计算文件摘要，并和调用方或响应头给出的值比较。
 */
internal object FileIntegrity {
    /**
     * 判断 [file] 是否符合 [spec]。
     *
     * `Content-MD5` 按 RFC 1864 做 Base64，也接受 32 位十六进制。没有响应头时判为不符合。
     *
     * @param file 已经写完的临时文件或目标文件。
     * @param spec 调用方要求的校验。
     * @param contentMd5 响应头 `Content-MD5`。没有时为 null。
     * @return 摘要一致时为 true。
     */
    fun matches(file: File, spec: IntegritySpec, contentMd5: String?): Boolean {
        return when (spec) {
            is IntegritySpec.Sha256 -> digest(file, "SHA-256").equals(spec.hex, ignoreCase = true)
            is IntegritySpec.Md5 -> digest(file, "MD5").equals(spec.hex, ignoreCase = true)
            IntegritySpec.ContentMd5 -> {
                val header = contentMd5?.trim().orEmpty()
                val expected = decodeContentMd5(header)
                header.isNotEmpty() && expected != null && MessageDigest.isEqual(md5Bytes(file), expected)
            }
        }
    }

    private fun digest(file: File, algorithm: String): String {
        val bytes = md5Bytes(file, algorithm)
        return bytes.joinToString(separator = "") { byte -> "%02x".format(byte.toInt() and 0xff) }
    }

    private fun md5Bytes(file: File, algorithm: String = "MD5"): ByteArray {
        val digest = MessageDigest.getInstance(algorithm)
        file.inputStream().use { input ->
            val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
            while (true) {
                val read = input.read(buffer)
                if (read < 0) break
                digest.update(buffer, 0, read)
            }
        }
        return digest.digest()
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
