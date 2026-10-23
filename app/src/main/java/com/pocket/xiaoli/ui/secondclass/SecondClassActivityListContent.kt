package com.pocket.xiaoli.ui.secondclass

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pocket.xiaoli.data.secondclass.SecondClassActivity
import com.pocket.xiaoli.data.secondclass.SecondClassActivityStatus
import com.pocket.xiaoli.ui.components.EmptyState
import com.pocket.xiaoli.ui.components.ErrorCard
import com.pocket.xiaoli.ui.components.campusui.CampusCard
import com.pocket.xiaoli.ui.theme.LocalPocketXiaoliColors
import com.pocket.xiaoli.ui.theme.LocalSkin

/**
 * 「我的活动」列表的状态。四态与成绩单页一致（[SecondClassTranscriptState]），
 * 刻意不共用一个 sealed 类型：两页能取到的数据不同（这里是活动，那里是成绩单），
 * 硬合并只会得到一个泛型擦除后谁都读不懂的状态机。
 */
internal sealed interface SecondClassActivityListState {
    data object Loading : SecondClassActivityListState

    /** 登录态失效 —— 与成绩单页同一条理由：它必须能引导用户**回到到梦空间去登录**。 */
    data object NeedLogin : SecondClassActivityListState

    data class Failed(val message: String, val detail: String? = null) : SecondClassActivityListState

    data class Loaded(val activities: List<SecondClassActivity>) : SecondClassActivityListState
}

/**
 * 「我的活动」的**纯内容组件**（为什么抽成 `internal` 纯组件：同
 * [SecondClassTranscriptContent] —— 渲得出才拍得了基线，拍得了基线才知道它长什么样）。
 *
 * ## ⚠️ 界面上刻意不出现的三样东西
 *
 * 1. **不给数值加单位**。`credit` 是"积分"还是"学分"**各校不同**（烟理是「积分置换学分」，
 *    见架构文档 §11），所以只显示原始数字，**不写"学分"两个字** —— 写了就是在替学校下定义。
 * 2. **不显示"报名 / 签到"按钮**。写操作一律不做（B3/B5）；报名要去平台自己的系统。
 *    这一页是**只读列表 + 一个去平台的出口**。
 * 3. **`UNKNOWN` 状态不渲染任何胶囊**。服务端给了个我们不认识的状态时，
 *    显示"未知"是**把我们的无知当成信息**推给用户；不显示反而更诚实。
 */
@Composable
internal fun SecondClassActivityListContent(
    state: SecondClassActivityListState,
    modifier: Modifier = Modifier,
    onOpenPlatform: () -> Unit = {},
    onOpenActivity: (SecondClassActivity) -> Unit = {},
    onRetry: (() -> Unit)? = null,
) {
    val skin = LocalSkin.current
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = skin.screenH),
        verticalArrangement = Arrangement.spacedBy(skin.spaceSm),
    ) {
        when (state) {
            SecondClassActivityListState.Loading -> LoadingHint()
            SecondClassActivityListState.NeedLogin -> NeedLoginCard(onOpenPlatform)
            is SecondClassActivityListState.Failed ->
                ErrorCard(message = state.message, detail = state.detail, onRetry = onRetry)

            is SecondClassActivityListState.Loaded -> {
                if (state.activities.isEmpty()) {
                    EmptyState(
                        title = "还没有活动记录",
                        subtitle = "在到梦空间报名并参加活动后，这里会列出你的活动",
                    )
                } else {
                    SectionTitle("我的活动")
                    state.activities.forEach { activity ->
                        ActivityCard(activity = activity, onClick = { onOpenActivity(activity) })
                    }
                    Text(
                        text = "报名、签到与学分申请请在到梦空间 App 内完成。",
                        fontSize = 11.sp,
                        color = LocalPocketXiaoliColors.current.textTertiary,
                        modifier = Modifier.padding(top = skin.spaceXs),
                    )
                }
                PillAction(text = "用 App 打开到梦空间", onClick = onOpenPlatform)
            }
        }
    }
}

/** 载入中不转圈：无限动画会让基线图时红时绿（同成绩单页）。 */
@Composable
private fun LoadingHint() {
    Text(
        text = "正在读取活动…",
        modifier = Modifier.padding(vertical = 36.dp),
        fontSize = 13.sp,
        color = LocalPocketXiaoliColors.current.textSecondary,
    )
}

@Composable
private fun NeedLoginCard(onOpenPlatform: () -> Unit) {
    val colors = LocalPocketXiaoliColors.current
    CampusCard {
        Text(
            text = "需要先登录到梦空间",
            fontSize = 14.sp,
            fontWeight = FontWeight.SemiBold,
            color = colors.textPrimary,
        )
        Text(
            text = "第二课堂用的是到梦空间自己的账号（用「学号 + 姓名」激活），" +
                "不能靠学校统一认证免登。请打开登录页，用【到梦空间】App 扫一扫登录。",
            fontSize = 12.sp,
            lineHeight = 18.sp,
            color = colors.textSecondary,
        )
        PillAction(text = "打开登录页", onClick = onOpenPlatform)
    }
}

@Composable
private fun ActivityCard(activity: SecondClassActivity, onClick: () -> Unit) {
    val colors = LocalPocketXiaoliColors.current
    CampusCard(onClick = onClick) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = activity.title.ifBlank { "未命名活动" },
                modifier = Modifier.weight(1f),
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
                color = colors.textPrimary,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            // 数值只给原样，**不写单位**（积分 / 学分口径各校不同）。
            if (!activity.credit.isNullOrBlank()) {
                Spacer(Modifier.width(8.dp))
                Text(activity.credit, fontSize = 12.sp, color = colors.accentMain)
            }
        }

        val label = statusLabel(activity.status)
        if (label != null) {
            StatusPill(label = label, color = statusColor(activity.status))
        }

        val time = listOfNotNull(
            activity.startAt?.takeIf { it.isNotBlank() },
            activity.endAt?.takeIf { it.isNotBlank() },
        ).joinToString(" – ")
        if (time.isNotBlank()) {
            Text(time, fontSize = 12.sp, color = colors.textSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }

        val where = listOfNotNull(
            activity.location?.takeIf { it.isNotBlank() },
            activity.organizer?.takeIf { it.isNotBlank() },
        ).joinToString(" · ")
        if (where.isNotBlank()) {
            Text(where, fontSize = 11.sp, color = colors.textTertiary, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

/**
 * 状态胶囊。配色沿用 `ErrorCard` 的"中立底 + 语义色描边与文字"，
 * 而不是语义色铺底 —— 8 套主题 × 明暗下前者对比度更稳。
 */
@Composable
private fun StatusPill(label: String, color: Color) {
    Surface(
        shape = RoundedCornerShape(50),
        color = LocalPocketXiaoliColors.current.surfaceAlt,
        border = BorderStroke(1.dp, color.copy(alpha = 0.35f)),
    ) {
        Text(
            text = label,
            modifier = Modifier.padding(horizontal = 9.dp, vertical = 3.dp),
            fontSize = 11.sp,
            fontWeight = FontWeight.Medium,
            color = color,
        )
    }
}

@Composable
private fun statusColor(status: SecondClassActivityStatus): Color {
    val colors = LocalPocketXiaoliColors.current
    return when (status) {
        SecondClassActivityStatus.SIGNUP -> colors.accentMain
        SecondClassActivityStatus.ONGOING -> colors.success
        // ⚠️ 这里**不能**用 `textTertiary`：它是 11sp 的小字压在中立底上，
        // 渲出来一看就偏淡（第一版就是 textTertiary，"已结束"三个字几乎要凑近看）。
        // 弱化靠"灰 vs 蓝/绿"这层色相差就够了，不必再牺牲可读性 ——
        // 而 `ThemeContrastTest` 查的是**令牌对**，查不到"某个 11sp 标签用了 tertiary"这种组合。
        SecondClassActivityStatus.ENDED -> colors.textSecondary
        SecondClassActivityStatus.CANCELLED -> colors.danger
        // 不会走到（[statusLabel] 对 UNKNOWN 返回 null，调用点会跳过整颗胶囊），
        // 给一个中性色只是为了穷尽 when。
        SecondClassActivityStatus.UNKNOWN -> colors.textTertiary
    }
}

/**
 * 状态 → 给用户看的中文标签。**`UNKNOWN` 返回 `null`，不是"未知"**。
 *
 * 理由：服务端给了个我们不认识的状态时，显示"未知"是把**我们的无知**当成信息推给用户；
 * 什么都不显示，用户至少还能从标题与时间里读懂这条记录。
 *
 * 纯函数，单测直接覆盖（`SecondClassActivityListScreenshotTest`）。
 */
internal fun statusLabel(status: SecondClassActivityStatus): String? = when (status) {
    SecondClassActivityStatus.SIGNUP -> "可报名"
    SecondClassActivityStatus.ONGOING -> "进行中"
    SecondClassActivityStatus.ENDED -> "已结束"
    SecondClassActivityStatus.CANCELLED -> "已取消"
    SecondClassActivityStatus.UNKNOWN -> null
}
