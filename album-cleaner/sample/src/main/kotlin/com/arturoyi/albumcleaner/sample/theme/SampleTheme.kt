/**
 * 演示的单色界面。深色和浅色都只用黑白和灰度，靠明暗和粗细区分层级。
 *
 * 默认是深色。颜色从 [LocalSamplePalette] 读，这样页面不用各自判断主题。
 * 色值和下载示例相同，不另加色相。
 *
 * @file
 */
package com.arturoyi.albumcleaner.sample.theme

import android.content.Context
import android.content.res.Configuration
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.LocalActivity
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.arturoyi.albumcleaner.sample.SampleApp
import com.arturoyi.albumcleaner.sample.settings.AppTheme
import java.util.Locale

/**
 * 一套界面用到的颜色。深色和浅色各有一份，色相只有黑白。
 *
 * @property dark 这是深色主题。
 * @property ink 标题、正文和实心控件。
 * @property onInk 画在 [ink] 上面的字。
 * @property muted 说明文字。
 * @property line 边框、分割线和进度槽。
 * @property paper 卡片、输入框、弹窗和底栏。
 * @property canvasTop 页面背景的上沿。
 * @property canvasBottom 页面背景的下沿。
 * @property indicator 底栏里选中项的底，也用来托住还没出来的缩略图。
 */
internal data class SamplePalette(
    val dark: Boolean,
    val ink: Color,
    val onInk: Color,
    val muted: Color,
    val line: Color,
    val paper: Color,
    val canvasTop: Color,
    val canvasBottom: Color,
    val indicator: Color,
)

private val DarkPalette = SamplePalette(
    dark = true,
    ink = Color(0xFFF4F4F4),
    onInk = Color(0xFF111111),
    muted = Color(0xFFA3A3A3),
    line = Color(0xFF3F3F3F),
    paper = Color(0xFF1C1C1C),
    canvasTop = Color(0xFF111111),
    canvasBottom = Color(0xFF0A0A0A),
    indicator = Color(0xFF2A2A2A),
)

private val LightPalette = SamplePalette(
    dark = false,
    ink = Color(0xFF111111),
    onInk = Color.White,
    muted = Color(0xFF5C5C5C),
    line = Color(0xFFE6E6E6),
    paper = Color.White,
    canvasTop = Color.White,
    canvasBottom = Color(0xFFF4F4F4),
    indicator = Color(0xFFF4F4F4),
)

private val LocalSamplePalette = staticCompositionLocalOf { DarkPalette }

/** 标题和实心控件。 */
internal val Ink: Color
    @Composable get() = LocalSamplePalette.current.ink

/** 实心控件上的字。 */
internal val OnInk: Color
    @Composable get() = LocalSamplePalette.current.onInk

/** 说明文字。 */
internal val Muted: Color
    @Composable get() = LocalSamplePalette.current.muted

/** 边框和进度槽。 */
internal val Line: Color
    @Composable get() = LocalSamplePalette.current.line

/** 卡片和输入框。 */
internal val Paper: Color
    @Composable get() = LocalSamplePalette.current.paper

/** 底栏选中项的底，以及缩略图占位。 */
internal val Indicator: Color
    @Composable get() = LocalSamplePalette.current.indicator

private val SampleTypography = Typography(
    headlineMedium = TextStyle(fontSize = 24.sp, fontWeight = FontWeight.Bold),
    titleMedium = TextStyle(fontSize = 15.sp, fontWeight = FontWeight.Bold),
    bodyLarge = TextStyle(fontSize = 15.sp),
    bodyMedium = TextStyle(fontSize = 15.sp),
    bodySmall = TextStyle(fontSize = 13.sp),
    labelLarge = TextStyle(fontSize = 15.sp, fontWeight = FontWeight.Medium),
)

/**
 * 包住整页。背景从上到下略微变深，卡片用 [Paper]。
 *
 * 语言在这里套进 [LocalContext]，下面的文案跟着变。预览里没有 [SampleApp] 时保持深色。
 *
 * @param content 当前页面。
 */
@Composable
internal fun SampleTheme(content: @Composable () -> Unit) {
    val app = LocalContext.current.applicationContext as? SampleApp
    val theme = app?.settings?.theme ?: AppTheme.Dark
    val language = app?.settings?.language
    val palette = if (theme == AppTheme.Dark) DarkPalette else LightPalette
    val base = LocalContext.current
    val locale = language?.toLocale()
    val localized = remember(base, locale) {
        if (locale == null) base else base.withLocale(locale)
    }
    val view = LocalView.current
    val activity = LocalActivity.current as? ComponentActivity
    if (!view.isInEditMode && activity != null) {
        SideEffect { activity.syncSampleBars(palette.dark) }
    }
    CompositionLocalProvider(
        LocalSamplePalette provides palette,
        LocalContext provides localized,
    ) {
        MaterialTheme(colorScheme = palette.asColorScheme(), typography = SampleTypography) {
            Box(
                Modifier
                    .fillMaxSize()
                    .background(Brush.verticalGradient(listOf(palette.canvasTop, palette.canvasBottom))),
            ) {
                content()
            }
        }
    }
}

/**
 * 让状态栏和导航栏的图标跟着深浅走，窗口底色也换成同一套黑或白。
 *
 * 在 [android.app.Activity.onCreate] 里先调一次，避免第一帧用错图标颜色。
 *
 * @param dark 当前是深色主题。
 */
internal fun ComponentActivity.syncSampleBars(dark: Boolean) {
    val transparent = android.graphics.Color.TRANSPARENT
    val style = if (dark) {
        SystemBarStyle.dark(transparent)
    } else {
        SystemBarStyle.light(transparent, transparent)
    }
    enableEdgeToEdge(statusBarStyle = style, navigationBarStyle = style)
    window.decorView.setBackgroundColor(if (dark) 0xFF111111.toInt() else 0xFFFFFFFF.toInt())
}

/**
 * 铺上和整页一样的渐变。
 *
 * 页面切换时每一页自己要有底，滑走的那页才不会透出下面的字。
 */
@Composable
internal fun Modifier.sampleCanvas(): Modifier {
    val palette = LocalSamplePalette.current
    return background(Brush.verticalGradient(listOf(palette.canvasTop, palette.canvasBottom)))
}

/** 圆角卡片，没有阴影。 */
@Composable
internal fun Modifier.cardSurface(): Modifier {
    val shape = RoundedCornerShape(16.dp)
    return this
        .clip(shape)
        .background(Paper)
        .border(width = 1.dp, color = Line, shape = shape)
}

/**
 * 每一页顶部的标题和一句说明。
 *
 * @param title 页名。
 * @param subtitle 这页是做什么的。
 * @param modifier 外边距。
 * @param topPadding 标题离顶部的距离。返回键已经占掉顶部时传 0。
 */
@Composable
internal fun PageHeader(
    title: String,
    subtitle: String,
    modifier: Modifier = Modifier,
    topPadding: Dp = 16.dp,
) {
    Column(
        modifier
            .fillMaxWidth()
            .padding(start = 16.dp, end = 16.dp, top = topPadding, bottom = 16.dp),
    ) {
        Text(text = title, color = Ink, fontSize = 24.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(8.dp))
        Text(text = subtitle, color = Muted, fontSize = 13.sp)
    }
}

/**
 * 实心主按钮。高度至少 48dp。
 *
 * @param text 按钮文字。
 * @param onClick 按下时调用。停用时不会调用。
 * @param modifier 额外的尺寸或边距。
 * @param enabled 不能点时为 false。
 * @param horizontalPadding 字两边的空白。一排多个按钮时可以收窄。
 */
@Composable
internal fun PrimaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    horizontalPadding: Dp = 16.dp,
) {
    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.heightIn(min = 48.dp).widthIn(min = 48.dp),
        shape = RoundedCornerShape(8.dp),
        elevation = ButtonDefaults.buttonElevation(
            defaultElevation = 0.dp,
            pressedElevation = 0.dp,
            focusedElevation = 0.dp,
            hoveredElevation = 0.dp,
            disabledElevation = 0.dp,
        ),
        colors = ButtonDefaults.buttonColors(
            containerColor = Ink,
            contentColor = OnInk,
            disabledContainerColor = Line,
            disabledContentColor = Muted,
        ),
        contentPadding = PaddingValues(horizontal = horizontalPadding, vertical = 0.dp),
    ) {
        Text(
            text = text,
            color = if (enabled) OnInk else Muted,
            fontSize = 15.sp,
            fontWeight = FontWeight.Medium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/**
 * 细边的次按钮。高度至少 48dp。
 *
 * @param text 按钮文字。
 * @param onClick 按下时调用。停用时不会调用。
 * @param modifier 额外的尺寸或边距。
 * @param enabled 不能点时为 false。
 * @param horizontalPadding 字两边的空白。一排多个按钮时可以收窄。
 */
@Composable
internal fun LineButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    horizontalPadding: Dp = 16.dp,
) {
    OutlinedButton(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.heightIn(min = 48.dp).widthIn(min = 48.dp),
        shape = RoundedCornerShape(8.dp),
        border = BorderStroke(1.dp, Line),
        colors = ButtonDefaults.outlinedButtonColors(
            containerColor = Paper,
            contentColor = Ink,
            disabledContainerColor = Paper,
            disabledContentColor = Muted,
        ),
        contentPadding = PaddingValues(horizontal = horizontalPadding, vertical = 0.dp),
    ) {
        Text(
            text = text,
            color = if (enabled) Ink else Muted,
            fontSize = 15.sp,
            fontWeight = FontWeight.Medium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/**
 * 做删除或离开之前再问一次。
 *
 * @param title 问句。
 * @param body 结果说明。
 * @param confirm 确认按钮。
 * @param dismiss 留下或取消。
 * @param onConfirm 用户确认。
 * @param onDismiss 用户取消。
 */
@Composable
internal fun ConfirmDialog(
    title: String,
    body: String,
    confirm: String,
    dismiss: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
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
            Text(text = body, color = Muted, fontSize = 13.sp)
        },
        confirmButton = {
            TextButton(onClick = onConfirm, modifier = Modifier.heightIn(min = 48.dp)) {
                Text(text = confirm, color = Ink, fontSize = 15.sp, fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, modifier = Modifier.heightIn(min = 48.dp)) {
                Text(text = dismiss, color = Muted, fontSize = 15.sp)
            }
        },
    )
}

/**
 * 扫描进度。色块宽度就是已经完成的比例。
 *
 * @param fraction 0 到 1。总数还不知道时传 0。
 * @param description 读屏时怎么称呼这条进度。
 * @param modifier 宽度和上边距。
 */
@Composable
internal fun ScanMeter(fraction: Float, description: String, modifier: Modifier = Modifier) {
    val value = fraction.coerceIn(0f, 1f)
    val track = Line
    val fill = Ink
    Canvas(
        modifier
            .fillMaxWidth()
            .height(8.dp)
            .semantics {
                contentDescription = description
                progressBarRangeInfo = ProgressBarRangeInfo(value, 0f..1f)
            },
    ) {
        val radius = CornerRadius(size.height / 2f, size.height / 2f)
        drawRoundRect(color = track, cornerRadius = radius)
        if (value > 0f) {
            drawRoundRect(
                color = fill,
                size = Size(size.width * value, size.height),
                cornerRadius = radius,
            )
        }
    }
}

private fun SamplePalette.asColorScheme() = if (dark) {
    darkColorScheme(
        primary = ink,
        onPrimary = onInk,
        primaryContainer = indicator,
        onPrimaryContainer = ink,
        secondary = ink,
        onSecondary = onInk,
        secondaryContainer = indicator,
        onSecondaryContainer = ink,
        background = canvasTop,
        onBackground = ink,
        surface = paper,
        onSurface = ink,
        surfaceVariant = indicator,
        onSurfaceVariant = muted,
        outline = line,
        outlineVariant = line,
        error = ink,
        onError = onInk,
        errorContainer = indicator,
        onErrorContainer = ink,
        surfaceTint = Color.Transparent,
    )
} else {
    lightColorScheme(
        primary = ink,
        onPrimary = onInk,
        primaryContainer = indicator,
        onPrimaryContainer = ink,
        secondary = ink,
        onSecondary = onInk,
        secondaryContainer = indicator,
        onSecondaryContainer = ink,
        background = canvasTop,
        onBackground = ink,
        surface = paper,
        onSurface = ink,
        surfaceVariant = indicator,
        onSurfaceVariant = muted,
        outline = line,
        outlineVariant = line,
        error = ink,
        onError = onInk,
        errorContainer = indicator,
        onErrorContainer = ink,
        surfaceTint = Color.Transparent,
    )
}

private fun Context.withLocale(locale: Locale): Context {
    val config = Configuration(resources.configuration)
    config.setLocale(locale)
    config.setLayoutDirection(locale)
    return createConfigurationContext(config)
}
