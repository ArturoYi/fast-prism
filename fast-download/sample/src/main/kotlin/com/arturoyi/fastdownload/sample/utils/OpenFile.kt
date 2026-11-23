/**
 * 把已经下完的文件交给系统打开。
 *
 * 只接受应用内部目录里的文件。系统拿到的是只读 content 地址，不是绝对路径。
 *
 * @file
 */
package com.arturoyi.fastdownload.sample.utils

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.webkit.MimeTypeMap
import androidx.core.content.FileProvider
import java.io.File
import java.util.Locale

/** 系统打不打得开。 */
internal enum class OpenResult {
    /** 已经交给系统。 */
    Opened,

    /** 文件不在内部目录里。 */
    Missing,

    /** 没有应用接收这种文件。 */
    NoApp,
}

/**
 * 用系统的查看动作打开 [absolutePath]。
 *
 * 路径必须落在 [Context.filesDir] 里面。没有能打开的应用时返回 [OpenResult.NoApp]，不抛出去。
 *
 * @param absolutePath 已完成文件的绝对路径。没有文件时为 null。
 * @return 打开结果。
 */
internal fun Context.openFinishedFile(absolutePath: String?): OpenResult {
    if (absolutePath.isNullOrBlank()) return OpenResult.Missing
    val file = File(absolutePath)
    val allowed = runCatching { file.isFile && file.isInside(filesDir) }.getOrDefault(false)
    if (!allowed) return OpenResult.Missing
    val uri = try {
        FileProvider.getUriForFile(this, "$packageName.fileprovider", file)
    } catch (_: IllegalArgumentException) {
        return OpenResult.Missing
    }
    val intent = Intent(Intent.ACTION_VIEW).apply {
        setDataAndType(uri, mimeOf(file))
        clipData = ClipData.newRawUri("file", uri)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        if (this@openFinishedFile !is Activity) addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
    return try {
        startActivity(intent)
        OpenResult.Opened
    } catch (_: ActivityNotFoundException) {
        OpenResult.NoApp
    }
}

private fun File.isInside(root: File): Boolean {
    val base = root.canonicalFile
    val target = canonicalFile
    return target.path.startsWith(base.path + File.separator)
}

private fun mimeOf(file: File): String {
    val extension = file.extension.lowercase(Locale.US)
    if (extension.isEmpty()) return "application/octet-stream"
    return MimeTypeMap.getSingleton().getMimeTypeFromExtension(extension)
        ?: "application/octet-stream"
}
