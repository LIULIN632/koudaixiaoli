package com.pocket.xiaoli.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * 界面风格（皮肤）标识。
 *
 * ## 为什么只有两套、且只切换"形状"维度
 *
 * 本项目的"皮肤"与"配色主题"是两个正交概念，刻意分开：
 * - **配色主题**（现有 8 套 × 明暗 = 16 种）→ 决定**颜色**，由 [PocketXiaoliColors] 承载
 * - **界面风格**（此处 2 套）→ 决定**形状与密度**（圆角 / 间距 / 卡片观感）
 *
 * 这样用户可以自由组合（"马卡龙粉 + Modern 紧凑"是合法组合），
 * 而不是被迫在"整套 UI"之间二选一 —— 后者的选择空间其实更小。
 *
 * 字号层级**不随皮肤切换**：两套风格都需要"标题大、正文中、说明小"的清晰层级，
 * 做成两套只会让维护成本翻倍而没有实际收益。
 */
 enum class SkinStyle(val key: String, val label: String, val description: String) {
     /**
      * 极简：紧凑排版 + 小圆角 + 无阴影。
      *
      * 观感目标：像系统设置页 —— 靠留白与底色差分隔，不靠装饰。
      */
     MINIMAL("minimal", "极简", "紧凑 · 小圆角 · 灰底白卡"),

     /**
      * 鲜活：宽松排版 + 大圆角 + 弥散阴影 + 彩色标签。
      *
      * 观感目标：卡片"浮"在页面上，颜色承担分类语义。
      */
     VIVID("vivid", "鲜活", "宽松 · 大圆角 · 弥散阴影");

     companion object {
         /**
          * 解析持久化的 key。
          *
          * ⚠️ **必须兼容两个历史 key**：`skin_style` 这个 SharedPreferences 键
          * 改造前存的是 `"classic"` / `"modern"`。老用户升级后若按新 key 查表
          * 会查不到而回退默认 —— 观感突变且无从解释。
          *
          * 迁移按"观感最接近"原则：
          * - 旧 `classic`（松间距 + 大圆角）→ [VIVID]
          * - 旧 `modern`（紧凑间距 + 规整圆角）→ [MINIMAL]
          */
         fun fromKey(key: String?): SkinStyle = when (key) {
             MINIMAL.key -> MINIMAL
             VIVID.key -> VIVID
             "classic" -> VIVID
             "modern" -> MINIMAL
             else -> VIVID
         }
     }
 }

/**
 * 随皮肤切换的**形状令牌**（圆角 + 间距）。
 *
 * 只放"确实因皮肤而异"的维度：
 * - 圆角：Classic 大圆角是"马卡龙 Soft UI"的核心特征；Modern 用规整圆角更接近原生 MIUI
 * - 间距：Classic 松（卡片 16dp 内距 / 24dp 模块间距）；Modern 紧凑（12dp / 20dp）
 *
 * 字号与颜色不在这里 —— 见 [SkinStyle] 的说明。
 */
@Immutable
data class SkinTokens(
    // ── 圆角 ──
    /** 极小元素：进度条、内嵌细条 */
    val radiusMicro: Dp,
    /** 小元素：标签、工具条目 */
    val radiusChip: Dp,
    /** 标准卡片 */
    val radiusCard: Dp,
    /** 大卡片 / 主视觉卡片 */
    val radiusLarge: Dp,
    /** 弹窗 / 模态 */
    val radiusDialog: Dp,

    // ── 间距 ──
    /**
     * 间距刻度（Classic）：2 / 4 / 8 / 12 / 16 / 20 / 24 / 32
     *
     * ⚠️ 兼容性铁律：`spaceXs..spaceXxl` 的数值**不可更改**。
     * 迁移期已把它们写进 700+ 处调用点（初版映射为 4/8/12/16/20/24），
     * 改动其中任何一个都会导致全 App 间距跳变 —— 那是破坏，不是整理。
     *
     * 新刻度只能**在两端追加**（见 [spaceMicro] / [spaceTiny] / [spaceHuge]）。
     *
     * 刻度取自真实设计统计（6dp 用过 99 次、14dp 52 次、2dp 41 次），
     * 而非先验规定 —— 设计系统的作用是描述并约束既有设计。
     */
    /** 2dp：紧贴（图标与角标、密集列表的行内间隔） */
    val spaceMicro: Dp,
    /** 3dp：微调（极少用） */
    val spaceTiny: Dp,
    /** 4dp：紧邻元素（图标与文字） */
    val spaceXs: Dp,
    /** 6dp：小间距（标签与文本、列表项内部） */
    val spaceSmall: Dp,
    /** 8dp：同组元素 */
    val spaceSm: Dp,
    /** 12dp：卡片之间 / 列表项之间 */
    val spaceMd: Dp,
    /** 14dp：卡片内边距（紧凑） */
    val spaceCompact: Dp,
    /** 16dp：屏幕边距 / 卡片内边距（常规） */
    val spaceLg: Dp,
    /** 18dp：卡片内边距（略宽） */
    val spaceWide: Dp,
    /** 20dp：大卡片内边距 */
    val spaceXl: Dp,
    /** 24dp：页面大模块之间 */
    val spaceXxl: Dp,
    /** 32dp：超大留白（空态、引导页） */
    val spaceHuge: Dp,
    // ── 表面色（Minimal 灰底白卡 / Vivid 纯白 + 阴影分层）──
    /**
     * 页面背景色的**目标值**（不是最终值）。
     *
     * ## 为什么放进"皮肤"而不是"配色主题"
     *
     * `PocketXiaoliColors.surfaceBase` 是**配色**的一部分（8 套主题各有底色）；
     * 这里表达的是**风格差异**：Minimal 靠"灰底 + 白卡"的对比分层，
     * Vivid 两者同色、靠阴影分层。同一配色下两种做法都成立 → 属皮肤维度。
     *
     * ## 为什么不直接替换配色主题的底色
     *
     * 直接替换会丢掉色相 —— 选了「马卡龙粉」的用户页面底色会从 `#FEF8F9`
     * 变成中性灰。所以最终值 = `lerp(配色主题底色, 本值, surfaceShift)`，
     * 见 [applySurfaces]。**本字段单独存在不产生任何效果**。
     */
    val backgroundColorLight: Color,
    val backgroundColorDark: Color,

    /** 卡片底色 */
    val cardColorLight: Color,
    val cardColorDark: Color,

    /**
     * 皮肤表面色相对**配色主题**的推移强度（0f..1f）。
     *
     * - `0f`：完全沿用配色主题的表面色（差异只由圆角 / 阴影表达）
     * - `1f`：完全改用皮肤的表面色（配色主题的底色被覆盖，色相丢失）
     *
     * 取中间值是刻意的：让"配色"与"皮肤"两条轴都保留一部分话语权。
     *
     * - [MinimalSkin] `0.85f` —— 必须**真的把底色压暗成灰**。
     *   取更小的值时 `#F5F5F7` 这类目标与配色主题的 `#F8FAFC` 只差 3 个色阶，
     *   "灰底白卡"在无阴影方案下根本读不出来。
     * - [VividSkin] `0.35f` —— 只把底色拉向白，保持"底色 ≈ 卡色、靠阴影分层"。
     *
     * 推导逻辑见 [applySurfaces]。这个字段是 2026-10 修"换皮肤没变化"时新增的：
     * 此前 [backgroundColorLight] / [cardColorLight] 只是被声明，**没有任何消费者**。
     */
    val surfaceShift: Float,

    // ── 装饰（边框 + 阴影）──
    /** 卡片描边宽度。`0.dp` = 无边框 */
    val borderWidth: Dp,

    /** 描边色（浅色 / 深色态） */
    val borderColorLight: Color,
    val borderColorDark: Color,

    /**
     * 阴影响度半径。
     *
     * - `0.dp` = 无阴影（Minimal）
     * - `>0.dp` = 弥散阴影（Vivid）
     *
     * 判定"要不要画阴影"的条件是
     * `shadowBlur > 0.dp || shadowColor != Color.Transparent`，
     * 二者任一成立即绘制 —— 这样"零模糊硬阴影"也能表达。
     */
    val shadowBlur: Dp,

    /** 阴影偏移（Vivid 用 (0, 4) 做自然投影） */
    val shadowOffsetX: Dp,
    val shadowOffsetY: Dp,

    /** 阴影颜色（浅色 / 深色态）。深色态必须换色，否则黑投影在深底上看不见。 */
    val shadowColorLight: Color,
    val shadowColorDark: Color,

    // ── 图标 ──
    /**
     * 图标取色的**上限**（单色 / 彩色）。
     *
     * ## 语义是"上限"，不是"开关"
     *
     * 实际是否彩色由 [resolveIcons] 合成：
     * `colorful = (iconMode == COLORFUL) && (appearance == RAINBOW)`。
     * 即**皮肤定上限、用户定偏好**：
     * - [MinimalSkin] 声明 `MONOCHROME` → 用户无论选彩虹还是纯净，图标都不上色。
     *   这是"极简 = 单色线性图标"这条风格承诺的保证。
     * - [VividSkin] 声明 `COLORFUL` → 尊重用户在「用色强度」里的选择。
     *
     * ## 为什么必须合成，而不能让皮肤独立表态
     *
     * `AppearanceMode`（彩虹 / 纯净）**已经**在决定"图标是否按语义上色" ——
     * 它的现成消费者有 8 处（`ServiceAccent` 的取色回退、首页主视觉卡配色等）。
     * 若皮肤再独立表态一次，就会出现"鲜活 + 纯净"下两个开关互相矛盾、
     * 谁也说不清该听谁的。合成把话语权分清楚：皮肤只否决，不做加法。
     */
    val iconMode: IconMode,

    /**
     * 图标**线形 / 面性**（`Icons.Outlined` / `Icons.Filled`）。
     *
     * ## 与 [iconMode] 正交，**不参与合成**
     *
     * "线性 / 面性"是**风格特征**（和圆角、阴影同类），不是用户偏好 ——
     * 所以它恒定跟随皮肤，不受「用色强度」影响。
     *
     * 后果是「鲜活 + 纯净」会得到**面性但单色**的图标。这是刻意的：
     * 用户改"用色强度"应该只改**颜色**，不该让图标的**几何形状**也跳变 ——
     * 后者会让一个"颜色偏好"旋钮产生超出预期的反馈面。
     */
    val iconStyle: IconStyle,

    /**
     * 是否启用彩色标签（Vivid 有 / Minimal 无）。
     *
     * 同样受 [iconMode] 的上限约束；最终值见 [IconPresentation.colorfulTags]。
     *
     * ## 与 [iconMode] 的取值冗余是刻意的
     *
     * 以 [MinimalSkin] / [VividSkin] 这两套数据，本字段与 [iconMode] **完全相关**
     * （前者两个都 false、后者两个都 true），所以合成结果永远相同。
     * 保留两个独立字段，是为了将来可能出现"彩色图标 + 中性角标"的第三套皮肤 ——
     * 那时它们才会真正分岔。`SkinThemeContractTest` 目前各自守着它们的契约。
     *
     * 若确认不会有那种皮肤，可以把两者合并成单个 `colorful` 布尔。
     */
    val colorfulTags: Boolean,
) {
    /** 屏幕左右边距 */
    val screenH: Dp get() = spaceLg

    /** 卡片内边距（常规） */
    val cardPadding: Dp get() = spaceLg

    /** 卡片内边距（大卡片 / 主视觉卡片） */
    val cardPaddingLarge: Dp get() = spaceXl

    /** 卡片之间的间距 */
    val cardGap: Dp get() = spaceMd

    /** 页面大模块之间的间距 */
    val sectionGap: Dp get() = spaceXxl

 }

/**
 * Minimal（极简）：紧凑间距 + 小圆角 + 无阴影 + **灰底白卡**。
 *
 * ## 底色为什么必须是 #E9E9EE 而不是 #F5F5F7
 *
 * 极简方案不画阴影，分层**全靠**"底色比卡色暗一档"。
 * 而 `#F5F5F7` 与配色主题的页底（如 `#F8FAFC`）只差 3 个色阶、
 * 亮度差仅 **0.086** —— 肉眼读不出"灰底"，卡片看着仍像贴在白纸上，
 * "灰底白卡"就成了空话（这正是"换了皮肤感觉没变化"的来源之一）。
 *
 * 压到 `#E9E9EE` 后亮度差 ≥ 0.18，分层才真正成立。
 * 该门限有单测守着（`SkinThemeContractTest`），别随手调回去。
 *
 * ## 间距刻度
 *
 * `spaceXs..spaceXxl` 是**全 App 共用的间距刻度**，已写进 700+ 处调用点
 * （`spaceLg` 133 处 / `spaceMd` 153 处）—— 动一个值等于全 App 排版跳变，
 * 改完必须目视验收。本皮肤比 [VividSkin] 整体紧凑一档，
 * 这是"皮肤"维度的正常差异，不是为了微调某个页面。
 */
 val MinimalSkin = SkinTokens(
    radiusMicro = 3.dp,
    radiusChip = 6.dp,
    radiusCard = 12.dp,
    radiusLarge = 16.dp,
    radiusDialog = 20.dp,
    spaceMicro = 2.dp,
    spaceTiny = 3.dp,
    spaceXs = 4.dp,
    spaceSmall = 6.dp,
    spaceSm = 8.dp,
    spaceMd = 10.dp,
    spaceCompact = 12.dp,
    spaceLg = 14.dp,
    spaceWide = 16.dp,
    spaceXl = 18.dp,
    spaceXxl = 20.dp,
    spaceHuge = 28.dp,
    // ── 表面：灰底 + 白卡 ──
    backgroundColorLight = Color(0xFFE9E9EE),
    backgroundColorDark = Color(0xFF121212),
    cardColorLight = Color(0xFFFFFFFF),
    cardColorDark = Color(0xFF1E1E1E),
    surfaceShift = 0.85f,
    // ── 装饰：无边框、无阴影 ──
    borderWidth = 0.dp,
    borderColorLight = Color(0xFFE5E5EA),
    borderColorDark = Color(0xFF2B2B2B),
    shadowBlur = 0.dp,
    shadowOffsetX = 0.dp,
    shadowOffsetY = 0.dp,
    shadowColorLight = Color.Transparent,
    shadowColorDark = Color.Transparent,
    // ── 图标：单色线性、无彩标 ──
    iconMode = IconMode.MONOCHROME,
    iconStyle = IconStyle.OUTLINED,
    colorfulTags = false,
 )
/**
 * Vivid（鲜活）：宽松间距 + 大圆角 + **弥散阴影** + 表面近同色。
 *
 * ## 与历史 `Classic` 的关系
 *
 * 圆角与历史 `Classic`（radiusCard 20dp / radiusLarge 28dp）**逐值一致** ——
 * 这是刻意的：老用户存档 `skin_style="classic"` 被 [SkinStyle.fromKey]
 * 迁移到本皮肤，圆角对齐才不会让升级变成"观感突变"。
 * 差异只在间距（整体 +2dp）与新增的阴影 / 表面色。
 *
 * 这个"刻意一致"的另一面：**本皮肤单独看与改造前几乎没有区别**。
 * 两套皮肤之间的可见差异要到 [MinimalSkin] 那边才读得出来 ——
 * 见 `docs/AppearanceGuide.md` 的说明。
 */
 val VividSkin = SkinTokens(
    radiusMicro = 4.dp,
    radiusChip = 12.dp,
    radiusCard = 20.dp,
    radiusLarge = 28.dp,
    radiusDialog = 32.dp,
    spaceMicro = 2.dp,
    spaceTiny = 3.dp,
    spaceXs = 4.dp,
    spaceSmall = 6.dp,
    spaceSm = 8.dp,
    spaceMd = 14.dp,
    spaceCompact = 16.dp,
    spaceLg = 18.dp,
    spaceWide = 20.dp,
    spaceXl = 22.dp,
    spaceXxl = 26.dp,
    spaceHuge = 36.dp,
    // ── 表面：纯白背景 + 白卡（靠阴影分层）──
    backgroundColorLight = Color(0xFFFFFFFF),
    backgroundColorDark = Color(0xFF121212),
    cardColorLight = Color(0xFFFFFFFF),
    cardColorDark = Color(0xFF1E1E1E),
    surfaceShift = 0.35f,
    // ── 装饰：无边框、弥散阴影 ──
    borderWidth = 0.dp,
    borderColorLight = Color(0xFFEFEFEF),
    borderColorDark = Color(0xFF2B2B2B),
    shadowBlur = 16.dp,
    shadowOffsetX = 0.dp,
    shadowOffsetY = 4.dp,
    shadowColorLight = Color(0x1A000000),
    shadowColorDark = Color(0x66000000),
    // ── 图标：彩色面性、有彩标 ──
    iconMode = IconMode.COLORFUL,
    iconStyle = IconStyle.FILLED,
    colorfulTags = true,
 )

/**
 * 皮肤标识 → 令牌实例的**唯一映射**。
 *
 * ## 为什么要有这个函数
 *
 * 改造前这个 `when` 内联在 `PocketXiaoliTheme` 里（局部 `val`），于是：
 * - **无法单测** —— "每套 `SkinStyle` 都有对应的令牌实例"这条不变量没人守，
 *   加了枚举项却忘了补实例，要到运行时换皮肤才发现观感没变；
 * - **无法复用** —— 设置页的皮肤预览要渲染"另一套皮肤"的令牌，拿不到这个映射
 *   就只能复制一份 `when`，于是又出现两处真源。
 *
 * 提成顶层函数后两件事都解决。因为 `when` 是穷尽的，新增 `SkinStyle` 条目时
 * 编译器会直接报错，不会静默漏掉。
 */
fun SkinStyle.tokens(): SkinTokens = when (this) {
    SkinStyle.MINIMAL -> MinimalSkin
    SkinStyle.VIVID -> VividSkin
}

/**
 * 当前皮肤令牌 —— 全项目**唯一**的间距/圆角来源。
 *
 * ## 怎么用
 *
 * ```kotlin
 * @Composable
 * fun MyCard() {
 *     val skin = LocalSkin.current          // ← 放在函数体开头
 *     Column(Modifier.padding(skin.cardPadding)) {
 *         Spacer(Modifier.height(skin.spaceSm))
 *     }
 * }
 * ```
 *
 * `val skin = LocalSkin.current` 必须写在**任何早返回分支之前** ——
 * 否则早返回路径拿不到令牌（历史上 `NextCourseCard` 踩过）。
 *
 * 若函数签名里已经有名为 `skin` 的参数（历史上是「外观方案」的 key），
 * 直接用 `LocalSkin.current.xxx` 内联，**不要**声明同名局部变量：会遮蔽参数、编译失败。
 *
 * ## 历史
 *
 * 曾经并存四套令牌模块（`Spacing`/`Radius`、`PocketXiaoliDimens`、`MotionDuration`、
 * 本文件），其中前三套**零引用**，2026-09 清理时删除。
 * 现在间距与圆角只认本文件；字号见 `PocketTypography`；颜色见 `PocketXiaoliColors`。
 */
 val LocalSkin = staticCompositionLocalOf { VividSkin }

 /** 当前皮肤风格（用于设置页显示与判断） */
 val LocalSkinStyle = staticCompositionLocalOf { SkinStyle.VIVID }

 /**
  * 图标呈现模式 —— 由皮肤决定，不由配色主题决定。
  *
  * ⚠️ **不要直接读它** —— 这里只是"上限"。真实是否彩色还取决于用户的
  * 「用色强度」（`AppearanceMode`），合成规则见 [resolveIcons]。
  * 需要判断图标怎么画时，请统一用 `currentIconPresentation()`。
  *
  * ⚠️ **语义契约**：消费方读这个值后，行为必须是：
  * - [MONOCHROME]：所有图标用同一个前景色，**只靠形状区分**
  * - [COLORFUL]：图标按各自语义取色，颜色是**可读信息的一部分**
  *
  * 反例（不要这样做）：把 [MONOCHROME] 实现成"仍然彩色但降饱和" ——
  * 那就失去了"单色系"的克制感，两个模式看起来没区别。
  */
 enum class IconMode {
    /** 单色：所有图标同一前景色 */
    MONOCHROME,

    /** 彩色：图标按语义类别取色 */
    COLORFUL,
 }

/**
 * 把皮肤的表面基调套用到配色主题的颜色令牌上 —— **纯函数，便于单测**。
 *
 * ## 这是"皮肤真的生效"的唯一桥接点
 *
 * `PocketXiaoliTheme` 调用它得到最终令牌，然后同一条链路喂给三处：
 * 1. `LocalPocketXiaoliColors` → 266 处 `colors.surfaceBase / surfaceCard / surfaceAlt`
 * 2. `MiuixColorMapping.toMiuixColors` → 全部 MIUIX 组件的 background / surface / surfaceVariant
 * 3. `MaterialTheme.colorScheme` 的 `background / surface / surfaceVariant`
 *    → `MainActivity` 的根 `Surface()` 与 `Scaffold` 默认底色
 *
 * 所以**改这里等于改全 App 的页面底色**，一处改动，不需要动任何页面文件。
 *
 * ## 为什么是 lerp 而不是直接替换
 *
 * 直接替换会废掉配色维度：选了「马卡龙粉」的用户，页面底色会从 `#FEF8F9`
 * 一步变成中性灰 —— 等于把"配色主题"这条轴砍掉一半。
 * [surfaceShift] 让两条轴都保留话语权：皮肤决定"底色比卡色暗多少"，
 * 配色主题决定"这层底色是什么色相"。
 *
 * @param theme 已叠加 accent 覆盖的配色主题令牌。
 *   ⚠️ 调用方必须在 accent 覆盖**之后**调用，否则自定义强调色会被覆盖回去。
 * @param isDark 决定取 [backgroundColorDark] / [cardColorDark] 还是浅色态目标。
 */
fun SkinTokens.applySurfaces(theme: PocketXiaoliColors, isDark: Boolean): PocketXiaoliColors {
    val baseTarget = if (isDark) backgroundColorDark else backgroundColorLight
    val cardTarget = if (isDark) cardColorDark else cardColorLight
    // 次级背景没有独立的皮肤目标值：取"卡色与底色的中间"正是它原有的语义
    // （两套配色里 surfaceAlt 都落在 surfaceCard 与 surfaceBase 之间）。
    val altTarget = lerp(cardTarget, baseTarget, 0.5f)
    val strength = surfaceShift.coerceIn(0f, 1f)
    fun toward(from: Color, to: Color) = lerp(from, to, strength)
    return theme.copy(
        surfaceBase = toward(theme.surfaceBase, baseTarget),
        surfaceCard = toward(theme.surfaceCard, cardTarget),
        surfaceAlt = toward(theme.surfaceAlt, altTarget),
    )
}
