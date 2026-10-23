package com.pocket.xiaoli.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.School
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pocket.xiaoli.data.School
import com.pocket.xiaoli.ui.theme.LocalPocketXiaoliColors
import com.pocket.xiaoli.ui.theme.LocalSkin
import com.pocket.xiaoli.ui.theme.accentColor
import com.pocket.xiaoli.ui.theme.accentSoftColor
import com.pocket.xiaoli.ui.theme.invisibleTouchTarget

/**
 * 学校列表行 —— 「导入课表」两个入口（教务直连 / 网页导入）共用。
 *
 * ## 为什么抽出来
 *
 * 这段 UI 原本在 `ImportScreen` 与 `WebImportScreen` 里**各写了一份**：
 * 结构相同（学校图标 + 名称 + 收藏按钮 + 尾部指示），差异只在
 * 「是否支持选中」「是否有编辑/删除」「副标题写什么」这几个开关上。
 *
 * 两份实现已经漂移出可见的不一致（背景透明度 0.5 vs 0.3、副标题文案两套、
 * 一个显示教务类型标签另一个不显示）。这类"同一屏元素长得不一样"的问题，
 * 用户未必说得出哪里怪，但会觉得不精致。
 *
 * 现在合成一个组件，差异全部交给参数 —— 同时也是**后续改一处即全局生效**的前提。
 *
 * ## 参数约定
 *
 * - [isSelected] 只在「教务直连」流程里有意义（那边要先选校再导入）；
 *   网页导入直接点学校就走流程，因此保持默认 `false`。
 * - [onEdit] / [onDelete] 仅对用户自建学校传入（内置学校不给改）。
 */
@Composable
fun SchoolRow(
    school: School,
    onSelect: () -> Unit,
    isFavorite: Boolean,
    onToggleFavorite: () -> Unit,
    isSelected: Boolean = false,
    onEdit: (() -> Unit)? = null,
    onDelete: (() -> Unit)? = null,
    /** 是否在末尾显示「进入」箭头。选中态已用对勾表达时不需要箭头。 */
    showChevron: Boolean = !isSelected,
) {
    val skin = LocalSkin.current
    val colors = LocalPocketXiaoliColors.current

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(skin.radiusMicro))
            .background(
                if (isSelected) accentSoftColor().copy(alpha = 0.5f) else Color.Transparent
            )
            .clickable { onSelect() }
            .padding(start = skin.spaceMd, top = skin.spaceCompact, bottom = skin.spaceCompact, end = skin.spaceXs),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = Icons.Outlined.School,
            contentDescription = null,
            tint = if (isSelected) accentColor() else colors.textTertiary,
            modifier = Modifier.size(22.dp),
        )
        Spacer(Modifier.width(skin.spaceMd))

        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = school.name,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Medium,
                    color = colors.textPrimary,
                )
                Spacer(Modifier.width(skin.spaceSmall))
                // 教务类型标签：让用户一眼看出"这所学校是正方还是强智"
                Surface(
                    shape = RoundedCornerShape(skin.radiusMicro),
                    color = accentSoftColor(),
                ) {
                    Text(
                        text = school.jwType.label,
                        fontSize = 11.sp,
                        color = accentColor(),
                        modifier = Modifier.padding(horizontal = skin.spaceXs, vertical = 1.dp),
                    )
                }
            }
            Text(
                text = if (school.baseUrl.isBlank()) {
                    "暂未收录教务地址 · 点击补充"
                } else {
                    "${school.city} · ${school.baseUrl}"
                },
                fontSize = 11.sp,
                color = colors.textTertiary,
            )
        }

        SchoolFavoriteButton(isFavorite = isFavorite, onToggle = onToggleFavorite)

        if (onEdit != null) {
            IconButton(onClick = onEdit, modifier = Modifier.size(32.dp).invisibleTouchTarget()) {
                Icon(
                    imageVector = Icons.Outlined.Edit,
                    contentDescription = "编辑",
                    tint = colors.textSecondary,
                    modifier = Modifier.size(16.dp),
                )
            }
        }
        if (onDelete != null) {
            IconButton(onClick = onDelete, modifier = Modifier.size(32.dp).invisibleTouchTarget()) {
                Icon(
                    imageVector = Icons.Outlined.Delete,
                    contentDescription = "删除",
                    tint = colors.danger,
                    modifier = Modifier.size(16.dp),
                )
            }
        }
        if (isSelected) {
            Icon(
                imageVector = Icons.Outlined.CheckCircle,
                contentDescription = "已选",
                tint = accentColor(),
                modifier = Modifier.size(20.dp),
            )
        } else if (showChevron) {
            Icon(
                imageVector = Icons.Outlined.ChevronRight,
                contentDescription = null,
                tint = colors.textTertiary,
                modifier = Modifier.size(20.dp),
            )
        }
    }
}
