/**
 * 分组页：每一组建议留下的那张，以及组里有多少张。
 *
 * @file
 */
package com.arturoyi.albumcleaner.sample.futures.groups

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import com.arturoyi.albumcleaner.sample.R
import com.arturoyi.albumcleaner.sample.SampleApp
import com.arturoyi.albumcleaner.sample.futures.common.MediaRow
import com.arturoyi.albumcleaner.sample.settings.ScanMilestone
import com.arturoyi.albumcleaner.sample.theme.LineButton
import com.arturoyi.albumcleaner.sample.theme.Muted
import com.arturoyi.albumcleaner.sample.theme.PageHeader
import com.arturoyi.albumcleaner.sample.theme.SampleTheme
import com.arturoyi.albumcleaner.sample.utils.formatBytes
import com.arturoyi.albumcleaner.sample.utils.label

/**
 * 一组在界面上的字。
 *
 * @property groupId 组标识。
 * @property keepAssetId 建议留下的照片，用来对缩略图。
 * @property title 分组依据。
 * @property detail 张数和体积。
 */
internal data class GroupLine(
    val groupId: String,
    val keepAssetId: Long,
    val title: String,
    val detail: String,
)

/**
 * 读仓库并画出分组。
 *
 * @param modifier 页面尺寸。
 */
@Composable
internal fun GroupsPage(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val store = (context.applicationContext as SampleApp).album
    val ready = store.cleanerState == CleanerState.READY
    val rows = store.groups.map { group ->
        GroupLine(
            groupId = group.groupId,
            keepAssetId = group.keepAssetId,
            title = group.type.label(context),
            detail = context.getString(R.string.group_detail, group.memberCount, formatBytes(group.totalBytes)),
        )
    }
    val emptyLabel = when {
        rows.isNotEmpty() -> null
        store.milestone == ScanMilestone.Never -> context.getString(R.string.empty_groups)
        store.space == null -> null
        store.milestone == ScanMilestone.Partial -> context.getString(R.string.empty_partial_groups)
        else -> context.getString(R.string.empty_groups_none)
    }
    GroupsContent(
        rows = rows,
        images = store.groupImages,
        emptyLabel = emptyLabel,
        canLoadMore = store.groupCursor != null && ready && !store.scanning,
        loadingMore = store.loadingMore,
        onLoadMore = store::loadMoreGroups,
        modifier = modifier,
    )
}

@Composable
private fun GroupsContent(
    rows: List<GroupLine>,
    images: Map<Long, androidx.compose.ui.graphics.ImageBitmap>,
    emptyLabel: String?,
    canLoadMore: Boolean,
    loadingMore: Boolean,
    onLoadMore: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier) {
        PageHeader(
            title = stringResource(R.string.groups_title),
            subtitle = stringResource(R.string.groups_subtitle),
        )
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
                items(items = rows, key = { row -> row.groupId }) { row ->
                    MediaRow(
                        title = row.title,
                        detail = row.detail,
                        image = images[row.keepAssetId],
                        action = null,
                        onAction = null,
                        enabled = false,
                    )
                }
                if (canLoadMore) {
                    item(key = "more") {
                        LineButton(
                            text = stringResource(R.string.action_load_more),
                            onClick = onLoadMore,
                            enabled = !loadingMore,
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
private fun GroupsPreview() {
    SampleTheme {
        GroupsContent(
            rows = emptyList(),
            images = emptyMap(),
            emptyLabel = "扫描之后，相似的照片会出现在这里",
            canLoadMore = false,
            loadingMore = false,
            onLoadMore = {},
            modifier = Modifier.fillMaxSize(),
        )
    }
}
