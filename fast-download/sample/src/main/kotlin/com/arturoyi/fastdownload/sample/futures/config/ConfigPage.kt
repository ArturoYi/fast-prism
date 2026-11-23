/**
 * 配置页：把当前下载器的配置按用途分成几块说明。
 *
 * 这是设置上面单独的一页。侧滑和返回按钮都由外壳关掉这一页。
 * 页面只画 [ConfigUiState]。只读的值由 [ConfigViewModel] 从下载器读出。
 * 可以改的项把选择交回 [ConfigViewModel.choose]。
 *
 * @file
 */
package com.arturoyi.fastdownload.sample.futures.config

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.ui.res.stringResource
import com.arturoyi.fastdownload.sample.R
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.arturoyi.fastdownload.sample.theme.Ink
import com.arturoyi.fastdownload.sample.theme.Line
import com.arturoyi.fastdownload.sample.theme.Muted
import com.arturoyi.fastdownload.sample.theme.OnInk
import com.arturoyi.fastdownload.sample.theme.PageHeader
import com.arturoyi.fastdownload.sample.theme.Paper
import com.arturoyi.fastdownload.sample.theme.SampleTheme
import com.arturoyi.fastdownload.sample.theme.cardSurface

/**
 * 画出分栏。通知权限那一行由外壳传入。
 *
 * @param notificationLabel 通知权限现在怎么描述。
 * @param onBack 回到设置。按钮调用它；侧滑由返回栈处理，最后也走到这里。预览里可以不传。
 * @param modifier 页面尺寸。
 * @param viewModel 把下载器配置写成几块说明，并接收修改。
 */
@Composable
internal fun ConfigPage(
    notificationLabel: String,
    onBack: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
    viewModel: ConfigViewModel = viewModel(),
) {
    val strings = LocalContext.current
    ConfigContent(
        sections = viewModel.state(strings, notificationLabel).sections,
        onSelect = viewModel::choose,
        onBack = onBack,
        modifier = modifier,
    )
}

@Composable
private fun ConfigContent(
    sections: List<SettingSection>,
    onSelect: (SettingKey, String) -> Unit,
    modifier: Modifier = Modifier,
    onBack: (() -> Unit)? = null,
) {
    var openId by rememberSaveable { mutableStateOf<String?>(null) }
    val openSection = sections.find { it.id == openId }
    Column(modifier) {
        if (onBack != null) {
            IconButton(
                onClick = onBack,
                modifier = Modifier.padding(start = 4.dp, top = 8.dp),
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Outlined.ArrowBack,
                    contentDescription = stringResource(R.string.action_back),
                    tint = Ink,
                )
            }
        }
        PageHeader(
            title = stringResource(R.string.config_title),
            subtitle = stringResource(R.string.config_subtitle),
            topPadding = if (onBack == null) 16.dp else 0.dp,
        )
        LazyColumn(
            modifier = Modifier.weight(1f).fillMaxWidth(),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            items(items = sections, key = { it.id }) { section ->
                SectionCard(
                    section = section,
                    onShowBody = { openId = section.id },
                    onSelect = onSelect,
                )
            }
        }
    }
    if (openSection != null) {
        SectionBodyDialog(
            title = openSection.title,
            body = openSection.body,
            onDismiss = { openId = null },
        )
    }
}

@Composable
private fun SectionCard(
    section: SettingSection,
    onShowBody: () -> Unit,
    onSelect: (SettingKey, String) -> Unit,
) {
    Column(Modifier.fillMaxWidth().cardSurface().padding(16.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = section.title,
                color = Ink,
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.weight(1f),
            )
            if (section.body.isNotBlank()) {
                IconButton(onClick = onShowBody, modifier = Modifier.padding(start = 8.dp)) {
                    Icon(
                        imageVector = Icons.Outlined.Info,
                        contentDescription = stringResource(R.string.config_section_info, section.title),
                        tint = Muted,
                    )
                }
            }
        }
        section.rows.forEach { row ->
            SettingLine(row, onSelect)
        }
        section.stack?.let { row ->
            SettingFacts(
                label = row.label,
                value = row.value,
                selectValue = true,
                modifier = Modifier.padding(top = 16.dp),
            )
        }
    }
}

private val labelStyle: TextStyle
    @Composable get() = TextStyle(
        color = Muted,
        fontSize = 15.sp,
        lineHeight = 22.sp,
    )

private val valueStyle: TextStyle
    @Composable get() = TextStyle(
        color = Ink,
        fontSize = 15.sp,
        fontWeight = FontWeight.Medium,
        lineHeight = 22.sp,
    )

/**
 * 名字在左，值靠右。值只占自己的宽度，短名字不再把长文案挤进半行。
 *
 * 仍排不进同一行时，值换到下一行并从左读完。
 */
@Composable
private fun SettingLine(row: SettingRow, onSelect: (SettingKey, String) -> Unit) {
    Column(Modifier.fillMaxWidth().padding(top = 16.dp)) {
        SettingFacts(label = row.label, value = row.value)
        when (val control = row.control) {
            is SettingControl.Segments -> LabeledSwitch(
                choices = control.choices,
                selectedId = control.selectedId,
                onSelect = { onSelect(control.key, it) },
                expand = true,
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
            )
            is SettingControl.Options -> OptionGrid(
                choices = control.choices,
                selectedId = control.selectedId,
                onSelect = { onSelect(control.key, it) },
            )
            null -> Unit
        }
    }
}

@Composable
private fun SettingFacts(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    selectValue: Boolean = false,
) {
    val measurer = rememberTextMeasurer()
    BoxWithConstraints(modifier.fillMaxWidth()) {
        val gapPx = px(16.dp)
        val safetyPx = px(4.dp)
        val oneLine = singleLineWidth(measurer, label, labelStyle) +
            gapPx +
            singleLineWidth(measurer, value, valueStyle) +
            safetyPx <= constraints.maxWidth
        if (oneLine) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(text = label, style = labelStyle, maxLines = 1, softWrap = false)
                SettingValueText(
                    text = value,
                    select = selectValue,
                    align = TextAlign.End,
                    maxLines = 1,
                    modifier = Modifier.weight(1f).padding(start = 16.dp),
                )
            }
        } else {
            Column(Modifier.fillMaxWidth()) {
                Text(text = label, style = labelStyle)
                SettingValueText(
                    text = value,
                    select = selectValue,
                    align = TextAlign.Start,
                    maxLines = Int.MAX_VALUE,
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                )
            }
        }
    }
}

@Composable
private fun SettingValueText(
    text: String,
    select: Boolean,
    align: TextAlign,
    maxLines: Int,
    modifier: Modifier = Modifier,
) {
    if (select) {
        SelectionContainer(modifier) {
            ValueLine(text = text, align = align, maxLines = maxLines, modifier = Modifier.fillMaxWidth())
        }
    } else {
        ValueLine(text = text, align = align, maxLines = maxLines, modifier = modifier)
    }
}

@Composable
private fun ValueLine(text: String, align: TextAlign, maxLines: Int, modifier: Modifier) {
    Text(
        text = text,
        modifier = modifier,
        style = valueStyle,
        textAlign = align,
        lineHeight = 22.sp,
        maxLines = maxLines,
        softWrap = maxLines != 1,
        overflow = if (maxLines == 1) TextOverflow.Ellipsis else TextOverflow.Clip,
    )
}

/**
 * 左右（或多段）都写出状态，选中的一段是当前值。
 *
 * 两段时靠在名字右边。更多段能排进一行时铺满这一行；
 * 排不下时每段单独占一行，整句都能看完，不再截成省略号。
 *
 * @param choices 状态。横排时从左到右，竖排时从上到下。
 * @param selectedId 当前选中的标识。还没选过时为 null。
 * @param onSelect 点到某一段。
 * @param expand 为 true 时按可用宽度决定横排或竖排。
 * @param modifier 外边距和宽度。
 */
@Composable
private fun LabeledSwitch(
    choices: List<SettingChoice>,
    selectedId: String?,
    onSelect: (String) -> Unit,
    modifier: Modifier = Modifier,
    expand: Boolean = false,
) {
    if (!expand) {
        SegmentRow(choices, selectedId, onSelect, modifier.height(48.dp), expand = false)
    } else {
        val measurer = rememberTextMeasurer()
        BoxWithConstraints(modifier) {
            val oneLine = segmentsFitOneLine(
                measurer = measurer,
                labels = choices.map { it.label },
                widthPx = constraints.maxWidth,
                dividerPx = px(1.dp),
                padPx = px(16.dp),
                safetyPx = px(4.dp),
                textStyle = valueStyle,
            )
            if (oneLine) {
                SegmentRow(
                    choices = choices,
                    selectedId = selectedId,
                    onSelect = onSelect,
                    modifier = Modifier.fillMaxWidth().height(48.dp),
                    expand = true,
                )
            } else {
                SegmentColumn(choices, selectedId, onSelect, Modifier.fillMaxWidth())
            }
        }
    }
}

@Composable
private fun SegmentRow(
    choices: List<SettingChoice>,
    selectedId: String?,
    onSelect: (String) -> Unit,
    modifier: Modifier,
    expand: Boolean,
) {
    val shape = RoundedCornerShape(8.dp)
    Row(
        modifier.clip(shape).border(width = 1.dp, color = Line, shape = shape),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        choices.forEachIndexed { index, choice ->
            if (index > 0) {
                Box(Modifier.fillMaxHeight().width(1.dp).background(Line))
            }
            SwitchSegment(
                text = choice.label,
                selected = choice.id == selectedId,
                expand = expand,
                onClick = { onSelect(choice.id) },
            )
        }
    }
}

@Composable
private fun SegmentColumn(
    choices: List<SettingChoice>,
    selectedId: String?,
    onSelect: (String) -> Unit,
    modifier: Modifier,
) {
    val shape = RoundedCornerShape(8.dp)
    Column(modifier.clip(shape).border(width = 1.dp, color = Line, shape = shape)) {
        choices.forEachIndexed { index, choice ->
            if (index > 0) {
                Box(Modifier.fillMaxWidth().height(1.dp).background(Line))
            }
            val selected = choice.id == selectedId
            Box(
                Modifier
                    .fillMaxWidth()
                    .heightIn(min = 48.dp)
                    .background(if (selected) Ink else Paper)
                    .selectable(
                        selected = selected,
                        role = Role.RadioButton,
                        onClick = { onSelect(choice.id) },
                    )
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = choice.label,
                    color = if (selected) OnInk else Ink,
                    fontSize = 15.sp,
                    fontWeight = if (selected) FontWeight.Medium else FontWeight.Normal,
                    lineHeight = 22.sp,
                    textAlign = TextAlign.Center,
                )
            }
        }
    }
}

@Composable
private fun RowScope.SwitchSegment(
    text: String,
    selected: Boolean,
    expand: Boolean,
    onClick: () -> Unit,
) {
    val width = if (expand) Modifier.weight(1f) else Modifier.widthIn(min = 48.dp)
    Box(
        modifier = width
            .fillMaxHeight()
            .background(if (selected) Ink else Paper)
            .selectable(selected = selected, role = Role.RadioButton, onClick = onClick)
            .padding(horizontal = if (expand) 8.dp else 16.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            color = if (selected) OnInk else Ink,
            fontSize = 15.sp,
            fontWeight = if (selected) FontWeight.Medium else FontWeight.Normal,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
        )
    }
}

/**
 * 四个固定档位，两列排开。选中的一档用实心黑底。
 *
 * 半格写不下一行时，改成每档占满一行。
 *
 * @param choices 档位，按顺序从左到右、从上到下。
 * @param selectedId 当前档。对不上时都不选中。
 * @param onSelect 点到某一档。
 */
@Composable
private fun OptionGrid(
    choices: List<SettingChoice>,
    selectedId: String?,
    onSelect: (String) -> Unit,
) {
    val measurer = rememberTextMeasurer()
    BoxWithConstraints(Modifier.fillMaxWidth().padding(top = 8.dp)) {
        val singleColumn = optionsNeedFullWidth(
            measurer = measurer,
            labels = choices.map { it.label },
            widthPx = constraints.maxWidth,
            gapPx = px(8.dp),
            padPx = px(16.dp),
            safetyPx = px(4.dp),
            textStyle = valueStyle,
        )
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            if (singleColumn) {
                choices.forEach { choice ->
                    OptionButton(
                        text = choice.label,
                        selected = choice.id == selectedId,
                        onClick = { onSelect(choice.id) },
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            } else {
                choices.chunked(2).forEach { row ->
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        row.forEach { choice ->
                            OptionButton(
                                text = choice.label,
                                selected = choice.id == selectedId,
                                onClick = { onSelect(choice.id) },
                                modifier = Modifier.weight(1f),
                            )
                        }
                        if (row.size == 1) {
                            Box(Modifier.weight(1f))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun OptionButton(
    text: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val shape = RoundedCornerShape(8.dp)
    Box(
        modifier
            .heightIn(min = 48.dp)
            .clip(shape)
            .background(if (selected) Ink else Paper)
            .border(width = 1.dp, color = if (selected) Ink else Line, shape = shape)
            .selectable(selected = selected, role = Role.RadioButton, onClick = onClick)
            .padding(horizontal = 8.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            color = if (selected) OnInk else Ink,
            fontSize = 15.sp,
            fontWeight = if (selected) FontWeight.Medium else FontWeight.Normal,
            lineHeight = 22.sp,
            textAlign = TextAlign.Center,
        )
    }
}

/**
 * 点开后看这一块配置在说什么。
 *
 * @param title 这块的名字。
 * @param body 一两句说明。
 * @param onDismiss 关掉弹窗。
 */
@Composable
private fun SectionBodyDialog(title: String, body: String, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(16.dp),
        containerColor = Paper,
        tonalElevation = 0.dp,
        titleContentColor = Ink,
        textContentColor = Muted,
        title = {
            Text(text = title, color = Ink, fontSize = 24.sp, fontWeight = FontWeight.Bold)
        },
        text = {
            Text(text = body, color = Muted, fontSize = 15.sp, lineHeight = 22.sp)
        },
        confirmButton = {
            TextButton(onClick = onDismiss, modifier = Modifier.heightIn(min = 48.dp)) {
                Text(
                    text = stringResource(R.string.action_got_it),
                    color = Ink,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Medium,
                )
            }
        },
    )
}

@Composable
private fun px(value: Dp): Int = with(LocalDensity.current) { value.roundToPx() }

private fun singleLineWidth(measurer: TextMeasurer, text: String, style: TextStyle): Int {
    return measurer.measure(
        text = text,
        style = style,
        maxLines = 1,
        softWrap = false,
    ).size.width
}

private fun segmentsFitOneLine(
    measurer: TextMeasurer,
    labels: List<String>,
    widthPx: Int,
    dividerPx: Int,
    padPx: Int,
    safetyPx: Int,
    textStyle: TextStyle,
): Boolean {
    if (labels.isEmpty()) return true
    val longest = labels.maxOf { singleLineWidth(measurer, it, textStyle) }
    val segment = (widthPx - dividerPx * labels.lastIndex) / labels.size
    return longest + padPx + safetyPx <= segment
}

private fun optionsNeedFullWidth(
    measurer: TextMeasurer,
    labels: List<String>,
    widthPx: Int,
    gapPx: Int,
    padPx: Int,
    safetyPx: Int,
    textStyle: TextStyle,
): Boolean {
    if (labels.isEmpty()) return false
    val longest = labels.maxOf { singleLineWidth(measurer, it, textStyle) }
    val cell = (widthPx - gapPx) / 2
    return longest + padPx + safetyPx > cell
}

@Preview(name = "常见宽度", showBackground = true, widthDp = 360, heightDp = 1000)
@Preview(name = "窄屏", showBackground = true, widthDp = 320, heightDp = 1100)
@Composable
private fun ConfigPreview() {
    SampleTheme {
        ConfigContent(
            sections = listOf(
                SettingSection(
                    id = "queue",
                    title = "队列",
                    body = "同时最多 3 条，多出来的会排队。重新打开应用后，没下完的会自己继续。",
                    rows = listOf(
                        SettingRow("队列名字", "default"),
                        SettingRow(
                            label = "同时下载",
                            value = "3 条",
                            control = SettingControl.Options(
                                key = SettingKey.Concurrent,
                                choices = listOf(
                                    SettingChoice("1", "1 条"),
                                    SettingChoice("2", "2 条"),
                                    SettingChoice("3", "3 条"),
                                    SettingChoice("4", "4 条"),
                                ),
                                selectedId = "3",
                            ),
                        ),
                        SettingRow("自动开始", "开"),
                    ),
                ),
                SettingSection(
                    id = "network",
                    title = "速度和网络",
                    body = "这两项对所有任务一起生效。单条任务还可以再加自己的限制。",
                    rows = listOf(
                        SettingRow(
                            label = "速度上限",
                            value = "不限",
                            control = SettingControl.Options(
                                key = SettingKey.Speed,
                                choices = listOf(
                                    SettingChoice("none", "不限"),
                                    SettingChoice("128kb", "128 KB/s"),
                                    SettingChoice("512kb", "512 KB/s"),
                                    SettingChoice("2mb", "2 MB/s"),
                                ),
                                selectedId = "none",
                            ),
                        ),
                        SettingRow(
                            label = "网络",
                            value = "不额外限制",
                            control = SettingControl.Segments(
                                key = SettingKey.Network,
                                choices = listOf(
                                    SettingChoice("all", "任何网络"),
                                    SettingChoice("unmetered", "不计流量"),
                                    SettingChoice("wifi", "只走 Wi-Fi"),
                                ),
                                selectedId = null,
                            ),
                        ),
                    ),
                ),
                SettingSection(
                    id = "files",
                    title = "文件",
                    body = "文件在应用内部目录，不需要存储权限。",
                    rows = listOf(
                        SettingRow("分块", "按大小分成 1 到 5 段"),
                        SettingRow("临时文件", "先写临时文件再改名"),
                        SettingRow("核对", "完成前可以核对 SHA-256 或 MD5。任务没带摘要时不会做。"),
                    ),
                    stack = SettingRow(
                        label = "目录",
                        value = "/data/user/0/com.arturoyi.fastdownload.sample/files/download",
                    ),
                ),
                SettingSection(
                    id = "background",
                    title = "后台",
                    body = "有下载时显示一条通知。",
                    rows = listOf(
                        SettingRow("保活", "前台服务，系统拒绝时改走 WorkManager"),
                        SettingRow("通知", "这个系统不用单独允许通知"),
                    ),
                ),
            ),
            onSelect = { _, _ -> },
            modifier = Modifier.fillMaxSize(),
        )
    }
}
