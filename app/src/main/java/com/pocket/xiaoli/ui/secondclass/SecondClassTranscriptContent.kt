package com.pocket.xiaoli.ui.secondclass

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pocket.xiaoli.data.secondclass.SecondClassGrowthRecord
import com.pocket.xiaoli.data.secondclass.SecondClassTranscript
import com.pocket.xiaoli.data.secondclass.SecondClassTranscriptModule
import com.pocket.xiaoli.ui.components.EmptyState
import com.pocket.xiaoli.ui.components.ErrorCard
import com.pocket.xiaoli.ui.components.campusui.CampusCard
import com.pocket.xiaoli.ui.theme.LocalPocketXiaoliColors
import com.pocket.xiaoli.ui.theme.LocalSkin

/**
 * 第二课堂成绩单页的状态。
 *
 * [NeedLogin] 是一等状态而不是"某种失败"：数据源 B 的登录态会静默过期，
 * 混进失败之后 UI 只会说"加载失败"，用户不知道该去重新登录（架构文档 §4）。
 */
internal sealed interface SecondClassTranscriptState {
    data object Loading : SecondClassTranscriptState

    /** 登录态失效 —— 必须能把用户引导回**到梦空间自己的**登录页。 */
    data object NeedLogin : SecondClassTranscriptState

    data class Failed(val message: String, val detail: String? = null) : SecondClassTranscriptState

    data class Loaded(val transcript: SecondClassTranscript) : SecondClassTranscriptState
}

/**
 * 第二课堂成绩单的**纯内容组件**。
 *
 * ## 为什么抽成 `internal` 纯组件
 *
 * 与 `GradeEvidenceTable` / `FollowUpChips` / `MascotSettingsSection` 同一条理由：
 * 整页渲染要塞 `AppContainer` / ViewModel，渲不出来就**没有基线图**，而没有基线图时
 * "这一页长什么样"只能靠读代码猜（`CLAUDE.md` 记过这个教训）。抽出来，就能有一张图钉住它。
 *
 * ## ⚠️ 两条写这个界面时最容易破的硬约束
 *
 * 1. **不做任何求和或单位换算。** `credit` 的**单位各校不同**（烟理是「积分置换学分」，
 *    山东理工是活动直接发学分，见架构文档 §11），所以这里一律**原样显示服务端给的字符串**。
 *    界面上因此没有"总进度百分比"、没有进度条 —— 那需要先知道单位（B5）。
 * 2. **`NeedLogin` 的文案不许提"统一认证"。** 到梦空间是「学号 + 姓名」自助激活的独立账号体系，
 *    与学校统一认证无关（§11.3）。写错用户会去统一认证里找一个不存在的入口。
 */
@Composable
internal fun SecondClassTranscriptContent(
    state: SecondClassTranscriptState,
    modifier: Modifier = Modifier,
    onOpenPlatform: () -> Unit = {},
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
            SecondClassTranscriptState.Loading -> LoadingHint()
            SecondClassTranscriptState.NeedLogin -> NeedLoginCard(onLogin = onOpenPlatform)
            is SecondClassTranscriptState.Failed ->
                ErrorCard(message = state.message, detail = state.detail, onRetry = onRetry)

            is SecondClassTranscriptState.Loaded -> TranscriptBody(state.transcript, onOpenPlatform = onOpenPlatform)
        }
    }
}

/**
 * 载入中：**只有一行文字，刻意不放转圈**。
 *
 * `CLAUDE.md` 的"三类不确定性"第一条就点名了**无限动画会让基准图自己变红**
 * （`AiAvatar` 的 `THINKING` 就为此要求传确定性状态）。转圈是无限动画，
 * 放进基线只会让这张图时红时绿 —— 而它要守的是**布局**，不是动效。
 */
@Composable
private fun LoadingHint() {
    val colors = LocalPocketXiaoliColors.current
    Text(
        text = "正在读取第二课堂数据…",
        modifier = Modifier.padding(vertical = 36.dp),
        fontSize = 13.sp,
        color = colors.textSecondary,
    )
}

@Composable
private fun NeedLoginCard(onLogin: () -> Unit) {
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
        PillAction(text = "打开登录页", onClick = onLogin)
    }
}

@Composable
private fun TranscriptBody(transcript: SecondClassTranscript, onOpenPlatform: () -> Unit) {
    val colors = LocalPocketXiaoliColors.current
    // 成长记录与学分模块是**两张平行的表**（两个独立端点，见 SecondClassTranscript 的 KDoc）。
    // 早先这里从 modules 里摊平 items —— 那依赖"模块名逐字相等"，配不上就会静默丢记录。
    val records = transcript.records

    if (transcript.totalCredit.isNullOrBlank() && transcript.modules.isEmpty() && records.isEmpty()) {
        EmptyState(
            title = "还没有第二课堂记录",
            subtitle = "参加活动并在到梦空间完成签到后，学分与成长记录会出现在这里",
        )
        return
    }

    if (!transcript.totalCredit.isNullOrBlank()) {
        CampusCard(
            // 总学分是这一页的"门面"：给一张带主色底与描边的卡，
            // 不让它和下面的模块列表长得一模一样。
            containerColor = colors.surfaceHighlight,
            border = BorderStroke(1.dp, colors.accentMain.copy(alpha = 0.18f)),
        ) {
            Text("第二课堂学分", fontSize = 12.sp, color = colors.textSecondary)
            Row(verticalAlignment = Alignment.Bottom) {
                Text(
                    text = transcript.totalCredit,
                    fontSize = 30.sp,
                    fontWeight = FontWeight.Bold,
                    color = colors.accentMain,
                )
                Spacer(Modifier.width(6.dp))
                Text(
                    text = "（以到梦空间为准）",
                    fontSize = 11.sp,
                    color = colors.textTertiary,
                    modifier = Modifier.padding(bottom = 4.dp),
                )
            }
        }
    }

    if (transcript.modules.isNotEmpty()) {
        SectionTitle("学分模块")
        transcript.modules.forEach { ModuleCard(it) }
    }

    if (records.isNotEmpty()) {
        SectionTitle("成长记录")
        // 模块名取自**记录自己**（`record.module`），不再从学分模块那边借 —— 两张表是平行的。
        records.forEach { record -> GrowthRecordCard(record.module.orEmpty(), record) }
    }

    // 这一页只读：任何"报名 / 签到 / 申请学分"都要去平台自己的系统办（B3/B5）。
    Text(
        text = "报名、签到与学分申请请在到梦空间 App 内完成。",
        fontSize = 11.sp,
        color = colors.textTertiary,
        modifier = Modifier.padding(top = LocalSkin.current.spaceXs),
    )
    PillAction(text = "用 App 打开到梦空间", onClick = onOpenPlatform)
}

@Composable
private fun ModuleCard(module: SecondClassTranscriptModule) {
    val colors = LocalPocketXiaoliColors.current
    val earned = module.earnedCredit?.trim()?.takeIf { it.isNotEmpty() }
    val required = module.requiredCredit?.trim()?.takeIf { it.isNotEmpty() }
    // 只做**字符串相等**的比较，不做减法/百分比：单位口径未确认（B5）。
    // 长度格式对不上（"12" vs "12.0"）时只是不显示对勾，不会算错。
    val done = earned != null && required != null && earned == required

    CampusCard(
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (done) {
                Icon(
                    imageVector = Icons.Outlined.CheckCircle,
                    contentDescription = "已修满",
                    tint = colors.success,
                    modifier = Modifier.size(16.dp),
                )
                Spacer(Modifier.width(8.dp))
            }
            Text(
                text = module.name.ifBlank { "未命名模块" },
                modifier = Modifier.weight(1f),
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
                color = colors.textPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                // 只展示服务端给的两个数，**不做减法算"还差多少"**：单位未确认（B5）。
                text = moduleCreditText(module),
                fontSize = 12.sp,
                fontWeight = if (done) FontWeight.Medium else FontWeight.Normal,
                color = if (done) colors.success else colors.textSecondary,
            )
        }
    }
}

@Composable
private fun GrowthRecordCard(moduleName: String, record: SecondClassGrowthRecord) {
    val colors = LocalPocketXiaoliColors.current
    CampusCard(contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = record.title.ifBlank { "未命名记录" },
                modifier = Modifier.weight(1f),
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
                color = colors.textPrimary,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            if (!record.credit.isNullOrBlank()) {
                Spacer(Modifier.width(8.dp))
                Text(record.credit, fontSize = 12.sp, color = colors.accentMain)
            }
        }
        val meta = listOfNotNull(
            moduleName.takeIf { it.isNotBlank() },
            record.occurredAt?.takeIf { it.isNotBlank() },
        ).joinToString(" · ")
        if (meta.isNotBlank()) {
            Text(meta, fontSize = 11.sp, color = colors.textTertiary, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

/** 把"已得 / 要求"拼成一行；缺哪一半就只显示另一半，**不编造 0**。 */
internal fun moduleCreditText(module: SecondClassTranscriptModule): String {
    val earned = module.earnedCredit?.takeIf { it.isNotBlank() }
    val required = module.requiredCredit?.takeIf { it.isNotBlank() }
    return when {
        earned != null && required != null -> "$earned / $required"
        earned != null -> earned
        required != null -> "要求 $required"
        else -> "--"
    }
}
