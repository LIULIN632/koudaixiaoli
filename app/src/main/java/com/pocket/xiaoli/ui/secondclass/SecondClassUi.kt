package com.pocket.xiaoli.ui.secondclass

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pocket.xiaoli.ui.theme.LocalPocketXiaoliColors
import com.pocket.xiaoli.ui.theme.LocalSkin

/**
 * 第二课堂各页共用的小组件。
 *
 * 为什么单独放一个文件、而不让每个页面各留一份 private 实现：
 * 成绩单与活动列表是同一模块的两页，`PillAction` / `SectionTitle` 一旦各写一份，
 * 两页的圆角、间距、触摸高度就会各自漂移 —— 而"平行实现"正是 `CLAUDE.md` 反复点名要避免的。
 * 它们暂时只服务本模块，所以放 `ui/secondclass/` 而不是 `ui/components/`：
 * 后者的门槛是"真的有多个模块在用"（`EmptyState` 从 `campusui` 搬出来就是这条判据）。
 */

/**
 * 触摸区下限，与 `ProactiveBubble` 的关闭键、`AssistantSettings` 的色块同一条底线。
 *
 * ⚠️ 44dp 这件事**在截图上完全看不出来**（一颗 30dp 的胶囊和 44dp 的在图里几乎一样），
 * 所以它靠语义断言守着：`SecondClassTranscriptScreenshotTest.去登录的触摸高度达标`。
 * 新增胶囊动作时**复用本常量**，别写死一个更小的值。
 */
internal val MIN_TOUCH = 44.dp

/**
 * 一个胶囊动作。
 *
 * 配色沿用 `ErrorCard.ErrorAction` 的做法：**语义中立的底 + 主色描边**，
 * 而不是主色铺底配白字（8 套主题 × 明暗下前者的对比度更稳，且不必新引一个前景色令牌）。
 */
@Composable
internal fun PillAction(text: String, onClick: () -> Unit) {
    val colors = LocalPocketXiaoliColors.current
    Surface(
        shape = RoundedCornerShape(50),
        color = colors.surfaceAlt,
        border = BorderStroke(1.dp, colors.accentMain.copy(alpha = 0.35f)),
        modifier = Modifier
            .heightIn(min = MIN_TOUCH)
            .clickable(onClick = onClick),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(text, fontSize = 13.sp, fontWeight = FontWeight.Medium, color = colors.accentMain)
        }
    }
}

/** 分区小标题（"学分模块" / "成长记录" / "活动"）。 */
@Composable
internal fun SectionTitle(text: String) {
    val colors = LocalPocketXiaoliColors.current
    Text(
        text = text,
        modifier = Modifier.padding(top = LocalSkin.current.spaceXs),
        fontSize = 13.sp,
        fontWeight = FontWeight.SemiBold,
        color = colors.textPrimary,
    )
}
