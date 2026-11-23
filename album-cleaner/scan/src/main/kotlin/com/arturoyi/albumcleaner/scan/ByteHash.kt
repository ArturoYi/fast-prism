/**
 * 不解码也能算的字节哈希，以及内容指纹。
 *
 * @file
 */
package com.arturoyi.albumcleaner.scan

import com.arturoyi.albumcleaner.storage.FeatureRecord
import java.security.MessageDigest

/** 头尾各读这么多字节。 */
internal const val HASH_EDGE_BYTES: Int = 4 * 1024

/** 这一版特征算法。版本变了就重算。 */
internal const val FINGERPRINT_ALGORITHM_VERSION: Int = 1

/**
 * 头尾字节的 SHA-256，十六进制小写。
 *
 * @param head 文件开头，最多 [HASH_EDGE_BYTES]。
 * @param tail 文件末尾，最多 [HASH_EDGE_BYTES]。文件不比头部长时，传和 [head] 相同的内容。
 */
internal fun partialSha256(head: ByteArray, tail: ByteArray): String {
    val digest = MessageDigest.getInstance("SHA-256")
    digest.update(head)
    if (!(head === tail)) {
        digest.update(tail)
    }
    return digest.digest().toHex()
}

/**
 * 一整段字节的 SHA-256，十六进制小写。
 */
internal fun sha256(bytes: ByteArray): String {
    return MessageDigest.getInstance("SHA-256").digest(bytes).toHex()
}

/**
 * 用来判断要不要重算的指纹。
 *
 * 没有采样时只有大小和修改时间。读过头尾之后，把局部哈希接在后面。
 */
internal fun contentFingerprint(size: Long, modified: Long?, sample: String?): String {
    val prefix = "$size|${modified ?: ""}"
    return if (sample.isNullOrEmpty()) prefix else "$prefix|$sample"
}

/**
 * 已有特征是否还能用。
 *
 * 算法版本要一致，并且大小和修改时间没变。采样哈希可以有，也可以没有。
 */
internal fun canReuseFeature(existing: FeatureRecord?, size: Long, modified: Long?): Boolean {
    existing ?: return false
    if (existing.algorithmVersion != FINGERPRINT_ALGORITHM_VERSION) return false
    val prefix = contentFingerprint(size, modified, sample = null)
    val stored = existing.contentFingerprint
    return stored == prefix || stored.startsWith("$prefix|")
}

/**
 * 同一大小的一组局部哈希。局部哈希相同才读全文。
 *
 * 全文都一样时仍保存局部哈希。全文不一样时，各自保存全文哈希，避免被当成同一张。
 *
 * @param partials 这次要写入的照片和它的局部哈希。
 * @param fullHash 读全文。只会对局部哈希撞车的照片调用。失败时返回 null，这一组保持局部哈希。
 */
internal fun resolveByteHashes(
    partials: Map<Long, String>,
    fullHash: (Long) -> String?,
): Map<Long, String> {
    if (partials.isEmpty()) return emptyMap()
    val resolved = partials.toMutableMap()
    partials.entries.groupBy { entry -> entry.value }.values.forEach { group ->
        if (group.size < 2) return@forEach
        val fulls = LinkedHashMap<Long, String>(group.size)
        for (entry in group) {
            val hash = fullHash(entry.key) ?: return@forEach
            fulls[entry.key] = hash
        }
        if (fulls.values.distinct().size > 1) {
            resolved.putAll(fulls)
        }
    }
    return resolved
}

internal fun ByteArray.toHex(): String {
    val chars = CharArray(size * 2)
    for (index in indices) {
        val value = this[index].toInt() and 0xFF
        chars[index * 2] = HEX[value ushr 4]
        chars[index * 2 + 1] = HEX[value and 0x0F]
    }
    return String(chars)
}

private val HEX: CharArray = "0123456789abcdef".toCharArray()
