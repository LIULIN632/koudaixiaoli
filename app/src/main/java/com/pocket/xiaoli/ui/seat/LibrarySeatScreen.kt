package com.pocket.xiaoli.ui.seat

import android.webkit.CookieManager
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
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
import androidx.compose.material3.FilterChip
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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pocket.xiaoli.data.libraryseat.LibrarySeatLayout
import com.pocket.xiaoli.data.libraryseat.LibrarySeatLibrary
import com.pocket.xiaoli.data.libraryseat.LibrarySeatRepository
import com.pocket.xiaoli.data.libraryseat.LibrarySeatReservation
import com.pocket.xiaoli.data.libraryseat.LibrarySeatSeat
import com.pocket.xiaoli.data.libraryseat.LibrarySeatSession
import com.pocket.xiaoli.data.libraryseat.LibrarySeatUser
import com.pocket.xiaoli.data.libraryseat.TraceIntException
import com.pocket.xiaoli.ui.components.ServicePageTopBar
import com.pocket.xiaoli.ui.service.ServiceWebScreen
import com.pocket.xiaoli.ui.theme.LocalPocketXiaoliColors
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import com.pocket.xiaoli.ui.theme.LocalSkin

private const val TRACEINT_LOGIN_URL =
    "https://web.traceint.com/web/index.html?n=6ab3b57c16b7c"

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LibrarySeatScreen(onBack: () -> Unit) {
    val skin = LocalSkin.current
    val context = LocalContext.current
    val colors = LocalPocketXiaoliColors.current
    val scope = rememberCoroutineScope()
    var showLogin by remember { mutableStateOf(false) }
    var refreshKey by remember { mutableIntStateOf(0) }
    var loading by remember { mutableStateOf(false) }
    var actionLoading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var session by remember { mutableStateOf<LibrarySeatSession?>(null) }
    var libraries by remember { mutableStateOf<List<LibrarySeatLibrary>>(emptyList()) }
    var selectedLibId by remember { mutableStateOf<Int?>(null) }
    var layout by remember { mutableStateOf<LibrarySeatLayout?>(null) }
    var mapLoading by remember { mutableStateOf(false) }
    var selectedSeat by remember { mutableStateOf<LibrarySeatSeat?>(null) }

    fun readCookie(): String {
        val manager = CookieManager.getInstance()
        val hosts = listOf(
            "https://wechat.v2.traceint.com",
            "https://web.traceint.com",
        )
        val parts = hosts.mapNotNull { manager.getCookie(it) }
            .flatMap { it.split(';') }
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .distinct()
        return parts.joinToString("; ")
    }

    suspend fun loadLayout(cookie: String, libId: Int) {
        mapLoading = true
        selectedSeat = null
        LibrarySeatRepository.fetchLayout(cookie, libId)
            .onSuccess { layout = it }
            .onFailure {
                layout = null
                Toast.makeText(context, it.message ?: "座位图加载失败", Toast.LENGTH_SHORT).show()
            }
        mapLoading = false
    }

    suspend fun loadLibraries(cookie: String) {
        mapLoading = true
        LibrarySeatRepository.fetchLibraries(cookie)
            .onSuccess { list ->
                libraries = list
                val first = list.firstOrNull { it.isOpen } ?: list.firstOrNull()
                selectedLibId = first?.id
                if (first != null) {
                    loadLayout(cookie, first.id)
                } else {
                    mapLoading = false
                }
            }
            .onFailure {
                libraries = emptyList()
                mapLoading = false
                Toast.makeText(context, it.message ?: "图书馆列表加载失败", Toast.LENGTH_SHORT).show()
            }
    }

    fun runAction(successText: String, action: suspend (String) -> Result<Unit>) {
        val cookie = readCookie()
        if (cookie.isBlank()) {
            showLogin = true
            return
        }
        actionLoading = true
        scope.launch {
            val result = action(cookie)
            withContext(Dispatchers.Main) {
                if (result.isSuccess) {
                    Toast.makeText(context, successText, Toast.LENGTH_SHORT).show()
                    refreshKey++
                } else {
                    Toast.makeText(
                        context,
                        result.exceptionOrNull()?.message ?: "操作失败",
                        Toast.LENGTH_SHORT,
                    ).show()
                }
                actionLoading = false
            }
        }
    }

    fun reserveSelected() {
        val libId = selectedLibId
        val seat = selectedSeat
        if (libId == null || seat == null) {
            Toast.makeText(context, "请先选一个空座位", Toast.LENGTH_SHORT).show()
            return
        }
        val cookie = readCookie()
        if (cookie.isBlank()) {
            showLogin = true
            return
        }
        actionLoading = true
        scope.launch {
            val result = LibrarySeatRepository.reserveSeat(cookie, libId, seat.key)
            withContext(Dispatchers.Main) {
                if (result.isSuccess) {
                    Toast.makeText(context, "预约成功", Toast.LENGTH_SHORT).show()
                    refreshKey++
                } else {
                    val e = result.exceptionOrNull()
                    if (e is TraceIntException && e.code == 1003) {
                        Toast.makeText(context, "该座位需要验证码，已打开网页选座", Toast.LENGTH_SHORT).show()
                        showLogin = true
                    } else {
                        Toast.makeText(context, e?.message ?: "预约失败", Toast.LENGTH_SHORT).show()
                    }
                }
                actionLoading = false
            }
        }
    }

    LaunchedEffect(refreshKey) {
        val cookie = readCookie()
        if (cookie.isBlank()) {
            loading = false
            error = null
            session = null
            libraries = emptyList()
            layout = null
            return@LaunchedEffect
        }
        loading = true
        LibrarySeatRepository.fetchSession(cookie)
            .onSuccess {
                session = it
                error = null
                if (it.user != null && it.reservation == null) {
                    loadLibraries(cookie)
                }
            }
            .onFailure {
                session = null
                error = it.message ?: "登录已失效，请重新登录"
            }
        loading = false
    }

    if (showLogin) {
        ServiceWebScreen(
            url = TRACEINT_LOGIN_URL,
            title = "图书馆座位",
            onBack = {
                showLogin = false
                refreshKey++
            },
        )
        return
    }

    Scaffold(
        containerColor = colors.surfaceBase,
        topBar = {
            ServicePageTopBar(
                title = "图书馆座位",
                onBack = onBack,
                actions = {
                    IconButton(onClick = { refreshKey++ }) {
                        Icon(Icons.Default.Refresh, "刷新")
                    }
                }
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .background(colors.surfaceBase)
                .verticalScroll(rememberScrollState())
                .padding(skin.spaceLg),
            verticalArrangement = Arrangement.spacedBy(skin.spaceMd),
        ) {
            when {
                loading -> {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(220.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        CircularProgressIndicator(color = colors.accentMain)
                    }
                }

                session?.user == null -> {
                    LoginCard(
                        error = error,
                        onLogin = { showLogin = true },
                        onRetry = { refreshKey++ },
                    )
                }

                else -> {
                    UserCard(user = session!!.user!!)
                    if (session!!.reservation == null) {
                        SeatBrowserCard(
                            libraries = libraries,
                            selectedLibId = selectedLibId,
                            layout = layout,
                            mapLoading = mapLoading,
                            selectedSeat = selectedSeat,
                            actionLoading = actionLoading,
                            onSelectLibrary = { id ->
                                selectedLibId = id
                                val cookie = readCookie()
                                if (cookie.isNotBlank()) {
                                    scope.launch { loadLayout(cookie, id) }
                                }
                            },
                            onSelectSeat = { selectedSeat = it },
                            onReserve = { reserveSelected() },
                            onOpenWeb = { showLogin = true },
                        )
                    } else {
                        ReservationCard(
                            reservation = session!!.reservation,
                            sToken = session!!.sToken,
                            actionLoading = actionLoading,
                            onOpenWeb = { showLogin = true },
                            onHold = {
                                runAction("已暂离") { LibrarySeatRepository.hold(it) }
                            },
                            onHoldConfirm = {
                                runAction("已回座") { LibrarySeatRepository.holdConfirm(it) }
                            },
                            onCancel = { sToken ->
                                runAction("已取消预约") {
                                    LibrarySeatRepository.cancel(it, sToken)
                                }
                            },
                        )
                    }
                }
            }

            Text(
                "数据来自我去图书馆 H5 登录态；二维码 / 蓝牙签到可在网页内完成。",
                fontSize = 11.sp,
                color = colors.textTertiary,
            )
        }
    }
}

@Composable
private fun LoginCard(
    error: String?,
    onLogin: () -> Unit,
    onRetry: () -> Unit,
) {
    val skin = LocalSkin.current
    val colors = LocalPocketXiaoliColors.current
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(skin.radiusCard),
        colors = CardDefaults.cardColors(containerColor = colors.surfaceCard),
    ) {
        Column(
            modifier = Modifier.padding(skin.spaceXl),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                "还没有登录图书馆座位",
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                color = colors.textPrimary,
            )
            Spacer(Modifier.height(skin.spaceSm))
            Text(
                error ?: "登录一次后，App 会复用登录态，在原生页里显示当前预约和操作按钮。",
                fontSize = 12.sp,
                color = if (error != null) colors.danger else colors.textSecondary,
            )
            Spacer(Modifier.height(skin.spaceLg))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Button(onClick = onLogin) { Text("打开网页登录") }
                OutlinedButton(onClick = onRetry) { Text("已登录，重试") }
            }
        }
    }
}

@Composable
private fun UserCard(user: LibrarySeatUser) {
    val skin = LocalSkin.current
    val colors = LocalPocketXiaoliColors.current
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(skin.radiusCard),
        colors = CardDefaults.cardColors(containerColor = colors.surfaceCard),
    ) {
        Column(modifier = Modifier.padding(skin.spaceLg)) {
            Text(
                user.name ?: "同学",
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                color = colors.textPrimary,
            )
            val subtitle = listOfNotNull(user.school, user.studentNo)
                .filter { it.isNotBlank() }
                .joinToString(" · ")
            if (subtitle.isNotBlank()) {
                Spacer(Modifier.height(skin.spaceXs))
                Text(subtitle, fontSize = 12.sp, color = colors.textSecondary)
            }
        }
    }
}

@Composable
private fun SeatBrowserCard(
    libraries: List<LibrarySeatLibrary>,
    selectedLibId: Int?,
    layout: LibrarySeatLayout?,
    mapLoading: Boolean,
    selectedSeat: LibrarySeatSeat?,
    actionLoading: Boolean,
    onSelectLibrary: (Int) -> Unit,
    onSelectSeat: (LibrarySeatSeat) -> Unit,
    onReserve: () -> Unit,
    onOpenWeb: () -> Unit,
) {
    val skin = LocalSkin.current
    val colors = LocalPocketXiaoliColors.current
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(skin.radiusCard),
        colors = CardDefaults.cardColors(containerColor = colors.surfaceCard),
    ) {
        Column(modifier = Modifier.padding(skin.spaceLg)) {
            Text("原生选座", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = colors.textPrimary)
            Spacer(Modifier.height(skin.spaceXs))
            Text("选择一个图书馆和空座位，点下方按钮直接预约。", fontSize = 11.sp, color = colors.textTertiary)
            Spacer(Modifier.height(10.dp))

            if (libraries.isEmpty()) {
                Text("暂无可预约图书馆", fontSize = 13.sp, color = colors.textSecondary)
                Spacer(Modifier.height(10.dp))
                Button(onClick = onOpenWeb, modifier = Modifier.fillMaxWidth()) {
                    Text("去网页看看")
                }
                return@Column
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(skin.spaceSm),
            ) {
                libraries.forEach { library ->
                    FilterChip(
                        selected = selectedLibId == library.id,
                        onClick = { onSelectLibrary(library.id) },
                        label = {
                            Text(
                                listOfNotNull(library.name, library.floor)
                                    .filter { it.isNotBlank() }
                                    .joinToString(" "),
                                fontSize = 11.sp,
                            )
                        },
                    )
                }
            }

            Spacer(Modifier.height(10.dp))

            if (mapLoading) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(180.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    CircularProgressIndicator(color = colors.accentMain)
                }
            } else if (layout == null || layout.seats.none { it.type == 1 }) {
                Text("这个馆暂时没有可选择的座位图", fontSize = 13.sp, color = colors.textSecondary)
                Spacer(Modifier.height(10.dp))
                Button(onClick = onOpenWeb, modifier = Modifier.fillMaxWidth()) {
                    Text("去网页选座")
                }
            } else {
                NativeSeatMap(
                    layout = layout,
                    selectedKey = selectedSeat?.key,
                    onSeatClick = onSelectSeat,
                )
                Spacer(Modifier.height(skin.spaceSm))
                Text(
                    selectedSeat?.name?.let { "已选座位：$it" } ?: "点一个白色座位",
                    fontSize = 12.sp,
                    color = if (selectedSeat == null) colors.textTertiary else colors.accentMain,
                    fontWeight = FontWeight.Medium,
                )
                Spacer(Modifier.height(skin.spaceSm))
                Row(horizontalArrangement = Arrangement.spacedBy(skin.spaceSm)) {
                    Button(
                        onClick = onReserve,
                        enabled = !actionLoading && selectedSeat != null,
                        modifier = Modifier.weight(1f),
                    ) { Text("预约选中座位") }
                    OutlinedButton(
                        onClick = onOpenWeb,
                        modifier = Modifier.weight(1f),
                    ) { Text("网页选座") }
                }
            }
        }
    }
}

@Composable
private fun NativeSeatMap(
    layout: LibrarySeatLayout,
    selectedKey: String?,
    onSeatClick: (LibrarySeatSeat) -> Unit,
) {
    val skin = LocalSkin.current
    val colors = LocalPocketXiaoliColors.current
    val cell = 34.dp
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(300.dp)
            .background(colors.surfaceAlt, RoundedCornerShape(skin.radiusChip)),
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .horizontalScroll(rememberScrollState())
                .verticalScroll(rememberScrollState()),
        ) {
            Box(
                modifier = Modifier.size(
                    width = cell * (layout.maxX + 1).coerceAtLeast(1),
                    height = cell * (layout.maxY + 1).coerceAtLeast(1),
                ),
            ) {
                layout.seats.filter { it.type == 1 }.forEach { seat ->
                    val available = !seat.occupied && seat.seatStatus <= 1
                    val selected = seat.key == selectedKey
                    val bg = when {
                        selected -> colors.accentMain
                        !available -> when (seat.seatStatus) {
                            2 -> Color(0xFF53B175)
                            4 -> Color(0xFFDADADA)
                            5 -> Color(0xFFFE9B4B)
                            else -> Color(0xFFF36F6F)
                        }
                        else -> colors.surfaceCard
                    }
                    Box(
                        modifier = Modifier
                            .offset(x = cell * seat.x, y = cell * seat.y)
                            .size(cell - 4.dp)
                            .clip(RoundedCornerShape(skin.radiusMicro))
                            .background(bg)
                            .border(
                                width = 1.dp,
                                color = if (selected) colors.accentMain else colors.borderDivider,
                                shape = RoundedCornerShape(skin.radiusMicro),
                            )
                            .clickable(enabled = available) { onSeatClick(seat) },
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = seat.name.orEmpty(),
                            fontSize = 9.sp,
                            color = if (selected || !available) Color.White else colors.textPrimary,
                            maxLines = 1,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ReservationCard(
    reservation: LibrarySeatReservation?,
    sToken: String?,
    actionLoading: Boolean,
    onOpenWeb: () -> Unit,
    onHold: () -> Unit,
    onHoldConfirm: () -> Unit,
    onCancel: (String) -> Unit,
) {
    val skin = LocalSkin.current
    val colors = LocalPocketXiaoliColors.current
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(skin.radiusCard),
        colors = CardDefaults.cardColors(containerColor = colors.surfaceCard),
    ) {
        Column(modifier = Modifier.padding(skin.spaceLg)) {
            Text(
                "当前预约",
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                color = colors.textTertiary,
            )
            Spacer(Modifier.height(10.dp))

            if (reservation == null) {
                Text("当前没有座位预约", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = colors.textPrimary)
                Spacer(Modifier.height(skin.spaceMd))
                Button(onClick = onOpenWeb, modifier = Modifier.fillMaxWidth()) {
                    Text("去网页选座")
                }
                return@Column
            }

            Text(
                reservation.seatName ?: "座位待定",
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold,
                color = colors.accentMain,
            )
            Spacer(Modifier.height(skin.spaceSmall))
            val library = listOfNotNull(reservation.libraryName, reservation.libraryFloor)
                .filter { it.isNotBlank() }
                .joinToString(" · ")
            if (library.isNotBlank()) {
                Text(library, fontSize = 14.sp, color = colors.textPrimary)
            }
            Spacer(Modifier.height(skin.spaceSm))
            ReservationInfo("预约日期", reservation.date)
            ReservationInfo("到期时间", reservation.expDate)
            ReservationInfo("剩余时间", reservation.diff)
            ReservationInfo("暂离时间", reservation.holdDate)

            Spacer(Modifier.height(skin.spaceCompact))
            Row(horizontalArrangement = Arrangement.spacedBy(skin.spaceSm)) {
                Button(
                    onClick = onOpenWeb,
                    enabled = !actionLoading,
                    modifier = Modifier.weight(1f),
                ) { Text("签到/扫码") }
                OutlinedButton(
                    onClick = onHold,
                    enabled = !actionLoading,
                    modifier = Modifier.weight(1f),
                ) { Text("暂离") }
            }
            Spacer(Modifier.height(skin.spaceSm))
            Row(horizontalArrangement = Arrangement.spacedBy(skin.spaceSm)) {
                OutlinedButton(
                    onClick = onHoldConfirm,
                    enabled = !actionLoading,
                    modifier = Modifier.weight(1f),
                ) { Text("回座") }
                if (!sToken.isNullOrBlank()) {
                    OutlinedButton(
                        onClick = { onCancel(sToken) },
                        enabled = !actionLoading,
                        modifier = Modifier.weight(1f),
                    ) { Text("取消预约") }
                } else {
                    OutlinedButton(
                        onClick = onOpenWeb,
                        enabled = !actionLoading,
                        modifier = Modifier.weight(1f),
                    ) { Text("预约规则") }
                }
            }
        }
    }
}

@Composable
private fun ReservationInfo(label: String, value: String?) {
    val skin = LocalSkin.current
    if (value.isNullOrBlank()) return
    val colors = LocalPocketXiaoliColors.current
    Row(modifier = Modifier.padding(vertical = skin.spaceTiny)) {
        Text(label, fontSize = 12.sp, color = colors.textTertiary)
        Spacer(Modifier.width(10.dp))
        Text(value, fontSize = 12.sp, color = colors.textPrimary, fontWeight = FontWeight.Medium)
    }
}
