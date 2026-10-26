package com.pocket.xiaoli.ui.profile

import android.content.Intent
import android.net.Uri
import android.annotation.SuppressLint
import android.webkit.CookieManager
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.pocket.xiaoli.data.learning.LearningPlatform
import com.pocket.xiaoli.ui.theme.LocalPocketXiaoliColors

@SuppressLint("SetJavaScriptEnabled")
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LearningWebLoginScreen(
    platform: LearningPlatform,
    onSaveCookie: (String) -> Unit,
    onClose: () -> Unit
) {
    val colors = LocalPocketXiaoliColors.current
    var webView by remember { mutableStateOf<WebView?>(null) }
    var loading by remember { mutableStateOf(true) }
    var pageError by remember { mutableStateOf<String?>(null) }
    var currentUrl by remember { mutableStateOf(platform.loginUrl) }
    val context = androidx.compose.ui.platform.LocalContext.current

    BackHandler {
        val view = webView
        if (view != null && view.canGoBack()) view.goBack() else onClose()
    }

    Scaffold(
        containerColor = colors.surfaceBase,
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("${platform.label} 登录", fontSize = 16.sp)
                        Text(
                            text = pageError ?: if (loading) "正在加载……" else currentUrl,
                            fontSize = 9.sp,
                            maxLines = 1
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onClose) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
                actions = {
                    TextButton(
                        onClick = {
                            try {
                                val browserIntent = Intent(Intent.ACTION_VIEW, Uri.parse(platform.loginUrl))
                                browserIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                context.startActivity(browserIntent)
                            } catch (_: Exception) {
                            }
                        }
                    ) {
                        Text("浏览器打开", fontSize = 12.sp)
                    }
                    TextButton(
                        onClick = {
                            val manager = CookieManager.getInstance()
                            manager.flush()
                            val cookie = collectPlatformCookie(platform)
                            onSaveCookie(cookie)
                        }
                    ) {
                        // 右上角这个动作现在是这条流程里唯一"必须自己按一下"的地方
                        // （原先那层面板里写着"登录成功后点右上角保存"）—— 用主色把权重给出来。
                        Text("保存登录状态", color = colors.accentMain, fontWeight = FontWeight.Medium)
                    }
                }
            )
        }
    ) { padding ->
        AndroidView(
            factory = { context ->
                WebView(context).apply {
                    settings.javaScriptEnabled = true
                    settings.domStorageEnabled = true
                    settings.databaseEnabled = true
                    settings.loadWithOverviewMode = true
                    settings.useWideViewPort = true
                    CookieManager.getInstance().setAcceptCookie(true)
                    CookieManager.getInstance().setAcceptThirdPartyCookies(this, true)
                    webViewClient = loginWebViewClient(
                        onStart = { url -> loading = true; pageError = null; currentUrl = url },
                        onFinish = { url -> loading = false; currentUrl = url },
                        onError = { msg -> loading = false; pageError = msg }
                    )
                    loadUrl(platform.loginUrl)
                    webView = this
                }
            },
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        )
    }
}
/** 合并学习通多个子域的 Cookie：host-only Cookie 不会跨子域返回，只取 i.chaoxing.com 会漏 */
private fun collectPlatformCookie(platform: LearningPlatform): String {
    val manager = CookieManager.getInstance()
    manager.flush()
    val merged = LinkedHashMap<String, String>()
    platform.cookieUrls.forEach { url ->
        manager.getCookie(url).orEmpty().split(';').forEach { part ->
            val item = part.trim()
            val eq = item.indexOf('=')
            if (eq > 0) merged[item.substring(0, eq).trim()] = item.substring(eq + 1).trim()
        }
    }
    return merged.entries.joinToString("; ") { it.key + "=" + it.value }
}

/** 登录 WebView 回调：把加载状态与失败原因回传界面，避免白屏时看不到任何信息 */
private fun loginWebViewClient(
    onStart: (String) -> Unit,
    onFinish: (String) -> Unit,
    onError: (String) -> Unit
) : WebViewClient = object : WebViewClient() {
    override fun onPageStarted(view: WebView?, url: String?, favicon: android.graphics.Bitmap?) {
        onStart(url.orEmpty())
    }

    override fun onPageFinished(view: WebView?, url: String?) {
        onFinish(url.orEmpty())
    }

    override fun onReceivedError(
        view: WebView?,
        request: android.webkit.WebResourceRequest,
        error: android.webkit.WebResourceError
    ) {
        if (request.isForMainFrame) {
            onError("加载失败：" + error.description + "（" + request.url + "）")
        }
    }
}