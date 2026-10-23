package com.pocket.xiaoli.ui.electricity

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
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
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pocket.xiaoli.data.CampusInfo
import com.pocket.xiaoli.data.CampusServiceKeys
import com.pocket.xiaoli.data.electricity.DormElectricityBalance
import com.pocket.xiaoli.data.electricity.DormElectricityRepository
import com.pocket.xiaoli.data.electricity.WeChatUrlLauncher
import com.pocket.xiaoli.data.model.DormBuildingCatalog
import com.pocket.xiaoli.data.model.DormInfo
import com.pocket.xiaoli.data.model.DormServiceRouter
import kotlinx.coroutines.launch
import com.pocket.xiaoli.ui.components.ErrorCard
import com.pocket.xiaoli.ui.components.ServicePageTopBar
import com.pocket.xiaoli.ui.theme.LocalPocketXiaoliColors
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import com.pocket.xiaoli.ui.theme.LocalSkin
import com.pocket.xiaoli.ui.components.infocard.InfoRow
import com.pocket.xiaoli.ui.components.infocard.InfoCard
import com.pocket.xiaoli.ui.components.infocard.InfoValue
import com.pocket.xiaoli.ui.components.infocard.InfoTone

/**
 * 宿舍电费原生页。
 *
 * 南校宿舍直接显示实时余额、户号、地址和缴费入口；其他校区 / 未绑定宿舍
 * 会降级到校园门户或提示先绑定。没有真实接口时绝不伪造金额。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ElectricityScreen(
    dormInfo: DormInfo?,
    fallbackCampus: CampusInfo,
    onBack: () -> Unit,
    onOpenWeb: (url: String, title: String) -> Unit,
    onBalanceLoaded: (DormElectricityBalance?) -> Unit = {},
) {
    val skin = LocalSkin.current
    val colors = LocalPocketXiaoliColors.current
    val context = LocalContext.current
    val campus = DormServiceRouter.serviceCampus(dormInfo, fallbackCampus)
    val supported = dormInfo != null && dormInfo.isBound && campus.id == DormBuildingCatalog.CAMPUS_SOUTH
    val dormKey = dormInfo?.let {
        // 宿舍编号也要进 key：改了编号就是"换了一个认人方式"，旧结果必须作废
        // orEmpty()：宿舍来自 JSON 存档，老存档里这些字段可能是 null
        listOf(
            it.building.orEmpty(),
            it.room.orEmpty(),
            it.serviceCampusId.orEmpty(),
            it.dormCode.orEmpty(),
        ).joinToString("|")
    } ?: "unbound"

    var detail by remember(dormKey) { mutableStateOf(DormElectricityRepository.cachedSnapshot(dormInfo)) }
    var loading by remember(dormKey) { mutableStateOf(false) }
    var error by remember(dormKey) { mutableStateOf<String?>(null) }
    var refreshTick by remember(dormKey) { mutableStateOf(0) }
    var forceNext by remember(dormKey) { mutableStateOf(false) }

    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }

    /**
     * 缴费为什么不能在本 App 内完成（2026-09-30 查清，证据见表内注释与 `docs/安全审计-TLS.md`）。
     *
     * 不要试图"把缴费页修好"—— 它依赖**微信 openid 会话**，我们从 App 侧拿不到：
     *
     * 1. `payItemIndex?accountId=..&itemId=..` 能正常显示余额/户号（这步不依赖微信）
     * 2. 点「立即缴费」→ 跳 `YDPost`
     * 3. 而 `YDPost` 在**我们发起的任何请求**里都只返回一个空壳页（2432 字节），
     *    页面上**唯一可点的东西就是「增绑新用户」→ `payItemAdd`**
     *    —— 这就是用户看到的"点缴费跳到了绑定界面"
     *
     * 已验证排除的原因（全部无效）：会话 Cookie、微信 User-Agent、
     * `Referer: servicewechat.com`、`X-Requested-With: com.tencent.mm`、GET 与 POST。
     *
     * ⇒ 服务端只在微信里给出支付入口。**所以这一页的正确姿势是把人送去微信**，
     *   而不是给一个点了会落到绑定页的按钮。
     */
    val weChatInstalled = remember { WeChatUrlLauncher.isWeChatInstalled(context) }

    /** 缴费页的直达地址（拿不到匹配账户时为 null，此时只能走总入口）。 */
    val payUrl = DormElectricityRepository.matchedPaymentUrl(detail)

    fun refresh() {
        forceNext = true
        refreshTick++
    }

    LaunchedEffect(dormKey, refreshTick) {
        val dorm = dormInfo ?: return@LaunchedEffect
        if (!supported) return@LaunchedEffect
        loading = true
        error = null
        val result = DormElectricityRepository.query(dorm, forceRefresh = forceNext)
        forceNext = false
        result.fold(
            onSuccess = { value ->
                detail = value
                onBalanceLoaded(value)
                if (value == null) {
                    // 光说"没匹配到"没用，得说清下一步。认人有两条路：宿舍编号是唯一标识，
                    // 楼栋 + 房间号则是拿地址去猜写法（`北校10楼南602` / `南校区10栋602` 都见过）。
                    val code = dorm.dormCode.orEmpty()
                    error = if (code.isBlank()) {
                        "没有匹配到该宿舍的缴费账户：先核对楼栋 / 房间号。" +
                            "缴费页上如果有「用户编号」，把它填进绑定宿舍的「宿舍编号」，就能精确认到本人那一间。"
                    } else {
                        "填了宿舍编号「$code」但没对上：核对它是否和缴费页的「用户编号」一致" +
                            "（大小写、横线、井号可以不管），再核对楼栋 / 房间号。"
                    }
                }
            },
            onFailure = { e ->
                error = e.message ?: "查询失败"
                if (detail != null) error = "刷新失败，已显示上次结果"
            },
        )
        loading = false
    }

    fun openPortal() {
        val url = fallbackCampus.serviceUrls[CampusServiceKeys.ELECTRICITY]
            ?: "http://ehall.yitsd.edu.cn/new/index.html"
        onOpenWeb(url, "宿舍电费")
    }

    /**
     * 缴费：把链接送到微信去打开。
     *
     * ⚠️ v1.1.3 曾用 `weixin://dl/business/?t=<编码后的URL>` 深链，**实测不能正常跳转**
     * （那是微信支付的回跳格式，且目标 URL 被整体编码后微信未必解码）。
     * 现在改成"复制 + 拉起微信"，与项目里已验证可行的
     * `SchoolServiceLauncher.openWeChatMiniProgram` 同一套路。
     *
     * 拉起微信本身用 `getLaunchIntentForPackage`（而不是 `getPackageInfo`）——
     * 后者在部分 ROM 上会因包可见性限制抛异常，把"装了微信"误判为"没装"，
     * 于是代码根本走不到拉起这一步。
     */
    fun openPayment() {
        // 认得出本人账户 → 直达本人那一间；认不出 → 只能给总入口
        // （认不出时**不能**退到 payItemAdd「增绑新用户」，那正是"跳到绑定界面"的成因）
        val target = payUrl ?: DormElectricityRepository.paymentUrl(detail)

        when (WeChatUrlLauncher.sendToWeChat(context, target)) {
            WeChatUrlLauncher.Outcome.CopiedAndOpened ->
                scope.launch {
                    snackbarHostState.showSnackbar("链接已复制，在微信里粘贴打开即可缴费")
                }

            WeChatUrlLauncher.Outcome.CopiedOnly ->
                scope.launch {
                    snackbarHostState.showSnackbar("链接已复制 —— 请手动打开微信，粘贴到任意聊天窗口")
                }

            WeChatUrlLauncher.Outcome.CopyFailed ->
                scope.launch {
                    snackbarHostState.showSnackbar("复制失败，已改为在本页打开（缴费仍需在微信里完成）")
                }.also { onOpenWeb(target, "宿舍电费") }

            WeChatUrlLauncher.Outcome.InvalidUrl ->
                // 走到这里说明缴费地址不合法（不该发生）。如实说，不假装没事。
                scope.launch {
                    snackbarHostState.showSnackbar("缴费地址不可用，请先在绑定信息里核对宿舍编号")
                }
        }
    }

    /** 只复制缴费链接（没装微信、或用户主动选择自己带走链接时用）。 */
    fun copyPayLink() {
        val target = payUrl ?: DormElectricityRepository.paymentUrl(detail)
        when (WeChatUrlLauncher.copyLink(context, target)) {
            WeChatUrlLauncher.Outcome.CopiedOnly ->
                scope.launch { snackbarHostState.showSnackbar("缴费链接已复制") }

            WeChatUrlLauncher.Outcome.CopyFailed ->
                scope.launch { snackbarHostState.showSnackbar("复制失败，请稍后重试") }

            else ->
                scope.launch {
                    snackbarHostState.showSnackbar("缴费地址不可用，请先核对宿舍编号")
                }
        }
    }

    Scaffold(
        topBar = {
            ServicePageTopBar(
                title = "宿舍电费",
                onBack = onBack,
                actions = {
                    IconButton(onClick = { refresh() }, enabled = supported && !loading) {
                        Icon(Icons.Default.Refresh, "刷新余额")
                    }
                }
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
                .padding(skin.spaceLg),
            verticalArrangement = Arrangement.spacedBy(skin.spaceMd),
        ) {
            if (!supported) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(skin.radiusCard),
                    colors = CardDefaults.cardColors(containerColor = colors.surfaceCard),
                ) {
                    Column(modifier = Modifier.padding(skin.spaceXl)) {
                        Text(
                            text = if (dormInfo == null || !dormInfo.isBound) "先绑定宿舍" else "本校区电费入口",
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            color = colors.textPrimary,
                        )
                        Spacer(Modifier.height(skin.spaceSm))
                        Text(
                            text = if (dormInfo == null || !dormInfo.isBound) {
                                "在「我的 → 绑定信息」绑定楼栋和房间号后，南校宿舍电费可在这里原生查询。"
                            } else {
                                "当前宿舍服务校区为「${campus.shortName}」。该校区电费暂未提供原生余额接口，请打开校园门户查询。"
                            },
                            fontSize = 13.sp,
                            color = colors.textSecondary,
                            lineHeight = 20.sp,
                        )
                        Spacer(Modifier.height(skin.spaceLg))
                        Button(onClick = { openPortal() }) {
                            Text("打开电费入口")
                        }
                    }
                }
            } else {
                // supported 为 true 就意味着 dormInfo 非空，K2 的智能转换已经把类型收窄了
                BalanceCard(detail = detail, dorm = dormInfo, campusName = campus.shortName)

                if (loading) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(skin.radiusCard))
                            .background(colors.surfaceCard)
                            .padding(skin.spaceLg),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(18.dp),
                            strokeWidth = 2.dp,
                            color = colors.accentMain,
                        )
                        Spacer(Modifier.size(10.dp))
                        Text("正在查询实时余额…", fontSize = 13.sp, color = colors.textSecondary)
                    }
                }

                error?.let { message ->
                    // 统一错误卡片：项目里此前是"每页一行红字"，没有说清下一步能做什么。
                    // 这里补上「重试」（refresh() 本来就在按钮区，但错误旁边再给一次更直接）
                    ErrorCard(
                        title = "查询失败",
                        message = message,
                        onRetry = { refresh() },
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(skin.spaceMd),
                ) {
                    OutlinedButton(
                        onClick = { refresh() },
                        enabled = !loading,
                        modifier = Modifier.weight(1f),
                    ) {
                        Text("刷新余额")
                    }
                    Button(
                        onClick = { openPayment() },
                        modifier = Modifier.weight(1f),
                    ) {
                        // 文案如实说明"要去哪"：这个按钮不会在本 App 内完成缴费，
                        // 点下去会带着链接打开微信。写"去缴费"会让人以为在应用内完成。
                        Text(if (weChatInstalled) "去微信缴费" else "去缴费")
                    }
                }

                // ⚠️ 必须说清"为什么不能在 App 里缴" + "点了之后要做什么"。
                //
                // 前半句是原因：不说清的话，用户点了缴费、看到"增绑新用户"绑定页，
                // 只会以为是我们做坏了 —— 实际是这一步依赖微信 openid 会话。
                //
                // 后半句是**操作步骤**。这一条是 v1.1.3 的教训：当时只弹一句
                // "链接已复制"，但没告诉用户"粘到哪里"（很多人不知道微信可以发给自己），
                // 结果看起来就像"点了没用"。所以把步骤写在按钮**下面**，点之前就看得到。
                if (weChatInstalled) {
                    Text(
                        text = "缴费只在微信里能完成（学校缴费系统只在微信内提供支付入口，" +
                            "在其它应用里打开会退化成「增绑新用户」页；余额与户号查询不受影响）。\n\n" +
                            "点上面的按钮后会：① 复制缴费链接 ② 自动打开微信。" +
                            "在微信里把链接粘贴到「文件传输助手」或任意聊天窗口，发送后点开即可缴费。",
                        fontSize = 11.sp,
                        color = colors.textTertiary,
                        lineHeight = 17.sp,
                    )
                } else {
                    Text(
                        text = "缴费需要微信：学校缴费系统只在微信内提供支付入口，" +
                            "本机未检测到微信，请在微信中打开下面的链接。",
                        fontSize = 11.sp,
                        color = colors.textTertiary,
                        lineHeight = 17.sp,
                    )
                    Spacer(Modifier.height(skin.spaceSm))
                    OutlinedButton(
                        onClick = { copyPayLink() },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text("复制缴费链接")
                    }
                }
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

@Composable
private fun BalanceCard(
    detail: DormElectricityBalance?,
    dorm: DormInfo,
    campusName: String,
) {
    val skin = LocalSkin.current
    val raw = detail?.balance?.trim().orEmpty()
    val hasBalance = raw.isNotBlank()
    val overdraw = raw.startsWith("-")
    // ⚠️ locale 取可观察的那个，显式传给 formatTime。
    // 在纯函数里读 `Locale.getDefault()` 不建立订阅（被 Composable 调用时同样命中
    // Compose lint 的 NonObservableLocale）—— 同 CampusCardScreen 的处理方式。
    val locale = LocalConfiguration.current.locales[0]

    // 用共享的信息卡原语：容器/主值/明细行的字号、间距、颜色都来自设计系统，
    // 因此与「校园卡余额」等同类卡片观感一致，而不是各写一套。
    InfoCard {
        InfoValue(
            value = formatBalance(raw),
            label = if (!hasBalance) "余额待查询" else if (overdraw) "已欠费" else "可用余额",
            tone = if (overdraw) InfoTone.NEGATIVE else InfoTone.NORMAL,
            large = true,
        )
        Spacer(Modifier.height(skin.spaceWide))

        InfoRow("绑定宿舍", dorm.label)
        InfoRow("服务校区", campusName)
        // 「这是我的账户吗」是这一页最要紧的问题，所以把**认人方式**也写出来：
        // 按编号认（唯一标识）比按地址认（猜写法）可靠得多，用户自己就能判断该不该信。
        InfoRow("宿舍编号", dorm.dormCode.orEmpty().ifBlank { "未填（按楼栋 + 房间号匹配）" })
        if (detail != null) {
            InfoRow("匹配方式", if (detail.matchedByCode) "宿舍编号" else "楼栋 + 房间号")
        }
        InfoRow("用户编号", detail?.accountNo ?: "--")
        InfoRow("用户地址", detail?.address ?: "--")
        InfoRow(
            "更新时间",
            if (detail == null) "--" else {
                formatTime(detail.fetchedAtMillis, locale) + if (detail.fromCache) " · 缓存" else " · 实时"
            },
        )
    }
}


private fun formatBalance(raw: String): String {
    if (raw.isBlank()) return "--"
    return if (raw.startsWith("-")) "-¥" + raw.removePrefix("-") else "¥$raw"
}

/** @param locale 由调用方传入（必须来自 `LocalConfiguration`，见 [BalanceCard]） */
private fun formatTime(millis: Long, locale: Locale): String {
    if (millis <= 0L) return "刚刚"
    return SimpleDateFormat("MM-dd HH:mm", locale).format(Date(millis))
}
