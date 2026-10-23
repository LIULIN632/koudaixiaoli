package com.pocket.xiaoli.ui.service

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.webkit.CookieManager
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.outlined.Key
import androidx.compose.material.icons.outlined.Language
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Today
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.pocket.xiaoli.data.CampusServiceKeys
import com.pocket.xiaoli.data.service.CasLogin
import com.pocket.xiaoli.data.service.CampusServiceCatalog
import com.pocket.xiaoli.data.service.PortalService
import com.pocket.xiaoli.data.service.PortalSnapshot
import com.pocket.xiaoli.data.service.SchoolServiceLauncher
import com.pocket.xiaoli.data.service.TodayCampusPortal
import com.pocket.xiaoli.di.AppContainer
import com.pocket.xiaoli.network.CookieStore
import com.pocket.xiaoli.ui.theme.LocalPocketXiaoliColors
import com.pocket.xiaoli.ui.theme.LocalSkin
import com.pocket.xiaoli.util.YitAccountDefaults
import com.pocket.xiaoli.util.maskStudentId
import kotlinx.coroutines.launch

/**
 * 今日校园入口页。
 *
 * ## 它现在做三件事（2026-09-28 重做）
 *
 * 1. **门户状态说实话**：登录态与「你能用哪些服务」都从门户自己的接口读
 *    （`TodayCampusPortal`），不再是一句"登录一次后自动记住"的空承诺；
 * 2. **服务直达**：登录后列出门户返回的真实服务，点一下走
 *    `new/mobileAppShow?appId=…` —— 未登录时它自己 302 到统一认证，
 *    登录完回到原目标，正是 CAS 该有的样子。**不再需要手抄 7 个深层 URL**；
 * 3. **两条落地路径**：装了就拉起官方 App；不装就在内置网页里办事
 *    （登录页会被自动填充，档位见「我的 → 绑定信息 → 服务自动登录」）。
 *
 * 之前的版本只解释了"为什么做不到"（学校未公开统一认证地址），把用户丢到门户首页自己找。
 * 现在那句解释已经从文档和这一页上一起删掉 —— 机制拿到了，就不该再留着当挡箭牌。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TodayCampusScreen(
    container: AppContainer,
    onBack: () -> Unit,
    onOpenWeb: (String, String) -> Unit,
) {
    val skin = LocalSkin.current
    val colors = LocalPocketXiaoliColors.current
    val context = LocalContext.current
    val campus by container.selectedCampus.collectAsStateWithLifecycle()
    val autoLoginMode by container.serviceAutoLoginStore.observe().collectAsStateWithLifecycle()
    // 优先用「今日校园」自己那套账号（绑定信息页里单独存）；没有就退回教务绑定的那份。
    // 两份分开存的原因：智慧校园与教务的密码经常只改一边。
    val credential = remember(campus) {
        container.loadCasCredential()
            ?: campus.jwBaseUrl?.let { container.loadCredential(it) }
            ?: container.loadCredential("campus_bind")
    }
    val boundStudentId = credential?.first
    val appInstalled = remember {
        SchoolServiceLauncher.isAppInstalled(CampusServiceCatalog.TODAY_CAMPUS_PACKAGE, context)
    }
    val hallUrl = campus.serviceUrls[CampusServiceKeys.SMART_CAMPUS]
        ?: TodayCampusPortal.ORIGIN + "/new/index.html"

    // 门户状态：先显示上次的快照（进页面不闪白），再真拉一次。
    // 每次进页面都强制刷新 —— 从内置网页登录完退回来，正是最需要更新的时刻。
    var snapshot by remember { mutableStateOf(TodayCampusPortal.cachedSnapshot()) }
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var refreshTick by remember { mutableIntStateOf(0) }
    var nativeLoginRunning by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    LaunchedEffect(refreshTick) {
        loading = true
        error = null
        TodayCampusPortal.load(forceRefresh = true).fold(
            onSuccess = { snapshot = it },
            onFailure = { error = it.message ?: "门户暂时打不开" },
        )
        loading = false
    }

    fun openPortal(path: String, title: String) {
        onOpenWeb(path, title)
    }

    /**
     * 原生登录：**不打开网页**，直接把 CAS 走完（学「聚在工大」的做法）。
     *
     * 用到的学号密码来自本机绑定（加密存储），只在"这台手机 ↔ 学校服务器"之间完成，
     * 而且只在这一下明确点击时才用 —— 与「服务自动登录」三档里那个"要不要自动提交密码"
     * 的问题无关，因为这是用户自己按下的按钮。
     *
     * 需要图片验证码时不硬闯，退回内置网页让用户自己填（那是唯一诚实的做法）。
     */
    fun runNativeLogin() {
        val sid = credential?.first?.takeIf { it.isNotBlank() }
        val pwd = credential?.second?.takeIf { it.isNotBlank() }
        if (sid == null || pwd == null) {
            Toast.makeText(context, "先在「我的 → 绑定信息」绑定学号与密码", Toast.LENGTH_SHORT).show()
            return
        }
        nativeLoginRunning = true
        scope.launch {
            when (val outcome = CasLogin.login(studentId = sid, password = pwd, service = hallUrl)) {
                is CasLogin.Outcome.Success -> {
                    // 门户域名的会话：给服务列表与内置网页用
                    CookieStore.saveServiceCookies(TodayCampusPortal.ORIGIN, outcome.serviceCookies)
                    // CAS 域名的会话：留着以后免密扇出到其它系统（fanOut）
                    CookieStore.saveServiceCookies(CasLogin.AUTH_SERVER, outcome.authCookies)
                    TodayCampusPortal.clearCache()
                    refreshTick++
                    Toast.makeText(context, "已登录智慧校园", Toast.LENGTH_SHORT).show()
                }

                CasLogin.Outcome.CaptchaRequired -> {
                    Toast.makeText(context, "这次登录要填验证码，换到网页里登录", Toast.LENGTH_SHORT).show()
                    openPortal(hallUrl, "智慧校园")
                }

                is CasLogin.Outcome.Failed -> {
                    Toast.makeText(context, outcome.message, Toast.LENGTH_LONG).show()
                }
            }
            nativeLoginRunning = false
        }
    }

    fun clearPortalLogin() {
        // 只清「门户 + 统一认证」两个 host（别的服务页的登录态是无辜的）——
        // 同一件事在绑定信息页也要做，所以收在 CasLogin 里
        CasLogin.clearLocalSessions()
        snapshot = null
        refreshTick++
        Toast.makeText(context, "已清除智慧校园登录态", Toast.LENGTH_SHORT).show()
    }

    Scaffold(
        containerColor = colors.surfaceBase,
        topBar = {
            TopAppBar(
                title = { Text("今日校园", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "返回")
                    }
                },
                actions = {
                    IconButton(onClick = { refreshTick++ }, enabled = !loading) {
                        Icon(Icons.Outlined.Refresh, "刷新门户状态")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = colors.surfaceCard)
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
            // 卡间只有一档：大间距
            verticalArrangement = Arrangement.spacedBy(skin.sectionGap),
        ) {
            PortalStatusCard(
                snapshot = snapshot,
                loading = loading,
                error = error,
                onLogin = { openPortal(hallUrl, "智慧校园") },
                onNativeLogin = { runNativeLogin() },
                nativeLoginRunning = nativeLoginRunning,
                canNativeLogin = credential != null &&
                    credential.first.isNotBlank() &&
                    credential.second.isNotBlank(),
                onRefresh = { refreshTick++ },
                onClearLogin = { clearPortalLogin() },
            )

            PortalServiceCard(
                snapshot = snapshot,
                loading = loading,
                onOpenService = { service ->
                    openPortal(TodayCampusPortal.serviceUrl(service.appId), service.name)
                },
                onLogin = { openPortal(hallUrl, "智慧校园") },
            )

            TodayCampusAppCard(
                installed = appInstalled,
                onOpenApp = {
                    if (!SchoolServiceLauncher.launchApp(CampusServiceCatalog.TODAY_CAMPUS_PACKAGE, context)) {
                        Toast.makeText(context, "未安装今日校园", Toast.LENGTH_SHORT).show()
                    }
                },
                onInstall = { SchoolServiceLauncher.openMarketSearch("今日校园", context) }
            )

            TodayCampusAccountCard(
                boundStudentId = boundStudentId,
                autoLoginLabel = autoLoginMode.label,
                autoLoginDescription = autoLoginMode.description,
                onCopyPassword = { password -> copyToClipboard(context, "今日校园初始密码", password) }
            )

            AboutCard()
        }
    }
}

/** 门户状态：登录态 + 下一步做什么。这一页的主卡。 */
@Composable
private fun PortalStatusCard(
    snapshot: PortalSnapshot?,
    loading: Boolean,
    error: String?,
    onLogin: () -> Unit,
    onNativeLogin: () -> Unit,
    nativeLoginRunning: Boolean,
    canNativeLogin: Boolean,
    onRefresh: () -> Unit,
    onClearLogin: () -> Unit,
) {
    val skin = LocalSkin.current
    val colors = LocalPocketXiaoliColors.current
    val loggedIn = snapshot?.loggedIn == true

    SurfaceCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(42.dp)
                    .clip(RoundedCornerShape(skin.radiusChip))
                    .background(colors.accentMain.copy(alpha = 0.12f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Outlined.Language, null, tint = colors.accentMain, modifier = Modifier.size(22.dp))
            }
            Spacer(Modifier.width(skin.spaceMd))
            Column(modifier = Modifier.weight(1f)) {
                Text("智慧校园门户", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = colors.textPrimary)
                Text(
                    when {
                        error != null -> "读不到门户状态"
                        loading && snapshot == null -> "正在读取门户状态…"
                        loggedIn -> "已登录 · 下面是你账号里的服务"
                        snapshot == null -> "还没读到门户状态"
                        else -> "未登录 · 登录一次后本机记住"
                    },
                    fontSize = 12.sp,
                    color = colors.textSecondary,
                )
            }
            if (loading) {
                CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp, color = colors.accentMain)
            } else {
                StatePill(loggedIn = loggedIn, known = snapshot != null)
            }
        }

        if (error != null) {
            Spacer(Modifier.height(skin.spaceMd))
            Text(error, fontSize = 12.sp, color = colors.textTertiary, )
        }

        Spacer(Modifier.height(skin.spaceMd))
        if (loggedIn) {
            Button(
                onClick = onLogin,
                modifier = Modifier.fillMaxWidth().height(46.dp),
                shape = RoundedCornerShape(50),
                colors = ButtonDefaults.buttonColors(containerColor = colors.accentMain),
            ) {
                Text("进入门户", fontWeight = FontWeight.Bold)
            }
        } else if (canNativeLogin) {
            // 主路径：不打开网页，直接把 CAS 走完
            Button(
                onClick = onNativeLogin,
                enabled = !nativeLoginRunning,
                modifier = Modifier.fillMaxWidth().height(46.dp),
                shape = RoundedCornerShape(50),
                colors = ButtonDefaults.buttonColors(containerColor = colors.accentMain),
            ) {
                Text(if (nativeLoginRunning) "正在登录…" else "一键登录（不打开网页）", fontWeight = FontWeight.Bold)
            }
            Spacer(Modifier.height(skin.spaceSm))
            OutlinedButton(onClick = onLogin, modifier = Modifier.fillMaxWidth()) {
                Text("改用网页登录", fontSize = 13.sp)
            }
        } else {
            Button(
                onClick = onLogin,
                modifier = Modifier.fillMaxWidth().height(46.dp),
                shape = RoundedCornerShape(50),
                colors = ButtonDefaults.buttonColors(containerColor = colors.accentMain),
            ) {
                Text("登录智慧校园", fontWeight = FontWeight.Bold)
            }
        }

        Spacer(Modifier.height(skin.spaceSm))
        Row(horizontalArrangement = Arrangement.spacedBy(skin.spaceSm)) {
            OutlinedButton(onClick = onRefresh, enabled = !loading, modifier = Modifier.weight(1f)) {
                Text("刷新状态", fontSize = 13.sp)
            }
            if (loggedIn) {
                // 换账号 / 登录态坏了时用。只清门户与统一认证两个 host。
                OutlinedButton(onClick = onClearLogin, modifier = Modifier.weight(1f)) {
                    Text("退出登录", fontSize = 13.sp, color = colors.danger)
                }
            }
        }
    }
}

/** 门户返回的服务列表 —— 登录后点一下直达。 */
@Composable
private fun PortalServiceCard(
    snapshot: PortalSnapshot?,
    loading: Boolean,
    onOpenService: (PortalService) -> Unit,
    onLogin: () -> Unit,
) {
    val skin = LocalSkin.current
    val colors = LocalPocketXiaoliColors.current
    val services = if (snapshot?.loggedIn == true) snapshot.services else emptyList()

    SurfaceCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("门户里的服务", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = colors.textPrimary)
            Spacer(Modifier.weight(1f))
            if (services.isNotEmpty()) {
                Text("${services.size} 项", fontSize = 12.sp, color = colors.textTertiary)
            }
        }
        Spacer(Modifier.height(skin.spaceSm))

        when {
            snapshot?.loggedIn == true && services.isEmpty() ->
                Text(
                    "门户没有返回可用的服务。学校可能正在调整菜单，稍后再刷新看看。",
                    fontSize = 12.sp,
                    color = colors.textTertiary,
                )

            snapshot?.loggedIn == true -> services.forEach { service ->
                ServiceRow(service = service, onClick = { onOpenService(service) })
            }

            else -> {
                Text(
                    "登录后这里会列出你这个账号能用的服务，点一下直接进去。" +
                        "校园卡 / 电费 / 校园网 / 洗澡 / 热水 / 快递 / 报修 都在这个门户里 —— " +
                        "清单由门户自己下发，学校改了菜单也会跟着变。",
                    fontSize = 12.sp,
                    color = colors.textSecondary,
                    lineHeight = 18.sp,
                )
                if (!loading) {
                    Spacer(Modifier.height(skin.spaceSm))
                    TextButton(onClick = onLogin, modifier = Modifier.align(Alignment.CenterHorizontally)) {
                        Text("去登录看看", color = colors.accentMain, fontSize = 13.sp)
                    }
                }
            }
        }
    }
}

@Composable
private fun ServiceRow(service: PortalService, onClick: () -> Unit) {
    val skin = LocalSkin.current
    val colors = LocalPocketXiaoliColors.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(skin.radiusChip))
            .clickable(onClick = onClick)
            .padding(vertical = skin.spaceSm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // 首字色块而不是远程图标：门户图标是每项一个 PNG，不引图片库就得自己写缓存，
        // 而这个列表长度不定、还会随学校调整 —— 文字块零成本、且和「我的」页头像同一套做法。
        Box(
            modifier = Modifier
                .size(34.dp)
                .clip(RoundedCornerShape(skin.radiusChip))
                .background(colors.surfaceHighlight),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                service.name.take(1),
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
                color = colors.accentMain,
            )
        }
        Spacer(Modifier.width(skin.spaceMd))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                service.name,
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
                color = colors.textPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            service.category?.let { category ->
                Text(category, fontSize = 11.sp, color = colors.textTertiary, maxLines = 1)
            }
        }
        Icon(
            Icons.AutoMirrored.Filled.KeyboardArrowRight,
            contentDescription = null,
            tint = colors.textTertiary,
            modifier = Modifier.size(18.dp),
        )
    }
}

@Composable
private fun TodayCampusAppCard(
    installed: Boolean,
    onOpenApp: () -> Unit,
    onInstall: () -> Unit,
) {
    val skin = LocalSkin.current
    val colors = LocalPocketXiaoliColors.current
    SurfaceCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(42.dp)
                    .clip(RoundedCornerShape(skin.radiusChip))
                    .background(colors.accentMain.copy(alpha = 0.12f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Outlined.Today, null, tint = colors.accentMain, modifier = Modifier.size(22.dp))
            }
            Spacer(Modifier.width(skin.spaceMd))
            Column(modifier = Modifier.weight(1f)) {
                Text("官方 App", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = colors.textPrimary)
                Text(
                    if (installed) "已安装 · 签到、请假、打卡、问卷都在里面" else "未安装 · 只影响这些 App 内的事项",
                    fontSize = 12.sp,
                    color = colors.textSecondary,
                )
            }
            Surface(
                color = if (installed) colors.accentMain.copy(alpha = 0.12f) else colors.surfaceHighlight,
                shape = RoundedCornerShape(50),
            ) {
                Text(
                    if (installed) "已安装" else "未安装",
                    fontSize = 11.sp,
                    color = if (installed) colors.accentMain else colors.textTertiary,
                    modifier = Modifier.padding(horizontal = skin.spaceSm, vertical = 4.dp),
                )
            }
        }
        Spacer(Modifier.height(skin.spaceMd))
        OutlinedButton(onClick = if (installed) onOpenApp else onInstall, modifier = Modifier.fillMaxWidth()) {
            Text(if (installed) "打开今日校园" else "去应用商店安装")
        }
    }
}

@Composable
private fun TodayCampusAccountCard(
    boundStudentId: String?,
    autoLoginLabel: String,
    autoLoginDescription: String,
    onCopyPassword: (String) -> Unit,
) {
    val skin = LocalSkin.current
    val colors = LocalPocketXiaoliColors.current
    val password = remember(boundStudentId) {
        boundStudentId?.let { YitAccountDefaults.passwordFromStudentId(it) }
    }
    SurfaceCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Outlined.Key, null, tint = colors.accentMain, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(skin.spaceSm))
            Text("账号与初始密码", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = colors.textPrimary)
        }
        Spacer(Modifier.height(skin.spaceSm))
        if (boundStudentId.isNullOrBlank()) {
            Text(
                "还没绑定学号 —— 到「我的 → 绑定信息」绑定后，这里会算出初始密码，" +
                    "登录页也会自动把学号填好。",
                fontSize = 12.sp,
                color = colors.textSecondary,
                lineHeight = 18.sp,
            )
        } else {
            Text("已绑定学号：${maskStudentId(boundStudentId)}", fontSize = 13.sp, color = colors.textPrimary)
            Spacer(Modifier.height(skin.spaceXs))
            Text(YitAccountDefaults.HINT, fontSize = 12.sp, color = colors.textSecondary, lineHeight = 18.sp)
            Spacer(Modifier.height(skin.spaceMd))
            if (password != null) {
                OutlinedButton(onClick = { onCopyPassword(password) }, modifier = Modifier.fillMaxWidth()) {
                    Text("复制初始密码")
                }
                Spacer(Modifier.height(skin.spaceSm))
            }
            // 自动填充的档位在这里说清楚：用户在这一页最需要知道"它到底会不会替我填"
            Text(
                "打开服务时的填充档位：$autoLoginLabel —— $autoLoginDescription",
                fontSize = 11.sp,
                color = colors.textTertiary,
                lineHeight = 16.sp,
            )
        }
    }
}

@Composable
private fun AboutCard() {
    val skin = LocalSkin.current
    val colors = LocalPocketXiaoliColors.current
    SurfaceCard {
        Text("关于登录与直达", fontSize = 13.sp, fontWeight = FontWeight.Medium, color = colors.textPrimary)
        Spacer(Modifier.height(skin.spaceSmall))
        Text(
            "学校用统一身份认证（CAS）保护这些服务。本页的所有跳转都交给它处理：" +
                "点某个服务时若还没登录，会先跳到登录页，登录完自动回到那个服务。" +
                "「一键登录」用的是绑定在本机的学号密码，只在这台手机与学校服务器之间完成，" +
                "不经过我们的服务器；登录态存在本机。",
            fontSize = 12.sp,
            color = colors.textTertiary,
            lineHeight = 18.sp,
        )
    }
}

/** 一页里五张卡共用同一套卡片壳（圆角 / 内距 / 行距都来自令牌）。 */
@Composable
private fun SurfaceCard(content: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit) {
    val skin = LocalSkin.current
    val colors = LocalPocketXiaoliColors.current
    Surface(
        color = colors.surfaceCard,
        shape = RoundedCornerShape(skin.radiusCard),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(skin.cardPadding),
            content = content,
        )
    }
}

/** 登录态胶囊：只知道"是/否"时才给颜色，读不到就说"未知"。 */
@Composable
private fun StatePill(loggedIn: Boolean, known: Boolean) {
    val colors = LocalPocketXiaoliColors.current
    Surface(
        shape = RoundedCornerShape(50),
        color = when {
            !known -> colors.surfaceHighlight
            loggedIn -> colors.accentMain.copy(alpha = 0.12f)
            else -> colors.surfaceHighlight
        },
    ) {
        Text(
            when {
                !known -> "未知"
                loggedIn -> "已登录"
                else -> "未登录"
            },
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
            fontSize = 11.sp,
            fontWeight = if (loggedIn) FontWeight.Medium else FontWeight.Normal,
            color = if (loggedIn) colors.accentMain else colors.textSecondary,
            maxLines = 1,
        )
    }
}

// 学号脱敏统一走 com.pocket.xiaoli.util.maskStudentId（原先这里有一份
// "首 2 + 末 2" 的实现 —— 同一页面的学号与绑定信息页遮法不同，详见 StudentIdMask.kt）。

private fun copyToClipboard(context: Context, label: String, value: String) {
    val ok = runCatching {
        val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        cm.setPrimaryClip(ClipData.newPlainText(label, value))
        true
    }.getOrDefault(false)
    Toast.makeText(context, if (ok) "初始密码已复制" else "复制失败", Toast.LENGTH_SHORT).show()
}
