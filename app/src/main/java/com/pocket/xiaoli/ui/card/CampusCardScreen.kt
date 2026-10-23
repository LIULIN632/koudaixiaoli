package com.pocket.xiaoli.ui.card

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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pocket.xiaoli.data.card.CampusCardBalance
import com.pocket.xiaoli.data.card.YxyCardRepository
import com.pocket.xiaoli.ui.components.ErrorCard
import com.pocket.xiaoli.ui.components.ServicePageTopBar
import com.pocket.xiaoli.ui.components.infocard.InfoCard
import com.pocket.xiaoli.ui.components.infocard.InfoRow
import com.pocket.xiaoli.ui.components.infocard.InfoTone
import com.pocket.xiaoli.ui.components.infocard.InfoValue
import com.pocket.xiaoli.ui.theme.LocalPocketXiaoliColors
import com.pocket.xiaoli.ui.theme.LocalSkin
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 校园卡余额（原生）。
 *
 * 数据来自**易校园**（云马 / 小富宝平台）自己的接口，见 [YxyCardRepository] ——
 * 以前校园卡只有"拉起易校园 App / 打开门户自己找"两条路，余额得进 App 才看得到。
 *
 * 这一页只做确定性的事：余额、更新时间、去充值。**不做消费流水**（接口有，
 * 但流水要按月拉、字段还没在真机上核过，先不铺开）。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CampusCardScreen(
    phone: String,
    password: String,
    onBack: () -> Unit,
    onOpenRecharge: () -> Unit,
) {
    val skin = LocalSkin.current
    val colors = LocalPocketXiaoliColors.current
    val context = LocalContext.current

    var detail by remember(phone) { mutableStateOf(YxyCardRepository.cachedBalance(phone)) }
    var loading by remember(phone) { mutableStateOf(false) }
    var error by remember(phone) { mutableStateOf<String?>(null) }
    var refreshTick by remember(phone) { mutableIntStateOf(0) }

    LaunchedEffect(phone, refreshTick) {
        if (phone.isBlank() || password.isBlank()) return@LaunchedEffect
        loading = true
        error = null
        YxyCardRepository.query(
            context = context,
            phone = phone,
            password = password,
            forceRefresh = refreshTick > 0,
        ).fold(
            onSuccess = { detail = it },
            onFailure = { error = it.message ?: "查询失败" },
        )
        loading = false
    }

    Scaffold(
        containerColor = colors.surfaceBase,
        topBar = {
            ServicePageTopBar(
                title = "校园卡",
                onBack = onBack,
                actions = {
                    IconButton(onClick = { refreshTick++ }, enabled = !loading) {
                        Icon(Icons.Default.Refresh, "刷新余额")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(
                    start = skin.screenH,
                    end = skin.screenH,
                    top = skin.spaceMd,
                    bottom = skin.spaceXxl,
                ),
            verticalArrangement = Arrangement.spacedBy(skin.spaceMd),
        ) {
            if (phone.isBlank() || password.isBlank()) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(skin.radiusCard),
                    colors = CardDefaults.cardColors(containerColor = colors.surfaceCard),
                ) {
                    Column(Modifier.padding(skin.spaceXl)) {
                        Text("先绑定校园卡账号", fontSize = 17.sp, fontWeight = FontWeight.Bold, color = colors.textPrimary)
                        Spacer(Modifier.height(skin.spaceSm))
                        Text(
                            "校园卡走的是易校园自己的账号（**手机号 + 密码**，不是学号）。" +
                                "到「我的 → 绑定信息 → 校园卡」填一次，这里就能看余额。",
                            fontSize = 13.sp,
                            color = colors.textSecondary,
                            lineHeight = 20.sp,
                        )
                    }
                }
            } else {
                BalanceCard(detail = detail)

                if (loading) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(18.dp),
                            strokeWidth = 2.dp,
                            color = colors.accentMain,
                        )
                        Spacer(Modifier.size(skin.spaceSm))
                        Text("正在查询余额…", fontSize = 13.sp, color = colors.textSecondary)
                    }
                }

                error?.let { message ->
                    ErrorCard(
                        title = "查询失败",
                        message = message,
                        onRetry = { refreshTick++ },
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(skin.spaceMd),
                ) {
                    OutlinedButton(onClick = { refreshTick++ }, enabled = !loading, modifier = Modifier.weight(1f)) {
                        Text("刷新余额")
                    }
                    Button(onClick = onOpenRecharge, modifier = Modifier.weight(1f)) {
                        Text("去充值")
                    }
                }
            }

            Text(
                text = "余额由易校园（小富宝）平台提供，只在本机保留 5 分钟；" +
                    "账号密码存在本机加密存储里，不上传、不经过我们的服务器。",
                fontSize = 11.sp,
                color = colors.textTertiary,
                lineHeight = 17.sp,
            )
        }
    }
}

@Composable
private fun BalanceCard(detail: CampusCardBalance?) {
    val skin = LocalSkin.current
    val colors = LocalPocketXiaoliColors.current
    val overdraw = (detail?.balance ?: 0.0) < 0
    // ⚠️ locale 取**可观察**的那个，再显式传给下面两个纯函数。
    //
    // 那两个函数本身没标 @Composable，但**被 Composable 调用** —— 在它们内部读
    // `Locale.getDefault()` 同样不会建立订阅（Compose lint 的 NonObservableLocale
    // 追踪调用链，一样会报）。改造方式：把 locale 变成参数，由调用方提供。
    val locale = LocalConfiguration.current.locales[0]

    InfoCard {
        InfoValue(
            value = detail?.let { formatMoney(it.balance, locale) } ?: "--",
            label = when {
                detail == null -> "余额待查询"
                overdraw -> "已欠费"
                else -> "校园卡余额"
            },
            tone = if (overdraw) InfoTone.NEGATIVE else InfoTone.NORMAL,
            large = true,
        )
        Spacer(Modifier.height(skin.spaceWide))
        detail?.userName?.takeIf { it.isNotBlank() }?.let { InfoRow("持卡人", it) }
        detail?.schoolName?.takeIf { it.isNotBlank() }?.let { InfoRow("学校", it) }
        InfoRow(
            "更新时间",
            detail?.let { formatTime(it.fetchedAtMillis, locale) } ?: "--",
        )
    }
}

/** @param locale 由调用方传入（必须来自 `LocalConfiguration`，见 [BalanceCard]） */
private fun formatMoney(value: Double, locale: Locale): String {
    val sign = if (value < 0) "-" else ""
    return sign + "¥" + String.format(locale, "%.2f", kotlin.math.abs(value))
}

/** @param locale 由调用方传入（必须来自 `LocalConfiguration`，见 [BalanceCard]） */
private fun formatTime(millis: Long, locale: Locale): String {
    if (millis <= 0L) return "刚刚"
    return SimpleDateFormat("MM-dd HH:mm", locale).format(Date(millis))
}
