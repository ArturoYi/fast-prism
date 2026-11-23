/**
 * 设置页：每一项单独成行。主题和语言用开关，协议从底部弹出。
 *
 * 主题和语言立刻生效，并记在 [com.arturoyi.fastdownload.sample.settings.AppSettings] 里。
 * 三份协议共用同一段免责，点开后在 sheet 里读完。
 *
 * @file
 */
package com.arturoyi.fastdownload.sample.futures.settings

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.arturoyi.fastdownload.sample.R
import com.arturoyi.fastdownload.sample.SampleApp
import com.arturoyi.fastdownload.sample.settings.AppLanguage
import com.arturoyi.fastdownload.sample.settings.AppTheme
import com.arturoyi.fastdownload.sample.theme.Ink
import com.arturoyi.fastdownload.sample.theme.Line
import com.arturoyi.fastdownload.sample.theme.Muted
import com.arturoyi.fastdownload.sample.theme.OnInk
import com.arturoyi.fastdownload.sample.theme.PageHeader
import com.arturoyi.fastdownload.sample.theme.Paper
import com.arturoyi.fastdownload.sample.theme.PrimaryButton
import com.arturoyi.fastdownload.sample.theme.SampleTheme
import com.arturoyi.fastdownload.sample.theme.cardSurface

/** 可以展开全文的协议。三份共用同一段免责。 */
private enum class Policy {
    License,
    Terms,
    Privacy,
}

/**
 * 画出设置，并把「查看配置」交给外壳。
 *
 * @param onViewConfig 打开配置页。
 * @param modifier 页面尺寸。
 */
@Composable
internal fun SettingsPage(
    onViewConfig: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val settings = (context.applicationContext as? SampleApp)?.settings
    val version = remember(context.applicationContext) { context.applicationContext.versionName() }
    SettingsContent(
        theme = settings?.theme ?: AppTheme.Dark,
        language = settings?.language ?: AppLanguage.fromSystem(),
        version = version,
        onTheme = { settings?.chooseTheme(it) },
        onLanguage = { settings?.chooseLanguage(it) },
        onViewConfig = onViewConfig,
        modifier = modifier,
    )
}

@Composable
private fun SettingsContent(
    theme: AppTheme,
    language: AppLanguage,
    version: String,
    onTheme: (AppTheme) -> Unit,
    onLanguage: (AppLanguage) -> Unit,
    onViewConfig: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val dark = theme == AppTheme.Dark
    val english = language == AppLanguage.English
    var policy by remember { mutableStateOf<Policy?>(null) }
    Column(modifier) {
        PageHeader(
            title = stringResource(R.string.settings_title),
            subtitle = stringResource(R.string.settings_subtitle),
        )
        Column(
            Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(start = 16.dp, end = 16.dp, bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            SwitchRow(
                title = stringResource(R.string.settings_theme),
                value = stringResource(if (dark) R.string.theme_dark else R.string.theme_light),
                checked = dark,
                onCheckedChange = { onTheme(if (it) AppTheme.Dark else AppTheme.Light) },
            )
            SwitchRow(
                title = stringResource(R.string.settings_language),
                value = if (english) "English" else "中文",
                checked = english,
                onCheckedChange = { onLanguage(if (it) AppLanguage.English else AppLanguage.Chinese) },
            )
            LinkRow(
                title = stringResource(R.string.settings_view_config),
                body = stringResource(R.string.settings_view_config_body),
                onClick = onViewConfig,
            )
            LinkRow(
                title = stringResource(R.string.settings_license),
                body = stringResource(R.string.settings_license_body),
                onClick = { policy = Policy.License },
            )
            LinkRow(
                title = stringResource(R.string.settings_terms),
                body = stringResource(R.string.settings_terms_body),
                onClick = { policy = Policy.Terms },
            )
            LinkRow(
                title = stringResource(R.string.settings_privacy),
                body = stringResource(R.string.settings_privacy_body),
                onClick = { policy = Policy.Privacy },
            )
            VersionRow(
                title = stringResource(R.string.settings_version),
                version = version.ifBlank { "—" },
            )
        }
    }
    policy?.let { selected ->
        PolicySheet(policy = selected, onDismiss = { policy = null })
    }
}

/**
 * 一行开关。整行都能点。
 *
 * 语言旁边的字固定是「中文」或 English，切过去之后仍认得出。
 */
@Composable
private fun SwitchRow(
    title: String,
    value: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .cardSurface()
            .heightIn(min = 48.dp)
            .toggleable(value = checked, role = Role.Switch, onValueChange = onCheckedChange)
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = title,
            color = Ink,
            fontSize = 15.sp,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        Text(
            text = value,
            color = Muted,
            fontSize = 15.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(start = 8.dp),
        )
        Switch(
            checked = checked,
            onCheckedChange = null,
            colors = SwitchDefaults.colors(
                checkedThumbColor = OnInk,
                checkedTrackColor = Ink,
                checkedBorderColor = Ink,
                checkedIconColor = OnInk,
                uncheckedThumbColor = Ink,
                uncheckedTrackColor = Paper,
                uncheckedBorderColor = Line,
                uncheckedIconColor = Ink,
            ),
            modifier = Modifier.padding(start = 8.dp),
        )
    }
}

@Composable
private fun LinkRow(title: String, body: String, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .cardSurface()
            .heightIn(min = 48.dp)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(text = title, color = Ink, fontSize = 15.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(8.dp))
            Text(text = body, color = Muted, fontSize = 13.sp)
        }
        Icon(
            imageVector = Icons.AutoMirrored.Outlined.KeyboardArrowRight,
            contentDescription = null,
            tint = Muted,
            modifier = Modifier.padding(start = 8.dp),
        )
    }
}

@Composable
private fun VersionRow(title: String, version: String) {
    Row(
        Modifier
            .fillMaxWidth()
            .cardSurface()
            .heightIn(min = 48.dp)
            .padding(horizontal = 16.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = title,
            color = Ink,
            fontSize = 15.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.weight(1f),
        )
        Text(
            text = version,
            color = Ink,
            fontSize = 15.sp,
            fontWeight = FontWeight.Medium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(start = 8.dp),
        )
    }
}

/**
 * 从底部展开一份协议。正文后面附上共用的免责。
 *
 * 下拉、点外面，或点「知道了」都会收起。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PolicySheet(policy: Policy, onDismiss: () -> Unit) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val title = stringResource(policy.title)
    val body = stringResource(policy.body) + "\n\n" + stringResource(R.string.settings_disclaimer)
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = Paper,
        contentColor = Ink,
        scrimColor = Color.Black.copy(alpha = 0.45f),
        tonalElevation = 0.dp,
        shape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp),
        dragHandle = { BottomSheetDefaults.DragHandle(color = Muted) },
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .navigationBarsPadding()
                .padding(start = 16.dp, end = 16.dp, bottom = 16.dp),
        ) {
            Text(text = title, color = Ink, fontSize = 24.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(16.dp))
            Text(text = body, color = Ink, fontSize = 15.sp, lineHeight = 22.sp)
            Spacer(Modifier.height(16.dp))
            PrimaryButton(
                text = stringResource(R.string.action_got_it),
                onClick = onDismiss,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

private val Policy.title: Int
    get() = when (this) {
        Policy.License -> R.string.settings_license
        Policy.Terms -> R.string.settings_terms
        Policy.Privacy -> R.string.settings_privacy
    }

private val Policy.body: Int
    get() = when (this) {
        Policy.License -> R.string.settings_license_sheet
        Policy.Terms -> R.string.settings_terms_sheet
        Policy.Privacy -> R.string.settings_privacy_sheet
    }

private fun Context.versionName(): String {
    return try {
        val info = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            packageManager.getPackageInfo(packageName, PackageManager.PackageInfoFlags.of(0))
        } else {
            @Suppress("DEPRECATION")
            packageManager.getPackageInfo(packageName, 0)
        }
        info.versionName.orEmpty()
    } catch (_: PackageManager.NameNotFoundException) {
        ""
    }
}

@Preview(showBackground = true, widthDp = 360, heightDp = 800)
@Composable
private fun SettingsPreview() {
    SampleTheme {
        SettingsContent(
            theme = AppTheme.Dark,
            language = AppLanguage.Chinese,
            version = "1.0.1-rc",
            onTheme = {},
            onLanguage = {},
            onViewConfig = {},
            modifier = Modifier.fillMaxSize(),
        )
    }
}
