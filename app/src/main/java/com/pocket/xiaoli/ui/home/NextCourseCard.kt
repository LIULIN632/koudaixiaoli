package com.pocket.xiaoli.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Event
import androidx.compose.material.icons.outlined.Place
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.pocket.xiaoli.ui.components.AppCard
import com.pocket.xiaoli.ui.theme.AppearanceMode
import com.pocket.xiaoli.ui.theme.CampusLayoutMode
import com.pocket.xiaoli.ui.theme.LocalAppearanceMode
import com.pocket.xiaoli.ui.theme.LocalCampusLayoutMode
import com.pocket.xiaoli.ui.theme.LocalSkin
import com.pocket.xiaoli.ui.theme.PocketTypography
import com.pocket.xiaoli.ui.theme.PocketXiaoliColors
import com.pocket.xiaoli.ui.theme.miuixPress
import com.pocket.xiaoli.ui.theme.onAccentColor

/**
 * 首页「下一节课」大卡（**四态**）。
 *
 * | 态 | 判据 | 文案 |
 * |---|---|---|
 * | 正在上课 | `course.isOngoing` | 正在上课 + 课程信息 |
 * | 下一节课 | `course != null` | 下一节课（· 明天 / · N 天后） |
 * | 今天没有课啦 | `course == null && hasTimetable` | 好好休息 |
 * | **还没有课表** | `course == null && !hasTimetable` | **去导入 / 手动添加** |
 *
 * ## 第四态为什么必须存在（2026-10 补）
 *
 * 前三个态里，`course == null` 同时表示"今天没课"和"压根没导入课表"，
 * 而卡片对两者都说"今天没有课啦"。于是**新生第一屏读到的是一句假话**：
 * 他没有任何数据，App 却断言他今天没课；而真正该做的第一件事（导入课表）
 * 连入口都没有，卡片的落点只是"打开课表页"。
 *
 * 现在零数据时给的是**动作**（导入课表）而不是**结论**（今天没课）。
 *
 * ⚠️ `private` → `internal` 是**为了让快照测得到**（2026-10 用户报"这张卡改回之前的"时
 * 才发现：整个首页**一张基线图都没有**，于是"到底哪里和之前不一样"只能靠翻 git 历史与读代码）。
 * 整页 `HomeScreen` 要 `AppContainer` + ViewModel 才渲得出来，所以按 `CLAUDE.md` 的老办法
 * 把这张卡抽成可独立渲染的组件 —— **不要**为了"看着更封装"改回 private。
 *
 * ⚠️ 2026-10：标题右侧那个「?」说明气泡（`BubbleTip`）**加过又被用户退掉了** ——
 * 他说"改回之前的"，并附了加它之前的截图。所以这里保持**没有**气泡的样子。
 * 想再加回来，请先问：那张卡本身已经把"下一节课"讲清楚了，气泡是纯附加物。
 */
@Composable
internal fun NextCourseCard(
    course: HomeCourse?,
    colors: PocketXiaoliColors,
    onOpenTimetable: () -> Unit,
    onGoToClass: (String) -> Unit,
    /** 该方案下有没有课。零数据时卡片改说"去导入"，不再断言"今天没课"。 */
    hasTimetable: Boolean = true,
) {
// skin 必须定义在**早返回分支之前**：下面的 course == null 分支也用到 skin.spaceSmall，
// 若只定义在 return 之后，该分支会拿不到令牌（Unresolved reference）。
    val skin = LocalSkin.current

    if (course == null) {
        SoftCard(
            modifier = Modifier.fillMaxWidth(),
            color = colors.surfaceCard,
            onClick = onOpenTimetable
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .clip(CircleShape)
                        .background(colors.accentMain.copy(alpha = 0.10f)),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Event,
                        contentDescription = null,
                        tint = colors.accentMain,
                        modifier = Modifier.size(22.dp),
                    )
                }
                Spacer(Modifier.width(skin.spaceMd))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = if (hasTimetable) "今天没有课啦" else "还没有课表",
                        style = PocketTypography.sectionTitle,
                        fontWeight = FontWeight.Bold,
                        color = colors.textPrimary
                    )
                    Spacer(Modifier.height(skin.spaceXs))
                    Text(
                        // 零数据态：给**动作**，不给结论。
                        text = if (hasTimetable) {
                            "好好休息，也可以添加一条待办"
                        } else {
                            "导入课表后，这里会显示下一节课"
                        },
                        style = PocketTypography.body,
                        color = colors.textSecondary
                    )
                }
            }
            if (!hasTimetable) {
                Spacer(Modifier.height(skin.spaceLg))
                // 缩进一个「图标 + 间距」：按钮与上方的标题/说明左对齐，而不是
                // 挂在图标正下方。放在 Row 外面（而不是文字列里）是为了让 44dp 的
                // 日历图标仍与标题行居中，不被按钮拉低到副标题那一行。
                Row {
                    Spacer(Modifier.width(44.dp + skin.spaceMd))
                    HomePillButton(
                        label = "去导入课表",
                        container = colors.accentMain,
                        // 强调色填充 + 用户可选的浅色强调色 ⇒ 必须用 onAccentColor()
                        contentColor = onAccentColor(),
                    )
                }
            }
        }
        return
    }

    val isPure = LocalAppearanceMode.current == AppearanceMode.PURE
    val compact = LocalCampusLayoutMode.current == CampusLayoutMode.TOOLBOX
    // ⚠️ PURE 模式下这张卡是**强调色填充**（不是"品牌绿"那条路），所以卡内文字/图标
    // 必须用 onAccentColor() 而不是 Color.White：强调色可由用户换成浅色档，
    // 白字压 #7B9FF9 只有 2.57:1、压浅色预设约 1.9:1（实测见 docs/AI设计Skill使用说明.md 同级评审）。
    val hero = if (isPure) colors.accentMain else Color(0xFF2F6B5A)
    val onHero = if (isPure) onAccentColor() else Color.White
    // 渐变**只往暗端走**：PURE 的强调色可能很浅（#FFB74D 之类），往白里提会直接
    // 吃掉 onAccentColor 已经算到极限的对比度。深绿那套则上下都拉开一点明度。
    val heroTop = if (isPure) hero else Color(0xFF3A7A66)
    val heroBottom = if (isPure) lerp(hero, Color.Black, 0.22f) else Color(0xFF23503F)
    val heroBrush = Brush.linearGradient(colors = listOf(heroTop, heroBottom))
    val title = when {
        course.isOngoing -> "正在上课"
        course.dayOffset == 0 -> "下一节课"
        course.dayOffset == 1 -> "下一节课 · 明天"
        else -> "下一节课 · ${course.dayOffset} 天后"
    }
    val timeText = listOfNotNull(
        course.startTime.takeIf { it.isNotBlank() },
        course.endTime.takeIf { it.isNotBlank() }
    ).joinToString(" - ")
    val place = listOf(course.course.room, course.course.teacher)
        .filter { it.isNotBlank() }
        .joinToString(" · ")
    AppCard(
        modifier = Modifier.fillMaxWidth(),
        onClick = onOpenTimetable,
        // 主视觉卡用大圆角（而不是普通卡片的 radiusCard）：它一屏只有一张，
        // 圆角更大才撑得住"这是首页主角"的分量。
        shape = RoundedCornerShape(skin.radiusLarge),
        containerColor = Color.Transparent,
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(heroBrush)
                .drawBehind {
                    // 右上角一层极淡的光，让双色渐变有"被光扫过"的质感。
                    // 刻意压到 6%：它不能把白字的对比度拉下来。
                    val glowCenter = Offset(size.width * 1.02f, -size.height * 0.18f)
                    val glowRadius = size.width * 0.72f
                    drawCircle(
                        brush = Brush.radialGradient(
                            colors = listOf(Color.White.copy(alpha = 0.06f), Color.Transparent),
                            center = glowCenter,
                            radius = glowRadius,
                        ),
                        radius = glowRadius,
                        center = glowCenter,
                    )
                    // 右缘一枚描边圆环作背景纹理，不承载信息，所以压到 6%。
                    drawCircle(
                        color = Color.White.copy(alpha = 0.06f),
                        radius = size.width * 0.30f,
                        center = Offset(size.width * 1.02f, size.height * 0.78f),
                        style = Stroke(width = 1.dp.toPx()),
                    )
                }
        ) {
            Column(
                modifier = Modifier.padding(
                    // 紧凑模式用小内边距、常规模式用大内边距
                    if (compact) skin.cardPadding else skin.cardPaddingLarge
                )
            ) {
                // ⚠️ 这里**刻意没有**「?」说明气泡（2026-10 用户要求"改回之前的"）：
                // 加过一版 BubbleTip 挂在标题右侧，用户附了加它之前的截图要求退掉。
                // 要再加先读本函数 KDoc 里那条 —— 这张卡本身已经把"下一节课"说清楚了。
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (course.isOngoing) {
                        // 静止的 live 点，不做呼吸动画：首页要有稳定的截图基线，
                        // 无限动画会让每次截到的相位都不一样。
                        Box(contentAlignment = Alignment.Center) {
                            Box(
                                modifier = Modifier
                                    .size(14.dp)
                                    .border(1.dp, onHero.copy(alpha = 0.30f), CircleShape)
                            )
                            Box(
                                modifier = Modifier
                                    .size(6.dp)
                                    .background(onHero, CircleShape)
                            )
                        }
                        Spacer(Modifier.width(7.dp))
                    }
                    Text(
                        text = title,
                        color = onHero,
                        style = PocketTypography.body,
                        fontWeight = FontWeight.Medium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Spacer(Modifier.weight(1f))
                    // 时间从正文行挪到右上角的小胶囊：课程名因此能独占一行，
                    // 一眼先看到"上什么"，再看"几点"。
                    //
                    // ⚠️ 用**描边**而不是半透明底：底一旦掺白，压在上面的白字
                    // 对比度会从 4.33（强调色蓝的物理上限）掉到 3.5 左右。
                    Row(
                        modifier = Modifier
                            .clip(RoundedCornerShape(50))
                            .border(1.dp, onHero.copy(alpha = 0.30f), RoundedCornerShape(50))
                            .padding(horizontal = 10.dp, vertical = 5.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Schedule,
                            contentDescription = null,
                            tint = onHero,
                            modifier = Modifier.size(13.dp),
                        )
                        Spacer(Modifier.width(4.dp))
                        Text(
                            text = timeText.ifBlank { "时间待完善" },
                            color = onHero,
                            style = PocketTypography.caption,
                            fontWeight = FontWeight.SemiBold,
                        )
                    }
                }
                Spacer(Modifier.height(skin.spaceSm))
                Text(
                    text = course.course.name,
                    color = onHero,
                    style = PocketTypography.numeric,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                if (place.isNotBlank()) {
                    Spacer(Modifier.height(skin.spaceSm))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Outlined.Place,
                            contentDescription = null,
                            tint = onHero.copy(alpha = 0.72f),
                            modifier = Modifier.size(14.dp),
                        )
                        Spacer(Modifier.width(5.dp))
                        Text(
                            text = place,
                            color = onHero.copy(alpha = 0.82f),
                            style = PocketTypography.caption,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
                Spacer(Modifier.height(skin.spaceLg))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    // 主操作：实底胶囊。它和整张卡是同一个去处（课程表），
                    // 所以不单独挂 clickable —— 点它等于点卡，不会出现两个语义节点。
                    HomePillButton(
                        label = "查看课程表",
                        container = onHero,
                        contentColor = hero,
                    )
                    if (course.course.room.isNotBlank()) {
                        HomePillButton(
                            label = "去上课",
                            icon = Icons.Outlined.Place,
                            container = Color.Transparent,
                            contentColor = onHero,
                            // 同上：次要操作走描边，白字才留得住对比度。
                            borderColor = onHero.copy(alpha = 0.35f),
                            // 卡片内独立的真实操作（教室导航），用 MIUIX 按压反馈
                            // 让用户确认"点到了"，而不是穿透到整张卡。
                            onClick = { onGoToClass(course.course.room.trim()) },
                        )
                    }
                }
            }
        }
    }
}

/**
 * 首页卡片里的小胶囊：视觉高 32dp，外层**留 44dp 触区**。
 *
 * 胶囊看起来可以轻，但"去上课"不能因此难点 —— 这也是项目 44dp 热区纪律的落点。
 * 不传 [onClick] 时它只是视觉标签（整张卡本身已经是那个去处）。
 */
@Composable
private fun HomePillButton(
    label: String,
    container: Color,
    contentColor: Color,
    icon: ImageVector? = null,
    borderColor: Color? = null,
    onClick: (() -> Unit)? = null,
) {
    val outer = if (onClick != null) {
        Modifier
            .height(44.dp)
            .clip(RoundedCornerShape(50))
            .miuixPress(onClick = onClick)
    } else {
        Modifier.height(44.dp)
    }
    val border = if (borderColor != null) {
        Modifier.border(1.dp, borderColor, RoundedCornerShape(50))
    } else {
        Modifier
    }
    Box(modifier = outer, contentAlignment = Alignment.Center) {
        Row(
            modifier = Modifier
                .clip(RoundedCornerShape(50))
                .background(container)
                .then(border)
                .padding(horizontal = 14.dp, vertical = 7.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (icon != null) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = contentColor,
                    modifier = Modifier.size(13.dp),
                )
                Spacer(Modifier.width(4.dp))
            }
            Text(
                text = label,
                color = contentColor,
                style = PocketTypography.caption,
                fontWeight = FontWeight.SemiBold,
            )
        }
    }
}
