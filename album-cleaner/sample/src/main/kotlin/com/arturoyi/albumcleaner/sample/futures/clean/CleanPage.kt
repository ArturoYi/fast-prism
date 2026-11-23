/**
 * 清理页：可释放的空间、扫描，以及建议删除的照片。
 *
 * 扫描按钮只把请求交出去。权限和真正的开始由外壳决定，这里不会自己开扫。
 *
 * @file
 */
package com.arturoyi.albumcleaner.sample.futures.clean

import android.os.Build
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.arturoyi.albumcleaner.CleanerState
import com.arturoyi.albumcleaner.sample.R
import com.arturoyi.albumcleaner.sample.SampleApp
import com.arturoyi.albumcleaner.sample.futures.common.MediaRow
import com.arturoyi.albumcleaner.sample.settings.ScanMilestone
import com.arturoyi.albumcleaner.sample.theme.ConfirmDialog
import com.arturoyi.albumcleaner.sample.theme.Ink
import com.arturoyi.albumcleaner.sample.theme.LineButton
import com.arturoyi.albumcleaner.sample.theme.Muted
import com.arturoyi.albumcleaner.sample.theme.PageHeader
import com.arturoyi.albumcleaner.sample.theme.PrimaryButton
import com.arturoyi.albumcleaner.sample.theme.SampleTheme
import com.arturoyi.albumcleaner.sample.theme.ScanMeter
import com.arturoyi.albumcleaner.sample.theme.cardSurface
import com.arturoyi.albumcleaner.sample.utils.ByteAmount
import com.arturoyi.albumcleaner.sample.utils.byteAmount
import com.arturoyi.albumcleaner.sample.utils.failureText
import com.arturoyi.albumcleaner.sample.utils.formatBytes
import com.arturoyi.albumcleaner.sample.utils.label

/**
 * 一行候选在界面上需要的字和图。
 *
 * @property assetId 媒体库主键。删除时交回仓库。
 * @property title 为什么建议删除。
 * @property detail 建议留下哪张，以及大约多少字节。
 */
internal data class CandidateLine(
    val assetId: Long,
    val title: String,
    val detail: String,
)

/**
 * 读仓库并画出清理页。
 *
 * @param permissionGranted 现在能不能读至少一部分照片。
 * @param onStartScan 用户要开始或继续扫描。还没有权限时由外壳去申请。
 * @param modifier 页面尺寸。
 */
@Composable
internal fun CleanPage(
    permissionGranted: Boolean,
    onStartScan: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val store = (context.applicationContext as SampleApp).album
    val milestone = store.milestone
    val scanning = store.scanning
    val ready = store.cleanerState == CleanerState.READY
    val space = store.space
    val amount = if (space == null) ByteAmount(number = "—", unit = "") else byteAmount(space.totalEstimatedBytes)
    val countLabel = if (space == null) {
        null
    } else {
        context.getString(R.string.candidate_count, store.candidateTotal ?: store.candidates.size)
    }
    val applied = space?.appliedBytes ?: 0L
    val appliedLabel = if (applied > 0L) context.getString(R.string.applied, formatBytes(applied)) else null
    val progressLabel = if (scanning) {
        progressText(store.phase?.label(context), store.scanned, store.scanTotal)
    } else {
        null
    }
    val fraction = scanFraction(scanning, store.scanned, store.scanTotal)
    val separator = context.getString(R.string.reason_separator)
    val unknown = context.getString(R.string.reason_unknown)
    val rows = store.candidates.map { item ->
        val reasons = item.reasonCodes.joinToString(separator = separator) { code -> code.label(context) }
        CandidateLine(
            assetId = item.assetId,
            title = reasons.ifBlank { unknown },
            detail = context.getString(
                R.string.candidate_detail,
                item.keepAssetId,
                formatBytes(item.estimatedBytes),
            ),
        )
    }
    val images = store.candidateImages
    val emptyLabel = when {
        rows.isNotEmpty() -> null
        milestone == ScanMilestone.Never -> context.getString(R.string.empty_never)
        space == null -> null
        milestone == ScanMilestone.Partial -> context.getString(R.string.empty_partial)
        else -> context.getString(R.string.empty_none)
    }
    val buttonLabel = when {
        scanning -> stringResource(R.string.action_cancel)
        milestone == ScanMilestone.Never -> stringResource(R.string.action_start)
        milestone == ScanMilestone.Partial -> stringResource(R.string.action_resume)
        else -> stringResource(R.string.action_rescan)
    }
    val buttonEnabled = store.opened && when {
        scanning -> true
        store.refreshing || store.loadingMore -> false
        else -> ready
    }
    CleanContent(
        amount = amount,
        countLabel = countLabel,
        appliedLabel = appliedLabel,
        progressLabel = progressLabel,
        fraction = fraction,
        notice = failureText(context, store.failure, store.failureDetail),
        permissionMissing = !permissionGranted,
        loadingIndex = milestone != ScanMilestone.Never && space == null && !scanning && store.failure == null,
        rows = rows,
        images = images,
        emptyLabel = emptyLabel,
        buttonLabel = buttonLabel,
        buttonEnabled = buttonEnabled,
        cancel = scanning,
        canLoadMore = store.candidateCursor != null && ready && !scanning,
        loadingMore = store.loadingMore,
        actionsEnabled = ready && !store.refreshing,
        onButton = {
            if (scanning) store.cancelScan() else onStartScan()
        },
        onDelete = store::delete,
        onLoadMore = store::loadMoreCandidates,
        modifier = modifier,
    )
}

@Composable
private fun CleanContent(
    amount: ByteAmount,
    countLabel: String?,
    appliedLabel: String?,
    progressLabel: String?,
    fraction: Float,
    notice: String?,
    permissionMissing: Boolean,
    loadingIndex: Boolean,
    rows: List<CandidateLine>,
    images: Map<Long, androidx.compose.ui.graphics.ImageBitmap>,
    emptyLabel: String?,
    buttonLabel: String,
    buttonEnabled: Boolean,
    cancel: Boolean,
    canLoadMore: Boolean,
    loadingMore: Boolean,
    actionsEnabled: Boolean,
    onButton: () -> Unit,
    onDelete: (Long) -> Unit,
    onLoadMore: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var pending by remember { mutableStateOf<Long?>(null) }
    Column(modifier) {
        PageHeader(
            title = stringResource(R.string.clean_title),
            subtitle = stringResource(R.string.clean_subtitle),
        )
        Column(Modifier.padding(horizontal = 16.dp).cardSurface().padding(16.dp)) {
            Text(text = stringResource(R.string.space_caption), color = Muted, fontSize = 13.sp)
            Spacer(Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.Bottom) {
                Text(
                    text = amount.number,
                    color = Ink,
                    fontSize = 40.sp,
                    fontWeight = FontWeight.Bold,
                )
                if (amount.unit.isNotEmpty()) {
                    Text(
                        text = amount.unit,
                        color = Muted,
                        fontSize = 15.sp,
                        modifier = Modifier.padding(start = 8.dp, bottom = 8.dp),
                    )
                }
            }
            if (countLabel != null) {
                Spacer(Modifier.height(8.dp))
                Text(text = countLabel, color = Ink, fontSize = 15.sp, fontWeight = FontWeight.Bold)
            }
            if (appliedLabel != null) {
                Spacer(Modifier.height(8.dp))
                Text(text = appliedLabel, color = Muted, fontSize = 13.sp)
            }
            if (progressLabel != null) {
                Spacer(Modifier.height(8.dp))
                Text(text = progressLabel, color = Muted, fontSize = 13.sp)
                Spacer(Modifier.height(8.dp))
                ScanMeter(fraction = fraction, description = stringResource(R.string.scan_progress))
            }
            if (loadingIndex) {
                Spacer(Modifier.height(8.dp))
                Text(text = stringResource(R.string.loading_index), color = Muted, fontSize = 13.sp)
            }
            if (permissionMissing) {
                Spacer(Modifier.height(8.dp))
                Text(text = stringResource(R.string.permission_missing), color = Muted, fontSize = 13.sp)
            }
            if (notice != null) {
                Spacer(Modifier.height(8.dp))
                Text(text = notice, color = Ink, fontSize = 13.sp)
            }
        }
        Spacer(Modifier.height(16.dp))
        val buttonModifier = Modifier.padding(horizontal = 16.dp).fillMaxWidth()
        if (cancel) {
            LineButton(text = buttonLabel, onClick = onButton, enabled = buttonEnabled, modifier = buttonModifier)
        } else {
            PrimaryButton(text = buttonLabel, onClick = onButton, enabled = buttonEnabled, modifier = buttonModifier)
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
            Spacer(Modifier.weight(1f))
        } else {
            LazyColumn(
                modifier = Modifier.weight(1f).fillMaxWidth(),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(items = rows, key = { row -> row.assetId }) { row ->
                    MediaRow(
                        title = row.title,
                        detail = row.detail,
                        image = images[row.assetId],
                        action = stringResource(R.string.action_delete),
                        onAction = { pending = row.assetId },
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
    pending?.let { assetId ->
        val recoverable = Build.VERSION.SDK_INT >= 30
        ConfirmDialog(
            title = stringResource(R.string.delete_title),
            body = stringResource(if (recoverable) R.string.delete_body_trash else R.string.delete_body_gone),
            confirm = stringResource(R.string.delete_confirm),
            dismiss = stringResource(R.string.delete_keep),
            onConfirm = {
                pending = null
                onDelete(assetId)
            },
            onDismiss = { pending = null },
        )
    }
}

private fun progressText(phase: String?, scanned: Int, total: Int?): String {
    val name = phase ?: ""
    return if (total != null && total > 0) "$name $scanned/$total" else "$name $scanned"
}

private fun scanFraction(scanning: Boolean, scanned: Int, total: Int?): Float {
    if (!scanning || total == null || total <= 0) return 0f
    return scanned.toFloat() / total.toFloat()
}

@Preview(showBackground = true, widthDp = 360, heightDp = 800)
@Composable
private fun CleanPreview() {
    SampleTheme {
        CleanContent(
            amount = ByteAmount(number = "—", unit = ""),
            countLabel = null,
            appliedLabel = null,
            progressLabel = null,
            fraction = 0f,
            notice = null,
            permissionMissing = true,
            loadingIndex = false,
            rows = emptyList(),
            images = emptyMap(),
            emptyLabel = "点下方按钮，开始第一次扫描",
            buttonLabel = "开始扫描",
            buttonEnabled = true,
            cancel = false,
            canLoadMore = false,
            loadingMore = false,
            actionsEnabled = false,
            onButton = {},
            onDelete = {},
            onLoadMore = {},
            modifier = Modifier.fillMaxSize(),
        )
    }
}
