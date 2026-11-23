/**
 * 设置页：主题、语言、回收站和读取权限。
 *
 * @file
 */
package com.arturoyi.albumcleaner.sample.futures.settings

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material3.Icon
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.arturoyi.albumcleaner.sample.R
import com.arturoyi.albumcleaner.sample.SampleApp
import com.arturoyi.albumcleaner.sample.settings.AppLanguage
import com.arturoyi.albumcleaner.sample.settings.AppTheme
import com.arturoyi.albumcleaner.sample.theme.Ink
import com.arturoyi.albumcleaner.sample.theme.Line
import com.arturoyi.albumcleaner.sample.theme.Muted
import com.arturoyi.albumcleaner.sample.theme.OnInk
import com.arturoyi.albumcleaner.sample.theme.PageHeader
import com.arturoyi.albumcleaner.sample.theme.Paper
import com.arturoyi.albumcleaner.sample.theme.SampleTheme
import com.arturoyi.albumcleaner.sample.theme.cardSurface

/**
 * 画出设置。
 *
 * @param permissionGranted 现在能不能读照片。
 * @param onRequestPermission 还不能读时去申请。
 * @param onOpenRecycle 打开回收站。
 * @param modifier 页面尺寸。
 */
@Composable
internal fun SettingsPage(
    permissionGranted: Boolean,
    onRequestPermission: () -> Unit,
    onOpenRecycle: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val settings = (context.applicationContext as? SampleApp)?.settings
    val version = remember(context.applicationContext) { context.applicationContext.versionName() }
    SettingsContent(
        theme = settings?.theme ?: AppTheme.Dark,
        language = settings?.language ?: AppLanguage.fromSystem(),
        version = version,
        permissionGranted = permissionGranted,
        onTheme = { settings?.chooseTheme(it) },
        onLanguage = { settings?.chooseLanguage(it) },
        onRequestPermission = onRequestPermission,
        onOpenRecycle = onOpenRecycle,
        modifier = modifier,
    )
}

@Composable
private fun SettingsContent(
    theme: AppTheme,
    language: AppLanguage,
    version: String,
    permissionGranted: Boolean,
    onTheme: (AppTheme) -> Unit,
    onLanguage: (AppLanguage) -> Unit,
    onRequestPermission: () -> Unit,
    onOpenRecycle: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val dark = theme == AppTheme.Dark
    val english = language == AppLanguage.English
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
            verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(8.dp),
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
                title = stringResource(R.string.settings_recycle),
                body = stringResource(R.string.settings_recycle_body),
                onClick = onOpenRecycle,
            )
            if (permissionGranted) {
                ValueRow(
                    title = stringResource(R.string.settings_permission),
                    value = stringResource(R.string.permission_granted),
                )
            } else {
                LinkRow(
                    title = stringResource(R.string.settings_permission),
                    body = stringResource(R.string.permission_missing),
                    onClick = onRequestPermission,
                )
            }
            ValueRow(
                title = stringResource(R.string.settings_version),
                value = version.ifBlank { "—" },
            )
        }
    }
}

/**
 * 一行开关。整行都能点。
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
private fun ValueRow(title: String, value: String) {
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
            text = value,
            color = Ink,
            fontSize = 15.sp,
            fontWeight = FontWeight.Medium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(start = 8.dp),
        )
    }
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
            version = "0.1.0",
            permissionGranted = false,
            onTheme = {},
            onLanguage = {},
            onRequestPermission = {},
            onOpenRecycle = {},
            modifier = Modifier.fillMaxSize(),
        )
    }
}
