/**
 * 宿主展示用的缩略图。扫描热路径不用这一层。
 *
 * @file
 */
package com.arturoyi.albumcleaner

import android.content.ContentResolver
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Build
import android.os.CancellationSignal
import android.util.Size

/**
 * 按像素边长解码一张展示图。
 */
internal fun interface ThumbnailSource {
    /**
     * @param uri 内容地址。
     * @param sizePx 目标边长，像素。
     * @return 解不出来时为 null。调用方负责回收。
     */
    fun load(uri: String, sizePx: Int): Bitmap?
}

/**
 * API 29 起用系统缩略图。更早的系统按采样倍数解码，不把原图整张放进内存。
 *
 * @param context 任意上下文，内部会换成应用上下文。
 */
internal class MediaStoreThumbnails(
    context: Context,
) : ThumbnailSource {
    private val resolver: ContentResolver = context.applicationContext.contentResolver

    override fun load(uri: String, sizePx: Int): Bitmap? {
        val parsed = Uri.parse(uri)
        return try {
            if (Build.VERSION.SDK_INT >= 29) {
                resolver.loadThumbnail(parsed, Size(sizePx, sizePx), CancellationSignal())
            } else {
                decodeSampled(parsed, sizePx)
            }
        } catch (_: Exception) {
            null
        }
    }

    private fun decodeSampled(uri: Uri, sizePx: Int): Bitmap? {
        val bounds = resolver.openFileDescriptor(uri, "r")?.use { descriptor ->
            val options = BitmapFactory.Options()
            options.inJustDecodeBounds = true
            BitmapFactory.decodeFileDescriptor(descriptor.fileDescriptor, null, options)
            options
        } ?: return null
        return resolver.openFileDescriptor(uri, "r")?.use { descriptor ->
            val options = BitmapFactory.Options()
            options.inSampleSize = displaySample(bounds.outWidth, bounds.outHeight, sizePx)
            options.inPreferredConfig = Bitmap.Config.RGB_565
            BitmapFactory.decodeFileDescriptor(descriptor.fileDescriptor, null, options)
        }
    }
}

private fun displaySample(width: Int, height: Int, target: Int): Int {
    if (width <= 0 || height <= 0 || target <= 0) return 1
    var sample = 1
    val longest = maxOf(width, height)
    while (longest / (sample * 2) >= target && sample < 1_024) {
        sample *= 2
    }
    return sample
}
