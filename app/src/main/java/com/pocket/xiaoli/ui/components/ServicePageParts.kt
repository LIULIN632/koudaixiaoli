package com.pocket.xiaoli.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.dropShadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.shadow.Shadow
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pocket.xiaoli.ui.theme.LocalDarkMode
import com.pocket.xiaoli.ui.theme.LocalPocketXiaoliColors
import com.pocket.xiaoli.ui.theme.LocalSkin
import com.pocket.xiaoli.ui.theme.ServiceAccent
import com.pocket.xiaoli.ui.theme.onAccentColor
import com.pocket.xiaoli.ui.theme.serviceAccentColor

/**
 * 服务二级页（从服务页点进去的那些页）的**共用视觉语言**。
 *
 * ## 为什么值得单开一层
 *
 * 服务页本身（彩色分类卡 + 渐变）已经是一套很"重"的视觉，点进去却统一
 * 落到「白顶栏 + 灰底 + 12dp 圆角卡」—— 观感像从彩页翻进了一张表单，
 * 而且 14 个二级页各写一份，任何一次统一调整都要改 14 遍。
 *
 * 这里只放**反复出现且必须一致**的四样东西：
 * - [ServicePageTopBar] 顶栏：页底同色（不再是压在上面的一块白板）+ 圆形返回
 * - [ServiceHeroCard] 主视觉：用该服务的语义色做深色渐变，白字
 * - [ServiceSectionLabel] 区块标题：强调色竖条 + 16sp 粗体
 * - [ServiceEmptyCard] 空态：白卡 + 强调色图标 + 一句"下一步做什么"
 *
 * ## 着色纪律
 *
 * 主视觉卡用 **serviceAccentColor**（与服务网格里那张卡同源的语义色），
 * 所以"点的是绿卡，进去还是绿调"—— 页面身份是连续的，不靠另配一套色。
 * 强调色只做**身份**（图标底、竖条、描边）；**主操作按钮仍用 accentMain**，
 * 这样颜色数量不会重新失控。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ServicePageTopBar(
    title: String,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    actions: @Composable RowScope.() -> Unit = {},
) {
    val colors = LocalPocketXiaoliColors.current
    TopAppBar(
        modifier = modifier,
        title = {
            Text(
                text = title,
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                color = colors.textPrimary,
            )
        },
        navigationIcon = {
            TopBarCircleButton(
                onClick = onBack,
                icon = Icons.AutoMirrored.Filled.ArrowBack,
                label = "返回",
            )
        },
        actions = actions,
        colors = TopAppBarDefaults.topAppBarColors(
            // 顶栏与页底同色：标题区不再是压在浅灰页面上的一块白色横条。
            containerColor = colors.surfaceBase,
            titleContentColor = colors.textPrimary,
            navigationIconContentColor = colors.textSecondary,
            actionIconContentColor = colors.textSecondary,
        ),
    )
}

/**
 * 服务页的**主视觉卡**：该服务语义色的深色渐变 + 白字。
 *
 * ## 为什么必须"往黑里压"
 *
 * 分类色是给**图标与浅衬底**设计的（浅色态 V 0.60–0.72），直接拿来当卡片底时，
 * 最亮的一档（缴费的黄）与白字只有 **2.9:1**。所以这里统一把分类色压到
 * **相对亮度 ≤ 0.12**（≈ 白字 6.2:1）再用 —— 只降明度、不动色相与饱和度，
 * 观感仍是浓的宝石色，而不是加了一层灰。
 *
 * 压多少由 [deepServiceHeroTone] 逐色计算，不是写死一个混合比例：
 * 深色主题的分类色比浅色态更亮（V 0.82–0.96），写死比例会在这里翻车。
 *
 * 卡内的**次要文字请用 `Color.White.copy(alpha = 0.9f)`**，
 * 不要用 0.6–0.7：那样压在深底上只有 ~3:1。
 *
 * ## 大数字 + 小单位：用 [ServiceHeroCount]，不要 `Bottom` + padding 补差
 *
 * hero 里「34 条」「42 分」这类组合，各页**曾经**各写一处
 * `Row(Alignment.Bottom) + 小字 padding(bottom = 7.dp / 5.dp / 4.dp)` —— 三个不同的魔数。
 * `Bottom` 对齐的是**行框底边**，大小字号的行框底部留白不同，所以只能手调；
 * 系统字号一放大，数字变高而 7dp 不变，错位立刻回来。
 * 现在统一走 [ServiceHeroCount]：数字与单位合成**一个 Text**，同一条基线是文字排版本身
 * 保证的，不需要对齐规则、不需要偏移量。**不要退回 `Bottom` + 偏移量。**
 */
@Composable
fun ServiceHeroCard(
    accent: ServiceAccent,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    val skin = LocalSkin.current
    val tint = serviceAccentColor(accent)
    val deep = remember(tint) { deepServiceHeroTone(tint) }
    val top = remember(deep) { lerp(deep, Color.White, 0.06f) }
    val bottom = remember(deep) { lerp(deep, Color.Black, 0.18f) }
    val shape = RoundedCornerShape(skin.radiusLarge)
    val shadowModifier = if (skin.shadowBlur > 0.dp) {
        Modifier.dropShadow(
            shape = shape,
            // 彩色光晕而非黑投影：白底上的黑投影会发灰，正是"灰蒙蒙"的来源之一。
            shadow = Shadow(
                radius = skin.shadowBlur,
                spread = 0.dp,
                color = deep.copy(alpha = 0.30f),
                offset = DpOffset(0.dp, 6.dp),
            ),
        )
    } else {
        Modifier
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .then(shadowModifier)
            .clip(shape)
            .background(Brush.linearGradient(listOf(top, bottom)))
            .drawBehind {
                // 右上角一层很淡的白光，让平涂渐变有"被光扫过"的体积感。
                val glowRadius = size.width * 0.80f
                val glowCenter = Offset(size.width * 1.02f, -size.height * 0.10f)
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(Color.White.copy(alpha = 0.14f), Color.Transparent),
                        center = glowCenter,
                        radius = glowRadius,
                    ),
                    radius = glowRadius,
                    center = glowCenter,
                )
                // 右下角一枚描边圆环作背景纹理，不承载信息。
                drawCircle(
                    color = Color.White.copy(alpha = 0.10f),
                    radius = size.width * 0.32f,
                    center = Offset(size.width * 0.94f, size.height * 1.10f),
                    style = Stroke(width = 1.2.dp.toPx()),
                )
            }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(skin.cardPaddingLarge),
            content = content,
        )
    }
}

/**
 * 主视觉卡上的「大数字 + 小单位」（`12 条` / `86.4 分`）。
 *
 * ## 为什么是**一个** Text
 *
 * 数字与单位如果写成两个 Text，就只能靠 `Row` 的垂直对齐规则摆位：
 * `Alignment.Bottom` 齐的是**行框底边**，而 34sp 与 13sp 两档行框的底部留白不一样，
 * 于是各页手调了一个 `padding(bottom = 7.dp / 5.dp / 4.dp)` 的魔数 ——
 * 那个数只在"这一组字号"下成立，系统字体放大或有人改字号就立刻错位。
 * 而 Compose 1.12 **没有**基线对齐用的 `Alignment`（`TextBaseline` / `LineBaseline`
 * 与 `BaselineAlignment` 都不在这套 API 里），跨两个 Text 想齐基线只剩拿字体度量硬算。
 *
 * 合成一段带两种 `SpanStyle` 的文字之后，基线是文字排版自己保证的：
 * 单位自动坐在数字的基线上，随字号缩放，无需对齐规则、无需偏移量。
 *
 * 行高显式给到 `数字字号 × 1.25`：主题里 `LocalTextStyle` 继承来的 22sp 行高
 * 小于 34/42sp 的字面高度，不写出来这行会被压矮、和上下邻居贴在一起。
 */
@Composable
fun ServiceHeroCount(
    number: String,
    unit: String,
    modifier: Modifier = Modifier,
    numberSize: TextUnit = 34.sp,
) {
    Text(
        text = buildAnnotatedString {
            withStyle(SpanStyle(fontSize = numberSize, fontWeight = FontWeight.ExtraBold, color = Color.White)) {
                append(number)
            }
            withStyle(SpanStyle(fontSize = 13.sp, color = Color.White.copy(alpha = 0.9f))) {
                // 不换行空格：数字与单位之间要留缝，但不能成为断行点
                append('\u00A0')
                append(unit)
            }
        },
        lineHeight = numberSize * 1.25f,
        modifier = modifier,
    )
}

/** 主视觉卡右上角的图标衬底（白 16% 圆 + 20% 圆角图标）。 */
@Composable
fun ServiceHeroBadge(
    icon: ImageVector,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .size(46.dp)
            .clip(CircleShape)
            .background(Color.White.copy(alpha = 0.16f)),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = Color.White,
            modifier = Modifier.size(23.dp),
        )
    }
}

/** 主视觉卡底部的数据胶囊（白 14% 底 + 12sp 白字）。 */
@Composable
fun ServiceHeroChip(
    text: String,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(50))
            .background(Color.White.copy(alpha = 0.14f))
            .padding(horizontal = 10.dp, vertical = 5.dp),
    ) {
        Text(text = text, fontSize = 12.sp, color = Color.White)
    }
}

/**
 * 页面内的区块标题：强调色竖条 + 16sp 粗体 + 可选右侧附加内容。
 *
 * 服务二级页原先没有区块标题，列表直接贴着操作行开始 —— 一屏里
 * "筛选/操作/统计/列表"混在同一层，用户读不出结构。
 */
@Composable
fun ServiceSectionLabel(
    title: String,
    accent: ServiceAccent,
    modifier: Modifier = Modifier,
    trailing: @Composable (() -> Unit)? = null,
) {
    val skin = LocalSkin.current
    val colors = LocalPocketXiaoliColors.current
    val tint = serviceAccentColor(accent)
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .width(4.dp)
                .height(16.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(tint),
        )
        Spacer(Modifier.width(skin.spaceSm))
        Text(
            text = title,
            fontSize = 16.sp,
            fontWeight = FontWeight.Bold,
            color = colors.textPrimary,
            modifier = Modifier.weight(1f),
        )
        trailing?.invoke()
    }
}

/**
 * 服务页空态：白卡 + 语义色图标 + 标题/说明 + 可选主操作。
 *
 * 原先的空态是"屏幕正中一行 30% 透明灰字"—— 既不像提示，也不告诉用户
 * 下一步能做什么。空态是**页面的一种正式状态**，不是占位符。
 */
@Composable
fun ServiceEmptyCard(
    icon: ImageVector,
    title: String,
    message: String,
    accent: ServiceAccent,
    modifier: Modifier = Modifier,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
) {
    val skin = LocalSkin.current
    val colors = LocalPocketXiaoliColors.current
    val isDark = LocalDarkMode.current
    val tint = serviceAccentColor(accent)

    AppCard(modifier = modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = skin.spaceXl, vertical = skin.spaceXxl),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Box(
                modifier = Modifier
                    .size(60.dp)
                    .clip(CircleShape)
                    .background(tint.copy(alpha = if (isDark) 0.26f else 0.14f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = tint,
                    modifier = Modifier.size(28.dp),
                )
            }
            Spacer(Modifier.height(skin.spaceLg))
            Text(
                text = title,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                color = colors.textPrimary,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(skin.spaceXs))
            Text(
                text = message,
                fontSize = 13.sp,
                color = colors.textSecondary,
                textAlign = TextAlign.Center,
                lineHeight = 20.sp,
            )
            if (actionLabel != null && onAction != null) {
                Spacer(Modifier.height(skin.spaceLg))
                Button(
                    onClick = onAction,
                    shape = RoundedCornerShape(skin.radiusCard),
                    contentPadding = PaddingValues(
                        horizontal = skin.spaceXl,
                        vertical = skin.spaceSm,
                    ),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = colors.accentMain,
                        contentColor = onAccentColor(),
                    ),
                ) {
                    Text(actionLabel, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

/**
 * 把分类色压到"白字可读"的明度端，**但先把纯度抬回来**。
 *
 * ## 为什么不是一路混黑就完事
 *
 * 旧写法只与黑混合到亮度 0.12。蓝色、紫色这样压出来是宝石色，
 * 但橙色（`LIFE` 在当前主题下落在 20° 附近）这样压出来是**灰泥** ——
 * 混黑的同时把饱和度也一起拉没了，"今天吃什么"那张 hero 就是这个观感，
 * 也正是用户说的"灰蒙蒙"。
 *
 * 所以改成 HSV 里两步走：**饱和度先钉到 ≥0.86，再逐档降明度**。
 * 色相一点不动，各服务之间的色彩差异原样保留，只是深而不浊。
 *
 * 目标亮度 0.16（旧 0.12）：抬饱和后同色相更暗，0.16 与白字仍有约 5.5:1，
 * 够卡内 12–14sp 小字用；再亮就会开始牺牲"深色底 + 白字"的那层对比。
 */
private fun deepServiceHeroTone(tint: Color): Color {
    val hsv = FloatArray(3).also { android.graphics.Color.colorToHSV(tint.toArgb(), it) }
    hsv[1] = hsv[1].coerceAtLeast(0.86f)
    var value = hsv[2]
    var mixed = Color(android.graphics.Color.HSVToColor(hsv))
    while (mixed.luminance() > 0.16f && value > 0.16f) {
        value -= 0.04f
        hsv[2] = value
        mixed = Color(android.graphics.Color.HSVToColor(hsv))
    }
    return mixed
}
