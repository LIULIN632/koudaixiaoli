package com.pocket.xiaoli.ui.profile

import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.LocationOn
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.FlashOn
import androidx.compose.material.icons.filled.HotTub
import androidx.compose.material.icons.filled.LocalShipping
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.outlined.Timer
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.FlashOn
import androidx.compose.material.icons.outlined.HotTub
import androidx.compose.material.icons.outlined.LocalLaundryService
import androidx.compose.material.icons.outlined.LocalShipping
import androidx.compose.material.icons.outlined.Key
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pocket.xiaoli.data.CampusInfo
import com.pocket.xiaoli.data.jwTypeLabel
import com.pocket.xiaoli.ui.components.AppCard
import com.pocket.xiaoli.ui.theme.CampusLayoutMode
import com.pocket.xiaoli.ui.theme.LocalCampusLayoutMode
import com.pocket.xiaoli.ui.theme.LocalDarkMode
import com.pocket.xiaoli.ui.theme.LocalPocketXiaoliColors
import com.pocket.xiaoli.ui.theme.ServiceAccent
import com.pocket.xiaoli.ui.theme.serviceAccentColor
import com.pocket.xiaoli.ui.theme.serviceAccentContainer
import com.pocket.xiaoli.ui.theme.LocalSkin
import com.pocket.xiaoli.ui.theme.currentIconPresentation
import com.pocket.xiaoli.ui.theme.LocalProfileRowPadding
import com.pocket.xiaoli.ui.theme.rememberSinkIndication

data class SettingsItemData(
    val icon: ImageVector,

    /**
     * 面性（`Icons.Filled.*`）变体，供鲜活皮肤的 `IconStyle.FILLED` 使用；
     * 极简皮肤仍用 [icon]（线性）。
     *
     * ⚠️ 默认值 = [icon]，所以未登记 Filled 变体的条目**行为完全不变**。
     *
     * 顺带说明为什么现在要把两者分开：改造前这组条目的图标是**混着写的**
     *（3 个直接用了 `Icons.Filled.*`、1 个用 `Icons.Outlined.*`），
     * 于是"线性还是面性"根本不是设计决定，而是谁写这行时的随手选择。
     * 拆成 [icon] + [iconFilled] 之后，选哪一版由 `IconStyle` 统一裁决。
     */
    val iconFilled: ImageVector = icon,

    val title: String,
    val subtitle: String? = null,
    /** 语义分类；非空时优先，颜色从当前主题派生（见 ServiceAccent），不再硬编码色对。 */
    val accent: ServiceAccent? = null,
    // 迁移期保留：未填 accent 的条目回退到旧硬编码色，保证任何时刻都可编译可运行
    val iconTint: Color = Color.Unspecified,
    val iconBackground: Color = Color.Unspecified,
    val onClick: () -> Unit
)


@Composable
private fun HeroChip(text: String, onClick: (() -> Unit)? = null) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(Color.White.copy(alpha = 0.18f))
            .then(if (onClick != null) Modifier.clickable(interactionSource = remember { MutableInteractionSource() }, indication = rememberSinkIndication(), onClick = onClick) else Modifier)
            .padding(horizontal = 10.dp, vertical = 4.dp)
    ) {
        Text(text = text, color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Medium)
    }
}


private fun electricityHint(balance: String?, loading: Boolean): String {
    if (loading) return "查询中…"
    val value = balance?.trim().orEmpty()
    if (value.isBlank()) return "点击查询余额"
    return if (value.startsWith("-")) "-¥" + value.removePrefix("-") else "¥" + value
}

@Composable
fun ProfileStatsGrid(
    electricityBalance: String?,
    electricityLoading: Boolean,
    /** 洗澡格子的副标题：已登录显示学校/账户，未登录提示去登录 */
    showerHint: String = "热水 · 淋浴",
    /** 专注计时格子的副标题 —— 今日专注时长（见 `focusTileHint`） */
    focusHint: String = "今天还没开始",
    onElectricity: () -> Unit,
    onFocus: () -> Unit,
    onShower: () -> Unit,
    onExpress: () -> Unit,
) {
    val skin = LocalSkin.current
    Column(verticalArrangement = Arrangement.spacedBy(skin.spaceMd)) {
        Row(horizontalArrangement = Arrangement.spacedBy(skin.spaceMd)) {
            ServiceTile(
                modifier = Modifier.weight(1f),
                icon = Icons.Outlined.FlashOn,
                iconFilled = Icons.Filled.FlashOn,
                title = "宿舍电费",
                hint = electricityHint(electricityBalance, electricityLoading),
                serviceAccent = ServiceAccent.PAYMENT,
                onClick = onElectricity
            )
            // 这一格 2026-10 从「校园余额」换成「专注计时」（用户要求）。
            //
            // 换掉不等于校园卡功能下线：它仍在服务页（`TreasureBoxScreen`）、绑定信息页
            // 与 `ProfileDialogHost.onOpenCampusCardPage` 三处可达。这里让位的原因是
            // "我的"页四格的选品标准是**每天都会点**，而查余额是低频动作。
            ServiceTile(
                modifier = Modifier.weight(1f),
                icon = Icons.Outlined.Timer,
                iconFilled = Icons.Filled.Timer,
                title = "专注计时",
                hint = focusHint,
                serviceAccent = ServiceAccent.STUDY,
                onClick = onFocus
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(skin.spaceMd)) {
            // 这一格以前是「洗衣剩余 → 查看洗衣机」。
            //
            // 换掉的原因不是审美：洗衣那两家（U净 / 浣熊先生）的服务只跑在
            // 微信、支付宝宿主里，第三方 App 拿不到登录态，连"还剩几分钟"都查不出来
            // —— 点进去只能弹个说明框。而洗澡（趣智校园）是**能真的连上**的（见 ui/shower/）。
            // 结论与证据记在 docs/ServiceAudit.md。
            ServiceTile(
                modifier = Modifier.weight(1f),
                icon = Icons.Outlined.HotTub,
                iconFilled = Icons.Filled.HotTub,
                title = "洗澡",
                hint = showerHint,
                serviceAccent = ServiceAccent.LIFE,
                onClick = onShower
            )
            ServiceTile(
                modifier = Modifier.weight(1f),
                icon = Icons.Outlined.LocalShipping,
                iconFilled = Icons.Filled.LocalShipping,
                title = "快递取件码",
                hint = "淘宝 / 拼多多",
                serviceAccent = ServiceAccent.LIFE,
                onClick = onExpress
            )
        }
    }
}

@Composable
private fun ServiceTile(
    icon: ImageVector,
    title: String,
    hint: String,
    /** 语义分类；非空时颜色从主题派生，优先于下面的硬编码色。 */
    serviceAccent: ServiceAccent? = null,
    background: Color = Color.Unspecified,
    accent: Color = Color.Unspecified,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    iconFilled: ImageVector = icon,
) {
    val skin = LocalSkin.current
    val colors = LocalPocketXiaoliColors.current
    // 图标策略：皮肤上限 × 用户用色强度（见 IconPresentation.resolveIcons）。
    // 局部变量**不叫** icon —— 那样会遮蔽上面的 icon 参数。
    val iconPresentation = currentIconPresentation()
    // 单色态改用中性卡底 + 统一强调色（与 ServiceCard 同一套规则）
    val cardBackground = if (!iconPresentation.colorful) colors.surfaceCard
    else serviceAccent?.let { serviceAccentContainer(it) } ?: background
    val cardAccent = if (iconPresentation.colorful) serviceAccent?.let { serviceAccentColor(it) } ?: accent
    else colors.accentMain
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(skin.radiusLarge))
            .background(cardBackground)
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = rememberSinkIndication(), onClick = onClick)
            .padding(skin.spaceLg)
    ) {
        Icon(
            imageVector = if (iconPresentation.filled) iconFilled else icon,
            contentDescription = null,
            tint = cardAccent,
            modifier = Modifier.size(22.dp)
        )
        Spacer(Modifier.height(skin.spaceSm))
        Text(
            text = title,
            color = colors.textPrimary,
            fontSize = 15.sp,
            fontWeight = FontWeight.Bold
        )
        Spacer(Modifier.height(skin.spaceMicro))
        Text(
            text = hint,
            color = colors.textSecondary,
            fontSize = 12.sp
        )
    }
}

@Composable
fun SettingsGroupCard(
    title: String,
    items: List<SettingsItemData>
) {
    val skin = LocalSkin.current
    val colors = LocalPocketXiaoliColors.current
    // 图标策略：皮肤上限 × 用户用色强度（见 IconPresentation.resolveIcons）
    val iconPresentation = currentIconPresentation()
    val compact = LocalCampusLayoutMode.current == CampusLayoutMode.TOOLBOX
    Column {
        // 标题与设置页共用同一个组件：改造前这里是 20sp 粗体 + 主色文字，
        // 而设置页是 14sp 弱化色 —— 同一类东西两种长相。
        SettingsSectionHeader(title)
        // 与首页 / 设置页同一张卡：皮肤圆角 + VIVID 的弥散阴影。
        // 原来这里是 M3 Card + 0 elevation，白底白页，在 VIVID 下和首页的卡不是一套东西。
        AppCard {
            Column {
                items.forEachIndexed { index, item ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = rememberSinkIndication(), onClick = item.onClick)
                            // 垂直内边距跟「外观 → 我的 → 列表密度」（标准 14dp）。
                            // ⚠️ 工具箱布局（compact）仍然钉死 10dp：那是"界面风格"自己的
                            // 紧凑语义，与用户选的列表密度是两回事，不该被后者放大。
                            .padding(
                                horizontal = if (compact) 14.dp else 16.dp,
                                vertical = if (compact) 10.dp else LocalProfileRowPadding.current,
                            ),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                // 与设置页同一套图标语言：圆角方砖而不是圆点。
                                // 圆的读起来像"状态灯"，方砖才像"入口"；两页挨着切换时形状要一致。
                                .size(if (compact) 32.dp else 36.dp)
                                .clip(RoundedCornerShape(skin.radiusChip))
                                .background(
                                    if (!iconPresentation.colorful) colors.surfaceHighlight
                                    else item.accent?.let { serviceAccentContainer(it) } ?: item.iconBackground
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = if (iconPresentation.filled) item.iconFilled else item.icon,
                                contentDescription = item.title,
                                tint = if (iconPresentation.colorful) item.accent?.let { serviceAccentColor(it) } ?: item.iconTint
                                else colors.accentMain,
                                modifier = Modifier.size(if (compact) 17.dp else 19.dp)
                            )
                        }
                        Spacer(Modifier.width(if (compact) 10.dp else 12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = item.title,
                                color = colors.textPrimary,
                                fontSize = 15.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                            if (!item.subtitle.isNullOrBlank()) {
                                Spacer(Modifier.height(skin.spaceMicro))
                                Text(
                                    text = item.subtitle,
                                    color = colors.textSecondary,
                                    fontSize = 11.sp,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                            contentDescription = null,
                            tint = colors.textSecondary,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                    if (index != items.lastIndex) {
                        HorizontalDivider(
                            // 分隔线从头像块右侧起笔：16(边距) + 36(块) + 12(间隔) = 64
                            modifier = Modifier.padding(start = if (compact) 56.dp else 64.dp, end = 16.dp),
                            color = colors.borderDivider
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SoftSectionCard(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit
) {
    val skin = LocalSkin.current
    val colors = LocalPocketXiaoliColors.current
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(skin.radiusLarge),
        colors = CardDefaults.cardColors(containerColor = colors.surfaceCard),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column(
            modifier = Modifier.padding(skin.spaceWide),
            content = content
        )
    }
}


@Composable
fun CampusCard(
    campus: CampusInfo,
    onSelectCampus: () -> Unit
) {
    val skin = LocalSkin.current
    val colors = LocalPocketXiaoliColors.current
    val isDark = LocalDarkMode.current
    // 图标策略：皮肤上限 × 用户用色强度（见 IconPresentation.resolveIcons）
    val iconPresentation = currentIconPresentation()
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(skin.radiusLarge),
        colors = CardDefaults.cardColors(containerColor = colors.surfaceCard),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(interactionSource = remember { MutableInteractionSource() }, indication = rememberSinkIndication(), onClick = onSelectCampus)
                .padding(skin.spaceWide),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(46.dp)
                    .clip(CircleShape)
                    .background(
                        // 原式把 isPure 与 isDark 拆成两个分支却给同一个结果，
                        // 等价于 `isPure || isDark`。这里换成"皮肤不允许彩色"，
                        // 于是极简皮肤同样收成中性底 —— 与其它卡片一致。
                        if (isDark || !iconPresentation.colorful) colors.surfaceHighlight
                        else Color(0xFFDCE7FF)
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Outlined.LocationOn,
                    contentDescription = null,
                    // ⚠️ 原式是 `if (isPure) colors.accentMain else colors.accentMain` ——
                    // 两个分支**完全相同**，是个死条件：无论用色强度如何都取强调色。
                    // 保留实际语义（校园卡图标始终用强调色），去掉这个伪分支；
                    // 换成 `if (colorful) accent else accent` 只会把死条件搬个地方。
                    tint = colors.accentMain
                )
            }
            Spacer(Modifier.width(skin.spaceCompact))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "当前校区",
                    color = colors.textPrimary,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold
                )
                Spacer(Modifier.height(skin.spaceMicro))
                Text(
                    text = campus.name + if (campus.underConstruction) "（建设中）" else "",
                    color = colors.textSecondary,
                    fontSize = 12.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                val laundry = campus.laundryProvider
                if (!laundry.isNullOrBlank()) {
                    Spacer(Modifier.height(skin.spaceMicro))
                    Text(
                        text = "洗衣房：$laundry",
                        color = colors.textTertiary,
                        fontSize = 11.sp
                    )
                }
                if (!campus.jwBaseUrl.isNullOrBlank()) {
                    Spacer(Modifier.height(skin.spaceMicro))
                    Text(
                        text = "教务：${campus.jwTypeLabel}",
                        color = colors.textTertiary,
                        fontSize = 11.sp
                    )
                }
            }
            Spacer(Modifier.width(skin.spaceSm))
            Text(
                text = "切换",
                color = colors.accentMain,
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium
            )
        }
    }
}

@Composable
fun CampusSelectDialog(
    campuses: List<CampusInfo>,
    selectedId: String,
    onSelect: (CampusInfo) -> Unit,
    onDismiss: () -> Unit
) {
    val skin = LocalSkin.current
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("选择校区") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(skin.spaceSm)) {
                Text(
                    text = "不同校区的校园服务商可能不同，例如洗衣房。",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 12.sp
                )
                campuses.forEach { campus ->
                    val selected = campus.id == selectedId
                    val enabled = !campus.underConstruction
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(skin.radiusCard))
                            .background(
                                when {
                                    selected -> MaterialTheme.colorScheme.primaryContainer
                                    else -> MaterialTheme.colorScheme.surfaceVariant
                                }
                            )
                            .clickable(enabled = enabled) { onSelect(campus) }
                            .padding(horizontal = skin.spaceCompact, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = campus.name,
                                color = MaterialTheme.colorScheme.onSurface,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Medium
                            )
                            Spacer(Modifier.height(skin.spaceMicro))
                            Text(
                                text = if (campus.underConstruction) {
                                    "建设中，暂不可用"
                                } else {
                                    buildString {
                                        append("洗衣房：${campus.laundryProvider ?: "待配置"}")
                                        if (!campus.jwBaseUrl.isNullOrBlank()) {
                                            append(" · ${campus.jwTypeLabel}")
                                        }
                                    }
                                },
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                fontSize = 11.sp
                            )
                        }
                        if (campus.underConstruction) {
                            Text(
                                text = "建设中",
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Medium
                            )
                        } else if (selected) {
                            Icon(
                                imageVector = Icons.Outlined.CheckCircle,
                                contentDescription = "已选择",
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("完成")
            }
        }
    )
}
