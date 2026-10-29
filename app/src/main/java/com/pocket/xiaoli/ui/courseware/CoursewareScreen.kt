package com.pocket.xiaoli.ui.courseware

import android.annotation.SuppressLint
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import android.widget.FrameLayout
import androidx.compose.ui.viewinterop.AndroidView
import com.pocket.xiaoli.data.courseware.Courseware
import com.pocket.xiaoli.data.courseware.CoursewareServer
import com.pocket.xiaoli.ui.theme.LocalPocketXiaoliColors
import com.pocket.xiaoli.ui.theme.PocketTypography
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * 一个内置交互课件的宿主页：起本机回环服务 → WebView 打开 → 离开时把服务关掉。
 *
 * ## 为什么不直接用 `file:///android_asset/…`
 *
 * 课件是 ESM + import map 写的，Chromium 把 `file://` 下的模块脚本按跨源拦掉，
 * 直开只会得到一张白屏。为什么不用 `WebViewAssetLoader` / 打包成单文件 ——
 * 理由与安全边界都写在 [CoursewareServer] 的 KDoc 里，那里是唯一一份。
 *
 * ## 三处容易做错的细节
 *
 * 1. **`loadUrl` 只能出现在 factory 里**：放进 `update` 就是"每次重组都重刷一次页面"，
 *    表现是滑杆一动模型就回到初始状态（`ServiceWebScreen` 的 UA 那段注释就是同一课）。
 * 2. **服务必须活到页面离开**：`DisposableEffect.onDispose` 里 `close()`，
 *    否则要么端口泄漏，要么页面还在请求、服务已经没了。
 * 3. **失败要说清是哪一段**：起服务失败与页面加载失败是两件事，混成一句"打开失败"
 *    等于让用户和我们一起猜。
 */
@OptIn(ExperimentalMaterial3Api::class)
@SuppressLint("SetJavaScriptEnabled")
@Composable
internal fun CoursewareScreen(item: Courseware, onBack: () -> Unit) {
    val context = LocalContext.current
    val colors = LocalPocketXiaoliColors.current

    var server by remember { mutableStateOf<CoursewareServer?>(null) }
    var failure by remember { mutableStateOf<String?>(null) }
    var loading by remember { mutableStateOf(true) }
    // 重试的写法是"换 key 重起一遍"而不是"reload 同一个 WebView"：
    // 服务没起来时根本没有可 reload 的对象，而页面加载失败多半也是服务侧的问题。
    var attempt by remember { mutableIntStateOf(0) }

    LaunchedEffect(item, attempt) {
        loading = true
        failure = null
        server?.close()
        val started = withContext(Dispatchers.IO) {
            runCatching { CoursewareServer(context.assets) }
                .onFailure { failure = "本机课件服务没起来：${it.javaClass.simpleName} ${it.message}" }
                .getOrNull()
        }
        server = started
    }
    DisposableEffect(Unit) {
        onDispose { server?.close() }
    }

    val url = server?.urlFor(item)

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(item.title) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
                actions = {
                    // 课件挂在服务页的「学习」旁边，标一门课的名字比标"Three.js"有用
                    Text(item.course, style = PocketTypography.caption)
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            if (loading && failure == null) {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            }
            val problem = failure
            when {
                problem != null -> Column(
                    modifier = Modifier.fillMaxSize().padding(24.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterVertically),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(
                        problem,
                        color = colors.textSecondary,
                        modifier = Modifier.padding(bottom = 4.dp),
                    )
                    Button(onClick = { attempt++ }) {
                        Icon(Icons.Filled.Refresh, null, modifier = Modifier.padding(end = 6.dp))
                        Text("重试")
                    }
                }

                url != null -> key(url) {
                    // key(url) 不能省：重试会换一个新服务（新端口 → 新 url），
                    // 而 `AndroidView` 的 factory 只在**首次进入组合**时跑一次。
                    // 不加 key 就是"旧 WebView 还指着已经关掉的服务"，表现是重试点了没反应。
                    AndroidView(
                        modifier = Modifier.fillMaxSize(),
                        factory = { ctx ->
                            // WebView 的构造与首帧都在这里：机型上没有 WebView 提供者、
                            // 或内核版本过低时这里会直接抛（`WebViewFactory$Missing`、
                            // `UnsatisfiedLinkError` 都见过）。以前没有这层 catch，
                            // 用户看到的就是「点一下整个 App 没了」，也拿不到是哪一类。
                            runCatching {
                            WebView(ctx).apply {
                                settings.javaScriptEnabled = true
                                // WebGL 要 DOM storage；缓存关掉，与服务的 no-store 对齐
                                settings.domStorageEnabled = true
                                settings.cacheMode = WebSettings.LOAD_NO_CACHE
                                settings.javaScriptCanOpenWindowsAutomatically = false
                                webViewClient = object : WebViewClient() {
                                    override fun onPageFinished(view: WebView?, url: String?) {
                                        loading = false
                                    }

                                    override fun onReceivedError(
                                        view: WebView?,
                                        request: WebResourceRequest?,
                                        error: WebResourceError?,
                                    ) {
                                        // 只认主文档失败：子资源错了一格会让页面自己显示状态
                                        if (request?.isForMainFrame == true) {
                                            failure = "课件没能加载出来：${error?.description}"
                                            loading = false
                                        }
                                    }
                                }
                                loadUrl(url)
                            }
                            }.getOrElse { t ->
                                // 把类名露出来：下次用户点「发送崩溃日志」，这一行会进工单，
                                // 比"课件打不开"有用得多。
                                failure = "课件视图起不来：" + t.javaClass.simpleName +
                                    (t.message?.let { "（$it）" } ?: "")
                                loading = false
                                FrameLayout(ctx)
                            }
                        },
                    )
                }
            }
        }
    }
}
