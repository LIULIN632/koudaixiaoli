package com.pocket.xiaoli.ui.treasurebox

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FlashOn
import androidx.compose.material.icons.outlined.FlashOn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.dp
import com.github.takahirom.roborazzi.RobolectricDeviceQualifiers
import com.github.takahirom.roborazzi.captureRoboImage
import com.pocket.xiaoli.ui.theme.AppearanceMode
import com.pocket.xiaoli.ui.theme.CampusLayoutMode
import com.pocket.xiaoli.ui.theme.LocalCampusLayoutMode
import com.pocket.xiaoli.ui.theme.LocalPocketXiaoliColors
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
 * 「工具箱模式」金刚格的 **JVM 快照基线**。
 *
 * ## 它为什么必须存在
 *
 * `ServiceGridCell` 是 2026-10 新增的第二种布局范式（见 `ServiceGridCell` 的 KDoc）。
 * 落地时 **68 张基线全部 provide `CampusLayoutMode.MODERN`** —— 也就是说
 * **金刚格这一档既没被证明好看、也没被证明没坏**，是一条零覆盖的渲染路径。
 *
 * 这与本项目既有的判据直接冲突：
 *
 * > 「截断只有把图渲出来才看得见」—— 课表格子 `100/101/102` 三张基线的由来
 * >（语义树里那行文字是完整课名，`onNodeWithText` 查不出"它被截成了什么样"）。
 *
 * ## ⚠️ 复现真实宽度约束才是关键（照抄 `SkinScreenshotTest` 的同一条纪律）
 *
 * 服务页金刚格的一格宽 = `(屏宽 − 页边距×2 − 间距×3) / 4`。
 * 所以这里必须把 [ServiceGridCell] 放进 `Row` 并给 `Modifier.weight(1f)`。
 * 若图省事直接给 `Modifier.fillMaxWidth()`，格子会宽到永远不触发折行/截断 ——
 * **那条测试就是假的**：它会一直绿，哪怕真实界面已被挤断。
 *
 * ## 刻意选的是**边界组合**
 *
 * 4 字名、5 字名、带角标（WARNING 与 DANGER 两种描边变体）、以及**末行不满**
 *（3 格 + 1 空位）—— 末行那一条守的是"最后几个格子被拉宽、与上面的列对不齐"。
 *
 * ## 产出与门禁
 *
 * 基准图落在随仓库提交的 `app/src/test/screenshots/`。
 * 默认 verify 模式逐像素比对；**观感有意变更后**用
 * `.\gradlew :app:testDebugUnitTest -PrecordScreenshots` 重录并一起提交。
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = RobolectricDeviceQualifiers.Pixel5)
class ServiceGridScreenshotTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun `金刚格真实格子截图`() {
        val current = mutableStateOf(SkinStyle.VIVID)
        composeRule.setContent {
            // ⚠️ 必须显式 provide `TOOLBOX` —— 这条渲染路径**只在**该模式下才会被走到，
            // 而 `LocalCampusLayoutMode` 的默认值是 `MODERN`。
            CompositionLocalProvider(LocalCampusLayoutMode provides CampusLayoutMode.TOOLBOX) {
                PocketXiaoliTheme(
                    appearanceMode = AppearanceMode.RAINBOW,
                    skinStyle = current.value,
                    darkTheme = false,
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(LocalPocketXiaoliColors.current.surfaceBase),
                    ) {
                        ServiceGridSample()
                    }
                }
            }
        }
        listOf(
            "22-vivid-service-grid" to SkinStyle.VIVID,
            "23-minimal-service-grid" to SkinStyle.MINIMAL,
        ).forEach { (name, skin) ->
            composeRule.runOnIdle { current.value = skin }
            composeRule.waitForIdle()
            composeRule.onRoot().captureRoboImage("src/test/screenshots/$name.png")
        }
    }
}

/**
 * 金刚格的真实版式片段：4 列一行 + 一行不满。
 *
 * 版式参数（页边距、列间距、末行补空位）**逐条对齐** `ServicesBody` 的 `gridLayout` 分支，
 * 否则渲出来的是生产里不存在的布局。
 */
@Composable
private fun ServiceGridSample() {
    val skin = LocalSkin.current
    val colors = LocalPocketXiaoliColors.current

    fun entry(
        title: String,
        badge: String? = null,
        tone: BadgeTone = BadgeTone.NEUTRAL,
        accent: ServiceAccent = ServiceAccent.PAYMENT,
    ) = ServiceEntry(
        title = title,
        subtitle = "",
        icon = Icons.Outlined.FlashOn,
        tint = Color.Unspecified,
        background = Color.Unspecified,
        iconFilled = Icons.Filled.FlashOn,
        accent = accent,
        badge = badge,
        badgeTone = tone,
        onClick = {},
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.surfaceBase)
            .padding(horizontal = skin.spaceLg, vertical = skin.spaceWide),
        verticalArrangement = Arrangement.spacedBy(skin.spaceLg),
    ) {
        // 满行：4 格。含 4 字名、5 字名、以及两种角标变体。
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(skin.spaceSm),
        ) {
            ServiceGridCell(entry("宿舍电费", "待绑定", BadgeTone.WARNING), Modifier.weight(1f))
            ServiceGridCell(entry("洗衣机", accent = ServiceAccent.LIFE), Modifier.weight(1f))
            // 5 字名：4 列下最紧的一档
            ServiceGridCell(entry("普通话考试", accent = ServiceAccent.ACADEMIC), Modifier.weight(1f))
            // DANGER 走「surfaceCard 底 + 35% 描边」而非低透明度底色
            ServiceGridCell(
                entry("乘车码", "不可用", BadgeTone.DANGER, ServiceAccent.INFO),
                Modifier.weight(1f),
            )
        }
        // 不满行：3 格 + 1 空位 —— 守"最后几个格子被拉宽、与上面的列对不齐"
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(skin.spaceSm),
        ) {
            // 7 字名 = 现实里最宽的一档（`计算机等级考试`），守 `maxLines = 2` 的折行路径。
            //
            // ⚠️ 2026-10 重录后**这一格已不再折行了**：全局继承默认样式从 M3 出厂值
            //（16sp / 行高 24sp / **字距 0.5sp**）改成 `PocketTypography.body`
            //（14sp / 22sp / **无字距**）之后，同样 7 个字变窄了约 5px，一行放得下。
            // 量出来的证据：该行名称带只剩一条（改前有两条，第二行是孤零零的「试」）。
            //
            // 所以本图现在守的是"**最宽的真实名能一行放下**"（更该守的那件事 ——
            // 折成「计算机等级考 / 试」本来就难看）；`maxLines = 2` 作为安全网保留。
            ServiceGridCell(
                entry("计算机等级考试", accent = ServiceAccent.STUDY),
                Modifier.weight(1f),
            )
            ServiceGridCell(entry("空教室查询", accent = ServiceAccent.QUERY), Modifier.weight(1f))
            ServiceGridCell(entry("校园卡", "待登录", BadgeTone.INFO), Modifier.weight(1f))
            repeat(SERVICE_GRID_COLUMNS - 3) { Spacer(Modifier.weight(1f)) }
        }
    }
}
