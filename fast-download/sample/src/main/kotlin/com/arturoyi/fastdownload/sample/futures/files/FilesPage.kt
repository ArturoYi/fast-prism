/**
 * 文件页：按天下完的文件，用图标网格排开。
 *
 * 点一下交给系统打开。长按再确认删除。页面只画 [FilesUiState]。
 *
 * @file
 */
package com.arturoyi.fastdownload.sample.futures.files

import android.content.Context
import androidx.activity.compose.LocalActivity
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.InsertDriveFile
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.arturoyi.fastdownload.sample.R
import com.arturoyi.fastdownload.sample.theme.ConfirmDeleteDialog
import com.arturoyi.fastdownload.sample.theme.Ink
import com.arturoyi.fastdownload.sample.theme.Muted
import com.arturoyi.fastdownload.sample.theme.PageHeader
import com.arturoyi.fastdownload.sample.theme.SampleTheme
import com.arturoyi.fastdownload.sample.theme.cardSurface
import com.arturoyi.fastdownload.sample.utils.OpenResult
import com.arturoyi.fastdownload.sample.utils.openFinishedFile
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * 展示已完成文件。
 *
 * @param modifier 页面尺寸。
 * @param viewModel 这一页的状态和删除操作。
 */
@Composable
internal fun FilesPage(
    modifier: Modifier = Modifier,
    viewModel: FilesViewModel = viewModel(),
) {
    val state = viewModel.uiState
    FilesContent(
        days = state.days,
        onDelete = viewModel::delete,
        modifier = modifier,
    )
}

@Composable
private fun FilesContent(
    days: List<FileDay>,
    onDelete: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val strings = LocalContext.current
    val host = LocalActivity.current ?: strings
    var pending by remember { mutableStateOf<FinishedFile?>(null) }
    var openMessage by remember { mutableStateOf<String?>(null) }
    val message = if (days.isEmpty()) null else openMessage
    Column(modifier) {
        PageHeader(
            title = stringResource(R.string.files_title),
            subtitle = stringResource(R.string.files_subtitle),
        )
        if (message != null) {
            Text(
                text = message,
                color = Ink,
                fontSize = 13.sp,
                modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 8.dp),
            )
        }
        if (days.isEmpty()) {
            Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                Text(text = stringResource(R.string.files_empty), color = Muted, fontSize = 15.sp)
            }
        } else {
            LazyVerticalGrid(
                columns = GridCells.Adaptive(104.dp),
                modifier = Modifier.weight(1f).fillMaxWidth(),
                contentPadding = PaddingValues(16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                days.forEachIndexed { index, day ->
                    item(
                        key = "day-${day.startMillis}",
                        span = { GridItemSpan(maxLineSpan) },
                        contentType = "day",
                    ) {
                        Text(
                            text = dayTitle(day.startMillis),
                            color = Ink,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = if (index == 0) 0.dp else 8.dp)
                                .semantics { heading() },
                        )
                    }
                    items(items = day.files, key = { "file-${it.id}" }, contentType = { "file" }) { file ->
                        FileTile(
                            file = file,
                            onOpen = { openMessage = openMessage(host, strings, it) },
                            onAskDelete = { pending = it },
                        )
                    }
                }
            }
        }
    }
    pending?.let { file ->
        ConfirmDeleteDialog(
            name = file.name,
            onConfirm = {
                onDelete(file.id)
                pending = null
            },
            onDismiss = { pending = null },
        )
    }
}

@Composable
private fun FileTile(
    file: FinishedFile,
    onOpen: (FinishedFile) -> Unit,
    onAskDelete: (FinishedFile) -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .cardSurface()
            .combinedClickable(
                onClickLabel = stringResource(R.string.action_open),
                onLongClickLabel = stringResource(R.string.action_delete),
                role = Role.Button,
                onClick = { onOpen(file) },
                onLongClick = { onAskDelete(file) },
            )
            .padding(8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Icon(
            imageVector = Icons.AutoMirrored.Outlined.InsertDriveFile,
            contentDescription = null,
            tint = if (file.missing) Muted else Ink,
            modifier = Modifier.size(40.dp),
        )
        Text(
            text = file.name,
            color = Ink,
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
            minLines = 2,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            text = if (file.missing) stringResource(R.string.file_missing_short) else file.sizeLabel,
            color = Muted,
            fontSize = 13.sp,
            textAlign = TextAlign.Center,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun dayTitle(startMillis: Long): String {
    val zone = ZoneId.systemDefault()
    val day = Instant.ofEpochMilli(startMillis).atZone(zone).toLocalDate()
    val today = LocalDate.now(zone)
    return when (day) {
        today -> stringResource(R.string.files_today)
        today.minusDays(1) -> stringResource(R.string.files_yesterday)
        else -> formatDay(day, today.year)
    }
}

@Composable
private fun formatDay(day: LocalDate, thisYear: Int): String {
    val locales = LocalContext.current.resources.configuration.locales
    val locale = if (locales.isEmpty) Locale.getDefault() else locales[0]
    val chinese = locale.language.equals("zh", ignoreCase = true)
    val pattern = when {
        chinese && day.year == thisYear -> "M月d日"
        chinese -> "yyyy年M月d日"
        day.year == thisYear -> "MMM d"
        else -> "MMM d, yyyy"
    }
    return DateTimeFormatter.ofPattern(pattern, locale).format(day)
}

private fun openMessage(host: Context, strings: Context, file: FinishedFile): String? {
    return when (host.openFinishedFile(file.absolutePath)) {
        OpenResult.Opened -> null
        OpenResult.Missing -> strings.getString(R.string.file_missing)
        OpenResult.NoApp -> strings.getString(R.string.file_open_failed)
    }
}

@Preview(showBackground = true, widthDp = 360, heightDp = 800)
@Composable
private fun FilesPreview() {
    val now = System.currentTimeMillis()
    SampleTheme {
        FilesContent(
            days = listOf(
                FileDay(
                    startMillis = now,
                    files = listOf(
                        FinishedFile(
                            id = "a",
                            name = "sample-1mb.bin",
                            sizeLabel = "1.0 MB",
                            missing = false,
                            updatedAt = now,
                            absolutePath = null,
                        ),
                        FinishedFile(
                            id = "b",
                            name = "notes.pdf",
                            sizeLabel = "220 KB",
                            missing = false,
                            updatedAt = now,
                            absolutePath = null,
                        ),
                    ),
                ),
                FileDay(
                    startMillis = now - 3L * 24L * 60L * 60L * 1000L,
                    files = listOf(
                        FinishedFile(
                            id = "c",
                            name = "photo.jpg",
                            sizeLabel = "3.2 MB",
                            missing = true,
                            updatedAt = now,
                            absolutePath = null,
                        ),
                    ),
                ),
            ),
            onDelete = {},
            modifier = Modifier.fillMaxSize(),
        )
    }
}
