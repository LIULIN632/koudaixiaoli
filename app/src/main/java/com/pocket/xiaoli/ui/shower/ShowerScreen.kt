package com.pocket.xiaoli.ui.shower

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.HotTub
import androidx.compose.material.icons.outlined.QrCodeScanner
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.pocket.xiaoli.PocketXiaoliApplication
import com.pocket.xiaoli.data.qzxy.QzxyBleScanner
import com.pocket.xiaoli.data.qzxy.QzxyKnownDevice
import com.pocket.xiaoli.data.qzxy.QzxyUsingState
import com.pocket.xiaoli.ui.theme.LocalPocketXiaoliColors
import com.pocket.xiaoli.ui.theme.LocalSkin
import kotlinx.coroutines.delay

/**
 * 洗澡 —— 趣智校园原生控制页。
 *
 * ## 一条最重要的界面纪律
 *
 * **开阀结果未知时，禁止再次开阀。** 网络超时并不等于失败 —— 水可能已经开了。
 * 这时界面会弹一个待确认的横幅，并在用户处理之前**把「开始」按钮禁用**，
 * 只有「去看了一下，正在用」或「按服务端状态对齐」两条出路。
 * 少了这道闸，用户多戳一下就是第二单、第二笔预扣款。
 *
 * ## 为什么开阀前要二次确认
 *
 * 它是**扣钱动作**（服务端预扣，按用量结算）。确认框里会把预扣金额和
 * 「结束时会自动结算」讲清楚 —— 这不是多余的点击，是让用户知道自己在为什么付钱。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ShowerScreen(
    onBack: () -> Unit,
) {
    val skin = LocalSkin.current
    val colors = LocalPocketXiaoliColors.current
    val context = LocalContext.current
    val app = context.applicationContext as PocketXiaoliApplication
    val vm: ShowerViewModel = viewModel(
        factory = ShowerViewModel.Factory(app.appContainer.qzxyRepository),
    )
    val state by vm.state.collectAsStateWithLifecycle()

    var showScanner by remember { mutableStateOf(false) }
    var confirmStart by remember { mutableStateOf(false) }

    /**
     * 蓝牙权限申请。
     *
     * ⚠️ 必须真的发起申请：12+ 上 BLUETOOTH_SCAN 是**运行时**权限，
     * 不在清单里声明 + 不申请 = 扫描静默返回空列表、**一句错都不报**。
     * 12 以下系统把 BLE 扫描归到定位权限，同样要申请（见 [QzxyBleScanner.requiredPermissions]）。
     */
    val blePermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { grants ->
        if (grants.values.all { it }) {
            vm.discoverNearby(context)
        } else {
            vm.onBlePermissionDenied()
        }
    }

    /**
     * 设备码的二次确认。
     *
     * 「换一个」和「关闭」都**不该一点就生效**：
     * - 换一个：每天只有 20 次额度，误触点掉就少一次
     * - 关闭：关掉之后在热水器上就输不了码了，用户在浴室里发现会很难受
     * 所以两者都走一层确认。开启不必确认（它只是把功能恢复，没有代价）。
     */
    var useCodeConfirm by remember { mutableStateOf<UseCodeAction?>(null) }

    // 计时只在「正在用水」时走，一秒一跳
    var nowMs by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(state.running != null) {
        while (state.running != null) {
            nowMs = System.currentTimeMillis()
            delay(1000)
        }
    }

    // 候选使用码的倒计时也要一秒一跳 —— 但它可能在**没用**水的时候存在
    LaunchedEffect(state.useCodeCandidate != null) {
        while (state.useCodeCandidate != null) {
            nowMs = System.currentTimeMillis()
            delay(1000)
        }
    }

    // 登录后拉一次设备码（没领过码的账号会拿到 hasCode=false，那是正常状态）
    LaunchedEffect(state.isLoggedIn) {
        if (state.isLoggedIn) vm.loadUseCode()
    }

    if (showScanner) {
        QzxyQrScanDialog(
            onScanned = { raw ->
                showScanner = false
                vm.onQrScanned(raw)
            },
            onDismiss = { showScanner = false },
        )
        return
    }

    if (confirmStart) {
        val device = state.device
        AlertDialog(
            onDismissRequest = { confirmStart = false },
            title = { Text("开始用水？") },
            text = {
                Text(
                    // 位置放第一行：开阀前最该确认的就是"是不是这一层这台"
                    "位置：${device?.fullLocation.orEmpty()}\n" +
                        "设备：${device?.deviceName.orEmpty()}\n" +
                        "预扣：${device?.withholdMoney?.let { "¥%.2f".format(it) } ?: "以服务端为准"}\n\n" +
                        "开始后会先预扣一笔，结束后按实际用量自动结算并退回差额。",
                    fontSize = 13.sp,
                    lineHeight = 19.sp,
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    confirmStart = false
                    vm.startShower()
                }) { Text("开始") }
            },
            dismissButton = {
                TextButton(onClick = { confirmStart = false }) { Text("取消") }
            },
        )
    }

    // 设备码的二次确认
    useCodeConfirm?.let { action ->
        AlertDialog(
            onDismissRequest = { useCodeConfirm = null },
            title = { Text(action.title) },
            text = { Text(action.body, fontSize = 13.sp, lineHeight = 19.sp) },
            confirmButton = {
                TextButton(onClick = {
                    useCodeConfirm = null
                    when (action) {
                        // 前两个都是「换一个候选码」，走同一个 ViewModel 方法；
                        // 分开只是为了给用户不同的说明文案（额度规则不一样）
                        UseCodeAction.FIRST_CLAIM,
                        UseCodeAction.GENERATE -> vm.generateUseCode()
                        UseCodeAction.DISABLE -> vm.setUseCodeEnabled(false)
                        UseCodeAction.ENABLE -> vm.setUseCodeEnabled(true)
                    }
                }) {
                    Text(
                        action.confirmLabel,
                        // 关闭是"减少能力"的动作，用 danger 色提示一下后果
                        color = if (action == UseCodeAction.DISABLE) colors.danger else colors.accentMain,
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = { useCodeConfirm = null }) { Text("取消") }
            },
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("洗澡", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "返回")
                    }
                },
                actions = {
                    if (state.isLoggedIn) {
                        IconButton(onClick = { vm.refreshAccount() }) {
                            Icon(Icons.Outlined.Refresh, "刷新")
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = colors.surfaceCard),
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .background(colors.surfaceAlt)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = skin.spaceLg, vertical = skin.spaceMd),
            verticalArrangement = Arrangement.spacedBy(skin.spaceMd),
        ) {
            state.message?.let { MessageBanner(it) }

            // 待确认横幅 —— 必须排在控制区之前，用户第一眼就看到
            state.pendingConfirm?.let { text ->
                PendingConfirmCard(
                    text = text,
                    onReconcile = { vm.reconcileNow() },
                    onDismiss = { vm.resolvePendingConfirm() },
                )
            }

            if (state.isLoggedIn) {
                ControlCard(
                    state = state,
                    nowMs = nowMs,
                    onScan = { showScanner = true },
                    onStart = { confirmStart = true },
                    onStop = { vm.stopShower() },
                    onTogglePicker = { vm.toggleDevicePicker() },
                    onPickDevice = { mac -> vm.pickDevice(mac) },
                    onForgetDevice = { mac -> vm.forgetDevice(mac) },
                    onDiscover = {
                        // 没权限就先申请，批下来了回调里再扫（不能直接扫 —— 会静默失败）
                        if (QzxyBleScanner.hasPermissions(context)) {
                            vm.discoverNearby(context)
                        } else {
                            blePermissionLauncher.launch(QzxyBleScanner.requiredPermissions)
                        }
                    },
                )
                UseCodeCard(
                    state = state,
                    nowMs = nowMs,
                    onCopy = { code -> copyToClipboard(context, code, "使用码") },
                    onGenerate = { useCodeConfirm = UseCodeAction.GENERATE },
                    onFirstClaim = { useCodeConfirm = UseCodeAction.FIRST_CLAIM },
                    onClaim = { vm.claimUseCode() },
                    onDiscard = { vm.discardUseCodeCandidate() },
                    onToggle = { enabled ->
                        // 开启没有代价，不打断；关闭会让热水器上输不了码，需要确认
                        if (enabled) vm.setUseCodeEnabled(true)
                        else useCodeConfirm = UseCodeAction.DISABLE
                    },
                )
                AccountCard(state, onLogout = { vm.logout() })
            } else {
                HeaderCard(state)
                LoginCard(
                    sendingCode = state.sendingCode,
                    loggingIn = state.loggingIn,
                    codeSent = state.codeSent,
                    onSendCode = { tel -> vm.sendCode(tel) },
                    onLogin = { tel, code -> vm.login(tel, code) },
                )
            }
        }
    }
}

@Composable
private fun MessageBanner(text: String) {
    val skin = LocalSkin.current
    val colors = LocalPocketXiaoliColors.current
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(skin.radiusChip),
        colors = CardDefaults.cardColors(containerColor = colors.surfaceHighlight),
    ) {
        Text(
            text,
            modifier = Modifier.fillMaxWidth().padding(skin.spaceMd),
            fontSize = 13.sp,
            color = colors.textPrimary,
        )
    }
}

/**
 * 开阀结果未知时的处理卡。
 *
 * 两个按钮分别对应两种真实情况：
 * - 「按服务端状态对齐」：直接问一次服务端，最省事
 * - 「我知道了」：用户自己去看了机器，把闸门放开
 */
@Composable
private fun PendingConfirmCard(text: String, onReconcile: () -> Unit, onDismiss: () -> Unit) {
    val skin = LocalSkin.current
    val colors = LocalPocketXiaoliColors.current
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(skin.radiusCard),
        colors = CardDefaults.cardColors(containerColor = colors.surfaceCard),
        border = androidx.compose.foundation.BorderStroke(1.dp, colors.warning.copy(alpha = 0.5f)),
    ) {
        Column(Modifier.fillMaxWidth().padding(skin.cardPadding)) {
            Text("需要先确认状态", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = colors.warning)
            Spacer(Modifier.height(skin.spaceXs))
            Text(
                "$text\n\n在确认之前，「开始用水」会被禁用 —— 避免重复开阀、重复扣费。",
                fontSize = 12.sp,
                color = colors.textSecondary,
                lineHeight = 18.sp,
            )
            Spacer(Modifier.height(skin.spaceSm))
            Row(horizontalArrangement = Arrangement.spacedBy(skin.spaceSm)) {
                Button(onClick = onReconcile) { Text("按服务端状态对齐") }
                TextButton(onClick = onDismiss) { Text("我知道了") }
            }
        }
    }
}

@Composable
private fun HeaderCard(state: ShowerUiState) {
    val skin = LocalSkin.current
    val colors = LocalPocketXiaoliColors.current
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(skin.radiusCard),
        colors = CardDefaults.cardColors(containerColor = colors.surfaceCard),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(skin.cardPadding),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier.size(46.dp).clip(CircleShape)
                    .background(colors.accentMain.copy(alpha = 0.12f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Outlined.HotTub, null, tint = colors.accentMain, modifier = Modifier.size(24.dp))
            }
            Spacer(Modifier.width(skin.spaceMd))
            Column(Modifier.weight(1f)) {
                Text("趣智校园", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = colors.textPrimary)
                Spacer(Modifier.height(skin.spaceMicro))
                Text(
                    state.schoolName?.takeIf { it.isNotBlank() }?.let { "学校：$it" } ?: "用水 / 淋浴账户",
                    fontSize = 12.sp,
                    color = colors.textSecondary,
                )
            }
        }
    }
}

@Composable
private fun ControlCard(
    state: ShowerUiState,
    nowMs: Long,
    onScan: () -> Unit,
    onStart: () -> Unit,
    onStop: () -> Unit,
    onTogglePicker: () -> Unit,
    onPickDevice: (String) -> Unit,
    onForgetDevice: (String) -> Unit,
    onDiscover: () -> Unit,
) {
    val skin = LocalSkin.current
    val colors = LocalPocketXiaoliColors.current
    val running = state.running
    val device = state.device

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(skin.radiusCard),
        colors = CardDefaults.cardColors(containerColor = colors.surfaceCard),
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(skin.cardPadding),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            // ── 设备 ──
            if (device == null) {
                Text(
                    "还没绑定热水器",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium,
                    color = colors.textPrimary,
                )
                Spacer(Modifier.height(skin.spaceXs))
                Text(
                    "扫一下机身二维码就行 —— 不用开蓝牙，也不用装趣智校园 App。",
                    fontSize = 12.sp,
                    color = colors.textTertiary,
                    textAlign = TextAlign.Center,
                    lineHeight = 17.sp,
                )
                Spacer(Modifier.height(skin.spaceMd))
                Button(onClick = onScan, enabled = !state.loadingDevice) {
                    Icon(Icons.Outlined.QrCodeScanner, null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(skin.spaceSm))
                    Text("扫码绑定热水器")
                }
                if (state.loadingDevice) {
                    Spacer(Modifier.height(skin.spaceSm))
                    CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                }
                return@Column
            }

            // ── 位置：这是用户最需要确认的一件事（"我扫的是哪一层的那台"）──
            //
            // 设备名里本来就带位置：`热水器-{校区}-{楼栋}-{楼层}-{房间}`。
            // 所以主标题放**楼层 + 房间**，副标题放楼栋，原始全名退到最下面一行小字。
            // 把原始名字当主标题的话，"3层 301" 这种关键信息会埋在长串中间看不见。
            Text(
                device.locationSummary,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                color = colors.textPrimary,
                textAlign = TextAlign.Center,
            )
            device.building?.let { building ->
                Spacer(Modifier.height(skin.spaceMicro))
                Text(
                    building,
                    fontSize = 12.sp,
                    color = colors.textSecondary,
                    textAlign = TextAlign.Center,
                )
            }
            Spacer(Modifier.height(skin.spaceXs))
            Text(
                when {
                    !device.isOnline -> "设备离线"
                    running != null -> "正在用水"
                    (state.usingState as? QzxyUsingState.InUse)?.ownedByMe == false -> "正在被他人使用"
                    state.usingState is QzxyUsingState.Unknown -> "状态未知"
                    else -> "空闲"
                },
                fontSize = 12.sp,
                color = when {
                    !device.isOnline -> colors.danger
                    running != null -> colors.accentMain
                    (state.usingState as? QzxyUsingState.InUse)?.ownedByMe == false -> colors.warning
                    else -> colors.textSecondary
                },
            )
            // 原始设备名 —— 换设备 / 找报修时要说得出"哪一台"，所以别藏起来
            if (device.deviceName.isNotBlank() && device.deviceName != device.locationSummary) {
                Spacer(Modifier.height(skin.spaceXs))
                Text(
                    device.deviceName,
                    fontSize = 10.sp,
                    color = colors.textTertiary,
                    textAlign = TextAlign.Center,
                )
            }

            Spacer(Modifier.height(skin.spaceWide))

            // ── 计时 / 倒计时 ──
            if (running != null) {
                Text(
                    formatDuration(running.elapsedSeconds(nowMs)),
                    fontSize = 34.sp,
                    fontWeight = FontWeight.Bold,
                    color = colors.accentMain,
                )
                Spacer(Modifier.height(skin.spaceMicro))
                val remain = running.autoDisconRemainSeconds(nowMs)
                Text(
                    // 服务端没给倒计时就如实说，不自己编一个
                    remain?.let { "约 ${formatDuration(it)} 后自动关停" } ?: "已用时长",
                    fontSize = 12.sp,
                    color = colors.textTertiary,
                )
                Spacer(Modifier.height(skin.spaceWide))
            }

            // ── 主按钮 ──
            if (running != null) {
                Button(
                    onClick = onStop,
                    enabled = state.canStop,
                    colors = ButtonDefaults.buttonColors(containerColor = colors.danger),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    if (state.stopping) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            strokeWidth = 2.dp,
                            color = colors.surfaceCard,
                        )
                    } else {
                        Icon(Icons.Filled.Stop, null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(skin.spaceSm))
                        Text("结束用水")
                    }
                }
            } else {
                Button(
                    onClick = onStart,
                    enabled = state.canStart,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    if (state.starting) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            strokeWidth = 2.dp,
                            color = colors.surfaceCard,
                        )
                    } else {
                        Icon(Icons.Filled.PlayArrow, null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(skin.spaceSm))
                        Text("开始用水")
                    }
                }
            }

            Spacer(Modifier.height(skin.spaceSm))
            Row(horizontalArrangement = Arrangement.spacedBy(skin.spaceSm)) {
                // 主要入口改成「选设备」—— 这才是日常动作（挑本层那台），
                // 扫码退到列表内部，因为扫一次就够了，之后都在这儿挑
                TextButton(onClick = onTogglePicker) {
                    Text(
                        if (state.showDevicePicker) "收起设备列表"
                        else "选设备（${state.knownDevices.size}）",
                        fontSize = 13.sp,
                    )
                }
                TextButton(onClick = onScan) { Text("扫码", fontSize = 13.sp) }
            }

            if (state.showDevicePicker) {
                Spacer(Modifier.height(skin.spaceSm))
                DevicePicker(
                    devices = state.knownDevices,
                    selectedMac = device.macAddress,
                    enabled = running == null,
                    discovering = state.discovering,
                    onPick = onPickDevice,
                    onForget = onForgetDevice,
                    onScan = onScan,
                    onDiscover = onDiscover,
                )
            }
        }
    }
}

/**
 * 设备挑选列表 —— 「预约」那个界面在我们这边的对应物。
 *
 * ## 它是什么、为什么是这个形态
 *
 * 官方 App 里点了「预约」出来的是**设备列表**：一栋楼各楼层的机器，点一下就用。
 * 服务端的设备列表接口**无法从匿名请求判断是否存在**（`/device/` 前缀下
 * 未登录时所有路径返回同一句「未获取到用户信息」—— 认证在路由之前拦截，
 * 404 与"存在但需权限"长得一样）。
 *
 * 所以这个列表的数据来自**我们确定的来源**：扫码时服务端给回的
 * `macAddress` / `snCode` / 名字里的楼栋楼层房间。扫过一次就记下来，
 * 之后按楼层挑，不必再扫 —— 而且离线可用。
 *
 * ## 为什么按楼层排序
 *
 * 用户要找的是「我在 3 层，用 3 层那台」。所以按 `楼栋 → 楼层数字 → 房间` 排，
 * 且楼层用**数字**比大小（否则 `10层` 会排到 `2层` 前面）。
 */
@Composable
private fun DevicePicker(
    devices: List<QzxyKnownDevice>,
    selectedMac: String?,
    /** 正在用水时禁止换设备 —— 换了就关不掉旧设备上那单 */
    enabled: Boolean,
    discovering: Boolean,
    onPick: (String) -> Unit,
    onForget: (String) -> Unit,
    onScan: () -> Unit,
    /** 蓝牙搜索附近设备 */
    onDiscover: () -> Unit,
) {
    val skin = LocalSkin.current
    val colors = LocalPocketXiaoliColors.current

    Box(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(skin.radiusChip))
            .background(colors.surfaceHighlight)
            .padding(skin.spaceMd),
    ) {
        Column {
            Text(
                if (enabled) "选一台设备" else "正在用水，结束后才能换设备",
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium,
                color = if (enabled) colors.textPrimary else colors.warning,
            )
            Spacer(Modifier.height(skin.spaceSm))

            if (devices.isEmpty()) {
                Text(
                    "还没有设备。点「搜索附近」自动找 —— 也能扫机身二维码添加。",
                    fontSize = 11.sp,
                    color = colors.textTertiary,
                    lineHeight = 16.sp,
                )
            } else {
                // 按 楼栋 → 楼层数字 → 房间 排序
                val sorted = remember(devices) { devices.sortedWith(compareBy({ it.sortKey.first }, { it.sortKey.second }, { it.room.orEmpty() })) }
                var lastBuilding: String? = null
                sorted.forEach { dev ->
                    // 楼栋分组标题
                    if (dev.building != lastBuilding) {
                        lastBuilding = dev.building
                        if (dev.building != null) {
                            Text(
                                dev.building,
                                fontSize = 11.sp,
                                color = colors.textTertiary,
                                modifier = Modifier.padding(top = skin.spaceXs, bottom = 2.dp),
                            )
                        }
                    }
                    val isSelected = dev.macAddress.equals(selectedMac, ignoreCase = true)
                    Row(
                        Modifier.fillMaxWidth().padding(vertical = 3.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(
                                dev.locationLabel,
                                fontSize = 14.sp,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                color = if (isSelected) colors.accentMain else colors.textPrimary,
                            )
                            // 完整的原始设备名，万一楼层解析不对，用户还能靠它认出是哪台
                            Text(
                                dev.deviceName.ifBlank { dev.snCode },
                                fontSize = 10.sp,
                                color = colors.textTertiary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                        if (isSelected) {
                            Text("当前", fontSize = 11.sp, color = colors.accentMain)
                        } else {
                            TextButton(
                                onClick = { onPick(dev.macAddress) },
                                enabled = enabled,
                            ) { Text("选用", fontSize = 13.sp) }
                        }
                        IconButton(onClick = { onForget(dev.macAddress) }) {
                            Icon(
                                Icons.Outlined.DeleteOutline,
                                "忘记这台",
                                tint = colors.textTertiary,
                                modifier = Modifier.size(18.dp),
                            )
                        }
                    }
                    HorizontalDivider(color = colors.surfaceCard)
                }
            }

            Spacer(Modifier.height(skin.spaceXs))
            Row(verticalAlignment = Alignment.CenterVertically) {
                // 蓝牙搜索是设备列表的正规来源（服务端没有列设备的接口，列表是扫出来的）
                Button(onClick = onDiscover, enabled = !discovering) {
                    if (discovering) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(14.dp),
                            strokeWidth = 2.dp,
                            color = colors.surfaceCard,
                        )
                        Spacer(Modifier.width(skin.spaceSm))
                        Text("搜索中…", fontSize = 13.sp)
                    } else {
                        Text("搜索附近", fontSize = 13.sp)
                    }
                }
                Spacer(Modifier.width(skin.spaceSm))
                TextButton(onClick = onScan) { Text("扫码添加", fontSize = 13.sp) }
            }
        }
    }
}

/**
 * 账户卡：**余额打头**，下面接设备码。
 *
 * ## 为什么余额放最上面
 *
 * 进洗澡页最想知道的是「我还有多少钱、够不够洗」—— 这比设备码常用得多。
 * 所以余额是这张卡的视觉主体（大字号 + 强调色），设备码退到下面一个内嵌面板里。
 *
 * ## 缺省值的处理不能含糊
 *
 * | 状态 | 显示 | 为什么不能显示成别的 |
 * |---|---|---|
 * | 拿不到余额（未签约免密支付）| `未签约` | 显示 `¥0.00` 会让用户以为钱没了 |
 * | 正在查 | `查询中…` | 显示 0 同理 |
 * | 本次消费还没结算 | `—` | 显示 `¥0.00` 会让人以为这次没花钱 |
 *
 * ## 设备码：三个接口的分工决定了这里的交互
 *
 * | 接口 | 作用 | 改不改当前生效的码 |
 * |---|---|---|
 * | 拉取 | 看当前码 | 不改 |
 * | 「换一个」| 换出**候选**码 | **不改** |
 * | 「确定领取」| 生效 | **改**（只有它会） |
 *
 * 所以做成**先预览、再决定领不领**：候选码先显示并挂 3 分钟倒计时，
 * 点「确定领取」才生效；点「取消」什么都不用做（服务端 3 分钟后自己丢弃）。
 * 用户不会因为"手滑换了一个"就把在用的码弄丢。
 *
 * ⚠️ 而「换一个」与「关闭」**仍然各有一层弹窗确认**（在调用方 `ShowerScreen` 里）：
 * 换一个要花掉当天 20 次额度里的一次，关闭则会让热水器上再也输不了码。
 * 「开启」不确认 —— 它只是恢复能力，没有代价。
 */
@Composable
private fun UseCodeCard(
    state: ShowerUiState,
    nowMs: Long,
    onCopy: (String) -> Unit,
    /** 「换一个」—— 需确认（每天 20 次额度） */
    onGenerate: () -> Unit,
    /** 「领取一个」—— 需确认（每天只能领取一次，与换码额度不同） */
    onFirstClaim: () -> Unit,
    onClaim: () -> Unit,
    onDiscard: () -> Unit,
    onToggle: (Boolean) -> Unit,
) {
    val skin = LocalSkin.current
    val colors = LocalPocketXiaoliColors.current
    val current = state.useCode
    val candidate = state.useCodeCandidate

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(skin.radiusCard),
        colors = CardDefaults.cardColors(containerColor = colors.surfaceCard),
    ) {
        Column(Modifier.fillMaxWidth().padding(skin.cardPadding)) {

            // ═══════════ 余额（视觉主体）═══════════
            Text("校园卡余额", fontSize = 12.sp, color = colors.textTertiary)
            Spacer(Modifier.height(skin.spaceXs))
            Text(
                // 三种"没有数字"的状态要分开表达，见上方表格
                state.campusBalance?.amountYuan?.let { "¥%.2f".format(it) }
                    ?: if (state.loadingAccount) "查询中…" else "未签约",
                fontSize = 34.sp,
                fontWeight = FontWeight.Bold,
                color = if (state.campusBalance?.amountYuan != null) colors.accentMain
                else colors.textSecondary,
            )
            val balanceHint = when {
                state.campusBalance == null && state.loadingAccount -> "正在读取校园卡"
                state.campusBalance?.isSigned == false -> "未签约免密支付，拿不到实时余额"
                state.campusBalance?.amountYuan == null -> "需签约校园卡免密支付"
                else -> "校园卡实时余额"
            }
            Spacer(Modifier.height(skin.spaceMicro))
            Text(balanceHint, fontSize = 11.sp, color = colors.textTertiary)

            Spacer(Modifier.height(skin.spaceSm))
            // 次要金额排一行：趣智钱包余额 + 上次消费
            Row(Modifier.fillMaxWidth()) {
                MiniStat(
                    modifier = Modifier.weight(1f),
                    label = "趣智钱包",
                    value = state.wallet?.let { "¥%.2f".format(it.moneyYuan) }
                        ?: if (state.loadingAccount) "…" else "—",
                )
                MiniStat(
                    modifier = Modifier.weight(1f),
                    label = "上次消费",
                    // ⚠️ null 是「还没结算」，不是 0
                    value = state.lastConsume?.let { "¥%.2f".format(it) } ?: "—",
                )
            }

            // ═══════════ 设备码 ═══════════
            Spacer(Modifier.height(skin.spaceMd))
            HorizontalDivider(color = colors.surfaceAlt)
            Spacer(Modifier.height(skin.spaceMd))

            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "设备码",
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    color = colors.textPrimary,
                    modifier = Modifier.weight(1f),
                )
                if (current != null) {
                    Text(
                        if (current.isEnabled) "已开启" else "已关闭",
                        fontSize = 12.sp,
                        color = if (current.isEnabled) colors.success else colors.textTertiary,
                    )
                }
            }
            Spacer(Modifier.height(skin.spaceMicro))
            Text(
                "在热水器面板上直接输这 8 位数字，不用掏手机开阀。",
                fontSize = 12.sp,
                color = colors.textTertiary,
                lineHeight = 17.sp,
            )
            Spacer(Modifier.height(skin.spaceMd))

            when {
                state.loadingUseCode && current == null -> {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                        Spacer(Modifier.width(skin.spaceSm))
                        Text("加载中…", fontSize = 13.sp, color = colors.textTertiary)
                    }
                }

                // ⚠️ 没领过码的账号服务端真的返回 null（不是空串、也不是 0）。
                // 这里必须显示成「还没有」，而不是一直转圈 —— 那会让人以为坏了。
                current != null && !current.hasCode -> {
                    Text("还没有使用码", fontSize = 14.sp, color = colors.textSecondary)
                    Spacer(Modifier.height(skin.spaceSm))
                    Button(onClick = onFirstClaim, enabled = !state.useCodeBusy) {
                        Text("领取一个")
                    }
                }

                current != null -> {
                    // ── 当前生效的码 ──
                    Text(
                        formatUseCode(current.useCode.orEmpty()),
                        fontSize = 30.sp,
                        fontWeight = FontWeight.Bold,
                        color = colors.accentMain,
                    )
                    Spacer(Modifier.height(skin.spaceMicro))
                    current.useCodeStartTime?.takeIf { it.isNotBlank() }?.let {
                        Text("生效于 $it", fontSize = 11.sp, color = colors.textTertiary)
                    }
                    Spacer(Modifier.height(skin.spaceSm))

                    Row(horizontalArrangement = Arrangement.spacedBy(skin.spaceSm)) {
                        OutlinedButton(onClick = { onCopy(current.useCode.orEmpty()) }) { Text("复制") }
                        OutlinedButton(
                            onClick = onGenerate,
                            enabled = !state.useCodeBusy && candidate == null,
                        ) { Text("换一个") }
                        TextButton(
                            onClick = { onToggle(!current.isEnabled) },
                            enabled = !state.useCodeBusy,
                        ) { Text(if (current.isEnabled) "关闭" else "开启") }
                    }

                    // 今天还能不能重新领取 —— 服务端给了原因就照实显示，别自己编
                    if (!current.canResetToday) {
                        Spacer(Modifier.height(skin.spaceXs))
                        Text(
                            current.resetAvailabilityWarMark?.takeIf { it.isNotBlank() }
                                ?: "今天已不能再领取",
                            fontSize = 11.sp,
                            color = colors.textTertiary,
                        )
                    }
                }
            }

            // ── 候选码预览（还没生效）──
            if (candidate != null) {
                Spacer(Modifier.height(skin.spaceMd))
                Box(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(skin.radiusChip))
                        .background(colors.surfaceHighlight)
                        .padding(skin.spaceMd),
                ) {
                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                "待领取（还没生效）",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Medium,
                                color = colors.accentMain,
                                modifier = Modifier.weight(1f),
                            )
                            val remainMs = (state.candidateDeadlineMs - nowMs).coerceAtLeast(0)
                            Text(
                                "${remainMs / 1000} 秒后失效",
                                fontSize = 11.sp,
                                color = if (remainMs <= 30_000) colors.danger else colors.textTertiary,
                            )
                        }
                        Spacer(Modifier.height(skin.spaceXs))
                        Text(
                            formatUseCode(candidate.useCode.orEmpty()),
                            fontSize = 26.sp,
                            fontWeight = FontWeight.Bold,
                            color = colors.textPrimary,
                        )
                        candidate.remainTimes?.let {
                            Spacer(Modifier.height(skin.spaceMicro))
                            Text("今天还能换 $it 次", fontSize = 11.sp, color = colors.textTertiary)
                        }
                        Spacer(Modifier.height(skin.spaceSm))
                        Row(horizontalArrangement = Arrangement.spacedBy(skin.spaceSm)) {
                            Button(onClick = onClaim, enabled = !state.useCodeBusy) { Text("确定领取") }
                            TextButton(onClick = onDiscard) { Text("取消") }
                        }
                        Spacer(Modifier.height(skin.spaceMicro))
                        Text(
                            "取消不会影响正在用的码 —— 只有「确定领取」才会换掉它。",
                            fontSize = 11.sp,
                            color = colors.textTertiary,
                        )
                    }
                }
            }
        }
    }
}

/** `27389960` → `2738 9960`（分组更好念给机器输） */
private fun formatUseCode(code: String): String =
    if (code.length == 8) "${code.take(4)} ${code.drop(4)}" else code

/** 余额卡里的次要金额（趣智钱包 / 上次消费）。 */
@Composable
private fun MiniStat(modifier: Modifier, label: String, value: String) {
    val colors = LocalPocketXiaoliColors.current
    Column(modifier) {
        Text(label, fontSize = 11.sp, color = colors.textTertiary)
        Spacer(Modifier.height(2.dp))
        Text(value, fontSize = 14.sp, fontWeight = FontWeight.Medium, color = colors.textPrimary)
    }
}

/**
 * 设备码上需要二次确认的动作。
 *
 * ⚠️ 这三条都**有代价**，所以不放在一键可达的位置：
 * - [GENERATE]：花掉当天 20 次换码额度里的一次
 * - [DISABLE]：关掉之后热水器面板上就输不了码了 —— 用户在浴室里发现会很难受
 * - [ENABLE]：只是恢复能力，本来没有代价；保留这个枚举项是为了让确认弹窗的
 *   `when` 是穷尽的（少一个分支编译器就会报错，而不是运行时漏掉）
 */
private enum class UseCodeAction(
    val title: String,
    val body: String,
    val confirmLabel: String,
) {
    /**
     * 从没领过码的账号点「领取一个」。
     *
     * ⚠️ 它与 [GENERATE] 不是一回事：这一步受服务端的
     * 「**1 天只能领取一次**使用码」限制（`resetAvailability`），
     * 而 [GENERATE] 花的是「每天 20 次」那个额度。文案要把区别讲清，否则用户会困惑。
     */
    FIRST_CLAIM(
        title = "领取设备码？",
        body = "领取后的新码要再点一次「确定领取」才会生效。\n\n" +
            "⚠️ 使用码**每天只能领取一次**，领完当天就改不了了。",
        confirmLabel = "领取",
    ),
    GENERATE(
        title = "换一个设备码？",
        body = "换出来的新码不会立刻生效，需要你再点「确定领取」。\n\n" +
            "每换一次会消耗当天 20 次额度里的一次。取消不受影响，正在用的码也不会变。",
        confirmLabel = "换一个",
    ),
    DISABLE(
        title = "关闭设备码？",
        body = "关闭后，在热水器面板上输入这个码将无法开阀 —— " +
            "只能靠这个 App 远程开。\n\n出门在外或手机没电时会很不方便，确认要关吗？",
        confirmLabel = "关闭",
    ),
    ENABLE(
        title = "开启设备码？",
        body = "开启后可以在热水器面板上直接输码开阀。",
        confirmLabel = "开启",
    ),
}

/** 复制到剪贴板并提示。 */
private fun copyToClipboard(context: android.content.Context, text: String, label: String) {
    if (text.isBlank()) return
    runCatching {
        val cm = context.getSystemService(android.content.Context.CLIPBOARD_SERVICE)
            as android.content.ClipboardManager
        cm.setPrimaryClip(android.content.ClipData.newPlainText(label, text))
        android.widget.Toast.makeText(context, "$label 已复制", android.widget.Toast.LENGTH_SHORT).show()
    }
}

@Composable
private fun AccountCard(state: ShowerUiState, onLogout: () -> Unit) {
    val skin = LocalSkin.current
    val colors = LocalPocketXiaoliColors.current
    val session = state.session ?: return
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(skin.radiusCard),
        colors = CardDefaults.cardColors(containerColor = colors.surfaceCard),
    ) {
        Column(Modifier.fillMaxWidth().padding(skin.cardPadding)) {
            InfoRow("姓名", session.name.ifBlank { "未同步" })
            InfoRow("手机号", session.telephone.replaceRange(3, 7, "****"))
            Spacer(Modifier.height(skin.spaceSm))
            TextButton(onClick = onLogout) { Text("退出趣智校园账号", color = colors.danger) }
            if (state.running != null) {
                Text(
                    "正在用水时不能退出账号 —— 退了就关不掉、也算不了账。",
                    fontSize = 11.sp,
                    color = colors.textTertiary,
                )
            }
        }
    }
}

@Composable
private fun InfoRow(label: String, value: String) {
    val colors = LocalPocketXiaoliColors.current
    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        Text(label, fontSize = 13.sp, color = colors.textTertiary, modifier = Modifier.width(72.dp))
        Text(value, fontSize = 13.sp, color = colors.textPrimary, modifier = Modifier.weight(1f))
    }
}

@Composable
private fun LoginCard(
    sendingCode: Boolean,
    loggingIn: Boolean,
    codeSent: Boolean,
    onSendCode: (String) -> Unit,
    onLogin: (String, String) -> Unit,
) {
    val skin = LocalSkin.current
    val colors = LocalPocketXiaoliColors.current
    var telephone by remember { mutableStateOf("") }
    var code by remember { mutableStateOf("") }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(skin.radiusCard),
        colors = CardDefaults.cardColors(containerColor = colors.surfaceCard),
    ) {
        Column(Modifier.fillMaxWidth().padding(skin.cardPadding)) {
            Text("登录趣智校园", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = colors.textPrimary)
            Spacer(Modifier.height(skin.spaceMicro))
            Text(
                "用手机号 + 短信验证码。未注册的号码会自动创建账号 —— 就是你平时洗澡用的那个号。",
                fontSize = 12.sp,
                color = colors.textTertiary,
                lineHeight = 17.sp,
            )
            Spacer(Modifier.height(skin.spaceMd))

            OutlinedTextField(
                value = telephone,
                onValueChange = { input -> telephone = input.filter { it.isDigit() }.take(11) },
                label = { Text("手机号", fontSize = 12.sp) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(skin.spaceSm))

            Row(verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    value = code,
                    onValueChange = { input -> code = input.filter { it.isDigit() }.take(6) },
                    label = { Text("验证码", fontSize = 12.sp) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                    modifier = Modifier.weight(1f),
                )
                Spacer(Modifier.width(skin.spaceSm))
                TextButton(
                    onClick = { onSendCode(telephone) },
                    enabled = !sendingCode && telephone.length == 11,
                ) {
                    if (sendingCode) {
                        CircularProgressIndicator(modifier = Modifier.size(14.dp), strokeWidth = 2.dp)
                    } else {
                        Text(if (codeSent) "重新发送" else "获取验证码", fontSize = 13.sp)
                    }
                }
            }
            Spacer(Modifier.height(skin.spaceMd))

            Button(
                onClick = { onLogin(telephone, code) },
                enabled = !loggingIn && telephone.length == 11 && code.isNotBlank(),
                modifier = Modifier.fillMaxWidth(),
            ) {
                if (loggingIn) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(16.dp),
                        strokeWidth = 2.dp,
                        color = colors.surfaceCard,
                    )
                } else {
                    Text("登录")
                }
            }
        }
    }
}

/** `3725` → `1:02:05`；`125` → `2:05` */
private fun formatDuration(seconds: Long): String {
    val s = seconds.coerceAtLeast(0)
    val h = s / 3600
    val m = (s % 3600) / 60
    val sec = s % 60
    return if (h > 0) "%d:%02d:%02d".format(h, m, sec) else "%d:%02d".format(m, sec)
}
