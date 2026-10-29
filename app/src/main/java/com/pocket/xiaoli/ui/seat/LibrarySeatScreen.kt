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
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
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
import com.pocket.xiaoli.ui.components.AppCard
import com.pocket.xiaoli.ui.components.MiuixChoice
import com.pocket.xiaoli.ui.components.MiuixChoiceRow
import com.pocket.xiaoli.ui.components.ServiceHeroCard
import com.pocket.xiaoli.ui.components.ServiceHeroChip
import com.pocket.xiaoli.ui.components.ServicePageTopBar
import com.pocket.xiaoli.ui.service.ServiceWebScreen
import com.pocket.xiaoli.ui.theme.LocalPocketXiaoliColors
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import com.pocket.xiaoli.ui.theme.LocalSkin
import com.pocket.xiaoli.ui.theme.ServiceAccent
import com.pocket.xiaoli.util.maskStudentId

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

    // 登录页是整屏 `return`，主页面会整体离开组合。滚动状态必须提到分流之前，
    // 否则登录完回来页面又跳回顶部。
    val pageScrollState = rememberScrollState()

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
                .verticalScroll(pageScrollState)
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
                            reservation = session!!.reservation!!,
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

/**
 * 未登录：说清"为什么要登录"，并给两条出路。
 *
 * `internal` 是为了让 `LibrarySeatScreenshotTest` 能凭空渲这一态 ——
 * 整页的登录态要看 `CookieManager` 里有没有 traceint 的 Cookie，测试造不出来。
 */
@Composable
internal fun LoginCard(
    error: String?,
    onLogin: () -> Unit,
    onRetry: () -> Unit,
) {
    val skin = LocalSkin.current
    val colors = LocalPocketXiaoliColors.current
    AppCard(modifier = Modifier.fillMaxWidth()) {
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
                textAlign = TextAlign.Center,
                lineHeight = 18.sp,
            )
            Spacer(Modifier.height(skin.spaceLg))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Button(onClick = onLogin) { Text("打开网页登录") }
                OutlinedButton(onClick = onRetry) { Text("已登录，重试") }
            }
        }
    }
}

/**
 * 登录者是谁。
 *
 * ⚠️ 学号必须脱敏：`maskStudentId` 是全项目共用的一条规则（「我的」页、选课页、
 * 修课情况都走它），这一页原先把接口给的学号**整串印出来** —— 截图分享时就直接漏出去。
 */
@Composable
internal fun UserCard(user: LibrarySeatUser) {
    val skin = LocalSkin.current
    val colors = LocalPocketXiaoliColors.current
    AppCard(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(skin.spaceLg)) {
            Text(
                user.name ?: "同学",
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                color = colors.textPrimary,
            )
            val subtitle = listOfNotNull(
                user.school?.takeIf { it.isNotBlank() },
                user.studentNo?.takeIf { it.isNotBlank() }?.let { "学号 ${maskStudentId(it)}" },
            ).filter { it.isNotBlank() }.joinToString(" · ")
            if (subtitle.isNotBlank()) {
                Spacer(Modifier.height(skin.spaceXs))
                Text(subtitle, fontSize = 12.sp, color = colors.textSecondary)
            }
        }
    }
}

@Composable
internal fun SeatBrowserCard(
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
    AppCard(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(skin.spaceLg)) {
            Text("原生选座", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = colors.textPrimary)
            Spacer(Modifier.height(skin.spaceXs))
            Text("选一个馆、点一个白色座位，下面按钮直接预约。", fontSize = 11.sp, color = colors.textTertiary)
            Spacer(Modifier.height(10.dp))

            if (libraries.isEmpty()) {
                Text("暂无可预约图书馆", fontSize = 13.sp, color = colors.textSecondary)
                Spacer(Modifier.height(10.dp))
                Button(onClick = onOpenWeb, modifier = Modifier.fillMaxWidth()) {
                    Text("去网页看看")
                }
                return@Column
            }

            // 馆名与楼层合成一枚标签。用共享的横滚胶囊行而不是 Material 的 FilterChip：
            // 后者与资料站 / 修课情况那两处的胶囊不是一套，同一 App 里出现两种 chip 最掉价。
            MiuixChoiceRow(
                choices = libraries.map { library ->
                    MiuixChoice(
                        id = library.id.toString(),
                        label = listOfNotNull(library.name, library.floor)
                            .filter { it.isNotBlank() }
                            .joinToString(" ")
                            .let { if (library.isOpen) it else "$it（未开放）" },
                    )
                },
                selectedId = selectedLibId?.toString(),
                onSelect = { id -> id?.toIntOrNull()?.let(onSelectLibrary) },
            )

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
                // 点了不可选的格子要说一句话。以前那些格子 `clickable(enabled = false)`，
                // 点下去毫无反应，学生只会反复戳或者以为 App 卡住了。
                var denied by remember { mutableStateOf<String?>(null) }
                NativeSeatMap(
                    layout = layout,
                    selectedKey = selectedSeat?.key,
                    onSeatClick = { seat ->
                        if (seat.selectable) {
                            denied = null
                            onSelectSeat(seat)
                        } else {
                            denied = "座位 ${seat.name ?: seat.key} 现在不可选（学校页面把它标成了别的状态）"
                        }
                    },
                )
                Spacer(Modifier.height(skin.spaceSm))
                SeatLegend(available = layout.seats.count { it.type == 1 && it.selectable })
                Spacer(Modifier.height(skin.spaceXs))
                Text(
                    denied ?: (selectedSeat?.name?.let { "已选座位：$it" } ?: "点一个白色座位"),
                    fontSize = 12.sp,
                    color = when {
                        denied != null -> colors.warning
                        selectedSeat == null -> colors.textTertiary
                        else -> colors.accentMain
                    },
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

/**
 * 一个座位现在能不能点。
 *
 * 规则只写这一处（原先内联在座位图的绘制里，图例与点击判断各抄一遍）：
 * `seat_status` 0/1 且未被占用 = 可选。
 *
 * ⚠️ 其余状态码**不去解释成中文**：我们没拿到学校的权威码表，硬编一个"绿色 = 已预约"
 * 就是往界面上写假数据。图例只说"这些颜色是别的状态、点不动"。
 */
internal val LibrarySeatSeat.selectable: Boolean get() = !occupied && seatStatus <= 1

/** 学校 H5 自己的状态色（不是主题色，换主题也不该跟着变）。 */
private val seatStatusColors = mapOf(
    2 to Color(0xFF53B175),
    4 to Color(0xFFDADADA),
    5 to Color(0xFFFE9B4B),
)

/** 码表之外的状态一律给这一档，至少"不是可选"这件事看得出来。 */
private val seatUnknownColor = Color(0xFFF36F6F)

@Composable
internal fun NativeSeatMap(
    layout: LibrarySeatLayout,
    selectedKey: String?,
    onSeatClick: (LibrarySeatSeat) -> Unit,
) {
    val skin = LocalSkin.current
    val colors = LocalPocketXiaoliColors.current
    val cell = 34.dp
    // 高度**跟着座位行数走**，最多 300dp。原先写死 300dp：学校只给三行座位时，
    // 下面就是整整一片空灰底，看着像图没加载出来。
    val mapHeight = (cell * (layout.maxY + 1).coerceAtLeast(1)).coerceAtMost(300.dp)
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(mapHeight)
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
                    val available = seat.selectable
                    val selected = seat.key == selectedKey
                    val bg = when {
                        selected -> colors.accentMain
                        !available -> seatStatusColors[seat.seatStatus] ?: seatUnknownColor
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
                            // ⚠️ 不可选的格子也接受点击：以前是 `enabled = false`，
                            // 点下去**一点回应都没有**，用户只会反复戳或者以为 App 卡了。
                            .clickable { onSeatClick(seat) },
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

/**
 * 座位图图例。
 *
 * 为什么必须有：这一屏原先只有色块，**没有任何一处**说"白色是可以点的、彩色是不能点的"，
 * 第一次进来只能靠猜。
 *
 * ⚠️ 这里刻意**不给每个状态码配中文名**：我们没拿到学校那份权威码表，
 * 把 `seat_status = 2` 写成"已预约"就是往界面上编数据。
 * 所以只说我们确定的两档（可选 / 已选），其余归成一句"别的状态、点不动"。
 */
@Composable
private fun SeatLegend(available: Int) {
    val skin = LocalSkin.current
    val colors = LocalPocketXiaoliColors.current
    val shape = RoundedCornerShape(2.dp)
    Column(verticalArrangement = Arrangement.spacedBy(skin.spaceXs)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            listOf("可选" to colors.surfaceCard, "已选" to colors.accentMain).forEach { (label, color) ->
                Box(
                    modifier = Modifier
                        .size(10.dp)
                        .clip(shape)
                        .background(color)
                        .border(1.dp, colors.borderDivider, shape),
                )
                Spacer(Modifier.width(skin.spaceMicro))
                Text(label, fontSize = 10.sp, color = colors.textTertiary)
                Spacer(Modifier.width(skin.spaceMd))
            }
            Text("本图可点 " + available + " 个", fontSize = 10.sp, color = colors.textSecondary)
        }
        Text("其余颜色是学校页面标的其他状态，点不动。", fontSize = 10.sp, color = colors.textTertiary)
    }
}

/**
 * 已有预约时的两块：主视觉卡说"座在哪、还剩多久"，下面一张卡给四个动作。
 *
 * `internal` 是为了让测试渲得出来 —— 这一态要看登录后的接口回包，测试造不出来。
 *
 * ## 2026-10-29 从"白卡里的文字列表"改成主视觉卡
 *
 * 原先座位号是卡里一行 22sp 主色字，**剩余时间**与到期时间混在四行 12sp 灰字里。
 * 可这三个字段恰恰是这一页唯一要"随时抬头看"的东西 —— 还剩五分钟就该决定要不要暂离。
 * 现在座位号当主角，剩余 / 到期 / 暂离截止压成胶囊，动作按钮另起一张卡。
 *
 * ⚠️ 顺手删掉了"没有预约"那条分支：调用方只在 `reservation != null` 时才渲这张卡，
 * 那条分支永远走不到，留着只会让人以为"这里可能显示没预约"。
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun ReservationCard(
    reservation: LibrarySeatReservation,
    sToken: String?,
    actionLoading: Boolean,
    onOpenWeb: () -> Unit,
    onHold: () -> Unit,
    onHoldConfirm: () -> Unit,
    onCancel: (String) -> Unit,
) {
    val skin = LocalSkin.current
    Column(verticalArrangement = Arrangement.spacedBy(skin.spaceMd)) {
        ServiceHeroCard(accent = ServiceAccent.QUERY) {
            Text("当前预约", fontSize = 12.sp, color = Color.White.copy(alpha = 0.9f))
            Spacer(Modifier.height(skin.spaceXs))
            Text(
                reservation.seatName ?: "座位待定",
                fontSize = 34.sp,
                fontWeight = FontWeight.ExtraBold,
                color = Color.White,
                lineHeight = 42.sp,
            )
            val where = listOfNotNull(reservation.libraryName, reservation.libraryFloor)
                .filter { it.isNotBlank() }
                .joinToString(" · ")
            if (where.isNotBlank()) {
                Spacer(Modifier.height(skin.spaceMicro))
                Text(where, fontSize = 13.sp, color = Color.White.copy(alpha = 0.9f))
            }
            val chips = buildList {
                reservation.diff?.takeIf { it.isNotBlank() }?.let { add("剩余 $it") }
                reservation.expDate?.takeIf { it.isNotBlank() }?.let { add("到期 $it") }
                reservation.date?.takeIf { it.isNotBlank() }?.let { add("开始 $it") }
                reservation.holdDate?.takeIf { it.isNotBlank() }?.let { add("暂离至 $it") }
            }
            if (chips.isNotEmpty()) {
                Spacer(Modifier.height(skin.spaceLg))
                // FlowRow 而不是单行：四个胶囊在 360dp 上装不下，挤成一行会被裁掉。
                // 这四个字段改造前是卡里的四行灰字，一个都不能丢 —— 换版式不是删信息。
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(skin.spaceSm),
                    verticalArrangement = Arrangement.spacedBy(skin.spaceSm),
                ) {
                    chips.forEach { ServiceHeroChip(it) }
                }
            }
        }
        AppCard(modifier = Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier.padding(skin.spaceLg),
                verticalArrangement = Arrangement.spacedBy(skin.spaceSm),
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(skin.spaceSm)) {
                    Button(
                        onClick = onOpenWeb,
                        enabled = !actionLoading,
                        modifier = Modifier.weight(1f),
                    ) { Text("签到 / 扫码") }
                    OutlinedButton(
                        onClick = onHold,
                        enabled = !actionLoading,
                        modifier = Modifier.weight(1f),
                    ) { Text("暂离") }
                }
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
