/**
 * 同一张缩略图上的 dHash、感知哈希和模糊分。
 *
 * @file
 */
package com.arturoyi.albumcleaner.scan

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sqrt

/**
 * 灰度图。像素是 0 到 255。
 *
 * @property width 宽。
 * @property height 高。
 * @property pixels 行优先的灰度值。
 */
internal class GrayImage(
    val width: Int,
    val height: Int,
    val pixels: IntArray,
) {
    init {
        require(width > 0 && height > 0 && pixels.size == width * height) {
            "灰度图尺寸不对"
        }
    }
}

/**
 * 把最长边收到 [target] 附近时，BitmapFactory 该用的采样倍数。
 *
 * 只取 2 的幂，避免把原图整张解出来。
 */
internal fun sampleSize(width: Int, height: Int, target: Int): Int {
    if (width <= 0 || height <= 0 || target <= 0) return 1
    var sample = 1
    val longest = maxOf(width, height)
    while (longest / (sample * 2) >= target && sample < 1_024) {
        sample *= 2
    }
    return sample
}

/**
 * 9×8 差分哈希，64 位。左边比右边亮的那一位是 1。
 */
internal fun dHash(image: GrayImage): Long {
    val small = scaleBox(image, width = 9, height = 8)
    var hash = 0L
    var bit = 0
    for (y in 0 until 8) {
        val row = y * 9
        for (x in 0 until 8) {
            if (small.pixels[row + x] > small.pixels[row + x + 1]) {
                hash = hash or (1L shl bit)
            }
            bit += 1
        }
    }
    return hash
}

/**
 * 3×3 拉普拉斯方差。平坦的图接近 0。
 *
 * 核是 `0 1 0 / 1 -4 1 / 0 1 0`。边缘一圈不参与。
 */
internal fun blurVariance(image: GrayImage): Double {
    if (image.width < 3 || image.height < 3) return 0.0
    var count = 0
    var sum = 0.0
    var sumSquares = 0.0
    for (y in 1 until image.height - 1) {
        for (x in 1 until image.width - 1) {
            val center = grayAt(image, x, y)
            val value = grayAt(image, x, y - 1) +
                grayAt(image, x, y + 1) +
                grayAt(image, x - 1, y) +
                grayAt(image, x + 1, y) -
                4.0 * center
            sum += value
            sumSquares += value * value
            count += 1
        }
    }
    if (count == 0) return 0.0
    val mean = sum / count
    val variance = sumSquares / count - mean * mean
    return if (variance < 0.0) 0.0 else variance
}

/**
 * 32×32 灰度上的 8×8 低频 DCT。直流分量不参与，剩下 63 位。
 *
 * 余弦表按 32 个样本的周期预计算，对应低频的前 8 个系数。
 */
internal fun pHash(image: GrayImage): Long {
    val small = scaleBox(image, width = 32, height = 32)
    val coefficients = Array(8) { DoubleArray(8) }
    for (v in 0 until 8) {
        for (u in 0 until 8) {
            var sum = 0.0
            for (y in 0 until 32) {
                val weightY = COS_32[v][y]
                val row = y * 32
                for (x in 0 until 32) {
                    sum += small.pixels[row + x] * COS_32[u][x] * weightY
                }
            }
            coefficients[u][v] = sum * DCT_WEIGHT[u] * DCT_WEIGHT[v]
        }
    }
    val ac = DoubleArray(63)
    var count = 0
    for (u in 0 until 8) {
        for (v in 0 until 8) {
            if (u == 0 && v == 0) continue
            ac[count] = coefficients[u][v]
            count += 1
        }
    }
    ac.sort()
    val median = ac[ac.size / 2]
    var hash = 0L
    var bit = 0
    for (u in 0 until 8) {
        for (v in 0 until 8) {
            if (u == 0 && v == 0) continue
            if (coefficients[u][v] > median) {
                hash = hash or (1L shl bit)
            }
            bit += 1
        }
    }
    return hash
}

/** 64 位哈希的十六进制，补满 16 位。 */
internal fun Long.toHashText(): String {
    return java.lang.Long.toUnsignedString(this, 16).padStart(16, '0')
}

/**
 * 盒子平均缩放到目标尺寸。
 */
internal fun scaleBox(image: GrayImage, width: Int, height: Int): GrayImage {
    if (image.width == width && image.height == height) return image
    val pixels = IntArray(width * height)
    for (y in 0 until height) {
        val yStart = y * image.height / height
        val yEnd = maxOf(yStart + 1, (y + 1) * image.height / height).coerceAtMost(image.height)
        for (x in 0 until width) {
            val xStart = x * image.width / width
            val xEnd = maxOf(xStart + 1, (x + 1) * image.width / width).coerceAtMost(image.width)
            var sum = 0
            var count = 0
            for (yy in yStart until yEnd) {
                val row = yy * image.width
                for (xx in xStart until xEnd) {
                    sum += image.pixels[row + xx]
                    count += 1
                }
            }
            pixels[y * width + x] = if (count == 0) 0 else sum / count
        }
    }
    return GrayImage(width, height, pixels)
}

private fun grayAt(image: GrayImage, x: Int, y: Int): Double {
    return image.pixels[y * image.width + x].toDouble()
}

private val COS_32: Array<DoubleArray> = Array(8) { frequency ->
    DoubleArray(32) { sample ->
        cos((2.0 * sample + 1.0) * frequency * PI / 64.0)
    }
}

private val DCT_WEIGHT: DoubleArray = DoubleArray(8) { frequency ->
    if (frequency == 0) 1.0 / sqrt(2.0) else 1.0
}
