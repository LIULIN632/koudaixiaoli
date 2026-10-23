package com.pocket.xiaoli.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pocket.xiaoli.ui.theme.LocalPocketXiaoliColors

/**
 * 分组小标题：一条 3×13dp 主色竖条 + 13sp 半粗标题。
 *
 * 首页「待办」那种 20sp 大标题放到二级页会喧宾夺主；这一档只做列表分隔。
 * 搜索页、鲤鱼设置页、资料站、快问快答共用此处，避免每个页面各写一条竖条。
 */
@Composable
fun SectionCaption(text: String, modifier: Modifier = Modifier) {
    val colors = LocalPocketXiaoliColors.current
    Row(modifier = modifier, verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier
                .width(3.dp)
                .height(13.dp)
                .clip(RoundedCornerShape(50))
                .background(colors.accentMain),
        )
        Spacer(Modifier.width(7.dp))
        Text(
            text = text,
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold,
            color = colors.textPrimary,
        )
    }
}
