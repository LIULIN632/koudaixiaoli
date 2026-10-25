package com.pocket.xiaoli.ui.shower

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.pocket.xiaoli.data.qzxy.QzxyBleScanner
import com.pocket.xiaoli.data.qzxy.QzxyCampusUserInfo
import com.pocket.xiaoli.data.qzxy.QzxyCloseOutcome
import com.pocket.xiaoli.data.qzxy.QzxyDeviceInfo
import com.pocket.xiaoli.data.qzxy.QzxyKnownDevice
import com.pocket.xiaoli.data.qzxy.QzxyOpenOutcome
import com.pocket.xiaoli.data.qzxy.QzxyErrors
import com.pocket.xiaoli.data.qzxy.QzxyRepository
import com.pocket.xiaoli.data.qzxy.QzxyRunningState
import com.pocket.xiaoli.data.qzxy.QzxySession
import com.pocket.xiaoli.data.qzxy.QzxyUseCode
import com.pocket.xiaoli.data.qzxy.QzxyUseCodeCandidate
import com.pocket.xiaoli.data.qzxy.QzxyUsingState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * 洗澡页面的状态。
 *
 * ## 一条贯穿全类的不变量
 *
 * **「水在流」这件事以 [ShowerUiState.running] 为准，而不是以"点了按钮"为准。**
 * 开阀的结果可能是"未知"（网络超时），那时水可能已经开了 ——
 * 所以 [ShowerUiState.pendingConfirm] 会被置起来，界面必须提示用户去看一眼，
 * 而且**在确认之前不许再点开始**（那就是重复开阀、重复扣费）。
 */
data class ShowerUiState(
    val session: QzxySession? = null,
    // ── 登录流程 ──
    val sendingCode: Boolean = false,
    val loggingIn: Boolean = false,
    val codeSent: Boolean = false,
    // ── 账号 ──
    val loadingAccount: Boolean = false,
    val campusBalance: QzxyCampusUserInfo? = null,
    /**
     * 余额接口回的是"登录已失效"（趣智不支持多设备同时在线，在别处登录会挤掉本机）。
     *
     * 与"未签约"必须分开：两者都拿不到 `amount`，但一个要重新登录、一个要去签约。
     */
    val authExpired: Boolean = false,
    /** 开阀被"请先预约"挡住 —— 预约只能在官方 App 完成（服务端接口未公开）。 */
    val needsBooking: Boolean = false,
    val schoolName: String? = null,
    // ── 设备 ──
    val device: QzxyDeviceInfo? = null,
    val loadingDevice: Boolean = false,
    /** 该设备当前是否有进行中的订单 */
    val usingState: QzxyUsingState? = null,
    /** 用过的热水器台账（扫码记下来的），按楼层挑设备用 */
    val knownDevices: List<QzxyKnownDevice> = emptyList(),
    /** 是否展开设备挑选列表 */
    val showDevicePicker: Boolean = false,
    /** 正在蓝牙搜索附近设备 */
    val discovering: Boolean = false,
    // ── 用水 ──
    val running: QzxyRunningState? = null,
    val starting: Boolean = false,
    val stopping: Boolean = false,
    /**
     * 开阀结果未知，等用户确认。
     *
     * 非空时**界面必须禁止再次开阀** —— 否则用户多点一次就是第二单。
     */
    val pendingConfirm: String? = null,
    /** 上一次结束的消费金额（元）。null = 结算中，界面不要显示 0 */
    val lastConsume: Double? = null,
    // ── 使用码（给热水器物理键盘用的 8 位码）──
    /** 服务端当前生效的使用码。`hasCode == false` 表示这个账号还没领过码 */
    val useCode: QzxyUseCode? = null,
    val loadingUseCode: Boolean = false,
    /**
     * 「换一个」换出来的**候选码**（还没生效）。
     *
     * 非空时界面进入"预览"状态：显示候选码 + 倒计时，
     * 用户点「确定领取」才生效；点取消什么都不用做（服务端 3 分钟后自动丢弃候选码）。
     */
    val useCodeCandidate: QzxyUseCodeCandidate? = null,
    /** 候选码的领取时限（毫秒时间戳）。界面据此显示倒计时 */
    val candidateDeadlineMs: Long = 0L,
    val useCodeBusy: Boolean = false,
    val message: String? = null,
) {
    val isLoggedIn: Boolean get() = session != null

    /** 能不能点「开始用水」：已登录、有设备、设备在线、没在用水、没在忙、也没有待确认 */
    val canStart: Boolean
        get() = isLoggedIn && device != null && device.isOnline &&
            running == null && !starting && !stopping && pendingConfirm == null &&
            (usingState as? QzxyUsingState.InUse)?.ownedByMe != false

    val canStop: Boolean get() = running != null && !stopping && !starting
}

class ShowerViewModel(private val repo: QzxyRepository) : ViewModel() {

    private val _state = MutableStateFlow(
        ShowerUiState(
            session = repo.currentSession(),
            running = repo.running.value,
            knownDevices = repo.knownDevices.value,
            // 台账里挑一台作为初始选择（上次用的那台排最前），这样一进来就有设备能用
            device = repo.knownDevices.value.firstOrNull()?.let { repo.selectKnownDevice(it.macAddress) },
        )
    )
    val state: StateFlow<ShowerUiState> = _state.asStateFlow()

    init {
        if (repo.isLoggedIn) {
            refreshAccount()
            // 有运行态就先对齐一次服务端 —— 本地状态可能已经过期
            //（设备超时自停、或在别处被关掉）
            repo.running.value?.let { run ->
                viewModelScope.launch {
                    repo.reconcile(run.snCode)
                    syncRunning()
                }
            }
        }
    }

    fun consumeMessage() {
        _state.value = _state.value.copy(message = null)
    }

    // ─────────────────────────────────────────────
    //  登录
    // ─────────────────────────────────────────────

    fun sendCode(telephone: String) {
        val tel = telephone.trim()
        if (tel.length != 11) {
            _state.value = _state.value.copy(message = "请输入 11 位手机号")
            return
        }
        _state.value = _state.value.copy(sendingCode = true, message = null)
        viewModelScope.launch {
            repo.sendSmsCode(tel)
                .onSuccess {
                    _state.value = _state.value.copy(
                        sendingCode = false, codeSent = true, message = "验证码已发送",
                    )
                }
                .onFailure {
                    _state.value = _state.value.copy(
                        sendingCode = false, message = it.message ?: "验证码发送失败",
                    )
                }
        }
    }

    fun login(telephone: String, code: String) {
        val tel = telephone.trim()
        if (tel.length != 11) {
            _state.value = _state.value.copy(message = "请输入 11 位手机号")
            return
        }
        if (code.isBlank()) {
            _state.value = _state.value.copy(message = "请输入验证码")
            return
        }
        _state.value = _state.value.copy(loggingIn = true, message = null)
        viewModelScope.launch {
            repo.loginWithSms(tel, code)
                .onSuccess { session ->
                    _state.value = _state.value.copy(
                        session = session, loggingIn = false, codeSent = false,
                    )
                    refreshAccount()
                    // 登录后自动把上次用的设备带出来，省一次扫码
                    repo.lastDeviceSnCode().takeIf { it.isNotBlank() }?.let { loadLastDevice(it) }
                }
                .onFailure {
                    _state.value = _state.value.copy(
                        loggingIn = false, message = it.message ?: "登录失败",
                    )
                }
        }
    }

    fun logout() {
        repo.logout()
            .onSuccess {
                _state.value = ShowerUiState(message = "已退出趣智校园账号")
            }
            .onFailure {
                // 正在用水时不许退 —— 退掉就再也关不掉那一单了
                _state.value = _state.value.copy(message = it.message ?: "退出失败")
            }
    }

    // ─────────────────────────────────────────────
    //  账号
    // ─────────────────────────────────────────────

    fun refreshAccount() {
        if (!repo.isLoggedIn) return
        _state.value = _state.value.copy(loadingAccount = true)
        viewModelScope.launch {
            val balanceResult = repo.campusBalance()
            val balance = balanceResult.getOrNull()
            // 失败原因里带"登录失效"时如实标出来 —— 别让用户以为是自己没签约。
            val expired = balanceResult.exceptionOrNull()
                ?.message
                ?.let(QzxyErrors::isAuthExpired) == true
            val school = repo.schoolName()
            repo.refreshProfile()
            _state.value = _state.value.copy(
                session = repo.currentSession() ?: _state.value.session,
                // 普通网络失败保留旧数字（比空着强）；登录已失效则必须清掉 ——
                // 旧余额是上一次登录的钱，继续显示会让用户放心地走进浴室。
                campusBalance = if (expired) null else (balance ?: _state.value.campusBalance),
                authExpired = expired,
                schoolName = school ?: _state.value.schoolName,
                loadingAccount = false,
            )
        }
    }

    // ─────────────────────────────────────────────
    //  设备
    // ─────────────────────────────────────────────

    /** 扫码结果 → 查设备并选中。 */
    fun onQrScanned(raw: String) {
        _state.value = _state.value.copy(loadingDevice = true, message = null)
        viewModelScope.launch {
            repo.deviceByQrText(raw)
                .onSuccess { device ->
                    repo.rememberDevice(device)
                    _state.value = _state.value.copy(
                        device = device,
                        loadingDevice = false,
                        knownDevices = repo.knownDevices.value,
                        showDevicePicker = false,
                    )
                    refreshUsingState(device.snCode)
                }
                .onFailure {
                    _state.value = _state.value.copy(
                        loadingDevice = false, message = it.message ?: "读取设备失败",
                    )
                }
        }
    }

    // ─────────────────────────────────────────────
    //  设备台账（挑设备用，不请求网络）
    // ─────────────────────────────────────────────

    /**
     * 蓝牙搜索附近的热水器（需要 [android.content.Context]）。
     *
     * 这是「设备列表」的正规来源 —— 服务端没有列设备的接口，列表是**扫出来的**。
     * 权限/蓝牙没开时给出**能照着做的**提示，而不是静默失败或者一句"搜索失败"。
     */
    fun discoverNearby(context: android.content.Context) {
        if (_state.value.discovering) return
        _state.value = _state.value.copy(discovering = true, message = null, showDevicePicker = true)
        viewModelScope.launch {
            // 先在界面线程把"不能扫"的原因问清楚，好给用户可操作的提示
            val reason = QzxyBleScanner.unavailableReason(context)
            val needPermission = !QzxyBleScanner.hasPermissions(context)
            if (reason != null || needPermission) {
                _state.value = _state.value.copy(
                    discovering = false,
                    message = reason ?: "需要蓝牙权限才能搜索附近的设备",
                )
                return@launch
            }
            val added = repo.discoverNearbyDevices(context)
            _state.value = _state.value.copy(
                discovering = false,
                knownDevices = repo.knownDevices.value,
                message = if (added > 0) "找到 $added 台设备" else "附近没搜到设备（也可以扫码添加）",
            )
        }
    }

    /** 用户拒绝了蓝牙权限。给出**能照着做**的提示，而不是静默什么也不发生。 */
    fun onBlePermissionDenied() {
        _state.value = _state.value.copy(
            discovering = false,
            message = "需要蓝牙权限才能自动搜索附近设备 —— 也可以直接扫机身二维码添加",
        )
    }

    /** 展开 / 收起设备挑选列表。 */
    fun toggleDevicePicker() {
        val open = !_state.value.showDevicePicker
        _state.value = _state.value.copy(
            showDevicePicker = open,
            knownDevices = repo.knownDevices.value,
        )
    }

    /**
     * 从台账里挑一台用。
     *
     * ⚠️ **正在用水时不允许换设备** —— 换了之后「结束用水」会拿新设备的 snCode
     * 去关旧设备上的那单，关不掉，钱还在扣。所以这项禁止放在 ViewModel 里，
     * 而不是只靠界面把按钮禁掉（界面总会有人改漏）。
     */
    fun pickDevice(macAddress: String) {
        if (_state.value.running != null) {
            _state.value = _state.value.copy(message = "正在用水，请先结束后再换设备")
            return
        }
        val picked = repo.selectKnownDevice(macAddress)
        if (picked == null) {
            _state.value = _state.value.copy(message = "这台设备不在列表里了")
            return
        }
        repo.rememberDevice(picked)
        _state.value = _state.value.copy(
            device = picked,
            knownDevices = repo.knownDevices.value,
            showDevicePicker = false,
            usingState = null, // 换了设备，旧设备的占用状态作废
            message = null,
        )
        refreshUsingState(picked.snCode)
    }

    fun forgetDevice(macAddress: String) {
        repo.forgetDevice(macAddress)
        _state.value = _state.value.copy(
            knownDevices = repo.knownDevices.value,
            // 删掉的正好是当前选中那台 → 清空选择，避免界面显示一台已不存在的设备
            device = _state.value.device?.takeIf {
                it.macAddress.uppercase() != macAddress.uppercase()
            },
        )
    }

    /** 用上次的设备（登录后用，省一次扫码）。 */
    private fun loadLastDevice(snCode: String) {
        _state.value = _state.value.copy(loadingDevice = true)
        viewModelScope.launch {
            // 上次存的是 snCode，而 deviceByMac 要 MAC —— 直接按 snCode 查不到设备详情。
            // 这里走 queryUsing：能拿到 orderNo / isOwner，够用来恢复状态；
            // 设备名从本地缓存里取。
            val st = repo.usingState(snCode)
            _state.value = _state.value.copy(
                loadingDevice = false,
                usingState = st,
                device = _state.value.device ?: QzxyDeviceInfo(
                    deviceName = repo.lastDeviceName(),
                    snCode = snCode,
                    onlineStatusId = 1,
                ),
            )
        }
    }

    fun refreshUsingState(snCode: String) {
        viewModelScope.launch {
            val st = repo.usingState(snCode)
            _state.value = _state.value.copy(usingState = st)
        }
    }

    // ─────────────────────────────────────────────
    //  用水
    // ─────────────────────────────────────────────

    /** 开始用水（开阀）。 */
    fun startShower() {
        val device = _state.value.device ?: return
        if (!_state.value.canStart) return
        _state.value = _state.value.copy(starting = true, message = null, lastConsume = null)
        viewModelScope.launch {
            when (val outcome = repo.openValve(device.snCode, device.deviceName)) {
                is QzxyOpenOutcome.Opened -> {
                    syncRunning()
                    _state.value = _state.value.copy(
                        starting = false,
                        message = if (outcome.resumed) "已恢复正在进行的用水" else "已开始用水",
                        needsBooking = false,
                    )
                    refreshUsingState(device.snCode)
                }
                is QzxyOpenOutcome.InUseByOthers -> {
                    _state.value = _state.value.copy(starting = false, message = outcome.message)
                    refreshUsingState(device.snCode)
                }
                is QzxyOpenOutcome.Failed -> {
                    val expired = QzxyErrors.isAuthExpired(outcome.message)
                    _state.value = _state.value.copy(
                        starting = false,
                        message = outcome.message,
                        // "请先预约"必须单独标出来：重试一百次也开不了，得去官方 App。
                        needsBooking = QzxyErrors.needsBooking(outcome.message),
                        // 挤号可能先在任何一个接口上暴露，不只余额（muguang 的做法）——
                        // 开阀失败文案带"登录失效"也标挤号，让 SessionExpiredCard 出来。
                        authExpired = expired,
                        campusBalance = if (expired) null else _state.value.campusBalance,
                    )
                }
                is QzxyOpenOutcome.Unknown -> {
                    // ⚠️ 未知 ≠ 失败。置起待确认，界面在用户确认前禁止再次开阀
                    syncRunning()
                    _state.value = _state.value.copy(
                        starting = false,
                        pendingConfirm = outcome.message,
                        message = outcome.message,
                    )
                }
            }
        }
    }

    /** 结束用水（关阀）。 */
    fun stopShower() {
        val running = _state.value.running ?: return
        if (!_state.value.canStop) return
        _state.value = _state.value.copy(stopping = true, message = null)
        viewModelScope.launch {
            when (val outcome = repo.closeValve(running.snCode, running.orderNo)) {
                is QzxyCloseOutcome.Closed -> {
                    syncRunning()
                    _state.value = _state.value.copy(
                        stopping = false,
                        lastConsume = outcome.consumeYuan,
                        message = outcome.consumeYuan?.let { "已结束，本次消费 ¥%.2f".format(it) }
                            ?: "已结束（金额结算中）",
                    )
                }
                is QzxyCloseOutcome.Unconfirmed -> {
                    // 关阀请求没发出去 —— 本地态不清，让用户能重试
                    _state.value = _state.value.copy(stopping = false, message = outcome.message)
                }
                is QzxyCloseOutcome.Failed -> {
                    _state.value = _state.value.copy(stopping = false, message = outcome.message)
                }
            }
        }
    }

    /** 用户确认过设备实际状态后，清掉「待确认」。 */
    fun resolvePendingConfirm() {
        _state.value = _state.value.copy(pendingConfirm = null)
    }

    /** 待确认时按服务端对齐一次。 */
    fun reconcileNow() {
        val sn = _state.value.running?.snCode ?: _state.value.device?.snCode ?: return
        viewModelScope.launch {
            repo.reconcile(sn)
            syncRunning()
            _state.value = _state.value.copy(pendingConfirm = null, message = "已按服务端状态对齐")
        }
    }

    private fun syncRunning() {
        _state.value = _state.value.copy(running = repo.running.value)
    }

    // ─────────────────────────────────────────────
    //  使用码
    // ─────────────────────────────────────────────

    /** 拉当前使用码。没领过码的账号服务端返回 `useCode = null` —— 那是正常状态。 */
    fun loadUseCode() {
        if (!repo.isLoggedIn) return
        _state.value = _state.value.copy(loadingUseCode = true)
        viewModelScope.launch {
            repo.useCode()
                .onSuccess { _state.value = _state.value.copy(useCode = it, loadingUseCode = false) }
                .onFailure {
                    _state.value = _state.value.copy(
                        loadingUseCode = false,
                        message = it.message ?: "使用码查询失败",
                    )
                }
        }
    }

    /**
     * 「换一个」—— 只换候选码，**不生效**。
     *
     * 界面进入预览态并起 3 分钟倒计时；用户不确认就什么都不发生。
     */
    fun generateUseCode() {
        _state.value = _state.value.copy(useCodeBusy = true, message = null)
        viewModelScope.launch {
            repo.generateUseCode()
                .onSuccess { candidate ->
                    _state.value = _state.value.copy(
                        useCodeBusy = false,
                        useCodeCandidate = candidate,
                        candidateDeadlineMs = System.currentTimeMillis() + CANDIDATE_TTL_MS,
                    )
                }
                .onFailure {
                    _state.value = _state.value.copy(
                        useCodeBusy = false, message = it.message ?: "换码失败",
                    )
                }
        }
    }

    /** 「确定领取」—— 唯一会改服务端生效码的一步。成功后重新拉一次以拿到新的生效时间。 */
    fun claimUseCode() {
        val candidate = _state.value.useCodeCandidate?.useCode ?: return
        _state.value = _state.value.copy(useCodeBusy = true, message = null)
        viewModelScope.launch {
            repo.claimUseCode(candidate)
                .onSuccess {
                    _state.value = _state.value.copy(
                        useCodeBusy = false,
                        useCodeCandidate = null,
                        candidateDeadlineMs = 0L,
                        message = "已领取新使用码",
                    )
                    loadUseCode()
                }
                .onFailure {
                    _state.value = _state.value.copy(
                        useCodeBusy = false, message = it.message ?: "领取失败",
                    )
                }
        }
    }

    /** 放弃候选码。**服务端什么都没改**，所以本地清掉即可。 */
    fun discardUseCodeCandidate() {
        _state.value = _state.value.copy(useCodeCandidate = null, candidateDeadlineMs = 0L)
    }

    fun setUseCodeEnabled(enabled: Boolean) {
        _state.value = _state.value.copy(useCodeBusy = true, message = null)
        viewModelScope.launch {
            repo.setUseCodeEnabled(enabled)
                .onSuccess {
                    _state.value = _state.value.copy(
                        useCodeBusy = false,
                        message = if (enabled) "使用码已开启" else "使用码已关闭",
                    )
                    loadUseCode()
                }
                .onFailure {
                    _state.value = _state.value.copy(
                        useCodeBusy = false, message = it.message ?: "操作失败",
                    )
                }
        }
    }

    class Factory(private val repo: QzxyRepository) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T = ShowerViewModel(repo) as T
    }

    companion object {
        /** 候选码的领取时限：3 分钟（官方 App 行为，超时不 `set` 就作废）。 */
        const val CANDIDATE_TTL_MS = 3 * 60 * 1000L
    }
}
