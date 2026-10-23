package com.pocket.xiaoli.ui.assistant

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.filled.Face
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.TextFields
import androidx.compose.material.icons.outlined.Face
import androidx.compose.material.icons.outlined.Key
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.NotificationsActive
import androidx.compose.material.icons.outlined.TextFields
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.dropShadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.shadow.Shadow
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pocket.xiaoli.data.proactive.ProactiveLevel
import com.pocket.xiaoli.ui.components.AppCard
import com.pocket.xiaoli.ui.components.SectionCaption
import com.pocket.xiaoli.ui.theme.CourseColors
import com.pocket.xiaoli.ui.theme.CourseColorsDark
import com.pocket.xiaoli.ui.theme.CourseTextColors
import com.pocket.xiaoli.ui.theme.CourseTextColorsDark
import com.pocket.xiaoli.ui.theme.LocalDarkMode
import com.pocket.xiaoli.ui.theme.LocalPocketXiaoliColors
import com.pocket.xiaoli.ui.theme.LocalSkin
import com.pocket.xiaoli.ui.theme.MascotAvatar
import com.pocket.xiaoli.ui.theme.MascotLook
import com.pocket.xiaoli.ui.theme.MascotPalette
import com.pocket.xiaoli.ui.theme.currentIconPresentation
import com.pocket.xiaoli.ui.theme.miuixPress

/**
 * AI 设置的**首页**：一张状态卡 + 四个入口，每个入口通向一个二级页。
 *
 * ## 为什么是"首页 + 二级页"而不是四个 NavHost 路由
 *
 * 二级页用**本页内部状态**切换（见 [AssistantAiSettingsScreen]），不新增导航路由。
 * 关键理由是**模型草稿**：接口地址 / Key 是输入中的文本，若每次进二级页都重建，
 * 用户打了一半切去看形象，回来就丢了一半。挂在宿主上就天然活得比页面久。
 *
 * ## 为什么首页要显示"现在是什么"
 *
 * 四项设置的实际取值大多藏在二级页里（形象叫什么、字号哪档、提醒哪档）。
 * 首页只给四个名字的话，用户要逐个点进去才知道自己设成了什么 ——
 * 所以每一行第二行文字都是**实时摘要**，四个摘要与二级页读的是同一批真源。
 *
 * ## 纯状态驱动
 *
 * 本函数不碰 store / container：所有值由宿主读好传进来。快照测试因此可以直接渲它。
 */
@Composable
internal fun AssistantSettingsHome(
    look: MascotLook,
    fontScale: Int,
    showReasoning: Boolean,
    proactiveLevel: ProactiveLevel,
    configured: Boolean,
    modelName: String,
    hasUnsavedModel: Boolean,
    onOpenMascot: () -> Unit,
    onOpenAppearance: () -> Unit,
    onOpenProactive: () -> Unit,
    onOpenModel: () -> Unit,
) {
    val skin = LocalSkin.current
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(skin.spaceMd),
    ) {
        StatusHero(
            look = look,
            configured = configured,
            modelName = modelName,
        )

        EntryGroup(title = "助手的样子") {
            SettingsEntryRow(
                icon = if (currentIconPresentation().filled) Icons.Filled.Face else Icons.Outlined.Face,
                title = "形象与动作",
                summary = mascotSummary(look),
                tintIndex = 3,
                onClick = onOpenMascot,
            )
            EntryDivider()
            SettingsEntryRow(
                icon = if (currentIconPresentation().filled) Icons.Filled.TextFields else Icons.Outlined.TextFields,
                title = "对话外观",
                summary = "字号${fontScaleLabel(fontScale)} · 思考链${if (showReasoning) "开" else "关"}",
                tintIndex = 1,
                onClick = onOpenAppearance,
            )
            EntryDivider()
            SettingsEntryRow(
                icon = if (currentIconPresentation().filled) {
                    Icons.Filled.NotificationsActive
                } else {
                    Icons.Outlined.NotificationsActive
                },
                title = "主动提醒",
                summary = "${proactiveLevelTitle(proactiveLevel)} · ${proactiveLevelSummary(proactiveLevel)}",
                tintIndex = 4,
                onClick = onOpenProactive,
            )
        }

        EntryGroup(title = "连接与隐私") {
            SettingsEntryRow(
                icon = if (currentIconPresentation().filled) Icons.Filled.Key else Icons.Outlined.Key,
                title = "模型与密钥",
                summary = modelSummary(configured, modelName, hasUnsavedModel),
                summaryAccent = hasUnsavedModel,
                tintIndex = 2,
                onClick = onOpenModel,
            )
        }

        PrivacyLine()
    }
}

/** 首页顶部那张状态卡：小鲤现在是什么样、能不能开始聊。 */
@Composable
private fun StatusHero(
    look: MascotLook,
    configured: Boolean,
    modelName: String,
) {
    val skin = LocalSkin.current
    val colors = LocalPocketXiaoliColors.current
    val isDark = LocalDarkMode.current
    val colorful = currentIconPresentation().colorful
    val shape = RoundedCornerShape(skin.radiusLarge)

    // 与欢迎页同一套"卡片从一块纯色变成有方向的色面"的做法；
    // 纯净 / 极简模式保持中性卡 + 描边。
    val brush = if (colorful) {
        Brush.linearGradient(
            listOf(
                colors.accentMain.copy(alpha = if (isDark) 0.24f else 0.14f),
                lerp(colors.accentMain.copy(alpha = 0.10f), colors.surfaceCard, 0.55f),
                colors.surfaceCard,
            )
        )
    } else null
    val shadow = if (colorful && skin.shadowBlur > 0.dp) {
        Modifier.dropShadow(
            shape = shape,
            shadow = Shadow(
                radius = skin.shadowBlur,
                spread = 0.dp,
                color = colors.accentMain.copy(alpha = if (isDark) 0.26f else 0.12f),
                offset = DpOffset(0.dp, skin.shadowOffsetY),
            ),
        )
    } else {
        Modifier
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .then(shadow)
            .clip(shape)
            .then(if (brush != null) Modifier.background(brush) else Modifier.background(colors.surfaceCard))
            .then(if (colorful) Modifier else Modifier.border(1.dp, colors.borderDivider, shape))
            .padding(horizontal = skin.spaceLg, vertical = skin.spaceCompact),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "小鲤",
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Bold,
                    color = colors.textPrimary,
                )
                Spacer(Modifier.width(skin.spaceSm))
                StatusChip(configured = configured)
            }
            Spacer(Modifier.height(skin.spaceXs))
            Text(
                text = if (configured) {
                    "已连接 $modelName，课程、作业、成绩、天气都能问"
                } else {
                    "填好接口地址、模型名和 Key，就能开始对话"
                },
                fontSize = 12.sp,
                color = colors.textSecondary,
                lineHeight = 18.sp,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Spacer(Modifier.width(skin.spaceSm))
        // 头像不眨眼：首页是静态的"它现在长这样"，一直动会跟下面「形象与动作」的预览抢注意力
        Box(
            modifier = Modifier.height(64.dp),
            contentAlignment = Alignment.Center,
        ) {
            if (colorful && skin.shadowBlur > 0.dp) {
                Box(
                    modifier = Modifier
                        .requiredSize(96.dp)
                        .background(
                            brush = Brush.radialGradient(
                                listOf(
                                    colors.accentMain.copy(alpha = if (isDark) 0.20f else 0.18f),
                                    colors.accentMain.copy(alpha = 0f),
                                )
                            ),
                            shape = CircleShape,
                        )
                )
            }
            MascotAvatar(size = 56.dp, look = look, blinking = false)
        }
    }
}

/** 「已就绪 / 待配置」小胶囊。用语义状态色（warning 表示"还差一步"），不是装饰色。 */
@Composable
private fun StatusChip(configured: Boolean) {
    val colors = LocalPocketXiaoliColors.current
    val tint = if (configured) colors.accentMain else colors.warning
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(tint.copy(alpha = 0.12f))
            .border(1.dp, tint.copy(alpha = 0.18f), RoundedCornerShape(50))
            .padding(horizontal = 8.dp, vertical = 2.dp),
    ) {
        Text(
            text = if (configured) "已就绪" else "待配置",
            fontSize = 10.sp,
            fontWeight = FontWeight.Medium,
            color = tint,
        )
    }
}

/** 一张卡里的一组入口（卡边到底，行自己负责内边距）。 */
@Composable
private fun EntryGroup(
    title: String,
    content: @Composable ColumnScope.() -> Unit,
) {
    val skin = LocalSkin.current
    Column(modifier = Modifier.fillMaxWidth()) {
        SectionCaption(
            text = title,
            modifier = Modifier.padding(start = skin.spaceXs, bottom = skin.spaceSm),
        )
        AppCard { Column(content = content) }
    }
}

/**
 * 一行入口：彩色图标块 + 标题 + 实时摘要 + 右箭头。
 *
 * 触摸高度 64dp（`heightIn` 下界），高于本项目 44dp 的无障碍底线 ——
 * 这一行是首页唯一的点击目标，点不中就没有二级页可进。
 */
@Composable
private fun SettingsEntryRow(
    icon: ImageVector,
    title: String,
    summary: String,
    tintIndex: Int,
    onClick: () -> Unit,
    summaryAccent: Boolean = false,
) {
    val skin = LocalSkin.current
    val colors = LocalPocketXiaoliColors.current
    val isDark = LocalDarkMode.current
    val colorful = currentIconPresentation().colorful
    val palette = if (isDark) CourseColorsDark else CourseColors
    val textPalette = if (isDark) CourseTextColorsDark else CourseTextColors
    val tint = if (colorful) palette[tintIndex % palette.size] else colors.surfaceHighlight
    val tintText = if (colorful) textPalette[tintIndex % textPalette.size] else colors.accentMain

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .semantics(mergeDescendants = true) { role = Role.Button }
            .miuixPress(onClick = onClick)
            // 13dp 上下 = 40dp 图标块 + 两侧留白，最终行高 ≥64dp
            .padding(horizontal = skin.spaceLg, vertical = 13.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(RoundedCornerShape(12.dp))
                .then(
                    if (colorful) {
                        Modifier.background(
                            Brush.linearGradient(
                                listOf(tint, lerp(tint, colors.surfaceCard, if (isDark) 0.22f else 0.45f)),
                            )
                        )
                    } else {
                        Modifier.background(tint)
                    }
                ),
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, contentDescription = null, tint = tintText, modifier = Modifier.size(21.dp))
        }
        Spacer(Modifier.width(skin.spaceMd))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold,
                color = colors.textPrimary,
            )
            Spacer(Modifier.height(2.dp))
            Text(
                text = summary,
                fontSize = 12.sp,
                color = if (summaryAccent) colors.accentMain else colors.textSecondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Spacer(Modifier.width(skin.spaceSm))
        Icon(
            imageVector = Icons.AutoMirrored.Outlined.KeyboardArrowRight,
            contentDescription = null,
            tint = colors.textTertiary,
            modifier = Modifier.size(20.dp),
        )
    }
}

/**
 * 行间分割线：从文字左边缘起（16dp 内边距 + 40dp 图标 + 12dp 间隔 = 68dp）。
 * 改行内边距或图标尺寸时这个值要跟着改。
 */
@Composable
private fun EntryDivider() {
    val colors = LocalPocketXiaoliColors.current
    HorizontalDivider(
        color = colors.borderDivider,
        thickness = 0.5.dp,
        modifier = Modifier.padding(start = 68.dp, end = 16.dp),
    )
}

/** 首页底部那句隐私说明：它是这个页面里唯一一句"承诺"，不是装饰。 */
@Composable
private fun PrivacyLine() {
    val skin = LocalSkin.current
    val colors = LocalPocketXiaoliColors.current
    Row(
        modifier = Modifier.padding(horizontal = skin.spaceXs, vertical = skin.spaceXs),
        verticalAlignment = Alignment.Top,
    ) {
        Icon(
            imageVector = Icons.Outlined.Lock,
            contentDescription = null,
            tint = colors.textTertiary,
            modifier = Modifier.size(13.dp).padding(top = 1.dp),
        )
        Spacer(Modifier.width(6.dp))
        Text(
            text = "Key 只存在本机加密存储里，不上传；对话内容会发给你填的模型服务商。",
            fontSize = 11.sp,
            color = colors.textSecondary,
            lineHeight = 16.sp,
        )
    }
}

/** 「形象与动作」这一行现在的值（与二级页大预览同一套规则）。 */
internal fun mascotSummary(look: MascotLook): String = buildString {
    append(look.shape.name)
    when {
        look.shape.projection != null -> append(" · 32 种表情")
        look.shape.body == null -> {
            append(" · ")
            append(MascotPalette.labelOf(look.colorHex))
        }
        else -> append(" · 原版配色")
    }
}

/** 「模型与密钥」这一行现在的值；[unsaved] 时要点明"改了但没存"。 */
internal fun modelSummary(configured: Boolean, modelName: String, unsaved: Boolean): String = when {
    unsaved -> "$modelName · 有未保存的修改"
    configured -> "$modelName · 已保存"
    else -> "还没填 API Key，点这里配置"
}
