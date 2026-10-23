package com.pocket.xiaoli.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Text
import androidx.compose.ui.unit.dp
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.SmallTitle
import top.yukonga.miuix.kmp.basic.SmallTopAppBar

import com.pocket.xiaoli.ui.theme.LocalDarkMode
import com.pocket.xiaoli.ui.theme.LocalPocketXiaoliColors
import top.yukonga.miuix.kmp.theme.MiuixTheme

/**
 * 设置页的 MIUIX 外壳：**紧凑顶栏 + 返回箭头**，内容区由调用方自己排。
 *
 * 2026-10-18 第二次改：先做的是 `TopAppBar(largeTitle = …)`（大标题 + 滚动收折），
 * 用户一看就说「顶部太宽了，覆盖太大」—— 大标题形态在**不滚动**时也要给标题预留
 * 一块收折空间（`TopAppBarDefaults.CollapsedHeight` 52dp + 大标题区 + 底部留白），
 * 一屏 2340px 里它吃掉近 140dp，而设置页首屏只有两三个分组，代价不值。
 * 换成 `SmallTopAppBar` 后顶栏就是一条 52dp，内容立刻顶上来。
 *（想换回去只需把下面这个调用换成 `TopAppBar(title, largeTitle = title, scrollBehavior)`。）
 *
 * 为什么换成这套（2026-10-18，用户「设置页不够好看，直接用岱宗的」）：
 * 岱宗盒子的设置页是 MIUIX 的 `Scaffold` + 顶栏，分组标题用 `SmallTitle`，
 * 组与组之间靠卡片分隔。原实现是 Material3 `Scaffold` + 15sp 粗体小标题 +
 * 自绘组标题，层次是我们自己拍的。
 *
 * 只换"壳"，**滚动与页边距仍归调用方**：设置首页与二级页的留白都跟皮肤令牌走
 * （`skin.spaceLg`，14/16/18dp），写死 MIUIX 的 16dp 会让「外观 → 界面密度」在
 * 这一页看起来时灵时不灵。
 *
 * @param title 页名，作为顶栏标题。
 * @param onBack 左上角返回。
 * @param actions 顶栏右侧动作（消息页的「重新检查」用；设置页不传）。
 * @param manageStatusBar 顶栏是否连状态栏一起接管。
 *   开启后顶栏底色一直画到屏幕顶端，文字/返回键靠 `statusBarsPadding()` 让位。
 *   **只有设置了 `SELF_MANAGED_TOP_ROUTES` 的路由才能开**——那些路由的顶部 inset
 *   没有在导航层被消费；普通路由开了会把顶栏再压低一个状态栏的高度。
 * @param content 内容区，参数是 `Scaffold` 给的 `PaddingValues`。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun MiuixSettingsScaffold(
    title: String,
    onBack: () -> Unit,
    actions: @Composable RowScope.() -> Unit = {},
    manageStatusBar: Boolean = false,
    content: @Composable (PaddingValues) -> Unit,
) {
    val navigationIcon: @Composable () -> Unit = {
        IconButton(onClick = onBack) {
            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
        }
    }
    val body: @Composable (PaddingValues) -> Unit = { padding ->
        // ⚠️ 这里**不能**再 `.padding(padding)`：两个调用方（设置首页、九个二级页）
        // 都是自己在内容列上应用 padding 的，套两层会让内容被顶下去一整个顶栏的高度。
        Box(modifier = Modifier.fillMaxSize()) {
            content(padding)
        }
    }

    if (manageStatusBar) {
        val colors = LocalPocketXiaoliColors.current
        val isDark = LocalDarkMode.current
        val barBrush = Brush.verticalGradient(
            listOf(
                colors.accentMain.copy(alpha = if (isDark) 0.18f else 0.10f),
                MiuixTheme.colorScheme.surface,
            )
        )
        Scaffold(
            topBar = {
                // 底色放在外层 Box：SmallTopAppBar 自带的那层背景画在 inset padding
                // **之内**，只给状态栏下沿以下上色，顶部仍是页底。
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(barBrush)
                ) {
                    SmallTopAppBar(
                        title = title,
                        modifier = Modifier.statusBarsPadding(),
                        color = Color.Transparent,
                        navigationIcon = navigationIcon,
                        actions = actions,
                        defaultWindowInsetsPadding = false,
                    )
                }
            },
            // MIUIX 默认把 systemBars + displayCutout 当成内容 inset；接管模式下
            // 顶部额度已经算进 topBar 的高度里，再留一份就会双倍让位。
            contentWindowInsets = WindowInsets(0, 0, 0, 0),
            content = body,
        )
    } else {
        Scaffold(
            topBar = {
                SmallTopAppBar(
                    title = title,
                    navigationIcon = navigationIcon,
                    actions = actions,
                )
            },
            content = body,
        )
    }
}

/** 分组小标题：MIUIX 的 `SmallTitle`，与岱宗设置页同一层次（比正文小、弱化色）。 */
@Composable
internal fun MiuixSettingsSection(title: String) {
    // MIUIX 默认用 `onSurfaceVariantSummary`（三级灰）。在近白页底上，
    // 分组标题会比卡片正文还弱 —— 层次看着像反的。提到主文字色，靠字号区分层级。
    SmallTitle(title, textColor = MiuixTheme.colorScheme.onSurface)
}

/**
 * 第三层小标题：**学科 → 课程 → 文件**里的「课程」那一层。
 *
 * 比 [MiuixSettingsSection] 再弱一档（footnote1 + 三级文字色、左缩 4dp），
 * 靠字号与颜色区分层级，不用加线也不用加底色 —— 岱宗的目录树也是这么分层的。
 */
@Composable
internal fun MiuixSettingsSubSection(title: String) {
    Text(
        text = title,
        style = MiuixTheme.textStyles.footnote1,
        color = MiuixTheme.colorScheme.onSurfaceSecondary,
        fontWeight = androidx.compose.ui.text.font.FontWeight.Medium,
        modifier = Modifier.padding(start = 4.dp, top = 2.dp),
    )
}

/** 一个筛选项：[id] 用于回调，[label] 是显示文字。 */
internal data class MiuixChoice(val id: String, val label: String)

/**
 * 一条**横向可滚**的筛选 chip 行，学科与课程共用。
 *
 * 为什么横向滚而不是 `FlowRow` 换行：学科/课程一多就会占掉三四行，
 * 把搜索结果挤到屏幕外；横滚只占一行，手指一划就能看到后面的。
 * 传入 [allLabel] 时最前面多一个「全部」（对应 `id = null`）。
 */
@Composable
internal fun MiuixChoiceRow(
    choices: List<MiuixChoice>,
    selectedId: String?,
    onSelect: (String?) -> Unit,
    allLabel: String? = null,
) {
    val colors = LocalPocketXiaoliColors.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        if (allLabel != null) {
            MiuixChoiceChip(
                label = allLabel,
                selected = selectedId == null,
                onClick = { onSelect(null) },
                colors = colors,
            )
        }
        choices.forEach { choice ->
            MiuixChoiceChip(
                label = choice.label,
                selected = choice.id == selectedId,
                onClick = { onSelect(choice.id) },
                colors = colors,
            )
        }
    }
}

@Composable
private fun MiuixChoiceChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    colors: com.pocket.xiaoli.ui.theme.PocketXiaoliColors,
) {
    FilterChip(
        selected = selected,
        onClick = onClick,
        label = { Text(label, style = MiuixTheme.textStyles.footnote1) },
        colors = FilterChipDefaults.filterChipColors(
            selectedContainerColor = colors.accentMain,
            // 选中态底色是 accent，文字用 surfaceBase（浅色白字 / 深色近黑字）两边都压得住。
            selectedLabelColor = colors.surfaceBase,
        ),
    )
}