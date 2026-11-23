/**
 * dHash、模糊分、感知哈希和采样倍数。
 *
 * @file
 */
package com.arturoyi.albumcleaner.scan

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 纯计算，不解码。
 */
class VisualHashTest {
    /**
     * 左边更亮时，64 位全部置上。
     */
    @Test
    fun dHashSetsBitsWhenTheLeftPixelIsBrighter() {
        val pixels = IntArray(9 * 8)
        for (y in 0 until 8) {
            for (x in 0 until 9) pixels[y * 9 + x] = 9 - x
        }
        assertEquals(-1L, dHash(GrayImage(9, 8, pixels)))
        assertEquals(0L, dHash(solid(9, 8, 40)))
    }

    /**
     * 平坦的图方差是 0。有一块亮斑时方差大于 0。
     */
    @Test
    fun blurVarianceIsZeroForAFlatImage() {
        assertEquals(0.0, blurVariance(solid(8, 8, 30)), 0.0)
        val impulse = solid(8, 8, 0)
        impulse.pixels[3 * 8 + 3] = 255
        assertTrue(blurVariance(impulse) > 0.0)
    }

    /**
     * 同一张图的感知哈希稳定。改一个像素就会变。
     */
    @Test
    fun pHashChangesWhenTheImageChanges() {
        val original = solid(16, 16, 20)
        val edited = solid(16, 16, 20)
        edited.pixels[0] = 255
        assertEquals(pHash(original), pHash(solid(16, 16, 20)))
        assertNotEquals(pHash(original), pHash(edited))
    }

    /**
     * 采样倍数把长边收到目标附近，并且是 2 的幂。
     */
    @Test
    fun sampleSizeShrinksTheLongEdge() {
        assertEquals(16, sampleSize(width = 1024, height = 768, target = 64))
        assertEquals(1, sampleSize(width = 40, height = 30, target = 64))
    }
}

private fun solid(width: Int, height: Int, value: Int): GrayImage {
    return GrayImage(width, height, IntArray(width * height) { value })
}
