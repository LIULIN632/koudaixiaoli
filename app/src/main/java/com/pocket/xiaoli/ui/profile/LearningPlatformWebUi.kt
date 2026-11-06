package com.pocket.xiaoli.ui.profile

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.view.ViewGroup
import android.webkit.CookieManager
import android.webkit.RenderProcessGoneDetail
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
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.pocket.xiaoli.data.learning.LearningPlatform
import com.pocket.xiaoli.ui.theme.LocalPocketXiaoliColors

/**
 * 学习平台（学习通 / 智慧树）网页登录页。
 *
 * 2026-11 重写。原版有几处会让用户看到「点一下就闪退」，这里逐条兜住：
 *
 * 1. **系统 WebView 可能根本不可用**（被停用、正在更新、魔改 ROM）。原版把 `WebView(context)`
 *    建在 `AndroidView` 的 factory 里，抛错就崩在组合阶段。现在先探 [WebView.getCurrentWebViewPackage]，
 *    并且把创建包进 `runCatching`：拿不到就画一页可读说明 +「用浏览器打开」。
 * 2. **渲染进程被杀**（低内存、停留过久）会走 `onRenderProcessGone`，原版没接 → App 被系统带走。
 *    现在接住并重建（`rendererGeneration` 变化 → 重新 remember 一个 WebView）。
 * 3. **离开页面不销毁 WebView**：登录页里有视频/大图时内存不回，反复进出后下一次创建可能失败。
 *    现在 `DisposableEffect` 里 stopLoading + removeView + destroy。
 * 4. **登录成功没有反馈**：原版要用户自己判断"登好了没"。现在识别到已进业务域就直接提示并给「保存」。
 *
 * 边界不变：**账号密码只输入到学习通自己的页面里**，本页不注入脚本、不读输入框、不上传任何东西；
 * 能拿到的只有 Cookie，且由用户点「保存」才落盘。
 */
@SuppressLint("SetJavaScriptEnabled")
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LearningWebLoginScreen(
    platform: LearningPlatform,
    onSaveCookie: (String) -> Unit,
    onClose: () -> Unit
) {
    val colors = LocalPocketXiaoliColors.current
    val context = LocalContext.current

    val webViewAvailable = remember {
        runCatching { WebView.getCurrentWebViewPackage() }.getOrNull() != null
    }
    var loading by remember { mutableStateOf(true) }
    var pageError by remember { mutableStateOf<String?>(null) }
    var currentUrl by remember { mutableStateOf(platform.loginUrl) }
    var rendererGeneration by remember { mutableIntStateOf(0) }
    var fatalError by remember { mutableStateOf<String?>(null) }
    var likelyLoggedIn by remember { mutableStateOf(false) }
    var event by remember { mutableStateOf<WebViewEvent?>(null) }

    // 网页事件统一在这里落地，避免在 WebView 回调里直接改 Compose 状态。
    LaunchedEffect(event) {
        val e = event ?: return@LaunchedEffect
        when (e.kind) {
            WebViewEvent.Kind.START -> {
                loading = true
                pageError = null
                currentUrl = e.url
            }
            WebViewEvent.Kind.FINISH -> {
                loading = false
                currentUrl = e.url
                likelyLoggedIn = isLoggedInUrl(platform, e.url)
            }
            WebViewEvent.Kind.ERROR -> {
                loading = false
                pageError = e.message
            }
            WebViewEvent.Kind.RENDERER_GONE -> {
                loading = false
                pageError = "页面被系统回收，正在恢复…"
                rendererGeneration++
            }
        }
        event = null
    }

    val webView: WebView? = remember(rendererGeneration, webViewAvailable) {
        if (!webViewAvailable) null
        else runCatching { buildLoginWebView(context, platform) { event = it } }.getOrNull()
    }

    fun openInBrowser() {
        val ok = runCatching {
            context.startActivity(
                Intent(Intent.ACTION_VIEW, Uri.parse(platform.loginUrl))
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
        }.isSuccess
        if (!ok) pageError = "没找到可用的浏览器"
    }

    fun snapshotCookie(): String = runCatching {
        CookieManager.getInstance().flush()
        collectPlatformCookie(platform)
    }.getOrDefault("")

    BackHandler {
        if (webView != null && webView.canGoBack()) webView.goBack() else onClose()
    }

    androidx.compose.runtime.DisposableEffect(webView) {
        onDispose {
            runCatching {
                webView?.apply {
                    stopLoading()
                    loadUrl("about:blank")
                    (parent as? ViewGroup)?.removeView(this)
                    destroy()
                }
            }
        }
    }

    Scaffold(
        containerColor = colors.surfaceBase,
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("${platform.label} 登录", fontSize = 16.sp, fontWeight = FontWeight.Medium)
                        Text(
                            text = fatalError
                                ?: pageError
                                ?: when {
                                    loading -> "正在加载……"
                                    likelyLoggedIn -> "已登录：点右上角「保存登录状态」"
                                    else -> currentUrl
                                },
                            fontSize = 9.sp,
                            maxLines = 1,
                            color = if (likelyLoggedIn) colors.accentMain else colors.textSecondary,
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onClose) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
                actions = {
                    IconButton(onClick = ::openInBrowser) {
                        Icon(
                            Icons.AutoMirrored.Filled.OpenInNew,
                            contentDescription = "用浏览器打开",
                            tint = colors.textPrimary,
                        )
                    }
                    IconButton(onClick = {
                        pageError = null
                        loading = true
                        webView?.reload()
                    }) {
                        Icon(Icons.Filled.Refresh, contentDescription = "刷新", tint = colors.textPrimary)
                    }
                    TextButton(onClick = { onSaveCookie(snapshotCookie()) }) {
                        Text("保存登录状态", color = colors.accentMain, fontWeight = FontWeight.Medium)
                    }
                },
            )
        },
    ) { padding ->
        if (webView == null) {
            WebViewUnavailablePanel(
                platformLabel = platform.label,
                detail = fatalError
                    ?: "这台设备没有可用的系统 WebView（可能被停用，或正在后台更新）。",
                modifier = Modifier.fillMaxSize().padding(padding),
                onOpenBrowser = ::openInBrowser,
                onRetry = {
                    fatalError = null
                    rendererGeneration++
                },
            )
        } else {
            Column(modifier = Modifier.fillMaxSize().padding(padding)) {
                if (loading) {
                    LinearProgressIndicator(
                        modifier = Modifier.fillMaxWidth().height(2.dp),
                        color = colors.accentMain,
                        trackColor = colors.surfaceHighlight,
                    )
                }
                if (likelyLoggedIn) {
                    LoggedInBanner(
                        platformLabel = platform.label,
                        onSave = { onSaveCookie(snapshotCookie()) },
                    )
                }
                AndroidView(
                    factory = { webView },
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }
    }
}

/** 网页事件：加载中 / 加载完 / 出错 / 渲染进程没了。 */
private data class WebViewEvent(
    val kind: Kind,
    val url: String = "",
    val message: String = "",
) {
    enum class Kind { START, FINISH, ERROR, RENDERER_GONE }
}

/**
 * 建 WebView。创建本身也可能抛（ROM 删了/改坏了 WebView 实现），
 * 所以由调用方 `runCatching` —— 异常冒到组合层就是闪退。
 */
@SuppressLint("SetJavaScriptEnabled")
private fun buildLoginWebView(
    context: Context,
    platform: LearningPlatform,
    onEvent: (WebViewEvent) -> Unit,
): WebView = WebView(context).apply {
    settings.javaScriptEnabled = true
    settings.domStorageEnabled = true
    settings.databaseEnabled = true
    settings.loadWithOverviewMode = true
    settings.useWideViewPort = true
    runCatching {
        val manager = CookieManager.getInstance()
        manager.setAcceptCookie(true)
        manager.setAcceptThirdPartyCookies(this, true)
    }
    webViewClient = loginWebViewClient(onEvent)
    loadUrl(platform.loginUrl)
}

/** 登录 WebView 回调：把加载状态与失败原因回传界面，避免白屏时看不到任何信息 */
private fun loginWebViewClient(onEvent: (WebViewEvent) -> Unit): WebViewClient =
    object : WebViewClient() {
        override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
            onEvent(WebViewEvent(WebViewEvent.Kind.START, url.orEmpty()))
        }

        override fun onPageFinished(view: WebView?, url: String?) {
            onEvent(WebViewEvent(WebViewEvent.Kind.FINISH, url.orEmpty()))
        }

        override fun onReceivedError(
            view: WebView?,
            request: WebResourceRequest,
            error: WebResourceError,
        ) {
            if (request.isForMainFrame) {
                onEvent(
                    WebViewEvent(
                        WebViewEvent.Kind.ERROR,
                        request.url.toString(),
                        "加载失败：" + error.description + "（" + request.url + "）",
                    )
                )
            }
        }

        override fun onRenderProcessGone(view: WebView?, detail: RenderProcessGoneDetail?): Boolean {
            // 返回 true = 这次崩溃我处理了；返回 false 会让系统把 App 一起杀掉（用户看到的就是闪退）。
            runCatching {
                view?.apply {
                    (parent as? ViewGroup)?.removeView(this)
                    destroy()
                }
            }
            onEvent(WebViewEvent(WebViewEvent.Kind.RENDERER_GONE))
            return true
        }
    }

/** 系统 WebView 不可用（或被停用/正在更新）时的一页说明。 */
@Composable
private fun WebViewUnavailablePanel(
    platformLabel: String,
    detail: String,
    modifier: Modifier = Modifier,
    onOpenBrowser: () -> Unit,
    onRetry: () -> Unit,
) {
    val colors = LocalPocketXiaoliColors.current
    Box(modifier = modifier.padding(24.dp), contentAlignment = Alignment.Center) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(
                "打不开内嵌网页",
                fontSize = 17.sp,
                fontWeight = FontWeight.Medium,
                color = colors.textPrimary,
            )
            Text(
                detail,
                fontSize = 13.sp,
                color = colors.textSecondary,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(2.dp))
            Text(
                "可以先在浏览器里登录 $platformLabel，登录后回这里点「保存登录状态」；" +
                    "也可以去系统设置里把「Android System WebView」恢复启用再重试。",
                fontSize = 12.sp,
                color = colors.textSecondary,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(4.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedButton(onClick = onOpenBrowser) { Text("用浏览器打开") }
                OutlinedButton(onClick = onRetry) { Text("重试") }
            }
        }
    }
}

/** 识别到已进入登录后的业务页面时，直接把"该点保存了"推到用户眼前。 */
@Composable
private fun LoggedInBanner(platformLabel: String, onSave: () -> Unit) {
    val colors = LocalPocketXiaoliColors.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            "已进入$platformLabel 首页，登录应该成功了 →",
            fontSize = 12.sp,
            color = colors.accentMain,
            modifier = Modifier.weight(1f),
            maxLines = 2,
        )
        Spacer(Modifier.width(8.dp))
        TextButton(onClick = onSave) {
            Text("保存", fontSize = 12.sp, fontWeight = FontWeight.Medium, color = colors.accentMain)
        }
    }
}

/** 合并学习通多个子域的 Cookie：host-only Cookie 不会跨子域返回，只取 i.chaoxing.com 会漏 */
internal fun collectPlatformCookie(platform: LearningPlatform): String {
    val manager = CookieManager.getInstance()
    manager.flush()
    val merged = LinkedHashMap<String, String>()
    platform.cookieUrls.forEach { url ->
        runCatching { manager.getCookie(url) }.getOrNull().orEmpty().split(';').forEach { part ->
            val item = part.trim()
            val eq = item.indexOf('=')
            if (eq > 0) merged[item.substring(0, eq).trim()] = item.substring(eq + 1).trim()
        }
    }
    return merged.entries.joinToString("; ") { it.key + "=" + it.value }
}

/**
 * 登录成功的判据：还在 `passport`（登录页）就不算，进了业务域才算。
 * 只看域名，不看页面文字 —— 文案随时会变，域名稳定。
 */
internal fun isLoggedInUrl(platform: LearningPlatform, url: String): Boolean {
    val host = hostOf(url) ?: return false
    // 还停在登录域名或登录路径上：不算登录成功（会被踢回来的那种重定向也算没登）
    if (host.startsWith("passport")) return false
    if (hostOf(url, withPath = true).orEmpty().substringAfter(host).startsWith("/login")) return false
    return platform.cookieUrls.any { base ->
        val baseHost = hostOf(base) ?: return@any false
        // 只比「可注册域」（末两段）：学习通的业务页分布在 mooc1 / mooc2-ans / i 等子域，
        // 逐个列子域会漏；而只比完整 host 又会把 onlineweb.zhihuishu.com 判成未登录。
        val registrable = baseHost.split('.').takeLast(2).joinToString(".")
        host == registrable || host.endsWith(".$registrable")
    }
}

/** 从 URL 里取 host（不依赖 android.net.Uri，纯 JVM 单测也能跑）。 */
internal fun hostOf(url: String, withPath: Boolean = false): String? {
    if (url.isBlank()) return null
    val noScheme = url.substringAfter("://", url).lowercase()
    if (noScheme == url.lowercase() && !url.contains("://")) return null
    val host = noScheme.substringBefore('/').substringBefore('?')
    if (host.isBlank()) return null
    if (!withPath) return host
    val path = noScheme.substringAfter(host, "")
    return host + path
}
