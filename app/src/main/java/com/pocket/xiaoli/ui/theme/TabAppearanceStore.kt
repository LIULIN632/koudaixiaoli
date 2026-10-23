package com.pocket.xiaoli.ui.theme

import android.content.Context
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * 「我的」页各列表行的垂直内边距。
 *
 * 由 `AppNavigation` 的 `profile` 路由从 [TabAppearance.profileRowVerticalPadding] 提供；
 * 行组件（`ChecklistRow` / `DangerRow` / `SettingsMenuItem` / `SettingsGroupCard`）直接读。
 *
 * ## ⚠️ 为什么是 CompositionLocal 而不是往参数里穿透
 *
 * 它要影响**四个文件里的五种行**（绑定信息清单、销毁操作行、设置页两种行、我的页分组卡），
 * 而它们中间隔着 `ProfileScreen`（二十来个早返回分支）与三个二级页。
 * 按页面一层层传参会把"密度"写进十几个函数签名 —— 项目里为 `onComposerFocusChange`
 * 这么干过一次，后果是"只有那一个控件生效"。
 *
 * 用 `staticCompositionLocalOf`：这个值只在用户改「列表密度」时变一次，
 * 而改一次就该让整页重新量一遍 —— 正是 static 版"变了就作废整棵子树"的语义。
 *
 * ## ⚠️ 默认值必须与「标准」档一致
 *
 * 14dp = [TabAppearance.profileRowVerticalPadding] 在档位 1（标准）下的值。
 * 外层没人 provide 时（例如快照测试单独渲某一行）应当表现为"标准"，
 * 而不是一个谁也没选过的第三个数。
 */
val LocalProfileRowPadding = staticCompositionLocalOf { 14.dp }

/**
 * 各 tab 的外观设置。
 *
 * ## 范围
 *
 * 只放**某一 tab 独有**的外观项。全局项（配色主题 / 深色模式 / 皮肤风格 / 界面风格）
 * 不在这里 —— 见 `AppearanceCategory` 的说明。
 *
 * ## 为什么是标量配置而不用 Room
 *
 * 这些是纯标量、读写频繁、无关联查询需求。放 SharedPreferences 就不必为它们升
 * 数据库版本、也不用写迁移 —— 项目里主题类配置一直这么存（见 `AppContainer` 的
 * `setBorderStyle` 等）。
 *
 * ## 一次全部载入内存 + StateFlow
 *
 * 读值全部从内存里的 StateFlow 拿，**不每次读 prefs**。理由是 Compose 重组时
 * 若在组合函数里读 prefs（getter 不参与快照系统），**改了不会触发重组** ——
 * 这个坑在会话列表的 `currentConversationId` 与底栏样式上各踩过一次。
 */
class TabAppearanceStore(context: Context) {

    private val prefs = context.getSharedPreferences("pocket_xiaoli_prefs", Context.MODE_PRIVATE)

    private val _state = MutableStateFlow(readAll())
    val state: StateFlow<TabAppearance> = _state.asStateFlow()

    val current: TabAppearance get() = _state.value

    private fun readAll() = TabAppearance(
        assistantFontScale = prefs.getInt(KEY_AI_FONT_SCALE, STANDARD_SCALE),
        assistantShowReasoning = prefs.getBoolean(KEY_AI_REASONING, true),
        assistantMascotShape = prefs.getString(KEY_AI_MASCOT_SHAPE, MascotShapes.defaultName).orEmpty(),
        assistantMascotColor = prefs.getString(KEY_AI_MASCOT_COLOR, MascotPalette.DEFAULT).orEmpty(),
        servicesShowSectionTitle = prefs.getBoolean(KEY_SVC_SECTION_TITLE, true),
        profileDensity = prefs.getInt(KEY_PROFILE_DENSITY, STANDARD_SCALE),
        profileShowFooter = prefs.getBoolean(KEY_PROFILE_FOOTER, true),
    )

    /** 统一的写入入口：写 prefs 并同步内存态，调用方不必关心两者一致性。 */
    private inline fun update(block: (android.content.SharedPreferences.Editor) -> Unit, next: (TabAppearance) -> TabAppearance) {
        prefs.edit().also(block).apply()
        _state.value = next(_state.value)
    }

    /** 回答字号档位：0 小 / 1 标准 / 2 大。用档位而不是任意 sp —— 少一个滑块，也少一堆无效值。 */
    fun setAssistantFontScale(value: Int) = update({ it.putInt(KEY_AI_FONT_SCALE, value) }) {
        it.copy(assistantFontScale = value)
    }

    fun setAssistantShowReasoning(value: Boolean) = update({ it.putBoolean(KEY_AI_REASONING, value) }) {
        it.copy(assistantShowReasoning = value)
    }

    /** 鲤鱼的形象：形状名（见 [MascotShapes]）。存**名字**而不是索引 —— 见 [MascotLook] 的说明。 */
    fun setAssistantMascotShape(value: String) = update({ it.putString(KEY_AI_MASCOT_SHAPE, value) }) {
        it.copy(assistantMascotShape = value)
    }

    /** 鲤鱼的形象：本色（`#RRGGBB`）。非法值由 [parseMascotColor] 兜底，写入侧不校验。 */
    fun setAssistantMascotColor(value: String) = update({ it.putString(KEY_AI_MASCOT_COLOR, value) }) {
        it.copy(assistantMascotColor = value)
    }

    fun setServicesShowSectionTitle(value: Boolean) = update({ it.putBoolean(KEY_SVC_SECTION_TITLE, value) }) {
        it.copy(servicesShowSectionTitle = value)
    }

    /** 我的页列表密度：0 紧凑 / 1 标准 / 2 宽松。 */
    fun setProfileDensity(value: Int) = update({ it.putInt(KEY_PROFILE_DENSITY, value) }) {
        it.copy(profileDensity = value)
    }

    fun setProfileShowFooter(value: Boolean) = update({ it.putBoolean(KEY_PROFILE_FOOTER, value) }) {
        it.copy(profileShowFooter = value)
    }

    private companion object {
        /**
         * 「标准」档的索引。**两处默认值都必须用它**。
         *
         * ⚠️ 2026-10 修：`assistantFontScale` 原先默认 `0`（= 小，0.9×）。那个值在当时**没有消费者**
         * 所以看不出问题；一旦把倍率接上（见 `AssistantScreen` 的 Density 缩放），
         * 默认 0 就会让**所有没碰过设置的用户**的回答字号凭空缩 10% ——
         * 正好违反本文件 KDoc 写的"默认值取现状、老用户零感知"。
         */
        const val STANDARD_SCALE = 1

        const val KEY_AI_FONT_SCALE = "tab_ai_font_scale"
        const val KEY_AI_REASONING = "tab_ai_reasoning"
        const val KEY_AI_MASCOT_SHAPE = "tab_ai_mascot_shape"
        const val KEY_AI_MASCOT_COLOR = "tab_ai_mascot_color"
        const val KEY_SVC_SECTION_TITLE = "tab_services_section_title"
        const val KEY_PROFILE_DENSITY = "tab_profile_density"
        const val KEY_PROFILE_FOOTER = "tab_profile_footer"
    }
}

/**
 * 各 tab 外观设置的快照。
 *
 * 默认值刻意都取"现状"——即用户不碰任何设置时，看到的就是改动前的样子。
 * 这样这个功能对老用户是**零感知**的，不会因为升级突然变样。
 *
 * ⚠️ 这里**没有**首页的显隐/排序项：那套已由 `HomeModule` + `HomeLayout` +
 * `CustomHomeScreen` 实现，再放一份会变成两套并存、彼此冲突的机制。
 * 外观中心的「首页」分类只负责把用户送到那个既有页面。
 */
data class TabAppearance(
    val assistantFontScale: Int = 1,
    val assistantShowReasoning: Boolean = true,
    val assistantMascotShape: String = MascotShapes.defaultName,
    val assistantMascotColor: String = MascotPalette.DEFAULT,
    val servicesShowSectionTitle: Boolean = true,
    val profileDensity: Int = 1,
    val profileShowFooter: Boolean = true,
) {
    /**
     * 鲤鱼回答字号的倍率。索引 0/1/2 → 0.9 / 1.0 / 1.15。
     *
     * ⚠️ **消费者：`AssistantScreen`**（把消息轮包在 `Density(fontScale = 系统 × 本值)` 里）。
     * 2026-10 之前这个字段**没有任何消费者** —— 「外观 → 鲤鱼 → 回答字号」改了不生效，
     * 用户在设置里怎么调都"还是那么大"。这类"声明了没接线"是 `AppearanceGuide.md`
     * 专门警告过的坑：**加外观字段必须同时确认消费者**。
     */
    val assistantFontMultiplier: Float
        get() = when (assistantFontScale) {
            0 -> 0.9f
            2 -> 1.15f
            else -> 1.0f
        }

    /**
     * 我的页列表的垂直内边距（dp），随密度档位变化。
     *
     * ⚠️ **消费者：`LocalProfileRowPadding`**（见本文件顶部）→ `ChecklistRow` / `DangerRow` /
     * `SettingsMenuItem` / `SettingsGroupCard`。2026-10 之前它同样**没有任何消费者**。
     */
    val profileRowVerticalPadding: Int
        get() = when (profileDensity) {
            0 -> 10
            2 -> 18
            else -> 14
        }

    /**
     * 当前形象，供 `LocalMascotLook` 直接 provide。
     *
     * ⚠️ 两个字符串是**不可信输入**（可能来自更老的版本或手改的 prefs），
     * 但这里**不做校验**：[MascotLook.shape] 与 [MascotLook.color] 各自兜底，
     * 在读取边界归零的好处是"渲染路径上没有分支" —— 也避免了校验与兜底两处规则漂移。
     */
    val assistantMascotLook: MascotLook
        get() = MascotLook(assistantMascotShape, assistantMascotColor)
}
