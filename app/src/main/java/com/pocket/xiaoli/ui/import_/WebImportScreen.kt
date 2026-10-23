package com.pocket.xiaoli.ui.import_

import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.net.http.SslError
import android.view.ViewGroup
import android.webkit.*
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.outlined.*
import androidx.compose.material.icons.outlined.Language
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.viewmodel.compose.viewModel
import com.pocket.xiaoli.PocketXiaoliApplication
import com.pocket.xiaoli.data.JwSystemType
import com.pocket.xiaoli.data.School
import com.pocket.xiaoli.ui.components.SchoolFavoriteButton
import com.pocket.xiaoli.ui.components.PocketPasswordTransformation
import com.pocket.xiaoli.ui.profile.components.SchoolRequestDialog
import com.pocket.xiaoli.network.CookieStore
import com.pocket.xiaoli.ui.theme.*
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.pocket.xiaoli.ui.components.SchoolRow

@OptIn(ExperimentalMaterial3Api::class)
@SuppressLint("SetJavaScriptEnabled")
@Composable
fun WebImportScreen(
    onBack: () -> Unit,
    freeMode: Boolean = false,
    sessionKey: Int = 0
) {
    val skin = LocalSkin.current
    val colors = LocalPocketXiaoliColors.current
    val isDark = LocalDarkMode.current
    val context = LocalContext.current
    val app = context.applicationContext as PocketXiaoliApplication
    val viewModel: WebImportViewModel = viewModel(
        factory = WebImportViewModel.Factory(app.repository, app.appContainer.schoolRegistry, app.appContainer) { app.appContainer.activeSchemeId.value }
    )

    // Reset to school list when entering in non-free mode (each sessionKey = fresh entry)
    LaunchedEffect(sessionKey) {
        if (!freeMode) {
            viewModel.clearSchool()
        }
    }

    val searchQuery by viewModel.searchQuery.collectAsStateWithLifecycle()
    val selectedSchool by viewModel.selectedSchool.collectAsStateWithLifecycle()
    val filteredSchools by viewModel.filteredSchools.collectAsStateWithLifecycle()
    val favoriteIds by viewModel.favoriteIds.collectAsStateWithLifecycle()
    val importedCount by viewModel.importedCount.collectAsStateWithLifecycle()
    val isImporting by viewModel.isImporting.collectAsStateWithLifecycle()
    val toastMessage by viewModel.toastMessage.collectAsStateWithLifecycle()
    val parseLogs by viewModel.parseLogs.collectAsStateWithLifecycle()
    val customUrl by viewModel.customUrl.collectAsStateWithLifecycle()
    val freeUrlActive by viewModel.freeUrlActive.collectAsStateWithLifecycle()
    val urlHistory by viewModel.urlHistory.collectAsStateWithLifecycle()

    var loadingProgress by remember { mutableFloatStateOf(0f) }
    var currentUrl by remember { mutableStateOf("") }
    var pageTitle by remember { mutableStateOf("教务登录") }
    var showLogs by remember { mutableStateOf(false) }
    var showUrlEditor by remember { mutableStateOf(false) }
    var urlEditText by remember { mutableStateOf("") }
    var showFreeUrlDialog by remember { mutableStateOf(freeMode) }
    var showSchoolRequest by remember { mutableStateOf(false) }
    var freeUrlInput by remember { mutableStateOf("") }
    // 手动添加/完善学校信息弹窗的输入（提升到页面级，便于点空地址学校时预填）
    var freeNameInput by remember { mutableStateOf("") }
    var freeJwType by remember { mutableStateOf(JwSystemType.ZHENGFANG) }
    var pendingBlankSchool by remember { mutableStateOf<School?>(null) }
    var showCredentialSheet by remember { mutableStateOf(false) }
    var passwordVisible by remember { mutableStateOf(true) }

    // Initialize URL history from SharedPreferences
    LaunchedEffect(Unit) {
        viewModel.initUrlHistory(context)
    }

    // Show toast messages
    LaunchedEffect(toastMessage) {
        toastMessage?.let {
            Toast.makeText(context, it, Toast.LENGTH_LONG).show()
            viewModel.toastShown()
        }
    }

    // Load saved account when school changes
    LaunchedEffect(selectedSchool) {
        val baseUrl = viewModel.getEffectiveUrl()?.trimEnd('/') ?: selectedSchool?.baseUrl?.trimEnd('/')
        if (!baseUrl.isNullOrBlank()) {
            viewModel.loadSavedAccount(baseUrl)
        }
    }

    // ScheduleBridge — connects JS to ViewModel
    val scheduleBridge = remember {
        ScheduleBridge(
            viewModel = viewModel,
            onHtmlCaptured = { html -> viewModel.onHtmlCaptured(html) }
        )
    }

    // WebView instance
    val webView = remember {
        WebView(context).apply {
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
            with(settings) {
                javaScriptEnabled = true
                domStorageEnabled = true
                loadWithOverviewMode = true
                useWideViewPort = true
                builtInZoomControls = true
                displayZoomControls = false
                mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
                allowFileAccess = false
                @Suppress("DEPRECATION")
                savePassword = true
                @Suppress("DEPRECATION")
                saveFormData = true
                cacheMode = WebSettings.LOAD_NO_CACHE
                // Allow access to asset files for JS adapter scripts
                @Suppress("DEPRECATION")
                allowFileAccessFromFileURLs = true
            }
            // ⚠️ lint 的 `JavascriptInterface` 是**误报**，不要"修"它：
            // `remember { ScheduleBridge(...) }` 让 lint 只看到泛型 `T`，于是它认为
            // "这个接口没有任何方法带 @JavascriptInterface"。实际 `ScheduleBridge`
            // 的 9 个方法**全部**都带了该注解。
            // 这个抑制与 `app/build.gradle.kts` 的 `abortOnError = true` 是一对：
            // 删掉这里，构建会立刻失败——那是设计如此（宁可吵一次，也不要退回"lint 不拦构建"）。
            @Suppress("JavascriptInterface")
            addJavascriptInterface(scheduleBridge, "AndroidBridge")
        }
    }

    // 本校锁定（或恢复上次选择）时不会出现学校列表，需要自动打开一次教务系统页面，
    // 否则 WebView 永远空白：原先只有「点击学校行」才会 loadUrl。
    LaunchedEffect(selectedSchool, webView) {
        val school = selectedSchool ?: return@LaunchedEffect
        val target = viewModel.getEffectiveUrl()?.trimEnd('/' )
            ?: school.baseUrl?.trimEnd('/').orEmpty()
        if (target.isBlank()) return@LaunchedEffect
        if (webView.url.isNullOrBlank()) {
            webView.loadUrl(target)
        }
    }

    // Give ViewModel access to WebView for promise resolution
    LaunchedEffect(webView) {
        viewModel.webView = webView
    }

    // Configure WebView client when school changes
    LaunchedEffect(selectedSchool) {
        webView.webViewClient = object : WebViewClient() {
            override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                loadingProgress = 0f
                url?.let { currentUrl = it }
                // 记录本次导入访问过的主机，供离开页面时定向清理 cookie
                viewModel.recordVisitedHost(url)
                // 页面开始加载即作废旧页面的待执行注入重试，防止跳转到非登录页后
                // 延迟任务仍把账号密码填入新页面（如成绩查询页的输入框）。
                viewModel.cancelPendingInjections()
            }

            override fun onPageFinished(view: WebView?, url: String?) {
                loadingProgress = 1f
                // Inject bridge promise polyfill after every page navigation
                viewModel.injectBridgePromise()
                // Auto-fill saved credentials into WebView login form
                viewModel.injectCredentialsToWebView(url)
                // Extract and persist cookies for OkHttp reuse
                CookieStore.extractFromWebView(url)
            }

            override fun onReceivedError(
                view: WebView?,
                request: android.webkit.WebResourceRequest?,
                error: android.webkit.WebResourceError?
            ) {
                if (request?.isForMainFrame == true) {
                    pageTitle = "加载失败：" + (error?.description ?: "") + "（" + request.url + "）"
                }
            }

            /**
             * ⚠️ 这里**必须 cancel，不能 proceed**（2026-09-30 修）。
             *
             * 原来写的是 `handler?.proceed()` —— 等于对任何证书错误都放行。
             * 而这个页面正是**用户输入教务账号密码的地方**，放行 SSL 错误意味着
             * 中间人可以替换证书、拿到明文凭据。这是本页最不该出现的一行代码。
             *
             * 之所以可以安全地改严：本项目服务的教务主机经实测**证书全部正常**
             * （`*.yitsd.edu.cn`，上海寰度签发，默认校验直接通过；详见
             * `docs/安全审计-TLS.md`）。也就是说这里的历史放宽**从一开始就没必要**。
             *
             * 判据用 lint 的规则名就是 `WebViewClientOnReceivedSslError`。
             *
             * 代价与兜底：若哪天学校证书过期且未及时续，这里会**明确报出来**
             * （而不是静默降级成"能打开但被监听"）。提示里写清了原因和该找谁，
             * 不至于让用户对着一个空白页猜。
             */
            override fun onReceivedSslError(
                view: WebView?, handler: SslErrorHandler?, error: SslError?
            ) {
                handler?.cancel()
                pageTitle = "该网站的证书校验未通过，为了账号安全已停止加载"
            }
        }
        webView.webChromeClient = object : WebChromeClient() {
            override fun onProgressChanged(view: WebView?, newProgress: Int) {
                loadingProgress = newProgress / 100f
            }
            override fun onReceivedTitle(view: WebView?, title: String?) {
                title?.let { pageTitle = it }
            }
        }
    }

    // Cleanup
    DisposableEffect(Unit) {
        onDispose {
            viewModel.flushAutoSave()
            viewModel.cancelPendingInjections()
            webView.stopLoading()
            webView.clearCache(true)
            // 定向清理：只清本次导入涉及的主机。原为 removeAllCookies(null)，
            // 那会把「服务」页内置 WebView 与学习平台的登录态一并清掉（CookieManager 是全 App 单例）。
            viewModel.clearVisitedCookies()
            webView.destroy()
        }
    }

    // 系统返回：网页可回退则先回退网页，再逐级退出（防止直接关闭整个导入界面）
    BackHandler {
        if (webView.canGoBack()) {
            webView.goBack()
        } else if (freeUrlActive) {
            viewModel.exitFreeUrlMode()
        } else if (selectedSchool != null) {
            if (viewModel.campusOnly) onBack() else viewModel.clearSchool()
        } else {
            onBack()
        }
    }

    Scaffold(
        floatingActionButton = {
            if (selectedSchool != null || freeUrlActive) {
                FloatingActionButton(
                    onClick = { showCredentialSheet = true; passwordVisible = true },
                    containerColor = if (viewModel.hasSavedCredentials) Color(0xFF4CAF50) else colors.surfaceCard,
                    contentColor = if (viewModel.hasSavedCredentials) Color.White else accentColor(),
                    modifier = Modifier.size(40.dp),
                    shape = RoundedCornerShape(skin.radiusChip)
                ) {
                    Icon(
                        if (viewModel.hasSavedCredentials) Icons.Outlined.Lock else Icons.Outlined.VpnKey,
                        null,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        },
        topBar = {
            TopAppBar(
                title = {
                    if (selectedSchool != null) {
                        Column {
                            Text(selectedSchool!!.name, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                            Text(pageTitle, fontSize = 11.sp, color = colors.textTertiary, maxLines = 1)
                        }
                    } else if (freeUrlActive) {
                        Column {
                            Text("自定义网址导入", fontSize = 16.sp, fontWeight = FontWeight.Bold)
                            Text(pageTitle, fontSize = 11.sp, color = colors.textTertiary, maxLines = 1)
                        }
                    } else {
                        Text("网页教务导入", fontWeight = FontWeight.Bold)
                    }
                },
                navigationIcon = {
                    IconButton(onClick = {
                        if (freeUrlActive) viewModel.exitFreeUrlMode()
                        else if (selectedSchool != null) { if (viewModel.campusOnly) onBack() else viewModel.clearSchool() }
                        else onBack()
                    }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "返回")
                    }
                },
                actions = {
                    if (selectedSchool != null || freeUrlActive) {
                        // URL edit button
                        IconButton(onClick = {
                            urlEditText = customUrl.ifEmpty { selectedSchool?.baseUrl ?: "" }
                            showUrlEditor = true
                        }) {
                            Icon(Icons.Outlined.Edit, "修改网址", tint = accentColor())
                        }
                        IconButton(onClick = { showLogs = !showLogs }) {
                            Icon(Icons.Outlined.Terminal, "日志")
                        }
                        IconButton(onClick = { webView.reload() }) {
                            Icon(Icons.Filled.Refresh, "刷新")
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = colors.surfaceCard)
            )
        },
        bottomBar = {
            if (selectedSchool != null || freeUrlActive) {
                Surface(
                    color = colors.surfaceCard,
                    shadowElevation = 8.dp
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = skin.spaceLg, vertical = 10.dp)
                    ) {
                        // URL hint
                        if (customUrl.isNotEmpty()) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(Icons.Outlined.Link, null, tint = accentColor(), modifier = Modifier.size(12.dp))
                                Spacer(Modifier.width(skin.spaceXs))
                                Text(
                                    "自定义网址: ${customUrl.take(50)}${if (customUrl.length > 50) "..." else ""}",
                                    fontSize = 11.sp, color = accentColor(), maxLines = 1
                                )
                            }
                            Spacer(Modifier.height(skin.spaceXs))
                        }

                        // Cookie status
                        val hasCookies = remember { mutableStateOf(CookieStore.hasCookies()) }
                        LaunchedEffect(currentUrl) {
                            hasCookies.value = CookieStore.hasCookies()
                        }
                        if (hasCookies.value) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.Center
                            ) {
                                Icon(Icons.Outlined.Lock, null, tint = Color(0xFF4CAF50), modifier = Modifier.size(12.dp))
                                Spacer(Modifier.width(skin.spaceXs))
                                Text("Cookie 已保存", fontSize = 11.sp, color = Color(0xFF4CAF50))
                            }
                            Spacer(Modifier.height(skin.spaceXs))
                        }

                        // Status hint
                        if (importedCount > 0) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.Center
                            ) {
                                Icon(Icons.Outlined.CheckCircle, null, tint = Color(0xFF4CAF50), modifier = Modifier.size(14.dp))
                                Spacer(Modifier.width(skin.spaceXs))
                                Text("已导入 $importedCount 门课程", fontSize = 12.sp, color = Color(0xFF4CAF50), fontWeight = FontWeight.Medium)
                            }
                        } else {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.Center
                            ) {
                                Icon(Icons.Outlined.Info, null, tint = colors.textTertiary, modifier = Modifier.size(14.dp))
                                Spacer(Modifier.width(skin.spaceXs))
                                Text("提示：登录后进入课表页面，点击「制作」导入", fontSize = 11.sp, color = colors.textTertiary)
                            }
                        }

                        Spacer(Modifier.height(skin.spaceSm))

                        // 制作按钮 — JS 适配器一键导入
                        Button(
                            onClick = {
                                if (!isImporting) {
                                    viewModel.executeJsAdapter()
                                }
                            },
                            enabled = !isImporting,
                            modifier = Modifier.fillMaxWidth().height(48.dp),
                            shape = RoundedCornerShape(skin.radiusChip),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = accentColor(),
                                contentColor = Color.White
                            )
                        ) {
                            if (isImporting) {
                                CircularProgressIndicator(modifier = Modifier.size(16.dp), color = Color.White, strokeWidth = 2.dp)
                                Spacer(Modifier.width(skin.spaceSm))
                                Text("解析中…", fontSize = 14.sp, fontWeight = FontWeight.Bold)
                            } else {
                                Icon(Icons.Outlined.FileDownload, null, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(skin.spaceSmall))
                                Text("一键导入", fontSize = 14.sp, fontWeight = FontWeight.Bold)
                            }
                        }

                        // OkHttp 直接抓取 — 使用 WebView Cookie
                        if (hasCookies.value) {
                            Spacer(Modifier.height(skin.spaceSmall))
                            OutlinedButton(
                                onClick = {
                                    if (!isImporting) {
                                        viewModel.fetchCourseViaOkHttp(currentUrl)
                                    }
                                },
                                enabled = !isImporting,
                                modifier = Modifier.fillMaxWidth().height(40.dp).invisibleTouchTarget(),
                                shape = RoundedCornerShape(skin.radiusChip),
                                border = BorderStroke(1.dp, accentColor())
                            ) {
                                Icon(Icons.Outlined.Wifi, null, tint = accentColor(), modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(skin.spaceSmall))
                                Text("OkHttp 直接抓取", fontSize = 13.sp, color = accentColor())
                            }
                        }
                    }
                }
            }
        }
    ) { padding ->
        Box(modifier = Modifier.padding(padding).fillMaxSize()) {
            if (selectedSchool == null && !freeUrlActive && !viewModel.campusOnly) {
                // School selection screen + 通用导入 entry
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    item {
                        Text("选择学校", fontSize = 20.sp, fontWeight = FontWeight.ExtraBold, color = colors.textPrimary)
                        Spacer(Modifier.height(skin.spaceXs))
                        Text("登录教务系统后进入课表页面，点击「一键导入」自动识别课程", fontSize = 13.sp, color = colors.textTertiary)
                    }
                    // 添加自己的学校入口 — 手动添加学校名称+网址
                    item {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(skin.radiusChip))
                                .background(accentColor().copy(alpha = 0.12f))
                                .clickable {
                                    pendingBlankSchool = null
                                    freeNameInput = ""
                                    freeJwType = JwSystemType.ZHENGFANG
                                    freeUrlInput = ""
                                    showFreeUrlDialog = true
                                }
                                .padding(horizontal = skin.spaceCompact, vertical = 14.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Outlined.AddLink, null, tint = accentColor(), modifier = Modifier.size(24.dp))
                            Spacer(Modifier.width(skin.spaceMd))
                            Column(Modifier.weight(1f)) {
                                Text("添加自己的学校", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = accentColor())
                                Text("输入学校名称和教务系统地址，自动保存到列表", fontSize = 12.sp, color = colors.textSecondary)
                            }
                            Icon(Icons.Outlined.Add, null, tint = accentColor(), modifier = Modifier.size(20.dp))
                        }
                    }
                    item {
                        OutlinedTextField(
                            value = searchQuery,
                            onValueChange = { viewModel.updateSearch(it) },
                            placeholder = { Text("搜索学校名称或城市") },
                            leadingIcon = { Icon(Icons.Outlined.Search, null, tint = colors.textTertiary) },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(skin.radiusMicro),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = accentColor(),
                                unfocusedBorderColor = colors.borderDivider
                            )
                        )
                    }
                    if (filteredSchools.isEmpty()) {
                        item {
                            Column(
                                modifier = Modifier.fillMaxWidth().padding(vertical = skin.spaceXxl),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Text(
                                    if (searchQuery.isEmpty()) "暂无可用学校" else "未找到「${searchQuery}」",
                                    fontSize = 13.sp,
                                    color = colors.textTertiary
                                )
                                if (searchQuery.isNotEmpty()) {
                                    Spacer(Modifier.height(skin.spaceSmall))
                                    TextButton(onClick = { showSchoolRequest = true }) {
                                        Text("不是你要找的学校？反馈", color = accentColor(), fontSize = 13.sp)
                                    }
                                }
                            }
                        }
                    } else {
                        val favSchools = filteredSchools.filter { it.id in favoriteIds }
                        val otherSchools = filteredSchools.filter { it.id !in favoriteIds }

                        // ── Favorites section ──
                        if (favSchools.isNotEmpty()) {
                            item {
                                Text(
                                    "⭐ 我的收藏",
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = colors.textPrimary,
                                    modifier = Modifier.padding(top = skin.spaceXs, bottom = 4.dp)
                                )
                            }
                            items(favSchools, key = { it.id }) { school ->
                                SchoolRow(
                                    school = school,
                                    isFavorite = true,
                                    onSelect = {
                                        if (school.baseUrl.isBlank()) {
                                            // 内置未收录地址 → 完善学校信息（与教务直连导入一致）
                                            pendingBlankSchool = school
                                            freeNameInput = school.name
                                            freeUrlInput = ""
                                            freeJwType = school.jwType
                                            showFreeUrlDialog = true
                                        } else {
                                            viewModel.selectSchool(school)
                                            viewModel.setCustomUrl("")
                                            val loadUrl = viewModel.getEffectiveUrl() ?: school.baseUrl
                                            webView.loadUrl(loadUrl.trimEnd('/'))
                                        }
                                    },
                                    onToggleFavorite = { viewModel.toggleFavorite(school) }
                                )
                            }
                            item {
                                HorizontalDivider(
                                    color = colors.textTertiary.copy(alpha = 0.3f),
                                    thickness = 1.dp,
                                    modifier = Modifier.padding(vertical = skin.spaceSm)
                                )
                            }
                            item {
                                Text(
                                    "全部学校",
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = colors.textPrimary,
                                    modifier = Modifier.padding(top = skin.spaceXs, bottom = 4.dp)
                                )
                            }
                        }

                        // ── All schools section ──
                        items(otherSchools, key = { it.id }) { school ->
                            SchoolRow(
                                school = school,
                                isFavorite = school.id in favoriteIds,
                                onSelect = {
                                    if (school.baseUrl.isBlank()) {
                                        // 内置未收录地址 → 完善学校信息（与教务直连导入一致）
                                        pendingBlankSchool = school
                                        freeNameInput = school.name
                                        freeUrlInput = ""
                                        freeJwType = school.jwType
                                        showFreeUrlDialog = true
                                    } else {
                                        viewModel.selectSchool(school)
                                        viewModel.setCustomUrl("")
                                        val loadUrl = viewModel.getEffectiveUrl() ?: school.baseUrl
                                        webView.loadUrl(loadUrl.trimEnd('/'))
                                    }
                                },
                                onToggleFavorite = { viewModel.toggleFavorite(school) }
                            )
                        }
                    }
                }
            } else {
                // WebView
                AndroidView(
                    modifier = Modifier.fillMaxSize(),
                    factory = { webView },
                    update = {}
                )

                // Loading progress
                if (loadingProgress < 1f) {
                    LinearProgressIndicator(
                        progress = { loadingProgress },
                        modifier = Modifier.fillMaxWidth().align(Alignment.TopCenter),
                        color = accentColor(),
                        trackColor = Color.Transparent
                    )
                }

                // Import result overlay
                if (importedCount > 0) {
                    Card(
                        modifier = Modifier.align(Alignment.TopCenter).padding(skin.spaceLg),
                        colors = CardDefaults.cardColors(containerColor = if (isDark) Color(0xFF1B3B23) else Color(0xFFE8F5E9)),
                        shape = RoundedCornerShape(skin.radiusChip)
                    ) {
                        Row(
                            modifier = Modifier.padding(skin.spaceLg),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Outlined.CheckCircle, null, tint = Color(0xFF4CAF50))
                            Spacer(Modifier.width(skin.spaceMd))
                            Column {
                                Text("导入成功！", fontWeight = FontWeight.ExtraBold, color = colors.textPrimary)
                                Text("已导入 $importedCount 门课程", fontSize = 13.sp, color = colors.textSecondary)
                            }
                            Spacer(Modifier.weight(1f))
                            TextButton(onClick = onBack) { Text("返回课表", color = accentColor()) }
                        }
                    }
                }

                // Parsing logs overlay
                if (showLogs && parseLogs.isNotEmpty()) {
                    Card(
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .fillMaxWidth()
                            .heightIn(max = 300.dp)
                            .padding(skin.spaceSm),
                        colors = CardDefaults.cardColors(containerColor = Color(0xFF1E1E1E)),
                        shape = RoundedCornerShape(skin.radiusChip)
                    ) {
                        Column(modifier = Modifier.padding(skin.spaceMd)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text("解析日志", color = SurfaceCard, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                                Spacer(Modifier.weight(1f))
                                TextButton(onClick = { showLogs = false }) {
                                    Text("关闭", color = Color(0xFF90CAF9), fontSize = 12.sp)
                                }
                            }
                            Spacer(Modifier.height(skin.spaceSmall))
                            Column(
                                modifier = Modifier.verticalScroll(rememberScrollState())
                            ) {
                                parseLogs.forEach { log ->
                                    Text(
                                        log,
                                        color = Color(0xFFAAAAAA),
                                        fontSize = 11.sp,
                                        lineHeight = 16.sp
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    // ── Credential BottomSheet ──
    if (showCredentialSheet) {
        ModalBottomSheet(
            onDismissRequest = { showCredentialSheet = false },
            containerColor = colors.surfaceCard,
            shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = skin.spaceLg)
                    .padding(bottom = skin.spaceHuge)
            ) {
                Text(
                    "登录凭证",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = colors.textPrimary
                )
                Text(
                    "填写后将加密保存在本机，下次打开自动填充到网页表单",
                    fontSize = 12.sp,
                    color = colors.textTertiary
                )
                Spacer(Modifier.height(skin.spaceLg))
                OutlinedTextField(
                    value = viewModel.username,
                    onValueChange = { viewModel.username = it; viewModel.scheduleAutoSave() },
                    label = { Text("学号/工号") },
                    placeholder = { Text("输入学号或工号") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Ascii),
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(skin.radiusChip),
                    leadingIcon = { Icon(Icons.Outlined.Person, null, tint = colors.textTertiary) },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = accentColor(),
                        unfocusedBorderColor = colors.borderDivider
                    )
                )
                Spacer(Modifier.height(10.dp))
                OutlinedTextField(
                    value = viewModel.password,
                    onValueChange = { viewModel.password = it; viewModel.scheduleAutoSave() },
                    label = { Text("密码") },
                    placeholder = { Text("输入教务系统密码") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    visualTransformation = if (passwordVisible) VisualTransformation.None else PocketPasswordTransformation,
                    trailingIcon = {
                        IconButton(onClick = { passwordVisible = !passwordVisible }) {
                            Icon(
                                if (passwordVisible) Icons.Outlined.VisibilityOff else Icons.Outlined.Visibility,
                                if (passwordVisible) "隐藏密码" else "显示密码",
                                tint = colors.textTertiary
                            )
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(skin.radiusChip),
                    leadingIcon = { Icon(Icons.Outlined.Lock, null, tint = colors.textTertiary) },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = accentColor(),
                        unfocusedBorderColor = colors.borderDivider
                    )
                )
                Spacer(Modifier.height(skin.spaceSm))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Checkbox(
                        checked = viewModel.isRememberChecked,
                        onCheckedChange = {
                            viewModel.isRememberChecked = it
                            if (!it) viewModel.clearSavedCredentials()
                        },
                        colors = CheckboxDefaults.colors(
                            checkedColor = accentColor(),
                            uncheckedColor = colors.textTertiary
                        )
                    )
                    Text(
                        "记住账号密码",
                        fontSize = 13.sp,
                        color = colors.textSecondary
                    )
                }
                Spacer(Modifier.height(skin.spaceMd))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Button(
                        onClick = {
                            viewModel.saveCredentials()
                            viewModel.injectCredentialsToWebView()
                            showCredentialSheet = false
                            Toast.makeText(context, "已保存并填充到网页", Toast.LENGTH_SHORT).show()
                        },
                        modifier = Modifier.weight(1f).height(46.dp),
                        shape = RoundedCornerShape(skin.radiusChip),
                        colors = ButtonDefaults.buttonColors(containerColor = accentColor())
                    ) {
                        Icon(Icons.Outlined.Save, null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(skin.spaceSmall))
                        Text("填充到网页", fontWeight = FontWeight.Bold)
                    }
                    if (viewModel.hasSavedCredentials) {
                        OutlinedButton(
                            onClick = {
                                viewModel.clearSavedCredentials()
                                showCredentialSheet = false
                                Toast.makeText(context, "已清除保存的账号", Toast.LENGTH_SHORT).show()
                            },
                            modifier = Modifier.weight(1f).height(46.dp),
                            shape = RoundedCornerShape(skin.radiusChip),
                            border = BorderStroke(1.dp, colors.danger)
                        ) {
                            Icon(Icons.Outlined.Delete, null, tint = colors.danger, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(skin.spaceXs))
                            Text("清除", color = colors.danger, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }

    // 申请收录学校弹窗（搜索无结果时：预填关键词，走邮件申请）
    if (showSchoolRequest) {
        SchoolRequestDialog(
            context = context,
            initialName = searchQuery,
            onDismiss = { showSchoolRequest = false }
        )
    }

    // ── Add Custom School Dialog (添加自己的学校) ──
    if (showFreeUrlDialog) {
        var showJwTypePicker by remember { mutableStateOf(false) }

        AlertDialog(
            onDismissRequest = {
                showFreeUrlDialog = false
                pendingBlankSchool = null
            },
            title = {
                Text(
                    if (pendingBlankSchool != null) "完善学校信息" else "添加自己的学校",
                    fontWeight = FontWeight.ExtraBold
                )
            },
            text = {
                Column {
                    Text(
                        if (pendingBlankSchool != null)
                            "「${pendingBlankSchool?.name}」暂未收录教务地址，填写后即可导入。"
                        else
                            "输入你的学校名称和教务系统网址，\n添加后将自动保存到学校列表。",
                        fontSize = 12.sp,
                        color = colors.textSecondary,
                        lineHeight = 18.sp
                    )
                    Spacer(Modifier.height(skin.spaceMd))
                    OutlinedTextField(
                        value = freeNameInput,
                        onValueChange = { freeNameInput = it },
                        placeholder = { Text("例如：北京大学", color = colors.textTertiary) },
                        label = { Text("学校名称") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = accentColor(),
                            cursorColor = accentColor()
                        )
                    )
                    Spacer(Modifier.height(skin.spaceSm))
                    OutlinedTextField(
                        value = freeUrlInput,
                        onValueChange = { freeUrlInput = it },
                        placeholder = { Text("https://jiaowu.example.edu.cn", color = colors.textTertiary) },
                        label = { Text("教务系统网址") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = accentColor(),
                            cursorColor = accentColor()
                        )
                    )
                    Spacer(Modifier.height(skin.spaceSm))
                    // 教务类型选择
                    Text("教务系统类型", fontSize = 13.sp, fontWeight = FontWeight.Medium, color = colors.textPrimary)
                    Spacer(Modifier.height(skin.spaceXs))
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(skin.radiusMicro))
                            .background(accentSoftColor().copy(alpha = 0.3f))
                            .clickable { showJwTypePicker = true }
                            .padding(horizontal = skin.spaceMd, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            freeJwType.label,
                            fontSize = 14.sp,
                            color = accentColor(),
                            fontWeight = FontWeight.Medium,
                            modifier = Modifier.weight(1f)
                        )
                        Text(
                            freeJwType.description,
                            fontSize = 11.sp,
                            color = colors.textTertiary,
                            modifier = Modifier.weight(1f)
                        )
                        Icon(Icons.Outlined.ChevronRight, null, tint = colors.textTertiary, modifier = Modifier.size(18.dp))
                    }

                    // URL history
                    if (urlHistory.isNotEmpty()) {
                        Spacer(Modifier.height(skin.spaceMd))
                        Text("最近使用", fontSize = 12.sp, fontWeight = FontWeight.Medium, color = colors.textSecondary)
                        Spacer(Modifier.height(skin.spaceSmall))
                        Column(
                            modifier = Modifier.heightIn(max = 120.dp).verticalScroll(rememberScrollState())
                        ) {
                            urlHistory.take(4).forEach { url ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(skin.radiusMicro))
                                        .clickable {
                                            freeUrlInput = url
                                            // Try to extract school name from URL
                                            val host = try {
                                                java.net.URL(url).host.removePrefix("jw").removePrefix("jiaowu").removePrefix("www.")
                                                    .split(".").firstOrNull()?.replaceFirstChar { it.uppercase() } ?: ""
                                            } catch (_: Exception) { "" }
                                            if (freeNameInput.isBlank() && host.isNotBlank()) {
                                                freeNameInput = host
                                            }
                                        }
                                        .padding(vertical = skin.spaceSmall, horizontal = 4.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(Icons.Outlined.History, null, tint = colors.textTertiary, modifier = Modifier.size(14.dp))
                                    Spacer(Modifier.width(skin.spaceSm))
                                    Text(
                                        url.take(40) + if (url.length > 40) "..." else "",
                                        fontSize = 12.sp,
                                        color = accentColor(),
                                        maxLines = 1,
                                        modifier = Modifier.weight(1f)
                                    )
                                    IconButton(
                                        onClick = { viewModel.removeUrlFromHistory(url) },
                                        modifier = Modifier.size(24.dp).invisibleTouchTarget()
                                    ) {
                                        Icon(Icons.Outlined.Close, "删除", tint = colors.textTertiary, modifier = Modifier.size(14.dp))
                                    }
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        var url = freeUrlInput.trim()
                        val name = freeNameInput.trim()
                        if (url.isNotEmpty() && name.isNotEmpty()) {
                            if (!url.startsWith("http://") && !url.startsWith("https://")) {
                                url = "https://$url"
                            }
                            // Save as custom school
                            viewModel.addCustomSchool(name, url, freeJwType)
                            viewModel.enterFreeUrlMode(url)
                            webView.loadUrl(url)
                            pendingBlankSchool = null
                            showFreeUrlDialog = false
                        }
                    },
                    enabled = freeUrlInput.isNotBlank() && freeNameInput.isNotBlank(),
                    colors = ButtonDefaults.buttonColors(containerColor = accentColor())
                ) {
                    Icon(Icons.Outlined.Add, null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(skin.spaceXs))
                    Text(if (pendingBlankSchool != null) "保存并打开" else "添加学校")
                }
            },
            dismissButton = {
                Row {
                    // Quick URL-only mode: skip name, just open the URL
                    TextButton(onClick = {
                        var url = freeUrlInput.trim()
                        if (url.isNotEmpty()) {
                            if (!url.startsWith("http://") && !url.startsWith("https://")) {
                                url = "https://$url"
                            }
                            viewModel.enterFreeUrlMode(url)
                            webView.loadUrl(url)
                            showFreeUrlDialog = false
                        }
                    }) {
                        Text("仅打开", color = colors.textTertiary, fontSize = 13.sp)
                    }
                    TextButton(onClick = {
                        pendingBlankSchool = null
                        showFreeUrlDialog = false
                    }) {
                        Text("取消")
                    }
                }
            }
        )

        // ── 教务类型选择器 ──
        if (showJwTypePicker) {
            AlertDialog(
                onDismissRequest = { showJwTypePicker = false },
                title = { Text("选择教务系统类型", fontWeight = FontWeight.ExtraBold) },
                text = {
                    Column {
                        JwSystemType.values().forEach { type ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(skin.radiusMicro))
                                    .background(
                                        if (freeJwType == type) accentSoftColor().copy(alpha = 0.5f)
                                        else Color.Transparent
                                    )
                                    .clickable { freeJwType = type; showJwTypePicker = false }
                                    .padding(vertical = skin.spaceMd, horizontal = 8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                RadioButton(
                                    selected = freeJwType == type,
                                    onClick = { freeJwType = type; showJwTypePicker = false },
                                    colors = RadioButtonDefaults.colors(selectedColor = accentColor())
                                )
                                Spacer(Modifier.width(skin.spaceSm))
                                Column {
                                    Text(type.label, fontSize = 15.sp, fontWeight = FontWeight.Medium, color = colors.textPrimary)
                                    Text(type.description, fontSize = 12.sp, color = colors.textTertiary)
                                }
                            }
                        }
                    }
                },
                confirmButton = {},
                dismissButton = {
                    TextButton(onClick = { showJwTypePicker = false }) { Text("取消") }
                }
            )
        }
    }

    // ── URL Edit Dialog ──
    if (showUrlEditor) {
        AlertDialog(
            onDismissRequest = { showUrlEditor = false },
            title = {
                Text("修改教务网址", fontWeight = FontWeight.ExtraBold)
            },
            text = {
                Column {
                    Text(
                        "如需使用自定义教务地址（如IP直接访问、非标端口等），请在下方输入完整网址。\n\n当前地址：${customUrl.ifEmpty { selectedSchool?.baseUrl ?: "" }}",
                        fontSize = 12.sp,
                        color = colors.textSecondary,
                        lineHeight = 18.sp
                    )
                    Spacer(Modifier.height(skin.spaceSm))
                    OutlinedTextField(
                        value = urlEditText,
                        onValueChange = { urlEditText = it },
                        placeholder = { Text("https://jiaowu.example.edu.cn", color = colors.textTertiary) },
                        label = { Text("教务系统网址") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = accentColor(),
                            cursorColor = accentColor()
                        )
                    )
                    Spacer(Modifier.height(skin.spaceXs))
                    Text(
                        "提示：修改后请点击「应用」，将重新加载页面。\n支持 http/https，可带端口号（如 :8080）",
                        fontSize = 11.sp,
                        color = colors.textTertiary,
                        lineHeight = 14.sp
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val url = urlEditText.trim()
                        if (url.isNotEmpty()) {
                            viewModel.setCustomUrl(url)
                            webView.loadUrl(url)
                        }
                        showUrlEditor = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = accentColor())
                ) {
                    Text("应用")
                }
            },
            dismissButton = {
                Row {
                    TextButton(onClick = {
                        // Reset to school default or clear in free mode
                        viewModel.setCustomUrl("")
                        val resetUrl = selectedSchool?.baseUrl?.trimEnd('/') ?: ""
                        if (resetUrl.isNotEmpty()) webView.loadUrl(resetUrl)
                        showUrlEditor = false
                    }) {
                        Text("恢复默认", color = colors.danger)
                    }
                    TextButton(onClick = { showUrlEditor = false }) {
                        Text("取消")
                    }
                }
            }
        )
    }
}


