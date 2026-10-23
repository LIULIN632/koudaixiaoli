package com.pocket.xiaoli.ui.navigation

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.DateRange
import androidx.compose.material.icons.outlined.GridView
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.github.takahirom.roborazzi.RobolectricDeviceQualifiers
import com.github.takahirom.roborazzi.captureRoboImage
import com.pocket.xiaoli.ui.theme.AppearanceMode
import com.pocket.xiaoli.ui.theme.LocalMascotLook
import com.pocket.xiaoli.ui.theme.LocalPocketXiaoliColors
import com.pocket.xiaoli.ui.theme.LocalSkin
import com.pocket.xiaoli.ui.theme.MascotLook
import com.pocket.xiaoli.ui.theme.MascotPalette
import com.pocket.xiaoli.ui.theme.PocketXiaoliTheme
import com.pocket.xiaoli.ui.theme.SkinStyle
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * 底栏「鲤鱼」那一格的视觉基线。
 *
 * ## 为什么要有这一张
 *
 * 这一格是**唯一不走灰色线性图标**的 tab（做法照岱宗盒子的 `PidaiNavButton` 抄机制：
 * 满色形象 + 选中柔光 + 会动）。而它此前没有任何快照 —— 底栏不进任何一张既有基线，
 * 所以"形象有没有被裁掉、柔光是不是把格子糊成一团、选中/未选中分不分得出来"
 * 这类问题在 1639 条单测里**一条都拦不住**。这个文件就是那个缺口。
 *
 * ## 三格并排的意义
 *
 * 左边未选中、中间选中、右边是邻居的普通线性图标（20dp）——
 * 只有并排才能看出"它比别的图标大一圈、而且没有灰掉"这件事有没有成立。
 *
 * ## 全部 `blink = false`
 *
 * 眨眼是一条无限循环动画：快门永远等不到 idle，截到的是随机相位。
 * 见 `AssistantTabIcon` 的 `blink` 参数注释 —— 别为了"更像"把它打开。
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = RobolectricDeviceQualifiers.Pixel5)
class AssistantTabIconScreenshotTest {

    @get:Rule
    val composeRule = createComposeRule()

    /** 浅色：未选中 / 选中 / 邻居图标 并排，用默认的鲤鱼 · 蓝。 */
    @Test
    fun `底栏鲤鱼格选中与未选中`() {
        composeRule.setContent {
            Screen(dark = false) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Cell("未选中") {
                        AssistantTabIcon(selected = false, blink = false)
                    }
                    Cell("选中") {
                        AssistantTabIcon(selected = true, blink = false)
                    }
                    Cell("邻居 tab") {
                        Icon(
                            imageVector = Icons.Outlined.DateRange,
                            contentDescription = null,
                            modifier = Modifier.size(20.dp),
                            tint = LocalPocketXiaoliColors.current.textTertiary,
                        )
                    }
                }
            }
        }
        composeRule.onRoot().captureRoboImage("src/test/screenshots/110-tab-assistant-icon.png")
    }

    /**
     * 深色底栏 + 自带配色的形象。
     *
     * 深色主题下「睡猫」这种近黑的身体几乎融进栏底（`MascotAvatar` 会补一圈细白边），
     * 而底栏比设置页更小、更容易糊 —— 这张图就是去看那圈白边在 27dp 上还剩不剩。
     */
    @Test
    fun `底栏鲤鱼格在深色主题下`() {
        val picks = listOf("星星", "幽灵", "睡猫")
        composeRule.setContent {
            Screen(dark = true) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    picks.forEach { name ->
                        Cell(name) {
                            CompositionLocalProvider(
                                LocalMascotLook provides MascotLook(name, MascotPalette.DEFAULT),
                            ) {
                                AssistantTabIcon(selected = true, blink = false)
                            }
                        }
                    }
                }
            }
        }
        composeRule.onRoot().captureRoboImage("src/test/screenshots/111-tab-assistant-icon-dark.png")
    }


    /**
     * **真底栏**（不是三格示意）：玻璃栏 + 五格，选中鲤鱼。
     *
     * 110/111 只审"这一格长什么样"，审不出"34dp 的头像圈塞进 72dp 的栏里会不会被裁、
     * 会不会把整行顶高、和邻居的间距还剩多少"。这张渲的是 `GlassBottomBar` 本体
     * （`backdrop = null` → 走"选中格上底色"的降级分支，不依赖模糊能力）。
     *
     * ⚠️ 五格数据在这里是**手抄**的：真源 `bottomNavItems` 是 `AppNavigation.kt` 的
     * private，测试拿不到。抄的成本由这张图承担 —— 路由/文字/图标若在真源里改了，
     * 这张基线与真机不符，改动者会在这张图上看到差异，然后回来同步这里。
     */
    @Test
    fun `真玻璃底栏里的鲤鱼格`() {
        val items = listOf(
            BottomNavItem("home", "首页", Icons.Outlined.Home),
            BottomNavItem("timetable", "课表", Icons.Outlined.CalendarMonth),
            BottomNavItem("assistant", "鲤鱼", Icons.Outlined.AutoAwesome),
            BottomNavItem("services", "服务", Icons.Outlined.GridView),
            BottomNavItem("profile", "我的", Icons.Outlined.Person),
        )
        composeRule.setContent {
            Screen(dark = false) {
                GlassBottomBar(
                    items = items,
                    currentRoute = "assistant",
                    onSelect = {},
                    backdrop = null,
                )
            }
        }
        composeRule.onRoot().captureRoboImage("src/test/screenshots/112-glass-bar-assistant.png")
    }

    /**
     * 底栏那格必须**跟着 AI 设置里选的形象走**。
     *
     * 这条不是"再多一张好看图"：`LocalMascotLook` 的 provider 曾经只包住 `NavHost`，
     * 底栏浮层在它外面（叠在内容之上、不在 Scaffold 的 `bottomBar` 槽里），
     * 于是用户把形象换成幽灵之后，对话头像变了、底栏却一直还是默认那条蓝鲤鱼。
     * 这张图把"底栏渲染在 provider 之内"钉死 —— 谁能把它改回去，这张就红。
     */
    @Test
    fun `真玻璃底栏跟着 AI 设置的形象走`() {
        val items = listOf(
            BottomNavItem("home", "首页", Icons.Outlined.Home),
            BottomNavItem("timetable", "课表", Icons.Outlined.CalendarMonth),
            BottomNavItem("assistant", "鲤鱼", Icons.Outlined.AutoAwesome),
            BottomNavItem("services", "服务", Icons.Outlined.GridView),
            BottomNavItem("profile", "我的", Icons.Outlined.Person),
        )
        composeRule.setContent {
            Screen(dark = false) {
                CompositionLocalProvider(
                    // 幽灵：自带原版配色（#EC2F6B），一眼能看出底栏有没有吃到这个值
                    LocalMascotLook provides MascotLook("幽灵", MascotPalette.DEFAULT),
                ) {
                    GlassBottomBar(
                        items = items,
                        currentRoute = "assistant",
                        onSelect = {},
                        backdrop = null,
                    )
                }
            }
        }
        composeRule.onRoot().captureRoboImage("src/test/screenshots/113-glass-bar-native-mascot.png")
    }

    /**
     * 选中一个**普通 tab**（图标 + 文字）时的胶囊形状。
     *
     * 2026-10-22 用户报「选中后应该是椭圆」：胶囊此前是 `fillMaxHeight().fillMaxWidth(1/5)`
     * ⇒ 64dp 见方 + 圆角 50% = **正圆**。这张图盯住「横长竖短、且把图标与文字都裹住」——
     * 112 那张选中的是鲤鱼（只有形象没有文字），裹不住内容时的样子它看不出来。
     */
    @Test
    fun `真玻璃底栏里普通 tab 的选中胶囊`() {
        val items = listOf(
            BottomNavItem("home", "首页", Icons.Outlined.Home),
            BottomNavItem("timetable", "课表", Icons.Outlined.CalendarMonth),
            BottomNavItem("assistant", "鲤鱼", Icons.Outlined.AutoAwesome),
            BottomNavItem("services", "服务", Icons.Outlined.GridView),
            BottomNavItem("profile", "我的", Icons.Outlined.Person),
        )
        composeRule.setContent {
            Screen(dark = false) {
                GlassBottomBar(
                    items = items,
                    currentRoute = "home",
                    onSelect = {},
                    backdrop = null,
                )
            }
        }
        composeRule.onRoot().captureRoboImage("src/test/screenshots/114-glass-bar-normal-tab.png")
    }

    @Composable
    private fun Cell(label: String, content: @Composable () -> Unit) {
        val colors = LocalPocketXiaoliColors.current
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Box(
                modifier = Modifier.height(44.dp),
                contentAlignment = Alignment.Center,
            ) { content() }
            Spacer(Modifier.height(4.dp))
            Text(text = label, fontSize = 11.sp, color = colors.textTertiary)
        }
    }

    /** 与其它快照同一套主题外壳（马卡龙 · 彩虹 · 鲜明）。 */
    @Composable
    private fun Screen(dark: Boolean, content: @Composable () -> Unit) {
        PocketXiaoliTheme(
            appearanceMode = AppearanceMode.RAINBOW,
            skinStyle = SkinStyle.VIVID,
            darkTheme = dark,
        ) {
            val colors = LocalPocketXiaoliColors.current
            val skin = LocalSkin.current
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .background(colors.surfaceBase)
                    .padding(horizontal = skin.spaceLg, vertical = skin.spaceWide),
                verticalArrangement = Arrangement.spacedBy(skin.spaceMd),
            ) {
                content()
            }
        }
    }
}
