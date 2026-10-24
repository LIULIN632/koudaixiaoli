package com.pocket.xiaoli.ui.electricity

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FlashOn
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.WarningAmber
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pocket.xiaoli.data.electricity.DormElectricityBalance
import com.pocket.xiaoli.data.proactive.ProactiveRules
import com.pocket.xiaoli.ui.components.AppCard
import com.pocket.xiaoli.ui.components.ErrorCard
import com.pocket.xiaoli.ui.components.ServiceEmptyCard
import com.pocket.xiaoli.ui.components.ServiceHeroBadge
import com.pocket.xiaoli.ui.components.ServiceHeroCard
import com.pocket.xiaoli.ui.components.ServiceHeroChip
import com.pocket.xiaoli.ui.components.ServiceHeroCount
import com.pocket.xiaoli.ui.components.ServicePageTopBar
import com.pocket.xiaoli.ui.components.ServiceSectionLabel
import com.pocket.xiaoli.ui.components.infocard.InfoRow
import com.pocket.xiaoli.ui.theme.LocalDarkMode
import com.pocket.xiaoli.ui.theme.LocalPocketXiaoliColors
import com.pocket.xiaoli.ui.theme.LocalSkin
import com.pocket.xiaoli.ui.theme.ServiceAccent
import com.pocket.xiaoli.ui.theme.onAccentColor
import com.pocket.xiaoli.ui.theme.serviceAccentColor

/**
 * 电费页的纯内容层：只吃 [ElectricityViewState] + 回调。
 *
 * 与 [ElectricityScreen] 的分工：那边管查询、缓存、微信跳转与 Snackbar；
 * 这边只管长什么样。好处是截图测试可以直接摆出「欠费 / 低余额 / 未绑定」
 * 这些真实查询很难稳定复现的状态。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ElectricityContent(
    state: ElectricityViewState,
    snackbarHostState: SnackbarHostState,
    onBack: () -> Unit,
    onRefresh: () -> Unit,
    onPayment: () -> Unit,
    onCopyLink: () -> Unit,
    onOpenPortal: () -> Unit,
) {
    val skin = LocalSkin.current
    val colors = LocalPocketXiaoliColors.current

    Scaffold(
        topBar = {
            ServicePageTopBar(
                title = "宿舍电费",
                onBack = onBack,
                actions = {
                    if (state.supported) {
                        IconButton(onClick = onRefresh, enabled = !state.loading) {
                            Icon(Icons.Default.Refresh, "刷新余额")
                        }
                    }
                },
            )
        },
        containerColor = colors.surfaceBase,
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = skin.spaceLg, vertical = skin.spaceSm),
            verticalArrangement = Arrangement.spacedBy(skin.spaceMd),
        ) {
            if (!state.supported) {
                UnsupportedHero(state)
                ServiceEmptyCard(
                    icon = Icons.Filled.FlashOn,
                    title = if (state.unbound) "先绑定宿舍" else "这个校区还没接原生余额",
                    message = if (state.unbound) {
                        "在「我的 → 绑定信息」里填上楼栋和房间号，南校宿舍的电费就能在这里直接查。"
                    } else {
                        "当前是「${state.campusName}」，这个校区暂时没有原生余额接口，打开校园门户一样能查。"
                    },
                    accent = ServiceAccent.PAYMENT,
                    actionLabel = "打开电费入口",
                    onAction = onOpenPortal,
                )
            } else {
                BalanceHero(state)
                if (state.loading) LoadingBar()
                state.error?.let { message ->
                    ErrorCard(title = "没查到余额", message = message, onRetry = onRefresh)
                }
                BalanceAlert(state.detail)
                ActionRow(state = state, onRefresh = onRefresh, onPayment = onPayment)
                PaymentGuideCard(weChatInstalled = state.weChatInstalled, onCopyLink = onCopyLink)
                Spacer(Modifier.height(skin.spaceXs))
                ServiceSectionLabel(title = "账户信息", accent = ServiceAccent.PAYMENT)
                AccountCard(state)
            }

            Text(
                text = "查询结果仅在本机内存保留 5 分钟；不使用模拟金额，不上传姓名、宿舍等个人信息。",
                fontSize = 11.sp,
                color = colors.textTertiary,
                lineHeight = 17.sp,
            )
        }
    }
}

// ── 状态与纯函数 ────────────────────────────────────────────────────────

/** 一屏所需的全部输入；`detail == null` 表示还没查到（而非余额为 0）。 */
internal data class ElectricityViewState(
    val supported: Boolean = false,
    val unbound: Boolean = true,
    val campusName: String = "",
    val dormLabel: String = "",
    val dormCode: String = "",
    val detail: DormElectricityBalance? = null,
    val loading: Boolean = false,
    val error: String? = null,
    val weChatInstalled: Boolean = false,
    /** 形如「10-24 09:30 · 实时」；未查到时为 null。 */
    val updatedText: String? = null,
)

/** 余额的语义档位。阈值只有一处：[ProactiveRules.LOW_BALANCE_ELECTRICITY]。 */
internal enum class BalanceLevel { NONE, NORMAL, LOW, OVERDRAFT }

internal fun balanceLevel(raw: String?): BalanceLevel {
    val value = raw?.trim()?.toDoubleOrNull() ?: return BalanceLevel.NONE
    return when {
        value < 0.0 -> BalanceLevel.OVERDRAFT
        value < ProactiveRules.LOW_BALANCE_ELECTRICITY -> BalanceLevel.LOW
        else -> BalanceLevel.NORMAL
    }
}

/** 金额原样展示（不加 ¥，单位由卡片统一给）；未查到给占位。 */
internal fun balanceDigits(raw: String?): String =
    raw?.trim().orEmpty().ifBlank { "--" }

/** 认人方式，两个字的差别对用户很重要：编号唯一，地址靠猜写法。 */
internal fun matchSourceLabel(detail: DormElectricityBalance?): String? = detail?.let {
    if (it.matchedByCode) "按宿舍编号" else "按楼栋 + 房间号"
}

private fun amountText(raw: String): String =
    if (raw.startsWith("-")) "¥" + raw.removePrefix("-") else "¥$raw"

// ── 主视觉 ──────────────────────────────────────────────────────────────

@Composable
private fun BalanceHero(state: ElectricityViewState) {
    val skin = LocalSkin.current
    val detail = state.detail
    val level = balanceLevel(detail?.balance)
    val subtitle = listOfNotNull(
        state.campusName.ifBlank { null },
        matchSourceLabel(detail),
    ).joinToString(" · ")

    ServiceHeroCard(accent = ServiceAccent.PAYMENT) {
        Row(verticalAlignment = Alignment.Top) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = state.dormLabel.ifBlank { "宿舍电费" },
                    fontSize = 22.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = Color.White,
                )
                if (subtitle.isNotBlank()) {
                    Spacer(Modifier.height(skin.spaceXs))
                    Text(
                        text = subtitle,
                        fontSize = 12.sp,
                        lineHeight = 18.sp,
                        color = Color.White.copy(alpha = 0.9f),
                    )
                }
            }
            Spacer(Modifier.width(skin.spaceSm))
            ServiceHeroBadge(icon = Icons.Filled.FlashOn)
        }

        Spacer(Modifier.height(skin.spaceLg))

        Text(
            text = when (level) {
                BalanceLevel.NONE -> "余额待查询"
                BalanceLevel.OVERDRAFT -> "已欠费"
                else -> "可用余额"
            },
            fontSize = 12.sp,
            color = Color.White.copy(alpha = 0.9f),
        )
        Spacer(Modifier.height(skin.spaceMicro))

        if (level == BalanceLevel.NONE) {
            Text(
                text = "待查询",
                fontSize = 30.sp,
                fontWeight = FontWeight.ExtraBold,
                color = Color.White,
            )
        } else {
            ServiceHeroCount(
                number = balanceDigits(detail?.balance),
                unit = "元",
                numberSize = 42.sp,
            )
        }

        if (state.updatedText != null) {
            Spacer(Modifier.height(skin.spaceMd))
            ServiceHeroChip(state.updatedText)
        }
    }
}

@Composable
private fun UnsupportedHero(state: ElectricityViewState) {
    val skin = LocalSkin.current
    ServiceHeroCard(accent = ServiceAccent.PAYMENT) {
        Row(verticalAlignment = Alignment.Top) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = if (state.unbound) "先把宿舍绑上" else "换到校园门户查",
                    fontSize = 22.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = Color.White,
                )
                Spacer(Modifier.height(skin.spaceXs))
                Text(
                    text = if (state.unbound) {
                        "绑定楼栋和房间号后，南校宿舍的电费能在这里直接看。"
                    } else {
                        "当前是「${state.campusName}」，这个校区暂时没有原生余额接口。"
                    },
                    fontSize = 12.sp,
                    lineHeight = 18.sp,
                    color = Color.White.copy(alpha = 0.9f),
                )
            }
            Spacer(Modifier.width(skin.spaceSm))
            ServiceHeroBadge(icon = Icons.Filled.FlashOn)
        }
    }
}

// ── 状态提示 ────────────────────────────────────────────────────────────

@Composable
private fun LoadingBar() {
    val skin = LocalSkin.current
    val colors = LocalPocketXiaoliColors.current
    Row(verticalAlignment = Alignment.CenterVertically) {
        LinearProgressIndicator(
            modifier = Modifier
                .weight(1f)
                .height(3.dp)
                .clip(RoundedCornerShape(50)),
            color = colors.accentMain,
            trackColor = colors.surfaceAlt,
        )
        Spacer(Modifier.width(skin.spaceSm))
        Text("正在查询实时余额…", fontSize = 11.sp, color = colors.textTertiary)
    }
}

/**
 * 低余额 / 欠费提示。
 *
 * 阈值复用主动提醒那一份（[ProactiveRules.LOW_BALANCE_ELECTRICITY]），
 * 不再在界面里写一个 20 —— 两处阈值一旦分叉，会出现「首页提醒了、电费页说正常」。
 */
@Composable
private fun BalanceAlert(detail: DormElectricityBalance?) {
    val skin = LocalSkin.current
    val colors = LocalPocketXiaoliColors.current
    val isDark = LocalDarkMode.current
    val level = balanceLevel(detail?.balance)
    if (level != BalanceLevel.LOW && level != BalanceLevel.OVERDRAFT) return

    val raw = balanceDigits(detail?.balance)
    val tint = if (level == BalanceLevel.OVERDRAFT) colors.danger else colors.warning
    val title = if (level == BalanceLevel.OVERDRAFT) "已经欠费" else "余额偏低"
    val message = if (level == BalanceLevel.OVERDRAFT) {
        "当前欠 ¥${raw.removePrefix("-")}，充值后才能继续用电。"
    } else {
        "只剩 ${amountText(raw)}，低于 ${ProactiveRules.LOW_BALANCE_ELECTRICITY.toInt()} 元提醒线。" +
            "先充一点，别等到断电。"
    }

    AppCard(
        modifier = Modifier.fillMaxWidth(),
        border = BorderStroke(1.dp, tint.copy(alpha = 0.45f)),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(skin.cardPaddingLarge),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(38.dp)
                    .clip(CircleShape)
                    .background(tint.copy(alpha = if (isDark) 0.24f else 0.14f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Outlined.WarningAmber,
                    contentDescription = null,
                    tint = tint,
                    modifier = Modifier.size(20.dp),
                )
            }
            Spacer(Modifier.width(skin.spaceMd))
            Column(modifier = Modifier.weight(1f)) {
                Text(title, fontSize = 14.sp, fontWeight = FontWeight.Bold, color = tint)
                Spacer(Modifier.height(skin.spaceMicro))
                Text(
                    text = message,
                    fontSize = 12.sp,
                    lineHeight = 18.sp,
                    color = colors.textSecondary,
                )
            }
        }
    }
}

// ── 操作 ────────────────────────────────────────────────────────────────

@Composable
private fun ActionRow(
    state: ElectricityViewState,
    onRefresh: () -> Unit,
    onPayment: () -> Unit,
) {
    val skin = LocalSkin.current
    val colors = LocalPocketXiaoliColors.current
    val shape = RoundedCornerShape(skin.radiusCard)

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(skin.spaceMd),
    ) {
        OutlinedButton(
            onClick = onRefresh,
            enabled = !state.loading,
            shape = shape,
            modifier = Modifier.weight(1f),
            contentPadding = PaddingValues(horizontal = skin.spaceMd, vertical = skin.spaceSm),
        ) {
            Icon(
                imageVector = Icons.Default.Refresh,
                contentDescription = null,
                modifier = Modifier.size(16.dp),
            )
            Spacer(Modifier.width(skin.spaceXs))
            Text("刷新", fontSize = 14.sp)
        }
        Button(
            onClick = onPayment,
            shape = shape,
            modifier = Modifier.weight(1.35f),
            contentPadding = PaddingValues(horizontal = skin.spaceMd, vertical = skin.spaceSm),
            colors = ButtonDefaults.buttonColors(
                containerColor = colors.accentMain,
                contentColor = onAccentColor(),
            ),
        ) {
            // 文案如实说明"要去哪"：缴费只会在微信里完成，写"去缴费"会让人以为在应用内完成
            Text(
                text = if (state.weChatInstalled) "去微信缴费" else "去缴费",
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
            )
        }
    }
}

// ── 缴费说明 ────────────────────────────────────────────────────────────

@Composable
private fun PaymentGuideCard(weChatInstalled: Boolean, onCopyLink: () -> Unit) {
    val skin = LocalSkin.current
    val colors = LocalPocketXiaoliColors.current
    AppCard(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(skin.cardPaddingLarge)) {
            Text(
                text = if (weChatInstalled) "缴费要走微信" else "缴费链接",
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
                color = colors.textPrimary,
            )
            Spacer(Modifier.height(skin.spaceXs))
            Text(
                text = "学校缴费系统只在微信内提供支付入口；在其它应用里打开会退化成" +
                    "「增绑新用户」页。余额与户号查询不受影响。",
                fontSize = 12.sp,
                lineHeight = 18.sp,
                color = colors.textSecondary,
            )
            Spacer(Modifier.height(skin.spaceMd))

            if (weChatInstalled) {
                GuideStep(1, "点上面的按钮：复制缴费链接并打开微信")
                GuideStep(2, "在微信里粘贴到「文件传输助手」或任意聊天窗口")
                GuideStep(3, "发送后点开链接，就能缴费")
            } else {
                Text(
                    text = "本机没有检测到微信。可以先把链接复制走，再到微信里打开。",
                    fontSize = 12.sp,
                    lineHeight = 18.sp,
                    color = colors.textSecondary,
                )
                Spacer(Modifier.height(skin.spaceMd))
                OutlinedButton(
                    onClick = onCopyLink,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(skin.radiusCard),
                    contentPadding = PaddingValues(vertical = skin.spaceSm),
                ) {
                    Icon(
                        imageVector = Icons.Outlined.ContentCopy,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                    )
                    Spacer(Modifier.width(skin.spaceXs))
                    Text("复制缴费链接", fontSize = 13.sp)
                }
            }
        }
    }
}

@Composable
private fun GuideStep(index: Int, text: String) {
    val skin = LocalSkin.current
    val colors = LocalPocketXiaoliColors.current
    val accent = serviceAccentColor(ServiceAccent.PAYMENT)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = skin.spaceMicro),
        verticalAlignment = Alignment.Top,
    ) {
        Box(
            modifier = Modifier
                .size(18.dp)
                .clip(CircleShape)
                .background(accent.copy(alpha = 0.16f)),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = "$index",
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                color = accent,
            )
        }
        Spacer(Modifier.width(skin.spaceSm))
        Text(
            text = text,
            modifier = Modifier.weight(1f),
            fontSize = 12.sp,
            lineHeight = 18.sp,
            color = colors.textSecondary,
        )
    }
}

// ── 账户信息 ────────────────────────────────────────────────────────────

@Composable
private fun AccountCard(state: ElectricityViewState) {
    val skin = LocalSkin.current
    val detail = state.detail
    AppCard(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(
                horizontal = skin.cardPaddingLarge,
                vertical = skin.spaceMd,
            ),
        ) {
            // 先回答「这是我的账户吗」，再说绑定关系
            InfoRow("用户编号", detail?.accountNo ?: "--")
            InfoRow("用户地址", detail?.address ?: "--")
            InfoRow("匹配方式", matchSourceLabel(detail) ?: "待查询")
            InfoRow("绑定宿舍", state.dormLabel.ifBlank { "--" })
            InfoRow("服务校区", state.campusName)
            InfoRow(
                "宿舍编号",
                state.dormCode.ifBlank { "未填（按楼栋 + 房间号匹配）" },
            )
        }
    }
}
