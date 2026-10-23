package com.pocket.xiaoli.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.kyant.backdrop.Backdrop
import com.pocket.xiaoli.ui.glass.glassChipSurface
import com.pocket.xiaoli.ui.theme.LocalPocketXiaoliColors

/**
 * 顶栏上的**圆形浅底按钮**（鲤鱼页那一套）。
 *
 * 为什么不用默认的 `IconButton`：默认长相是"一根孤零零的线框图标"，
 * 压在浅色顶栏上几乎看不出边界，用户看不出"这里能点"。参考软件的顶栏按钮
 * 都是**圆形浅底 + 深色图标**，边界一眼可见。
 *
 * 对**磨砂顶栏**尤其重要：底色半透明之后，细线图标会被背后滚过的内容吃掉。
 *
 * ## 底色必须是"同一块玻璃"，不能是实心浅底
 *
 * 第一版这里画的是**实心**的 `surfaceHighlight` 圆。压在磨砂顶栏上，它读起来是
 * "贴在玻璃上的一张不干胶" —— 两种材质互相打架（用户的原话："那几个按钮跟磨砂材质不匹配"）。
 * 现在改走 [glassChipSurface]：背后内容照糊、透出一层淡的主色系底、内阴影收边，
 * 与顶栏本体、底栏是同一套玻璃语法。底色的**主题语义**没变（还是 `surfaceHighlight` 跟着主题走），
 * 变的只是它现在以半透明参与，而不是一块实色。
 *
 * 抽到 `ui/components/` 是因为鲤鱼的三个页面（鲤鱼 / 历史对话 / 助手设置）都要它 ——
 * 三处各写一份的话，"磨砂顶栏上图标读不清"这个坑迟早会从其中一处重新踩回来。
 */
@Composable
fun TopBarCircleButton(
    onClick: () -> Unit,
    icon: ImageVector,
    label: String,
    backdrop: Backdrop? = null,
    modifier: Modifier = Modifier,
) {
    val colors = LocalPocketXiaoliColors.current
    IconButton(
        onClick = onClick,
        modifier = modifier,
    ) {
        // ⚠️ 圆底画在 IconButton **内部**的 Box 上，不能写成
        // `IconButton(modifier = Modifier.size(34.dp).background(...))`。
        //
        // 原因：IconButton 内部会套一层 `minimumInteractiveComponentSize()`（48dp 热区），
        // 而外层 `.size(34.dp)` 把它的约束**钉死**成 34dp —— 热区跟着缩水到 34dp，
        // 低于 Material / 无障碍要求的 48dp 最低值，手指点不准；
        // 同时按钮占位的宽度也从 48dp 变成 38dp，顶栏标题的左起位置跟着比其它子页面偏左 10dp。
        //
        // 现在：热区交给 M3（48dp），可见的圆 34dp 居中 —— 视觉不变，能点、也对齐了。
        Box(
            modifier = Modifier
                .size(34.dp)
                // 与顶栏同一块材质：背后内容被糊掉、透出淡底、白色高光描边。
                // backdrop 为 null（低版本 / 未启用玻璃）时它会自己退成半透明底 + 高光边
                .glassChipSurface(backdrop = backdrop, surfaceColor = colors.surfaceHighlight),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                tint = colors.textSecondary,
                modifier = Modifier.size(18.dp),
            )
        }
    }
}
