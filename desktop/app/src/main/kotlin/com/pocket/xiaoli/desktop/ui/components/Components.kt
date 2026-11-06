package com.pocket.xiaoli.desktop.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Icon
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pocket.xiaoli.desktop.ui.icons.PocketIcons
import com.pocket.xiaoli.desktop.ui.theme.LocalDarkMode
import com.pocket.xiaoli.desktop.ui.theme.PocketTypography
import com.pocket.xiaoli.desktop.ui.theme.Radii
import com.pocket.xiaoli.desktop.ui.theme.accentColor
import com.pocket.xiaoli.desktop.ui.theme.accentSoftColor
import com.pocket.xiaoli.desktop.ui.theme.onAccentColor
import com.pocket.xiaoli.desktop.ui.theme.pocketColors

// ═══ 轻量通知（代替 Snackbar：桌面端只需要"说一句"）═══
object Notice {
    var message by mutableStateOf<String?>(null)
        private set

    fun show(text: String) {
        message = text
    }

    fun clear() {
        message = null
    }
}

/** 卡片：白底 + 弥散阴影（深色下改 1dp 描边，与 Vivid 皮肤一致）。 */
@Composable
fun PocketCard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    padding: Dp = 18.dp,
    shape: Shape = RoundedCornerShape(Radii.card),
    content: @Composable ColumnScope.() -> Unit,
) {
    val colors = pocketColors()
    val dark = LocalDarkMode.current
    val interaction = remember { MutableInteractionSource() }
    val hovered by interaction.collectIsHoveredAsState()
    val elevated = modifier
        .hoverable(interaction)
        .then(
            if (!dark) Modifier.shadow(
                elevation = if (hovered) 12.dp else 6.dp,
                shape = shape,
                clip = false,
                ambientColor = Color(0x14000000),
                spotColor = Color(0x1F000000),
            ) else Modifier
        )
        .clip(shape)
        .background(colors.surfaceCard)
        .then(if (dark) Modifier.border(1.dp, colors.borderDivider, shape) else Modifier)
        .then(
            if (onClick != null) {
                Modifier.clickable(onClick = onClick)
            } else {
                Modifier
            }
        )
    Column(modifier = elevated.padding(padding), content = content)
}

@Composable
fun SectionHeader(
    title: String,
    subtitle: String? = null,
    modifier: Modifier = Modifier,
    trailing: @Composable RowScope.() -> Unit = {},
) {
    val colors = pocketColors()
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = PocketTypography.sectionTitle, color = colors.textPrimary)
            if (subtitle != null) {
                Spacer(Modifier.height(2.dp))
                Text(subtitle, style = PocketTypography.caption, color = colors.textTertiary)
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically, content = trailing)
    }
}

@Composable
fun ScreenTitle(
    title: String,
    subtitle: String? = null,
    modifier: Modifier = Modifier,
    actions: @Composable RowScope.() -> Unit = {},
) {
    val colors = pocketColors()
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = PocketTypography.screenTitle, color = colors.textPrimary)
            if (subtitle != null) {
                Spacer(Modifier.height(4.dp))
                Text(subtitle, style = PocketTypography.caption, color = colors.textTertiary)
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically, content = actions)
    }
}

@Composable
fun PrimaryButton(
    text: String,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    enabled: Boolean = true,
    fillWidth: Boolean = false,
    onClick: () -> Unit,
) {
    val accent = accentColor()
    val onAccent = onAccentColor()
    val shape = RoundedCornerShape(Radii.chip)
    val press = rememberPressState(enabled)
    val surface = pressSurface(if (enabled) accent else accent.copy(alpha = 0.35f), press.hovered, press.pressed)
    val bg by animateColorAsState(surface, animationSpec = tween(120), label = "primary-bg")
    Row(
        modifier = modifier
            .then(if (fillWidth) Modifier.fillMaxWidth() else Modifier)
            .scale(press.scale)
            .height(38.dp)
            .clip(shape)
            .background(bg)
            .hoverable(press.source, enabled)
            .clickable(interactionSource = press.source, indication = null, enabled = enabled, onClick = onClick)
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        if (icon != null) {
            Icon(icon, contentDescription = null, tint = onAccent, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(6.dp))
        }
        Text(text, style = PocketTypography.body.copy(fontWeight = FontWeight.Bold), color = onAccent)
    }
}

@Composable
fun SecondaryButton(
    text: String,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    fillWidth: Boolean = false,
    onClick: () -> Unit,
) {
    val colors = pocketColors()
    val accent = accentColor()
    val shape = RoundedCornerShape(Radii.chip)
    val press = rememberPressState()
    val bg by animateColorAsState(pressSurface(colors.surfaceCard, press.hovered, press.pressed), animationSpec = tween(120), label = "secondary-bg")
    Row(
        modifier = modifier
            .then(if (fillWidth) Modifier.fillMaxWidth() else Modifier)
            .scale(press.scale)
            .height(38.dp)
            .clip(shape)
            .background(bg)
            .border(pressBorder(press, accent), shape)
            .hoverable(press.source)
            .clickable(interactionSource = press.source, indication = null, onClick = onClick)
            .padding(horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        if (icon != null) {
            Icon(icon, contentDescription = null, tint = accent, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(6.dp))
        }
        Text(text, style = PocketTypography.body, color = colors.textSecondary)
    }
}

/** 危险操作按钮（删除 / 重置）：红底白字，只在二次确认里用。 */
@Composable
fun DangerButton(
    text: String,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    val colors = pocketColors()
    val press = rememberPressState()
    val bg by animateColorAsState(pressSurface(colors.danger, press.hovered, press.pressed), animationSpec = tween(120), label = "danger-bg")
    Row(
        modifier = modifier
            .scale(press.scale)
            .height(38.dp)
            .clip(RoundedCornerShape(Radii.chip))
            .background(bg)
            .hoverable(press.source)
            .clickable(interactionSource = press.source, indication = null, onClick = onClick)
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        Text(text, style = PocketTypography.body, color = Color.White)
    }
}

@Composable
fun GhostIconButton(
    icon: ImageVector,
    contentDescription: String? = null,
    tint: Color? = null,
    size: Dp = 34.dp,
    onClick: () -> Unit,
) {
    val colors = pocketColors()
    val press = rememberPressState()
    val bg by animateColorAsState(
        pressSurface(colors.surfaceAlt, press.hovered, press.pressed).let {
            if (press.hovered || press.pressed) it else Color.Transparent
        },
        animationSpec = tween(120),
        label = "ghost-bg",
    )
    Box(
        modifier = Modifier
            .scale(press.scale)
            .size(size)
            .clip(RoundedCornerShape(Radii.chip))
            .background(bg)
            .hoverable(press.source)
            .clickable(interactionSource = press.source, indication = null, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = contentDescription, tint = tint ?: colors.textSecondary, modifier = Modifier.size(18.dp))
    }
}

@Composable
fun TagChip(
    text: String,
    modifier: Modifier = Modifier,
    color: Color? = null,
    background: Color? = null,
    onClick: (() -> Unit)? = null,
) {
    val colors = pocketColors()
    val accent = accentColor()
    val clickable = onClick != null
    val press = rememberPressState(clickable)
    val fg = color ?: colors.textSecondary
    val bg by animateColorAsState(
        if (clickable) pressSurface(background ?: colors.surfaceAlt, press.hovered, press.pressed)
        else background ?: colors.surfaceAlt,
        animationSpec = tween(120),
        label = "chip-bg",
    )
    Row(
        modifier = modifier
            .scale(press.scale)
            .clip(RoundedCornerShape(Radii.chip))
            .background(bg)
            .then(
                if (clickable) {
                    Modifier
                        .border(pressBorder(press, accent), RoundedCornerShape(Radii.chip))
                        .hoverable(press.source)
                        .clickable(interactionSource = press.source, indication = null, onClick = onClick)
                } else Modifier
            )
            .padding(horizontal = 8.dp, vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(text, style = PocketTypography.label.copy(fontSize = 11.sp), color = fg, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
fun IconBadge(
    icon: ImageVector,
    tint: Color,
    background: Color,
    size: Dp = 36.dp,
) {
    Box(
        modifier = Modifier
            .size(size)
            .clip(RoundedCornerShape(12.dp))
            .background(background),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(size * 0.5f))
    }
}

@Composable
fun StatTile(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    hint: String? = null,
    accent: Color? = null,
) {
    val colors = pocketColors()
    Column(modifier) {
        Text(label, style = PocketTypography.caption, color = colors.textTertiary)
        Spacer(Modifier.height(4.dp))
        Row(verticalAlignment = Alignment.Bottom) {
            Text(value, style = PocketTypography.numeric, color = accent ?: colors.textPrimary)
            if (hint != null) {
                Spacer(Modifier.width(4.dp))
                Text(hint, style = PocketTypography.caption, color = colors.textTertiary, modifier = Modifier.padding(bottom = 4.dp))
            }
        }
    }
}

/** 设置行：图标 + 标题/说明 + 右侧控件。整行可点。 */
@Composable
fun SettingRow(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    icon: ImageVector? = null,
    iconTint: Color? = null,
    trailing: @Composable RowScope.() -> Unit = {},
    onClick: (() -> Unit)? = null,
) {
    val colors = pocketColors()
    val interaction = remember { MutableInteractionSource() }
    val hovered by interaction.collectIsHoveredAsState()
    Row(
        modifier = modifier
            .fillMaxWidth()
            .hoverable(interaction)
            .clip(RoundedCornerShape(12.dp))
            .background(if (hovered && onClick != null) colors.surfaceAlt else Color.Transparent)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(horizontal = 8.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) {
            // 图标底座原来是灰底灰图，整页看着发闷；统一换成主题色的软底
            IconBadge(icon, iconTint ?: accentColor(), accentSoftColor(), 34.dp)
            Spacer(Modifier.width(12.dp))
        }
        Column(Modifier.weight(1f)) {
            Text(title, style = PocketTypography.body, color = colors.textPrimary)
            if (subtitle != null) {
                Spacer(Modifier.height(1.dp))
                Text(subtitle, style = PocketTypography.caption, color = colors.textTertiary)
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically, content = trailing)
    }
}

@Composable
fun SwitchPocket(checked: Boolean, onChange: (Boolean) -> Unit) {
    val colors = pocketColors()
    Switch(
        checked = checked,
        onCheckedChange = onChange,
        colors = SwitchDefaults.colors(
            checkedThumbColor = Color.White,
            checkedTrackColor = colors.accentMain,
            uncheckedThumbColor = Color.White,
            uncheckedTrackColor = colors.surfaceAlt,
            uncheckedBorderColor = colors.borderDivider,
        ),
    )
}

/** 圆角输入框（视觉与移动端一致：muted 底 + 无边框）。 */
@Composable
fun PocketTextField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    placeholder: String = "",
    singleLine: Boolean = true,
    minHeight: Dp = 40.dp,
    leadingIcon: ImageVector? = null,
    password: Boolean = false,
    numberOnly: Boolean = false,
    enabled: Boolean = true,
    onImeAction: (() -> Unit)? = null,
    textStyle: TextStyle = PocketTypography.body,
    onPreviewKey: ((androidx.compose.ui.input.key.KeyEvent) -> Boolean)? = null,
    focusRequester: FocusRequester? = null,
) {
    val colors = pocketColors()
    val accent = accentColor()
    var focused by remember { mutableStateOf(false) }
    Row(
        modifier = modifier
            .heightIn(min = minHeight)
            .clip(RoundedCornerShape(Radii.chip))
            .background(colors.surfaceAlt)
            .border(1.dp, if (focused) accent.copy(alpha = 0.6f) else Color.Transparent, RoundedCornerShape(Radii.chip))
            .padding(horizontal = 12.dp, vertical = if (singleLine) 0.dp else 8.dp)
            .alpha(if (enabled) 1f else 0.6f),
        verticalAlignment = if (singleLine) Alignment.CenterVertically else Alignment.Top,
    ) {
        if (leadingIcon != null) {
            Icon(leadingIcon, contentDescription = null, tint = colors.textTertiary, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(8.dp))
        }
        Box(Modifier.weight(1f)) {
            // ⚠️ 这里用 `TextFieldValue` 而不是 `String` 版：中文输入法的**组合态**（拼音还没上屏）
            // 只有前者会告诉我它是不是在组合中（`composition != null`）。
            // 用 String 版时，拼音会被逐字写进 value —— 实测打「nihao」会触发 5 次 onValueChange，
            // 最后上屏再替换一次；「输入即校验 / 输入即搜索 / 只收数字」的字段全都会被拼音干扰。
            // 现在的口径：**组合中只更新本地的显示态，不向上报告**；上屏那一刻才回调一次。
            var fieldValue by remember { mutableStateOf(TextFieldValue(value)) }
            // 外部改值（清空、导入、切换记录）时同步进来；但别在用户打字时覆盖它
            if (fieldValue.text != value && fieldValue.composition == null) {
                fieldValue = TextFieldValue(value, selection = TextRange(value.length))
            }
            if (fieldValue.text.isEmpty()) {
                Text(placeholder, style = textStyle, color = colors.textTertiary, modifier = Modifier.padding(top = if (singleLine) 0.dp else 2.dp))
            }
            BasicTextField(
                value = fieldValue,
                onValueChange = { next ->
                    fieldValue = next
                    // composition 非空 = 还在拼，别把拼音当正文交出去
                    if (next.composition == null) {
                        val committed = next.text
                        if (numberOnly) {
                            // 数字字段（验证码/学期周数）不接受非数字，粘贴混进来也挡掉
                            val digitsOnly = committed.filter { it.isDigit() }
                            if (digitsOnly != committed) fieldValue = fieldValue.copy(text = digitsOnly, selection = TextRange(digitsOnly.length))
                            onValueChange(digitsOnly)
                        } else {
                            onValueChange(committed)
                        }
                    }
                },
                enabled = enabled,
                singleLine = singleLine,
                textStyle = textStyle.copy(color = colors.textPrimary),
                cursorBrush = SolidColor(accent),
                visualTransformation = when {
                    password -> PasswordVisualTransformation()
                    else -> VisualTransformation.None
                },
                keyboardOptions = KeyboardOptions(
                    keyboardType = if (numberOnly) KeyboardType.Number else KeyboardType.Text,
                    imeAction = if (onImeAction != null) ImeAction.Done else ImeAction.Default,
                ),
                keyboardActions = KeyboardActions(onDone = { onImeAction?.invoke() }),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = if (singleLine) 10.dp else 0.dp)
                    .then(if (focusRequester != null) Modifier.focusRequester(focusRequester) else Modifier)
                    .then(if (onPreviewKey != null) Modifier.onPreviewKeyEvent { onPreviewKey(it) } else Modifier),
            )
        }
    }
}

/** 分段控件（周次切换、视图切换这类 2~4 个选项）。 */
@Composable
fun Segmented(
    options: List<String>,
    selectedIndex: Int,
    modifier: Modifier = Modifier,
    onSelect: (Int) -> Unit,
) {
    val colors = pocketColors()
    val accent = accentColor()
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(Radii.chip))
            .background(colors.surfaceAlt)
            .border(1.dp, colors.borderDivider, RoundedCornerShape(Radii.chip))
            .padding(3.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        options.forEachIndexed { index, option ->
            val selected = index == selectedIndex
            val press = rememberPressState(!selected)
            // 选中态原来是"白底压在浅灰底上"，对比太弱；换成强调色填充，一眼看出在哪一段
            val bg by animateColorAsState(
                if (selected) pressSurface(accent, press.hovered, press.pressed)
                else if (press.hovered) colors.surfaceCard else Color.Transparent,
                animationSpec = tween(120),
                label = "segment-bg",
            )
            Box(
                modifier = Modifier
                    .scale(press.scale)
                    .clip(RoundedCornerShape(10.dp))
                    .background(bg)
                    .then(if (selected) Modifier.shadow(2.dp, RoundedCornerShape(10.dp)) else Modifier)
                    .hoverable(press.source)
                    .clickable(interactionSource = press.source, indication = null, enabled = !selected) { onSelect(index) }
                    .padding(horizontal = 12.dp, vertical = 6.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    option,
                    style = PocketTypography.caption.copy(fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal),
                    color = if (selected) onAccentColor() else colors.textSecondary,
                )
            }
        }
    }
}

@Composable
fun EmptyState(
    icon: ImageVector,
    title: String,
    hint: String? = null,
    modifier: Modifier = Modifier,
    action: (@Composable () -> Unit)? = null,
) {
    val colors = pocketColors()
    Column(
        modifier = modifier.fillMaxWidth().padding(vertical = 22.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        // 图标原来是灰底灰图（用户明确反感"灰蒙蒙"）：放进强调色的软圆里，空状态也有主题色。
        Box(
            modifier = Modifier
                .size(52.dp)
                .clip(CircleShape)
                .background(accentSoftColor()),
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, contentDescription = null, tint = accentColor(), modifier = Modifier.size(24.dp))
        }
        Spacer(Modifier.height(10.dp))
        Text(title, style = PocketTypography.cardTitle, color = colors.textSecondary, textAlign = TextAlign.Center)
        if (hint != null) {
            Spacer(Modifier.height(4.dp))
            Text(hint, style = PocketTypography.caption, color = colors.textTertiary, textAlign = TextAlign.Center)
        }
        if (action != null) {
            Spacer(Modifier.height(14.dp))
            action()
        }
    }
}

@Composable
fun DividerLine(modifier: Modifier = Modifier) {
    Box(
        modifier
            .fillMaxWidth()
            .height(1.dp)
            .background(pocketColors().borderDivider)
    )
}

/** 应用内模态框（不用平台 Dialog：窗口内一层遮罩，焦点与尺寸都可控）。 */
@Composable
fun PocketModal(
    title: String,
    subtitle: String? = null,
    onDismiss: () -> Unit,
    width: Dp = 460.dp,
    footer: @Composable RowScope.() -> Unit = {},
    content: @Composable ColumnScope.() -> Unit,
) {
    val colors = pocketColors()
    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.35f))
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onDismiss)
            .padding(24.dp),
        contentAlignment = Alignment.Center,
    ) {
        // 内容区最多占「窗口高度 - 头部/底部/外边距」，超出的部分自己滚：
        // 弹窗外层是固定高度的，如果只写 heightIn(max) + weight，内容会把 footer 顶出卡片并被裁掉。
        val contentMaxHeight = (maxHeight - 160.dp).coerceAtLeast(120.dp)
        // ⚠️ 阴影必须画在**卡片外层**：`.background()` 之后再挂 `.shadow()` 会让 Skia 给这张卡片
        // 套一层独立 render layer，实测（2026-11-02）会把卡片底色的绘制位置和内容错开——
        // 表现是「输入框贴到卡片边缘、内容溢出到卡片外、footer 与正文重叠」。同一个 PocketModal
        // 在 AI 配置弹窗上正常、在登录弹窗上错位，就是这层图层顺序踩中的结果。
        Box(
            modifier = Modifier
                .width(width)
                .shadow(24.dp, RoundedCornerShape(Radii.dialog)),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(Radii.dialog))
                    .background(colors.surfaceCard)
                    .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {}
                    .padding(20.dp),
            ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(title, style = PocketTypography.sectionTitle, color = colors.textPrimary)
                    if (subtitle != null) {
                        Spacer(Modifier.height(2.dp))
                        Text(subtitle, style = PocketTypography.caption, color = colors.textTertiary)
                    }
                }
                GhostIconButton(PocketIcons.Close, onClick = onDismiss)
            }
            Spacer(Modifier.height(14.dp))
            // 内容单独限高并滚动：表单类弹窗（AI 配置）超过一屏时 footer 不能被顶掉
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = contentMaxHeight)
                    .verticalScroll(rememberScrollState()),
            ) {
                content()
            }
            Spacer(Modifier.height(18.dp))
            DividerLine()
            Spacer(Modifier.height(14.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically,
                content = footer,
            )
            }
        }
    }
}

/** 进度条（总进度金用渐变；普通进度用强调色）。 */
@Composable
fun ProgressBar(
    progress: Float,
    modifier: Modifier = Modifier,
    height: Dp = 8.dp,
    gold: Boolean = false,
) {
    val colors = pocketColors()
    val brush = if (gold) {
        androidx.compose.ui.graphics.Brush.horizontalGradient(
            listOf(com.pocket.xiaoli.desktop.ui.theme.MetallicGoldStart, com.pocket.xiaoli.desktop.ui.theme.MetallicGoldEnd)
        )
    } else {
        androidx.compose.ui.graphics.Brush.horizontalGradient(listOf(colors.accentMain, colors.accentMain.copy(alpha = 0.7f)))
    }
    Box(
        modifier
            .fillMaxWidth()
            .height(height)
            .clip(RoundedCornerShape(height))
            .background(colors.surfaceAlt)
    ) {
        val animated by animateFloatAsState(progress.coerceIn(0f, 1f))
        Box(
            Modifier
                .fillMaxWidth(animated)
                .height(height)
                .clip(RoundedCornerShape(height))
                .background(brush)
        )
    }
}

/** 强调色小圆点。 */
@Composable
fun AccentDot(size: Dp = 8.dp, color: Color? = null) {
    Box(
        Modifier
            .size(size)
            .clip(RoundedCornerShape(size))
            .background(color ?: accentSoftColor().copy(alpha = 1f))
    )
}

/**
 * 销毁性操作的一次确认。
 *
 * Android 端有一条硬约束：删除类操作必须二次确认（见 memory/01-constraints.md）。
 * 桌面端把这条一起搬过来，所有「删学期 / 删课程 / 删待办 / 清空对话」都走这里，
 * 而不是点了就删。
 */
@Composable
fun ConfirmDialog(
    title: String,
    message: String,
    confirmText: String = "删除",
    danger: Boolean = true,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    val colors = pocketColors()
    PocketModal(
        title = title,
        onDismiss = onDismiss,
        width = 420.dp,
        footer = {
            Spacer(Modifier.weight(1f))
            SecondaryButton("取消") { onDismiss() }
            Spacer(Modifier.width(10.dp))
            if (danger) {
                DangerButton(confirmText) { onConfirm() }
            } else {
                PrimaryButton(confirmText) { onConfirm() }
            }
        },
    ) {
        Text(message, style = PocketTypography.body, color = colors.textSecondary)
    }
}
