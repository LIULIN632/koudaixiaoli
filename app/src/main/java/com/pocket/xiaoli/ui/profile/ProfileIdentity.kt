package com.pocket.xiaoli.ui.profile

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pocket.xiaoli.ui.theme.LocalPocketXiaoliColors
import com.pocket.xiaoli.ui.theme.LocalSkin

/**
 * 「我的」页的身份卡 —— 该页**没有顶栏**，正文第一条就是它，所以它是这个 tab 的第一眼。
 *
 * ## 2026-10 二次改版：默认**一个值都不显示**，点进去才给
 *
 * 用户反馈："别显示个人信息，点进去才能显示，改得好看点"。改造前是三行，第三行是重点：
 *
 * ```
 * [首字头像] 同学 ✎                                ›
 *            烟台理工学院 · 本校区
 * 学号 *********6789 · 宿舍 3号楼 412 · 学习平台 2/2
 * ```
 *
 * 问题有两层：
 *
 * 1. **隐私**：学号（哪怕脱敏）、宿舍、学习平台的绑定情况，是"打开 App 就到手"的信息；
 *    手机被人瞄一眼、或用户截屏分享时，它自己就跑出去了；
 * 2. **观感**：那三件事挤成一句跑马灯（没有阅读层级；未绑定态整行变琥珀，像一条警告条），
 *    同时把卡片撑到约 150dp，底部留出一段空白。
 *
 * 现在压成两行、**不含任何具体值**：
 *
 * ```
 * [头像] 我的信息                     [已绑定 2/3]  ›
 *        点开查看账号与绑定信息
 * ```
 *
 * - 具体信息住在「绑定信息」页（`AccountBindingScreen` 的 `BindingStatusCard`）：
 *   那里本来就有昵称/学校/脱敏学号与教务/学习平台/宿舍的逐项状态，**不新建界面**；
 * - 右上的胶囊只给**计数**（0 项 → 琥珀「待绑定」）—— 守住"绑好了没有要一眼可见"，
 *   同时不泄漏绑的是哪个学号、哪间宿舍；
 * - ⚠️ 琥珀字**压在卡底上**（实测 4.98:1 ✅），描边只是 35% 琥珀、底色仍是卡底。
 *   别把它改成 `surfaceHighlight` 底 —— 那里只有 4.15:1 ❌，`ThemeContrastTest` 会红，
 *   而这条正是 `ErrorCard` 用"描边而非淡底"表达异常的同一条理由。
 *
 * ## 隐私是**结构性**的，不是"传进来但不画"
 *
 * 这张卡现在**收不到**昵称、学号、宿舍、学校这些字符串：调用链
 *（`ProfileScreen` → `ProfileHomeContent` → 本卡）只传一个 `boundCount: Int`。
 * 那些值**根本进不了「我的」页正文**，而不是"进了但没渲染"。
 * `ProfilePrivacyTest` 从**页面**那一层断言语义树里查不到它们 —— 谁把参数加回来都会红。
 *
 * ## 头像上不再叠鲤鱼徽标
 *
 * 用户要求"个人信息那里去掉 AI 形象"。右侧的 `AiAvatar` 徽标已删除，
 * 只留中性的渐变头像圆；助手此刻的状态在鲤鱼页与消息区里看，那里才是它该出现的地方。
 */
@Composable
fun ProfileIdentityCard(
    /** 已绑好的项数：教务 / 宿舍 / 学习平台三项里绑好了几项（0..3）。**只看数量，不看内容**。 */
    boundCount: Int,
    onOpenBinding: () -> Unit
) {
    val skin = LocalSkin.current
    val colors = LocalPocketXiaoliColors.current
    val bound = boundCount.coerceIn(0, BOUND_ITEM_TOTAL)

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(skin.radiusLarge))
            // role = Button：整张卡是一条"进入绑定信息页"的动作。
            // 不给 role 的话，读屏只会念出卡里的一串文字，说不出"这里可以点"。
            .clickable(role = Role.Button, onClick = onOpenBinding),
        shape = RoundedCornerShape(skin.radiusLarge),
        colors = CardDefaults.cardColors(containerColor = colors.surfaceCard),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(skin.spaceWide),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // 中性头像：渐变圆 + 通用人像图标（不再是昵称首字）。
            Box(
                modifier = Modifier
                    .size(52.dp)
                    .clip(CircleShape)
                    .background(
                        Brush.linearGradient(
                            listOf(
                                colors.accentMain,
                                colors.accentMain.copy(alpha = 0.68f)
                            )
                        )
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Outlined.Person,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(26.dp)
                )
            }

            Spacer(Modifier.width(skin.spaceCompact))

            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "我的信息",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = colors.textPrimary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        // fill = false：标题短时胶囊紧跟其后；窄屏上让标题先截断，
                        // 也不要把计数挤出去（计数是这一行唯一的信息量）。
                        modifier = Modifier.weight(1f, fill = false)
                    )
                    Spacer(Modifier.width(skin.spaceXs))
                    BindingCountPill(bound = bound)
                }
                Spacer(Modifier.height(skin.spaceTiny))
                Text(
                    text = "点开查看账号与绑定信息",
                    fontSize = 12.sp,
                    color = colors.textSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Spacer(Modifier.width(skin.spaceXs))
            // 矢量箭头，不再用 Text("›") 这个字符 —— 全 App 其它入口
            // （如 SettingsGroupCard）用的都是同一个图标，字符箭头字重与基线都对不齐。
            Icon(
                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = null,
                tint = colors.textTertiary,
                modifier = Modifier.size(20.dp)
            )
        }
    }
}

/** 卡片上"绑齐了没"的分母：教务 / 宿舍 / 学习平台。 */
private const val BOUND_ITEM_TOTAL = 3

/**
 * 绑定进度胶囊：**只给计数，不给任何具体值**。
 *
 * - `0` 项 → 琥珀「待绑定」，用**描边**而不是淡琥珀底（见 [ProfileIdentityCard] 的说明）；
 * - 其余 → 灰字「已绑定 n/3」，坐在 `surfaceHighlight` 上（那一对是既有配色，对比度已锁）。
 *
 * ⚠️ 刻意不做成可点的"去绑定"入口：整张卡已经是一个动作，卡内再放按钮，
 * 读屏与手指都会在"点哪儿"上犹豫（这个文件先前那支铅笔的教训）。
 */
@Composable
private fun BindingCountPill(bound: Int) {
    val colors = LocalPocketXiaoliColors.current
    val pending = bound == 0
    val shape = RoundedCornerShape(percent = 50)

    Box(
        modifier = Modifier
            .clip(shape)
            .background(if (pending) colors.surfaceCard else colors.surfaceHighlight)
            .then(
                if (pending) {
                    Modifier.border(1.dp, colors.warning.copy(alpha = 0.35f), shape)
                } else {
                    Modifier
                }
            )
            .padding(horizontal = 8.dp, vertical = 3.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = if (pending) "待绑定" else "已绑定 $bound/$BOUND_ITEM_TOTAL",
            fontSize = 11.sp,
            color = if (pending) colors.warning else colors.textSecondary,
            maxLines = 1,
        )
    }
}
