/**
 * 下载页：顶部填链接，下面是还没完成的任务。每条都有开始、暂停和删除。点空白处收起输入。
 *
 * 页面只画 [TasksUiState]。添加、开始、暂停和删除都交给 [TasksViewModel]。
 *
 * @file
 */
package com.arturoyi.fastdownload.sample.futures.tasks

import android.content.Context
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusManager
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.SoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.arturoyi.fastdownload.api.downloadRequest
import com.arturoyi.fastdownload.sample.R
import com.arturoyi.fastdownload.core.model.DownloadRecord
import com.arturoyi.fastdownload.core.state.Status
import com.arturoyi.fastdownload.sample.DOWNLOAD_DIRECTORY
import com.arturoyi.fastdownload.sample.theme.ConfirmDeleteDialog
import com.arturoyi.fastdownload.sample.theme.DownloadBar
import com.arturoyi.fastdownload.sample.theme.Ink
import com.arturoyi.fastdownload.sample.theme.Line
import com.arturoyi.fastdownload.sample.theme.LineButton
import com.arturoyi.fastdownload.sample.theme.Muted
import com.arturoyi.fastdownload.sample.theme.PageHeader
import com.arturoyi.fastdownload.sample.theme.Paper
import com.arturoyi.fastdownload.sample.theme.PrimaryButton
import com.arturoyi.fastdownload.sample.theme.SampleTheme
import com.arturoyi.fastdownload.sample.theme.cardSurface
import com.arturoyi.fastdownload.sample.utils.ByteAmount
import com.arturoyi.fastdownload.sample.utils.byteAmount
import com.arturoyi.fastdownload.sample.utils.displayName
import com.arturoyi.fastdownload.sample.utils.formatBytes
import com.arturoyi.fastdownload.sample.utils.formatEta
import com.arturoyi.fastdownload.sample.utils.formatSpeed
import com.arturoyi.fastdownload.sample.utils.progressFraction
import com.arturoyi.fastdownload.sample.utils.statusText

/**
 * 添加任务，并操作还没完成的下载。
 *
 * @param modifier 页面尺寸。
 * @param viewModel 这一页的状态和操作。
 */
@Composable
internal fun TasksPage(
    modifier: Modifier = Modifier,
    viewModel: TasksViewModel = viewModel(),
) {
    val state = viewModel.uiState
    TasksContent(
        url = state.url,
        error = state.error,
        tasks = state.tasks,
        onUrlChange = viewModel::updateUrl,
        onSubmit = viewModel::submitUrl,
        onQuick = viewModel::quick,
        onStart = viewModel::start,
        onPause = viewModel::pause,
        onDelete = viewModel::delete,
        modifier = modifier,
        sample = state.sample,
    )
}

@Composable
private fun TasksContent(
    url: String,
    error: UrlError?,
    tasks: List<DownloadRecord>,
    onUrlChange: (String) -> Unit,
    onSubmit: () -> Boolean,
    onQuick: (QuickDownload) -> Unit,
    onStart: (String) -> Unit,
    onPause: (String) -> Unit,
    onDelete: (String) -> Unit,
    modifier: Modifier = Modifier,
    sample: QuickDownload? = sampleDownload,
) {
    val focusManager = LocalFocusManager.current
    val keyboard = LocalSoftwareKeyboardController.current
    var pending by remember { mutableStateOf<DownloadRecord?>(null) }
    val submit = {
        if (onSubmit()) dismissInput(focusManager, keyboard)
    }
    val fieldColors = OutlinedTextFieldDefaults.colors(
        focusedTextColor = Ink,
        unfocusedTextColor = Ink,
        errorTextColor = Ink,
        focusedContainerColor = Paper,
        unfocusedContainerColor = Paper,
        errorContainerColor = Paper,
        cursorColor = Ink,
        errorCursorColor = Ink,
        focusedBorderColor = Ink,
        unfocusedBorderColor = Line,
        errorBorderColor = Ink,
        focusedLabelColor = Muted,
        unfocusedLabelColor = Muted,
        errorLabelColor = Ink,
        focusedPlaceholderColor = Muted,
        unfocusedPlaceholderColor = Muted,
    )
    Column(modifier.hideInputOnBlank(focusManager, keyboard)) {
        PageHeader(
            title = stringResource(R.string.downloads_title),
            subtitle = stringResource(
                if (sample != null) R.string.downloads_subtitle_sample else R.string.downloads_subtitle,
            ),
        )
        Row(
            modifier = Modifier.padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Box(Modifier.weight(1f)) {
                OutlinedTextField(
                    value = url,
                    onValueChange = onUrlChange,
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text(stringResource(R.string.url_label)) },
                    placeholder = { Text(stringResource(R.string.url_placeholder)) },
                    singleLine = true,
                    isError = error != null,
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Uri,
                        imeAction = ImeAction.Done,
                    ),
                    keyboardActions = KeyboardActions(onDone = { submit() }),
                    shape = RoundedCornerShape(8.dp),
                    colors = fieldColors,
                )
            }
            PrimaryButton(text = stringResource(R.string.action_add), onClick = submit)
        }
        if (error != null) {
            Text(
                text = error.text(),
                color = Ink,
                fontSize = 13.sp,
                modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 8.dp),
            )
        }
        if (sample != null) {
            LineButton(
                text = stringResource(R.string.sample_1gb),
                onClick = {
                    onQuick(sample)
                    dismissInput(focusManager, keyboard)
                },
                modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 16.dp),
            )
        }
        if (tasks.isEmpty()) {
            Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                Text(text = stringResource(R.string.downloads_empty), color = Muted, fontSize = 15.sp)
            }
        } else {
            LazyColumn(
                modifier = Modifier.weight(1f).fillMaxWidth(),
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(items = tasks, key = { it.id }) { record ->
                    TaskCard(
                        record = record,
                        onStart = onStart,
                        onPause = onPause,
                        onAskDelete = { pending = it },
                    )
                }
            }
        }
    }
    pending?.let { record ->
        ConfirmDeleteDialog(
            name = record.displayName(),
            onConfirm = {
                onDelete(record.id)
                pending = null
            },
            onDismiss = { pending = null },
        )
    }
}

@Composable
private fun TaskCard(
    record: DownloadRecord,
    onStart: (String) -> Unit,
    onPause: (String) -> Unit,
    onAskDelete: (DownloadRecord) -> Unit,
) {
    val context = LocalContext.current
    val fraction = progressFraction(record.downloadedBytes, record.totalBytes)
    val percent = (fraction * 100).toInt()
    val downloaded = byteAmount(record.downloadedBytes)
    val total = formatBytes(record.totalBytes)
    val pace = downloadPace(record)
    val startEnabled = canStart(record.status)
    val pauseEnabled = canPause(record.status)
    Column(
        Modifier.fillMaxWidth().cardSurface().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            text = record.displayName(),
            color = Ink,
            fontSize = 15.sp,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            text = record.request.url,
            color = Muted,
            fontSize = 13.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            text = context.statusText(record),
            color = Muted,
            fontSize = 13.sp,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Row(
                Modifier.clearAndSetSemantics { contentDescription = "$percent%" },
                verticalAlignment = Alignment.CenterVertically,
            ) {
                RollingDigits(
                    text = percent.toString(),
                    places = 3,
                    color = Ink,
                    fontSize = 32.sp,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    text = "%",
                    color = Ink,
                    fontSize = 32.sp,
                    fontWeight = FontWeight.Bold,
                )
            }
            Column(Modifier.padding(start = 8.dp).weight(1f)) {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clearAndSetSemantics { contentDescription = amountLabel(downloaded, total) },
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    RollingDigits(
                        text = downloaded.number,
                        places = 8,
                        color = Muted,
                        fontSize = 13.sp,
                    )
                    if (downloaded.unit.isNotEmpty()) {
                        Text(
                            text = " ${downloaded.unit}",
                            color = Muted,
                            fontSize = 13.sp,
                            maxLines = 1,
                            softWrap = false,
                        )
                    }
                    Text(
                        text = " / $total",
                        color = Muted,
                        fontSize = 13.sp,
                        maxLines = 1,
                        softWrap = false,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false),
                    )
                }
                if (pace != null) {
                    Text(
                        text = pace,
                        color = Muted,
                        fontSize = 13.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
        DownloadBar(fraction = fraction)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TaskAction(
                text = stringResource(R.string.action_start),
                enabled = startEnabled,
                primary = startEnabled,
                onClick = { onStart(record.id) },
                modifier = Modifier.weight(1f),
            )
            TaskAction(
                text = stringResource(R.string.action_pause),
                enabled = pauseEnabled,
                primary = pauseEnabled,
                onClick = { onPause(record.id) },
                modifier = Modifier.weight(1f),
            )
            TaskAction(
                text = stringResource(R.string.action_delete),
                enabled = true,
                primary = false,
                onClick = { onAskDelete(record) },
                modifier = Modifier.weight(1f),
            )
        }
    }
}

/**
 * 任务上的一个操作。当前能做的用实心按钮。不能做的仍占位置，只是不能点。
 */
@Composable
private fun TaskAction(
    text: String,
    enabled: Boolean,
    primary: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    if (primary && enabled) {
        PrimaryButton(
            text = text,
            onClick = onClick,
            modifier = modifier,
            horizontalPadding = 8.dp,
        )
    } else {
        LineButton(
            text = text,
            onClick = onClick,
            modifier = modifier,
            enabled = enabled,
            horizontalPadding = 8.dp,
        )
    }
}

/** 速度和剩余时间最多每秒换一次，避免跟着每次进度跳。 */
private const val PACE_HOLD_NANOS: Long = 1_000_000_000L

/**
 * 下载中的速度和剩余时间。
 *
 * 进度回调比一秒更密。这里按秒取样，第一次测到速度时立刻显示。
 */
@Composable
private fun downloadPace(record: DownloadRecord): String? {
    val context = LocalContext.current
    val sampledAt = remember(record.id) { longArrayOf(System.nanoTime()) }
    var speed by remember(record.id) { mutableLongStateOf(record.speedBps) }
    var eta by remember(record.id) { mutableLongStateOf(record.etaMillis) }
    if (record.status == Status.DOWNLOADING) {
        SideEffect {
            val now = System.nanoTime()
            val due = now - sampledAt[0] >= PACE_HOLD_NANOS
            val firstSpeed = speed <= 0L && record.speedBps > 0L
            if (!due && !firstSpeed) return@SideEffect
            sampledAt[0] = now
            if (speed != record.speedBps) speed = record.speedBps
            if (eta != record.etaMillis) eta = record.etaMillis
        }
    }
    if (record.status != Status.DOWNLOADING) return null
    return listOfNotNull(formatSpeed(speed), context.formatEta(eta))
        .joinToString(" · ")
        .ifBlank { null }
}

/** 已下载和总体积读成一句。总体积不参与滚动。 */
private fun amountLabel(downloaded: ByteAmount, total: String): String {
    val head = if (downloaded.unit.isEmpty()) downloaded.number else "${downloaded.number} ${downloaded.unit}"
    return "$head / $total"
}

/**
 * 点在按钮和输入框之外时收起输入。
 *
 * 等子控件处理完再看这次点击。按钮、输入框已经消费的点击保持原样，滑动也不收起。
 */
private fun Modifier.hideInputOnBlank(
    focusManager: FocusManager,
    keyboard: SoftwareKeyboardController?,
): Modifier = pointerInput(focusManager, keyboard) {
    awaitEachGesture {
        val down = awaitFirstDown(
            pass = PointerEventPass.Final,
            requireUnconsumed = false,
        )
        val up = waitForUpOrCancellation(pass = PointerEventPass.Final)
        if (up != null && !down.isConsumed && !up.isConsumed) {
            dismissInput(focusManager, keyboard)
        }
    }
}

/**
 * 去掉输入框焦点，并收起键盘。
 *
 * @param focusManager 当前页面的焦点。
 * @param keyboard 软键盘。没有控制器时只清焦点。
 */
private fun dismissInput(focusManager: FocusManager, keyboard: SoftwareKeyboardController?) {
    focusManager.clearFocus()
    keyboard?.hide()
}

@Composable
private fun UrlError.text(): String = when (this) {
    UrlError.Empty -> stringResource(R.string.url_error_empty)
    UrlError.NeedHttps -> stringResource(R.string.url_error_https)
    UrlError.AlreadyAdded -> stringResource(R.string.url_error_exists)
    is UrlError.Unusable -> detail?.takeIf { it.isNotBlank() }
        ?: stringResource(R.string.url_error_invalid)
}

@Preview(showBackground = true, widthDp = 360, heightDp = 800)
@Composable
private fun TasksPreview() {
    val request = downloadRequest("https://example.com/sample-8mb.bin") {
        saveTo(DOWNLOAD_DIRECTORY, "sample-8mb.bin")
    }
    val record = DownloadRecord(
        id = "preview",
        request = request,
        status = Status.DOWNLOADING,
        createdAt = 1L,
        updatedAt = 1L,
        totalBytes = 8_388_608L,
        downloadedBytes = 3_145_728L,
        speedBps = 1_200_000L,
        etaMillis = 12_000L,
    )
    SampleTheme {
        TasksContent(
            url = "",
            error = null,
            tasks = listOf(record),
            onUrlChange = {},
            onSubmit = { false },
            onQuick = {},
            onStart = {},
            onPause = {},
            onDelete = {},
            modifier = Modifier.fillMaxSize(),
        )
    }
}
