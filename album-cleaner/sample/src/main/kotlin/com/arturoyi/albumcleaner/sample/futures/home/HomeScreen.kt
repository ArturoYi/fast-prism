/**
 * 底部三个页签：清理、分组、设置。回收站从设置推上来。
 *
 * 返回栈由 [androidx.navigation3.ui.NavDisplay] 管。侧滑和系统返回都把回收站滑走。
 *
 * @file
 */
package com.arturoyi.albumcleaner.sample.futures.home

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
import androidx.compose.material.icons.outlined.GridView
import androidx.compose.material.icons.outlined.Photo
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.ui.NavDisplay
import com.arturoyi.albumcleaner.sample.R
import com.arturoyi.albumcleaner.sample.futures.clean.CleanPage
import com.arturoyi.albumcleaner.sample.futures.groups.GroupsPage
import com.arturoyi.albumcleaner.sample.futures.recycle.RecyclePage
import com.arturoyi.albumcleaner.sample.futures.settings.SettingsPage
import com.arturoyi.albumcleaner.sample.theme.Indicator
import com.arturoyi.albumcleaner.sample.theme.Ink
import com.arturoyi.albumcleaner.sample.theme.Line
import com.arturoyi.albumcleaner.sample.theme.Muted
import com.arturoyi.albumcleaner.sample.theme.Paper
import com.arturoyi.albumcleaner.sample.theme.sampleCanvas
import kotlinx.serialization.Serializable

/** 一页滑入或滑出的时间。 */
private const val PAGE_SLIDE_MILLIS: Int = 280

/** 可以放进返回栈的页面。 */
@Serializable
internal sealed interface AppRoute : NavKey

/** 三个页签所在的首页。 */
@Serializable
internal data object HomeRoute : AppRoute

/** 从设置推上来的回收站。 */
@Serializable
internal data object RecycleRoute : AppRoute

private enum class Page(@StringRes val label: Int) {
    Clean(R.string.tab_clean),
    Groups(R.string.tab_groups),
    Settings(R.string.tab_settings),
    ;

    fun icon(): ImageVector = when (this) {
        Clean -> Icons.Outlined.Photo
        Groups -> Icons.Outlined.GridView
        Settings -> Icons.Outlined.Settings
    }
}

/**
 * 首页和回收站叠成返回栈。
 *
 * @param permissionGranted 现在能不能读照片。
 * @param onStartScan 用户要开始扫描。外壳负责在没权限时先申请。
 * @param onRequestPermission 设置里补申请读取权限。这次不会接着扫描。
 */
@Composable
internal fun HomeScreen(
    permissionGranted: Boolean,
    onStartScan: () -> Unit,
    onRequestPermission: () -> Unit,
) {
    val backStack = rememberNavBackStack(HomeRoute)
    val openRecycle = {
        if (backStack.lastOrNull() !is RecycleRoute) backStack.add(RecycleRoute)
    }
    val closeRecycle = {
        if (backStack.size > 1) backStack.removeLastOrNull()
    }
    NavDisplay(
        backStack = backStack,
        modifier = Modifier.fillMaxSize(),
        onBack = closeRecycle,
        entryProvider = entryProvider {
            entry<HomeRoute> {
                HomePage(
                    permissionGranted = permissionGranted,
                    onStartScan = onStartScan,
                    onRequestPermission = onRequestPermission,
                    onOpenRecycle = openRecycle,
                )
            }
            entry<RecycleRoute> {
                RecycleDestination(onBack = closeRecycle)
            }
        },
        transitionSpec = { pageForward() },
        popTransitionSpec = { pageBack() },
        predictivePopTransitionSpec = { _ -> pageBack() },
    )
}

/**
 * 三个页签。底栏只属于这一页，回收站盖上来时它一起被盖住。
 */
@Composable
private fun HomePage(
    permissionGranted: Boolean,
    onStartScan: () -> Unit,
    onRequestPermission: () -> Unit,
    onOpenRecycle: () -> Unit,
) {
    var pageIndex by rememberSaveable { mutableIntStateOf(0) }
    val page = Page.entries[pageIndex.coerceIn(0, Page.entries.lastIndex)]
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
                Page.Clean -> CleanPage(
                    permissionGranted = permissionGranted,
                    onStartScan = onStartScan,
                    modifier = pageModifier,
                )
                Page.Groups -> GroupsPage(modifier = pageModifier)
                Page.Settings -> SettingsPage(
                    permissionGranted = permissionGranted,
                    onRequestPermission = onRequestPermission,
                    onOpenRecycle = onOpenRecycle,
                    modifier = pageModifier,
                )
            }
        }
    }
}

/** 盖住首页的回收站。自己处理系统栏空隙，返回交给返回栈。 */
@Composable
private fun RecycleDestination(onBack: () -> Unit) {
    Box(Modifier.fillMaxSize().sampleCanvas()) {
        Scaffold(
            modifier = Modifier.fillMaxSize(),
            containerColor = Color.Transparent,
        ) { padding ->
            RecyclePage(
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
