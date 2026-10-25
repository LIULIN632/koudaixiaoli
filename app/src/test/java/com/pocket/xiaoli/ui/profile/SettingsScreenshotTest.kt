package com.pocket.xiaoli.ui.profile

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.DarkMode
import androidx.compose.material.icons.outlined.GridOn
import androidx.compose.material.icons.outlined.LocationCity
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.Palette
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.dp
import com.github.takahirom.roborazzi.RobolectricDeviceQualifiers
import com.github.takahirom.roborazzi.captureRoboImage
import com.pocket.xiaoli.ui.components.MiuixSettingsScaffold
import com.pocket.xiaoli.ui.theme.AppearanceMode
import com.pocket.xiaoli.ui.theme.LocalPocketXiaoliColors
import com.pocket.xiaoli.ui.theme.LocalProfileRowPadding
import com.pocket.xiaoli.ui.theme.LocalSkin
import com.pocket.xiaoli.ui.theme.PocketXiaoliTheme
import com.pocket.xiaoli.ui.theme.ServiceAccent
import com.pocket.xiaoli.ui.theme.SkinStyle
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * 设置页**外观**基线（MIUIX 大标题外壳 + 分组小标题 + 卡片 + 三种行）。
 *
 * ## 为什么要有这一组
 *
 * 2026-10-18 用户提「设置页不够好看，直接用岱宗的」—— 这一轮把 Material3 的
 * 小标题顶栏换成了 MIUIX 的 `Scaffold` + `TopAppBar(largeTitle = …)`，组标题换成
 * `SmallTitle`，卡片换成 MIUIX `Card`，开关换成 MIUIX `Switch`。
 *
 * 这类改造**编译与单测全绿也可能难看**（`memory/04-ui-verification.md`：
 * "UI 改完不看图等于没验"），所以用一张整页图把三件事钉住：
 *
 * 1. **大标题的层次**：页名 28sp 大标题压住整屏，而不是又一条 15sp 的粗体行；
 * 2. **组标题与卡片的关系**：`SmallTitle` 是索引不是标题，卡才是分组；
 * 3. **行高与图标**：图标缩到 32dp、切图标 18dp 之后，长副标题不会把行撑歪。
 *
 * ⚠️ 深浅两张都要有：MIUIX 的 `surfaceVariant` 由本项目的 9 个令牌映射而来
 * （`MiuixColorMapping.kt`），深色下若映射不对，卡会比页底还暗、层级反过来。
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = RobolectricDeviceQualifiers.Pixel5)
class SettingsScreenshotTest {

    @get:Rule
    val composeRule = createComposeRule()

    private data class Case(val name: String, val dark: Boolean)

    private val cases = listOf(
        Case("150-settings-miuix-light.png", dark = false),
        Case("151-settings-miuix-dark.png", dark = true),
    )

    @Test
    fun `设置页外观截图`() {
        val current = mutableStateOf(cases.first())
        composeRule.setContent {
            val case = current.value
            PocketXiaoliTheme(
                appearanceMode = AppearanceMode.RAINBOW,
                skinStyle = SkinStyle.VIVID,
                darkTheme = case.dark,
            ) {
                CompositionLocalProvider(LocalProfileRowPadding provides 14.dp) {
                    var switchOn by remember { mutableStateOf(true) }
                    var pick by remember { mutableStateOf(0) }
                    // 与设置页真实入口同参：顶栏接管状态栏（Robolectric 下 inset 为 0，
                    // 所以图上看到的是渐变底色本身，状态栏那一段不会额外出现）。
                    MiuixSettingsScaffold(
                        title = "设置",
                        onBack = {},
                        manageStatusBar = true,
                    ) { padding ->
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(padding)
                                .background(LocalPocketXiaoliColors.current.surfaceBase)
                                .verticalScroll(rememberScrollState())
                                .padding(
                                    horizontal = LocalSkin.current.spaceLg,
                                    vertical = 12.dp,
                                ),
                            verticalArrangement = Arrangement.spacedBy(LocalSkin.current.spaceWide),
                        ) {
                            SettingsGroup(title = "课表与学习") {
                                SettingsMenuItem(
                                    icon = Icons.Outlined.CalendarMonth,
                                    title = "课表",
                                    subtitle = "校区 · 时间段 · 学期 · 调休 · 网格边框",
                                    onClick = {},
                                    accent = ServiceAccent.ACADEMIC,
                                )
                                SettingsDivider()
                                SettingsSwitchItem(
                                    icon = Icons.Outlined.Notifications,
                                    title = "上课提醒",
                                    subtitle = "提前 10 分钟提醒",
                                    checked = switchOn,
                                    onToggle = { switchOn = !switchOn },
                                    accent = ServiceAccent.LIFE,
                                )
                            }
                            SettingsGroup(title = "外观") {
                                SettingsMenuChoiceItem(
                                    icon = Icons.Outlined.DarkMode,
                                    title = "深色/浅色模式",
                                    subtitle = "整界面配色跟随它",
                                    options = listOf(
                                        "浅色模式" to "light",
                                        "深色模式" to "dark",
                                        "跟随系统" to "system",
                                    ),
                                    selected = "light",
                                    onSelect = {},
                                    accent = ServiceAccent.STUDY,
                                )
                                SettingsDivider()
                                SettingsChoiceItem(
                                    icon = Icons.Outlined.Palette,
                                    title = "界面风格",
                                    subtitle = "决定圆角、留白与配色的浓度",
                                    options = listOf("清新" to 0, "浓郁" to 1),
                                    selected = pick,
                                    onSelect = { pick = it },
                                    accent = ServiceAccent.QUERY,
                                )
                            }
                        }
                    }
                }
            }
        }
        cases.forEach { case ->
            current.value = case
            composeRule.waitForIdle()
            composeRule.onRoot().captureRoboImage("src/test/screenshots/${case.name}")
        }
    }

    /**
     * 设置**首页**（七类入口）整页图。
     *
     * 上面那张是"三种行的外观"，图里只有两组、且是测试自己拼的；这张直接跑
     * 真实的 [SettingsHomeContent]，因为首页在 2026-10-23 这轮换了图标块语言
     * （32dp 淡色圆 → 36dp 分类色圆角方），需要钉住的是**七行放在一起的节奏**：
     * 分类色够不够分得开、卡片会不会太花、危险操作那一行有没有抢戏。
     *
     * 开发者实验室按解锁态渲染：普通态只是少一组，没必要占两倍的基线数量。
     */
    @Test
    fun `设置首页整页截图`() {
        val homepageCases = listOf(
            "175-settings-home-light.png" to false,
            "176-settings-home-dark.png" to true,
        )
        val current = mutableStateOf(homepageCases.first())
        composeRule.setContent {
            val (_, dark) = current.value
            PocketXiaoliTheme(
                appearanceMode = AppearanceMode.RAINBOW,
                skinStyle = SkinStyle.VIVID,
                darkTheme = dark,
            ) {
                CompositionLocalProvider(LocalProfileRowPadding provides 14.dp) {
                    MiuixSettingsScaffold(
                        title = "设置",
                        onBack = {},
                        manageStatusBar = true,
                    ) { padding ->
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(padding)
                                .background(LocalPocketXiaoliColors.current.surfaceBase)
                                .verticalScroll(rememberScrollState())
                                .padding(
                                    horizontal = LocalSkin.current.spaceLg,
                                    vertical = 12.dp,
                                ),
                        ) {
                            SettingsHomeContent(
                                developerUnlocked = true,
                                onOpenDeveloperLab = {},
                                onOpenPage = {},
                            )
                        }
                    }
                }
            }
        }
        homepageCases.forEach { case ->
            current.value = case
            composeRule.waitForIdle()
            composeRule.onRoot().captureRoboImage("src/test/screenshots/${case.first}")
        }
    }

    /**
     * 「?」说明入口的行高与位置（2026-10-24 起，多条设置行不再写副标题）。
     *
     * 钉两件事：
     * 1. **44dp 的触摸盒不把行撑高** —— 带「?」的行应与普通行同一高度档
     *    （图标块 36dp 本来就高于文字列，问号只是把内容高度从 36 抬到 44）；
     * 2. 问号在**行尾、箭头/开关之前**，不挤占标题列，也不会把右侧的当前值挤掉。
     *
     * 覆盖四种行：菜单行 + 问号、多选行 + 问号、菜单行 + 副标题（无问号）、开关行 + 问号。
     */
    @Test
    fun `设置行问号入口截图`() {
        val cases = listOf(
            "177-settings-row-hint.png" to false,
            "178-settings-row-hint-dark.png" to true,
        )
        val current = mutableStateOf(cases.first())
        composeRule.setContent {
            val (_, dark) = current.value
            PocketXiaoliTheme(
                appearanceMode = AppearanceMode.RAINBOW,
                skinStyle = SkinStyle.VIVID,
                darkTheme = dark,
            ) {
                CompositionLocalProvider(LocalProfileRowPadding provides 14.dp) {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(LocalPocketXiaoliColors.current.surfaceBase)
                            .verticalScroll(rememberScrollState())
                            .padding(
                                horizontal = LocalSkin.current.spaceLg,
                                vertical = 12.dp,
                            ),
                    ) {
                        SettingsCard {
                            SettingsMenuItem(
                                icon = Icons.Outlined.CalendarMonth,
                                title = "学期设置",
                                onClick = {},
                                accent = ServiceAccent.ACADEMIC,
                                hint = "学期开始日期决定「当前第几周」怎么算，开学前改一次就行。",
                            )
                            SettingsDivider()
                            SettingsMenuChoiceItem(
                                icon = Icons.Outlined.GridOn,
                                title = "课表网格边框",
                                options = listOf("无" to 0, "实线" to 1, "虚线" to 2),
                                selected = 1,
                                onSelect = {},
                                accent = ServiceAccent.STUDY,
                                hint = "同时作用于课表网格和教室平面图。",
                            )
                            SettingsDivider()
                            SettingsMenuItem(
                                icon = Icons.Outlined.LocationCity,
                                title = "当前校区",
                                subtitle = "烟理",
                                onClick = {},
                                accent = ServiceAccent.QUERY,
                            )
                            SettingsDivider()
                            SettingsSwitchItem(
                                icon = Icons.Outlined.Notifications,
                                title = "上课提醒总开关",
                                checked = true,
                                onToggle = {},
                                accent = ServiceAccent.LIFE,
                                hint = "课前自动发一条提醒。",
                            )
                        }
                    }
                }
            }
        }
        cases.forEach { case ->
            current.value = case
            composeRule.waitForIdle()
            composeRule.onRoot().captureRoboImage("src/test/screenshots/${case.first}")
        }
    }
}
