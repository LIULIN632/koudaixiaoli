package com.pocket.xiaoli.ui.profile

import android.content.Intent
import android.net.Uri
import android.annotation.SuppressLint
import android.webkit.CookieManager
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.compose.BackHandler
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.CloudSync
import androidx.compose.material.icons.outlined.MenuBook
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.pocket.xiaoli.data.learning.LearningPlatform
import com.pocket.xiaoli.ui.theme.AppearanceMode
import com.pocket.xiaoli.ui.theme.LocalAppearanceMode
import com.pocket.xiaoli.ui.theme.LocalPocketXiaoliColors
import com.pocket.xiaoli.ui.theme.LocalSkin

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LearningWebSheet(
    initialPlatform: LearningPlatform,
    chaoxingBound: Boolean,
    zhihuishuBound: Boolean,
    onOpenWebLogin: (LearningPlatform) -> Unit,
    onUnbind: (LearningPlatform) -> Unit,
    onDismiss: () -> Unit
) {
    val skin = LocalSkin.current
    val colors = LocalPocketXiaoliColors.current
    var selectedIndex by remember { mutableIntStateOf(if (initialPlatform == LearningPlatform.CHAOXING) 0 else 1) }
    val platforms = listOf(LearningPlatform.CHAOXING, LearningPlatform.ZHIHUISHU)
    val selected = platforms[selectedIndex]
    val bound = if (selected == LearningPlatform.CHAOXING) chaoxingBound else zhihuishuBound

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
        containerColor = colors.surfaceCard
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = skin.spaceXl, end = 20.dp, bottom = 30.dp),
            verticalArrangement = Arrangement.spacedBy(skin.spaceCompact)
        ) {
            Text(
                text = "学习平台登录",
                color = colors.textPrimary,
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = "登录在 App 内打开官方网页，登录成功后点右上角保存。Cookie 只加密保存在本机，不上传。",
                color = colors.textSecondary,
                fontSize = 12.sp
            )

            TabRow(selectedTabIndex = selectedIndex) {
                platforms.forEachIndexed { index, platform ->
                    Tab(
                        selected = selectedIndex == index,
                        onClick = { selectedIndex = index },
                        text = { Text(platform.label, fontSize = 13.sp) }
                    )
                }
            }

            Text(
                text = if (bound) "当前状态：已登录" else "当前状态：未登录",
                color = if (bound) colors.accentMain else colors.textSecondary,
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium
            )

            Button(
                onClick = { onOpenWebLogin(selected) },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp),
                shape = RoundedCornerShape(skin.radiusCard),
                colors = ButtonDefaults.buttonColors(containerColor = colors.accentMain)
            ) {
                Text(if (bound) "重新登录" else "打开网页登录")
            }

            if (bound) {
                TextButton(
                    onClick = { onUnbind(selected) },
                    modifier = Modifier.align(Alignment.CenterHorizontally)
                ) {
                    Text("清除登录状态", color = colors.danger, fontSize = 12.sp)
                }
            }
        }
    }
}

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
                        Text("保存登录状态")
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