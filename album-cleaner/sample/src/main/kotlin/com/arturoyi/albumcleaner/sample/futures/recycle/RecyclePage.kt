/**
 * 回收站。从设置推上来，侧滑和返回都回到设置。
 *
 * @file
 */
package com.arturoyi.albumcleaner.sample.futures.recycle

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.arturoyi.albumcleaner.CleanerState
import com.arturoyi.albumcleaner.ErrorCode
import com.arturoyi.albumcleaner.sample.R
import com.arturoyi.albumcleaner.sample.SampleApp
import com.arturoyi.albumcleaner.sample.futures.common.MediaRow
import com.arturoyi.albumcleaner.sample.settings.ScanMilestone
import com.arturoyi.albumcleaner.sample.theme.Ink
import com.arturoyi.albumcleaner.sample.theme.LineButton
import com.arturoyi.albumcleaner.sample.theme.Muted
import com.arturoyi.albumcleaner.sample.theme.PageHeader
import com.arturoyi.albumcleaner.sample.theme.SampleTheme
import com.arturoyi.albumcleaner.sample.utils.failureText
import com.arturoyi.albumcleaner.sample.utils.formatDay

/**
 * 一次删除在回收站里合成的一行。
 *
 * @property actionId 删除动作。恢复时交回仓库。
 * @property title 这一次有多少张。
 * @property detail 保留到哪一天，或已经恢复。
 * @property restored 已经恢复时不再显示按钮。
 */
internal data class RecycleLine(
    val actionId: String,
    val title: String,
    val detail: String,
    val restored: Boolean,
)

/**
 * 读仓库并画出回收站。
 *
 * @param onBack 回到设置。
 * @param modifier 页面尺寸。
 */
@Composable
internal fun RecyclePage(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val store = (context.applicationContext as SampleApp).album
    val ready = store.cleanerState == CleanerState.READY
    val rows = store.recycle
        .groupBy { entry -> entry.actionId }
        .values
        .map { entries ->
            val restored = entries.all { entry -> entry.restored }
            val expires = entries.maxOf { entry -> entry.expiresAt }
            RecycleLine(
                actionId = entries.first().actionId,
                title = context.getString(R.string.recycle_batch, entries.size),
                detail = if (restored) {
                    context.getString(R.string.recycle_restored)
                } else {
                    context.getString(R.string.recycle_until, formatDay(expires))
                },
                restored = restored,
            )
        }
        .sortedByDescending { line -> line.actionId }
    val emptyLabel = when {
        rows.isNotEmpty() -> null
        store.milestone == ScanMilestone.Never -> context.getString(R.string.empty_recycle_wait)
        store.recycleCursor == null && !store.refreshing -> context.getString(R.string.empty_recycle)
        else -> null
    }
    RecycleContent(
        rows = rows,
        emptyLabel = emptyLabel,
        notice = failureText(context, store.failure?.takeUnless { code -> code == ErrorCode.TASK_CANCELLED }, store.failureDetail),
        canLoadMore = store.recycleCursor != null && ready && !store.scanning,
        loadingMore = store.loadingMore,
        actionsEnabled = ready && !store.refreshing,
        onBack = onBack,
        onRestore = store::restore,
        onLoadMore = store::loadMoreRecycle,
        modifier = modifier,
    )
}

@Composable
private fun RecycleContent(
    rows: List<RecycleLine>,
    emptyLabel: String?,
    notice: String?,
    canLoadMore: Boolean,
    loadingMore: Boolean,
    actionsEnabled: Boolean,
    onBack: () -> Unit,
    onRestore: (String) -> Unit,
    onLoadMore: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier) {
        IconButton(
            onClick = onBack,
            modifier = Modifier.padding(start = 8.dp, top = 8.dp),
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Outlined.ArrowBack,
                contentDescription = stringResource(R.string.action_back),
                tint = Ink,
            )
        }
        PageHeader(
            title = stringResource(R.string.recycle_title),
            subtitle = stringResource(R.string.recycle_subtitle),
            topPadding = 0.dp,
        )
        if (notice != null) {
            Text(
                text = notice,
                color = Ink,
                fontSize = 13.sp,
                modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 8.dp),
            )
        }
        if (emptyLabel != null) {
            Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                Text(
                    text = emptyLabel,
                    color = Muted,
                    fontSize = 15.sp,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(horizontal = 16.dp),
                )
            }
        } else if (rows.isEmpty()) {
            Box(Modifier.weight(1f))
        } else {
            LazyColumn(
                modifier = Modifier.weight(1f).fillMaxWidth(),
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(items = rows, key = { row -> row.actionId }) { row ->
                    MediaRow(
                        title = row.title,
                        detail = row.detail,
                        image = null,
                        action = if (row.restored) null else stringResource(R.string.action_restore),
                        onAction = if (row.restored) null else {
                            { onRestore(row.actionId) }
                        },
                        enabled = actionsEnabled,
                    )
                }
                if (canLoadMore) {
                    item(key = "more") {
                        LineButton(
                            text = stringResource(R.string.action_load_more),
                            onClick = onLoadMore,
                            enabled = !loadingMore && actionsEnabled,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }
            }
        }
    }
}

@Preview(showBackground = true, widthDp = 360, heightDp = 800)
@Composable
private fun RecyclePreview() {
    SampleTheme {
        RecycleContent(
            rows = emptyList(),
            emptyLabel = "删除照片之后，可以在这里恢复",
            notice = null,
            canLoadMore = false,
            loadingMore = false,
            actionsEnabled = false,
            onBack = {},
            onRestore = {},
            onLoadMore = {},
            modifier = Modifier.fillMaxSize(),
        )
    }
}
