package com.pocket.xiaoli.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pocket.xiaoli.data.School
import com.pocket.xiaoli.data.isCustomSchool
import com.pocket.xiaoli.ui.theme.LocalPocketXiaoliColors
import com.pocket.xiaoli.ui.theme.LocalSkin
import com.pocket.xiaoli.ui.theme.accentColor

/**
 * 「本地学校列表」的共享装配：搜索框、空态、收藏 / 烟台分组、页脚。
 *
 * 教务直连导入与网页导入的**选校步骤完全同构**，此前两边各抄了一份：
 * 搜索框的描边 / 圆角、收藏分组的粗分隔线、空态文案一旦改错一边，用户看到的就是
 * "同一个 App 里两套选校列表"。行本身已在 [SchoolRow] 统一，这里把剩下的列表骨架也收拢。
 *
 * 两个调用点仍各管各的**动作**（选校后是填表单还是直接开 WebView、自定义学校入口长什么样），
 * 所以这里只收展示与分组，不碰 ViewModel。
 */

/** 学校搜索框 —— 两个导入入口共用。 */
@Composable
fun LocalSchoolSearchField(
    query: String,
    onQueryChange: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val skin = LocalSkin.current
    val colors = LocalPocketXiaoliColors.current
    OutlinedTextField(
        value = query,
        onValueChange = onQueryChange,
        placeholder = { Text("搜索学校名称或城市") },
        leadingIcon = { Icon(Icons.Outlined.Search, null, tint = colors.textTertiary) },
        singleLine = true,
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(skin.radiusMicro),
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = accentColor(),
            unfocusedBorderColor = colors.borderDivider,
        ),
    )
}

/** 搜索无结果时的空态；有关键词才给「反馈」出口，空搜索没必要引导用户报告。 */
@Composable
fun LocalSchoolsEmptyState(query: String, onFeedback: () -> Unit) {
    val skin = LocalSkin.current
    val colors = LocalPocketXiaoliColors.current
    Column(
        modifier = Modifier.fillMaxWidth().padding(vertical = skin.spaceXxl),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            if (query.isEmpty()) "暂无可用学校" else "未找到「$query」",
            fontSize = 13.sp,
            color = colors.textTertiary,
        )
        if (query.isNotEmpty()) {
            Spacer(Modifier.height(skin.spaceSmall))
            TextButton(onClick = onFeedback) {
                Text("不是你要找的学校？反馈", color = accentColor(), fontSize = 13.sp)
            }
        }
    }
}

/**
 * 往 `LazyColumn` 里塞「我的收藏 → 烟台学校 → 其余学校 → 页脚」。
 *
 * 顺序与分组是产品口径（收藏优先、烟台本地的排在前），不要在某一个页面里另排一套。
 *
 * @param selectedSchoolId 只有教务直连流程需要显示选中态；网页导入点一下就跳走，传 null
 * @param onEdit / [onDelete] 只对用户自建学校（`custom_`）生效，内置学校不给改
 * @param footer 页脚那句"外地学校怎么办"——两个入口对"添加自己的学校"入口的叫法不同，由调用方给
 */
fun LazyListScope.localSchoolItems(
    schools: List<School>,
    favoriteIds: Set<String>,
    onSelect: (School) -> Unit,
    onToggleFavorite: (School) -> Unit,
    footer: String,
    selectedSchoolId: String? = null,
    onEdit: ((School) -> Unit)? = null,
    onDelete: ((School) -> Unit)? = null,
) {
    val favorites = schools.filter { it.id in favoriteIds }
    val others = schools.filter { it.id !in favoriteIds }

    if (favorites.isNotEmpty()) {
        item { LocalSchoolSectionCaption("我的收藏") }
        items(favorites, key = { it.id }) { school ->
            LocalSchoolItem(
                school = school,
                selected = school.id == selectedSchoolId,
                favorite = true,
                onSelect = onSelect,
                onToggleFavorite = onToggleFavorite,
                onEdit = onEdit,
                onDelete = onDelete,
            )
        }
        item { LocalSchoolSectionDivider() }
        item { LocalSchoolSectionCaption("烟台学校") }
    }
    items(others, key = { it.id }) { school ->
        LocalSchoolItem(
            school = school,
            selected = school.id == selectedSchoolId,
            favorite = false,
            onSelect = onSelect,
            onToggleFavorite = onToggleFavorite,
            onEdit = onEdit,
            onDelete = onDelete,
        )
    }
    item { LocalSchoolFooter(footer) }
}

@Composable
private fun LocalSchoolItem(
    school: School,
    selected: Boolean,
    favorite: Boolean,
    onSelect: (School) -> Unit,
    onToggleFavorite: (School) -> Unit,
    onEdit: ((School) -> Unit)?,
    onDelete: ((School) -> Unit)?,
) {
    val colors = LocalPocketXiaoliColors.current
    SchoolRow(
        school = school,
        isSelected = selected,
        onSelect = { onSelect(school) },
        isFavorite = favorite,
        onToggleFavorite = { onToggleFavorite(school) },
        onEdit = if (school.isCustomSchool()) onEdit?.let { handler -> { handler(school) } } else null,
        onDelete = if (school.isCustomSchool()) onDelete?.let { handler -> { handler(school) } } else null,
    )
    HorizontalDivider(color = colors.borderDivider, thickness = 0.5.dp)
}

@Composable
private fun LocalSchoolSectionCaption(text: String) {
    val skin = LocalSkin.current
    SectionCaption(text, Modifier.padding(top = skin.spaceSm, bottom = skin.spaceMicro))
}

@Composable
private fun LocalSchoolSectionDivider() {
    val skin = LocalSkin.current
    val colors = LocalPocketXiaoliColors.current
    HorizontalDivider(
        color = colors.textTertiary.copy(alpha = 0.3f),
        thickness = 1.dp,
        modifier = Modifier.padding(vertical = skin.spaceSm),
    )
}

@Composable
private fun LocalSchoolFooter(text: String) {
    val skin = LocalSkin.current
    val colors = LocalPocketXiaoliColors.current
    Text(
        text,
        fontSize = 11.sp,
        color = colors.textTertiary,
        modifier = Modifier.padding(top = skin.spaceSm),
    )
}
