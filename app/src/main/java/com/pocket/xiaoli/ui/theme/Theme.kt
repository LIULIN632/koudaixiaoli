package com.pocket.xiaoli.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import top.yukonga.miuix.kmp.theme.MiuixTheme
import com.pocket.xiaoli.ui.theme.LocalSkin

 // ═══ 配色真源（原「配色主题枚举」，2026-10 已删）═══
 //
 // 这里原本有一个 **8 条目的 `ThemeType` 枚举**、一张 16 实例的色板表、以及
 // `LocalThemeType` 这个 CompositionLocal —— **全部移除**。全项目现在只有一套配色。
 //
 // ## 为什么收敛
 //
 // 8 套里 7 套**从未被任何 golden 基线覆盖**（68 张基线全在「口袋小鲤」下拍摄），
 // 只靠 `ThemeContrastTest` 的数值守着。而实测 `textSecondary` 在 8 套之间差到
 // **1.19:1 ↔ 4.85:1** —— 同一句"次要说明"换个配色就换一种深浅；更糟的是三级层次
 // 在部分配色下**直接塌掉**（抹茶绿/樱花粉/紫藤紫的 `primary↔secondary` 只有
 // **1.60:1**，肉眼分不出主次）。收敛后这种漂移**在结构上不可能再发生**。
 //
 // ⚠️ 删掉它不是"清理垃圾"，是**产品决定**：主动选过其它 7 套的用户被切到「口袋小鲤」。
 //
 // ## 遗留偏好键：刻意不清理
 //
 // - `color_theme` —— 原 `ThemeType` 的持久化 key，**不再被读取**。
 // - `app_skin`   —— 更早的皮肤键，随 `AppearanceSkinCatalog` 一并废弃。
 //
 // 两者都**留在老用户存档里不动**：删掉读它们的代码就够了，动存档没有必要
 //（与 `CLAUDE.md` 对 `nickname` 残留键的处置同策）。
 // 改配色请改 `DesignTokens.kt` 的 `PocketSoftLight` / `PocketSoftDark` —— 那是唯一真源。
val LocalDarkMode = staticCompositionLocalOf { false }

// ═══ Material3 颜色方案 ═══

private val PocketSoftLightScheme = lightColorScheme(
    primary = Color(0xFF4D6BFE),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFEEF2FF),
    onPrimaryContainer = Color(0xFF1E293B),
    secondary = Color(0xFF64748B),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFF1F5F9),
    onSecondaryContainer = Color(0xFF1E293B),
    tertiary = Color(0xFF7C3AED),
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFFF3E8FF),
    onTertiaryContainer = Color(0xFF2E1065),
    surface = Color(0xFFFFFFFF),
    onSurface = Color(0xFF1F2937),
    background = Color(0xFFF7F8FA),
    onBackground = Color(0xFF1F2937),
    surfaceVariant = Color(0xFFF1F3F5),
    onSurfaceVariant = Color(0xFF64748B),
    outline = Color(0xFFCFD4DC),
    outlineVariant = Color(0xFFEEF0F3),
    error = Color(0xFFEF4444),
    onError = Color.White,
    errorContainer = Color(0xFFFEE2E2),
    onErrorContainer = Color(0xFF7F1D1D)
)
private val PocketSoftDarkScheme = darkColorScheme(
    primary = Color(0xFF8AA4FF),
    onPrimary = Color(0xFF0B1220),
    primaryContainer = Color(0xFF26324F),
    onPrimaryContainer = Color(0xFFDCE4FF),
    secondary = Color(0xFF9AA4B2),
    onSecondary = Color(0xFF0B1220),
    secondaryContainer = Color(0xFF2A2F38),
    onSecondaryContainer = Color(0xFFECEDEF),
    tertiary = Color(0xFFB99AFF),
    onTertiary = Color(0xFF160B2E),
    tertiaryContainer = Color(0xFF3B2A63),
    onTertiaryContainer = Color(0xFFF0E8FF),
    surface = Color(0xFF171A20),
    onSurface = Color(0xFFECEDEF),
    background = Color(0xFF0F1115),
    onBackground = Color(0xFFECEDEF),
    surfaceVariant = Color(0xFF24272E),
    onSurfaceVariant = Color(0xFFB4B8C0),
    outline = Color(0xFF30343B),
    outlineVariant = Color(0xFF24272E),
    error = Color(0xFFF87171),
    onError = Color(0xFF2B0B0B),
    errorContainer = Color(0xFF4A2020),
    onErrorContainer = Color(0xFFFEE2E2)
)

// ═══ 椒盐音乐同款无衬线极简字体 ═══
/**
 * Material3 的 typography 表 —— 2026-10 起 **15 个角色全部显式声明**。
 *
 * ## 原先只写 5 个，代价是一个"幽灵字号"
 *
 * 旧写法只列 5 个覆盖项，另外 10 个**静默继承 M3 出厂值** —— 从源码上看不出它们是几号字。
 * 后果不是"少写几行"，而是任意 `Text(...)` **既不传 `style` 也不传 `fontSize`** 时
 * 读 `LocalTextStyle` → `bodyLarge` → **16sp**，而 16sp **既不在 `PocketTypography` 的 7 档里、
 * 也不在原文件的 5 个覆盖里** —— 一个谁都没选过的字号在全 App 静默生效。
 *
 * ## ⚠️ 这一步刻意**不改任何像素**
 *
 * 下面 10 个 M3 角色填的就是它们**当时实际生效的出厂值**；值由一个一次性探针从**库里打印出来**
 *（`TypographyDefaultsProbeTest`，取完值即删），不是凭记忆写的
 *（实测就发现记忆有偏差：`displayLarge` 的 letterSpacing 是 **-0.2**、`labelMedium` 是 **0.5**）。
 * 钉住这张表的是 `AppTypographyTest` —— 15 个角色逐个断言，改字号必须先改它。
 *
 * ## 下一步（Phase C 的真正迁移，**必须分批**）
 *
 * 实测：只把 `bodyLarge` 从 16sp 改成 `PocketTypography.body`（14sp），
 * **70 张基线里有 22 张失败** —— 幽灵 16sp 在 1/3 的界面里是**承重**的。
 * 所以它是一次真实的视觉迁移，要按区域分批（鲤鱼 / 我的 / 绑定 / 第二课堂 / 服务卡 / 课表）
 * 逐批重录 + 逐批目视复核，**不能一次性改**。
 *
 * ⚠️ 上表 5 个 app 覆盖项**刻意不设 `letterSpacing`**（= `Unspecified`，实际按 0 渲染），
 * 这与 M3 出厂值不同（如 `bodyMedium` 出厂是 0.2sp）。**给它补上就会改像素**，
 * 那是 Phase C 的决定，不是这里的顺手事。
 */
internal val AppTypography = Typography(
    // ── 本 App 自己的覆盖（沿用改造前的值，一个字没动）──
    displayLarge = TextStyle(
       fontSize = 32.sp, fontWeight = FontWeight.Bold, lineHeight = 40.sp
    ),
    headlineMedium = TextStyle(
       fontSize = 20.sp, fontWeight = FontWeight.SemiBold, lineHeight = 28.sp
    ),
    titleMedium = TextStyle(
       fontSize = 16.sp, fontWeight = FontWeight.Medium, lineHeight = 24.sp
    ),
    bodyMedium = TextStyle(
       fontSize = 14.sp, fontWeight = FontWeight.Normal, lineHeight = 20.sp
    ),
    labelMedium = TextStyle(
       fontSize = 12.sp, fontWeight = FontWeight.Medium, lineHeight = 16.sp
    ),
    // ── 以下 10 个 = M3 出厂值，显式写出来只为"可见"（值本身没动）──
    displayMedium = TextStyle(
       fontSize = 45.sp, fontWeight = FontWeight.Normal, lineHeight = 52.sp, letterSpacing = 0.0.sp
    ),
    displaySmall = TextStyle(
       fontSize = 36.sp, fontWeight = FontWeight.Normal, lineHeight = 44.sp, letterSpacing = 0.0.sp
    ),
    headlineLarge = TextStyle(
       fontSize = 32.sp, fontWeight = FontWeight.Normal, lineHeight = 40.sp, letterSpacing = 0.0.sp
    ),
    headlineSmall = TextStyle(
       fontSize = 24.sp, fontWeight = FontWeight.Normal, lineHeight = 32.sp, letterSpacing = 0.0.sp
    ),
    titleLarge = TextStyle(
       fontSize = 22.sp, fontWeight = FontWeight.Normal, lineHeight = 28.sp, letterSpacing = 0.0.sp
    ),
    titleSmall = TextStyle(
       fontSize = 14.sp, fontWeight = FontWeight.Medium, lineHeight = 20.sp, letterSpacing = 0.1.sp
    ),
    // ══════════════════════════════════════════════════════════════════
    // ⚠️⚠️ 幽灵字号就是它 —— 2026-10 起**正式接管为「全局继承默认文字样式」**
    // ══════════════════════════════════════════════════════════════════
    //
    // ## 它为什么是"全局默认"而不是"某档正文"
    //
    // `Text(fontSize = 11.sp)` 内部执行的是 `LocalTextStyle.current.merge(...)` ——
    // **它没设的属性一律从 `LocalTextStyle` 继承**，而 `LocalTextStyle` 就是本项。
    // 而本 App 的 `Text` 绝大多数只设 `fontSize`、不设 `lineHeight`/`letterSpacing`，
    // 于是**每一段文字（含 10sp 的小字）都在套用本项的 行高 与 字距**，与它自己的字号无关。
    //
    // 隔离实验证据（每次只改一个属性）：
    //   · 只把 lineHeight 24sp→22sp（fontSize 保持 16sp）→ **打掉 5 张基线**
    //   · 只把 letterSpacing 0.5sp→Unspecified        → 打掉课表格子课名那张
    //
    // ## 所以这里的目标值不是"挑个好看的正文号"，而是**定下全局文字默认**
    //
    // 改造前它顶着 M3 出厂值（16sp / 24sp 行高 / 0.5sp 字距），与 `PocketTypography`
    // 那 7 档**毫无关系** —— 这是"字体有的深有的浅、不统一"的一个直接根因：
    // 字号从 10sp 跨到 32sp，却共用同一个 24sp 行盒，小字上下空太多、大字又不够，
    // 视觉节奏必然不统一（项目里那批手写 `lineHeight` 就是这件事的补丁）。
    //
    // 现在取 `PocketTypography.body`（**14sp / 22sp / Normal / 无字距**）——
    // 也就是本项目自己那套档位里的正文档，并**刻意不保留 M3 的 0.5sp 字距**
    //（字距是拉丁文字的特性；中文加 0.5sp 只是把字撑松）。
    //
    // ⚠️ 改本项 = **一次全 App 的文字层次调整**，会同时影响 1/3 的基线。
    // 配套基线要**按区域分批逐张复核**。这正是 Phase C 的正题，不是顺手事。
    bodyLarge = PocketTypography.body,
    bodySmall = TextStyle(
       fontSize = 12.sp, fontWeight = FontWeight.Normal, lineHeight = 16.sp, letterSpacing = 0.4.sp
    ),
    labelLarge = TextStyle(
       fontSize = 14.sp, fontWeight = FontWeight.Medium, lineHeight = 20.sp, letterSpacing = 0.1.sp
    ),
    labelSmall = TextStyle(
       fontSize = 11.sp, fontWeight = FontWeight.Medium, lineHeight = 16.sp, letterSpacing = 0.5.sp
    )
)

/**
 * 由皮肤派生的 Material3 形状表。
 *
 * ## 为什么必须派生（2026-10 修）
 *
 * 原实现是**顶层 val**、写死 `20/28/22/32/36dp`，理由是"顶层 val 拿不到皮肤令牌"。
 * 后果是 `MaterialTheme.shapes` 完全不随皮肤切换 —— 而它有大量**隐式**读取方：
 * `MaterialTheme.shapes.X` 在项目里显式出现 **0 次**，但组件默认值全读它：
 *
 * | M3 角色 | 谁读它 | 旧写死值 |
 * |---|---|---|
 * | `extraSmall` | `TextField` 的容器形状 | 20dp |
 * | `medium` | **`Card` 的默认形状** | 22dp |
 * | `extraLarge` | `AlertDialog` | 36dp |
 *
 * 也就是说：项目里 77 处 `Card(` 中凡没显式传 `shape` 的，圆角**永远锁在 22dp**，
 * 换皮肤纹丝不动。改这一处，77 处卡片 + 所有输入框/对话框才真正跟随皮肤。
 *
 * ## 角色映射
 *
 * - `extraSmall` ← `radiusCard`：输入类控件与卡片同级（保持 Vivid 下 20dp 不变）
 * - `small` / `medium` ← `radiusCard`：其中 `medium` 是 M3 `Card` 的默认形状
 * - `large` ← `radiusLarge` · `extraLarge` ← `radiusDialog`
 *
 * 显式调用点为 0 → 改动没有调用点副作用，属纯收益。
 */
private fun skinShapes(skin: SkinTokens) = Shapes(
    extraSmall = RoundedCornerShape(skin.radiusCard),
    small = RoundedCornerShape(skin.radiusCard),
    medium = RoundedCornerShape(skin.radiusCard),
    large = RoundedCornerShape(skin.radiusLarge),
    extraLarge = RoundedCornerShape(skin.radiusDialog)
)

// ═══ 主题入口 ═══
@Composable
fun PocketXiaoliTheme(
    appearanceMode: AppearanceMode = AppearanceMode.RAINBOW,
    skinStyle: SkinStyle = SkinStyle.VIVID,
    darkTheme: Boolean = isSystemInDarkTheme(),
    customAccent: Color? = null,
    content: @Composable () -> Unit
) {

    // 配色只剩一套，不必再查表。
    val lightScheme = PocketSoftLightScheme
    val darkScheme = PocketSoftDarkScheme

    val colorScheme = if (darkTheme) darkScheme else lightScheme

    // 收敛后只剩一套配色：既没有"纯净模式强制某套"可言，也没有"按主题查表"可言，
    // 于是原来那个 17 分支的 `when` 塌成亮暗二选一。
    val basePocketXiaoliColors = if (darkTheme) PocketSoftDark else PocketSoftLight

    val accent = customAccent
    val finalPocketColors = if (accent != null) {
       basePocketXiaoliColors.copy(
           accentMain = accent,
           surfaceHighlight = accent.copy(alpha = 0.14f),
       )
    } else {
       basePocketXiaoliColors
    }
    // 注：总进度金（`progressMaster`）**不再按明暗切换** —— 它是金属金图形的暗端，
    // 明暗同值（见 `DesignTokens.progressMaster` 的说明）。此前那里有一处
    // `progressMasterFor(darkTheme)` 的桥接，随着"金只做图形"的结论一起去掉了。
    val finalColorScheme = if (accent != null) {
       // ⚠️ 不要在这里另写判据：原先是 `accent.luminance() > 0.55f` 的阈值写法，
       // 而它在深色强调色 `#8AA4FF`（亮度 0.396）上会选白字 —— 实测只有 2.36:1。
       // 现在与 `onAccentColor()` 共用同一个"取对比度更高者"的内核，两处不会再分裂。
       val onAccent = onAccentFor(accent)
       colorScheme.copy(
           primary = accent,
           onPrimary = onAccent,
           primaryContainer = accent.copy(alpha = 0.18f),
           onPrimaryContainer = onAccent,
           secondary = accent,
           onSecondary = onAccent,
       )
    } else {
       colorScheme
    }

    // 皮肤令牌：决定形状、密度与**表面基调**（圆角 / 间距 / 底色 / 阴影）。
    // 色相仍归配色主题 —— 皮肤只推移"底色与卡色的关系"，见 SkinTokens.applySurfaces。
    // 查表在 `SkinStyle.tokens()` —— 提成顶层函数是为了让「每套皮肤都有实例」
    // 这条不变量可被单测，也让设置页的皮肤预览能渲染"另一套皮肤"的令牌
    //（否则预览只能复制一份 `when`，又变成两处真源）。
    val skinTokens = skinStyle.tokens()

    // ── 2026-10 修「换了皮肤感觉没变化」──
    // 此前 skin.backgroundColor / cardColor / shadow* 只是被**声明**，没有任何消费者：
    // 换皮肤的可见变量只剩 2dp 间距（人眼不可辨）。下面这行是唯一的桥接点。
    // ⚠️ 必须在 accent 覆盖**之后**应用，否则自定义强调色会被覆盖回去。
    val skinnedColors = skinTokens.applySurfaces(finalPocketColors, darkTheme)
    // M3 侧同步：MainActivity 的根 Surface() / Scaffold 默认底色 / TopAppBar
    // 都读 colorScheme 的 surface / background / surfaceVariant —— 只改上面那行不够。
    // 角色对应沿用各配色原有语义（surface=卡色 / background=页底 / surfaceVariant=次级）。
    val skinnedScheme = finalColorScheme.copy(
       surface = skinnedColors.surfaceCard,
       background = skinnedColors.surfaceBase,
       surfaceVariant = skinnedColors.surfaceAlt
    )
    // M3 形状表同样由皮肤派生 —— 项目里 77 处 `Card(` 中没显式传 shape 的全读它。
    val skinnedShapes = skinShapes(skinTokens)

    CompositionLocalProvider(
       LocalDarkMode provides darkTheme,
       LocalAppearanceMode provides appearanceMode,
       LocalPocketXiaoliColors provides skinnedColors,
       // 未被皮肤推移过的基色。设置页的皮肤预览靠它 + `SkinStyle.tokens()` +
       // `applySurfaces` 现场推导"另一套皮肤"的底色 —— 三者都是既有真源，
       // 所以预览与实际切换后的观感**物理上不可能漂移**。
       LocalBaseColors provides finalPocketColors,
       LocalSkin provides skinTokens,
       LocalSkinStyle provides skinStyle
    ) {
       // MIUIX 主题层：把本项目的 9 个颜色令牌映射成 MIUIX 的 52 色方案
       // （见 MiuixColorMapping.kt）。这样 MIUIX 组件一放进来就能读到正确配色，
       // 不必先做整体改造；Material3 主题保持在外层，两套组件可并存。
       MiuixTheme(
            colors = skinnedColors.toMiuixColors(darkTheme),
            // ✅ 2026-10-18 起**有消费者了**：设置首页与全部二级设置页换成 MIUIX 外壳
            //（`ui/components/MiuixSettings.kt`：Scaffold + TopAppBar(largeTitle) + SmallTitle + 开关），
            // 这些组件的字号直接取这里的 textStyles。此前那句「塞 40sp 也一张基线不变」的实测结论已作废。
            // 见 MiuixTypographyMapping.kt 的 KDoc。
            textStyles = miuixTextStyles(),
        ) {
           MaterialTheme(
               colorScheme = skinnedScheme,
               typography = AppTypography,
               shapes = skinnedShapes,
               content = content
           )
       }
    }
}
/** Convenience: resolve active accent color from current theme */
@Composable
fun currentAccent(): Color = MaterialTheme.colorScheme.primary

@Composable
fun currentAccentSoft(): Color = MaterialTheme.colorScheme.primaryContainer
