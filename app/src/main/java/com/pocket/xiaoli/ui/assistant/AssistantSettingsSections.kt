package com.pocket.xiaoli.ui.assistant

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pocket.xiaoli.data.proactive.ProactiveLevel
import com.pocket.xiaoli.ui.proactive.BubbleTip
import com.pocket.xiaoli.ui.theme.LocalPocketXiaoliColors
import com.pocket.xiaoli.ui.theme.LocalSkin
import com.pocket.xiaoli.ui.theme.MASCOT_MOTION_CATALOG
import com.pocket.xiaoli.ui.theme.MascotAvatar
import com.pocket.xiaoli.ui.theme.MascotColor
import com.pocket.xiaoli.ui.theme.MascotFace
import com.pocket.xiaoli.ui.theme.MascotFaces
import com.pocket.xiaoli.ui.theme.MascotLook
import com.pocket.xiaoli.ui.theme.MascotMotion
import com.pocket.xiaoli.ui.theme.MascotPalette
import com.pocket.xiaoli.ui.theme.MascotShape
import com.pocket.xiaoli.ui.theme.MascotShapes
import com.pocket.xiaoli.ui.theme.forMotion
import com.pocket.xiaoli.ui.theme.parseMascotColor

/** 形状宫格的列数。17 个形状 = 5 行整 + 2 个。 */
private const val SHAPE_COLUMNS = 3

/** 色盘的列数。12 色 = 2 行整。 */
private const val COLOR_COLUMNS = 6

/** 「动作」那一行每排放几个 chip。7 个动作 = 1 行 4 个 + 1 行 3 个。 */
private const val MOTION_COLUMNS = 4

/**
 * 色块所在格子的高度。
 *
 * 48dp 是 Material 的最小触摸目标；本项目 `AssistantAccessibilityTest` 的底线是 44dp。
 * 色块本身只画 34dp 的圆，多出来的高度全是**触摸区**。
 */
private val COLOR_CELL_HEIGHT = 48.dp

/**
 * 「形象与动作」二级页。
 *
 * 抽成独立函数的理由：它是**纯状态驱动**的（进来什么 [look] 就画什么），
 * 于是快照测试可以直接渲它而不必去搭一个 DI 容器（`AppContainer` 会连上 Room / prefs）。
 * 设置页本体因此只剩下"把 store 的回调接上去"这一件事。
 */
@Composable
internal fun MascotSettingsSection(
    look: MascotLook,
    onPickShape: (String) -> Unit,
    onPickColor: (String) -> Unit,
) {
    val skin = LocalSkin.current
    val colors = LocalPocketXiaoliColors.current
    // 「动作试演」只改这一页的大预览，不写任何设置 —— 它不是配置项，是"看看它会怎么动"。
    var previewMotion by remember { mutableStateOf(MascotMotion.IDLE) }
    // 「表情试演」同理；null = 跟着动作走（见 `MascotFaces.forMotion`）。
    var previewFace by remember { mutableStateOf<MascotFace?>(null) }
    SettingsCard(title = "形象") {
        MascotPreview(look = look, motion = previewMotion, face = previewFace)

        Spacer(Modifier.height(skin.spaceMd))
        GroupLabel("动作")
        MASCOT_MOTION_CATALOG.chunked(MOTION_COLUMNS).forEach { rowMotions ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(skin.spaceSm),
            ) {
                rowMotions.forEach { motion ->
                    FilterChip(
                        selected = previewMotion == motion,
                        onClick = { previewMotion = motion },
                        label = { Text(motion.previewLabel, fontSize = 12.sp) },
                        // 44dp 是本项目的无障碍底线（见 docs/UI验收手册.md）；FilterChip 默认只有 32dp
                        modifier = Modifier.heightIn(min = 44.dp),
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = colors.accentMain,
                            selectedLabelColor = Color.White,
                        ),
                    )
                }
            }
            Spacer(Modifier.height(skin.spaceXs))
        }
        InstantHint("点一下就在上面演示；「收工」「出岔子」只演一遍（就是对话结束时那两下）。")

        // 表情：只有 Grok Ball 那三个身体认它（眼形是 48 点眼环现算的），
        // 其余 14 个形状眼形固定，给它们看一排点不动的 chip 只会让人以为坏了。
        if (look.shape.projection != null) {
            Spacer(Modifier.height(skin.spaceMd))
            GroupLabel("表情")
            // 32 个表情横着滑：一行塞不下、竖着铺 8 行会把整页变成一张表。
            LazyRow(horizontalArrangement = Arrangement.spacedBy(skin.spaceSm)) {
                item {
                    FilterChip(
                        selected = previewFace == null,
                        onClick = { previewFace = null },
                        label = { Text("随动作", fontSize = 12.sp) },
                        modifier = Modifier.heightIn(min = 44.dp),
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = colors.accentMain,
                            selectedLabelColor = Color.White,
                        ),
                    )
                }
                items(MascotFaces.all, key = { it.id }) { face ->
                    FilterChip(
                        selected = previewFace?.id == face.id,
                        onClick = {
                            previewFace = face
                            // 表情与动作抢同一个头：选了具体表情就把动作归零，
                            // 否则「思考」的歪头会叠在「开心」的眯眼上，看不清选了哪个
                            previewMotion = MascotMotion.IDLE
                        },
                        label = { Text(face.label, fontSize = 12.sp) },
                        modifier = Modifier.heightIn(min = 44.dp),
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = colors.accentMain,
                            selectedLabelColor = Color.White,
                        ),
                    )
                }
            }
            InstantHint("32 个表情照搬 Grok Ball；不选就随动作走（想事情时是「思考中」，出错时是「出错」）。")
        }

        Spacer(Modifier.height(skin.spaceMd))
        GroupLabel("形状")
        MascotShapes.all.chunked(SHAPE_COLUMNS).forEach { rowShapes ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(skin.spaceSm),
            ) {
                rowShapes.forEach { shape ->
                    ShapeCell(
                        shape = shape,
                        // 形状宫格统一用**当前颜色**预览：这样"换成这条鱼"
                        // 与"换成这个色"是两件可以分别判断的事
                        colorHex = look.colorHex,
                        selected = shape.name == look.shape.name,
                        onClick = { onPickShape(shape.name) },
                        modifier = Modifier.weight(1f),
                    )
                }
                // 补齐末行：不补的话最后一行若不满，格子会被 weight 拉宽成一条
                repeat(SHAPE_COLUMNS - rowShapes.size) {
                    Spacer(Modifier.weight(1f))
                }
            }
            Spacer(Modifier.height(skin.spaceSm))
        }

        Spacer(Modifier.height(skin.spaceXs))
        // 署名：这 11 个「非鱼」形象全部照搬了别人的开源素材，许可都要求保留归属，
        // 而且用户点名"别忘了感谢" —— 所以这几行不是装饰，删了就是许可问题。
        Text(
            text = "「星星」「蓝鲸」……等 7 个形象来自 DS Bot（Fei2411，Apache-2.0）；" +
                "「圆球」「楔子」「宝石」及其 32 个表情来自 Grok Ball（TyCoding，MIT），" +
                "「头盔」来自 npface（scccc6ut，MIT）。谢谢作者们开源。",
            fontSize = 11.sp,
            color = colors.textSecondary,
            lineHeight = 16.sp,
            modifier = Modifier.padding(bottom = skin.spaceSm),
        )
        if (look.shape.body == null) {
            GroupLabel("颜色")
            MascotPalette.all.chunked(COLOR_COLUMNS).forEach { rowColors ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(skin.spaceSm),
                ) {
                    rowColors.forEach { color ->
                        ColorCell(
                            color = color,
                            selected = color.hex.equals(look.colorHex, ignoreCase = true),
                            onClick = { onPickColor(color.hex) },
                            modifier = Modifier.weight(1f),
                        )
                    }
                    repeat(COLOR_COLUMNS - rowColors.size) {
                        Spacer(Modifier.weight(1f))
                    }
                }
                Spacer(Modifier.height(skin.spaceSm))
            }
        } else {
            // 这 11 个形象自带配色（黑猫、橙方块、红幽灵、黑球、navy 头盔…），
            // 用色盘硬刷一遍会变成"同一条鱼换七次颜色"，所以这一段对它不出现 ——
            // 但要说清"为什么没有"，否则用户会以为色盘坏了。
            InstantHint("这个形象用的是它原本的配色（和原作者给的一致），换回上面的鱼就可以改颜色。")
        }

        InstantHint("点选后立即生效，不需要保存。")
    }
}

/** 「对话外观」二级页（回答字号 / 思考链）。同样是纯状态驱动，见 [MascotSettingsSection]。 */
@Composable
internal fun AppearanceSettingsSection(
    fontScale: Int,
    showReasoning: Boolean,
    onFontScale: (Int) -> Unit,
    onReasoning: (Boolean) -> Unit,
) {
    val skin = LocalSkin.current
    val colors = LocalPocketXiaoliColors.current
    SettingsCard(title = "外观") {
        GroupLabel("回答字号")
        Row(horizontalArrangement = Arrangement.spacedBy(skin.spaceSm)) {
            FONT_SCALES.forEach { (label, value) ->
                FilterChip(
                    selected = fontScale == value,
                    onClick = { onFontScale(value) },
                    label = { Text(label, fontSize = 12.sp) },
                    // 与「动作」那排 chip 同一档触摸高度；M3 默认 32dp 低于本项目 44dp 底线
                    modifier = Modifier.heightIn(min = 44.dp),
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = colors.accentMain,
                        selectedLabelColor = Color.White,
                    ),
                )
            }
        }
        Spacer(Modifier.height(skin.spaceXs))
        Text(
            "只影响鲤鱼的正文，不影响其它页面。",
            fontSize = 11.sp,
            color = colors.textSecondary,
        )

        Spacer(Modifier.height(skin.spaceMd))
        SwitchLine(
            title = "显示思考链",
            subtitle = "模型返回推理过程时，在回答上方展示可折叠的思考过程",
            checked = showReasoning,
            onToggle = onReasoning,
        )

        InstantHint("两项都是点一下立即生效。")
    }
}

/**
 * 「主动提醒」二级页（关 / 少 / 标准）。
 *
 * 同样抽成独立函数、纯状态驱动，理由与 [MascotSettingsSection] 一样：
 * 快照测试可以直接渲它，不必搭 DI 容器。
 *
 * ## 三个档位与它们的说明气泡
 *
 * 档位名只有一两个字（"少"到底是什么？），所以每一档都挂一个「?」说明气泡 ——
 * 这是本项目第一批说明气泡的使用点。文案写的是**行为差异**（多久提一次、提哪些），
 * 不是营销话术；改了行为就要同步改它，否则说明气泡会变成谎话。
 */
@Composable
internal fun ProactiveSettingsSection(
    level: ProactiveLevel,
    onPick: (ProactiveLevel) -> Unit,
) {
    val skin = LocalSkin.current
    val colors = LocalPocketXiaoliColors.current
    SettingsCard(title = "主动提醒") {
        Text(
            "鲤鱼会在底栏上方冒一句话（快上课、快考试、有新成绩…）。点气泡能直接问它，8 秒后自动收起。",
            fontSize = 12.sp,
            color = colors.textSecondary,
            lineHeight = 17.sp,
        )
        Spacer(Modifier.height(skin.spaceSm))

        PROACTIVE_LEVELS.forEach { option ->
            val selected = option.level == level
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    // 48dp 是 Material 的最小触摸目标；本项目无障碍底线是 44dp（见 UI验收手册）
                    .heightIn(min = 48.dp)
                    .selectable(
                        selected = selected,
                        role = Role.RadioButton,
                        onClick = { onPick(option.level) },
                    )
                    .padding(vertical = skin.spaceXs),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        option.title,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium,
                        color = if (selected) colors.accentMain else colors.textPrimary,
                    )
                    Spacer(Modifier.height(skin.spaceMicro))
                    Text(option.summary, fontSize = 12.sp, color = colors.textSecondary, lineHeight = 16.sp)
                }
                if (selected) {
                    Icon(
                        Icons.Filled.Check,
                        contentDescription = "已选中",
                        tint = colors.accentMain,
                        modifier = Modifier.size(18.dp),
                    )
                }
                BubbleTip(
                    text = option.tip,
                    contentDescription = "「${option.title}」这一档的说明",
                )
            }
        }

        InstantHint("三档都是点一下立即生效，不需要保存。")
    }
}

/** 一个档位的展示文案。**行为变了就要改这里**（说明气泡是用户判断"要不要开"的唯一依据）。 */
internal data class ProactiveLevelOption(
    val level: ProactiveLevel,
    val title: String,
    val summary: String,
    val tip: String,
)

internal val PROACTIVE_LEVELS = listOf(
    ProactiveLevelOption(
        level = ProactiveLevel.OFF,
        title = "关",
        summary = "完全不冒泡，点它也不说话",
        tip = "课表、成绩、教室这些页面照常用，只是鲤鱼不会主动冒泡。随时可以在这里打开。",
    ),
    ProactiveLevelOption(
        level = ProactiveLevel.LOW,
        title = "少",
        summary = "只提醒考试、快上课、余额这类正事",
        tip = "两条正事提醒之间至少隔 1 小时，不闲聊。",
    ),
    ProactiveLevelOption(
        level = ProactiveLevel.STANDARD,
        title = "标准",
        summary = "偶尔也会闲聊几句",
        tip = "两条正事提醒之间至少隔 15 分钟；没有要紧事时偶尔搭句话，闲聊之间至少隔 25 分钟。",
    ),
)

/**
 * 首页摘要要用的档位名。
 *
 * ⚠️ 与 [ProactiveSettingsSection] 读同一张 [PROACTIVE_LEVELS]：摘要与选中态
 * 若各自写一套 `when`，将来加档位必然漂移（首页写「标准」、设置里选中的是「少」）。
 */
internal fun proactiveLevelTitle(level: ProactiveLevel): String =
    PROACTIVE_LEVELS.firstOrNull { it.level == level }?.title ?: "标准"

/** 同上；给首页入口行一句"现在是什么行为"。 */
internal fun proactiveLevelSummary(level: ProactiveLevel): String =
    PROACTIVE_LEVELS.firstOrNull { it.level == level }?.summary ?: "偶尔也会闲聊几句"

/**
 * 形象预览：一只会眨眼的大号吉祥物 + 当前形状与颜色名。
 *
 * 预览必须**用当前形象**（含颜色），因为这一段的两组控件改的就是它 ——
 * 这也是这个页面唯一能"马上看见结果"的地方。
 *
 * [motion] 由「动作」那一行的 chip 驱动：它是**试演**，不落盘（动作不是设置项）。
 */
@Composable
private fun MascotPreview(look: MascotLook, motion: MascotMotion, face: MascotFace?) {
    val skin = LocalSkin.current
    val colors = LocalPocketXiaoliColors.current
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            modifier = Modifier
                .size(160.dp)
                .clip(RoundedCornerShape(skin.radiusLarge))
                .background(colors.accentMain.copy(alpha = 0.06f)),
            contentAlignment = Alignment.Center,
        ) {
            MascotAvatar(size = 140.dp, look = look, motion = motion, expression = face)
        }
        Spacer(Modifier.height(skin.spaceSm))
        Text(
            // DS 那几个形象不吃色盘，副标题写"· 蓝"会撒谎 —— 直接说它是原版配色
            text = buildString {
                append(look.shape.name)
                if (look.shape.projection != null) {
                    // 有表情的形象：把当前脸也算进副标题，否则"点了 chip 没反应"的感觉很强
                    append(" · ")
                    append(face?.label ?: MascotFaces.forMotion(motion).label)
                } else if (look.shape.body == null) {
                    append(" · ")
                    append(MascotPalette.labelOf(look.colorHex))
                } else {
                    append(" · 原版配色")
                }
            },
            fontSize = 13.sp,
            fontWeight = FontWeight.Medium,
            color = colors.textPrimary,
        )
    }
}

/** 一个形状格子：迷你吉祥物 + 名字。选中态用主色描边 + 浅底。 */
@Composable
private fun ShapeCell(
    shape: MascotShape,
    colorHex: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val skin = LocalSkin.current
    val colors = LocalPocketXiaoliColors.current
    val cellShape = RoundedCornerShape(skin.radiusChip)
    Column(
        modifier = modifier
            .clip(cellShape)
            .background(if (selected) colors.accentMain.copy(alpha = 0.10f) else colors.surfaceHighlight)
            .border(
                width = if (selected) 1.5.dp else 1.dp,
                color = if (selected) colors.accentMain else colors.borderDivider,
                shape = cellShape,
            )
            // ⚠️ 用 selectable 而不是 clickable：宫格是"多选一"，读屏要能念出
            // "已选中/未选中"。只用 clickable 的话，选中态在语义树里**完全不存在**
            //（同类问题在会话列表的 isCurrent 上踩过一次）。
            .selectable(selected = selected, role = Role.RadioButton, onClick = onClick)
            .padding(vertical = skin.spaceSm),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        MascotAvatar(
            size = 44.dp,
            look = MascotLook(shape.name, colorHex),
            // 宫格里不眨眼：六个 rememberInfiniteTransition 几乎同时启动，
            // 一起眨看起来像故障，而这一格要回答的是"这条鱼长什么样"
            blinking = false,
        )
        Spacer(Modifier.height(skin.spaceXs))
        Text(
            text = shape.name,
            fontSize = 12.sp,
            color = if (selected) colors.accentMain else colors.textSecondary,
        )
    }
}

/** 一个色块。名字进语义树 —— 「蓝」比 `#3B8BFF` 对读屏用户有意义得多。 */
@Composable
private fun ColorCell(
    color: MascotColor,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = LocalPocketXiaoliColors.current
    val fill = parseMascotColor(color.hex)
    // 外层拿 weight（等分列宽）并把高度撑到 48dp，内层才是那个固定直径的圆 ——
    //
    // ⚠️ 高度**不能**只由那个 34dp 的圆决定：`selectable` 并不会像 Material 组件那样
    // 自动补足最小交互尺寸，于是触摸区就只有 34dp（低于本项目 44dp 的无障碍底线）。
    // 这一条是被 `AssistantSettingsScreenshotTest.色块的触摸高度达标` 抓出来的 ——
    // 纯靠眼睛看，34dp 与 48dp 的差别在缩略图上完全看不出来。
    Box(
        modifier = modifier
            .height(COLOR_CELL_HEIGHT)
            .selectable(selected = selected, role = Role.RadioButton, onClick = onClick)
            .semantics { contentDescription = color.label },
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier
                .size(34.dp)
                .clip(CircleShape)
                .background(fill)
                .border(
                    width = if (selected) 2.5.dp else 1.dp,
                    // 选中圈用文字主色：主色实底在"选中的那个色块本身就是主色"时会糊在一起
                    color = if (selected) colors.textPrimary else colors.borderDivider,
                    shape = CircleShape,
                ),
            contentAlignment = Alignment.Center,
        ) {
            if (selected) {
                Icon(
                    imageVector = Icons.Filled.Check,
                    contentDescription = null,
                    // 勾的颜色按色块明度取：黄底上画白勾等于没画
                    tint = if (fill.luminance() > 0.5f) Color.Black.copy(alpha = 0.72f) else Color.White,
                    modifier = Modifier.size(16.dp),
                )
            }
        }
    }
}
