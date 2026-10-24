package com.pocket.xiaoli.ui.electricity

import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import com.pocket.xiaoli.data.CampusInfo
import com.pocket.xiaoli.data.CampusServiceKeys
import com.pocket.xiaoli.data.electricity.DormElectricityBalance
import com.pocket.xiaoli.data.electricity.DormElectricityRepository
import com.pocket.xiaoli.data.electricity.WeChatUrlLauncher
import com.pocket.xiaoli.data.model.DormBuildingCatalog
import com.pocket.xiaoli.data.model.DormInfo
import com.pocket.xiaoli.data.model.DormServiceRouter
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 宿舍电费原生页（有状态层）。
 *
 * 南校宿舍直接显示实时余额、户号、地址和缴费入口；其他校区 / 未绑定宿舍
 * 会降级到校园门户或提示先绑定。没有真实接口时绝不伪造金额。
 *
 * 视觉全部在 [ElectricityContent]：那边是纯函数式的，截图测试可以摆出
 * 「欠费 / 低余额 / 未绑定」这些真实查询很难稳定复现的状态。
 */
@Composable
fun ElectricityScreen(
    dormInfo: DormInfo?,
    fallbackCampus: CampusInfo,
    onBack: () -> Unit,
    onOpenWeb: (url: String, title: String) -> Unit,
    onBalanceLoaded: (DormElectricityBalance?) -> Unit = {},
) {
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

    // locale 取可观察的那个，显式传给 formatTime。
    // 在纯函数里读 `Locale.getDefault()` 不建立订阅（被 Composable 调用时同样命中
    // Compose lint 的 NonObservableLocale）—— 同 CampusCardScreen 的处理方式。
    val locale = LocalConfiguration.current.locales[0]
    val updatedText = detail?.let {
        formatTime(it.fetchedAtMillis, locale) + if (it.fromCache) " · 缓存" else " · 实时"
    }

    ElectricityContent(
        state = ElectricityViewState(
            supported = supported,
            unbound = dormInfo == null || !dormInfo.isBound,
            campusName = campus.shortName,
            dormLabel = dormInfo?.label.orEmpty(),
            dormCode = dormInfo?.dormCode.orEmpty(),
            detail = detail,
            loading = loading,
            error = error,
            weChatInstalled = weChatInstalled,
            updatedText = updatedText,
        ),
        snackbarHostState = snackbarHostState,
        onBack = onBack,
        onRefresh = { refresh() },
        onPayment = { openPayment() },
        onCopyLink = { copyPayLink() },
        onOpenPortal = { openPortal() },
    )
}

/** @param locale 由调用方传入（必须来自 `LocalConfiguration`，见 [ElectricityScreen]） */
private fun formatTime(millis: Long, locale: Locale): String {
    if (millis <= 0L) return "刚刚"
    return SimpleDateFormat("MM-dd HH:mm", locale).format(Date(millis))
}
