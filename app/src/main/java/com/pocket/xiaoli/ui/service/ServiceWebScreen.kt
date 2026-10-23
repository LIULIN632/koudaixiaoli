package com.pocket.xiaoli.ui.service

import android.annotation.SuppressLint
import android.content.Intent
import android.net.Uri
import android.webkit.CookieManager
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.compose.BackHandler
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.pocket.xiaoli.network.CookieStore
import com.pocket.xiaoli.PocketXiaoliApplication
import com.pocket.xiaoli.data.service.ServiceAutoLoginMode
import com.pocket.xiaoli.data.electricity.ElectricityRechargeCapability
import com.pocket.xiaoli.ui.theme.LocalPocketXiaoliColors
import com.pocket.xiaoli.util.ServiceUrlClassifier
import com.pocket.xiaoli.ui.theme.LocalSkin
import com.pocket.xiaoli.ui.theme.invisibleTouchTarget

/**
 * 校园服务内置网页。
 *
 * 设计对齐「聚在工大」的 startWebUrlInner：
 *  - 在 App 内打开，保留返回栈（BackHandler 优先走 WebView 历史）
 *  - 支持注入 cookie 复用登录态（可选）
 *  - 加载失败时给出明确提示，并提供「用浏览器打开」兜底（避免白屏）
 *
 * 另加：烟理多数服务**都需要认证**，首次打开常先落在登录页。
 * 检测到登录页时给一句提示，避免用户面对登录框不知所以。
 */
@SuppressLint("SetJavaScriptEnabled")
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ServiceWebScreen(
    url: String,
    title: String,
    onBack: () -> Unit,
    cookie: String? = null,
) {
    val skin = LocalSkin.current
    val colors = LocalPocketXiaoliColors.current
    val context = LocalContext.current

    // ── 服务自动登录 ──
    //
    // 校园服务（校园卡 / 电费 / 校园网 / 洗澡 / 热水 / 快递 / 报修）都走统一身份认证，
    // 每次进去都要重登一遍 —— 而账号其实早就绑在本机了（「我的 → 绑定信息」，加密存储）。
    //
    // ⚠️ 三条硬约束，少一条都会变成"帮倒忙"：
    // 1. **识别不到就什么都不做**。选择器是通用匹配（CAS 常见写法），对不上时绝不乱填 ——
    //    把学号填进"查询框"比不填更糟。
    // 2. **已有内容不覆盖**（`el.value` 非空就跳过），不夺走用户已经输入的东西。
    // 3. 只在用户开了开关时执行；关闭时这段代码整条不跑。
    //
    // 另外：把用户数据嵌进 JS 字符串字面量前必须转义 —— 一个带引号的密码
    // 就能把整段脚本弄坏，而那种失效是**静默**的，最难查。
    fun escapeForJs(value: String): String =
        value.replace("\\", "\\\\").replace("'", "\\'").replace("\n", " ").replace("\r", "")

    /**
     * 缴费页的「立即缴费」按钮能不能用。
     *
     * 烟大南校后勤缴费页的内联 JS 有个**服务端渲染决定的**递归分支：
     * 渲染成 `if("" == "1")` 时，点击会自我调用 → 无限递归 → 页面毫无反应。
     * 实测同一个账户在不同时刻两种渲染都出现过，且与 UA / Cookie / Referer 无关；
     * 用户确认「微信里是正常的」。**这个缺陷我们改不了**（往金融页注入脚本风险更高），
     * 只能识别出来并给出能走通的替代路径。
     *
     * 声明在这里（而不是下面状态区），是因为 [detectRechargeCapability] 要用到它。
     * 判定逻辑与全部实测证据见 [ElectricityRechargeCapability]。
     */
    var rechargeBroken by remember { mutableStateOf(false) }
    var rechargeNoticeDismissed by remember { mutableStateOf(false) }

    /**
     * 检测当前页是不是**被服务端渲染坏了的缴费页**（「立即缴费」点了没反应）。
     *
     * 只在页面 URL 像缴费页时才做，其它页面完全不碰 —— 这条探测要读整页 HTML，
     * 而 `/pms/.../payItemList` 那种页面有 **582 KB**，没必要给每个服务页都读一遍。
     *
     * 判定与全部实测证据见 [ElectricityRechargeCapability]。
     */
    fun detectRechargeCapability(view: android.webkit.WebView?, pageUrl: String?) {
        val u = pageUrl.orEmpty()
        if (!u.contains("payItemIndex")) return
        view ?: return
        view.evaluateJavascript(
            "(function(){try{return document.documentElement.outerHTML}catch(e){return ''}})();"
        ) { html ->
            // evaluateJavascript 返回的是 JSON 字符串字面量（带引号与转义），需要反转义。
            val decoded = runCatching {
                com.google.gson.JsonParser.parseString(html.orEmpty()).asString
            }.getOrNull().orEmpty()
            if (ElectricityRechargeCapability.isRechargeBroken(decoded)) {
                rechargeBroken = true
            }
        }
    }

    fun maybeAutofillLogin(view: android.webkit.WebView?) {
        val container = runCatching {
            (context.applicationContext as PocketXiaoliApplication).appContainer
        }.getOrNull() ?: return
        val mode = container.serviceAutoLoginStore.mode
        if (mode == ServiceAutoLoginMode.OFF) return
        // 统一认证优先用「今日校园」那套账号（我的 → 绑定信息 → 今日校园 里单独存的那份）：
        // 智慧校园与教务的密码经常只改一边，用教务那份去登校园服务会白填一次
        val credential = runCatching {
            container.loadCasCredential()
                ?: container.currentCampusSchool()?.baseUrl?.let { base ->
                    container.loadCredential(base) ?: container.loadCredential("campus_bind")
                }
        }.getOrNull() ?: return
        val studentId = credential.first
        if (studentId.isBlank()) return
        // 只有「自动登录」档才碰密码；「只填学号」档连读都不读出来
        val password =
            if (mode == ServiceAutoLoginMode.AUTO_LOGIN) credential.second else ""

        // 烟理统一认证（`authserver.yitsd.edu.cn`，wisedu 的 CAS）的真实结构 —— 2026-09-28 校外实测：
        //
        //   用户名   <input id="username" name="username">
        //   密码     <input id="password">                 ← 可见的这个**没有 name**
        //   真正提交 <input id="passwordEncrypt" name="password" style="display:none">
        //            <input id="pwdDefaultEncryptSalt" value="...">
        //   登录按钮 <button type="submit" class="auth_login_btn">
        //   验证码   <div id="hidenCaptchaDiv"> 里的 #captchaResponse（平时隐藏，失败后可能显示）
        //
        // ⚠️ 两个必须照它来的点：
        // 1. **不能用 `form.submit()`** —— 它绕过页面自己的 submit 处理器，而密码是页面 JS
        //    在提交时加密写进 `#passwordEncrypt` 的。用 `form.submit()` 等于交一个空密码，
        //    服务端只会回一句"用户名或密码错误"。正确做法是**点那个真实按钮**（或 requestSubmit）。
        // 2. **有可见验证码就不自动提交** —— 验证码只能人来看，替它交一次等于白交一次，
        //    还可能把"账号密码错误"的计数加上去。填完让它自己点。
        //
        // 另外：只在「像登录页」时才动手（URL 特征见 ServiceUrlClassifier，或者页面上真有密码框）。
        // 这条是防"把学号填进门户的搜索框" —— 那种误填比不填更糟。
        val loginLikeUrl = ServiceUrlClassifier.isLoginLikeUrl(view?.url)
        val js = buildString {
            append("(function(){")
            append("function pick(s){try{return document.querySelector(s);}catch(e){return null;}}")
            append("var u=pick('#username')||pick('input[name=username]')||pick('input[id=username]')")
            append("||pick('input[name=userName]')||pick('input[name=loginName]')||pick('input[type=text]');")
            append("if(!u)return;")
            append("var p0=pick('#password')||pick('input[type=password]');")
            append("var isLoginUrl=").append(if (loginLikeUrl) "true" else "false").append(";")
            append("if(!isLoginUrl&&!p0)return;")
            append("var set=function(el,v){if(!el||el.value)return;el.value=v;")
            append("el.dispatchEvent(new Event('input',{bubbles:true}));")
            append("el.dispatchEvent(new Event('change',{bubbles:true}));};")
            append("set(u,'").append(escapeForJs(studentId)).append("');")
            if (password.isNotBlank()) {
                append("var p=pick('#password')||pick('input[type=password]')||pick('input[name=password]');")
                append("set(p,'").append(escapeForJs(password)).append("');")
                append("var cap=pick('#captchaResponse')||pick('input[name=captchaResponse]');")
                // offsetParent 为 null = 元素不可见（hidenCaptchaDiv 平时就是 display:none）
                append("if(cap&&cap.offsetParent)return;")
                append("var btn=pick('button.auth_login_btn')||pick('#loginButton')")
                append("||pick('button[type=submit]')||pick('input[type=submit]');")
                append("var f=u.form||pick('form');")
                // 点按钮 > requestSubmit（会触发 submit 处理器）> submit（最后手段）
                append("var go=function(){try{if(btn){btn.click();}")
                append("else if(f&&f.requestSubmit){f.requestSubmit();}")
                append("else if(f){f.submit();}}catch(e){}};")
                append("setTimeout(go,150);")
            }
            append("})();")
        }
        view?.evaluateJavascript(js, null)
    }
    var webView by remember { mutableStateOf<WebView?>(null) }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    var currentUrl by remember { mutableStateOf(url) }
    var loginHintDismissed by remember { mutableStateOf(false) }
    val initialServiceCookie = remember(url) { CookieStore.getServiceCookies(url) }
    val showLoginHint = !loginHintDismissed && ServiceUrlClassifier.isLoginLikeUrl(currentUrl)



    DisposableEffect(webView) {
        onDispose {
            persistServiceCookies(url, webView?.url)
        }
    }

    fun openExternal(target: String) {
        val ok = runCatching {
            context.startActivity(
                Intent(Intent.ACTION_VIEW, Uri.parse(target))
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
        }.isSuccess
        if (!ok) error = "未找到可用浏览器"
    }

    BackHandler {
        val wv = webView
        if (wv != null && wv.canGoBack()) wv.goBack() else onBack()
    }

    Scaffold(
        containerColor = colors.surfaceCard,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = title.ifBlank { url },
                        fontSize = 15.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
                actions = {
                    IconButton(onClick = { webView?.reload() }) {
                        Icon(Icons.Default.Refresh, contentDescription = "刷新")
                    }
                    IconButton(onClick = { openExternal(url) }) {
                        Icon(Icons.AutoMirrored.Filled.OpenInNew, contentDescription = "浏览器打开")
                    }
                },
            )
        },
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            AndroidView(
                modifier = Modifier.fillMaxSize(),
                factory = { ctx ->
                    WebView(ctx).apply {
                        settings.javaScriptEnabled = true
                        settings.domStorageEnabled = true
                        settings.databaseEnabled = true
                        settings.loadWithOverviewMode = true
                        settings.useWideViewPort = true
                        val cookieManager = CookieManager.getInstance()
                        cookieManager.setAcceptCookie(true)
                        cookieManager.setAcceptThirdPartyCookies(this, true)
                        // 优先恢复上次落盘的登录态；已有同名 cookie 时以 WebView 里的新值为准。
                        restoreServiceCookies(url, initialServiceCookie)
                        if (!cookie.isNullOrBlank()) {
                            restoreServiceCookies(url, cookie)
                        }
                        cookieManager.flush()

                        webViewClient = object : WebViewClient() {
                            override fun onPageStarted(
                                view: WebView?,
                                url: String?,
                                favicon: android.graphics.Bitmap?,
                            ) {
                                loading = true
                                error = null
                                if (!url.isNullOrBlank()) currentUrl = url
                            }

                            override fun onPageFinished(view: WebView?, url: String?) {
                                loading = false
                                if (!url.isNullOrBlank()) currentUrl = url
                                val finishedUrl = view?.url ?: url
                                persistServiceCookies(url, finishedUrl)
                                // 服务自动登录：登录页出现时按开关填账号（识别不到就什么都不做）
                                maybeAutofillLogin(view)
                                // 缴费页的按钮可能被服务端渲染坏（见 rechargeBroken 的注释）
                                detectRechargeCapability(view, finishedUrl)
                                // 有些登录页是异步写 cookie，页面 finished 后再补抓一次。
                                view?.postDelayed({
                                    persistServiceCookies(url, finishedUrl)
                                }, 1000L)
                            }

                            override fun shouldOverrideUrlLoading(
                                view: WebView?,
                                request: WebResourceRequest?,
                            ): Boolean {
                                val target = request?.url?.toString().orEmpty()
                                if (target.startsWith("http")) {
                                    // 兼容统一认证 / CAS 跳到其它域名：如果这个 host
                                    // 之前存过登录态，在它发请求前补回 Cookie。
                                    val saved = CookieStore.getServiceCookies(target)
                                    if (!saved.isNullOrBlank()) {
                                        restoreServiceCookies(target, saved)
                                    }
                                }
                                return false
                            }

                            override fun onReceivedError(
                                view: WebView?,
                                request: WebResourceRequest,
                                err: WebResourceError,
                            ) {
                                if (request.isForMainFrame) {
                                    loading = false
                                    error = "加载失败：" + err.description +
                                        "\n（该入口可能需要在校园网或登录后访问）"
                                }
                            }
                        }
                        loadUrl(url)
                        webView = this
                    }
                },
            )

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.TopCenter)
            ) {
                if (loading) {
                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                }
                // 缴费页按钮被服务端渲染坏时，先说清"为什么点了没反应"，
                // 再给能走通的路 —— 否则用户会以为是自己手机或 App 的问题。
                // 判定依据与实测证据见 ElectricityRechargeCapability。
                if (rechargeBroken && !rechargeNoticeDismissed) {
                    Surface(color = colors.surfaceHighlight) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = skin.spaceMd, vertical = skin.spaceSm),
                        ) {
                            Text(
                                text = "这一页的「立即缴费」可能点不动",
                                color = colors.textPrimary,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Medium,
                            )
                            Spacer(Modifier.height(skin.spaceMicro))
                            Text(
                                // 中文排版不在字与字之间加空格，而 lint 的 TextConcatSpace
                                // 会把跨行的字符串拼接当成英文句子来检查，于是误报"缺空格"。
                                // 这里把拼接点挪到句号之后、并让每段自成一句，既保持文案不变，
                                // 也不靠 suppress 压掉规则。
                                text = "这一页的「立即缴费」在微信外点不动：学校缴费系统只在微信内提供支付入口，在其它应用里打开会退化成「增绑新用户」页。" +
                                    "请回到「我的 → 电费」，用「去微信缴费」。" +
                                    "它会把链接复制好并打开微信，粘贴到任意聊天窗口即可。",
                                color = colors.textSecondary,
                                fontSize = 12.sp,
                                lineHeight = 17.sp,
                            )
                            Spacer(Modifier.height(skin.spaceXs))
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                TextButton(onClick = {
                                    val cm = context.getSystemService(android.content.Context.CLIPBOARD_SERVICE)
                                        as android.content.ClipboardManager
                                    cm.setPrimaryClip(
                                        android.content.ClipData.newPlainText("缴费链接", currentUrl)
                                    )
                                    android.widget.Toast.makeText(
                                        context, "链接已复制，粘贴到微信里打开", android.widget.Toast.LENGTH_LONG
                                    ).show()
                                }) { Text("复制链接", fontSize = 13.sp) }

                                TextButton(onClick = { openExternal(currentUrl) }) {
                                    Text("用浏览器打开", fontSize = 13.sp)
                                }

                                Spacer(Modifier.weight(1f))
                                TextButton(onClick = { rechargeNoticeDismissed = true }) {
                                    Text("知道了", fontSize = 13.sp)
                                }
                            }
                        }
                    }
                }
                if (showLoginHint) {
                    Surface(color = colors.surfaceHighlight) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(start = skin.spaceMd, end = 2.dp, top = 4.dp, bottom = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                text = "该服务需要登录。首次登录后本机自动记住，之后免登。",
                                fontSize = 12.sp,
                                color = colors.textPrimary,
                                modifier = Modifier.weight(1f),
                            )
                            IconButton(
                                onClick = { loginHintDismissed = true },
                                modifier = Modifier.size(28.dp).invisibleTouchTarget(),
                            ) {
                                Icon(
                                    Icons.Default.Close,
                                    contentDescription = "关闭提示",
                                    tint = colors.textTertiary,
                                    modifier = Modifier.size(16.dp),
                                )
                            }
                        }
                    }
                }
            }

            error?.let { message ->
                Column(
                    modifier = Modifier
                        .align(Alignment.Center)
                        .padding(skin.spaceXxl),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    Text(
                        text = message,
                        color = colors.textPrimary,
                        fontSize = 13.sp,
                        textAlign = TextAlign.Center,
                    )
                    Spacer(Modifier.height(skin.spaceMd))
                    Button(onClick = { openExternal(url) }) { Text("用浏览器打开") }
                    Spacer(Modifier.height(skin.spaceXs))
                    TextButton(onClick = { webView?.reload() }) { Text("重试") }
                }
            }
        }
    }
}

/** 只补 WebView 当前 cookie 里没有的名字，避免重复或覆盖新会话。 */
private fun restoreServiceCookies(url: String, savedHeader: String?) {
    if (savedHeader.isNullOrBlank() || !url.startsWith("http")) return
    val manager = CookieManager.getInstance()
    val current = parseCookieHeader(manager.getCookie(url))
    val saved = parseCookieHeader(savedHeader)
    saved.forEach { (name, value) ->
        if (!current.containsKey(name)) {
            manager.setCookie(url, name + "=" + value + "; Path=/")
        }
    }
}

/** 把当前 WebView 的 cookie 按 host 落盘，供下次打开服务页时恢复。 */
private fun persistServiceCookies(vararg urls: String?) {
    val manager = CookieManager.getInstance()
    manager.flush()
    val seen = mutableSetOf<String>()
    urls.forEach { target ->
        if (target.isNullOrBlank() || !target.startsWith("http")) return@forEach
        val host = runCatching { Uri.parse(target).host }.getOrNull().orEmpty()
        if (host.isBlank() || !seen.add(host)) return@forEach
        val cookies = manager.getCookie(target)
        if (!cookies.isNullOrBlank()) CookieStore.saveServiceCookies(target, cookies)
    }
}

private fun parseCookieHeader(header: String?): Map<String, String> {
    val map = LinkedHashMap<String, String>()
    header.orEmpty().split(';').forEach { raw ->
        val item = raw.trim()
        val eq = item.indexOf('=')
        if (eq > 0) map[item.substring(0, eq).trim()] = item.substring(eq + 1).trim()
    }
    return map
}
