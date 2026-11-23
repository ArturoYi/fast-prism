/**
 * 底部三个页签：下载、文件、设置。配置是设置上面单独的一页。
 *
 * 返回栈由 [androidx.navigation3.ui.NavDisplay] 管。侧滑和系统返回都把配置页滑走，回到设置。
 * 三个页签仍留在首页里。每个页自己拿 ViewModel。有下载时请求通知权限。
 *
 * @file
 */
package com.arturoyi.fastdownload.sample.futures.home

import androidx.annotation.StringRes
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.ui.NavDisplay
import com.arturoyi.fastdownload.sample.NotificationPrompt
import com.arturoyi.fastdownload.sample.R
import com.arturoyi.fastdownload.sample.futures.config.ConfigPage
import com.arturoyi.fastdownload.sample.futures.files.FilesPage
import com.arturoyi.fastdownload.sample.futures.settings.SettingsPage
import com.arturoyi.fastdownload.sample.futures.tasks.TasksPage
import com.arturoyi.fastdownload.sample.theme.Indicator
import com.arturoyi.fastdownload.sample.theme.Ink
import com.arturoyi.fastdownload.sample.theme.Line
import com.arturoyi.fastdownload.sample.theme.Muted
import com.arturoyi.fastdownload.sample.theme.Paper
import com.arturoyi.fastdownload.sample.theme.sampleCanvas
import kotlinx.serialization.Serializable

/** 一页滑入或滑出的时间。 */
private const val PAGE_SLIDE_MILLIS: Int = 280

/** 可以放进返回栈的页面。 */
@Serializable
internal sealed interface AppRoute : NavKey

/** 三个页签所在的首页。 */
@Serializable
internal data object HomeRoute : AppRoute

/** 从设置推上来的配置页。 */
@Serializable
internal data object ConfigRoute : AppRoute

private enum class Page(@StringRes val label: Int) {
    Tasks(R.string.tab_downloads),
    Files(R.string.tab_files),
    Settings(R.string.tab_settings),
    ;

    fun icon(): ImageVector = when (this) {
        Tasks -> Icons.Outlined.Download
        Files -> Icons.Outlined.Folder
        Settings -> Icons.Outlined.Settings
    }
}

/**
 * 首页和配置页叠成返回栈。侧滑时配置页跟着手指滑走。
 *
 * @param notifications 权限询问和说明。
 * @param permissionRevision 权限变化时加一，用来刷新配置页上的那一行。
 */
@Composable
internal fun DemoScreen(notifications: NotificationPrompt, permissionRevision: Int) {
    val backStack = rememberNavBackStack(HomeRoute)
    val openConfig = {
        if (backStack.lastOrNull() !is ConfigRoute) backStack.add(ConfigRoute)
    }
    val closeConfig = {
        if (backStack.size > 1) backStack.removeLastOrNull()
    }
    NavDisplay(
        backStack = backStack,
        modifier = Modifier.fillMaxSize(),
        onBack = closeConfig,
        entryProvider = entryProvider {
            entry<HomeRoute> {
                HomePage(
                    notifications = notifications,
                    onViewConfig = openConfig,
                )
            }
            entry<ConfigRoute> {
                ConfigDestination(
                    notifications = notifications,
                    permissionRevision = permissionRevision,
                    onBack = closeConfig,
                )
            }
        },
        transitionSpec = { pageForward() },
        popTransitionSpec = { pageBack() },
        predictivePopTransitionSpec = { _ -> pageBack() },
    )
}

/**
 * 三个页签。底栏只属于这一页，配置页盖上来时它一起被盖住。
 *
 * @param notifications 权限询问和说明。
 * @param onViewConfig 把配置页推到返回栈上。
 */
@Composable
private fun HomePage(
    notifications: NotificationPrompt,
    onViewConfig: () -> Unit,
) {
    val viewModel: DemoViewModel = viewModel()
    var pageIndex by rememberSaveable { mutableIntStateOf(0) }
    val page = Page.entries[pageIndex.coerceIn(0, Page.entries.lastIndex)]
    LaunchedEffect(viewModel.downloadAttempts) {
        if (viewModel.downloadAttempts > 0) notifications.askIfNeeded()
    }
    Box(Modifier.fillMaxSize().sampleCanvas()) {
        Scaffold(
            modifier = Modifier.fillMaxSize(),
            containerColor = Color.Transparent,
            bottomBar = {
                Column {
                    HorizontalDivider(thickness = 1.dp, color = Line)
                    NavigationBar(containerColor = Paper, tonalElevation = 0.dp) {
                        val colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = Ink,
                            selectedTextColor = Ink,
                            unselectedIconColor = Muted,
                            unselectedTextColor = Muted,
                            indicatorColor = Indicator,
                        )
                        Page.entries.forEachIndexed { index, item ->
                            NavigationBarItem(
                                selected = pageIndex == index,
                                onClick = { pageIndex = index },
                                icon = { Icon(item.icon(), contentDescription = null) },
                                label = { Text(stringResource(item.label), fontSize = 13.sp) },
                                colors = colors,
                            )
                        }
                    }
                }
            },
        ) { padding ->
            val pageModifier = Modifier.padding(padding).fillMaxSize()
            when (page) {
                Page.Tasks -> TasksPage(modifier = pageModifier)
                Page.Files -> FilesPage(modifier = pageModifier)
                Page.Settings -> SettingsPage(
                    onViewConfig = onViewConfig,
                    modifier = pageModifier,
                )
            }
        }
    }
}

/**
 * 盖住首页的配置页。自己处理状态栏和导航栏的空隙，返回交给返回栈。
 *
 * @param notifications 用来写通知权限那一行。
 * @param permissionRevision 权限变化时加一。
 * @param onBack 关掉这一页。按钮和侧滑都走这里。
 */
@Composable
private fun ConfigDestination(
    notifications: NotificationPrompt,
    permissionRevision: Int,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val notificationLabel = remember(permissionRevision, context) { notifications.label(context) }
    Box(Modifier.fillMaxSize().sampleCanvas()) {
        Scaffold(
            modifier = Modifier.fillMaxSize(),
            containerColor = Color.Transparent,
        ) { padding ->
            ConfigPage(
                notificationLabel = notificationLabel,
                onBack = onBack,
                modifier = Modifier.padding(padding).fillMaxSize(),
            )
        }
    }
}

/** 新页从右侧进来，底下的页稍微让开。 */
private fun pageForward() = slideInHorizontally(
    animationSpec = tween(PAGE_SLIDE_MILLIS, easing = FastOutSlowInEasing),
    initialOffsetX = { it },
) togetherWith slideOutHorizontally(
    animationSpec = tween(PAGE_SLIDE_MILLIS, easing = FastOutSlowInEasing),
    targetOffsetX = { -it / 4 },
)

/** 当前页向右滑走。侧滑返回时进度跟着手指。 */
private fun pageBack() = slideInHorizontally(
    animationSpec = tween(PAGE_SLIDE_MILLIS, easing = FastOutSlowInEasing),
    initialOffsetX = { -it / 4 },
) togetherWith slideOutHorizontally(
    animationSpec = tween(PAGE_SLIDE_MILLIS, easing = FastOutSlowInEasing),
    targetOffsetX = { it },
)
