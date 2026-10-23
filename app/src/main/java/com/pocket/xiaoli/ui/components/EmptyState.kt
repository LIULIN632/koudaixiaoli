package com.pocket.xiaoli.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Inbox
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pocket.xiaoli.ui.theme.LocalPocketXiaoliColors
import com.pocket.xiaoli.ui.theme.LocalSkin

/**
 * 通用「空态」：一个淡图标 + 一行主文案 + 一行可选说明。
 *
 * ## 为什么从 `campusui` 搬到这里
 *
 * 它原本叫 `CampusEmptyState`，住在 `ui/components/campusui/` —— 那个包按命名是
 * **校园功能专属**。但实际使用时它只被**非校园**页面用到（AI 会话列表、全局搜索），
 * 校园页面一个都没用。名字和位置都在暗示错误的归属，下一个人要写空态时
 * 会在"该不该用校园组件"上犹豫，或者干脆再复制一份。
 *
 * 空态是最典型的中立组件：它只依赖颜色/间距令牌，和"校园"没有任何关系。
 *
 * ## 与 [EmptyStatePlaceholder] 的区别
 *
 * 两者都在用，刻意都留着：
 * - 本组件：**带主副两行文案**，适合"说清这里为什么是空的"
 * - [EmptyStatePlaceholder]：只有一个图标 + 一行文案，适合"就是没数据"
 *
 * 合并它们需要先确认所有调用点的视觉预期，那是另一件事。
 */
@Composable
fun EmptyState(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String = "",
    icon: ImageVector = Icons.Outlined.Inbox,
) {
    val skin = LocalSkin.current
    val colors = LocalPocketXiaoliColors.current
    Box(modifier.fillMaxWidth().padding(vertical = 48.dp), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(icon, null, tint = colors.textTertiary.copy(alpha = 0.6f), modifier = Modifier.size(44.dp))
            Spacer(Modifier.height(skin.spaceMd))
            Text(title, fontSize = 14.sp, color = colors.textSecondary)
            if (subtitle.isNotBlank()) {
                Spacer(Modifier.height(skin.spaceXs))
                Text(
                    subtitle,
                    fontSize = 12.sp,
                    color = colors.textTertiary,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(horizontal = 32.dp),
                )
            }
        }
    }
}
