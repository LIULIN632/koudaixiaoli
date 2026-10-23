package com.pocket.xiaoli.ui.profile.components

import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pocket.xiaoli.ui.theme.*
import com.pocket.xiaoli.ui.theme.rememberSinkIndication

// ═══ 设计令牌：改为从全局主题读取 ═══
//
// 这几项原先是**硬编码的私有颜色常量**（PRD V3），与全局的 PocketXiaoliColors
// 完全重复 —— 后果是「我的」页**无法跟随 8 套配色主题**，切主题时它纹丝不动。
//
// 现在改为 @Composable getter，从 LocalPocketXiaoliColors 读取。
// 保留原有符号名，因此**所有调用点无需改动**，但整页会自动跟随主题与深浅色。
//
// 为什么必须是 @Composable：CompositionLocal 只能在组合期读取，
// 顶层 val 做不到 —— 这正是原实现被迫硬编码的原因。

private val TextDark: Color
    @Composable get() = LocalPocketXiaoliColors.current.textPrimary

private val TextGray: Color
    @Composable get() = LocalPocketXiaoliColors.current.textSecondary

private val DividerGray: Color
    @Composable get() = LocalPocketXiaoliColors.current.borderDivider

private val PinkAccent: Color
    @Composable get() = LocalPocketXiaoliColors.current.accentMain

private val BlueAccent: Color
    @Composable get() = LocalPocketXiaoliColors.current.accentMain

private val PinkBg: Color
    @Composable get() = LocalPocketXiaoliColors.current.surfaceHighlight

private val BlueBg: Color
    @Composable get() = LocalPocketXiaoliColors.current.surfaceHighlight

private val PageBg: Color
    @Composable get() = LocalPocketXiaoliColors.current.surfaceBase

/** 棕色调标签底色：无对应语义令牌，保留固定值（仅用于"标签"这一处装饰）。 */
private val BrownTagBg = Color(0xFF8B6F47)

/** 宠物卡渐变：这是 PetCard 的品牌视觉（蓝色渐变 + 柴犬场景），刻意不跟随主题。 */
private val PetCardBlue1 = Color(0xFFD6EAFF)
private val PetCardBlue2 = Color(0xFFFFFFFF)

// ═══ 助手卡：鲤鱼头像 + 问候 + 陪伴天数 ═══
//
// 历史：这里原先是 PetCard（蓝色渐变 + 柴犬场景 + 4 个可换宠物）。
// 宠物是从别的软件带来的概念，与「口袋小鲤」的 AI 助手定位不符，已整组移除。
// 现在卡片的视觉核心是**鲤鱼头像**，且头像会跟随 Agent 阶段变化
// （见 ui/theme/AiAvatar.kt 与 AppContainer.aiAvatarPhase）。

// ═══ V3: ReminderCard — pink bg, with icon ═══

// ═══ V3: QuickActionGrid 2×2 ═══

@Composable
private fun QuickActionCard(
    icon: ImageVector,
    title: String,
    subtitle: String,
    bgColor: Color,
    accentColor: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val skin = LocalSkin.current
    Card(
        modifier = modifier.clickable(interactionSource = remember { MutableInteractionSource() }, indication = rememberSinkIndication(), onClick = onClick),
        shape = RoundedCornerShape(skin.radiusChip),
        colors = CardDefaults.cardColors(containerColor = bgColor),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = skin.spaceLg, vertical = 14.dp)
        ) {
            Icon(icon, null, tint = accentColor, modifier = Modifier.size(24.dp))
            Spacer(Modifier.height(skin.spaceSm))
            Text(title, fontSize = 14.sp, fontWeight = FontWeight.Bold, color = accentColor)
            Spacer(Modifier.height(skin.spaceMicro))
            Text(subtitle, fontSize = 12.sp, color = TextGray)
        }
    }
}

// ═══ V3: MoreFeatures section header + list ═══



