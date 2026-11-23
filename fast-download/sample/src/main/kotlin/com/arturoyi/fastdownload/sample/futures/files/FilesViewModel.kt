/**
 * 文件页的状态：已经下完的文件。
 *
 * @file
 */
package com.arturoyi.fastdownload.sample.futures.files

import android.app.Application
import android.content.Context
import androidx.lifecycle.AndroidViewModel
import com.arturoyi.fastdownload.core.model.DownloadRecord
import com.arturoyi.fastdownload.core.state.Status
import com.arturoyi.fastdownload.sample.SampleApp
import com.arturoyi.fastdownload.sample.utils.displayName
import com.arturoyi.fastdownload.sample.utils.formatBytes
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/**
 * 一条已经下完、可以展示的文件。
 *
 * @property id 任务标识。
 * @property name 文件名。
 * @property sizeLabel 文件大小。找不到文件时为空，说明由页面来写。
 * @property missing 目录里已经没有这个文件。
 * @property updatedAt 完成时间，用来按天分组。
 * @property absolutePath 文件绝对路径。文件不在时为 null。
 */
internal data class FinishedFile(
    val id: String,
    val name: String,
    val sizeLabel: String,
    val missing: Boolean,
    val updatedAt: Long,
    val absolutePath: String?,
)

/**
 * 同一天下完的文件。
 *
 * @property startMillis 这一天的本地零点。
 * @property files 这一天的文件，最近完成的在前。
 */
internal data class FileDay(
    val startMillis: Long,
    val files: List<FinishedFile>,
)

/**
 * 文件页在界面上要画的内容。
 *
 * @property days 按本地时区的完成日期分组，最近的一天在前。
 */
internal data class FilesUiState(
    val days: List<FileDay>,
)

/**
 * 从任务列表里挑出已完成的，并看目标文件还在不在。
 *
 * @param application 用来取 [SampleApp.downloads]。
 */
internal class FilesViewModel(
    application: Application,
) : AndroidViewModel(application) {
    private val downloads = (application as SampleApp).downloads

    /**
     * 当前要画的内容。
     *
     * 在组合里读取。任务列表变化时会刷新。
     */
    val uiState: FilesUiState
        get() = FilesUiState(
            days = groupByDay(finishedFiles(getApplication(), downloads.records)),
        )

    /** 去掉这一条记录和文件。 */
    fun delete(id: String) {
        downloads.delete(id)
    }
}

private fun finishedFiles(context: Context, records: List<DownloadRecord>): List<FinishedFile> {
    return records
        .filter { it.status == Status.COMPLETED }
        .sortedByDescending { it.updatedAt }
        .map { record ->
            val resolved = runCatching {
                record.request.destination.resolveInside(context.filesDir)
            }.getOrNull()?.takeIf { it.isFile }
            FinishedFile(
                id = record.id,
                name = record.displayName(),
                sizeLabel = if (resolved == null) "" else formatBytes(resolved.length()),
                missing = resolved == null,
                updatedAt = record.updatedAt,
                absolutePath = resolved?.absolutePath,
            )
        }
}

/** 按本地日期分组。输入需要最近完成的在前，分组后顺序不变。 */
private fun groupByDay(files: List<FinishedFile>): List<FileDay> {
    val zone = ZoneId.systemDefault()
    val grouped = linkedMapOf<Long, MutableList<FinishedFile>>()
    files.forEach { file ->
        val start = LocalDate.ofInstant(Instant.ofEpochMilli(file.updatedAt), zone)
            .atStartOfDay(zone)
            .toInstant()
            .toEpochMilli()
        grouped.getOrPut(start) { mutableListOf() }.add(file)
    }
    return grouped.map { (start, dayFiles) -> FileDay(start, dayFiles.toList()) }
}
