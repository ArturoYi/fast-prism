/**
 * 局部哈希、全文消歧和指纹复用。
 *
 * @file
 */
package com.arturoyi.albumcleaner.scan

import com.arturoyi.albumcleaner.storage.FeatureRecord
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 不碰数据库。
 */
class ByteHashTest {
    /**
     * 头尾相同的引用只哈希一次。不同的头尾会把两段都算进去。
     */
    @Test
    fun partialHashCoversHeadAndTail() {
        val head = byteArrayOf(1, 2, 3)
        assertEquals(sha256(head), partialSha256(head, head))
        assertTrue(partialSha256(head, byteArrayOf(9)) != partialSha256(head, head))
    }

    /**
     * 局部哈希不同时不读全文。相同但全文不同时，保存全文哈希。
     */
    @Test
    fun fullHashRunsOnlyWhenPartialsCollide() {
        val fullCalls = mutableListOf<Long>()
        val distinct = resolveByteHashes(mapOf(1L to "aa", 2L to "bb")) { id ->
            fullCalls += id
            "full-$id"
        }
        assertTrue(fullCalls.isEmpty())
        assertEquals(mapOf(1L to "aa", 2L to "bb"), distinct)

        val collided = resolveByteHashes(mapOf(1L to "aa", 2L to "aa")) { id ->
            fullCalls += id
            "full-$id"
        }
        assertEquals(listOf(1L, 2L), fullCalls)
        assertEquals(mapOf(1L to "full-1", 2L to "full-2"), collided)

        val sameFile = resolveByteHashes(mapOf(3L to "aa", 4L to "aa")) { "same" }
        assertEquals(mapOf(3L to "aa", 4L to "aa"), sameFile)
    }

    /**
     * 大小或修改时间变了就要重算。采样哈希还在时也可以复用。
     */
    @Test
    fun reuseRequiresTheSameSizeAndModifiedTime() {
        val stored = feature(contentFingerprint(10L, 2L, "sample"))
        assertTrue(canReuseFeature(stored, 10L, 2L))
        assertFalse(canReuseFeature(stored, 11L, 2L))
        assertFalse(canReuseFeature(stored, 10L, 3L))
        assertFalse(canReuseFeature(stored.copy(algorithmVersion = 2), 10L, 2L))
        assertFalse(canReuseFeature(null, 10L, 2L))
        assertTrue(canReuseFeature(feature(contentFingerprint(10L, 2L, null)), 10L, 2L))
    }
}

private fun feature(fingerprint: String): FeatureRecord {
    return FeatureRecord(
        assetId = 1L,
        byteHash = null,
        phash = null,
        dhash = null,
        blurScore = null,
        contentFingerprint = fingerprint,
        algorithmVersion = FINGERPRINT_ALGORITHM_VERSION,
        computedAt = 1L,
    )
}
