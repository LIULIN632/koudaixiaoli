package com.pocket.xiaoli.ui.assistant

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.github.takahirom.roborazzi.RobolectricDeviceQualifiers
import com.github.takahirom.roborazzi.captureRoboImage
import com.pocket.xiaoli.data.proactive.ProactiveLevel
import com.pocket.xiaoli.ui.theme.AppearanceMode
import com.pocket.xiaoli.ui.theme.LocalPocketXiaoliColors
import com.pocket.xiaoli.ui.theme.LocalSkin
import com.pocket.xiaoli.ui.theme.MascotAvatar
import com.pocket.xiaoli.ui.theme.MascotFaces
import com.pocket.xiaoli.ui.theme.MascotLook
import com.pocket.xiaoli.ui.theme.MascotPalette
import com.pocket.xiaoli.ui.theme.MascotShapes
import com.pocket.xiaoli.ui.theme.PocketXiaoliTheme
import com.pocket.xiaoli.ui.theme.SkinStyle
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * 鲤鱼「AI 设置」这一轮新增的两个界面块的视觉基线 + 语义检查。
 *
 * ## 为什么"六形状一览"（90）是这组里最重要的一张
 *
 * 形状是一串 path 数据，**画出来像不像一条鱼是它唯一的验收标准** ——
 * 而这一点在代码里、在别的测试里都看不出来。这张图同时也是"path 能被解析"的守卫：
 * `PathParser` 遇到畸形数据会直接抛，那时这张基线根本渲不出来。
 *
 * ## 为什么只渲"段"而不渲整页
 *
 * `AssistantAiSettingsScreen` 的本体要 `appContainer`（会连上 Room / prefs）。
 * 而它真正有观感风险的部分就是 `MascotSettingsSection` / `AppearanceSettingsSection` ——
 * 模型那一段是既有实现的搬运，样式未动。所以把两段抽成**纯状态驱动**的 internal 函数后，
 * 测试可以直接喂状态渲染，不欠债也不搭脚手架。
 *
 * ## 语义检查与截图放在同一个文件
 *
 * 选中态（形状 / 颜色）在截图上只是一圈描边 —— 缩略图里几乎看不见，
 * 而它恰恰是这两组控件的全部意义。所以与 `AssistantWelcomeAndListScreenshotTest`
 * 同一套做法：**图 + `assertIsSelected` 一起**。
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = RobolectricDeviceQualifiers.Pixel5)
class AssistantSettingsScreenshotTest {

    @get:Rule
    val composeRule = createComposeRule()

    /** 六个形状并排，固定蓝色、不眨眼（这一张要审的是**轮廓**）。 */
    @Test
    fun `吉祥物六形状一览`() {
        composeRule.setContent {
            Screen {
                val colors = LocalPocketXiaoliColors.current
                Column(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    MascotShapes.all.chunked(3).forEach { row ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceEvenly,
                        ) {
                            row.forEach { shape ->
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    MascotAvatar(
                                        size = 84.dp,
                                        look = MascotLook(shape.name, MascotPalette.DEFAULT),
                                        // ⚠️ 宫格与一览都不眨眼：六个 rememberInfiniteTransition
                                        // 几乎同时启动，一起眨看起来像故障；而快照要的是确定的一帧
                                        blinking = false,
                                    )
                                    Spacer(Modifier.height(6.dp))
                                    // ⚠️ 颜色必须显式给：裸 `Text` 取的是 `LocalContentColor`，
                                    // 它在没有 Surface 的裸 Column 里恒为黑 —— 深色主题下等于没写
                                    Text(shape.name, fontSize = 12.sp, color = colors.textPrimary)
                                }
                            }
                        }
                        Spacer(Modifier.height(12.dp))
                    }
                }
            }
        }
        composeRule.onRoot().captureRoboImage("src/test/screenshots/90-mascot-shapes.png")
    }

    /**
     * 深色主题下的四个颜色。
     *
     * 存在的理由：色盘里最暗的 `#2B2F38` 与最亮的 `#F5C518` 在深色底上差得极远，
     * 而"深色的鱼看不见了"是这一轮唯一**可能真的发生**的可用性事故
     *（调研稿原本给的调色板里还有纯黑）。
     */
    @Test
    fun `深色主题下的吉祥物配色`() {
        composeRule.setContent {
            Screen(dark = true) {
                val skin = LocalSkin.current
                val colors = LocalPocketXiaoliColors.current
                Column(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = skin.spaceLg),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    listOf("#3B8BFF", "#F5C518", "#2B2F38", "#C7A8F0").chunked(2).forEach { row ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceEvenly,
                        ) {
                            row.forEach { hex ->
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    MascotAvatar(
                                        size = 84.dp,
                                        look = MascotLook(MascotShapes.defaultName, hex),
                                        blinking = false,
                                    )
                                    Spacer(Modifier.height(6.dp))
                                    Text(MascotPalette.labelOf(hex), fontSize = 12.sp, color = colors.textPrimary)
                                }
                            }
                        }
                        Spacer(Modifier.height(16.dp))
                    }
                }
            }
        }
        composeRule.onRoot().captureRoboImage("src/test/screenshots/91-mascot-dark.png")
    }

    /**
     * DS Bot 那 7 个形象的**原版配色**一览。
     *
     * 与 90 分开录的理由：90 的宫格是"轮廓清单"，颜色用的是当前色盘色；
     * 这一张才是"它本来长什么样"的证据 —— 星星的三色渐变、红幽灵、橙方块、黑猫…
     * 一旦谁把 `body` 弄丢或改成走色盘，这张图立刻会变成七条同色鱼。
     */
    @Test
    fun `DS 的七个形象按原版配色`() {
        composeRule.setContent {
            Screen {
                val colors = LocalPocketXiaoliColors.current
                Column(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    MascotShapes.all.filter { it.body != null }.chunked(4).forEach { row ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceEvenly,
                        ) {
                            row.forEach { shape ->
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    MascotAvatar(
                                        size = 68.dp,
                                        look = MascotLook(shape.name, MascotPalette.DEFAULT),
                                        blinking = false,
                                    )
                                    Spacer(Modifier.height(6.dp))
                                    Text(shape.name, fontSize = 12.sp, color = colors.textPrimary)
                                }
                            }
                        }
                        Spacer(Modifier.height(14.dp))
                    }
                }
            }
        }
        composeRule.onRoot().captureRoboImage("src/test/screenshots/96-mascot-native-colors.png")
    }

    /** 「形象」段：大预览（会眨眼）+ 形状宫格 + 色盘。默认形象。 */
    @Test
    fun `形象设置段截图`() {
        composeRule.setContent {
            Screen {
                Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                    MascotSettingsSection(
                        look = MascotLook(MascotShapes.defaultName, MascotPalette.DEFAULT),
                        onPickShape = {},
                        onPickColor = {},
                    )
                }
            }
        }
        composeRule.onRoot().captureRoboImage("src/test/screenshots/92-ai-settings-mascot.png")
    }

    /**
     * 「形象」段：换到**河豚 + 黄**之后。
     *
     * 与 92 成对：只录默认态的话，"选中态有没有跟着走"这件事没有证据 ——
     * 描边与勾都挂在选中项上，而默认态里选中项恰好是第一格。
     */
    @Test
    fun `形象设置段换形状换色截图`() {
        composeRule.setContent {
            Screen {
                Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                    MascotSettingsSection(
                        look = MascotLook("河豚", "#F5C518"),
                        onPickShape = {},
                        onPickColor = {},
                    )
                }
            }
        }
        composeRule.onRoot().captureRoboImage("src/test/screenshots/93-ai-settings-mascot-swapped.png")
    }

    /**
     * DS 那 7 个形象在**深色主题**下的样子。
     *
     * 存在的理由和 91 一样、但更尖锐：`月亮` 与 `睡猫` 的原版底色是近黑（#2D2D2D / #141416），
     * 深色底上"能不能看出轮廓"是这次接入**唯一可能真的翻车**的地方。
     * 结论写在这张图上：身影靠它们自己的白色高光层与白眼睛辨认，不做额外描边（不改造原版）。
     */
    @Test
    fun `DS 形象在深色主题下`() {
        composeRule.setContent {
            Screen(dark = true) {
                val skin = LocalSkin.current
                val colors = LocalPocketXiaoliColors.current
                Column(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = skin.spaceLg),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    MascotShapes.all.filter { it.body != null }.chunked(2).forEach { row ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceEvenly,
                        ) {
                            row.forEach { shape ->
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    MascotAvatar(
                                        size = 84.dp,
                                        look = MascotLook(shape.name, MascotPalette.DEFAULT),
                                        blinking = false,
                                    )
                                    Spacer(Modifier.height(6.dp))
                                    Text(shape.name, fontSize = 12.sp, color = colors.textPrimary)
                                }
                            }
                        }
                        Spacer(Modifier.height(16.dp))
                    }
                }
            }
        }
        composeRule.onRoot().captureRoboImage("src/test/screenshots/98-mascot-native-dark.png")
    }

    /**
     * 「形象」段：选中**自带配色**的形象（幽灵）之后。
     *
     * 这一张守两件事，都是语义层面看不出来的：
     * ① 色盘整段**消失**、换成一句解释 —— 否则用户会对着点不动的色盘说"坏了"；
     * ② 副标题是「幽灵 · 原版配色」而不是「幽灵 · 蓝」—— 后者在撒谎。
     */
    @Test
    fun `形象设置段选中自带配色的形象截图`() {
        composeRule.setContent {
            Screen {
                Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                    MascotSettingsSection(
                        look = MascotLook("幽灵", MascotPalette.DEFAULT),
                        onPickShape = {},
                        onPickColor = {},
                    )
                }
            }
        }
        composeRule.onRoot().captureRoboImage("src/test/screenshots/97-ai-settings-native-mascot.png")
    }

    /**
     * 「形象」段：选中 **Grok Ball 的圆球** 之后。
     *
     * 守两件事：① 「表情」那一排只在眼形可变的形象下出现（其余 14 个形状眼形固定）；
     * ② 副标题带上当前表情名 —— 否则用户点了 chip 看不出到底换没换。
     */
    @Test
    fun `形象设置段选中 Grok 形象会多出表情一排`() {
        composeRule.setContent {
            Screen {
                Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                    MascotSettingsSection(
                        look = MascotLook("圆球", MascotPalette.DEFAULT),
                        onPickShape = {},
                        onPickColor = {},
                    )
                }
            }
        }
        composeRule.onRoot().captureRoboImage("src/test/screenshots/114-ai-settings-faces.png")
    }

    /**
     * Grok 三个身体的眼形抽样：同一套 48 点眼环在三副轮廓上按各自的 `face` 投影。
     *
     * 这一张的价值在**几何**：眼环是原作者坐标系（`HEAD_C = 114.2705`）里的数据，
     * 投错一步就会有一只眼飘到三角形外面（第一版没乘 `face` 时就那样）。
     */
    @Test
    fun `Grok 三副身体的眼环投影抽样`() {
        val samples = listOf("02", "30", "32", "34")
        composeRule.setContent {
            Screen {
                Column(modifier = Modifier.fillMaxWidth().padding(horizontal = LocalSkin.current.spaceLg)) {
                    MascotShapes.all.filter { it.projection != null }.forEach { shape ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceEvenly,
                        ) {
                            samples.forEach { id ->
                                val face = MascotFaces.byId(id)
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    MascotAvatar(
                                        size = 84.dp,
                                        look = MascotLook(shape.name, MascotPalette.DEFAULT),
                                        blinking = false,
                                        expression = face,
                                    )
                                    Spacer(Modifier.height(4.dp))
                                    Text(
                                        "${shape.name} · ${face.label}",
                                        fontSize = 11.sp,
                                        color = LocalPocketXiaoliColors.current.textPrimary,
                                    )
                                }
                            }
                        }
                        Spacer(Modifier.height(12.dp))
                    }
                }
            }
        }
        composeRule.onRoot().captureRoboImage("src/test/screenshots/115-grok-faces.png")
    }

    /** 「外观」段：字号选「大」、思考链开。 */
    @Test
    fun `外观设置段截图`() {
        composeRule.setContent {
            Screen {
                AppearanceSettingsSection(
                    fontScale = 2,
                    showReasoning = true,
                    onFontScale = {},
                    onReasoning = {},
                )
            }
        }
        composeRule.onRoot().captureRoboImage("src/test/screenshots/94-ai-settings-appearance.png")
    }

    /**
     * 形状与颜色两组控件都必须**把选中态说给读屏**。
     *
     * 改造前这两组控件不存在；而同类坑本项目踩过：会话列表的"当前会话"只有一圈主色描边，
     * 对读屏用户等于不存在。所以这里**同时**断言选中与未选中的两格 ——
     * 只断言"选中那格是选中的"会漏掉"所有格子都变成选中"这种更糟的 bug。
     */
    @Test
    fun `形状与颜色格子都带选中语义`() {
        composeRule.setContent {
            Screen {
                Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                    MascotSettingsSection(
                        look = MascotLook("河豚", "#F5C518"),
                        onPickShape = {},
                        onPickColor = {},
                    )
                }
            }
        }

        composeRule.onNodeWithText("河豚").assertIsSelected()
        composeRule.onNodeWithText("鲤鱼").assertIsNotSelected()

        composeRule.onNodeWithContentDescription("黄").assertIsSelected()
        composeRule.onNodeWithContentDescription("蓝").assertIsNotSelected()
    }

    /**
     * 色块的触摸高度。
     *
     * 色块本身只有 34dp（视觉上是个小圆点），而 `selectable` 会自动补足最小交互尺寸 ——
     * 这条断言守的就是"那个自动补足还在"（判据与 `AssistantAccessibilityTest` 一致：≥44dp）。
     */
    @Test
    fun `色块的触摸高度达标`() {
        composeRule.setContent {
            Screen {
                Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                    MascotSettingsSection(
                        look = MascotLook(),
                        onPickShape = {},
                        onPickColor = {},
                    )
                }
            }
        }

        composeRule.onNodeWithContentDescription("蓝").assertHeightIsAtLeast(44.dp)
    }

    /**
     * 「主动提醒」段：标准档 + 每档一枚「?」说明。
     *
     * 与 92/94 同一个理由：这一段也是**纯状态驱动**的，能单独渲就该单独留基线 ——
     * 它此前完全不存在，而"选中态挂在哪一行""说明气泡的图标有没有挤到文字"这种事
     * 只能靠图看。默认档是标准（见 `ProactiveLevel` 的注释）。
     */
    @Test
    fun `主动提醒段截图`() {
        composeRule.setContent {
            Screen {
                ProactiveSettingsSection(
                    level = ProactiveLevel.STANDARD,
                    onPick = {},
                )
            }
        }
        composeRule.onRoot().captureRoboImage("src/test/screenshots/99-ai-settings-proactive.png")
    }

    /**
     * 三档是"多选一"，读屏必须念得出选中态。
     *
     * 只断言"标准是选中的"会漏掉"三行全变选中"这种更糟的 bug，所以两行都断言。
     */
    @Test
    fun `主动提醒三档带选中语义`() {
        composeRule.setContent {
            Screen {
                ProactiveSettingsSection(
                    level = ProactiveLevel.LOW,
                    onPick = {},
                )
            }
        }
        composeRule.onNodeWithText("少").assertIsSelected()
        composeRule.onNodeWithText("关").assertIsNotSelected()
        composeRule.onNodeWithText("标准").assertIsNotSelected()
    }

    /**
     * 设置首页：一张状态卡 + 一组「助手的样子」+ 一组「连接与隐私」。
     *
     * 这张图守的是**信息层级**：首页只回答"有哪几项、现在各是什么值"，
     * 控件全部退到二级页。缩略图上若出现形状宫格或输入框，说明二级页的东西漏回了首页。
     */
    @Test
    fun `设置首页截图`() {
        composeRule.setContent {
            Screen {
                AssistantSettingsHome(
                    look = MascotLook("河豚", "#F5C518"),
                    fontScale = 1,
                    showReasoning = true,
                    proactiveLevel = ProactiveLevel.STANDARD,
                    configured = true,
                    modelName = "deepseek-chat",
                    hasUnsavedModel = false,
                    onOpenMascot = {},
                    onOpenAppearance = {},
                    onOpenProactive = {},
                    onOpenModel = {},
                )
            }
        }
        composeRule.onRoot().captureRoboImage("src/test/screenshots/120-ai-settings-home.png")
    }

    /**
     * 设置首页的深色 + 待配置态。
     *
     * 与 120 成对：只录"已就绪"的话，状态胶囊、英雄卡渐变与四个图标块在
     * **深色底 + 未配置**下是否还看得清，就没有证据。四个摘要也刻意取另一组值，
     * 顺便守"摘要真的跟着状态走、不是写死的四句话"。
     */
    @Test
    fun `设置首页深色待配置截图`() {
        composeRule.setContent {
            Screen(dark = true) {
                AssistantSettingsHome(
                    look = MascotLook(MascotShapes.defaultName, MascotPalette.DEFAULT),
                    fontScale = 2,
                    showReasoning = false,
                    proactiveLevel = ProactiveLevel.OFF,
                    configured = false,
                    modelName = "deepseek-chat",
                    hasUnsavedModel = false,
                    onOpenMascot = {},
                    onOpenAppearance = {},
                    onOpenProactive = {},
                    onOpenModel = {},
                )
            }
        }
        composeRule.onRoot().captureRoboImage("src/test/screenshots/121-ai-settings-home-dark.png")
    }

    /**
     * 四个摘要读的是与二级页同一批真源，且入口行高度达标。
     *
     * 摘要写错不会崩、也不会被截图发现（图上只是四行小字），但对用户是直接的错误信息；
     * 所以这里逐条按值断言。行高下限 44dp 是本项目无障碍底线，这一行是首页唯一的点击目标。
     */
    @Test
    fun `设置首页的摘要跟着状态走`() {
        composeRule.setContent {
            Screen {
                AssistantSettingsHome(
                    look = MascotLook("河豚", "#F5C518"),
                    fontScale = 1,
                    showReasoning = true,
                    proactiveLevel = ProactiveLevel.STANDARD,
                    configured = true,
                    modelName = "deepseek-chat",
                    hasUnsavedModel = false,
                    onOpenMascot = {},
                    onOpenAppearance = {},
                    onOpenProactive = {},
                    onOpenModel = {},
                )
            }
        }

        composeRule.onNodeWithText("已就绪").assertIsDisplayed()
        composeRule.onNodeWithText("河豚 · 黄").assertIsDisplayed()
        composeRule.onNodeWithText("字号标准 · 思考链开").assertIsDisplayed()
        composeRule.onNodeWithText("标准 · 偶尔也会闲聊几句").assertIsDisplayed()
        composeRule.onNodeWithText("deepseek-chat · 已保存").assertIsDisplayed()
        composeRule.onNodeWithText("形象与动作").assertHeightIsAtLeast(44.dp)
    }

    /** 未配置与"改了没存"是两个不同的状态，首页必须各说各的。 */
    @Test
    fun `设置首页区分未配置与未保存`() {
        composeRule.setContent {
            Screen {
                AssistantSettingsHome(
                    look = MascotLook(),
                    fontScale = 0,
                    showReasoning = false,
                    proactiveLevel = ProactiveLevel.LOW,
                    configured = false,
                    modelName = "deepseek-chat",
                    hasUnsavedModel = false,
                    onOpenMascot = {},
                    onOpenAppearance = {},
                    onOpenProactive = {},
                    onOpenModel = {},
                )
            }
        }

        composeRule.onNodeWithText("待配置").assertIsDisplayed()
        composeRule.onNodeWithText("还没填 API Key，点这里配置").assertIsDisplayed()
        composeRule.onNodeWithText("填好接口地址、模型名和 Key，就能开始对话").assertIsDisplayed()
    }

    @Test
    fun `设置首页会提示模型有未保存的修改`() {
        composeRule.setContent {
            Screen {
                AssistantSettingsHome(
                    look = MascotLook(),
                    fontScale = 1,
                    showReasoning = true,
                    proactiveLevel = ProactiveLevel.STANDARD,
                    configured = true,
                    modelName = "deepseek-chat",
                    hasUnsavedModel = true,
                    onOpenMascot = {},
                    onOpenAppearance = {},
                    onOpenProactive = {},
                    onOpenModel = {},
                )
            }
        }

        composeRule.onNodeWithText("deepseek-chat · 有未保存的修改").assertIsDisplayed()
    }

    /** 四个入口**都能点**，且各自只触发自己的回调（点形象不该打开模型页）。 */
    @Test
    fun `设置首页四个入口都能点开二级页`() {
        var opened: String? = null
        composeRule.setContent {
            Screen {
                AssistantSettingsHome(
                    look = MascotLook(),
                    fontScale = 1,
                    showReasoning = true,
                    proactiveLevel = ProactiveLevel.STANDARD,
                    configured = true,
                    modelName = "deepseek-chat",
                    hasUnsavedModel = false,
                    onOpenMascot = { opened = "mascot" },
                    onOpenAppearance = { opened = "appearance" },
                    onOpenProactive = { opened = "proactive" },
                    onOpenModel = { opened = "model" },
                )
            }
        }

        listOf(
            "形象与动作" to "mascot",
            "对话外观" to "appearance",
            "主动提醒" to "proactive",
            "模型与密钥" to "model",
        ).forEach { (title, expected) ->
            opened = null
            composeRule.onNodeWithText(title).performClick()
            composeRule.runOnIdle {
                org.junit.Assert.assertEquals("点「$title」应只打开 $expected", expected, opened)
            }
        }
    }

    /** 统一的主题外壳：与别的快照测试同一套（马卡龙 · 彩虹 · 鲜明）。 */
    @Composable
    private fun Screen(dark: Boolean = false, content: @Composable () -> Unit) {
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
