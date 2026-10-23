package com.pocket.xiaoli.ui.navigation

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.GridView
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Person
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performClick
import com.github.takahirom.roborazzi.RobolectricDeviceQualifiers
import com.pocket.xiaoli.ui.theme.AppearanceMode
import com.pocket.xiaoli.ui.theme.LocalPocketXiaoliColors
import com.pocket.xiaoli.ui.theme.MascotLook
import com.pocket.xiaoli.ui.theme.PocketXiaoliTheme
import com.pocket.xiaoli.ui.theme.SkinStyle
import androidx.compose.ui.unit.dp
import java.util.concurrent.atomic.AtomicInteger
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * 底栏「鲤鱼」那格**点得动**。
 *
 * ## 为什么单开一条
 *
 * 这一格是全栏唯一没有文字、也没有 8dp 上下内边距的格子：`Column` 只包住一枚 40dp 的
 * 形象，撑不满 72dp 的栏，于是格子上下各有一截空白是**没有点击响应**的 —— 用户真机上
 * 点偏一点就"点了没反应"（2026-10 报过）。修法是给这一格 `fillMaxHeight()`。
 *
 * 这条用真 `GlassBottomBar` 点一次来钉住它：**能点到、且回调只触发一次**。
 * 不看图，只看行为，所以不进任何截图基线。
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = RobolectricDeviceQualifiers.Pixel5)
class AssistantTabClickTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val items = listOf(
        BottomNavItem("home", "首页", Icons.Outlined.Home),
        BottomNavItem("timetable", "课表", Icons.Outlined.CalendarMonth),
        BottomNavItem("assistant", "鲤鱼", Icons.Outlined.AutoAwesome),
        BottomNavItem("services", "服务", Icons.Outlined.GridView),
        BottomNavItem("profile", "我的", Icons.Outlined.Person),
    )

    @Test
    fun `点玻璃底栏的鲤鱼格会回调一次`() {
        val clicks = AtomicInteger(0)
        composeRule.setContent {
            PocketXiaoliTheme(
                appearanceMode = AppearanceMode.RAINBOW,
                skinStyle = SkinStyle.VIVID,
                darkTheme = false,
            ) {
                Box(
                    Modifier
                        .fillMaxSize()
                        .background(LocalPocketXiaoliColors.current.surfaceBase),
                ) {
                    GlassBottomBar(
                        items = items,
                        currentRoute = "home",
                        onSelect = { clicks.incrementAndGet() },
                        backdrop = null,
                    )
                }
            }
        }
        // 合并语义之后，"鲤鱼"这枚形象的 contentDescription 挂在真正可点的那个 Column 上
        composeRule.onNodeWithContentDescription("鲤鱼").performClick()
        composeRule.waitForIdle()
        assertEquals(1, clicks.get())
    }

    /**
     * 点击区域必须**盖住整格**，而不是只盖住那枚 40dp 的形象。
     *
     * 这条是"点了没反应"的直接判据：修之前 `Column` 只包形象、高度 40dp，格子上下各空出
     * 十几 dp 的死区；`fillMaxHeight()` 之后可点区域高度应当接近整格（>48dp）。
     */
    @Test
    fun `鲤鱼格的可点区域高过形象本身`() {
        composeRule.setContent {
            PocketXiaoliTheme(
                appearanceMode = AppearanceMode.RAINBOW,
                skinStyle = SkinStyle.VIVID,
                darkTheme = false,
            ) {
                Box(
                    Modifier
                        .fillMaxSize()
                        .background(LocalPocketXiaoliColors.current.surfaceBase),
                ) {
                    GlassBottomBar(
                        items = items,
                        currentRoute = "home",
                        onSelect = {},
                        backdrop = null,
                    )
                }
            }
        }
        val bounds = composeRule.onNodeWithContentDescription("鲤鱼")
            .fetchSemanticsNode()
            .boundsInRoot
        val heightDp = with(composeRule.density) { bounds.height.toDp() }
        assertTrue("可点区域只有 $heightDp，没撑满整格", heightDp >= 48.dp)
    }

    @Test
    fun `换成幽灵之后那格照样可点`() {
        val clicks = AtomicInteger(0)
        composeRule.setContent {
            PocketXiaoliTheme(
                appearanceMode = AppearanceMode.RAINBOW,
                skinStyle = SkinStyle.VIVID,
                darkTheme = false,
            ) {
                androidx.compose.runtime.CompositionLocalProvider(
                    com.pocket.xiaoli.ui.theme.LocalMascotLook provides MascotLook("幽灵"),
                ) {
                    Box(
                        Modifier
                            .fillMaxSize()
                            .background(LocalPocketXiaoliColors.current.surfaceBase),
                    ) {
                        GlassBottomBar(
                            items = items,
                            currentRoute = "profile",
                            onSelect = { clicks.incrementAndGet() },
                            backdrop = null,
                        )
                    }
                }
            }
        }
        composeRule.onNodeWithContentDescription("鲤鱼").performClick()
        composeRule.waitForIdle()
        assertEquals(1, clicks.get())
    }
}
