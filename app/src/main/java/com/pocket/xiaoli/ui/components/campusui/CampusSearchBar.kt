package com.pocket.xiaoli.ui.components.campusui

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import com.pocket.xiaoli.ui.glass.reportTextInputFocus
import com.pocket.xiaoli.ui.theme.LocalPocketXiaoliColors

/** CampusUI 通用搜索框：服务页、知识库等页面统一使用。 */
@Composable
fun CampusSearchBar(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    placeholder: String = "搜索",
    onSearch: () -> Unit = {},
) {
    val colors = LocalPocketXiaoliColors.current
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        // ⚠️ 输入框**内部**就上报焦点，而不是让每个使用方各自记得 ——
        // 服务页是 tab 路由，键盘弹出时若不收起底栏，底栏会被 `adjustResize` 一起顶上去。
        // 全局搜索页不是 tab 路由（底栏本就隐藏），此时上报是**无害的 no-op**。
        modifier = modifier.fillMaxWidth().reportTextInputFocus(),
        singleLine = true,
        shape = RoundedCornerShape(50),
        placeholder = { Text(placeholder, color = colors.textTertiary) },
        leadingIcon = {
            Icon(Icons.Default.Search, contentDescription = null, tint = colors.textTertiary)
        },
        trailingIcon = {
            if (value.isNotBlank()) {
                IconButton(onClick = { onValueChange("") }) {
                    Icon(Icons.Default.Close, contentDescription = "清空", tint = colors.textTertiary)
                }
            }
        },
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
        keyboardActions = KeyboardActions(onSearch = { onSearch() }),
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = colors.accentMain,
            unfocusedBorderColor = colors.borderDivider,
            focusedContainerColor = colors.surfaceCard,
            unfocusedContainerColor = colors.surfaceCard,
        ),
    )
}
