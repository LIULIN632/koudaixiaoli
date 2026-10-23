package com.pocket.xiaoli.data.qzxy

import com.pocket.xiaoli.data.qzxy.QzxyProjectInfo
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.StateFlow

/**
 * 趣智校园账号 + 设备 + 用水的唯一入口。
 *
 * 移植自 [`yehu-imei/linyu`](https://github.com/yehu-imei/linyu)（**MIT**）的
 * `data/AuthRepository.kt` + `data/ShowerController.kt`。
 *
 * ## 分工
 *
 * - [QzxySessionStore]：登录令牌（加密）
 * - [QzxyValveStore]：用水运行态（明文，无凭据）
 * - 本类：把接口调通，并把「查询 → 指令 → 轮询确认」封装好
 *
 * ## 全部判据都是实测来的，注释里逐条标了出处
 *
 * 这些规则**错了不会编译失败、也不会立刻报错**，只会表现为「显示空闲但水在流」
 * 或「提示失败但已扣费」——所以每一处都写清为什么这么判。
 */
class QzxyRepository(
    private val store: QzxySessionStore,
    private val valveStore: QzxyValveStore,
    private val deviceStore: QzxyDeviceStore,
) {

    val session: StateFlow<QzxySession?> = store.session
    val running: StateFlow<QzxyRunningState?> = valveStore.running

    /** 用过的热水器台账（扫码得到的设备清单）。 */
    val knownDevices: StateFlow<List<QzxyKnownDevice>> = deviceStore.devices

    fun currentSession(): QzxySession? = store.current()
    val isLoggedIn: Boolean get() = store.current() != null

    // ─────────────────────────────────────────────
    //  登录
    // ─────────────────────────────────────────────

    /**
     * 发短信验证码。不需要登录态 —— 已实测（2026-09-29）用无效号段调用返回
     * `{"success":true,"errorCode":0,"errorMessage":"成功"}`。
     */
    suspend fun sendSmsCode(telephone: String): Result<Unit> = runCatching {
        val resp = QzxyApi.sendSmsCode(telephone.trim())
        // ⚠️ 业务失败也是 HTTP 200，必须看 success
        if (!resp.success) throw QzxyException(resp.displayMessage ?: "验证码发送失败")
    }

    /** 短信验证码登录；未注册的手机号会被服务端自动注册。成功后立即落盘。 */
    suspend fun loginWithSms(telephone: String, smsCode: String): Result<QzxySession> = runCatching {
        val tel = telephone.trim()
        val resp = QzxyApi.smsLogin(tel, smsCode.trim())
        val data = resp.data
        if (!resp.success || data == null || data.loginCode.isBlank()) {
            throw QzxyException(resp.displayMessage ?: "登录失败")
        }
        store.save(data, tel)
        store.current() ?: throw QzxyException("登录态写入失败")
    }

    /**
     * 退出登录。
     *
     * ⚠️ **正在用水时不许退** —— 退掉就拿不到 `loginCode`，那一单再也关不掉、也算不了账，
     * 只能等设备自己超时关停。这条拦截放在这里而不是界面层，
     * 因为将来别处（如设置页）也可能加退出入口。
     */
    fun logout(): Result<Unit> {
        val running = valveStore.current()
        if (running != null) {
            return Result.failure(QzxyException("正在用水，请先结束再退出账号"))
        }
        store.clear()
        return Result.success(Unit)
    }

    /** 拉一次账号信息并回填姓名。失败不算错误（姓名只是装饰）。 */
    suspend fun refreshProfile() {
        val auth = store.current()?.auth ?: return
        runCatching {
            val resp = QzxyApi.accountInfo(auth)
            if (resp.success) store.updateName(resp.data?.name)
        }
    }

    // ─────────────────────────────────────────────
    //  余额 / 学校 / 账单
    // ─────────────────────────────────────────────

    /** 一卡通真实余额。未签约免密支付时 `amount` 为 null —— 这不是错误。 */
    suspend fun campusBalance(): Result<QzxyCampusUserInfo> = runCatching {
        val auth = requireAuth()
        val resp = QzxyApi.campusUserInfo(auth)
        if (!resp.success) throw QzxyException(resp.displayMessage ?: "余额查询失败")
        resp.data ?: throw QzxyException("服务端未返回余额")
    }

    suspend fun wallet(): Result<QzxyWallet> = runCatching {
        val auth = requireAuth()
        val resp = QzxyApi.wallet(auth)
        if (!resp.success) throw QzxyException(resp.displayMessage ?: "钱包查询失败")
        resp.data ?: throw QzxyException("服务端未返回钱包")
    }

    suspend fun schoolName(): String? = runCatching {
        val auth = store.current()?.auth ?: return@runCatching null
        val resp = QzxyApi.projectInfo(auth)
        if (resp.success) resp.data?.projectName else null
    }.getOrNull()

    /** 学校/项目的完整信息（含服务电话、运营公司）—— 报修时有用。 */
    suspend fun projectInfo(): QzxyProjectInfo? = runCatching {
        val auth = store.current()?.auth ?: return@runCatching null
        val resp = QzxyApi.projectInfo(auth)
        if (resp.success) resp.data else null
    }.getOrNull()

    /**
     * 本账号名下的**全部项目**。
     *
     * ⚠️ 一个账号可以有多个项目（洗浴热水 / 饮水 …），它们的钱包与账单是**分开**的，
     * 而登录响应只下发其中一个。界面应当让用户知道"现在查的是哪个项目"，
     * 否则余额对不上时用户完全无从判断。详见 [QzxyUserProject]。
     */
    suspend fun userProjects(): Result<List<QzxyUserProject>> = runCatching {
        val auth = requireAuth()
        val resp = QzxyApi.userProjectList(auth)
        if (!resp.success) throw QzxyException(resp.displayMessage ?: "项目列表查询失败")
        resp.data.orEmpty()
    }

    /** @param month 格式 `yyyy-MM` */
    suspend fun billList(month: String): Result<List<QzxyBill>> = runCatching {
        val auth = requireAuth()
        val resp = QzxyApi.billList(month, auth)
        if (!resp.success) throw QzxyException(resp.displayMessage ?: "账单查询失败")
        resp.data.orEmpty().mapNotNull { it.consumeBillDTO }
    }

    // ─────────────────────────────────────────────
    //  设备
    // ─────────────────────────────────────────────

    /**
     * 用设备 MAC 换详情。
     *
     * 这一步是「扫码」与「控制」之间的必经环节：机身二维码里只有 MAC，
     * 而开阀/查订单要的是 `snCode`。
     */
    suspend fun deviceByMac(mac: String): Result<QzxyDeviceInfo> = runCatching {
        val auth = requireAuth()
        val resp = QzxyApi.deviceByMac(mac, auth)
        if (!resp.success) throw QzxyException(resp.displayMessage ?: "未找到该设备")
        resp.data ?: throw QzxyException("服务端未返回设备信息")
    }

    /**
     * 扫码文本 → 设备详情。
     *
     * 合并了「解析二维码」与「查设备」两步，因为界面上它们永远是连着做的。
     * 二维码不是本服务商的格式时，[QzxyDeviceCode.parseDeviceMac] 返回 null，
     * 这里直接给出**明确的**提示，而不是拿垃圾串去请求接口。
     *
     * 成功后**自动记进台账** —— 用户下次不必再扫这一台。
     */
    suspend fun deviceByQrText(raw: String): Result<QzxyDeviceInfo> {
        val mac = QzxyDeviceCode.parseDeviceMac(raw)
            ?: return Result.failure(QzxyException("这不是热水器的二维码（读不到设备号）"))
        return deviceByMac(mac).onSuccess { deviceStore.remember(it) }
    }

    /**
     * 蓝牙搜索附近的热水器，认出来的记进台账。
     *
     * 这是「设备列表」的正规来源（服务端没有列设备的接口，见 [QzxyBleScanner] 的说明）：
     * 扫描拿到一批 MAC → 逐个换设备详情 → 自动进台账。
     *
     * **单个 MAC 失败不中断整轮**：附近可能有别的品牌的 BLE 设备（[QzxyBleScanner.matches]
     * 对空广播名是放行的），拿不到详情直接跳过即可。
     *
     * @return 本次认出并记下的设备数
     */
    suspend fun discoverNearbyDevices(context: android.content.Context): Int {
        val found = QzxyBleScanner.scan(context)
        if (found.isEmpty()) return 0
        var added = 0
        for (item in found) {
            val device = runCatching { QzxyApi.deviceByMac(item.macAddress, requireAuth()) }
                .getOrNull()
                ?.takeIf { it.success }
                ?.data
                ?: continue
            deviceStore.remember(device)
            added++
        }
        return added
    }

    /**
     * 从台账里挑一台用（不请求网络）。
     *
     * 台账里存的 snCode / 名字就是扫码时服务端给的，够用来开阀；
     * 所以「换一台设备」不必再连网查一次。
     */
    fun selectKnownDevice(macAddress: String): QzxyDeviceInfo? {
        val known = deviceStore.current().firstOrNull {
            it.macAddress == macAddress.uppercase()
        } ?: return null
        deviceStore.touch(known.snCode)
        return QzxyDeviceInfo(
            deviceName = known.deviceName,
            snCode = known.snCode,
            macAddress = known.macAddress,
            // 台账不存预扣金额/在线状态（它们会变），交给开阀时以服务端为准
            onlineStatusId = 1,
        )
    }

    fun forgetDevice(macAddress: String) = deviceStore.forget(macAddress)

    fun clearKnownDevices() = deviceStore.clear()

    /** 记住「上次使用的设备」，下次进页面直接选中。 */
    fun rememberDevice(device: QzxyDeviceInfo) =
        valveStore.rememberDevice(device.snCode, device.deviceName)

    fun lastDeviceSnCode(): String = valveStore.lastDeviceSnCode()
    fun lastDeviceName(): String = valveStore.lastDeviceName()

    /**
     * 查某台设备上有没有进行中的订单（三态，**不能压成布尔**）。
     *
     * - `errorCode == 307` 或 `data.orderNo != null` → 在用
     * - `success == true` 且无订单 → 确实停了
     * - 请求本身失败 → **不确定**
     */
    suspend fun usingState(snCode: String): QzxyUsingState {
        val auth = store.current()?.auth ?: return QzxyUsingState.Unknown("未登录")
        return runCatching {
            val resp = QzxyApi.queryUsing(snCode, auth)
            val order = resp.data
            when {
                resp.errorCode == QZXY_ERR_DEVICE_USING ->
                    QzxyUsingState.InUse(order, ownedByMe = order?.isOwner ?: false)
                !order?.orderNo.isNullOrBlank() ->
                    QzxyUsingState.InUse(order!!, ownedByMe = order.isOwner)
                resp.success -> QzxyUsingState.Idle
                else -> QzxyUsingState.Unknown(resp.displayMessage ?: "状态未知")
            }
        }.getOrElse { QzxyUsingState.Unknown(it.message ?: "网络异常") }
    }

    // ─────────────────────────────────────────────
    //  开阀 / 关阀
    // ─────────────────────────────────────────────

    /**
     * 开始用水（开阀）。
     *
     * 流程与判据**逐条照抄**参考实现实测过的版本：
     *
     * 1. 先 `queryUsing`：设备上已有订单就直接**恢复**，不再发 `downRate`
     *    —— 否则会开出第二单，重复扣预扣款
     * 2. `isOwner == false` → **别人的单**，立刻返回「被他人使用」，绝不恢复
     * 3. 下发 `downRate`，然后轮询 `downRateResult`，最多 9 次 × 700ms，
     *    总预算 [budgetMs]（默认 6 秒，给界面用；超时返回 [QzxyOpenOutcome.Unknown]）
     * 4. 成功的判据是 `success && (state == 0 || result == 0 || orderNo != null)`
     *    —— 三个条件取或，且 **`orderNo` 在这一步就能拿到**，不要事后去问
     *
     * ⚠️ 超时返回的是 [QzxyOpenOutcome.Unknown] 而**不是失败**：
     * 很可能水已经开了。界面必须提示"去看一眼"，绝不能让用户直接再点一次。
     */
    suspend fun openValve(
        snCode: String,
        deviceName: String = "",
        budgetMs: Long = 6_000L,
    ): QzxyOpenOutcome {
        if (snCode.isBlank()) return QzxyOpenOutcome.Failed("设备信息不完整")
        val auth = store.current()?.auth ?: return QzxyOpenOutcome.Failed("请先登录")
        val deadline = System.currentTimeMillis() + budgetMs

        // 1. 已有订单 → 恢复，不再开新单
        runCatching { QzxyApi.queryUsing(snCode, auth) }.getOrNull()?.let { existing ->
            val hasOrder = existing.errorCode == QZXY_ERR_DEVICE_USING ||
                !existing.data?.orderNo.isNullOrBlank()
            if (hasOrder) {
                if (existing.data?.isOwner == false) {
                    return QzxyOpenOutcome.InUseByOthers("该设备正在被他人使用")
                }
                val orderNo = existing.data?.orderNo.orEmpty()
                valveStore.start(snCode, orderNo, deviceName, autoDisconSeconds = 0)
                valveStore.rememberDevice(snCode, deviceName)
                return QzxyOpenOutcome.Opened(orderNo, 0, resumed = true)
            }
        }

        // 2. 下发开阀
        val resp = runCatching { QzxyApi.downRate(snCode, auth) }
            .getOrElse { return QzxyOpenOutcome.Failed(it.message ?: "开始失败") }
        if (!resp.success) return QzxyOpenOutcome.Failed(resp.displayMessage ?: "开始失败")

        // 3. 轮询确认
        var confirmedOrderNo = ""
        var autoDiscon = resp.data?.autoDisConTime?.takeIf { it > 0 } ?: 0
        var opened = false
        for (i in 0..8) {
            if (i > 0 && System.currentTimeMillis() >= deadline) break
            delay(700)
            val r = runCatching { QzxyApi.downRateResult(snCode, auth) }.getOrNull() ?: continue
            val d = r.data
            if (r.success && d != null && d.isSuccess) {
                opened = true
                confirmedOrderNo = d.orderNo.orEmpty()
                autoDiscon = d.autoDisConTime?.takeIf { it > 0 } ?: autoDiscon
                break
            }
        }
        if (!opened) {
            // 预算耗尽 / 一直没确认：**状态未知**，不是失败
            return QzxyOpenOutcome.Unknown("没能确认是否已开始，请看一下热水器，别急着再点")
        }

        // 4. 落盘
        valveStore.start(snCode, confirmedOrderNo, deviceName, autoDiscon)
        valveStore.rememberDevice(snCode, deviceName)
        return QzxyOpenOutcome.Opened(confirmedOrderNo, autoDiscon, resumed = false)
    }

    /**
     * 结束用水（关阀）。
     *
     * 判据同样照抄实测版本：
     * - `!success` 但 `errorCode == 307` 或 message 含「已在」→ **也算关成功**
     *   （服务端已受理/正在关闭中；这句判断依赖服务端中文字符串，是参考实现保留的原样）
     * - 请求**抛异常**（断网/超时）→ 再查一次服务端，查不到就返回 [QzxyCloseOutcome.Unconfirmed]
     *   —— 「没发出去」和「关了」是两回事，不能都报成功
     * - 关阀被受理之后**不再轮询等待**：`closeOrder/result/query` 实测恒返回 `data: null`，
     *   等它是白等，只会让界面卡最多 5 秒
     *
     * 无论确认结果如何都会清掉本地运行态（不把用户卡在"使用中"界面上）。
     */
    suspend fun closeValve(snCode: String, orderNo: String = ""): QzxyCloseOutcome {
        if (snCode.isBlank()) return QzxyCloseOutcome.Failed("设备信息不完整")
        val auth = store.current()?.auth ?: return QzxyCloseOutcome.Failed("请先登录")
        val startTime = valveStore.current()?.startedAtMs ?: 0L

        // orderNo 缺失时先补一次
        var order = orderNo.ifBlank { valveStore.current()?.orderNo.orEmpty() }
        if (order.isBlank()) {
            order = runCatching { QzxyApi.queryUsing(snCode, auth) }
                .getOrNull()?.data?.orderNo.orEmpty()
        }

        val close = runCatching { QzxyApi.closeOrder(snCode, order, auth) }
            .getOrElse { e ->
                // 请求本身没发出去 —— 没有证据说它关了
                val stillRunning = orderRunning(snCode)
                valveStore.clear()
                return QzxyCloseOutcome.Unconfirmed(
                    startTime,
                    if (stillRunning == true) "关阀请求没发出去，设备可能还在用水"
                    else "关阀请求没发出去：${e.message}",
                )
            }

        if (!close.success) {
            val accepted = close.errorCode == QZXY_ERR_DEVICE_USING ||
                close.displayMessage?.contains("已在") == true
            if (!accepted) {
                // 明确失败：**不动本地状态**，让用户能重试
                return QzxyCloseOutcome.Failed(close.displayMessage ?: "关闭失败，请重试")
            }
        }

        // 结算金额（拿不到就是 null，界面显示「结算中」，不是 0）
        val consume = runCatching { settleAmount(snCode, order) }.getOrNull()
        valveStore.clear()
        return QzxyCloseOutcome.Closed(startTime, consume)
    }

    /**
     * 以服务端为准对齐本地状态。
     *
     * 用在「开阀结果未知」之后：与其猜，不如按一次服务端真实状态。
     *
     * @return 对齐后是否处于使用中
     */
    suspend fun reconcile(snCode: String): Boolean {
        if (snCode.isBlank()) return false
        val state = usingState(snCode)
        val inUse = state is QzxyUsingState.InUse && state.ownedByMe
        return if (inUse) {
            val orderNo = (state as QzxyUsingState.InUse).order?.orderNo.orEmpty()
            val existing = valveStore.current()
            if (existing?.snCode != snCode || existing.orderNo != orderNo) {
                valveStore.start(snCode, orderNo, existing?.deviceName.orEmpty(), 0)
            }
            true
        } else {
            valveStore.clear()
            false
        }
    }

    // ─────────────────────────────────────────────
    //  使用码（给热水器物理键盘用的 8 位码）
    // ─────────────────────────────────────────────

    /**
     * 当前使用码。
     *
     * ⚠️ 服务端对**从没领过码**的账号返回 `useCode = null`（实测行为，见 [QzxyUseCode]）。
     * 这不是错误 —— 调用方要把它显示成「还没有使用码」，而不是一直转圈。
     */
    suspend fun useCode(): Result<QzxyUseCode> = runCatching {
        val auth = requireAuth()
        val resp = QzxyApi.currentUseCode(auth)
        if (!resp.success) throw QzxyException(resp.displayMessage ?: "使用码查询失败")
        resp.data ?: throw QzxyException("服务端未返回使用码")
    }

    /**
     * 「换一个」使用码 —— **只换候选码，不改当前生效的码**。
     *
     * 拿到候选码后由界面预览，用户点「确定领取」才调 [claimUseCode]。
     * 用户取消 = 什么都不用做（服务端 3 分钟后自动丢掉候选码）。
     */
    suspend fun generateUseCode(): Result<QzxyUseCodeCandidate> = runCatching {
        val auth = requireAuth()
        val resp = QzxyApi.generateUseCode(auth)
        val data = resp.data
        if (!resp.success || data?.useCode.isNullOrBlank()) {
            throw QzxyException(resp.displayMessage ?: "换码失败")
        }
        data
    }

    /** 「确定领取」—— **唯一**会把候选码设为当前生效码的一步。 */
    suspend fun claimUseCode(useCode: String): Result<Unit> = runCatching {
        val auth = requireAuth()
        val resp = QzxyApi.setUseCode(useCode, auth)
        if (!resp.success) throw QzxyException(resp.displayMessage ?: "领取失败")
    }

    /** 开 / 关使用码。 */
    suspend fun setUseCodeEnabled(enabled: Boolean): Result<Unit> = runCatching {
        val auth = requireAuth()
        val resp = QzxyApi.updateUseCodeStatus(if (enabled) 1 else 0, auth)
        if (!resp.success) throw QzxyException(resp.displayMessage ?: "操作失败")
    }

    // ─────────────────────────────────────────────
    //  内部
    // ─────────────────────────────────────────────

    /**
     * 服务端认为设备还在用水吗。
     *
     * @return true / false / **null = 查不到**（与 [usingState] 的三态同源，
     *   这里只需要区分"确定没在用"与"不知道"）
     */
    private suspend fun orderRunning(snCode: String): Boolean? =
        when (usingState(snCode)) {
            is QzxyUsingState.InUse -> true
            QzxyUsingState.Idle -> false
            is QzxyUsingState.Unknown -> null
        }

    /**
     * 查这一单结算了多少钱（元）。
     *
     * ⚠️ `consumeMoney` 单位是**厘**，换算在 [QzxyCloseResult.consumeYuan] 里做。
     * 拿不到返回 null —— 界面显示「结算中」，**不能显示 0**（那会让人以为没花钱）。
     */
    private suspend fun settleAmount(snCode: String, orderNo: String): Double? {
        if (orderNo.isBlank()) return null
        val auth = store.current()?.auth ?: return null
        val resp = QzxyApi.consumeOrderResult(snCode, orderNo, auth)
        return resp.data?.consumeYuan
    }

    private fun requireAuth(): QzxyAuth =
        store.current()?.auth ?: throw QzxyException("请先登录趣智校园账号")
}

/** 设备占用状态的三态结果。 */
sealed interface QzxyUsingState {
    /** 正在使用。`ownedByMe == false` 时是**别人**的单，绝不能拿来当自己的恢复。 */
    data class InUse(val order: QzxyOrderStatus?, val ownedByMe: Boolean) : QzxyUsingState

    /** 确认空闲。 */
    data object Idle : QzxyUsingState

    /** 查不出来（未登录 / 网络异常 / 服务端报错）—— **不能当成空闲**。 */
    data class Unknown(val reason: String) : QzxyUsingState
}
