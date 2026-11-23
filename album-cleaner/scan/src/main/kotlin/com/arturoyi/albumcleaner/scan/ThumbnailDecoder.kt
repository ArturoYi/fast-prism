/**
 * 扫描热路径上的 64px 缩略图。原图不进入这一层。
 *
 * @file
 */
package com.arturoyi.albumcleaner.scan

import android.content.ContentResolver
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Rect
import android.net.Uri
import android.os.Build
import android.os.CancellationSignal
import android.os.OperationCanceledException
import android.util.Size
import java.io.FileDescriptor

/**
 * 把一张图收成灰度小图。失败时返回 null。
 */
internal interface ThumbnailDecoder {
    /**
     * @param uri 内容地址。
     * @param signal 取消后停止解码。
     */
    fun gray64(uri: String, signal: CancellationSignal): GrayImage?
}

/**
 * API 29 起用 [ContentResolver.loadThumbnail]。更早的系统按采样倍数解码，配置是 RGB_565。
 *
 * 算完立刻 [Bitmap.recycle]。
 *
 * @param resolver 应用的内容解析器。
 */
internal class MediaStoreThumbnailDecoder(
    private val resolver: ContentResolver,
) : ThumbnailDecoder {
    override fun gray64(uri: String, signal: CancellationSignal): GrayImage? {
        if (signal.isCanceled) throw OperationCanceledException()
        val bitmap = decode(uri, signal) ?: return null
        return try {
            toGray(bitmap, TARGET)
        } finally {
            if (!bitmap.isRecycled) bitmap.recycle()
        }
    }

    private fun decode(uri: String, signal: CancellationSignal): Bitmap? {
        val parsed = Uri.parse(uri)
        return try {
            if (Build.VERSION.SDK_INT >= 29) {
                resolver.loadThumbnail(parsed, Size(TARGET, TARGET), signal)
            } else {
                decodeSampled(parsed)
            }
        } catch (cancelled: OperationCanceledException) {
            throw cancelled
        } catch (security: SecurityException) {
            throw security
        } catch (_: Exception) {
            null
        }
    }

    private fun decodeSampled(uri: Uri): Bitmap? {
        val bounds = resolver.openFileDescriptor(uri, "r")?.use { descriptor ->
            val options = android.graphics.BitmapFactory.Options()
            options.inJustDecodeBounds = true
            BitmapFactoryDecode.decode(descriptor.fileDescriptor, options)
            options
        } ?: return null
        val sample = sampleSize(bounds.outWidth, bounds.outHeight, TARGET)
        return resolver.openFileDescriptor(uri, "r")?.use { descriptor ->
            val options = android.graphics.BitmapFactory.Options()
            options.inSampleSize = sample
            options.inPreferredConfig = Bitmap.Config.RGB_565
            BitmapFactoryDecode.decode(descriptor.fileDescriptor, options)
        }
    }

    private companion object {
        const val TARGET: Int = 64
    }
}

/**
 * 把位图收成 [target] 边长的灰度。调用方负责回收 [source]。
 */
internal fun toGray(source: Bitmap, target: Int): GrayImage {
    val drawing = if (source.width == target && source.height == target && source.config == Bitmap.Config.RGB_565) {
        source
    } else {
        val scaled = Bitmap.createBitmap(target, target, Bitmap.Config.RGB_565)
        val canvas = Canvas(scaled)
        canvas.drawBitmap(source, null, Rect(0, 0, target, target), Paint(Paint.FILTER_BITMAP_FLAG))
        scaled
    }
    val argb = IntArray(target * target)
    drawing.getPixels(argb, 0, target, 0, 0, target, target)
    if (drawing !== source) drawing.recycle()
    val gray = IntArray(argb.size)
    for (index in argb.indices) {
        val color = argb[index]
        val red = (color shr 16) and 0xFF
        val green = (color shr 8) and 0xFF
        val blue = color and 0xFF
        gray[index] = (red * 77 + green * 150 + blue * 29) shr 8
    }
    return GrayImage(target, target, gray)
}

/**
 * 测试和低版本解码共用的入口，避免在哈希单测里触发位图。
 */
internal object BitmapFactoryDecode {
    fun decode(descriptor: FileDescriptor, options: android.graphics.BitmapFactory.Options): Bitmap? {
        return android.graphics.BitmapFactory.decodeFileDescriptor(descriptor, null, options)
    }
}
