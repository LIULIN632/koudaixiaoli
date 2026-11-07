package com.pocket.xiaoli.ui.courseware

import android.annotation.SuppressLint
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Intent
import android.net.Uri
import android.webkit.ConsoleMessage
import android.webkit.WebChromeClient
import android.widget.Toast
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
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
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import com.pocket.xiaoli.data.courseware.CoursewareRef
import com.pocket.xiaoli.data.courseware.CoursewareRepository
import com.pocket.xiaoli.data.courseware.CoursewareServer
import com.pocket.xiaoli.data.courseware.coursewareRoot
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
internal fun CoursewareScreen(item: CoursewareRef, onBack: () -> Unit) {
    val context = LocalContext.current
    val colors = LocalPocketXiaoliColors.current

    var server by remember { mutableStateOf<CoursewareServer?>(null) }
    var failure by remember { mutableStateOf<String?>(null) }
    var loading by remember { mutableStateOf(true) }
    // 页面加载完成 ≠ 课件跑起来了：three.js 起不来时网页一样会 200，但画面是空的。
    // 所以 onPageFinished 之后要再问一次页面「你初始化成功了吗」，失败时给出能复制的诊断。
    var diagnostics by remember { mutableStateOf("") }
    var pageProblem by remember { mutableStateOf<String?>(null) }
    // onConsoleMessage 在 WebView 线程上回调，用线程安全的列表接住
    val consoleTail = remember { java.util.Collections.synchronizedList(mutableListOf<String>()) }
    // 重试的写法是"换 key 重起一遍"而不是"reload 同一个 WebView"：
    // 服务没起来时根本没有可 reload 的对象，而页面加载失败多半也是服务侧的问题。
    var attempt by remember { mutableIntStateOf(0) }

    LaunchedEffect(item, attempt) {
        loading = true
        failure = null
        server?.close()
        CoursewareRepository.init(context)
        // 下载型课件要确认文件真的在，不能只看清单（用户可能清过数据）
        if (item.requiresDownload && !CoursewareRepository.isInstalled(item.id)) {
            loading = false
            failure = "这个课件还没下载完，回服务页点一下重新下载"
            return@LaunchedEffect
        }
        val started = withContext(Dispatchers.IO) {
            runCatching { CoursewareServer(context.assets, coursewareRoot(context)) }
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
            val problem = failure ?: pageProblem
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
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Button(onClick = { attempt++ }) {
                            Icon(Icons.Filled.Refresh, null, modifier = Modifier.padding(end = 6.dp))
                            Text("重试")
                        }
                        // 手机内核老、WebGL 被禁时，系统浏览器往往还能开起来——
                        // 与其卡在「打不开」，不如把地址交出去。
                        val external = url
                        if (external != null) {
                            OutlinedButton(
                                onClick = {
                                    runCatching {
                                        context.startActivity(
                                            Intent(Intent.ACTION_VIEW, Uri.parse(external))
                                        )
                                    }.onFailure { failure = "没有能打开网页的应用：${it.javaClass.simpleName}" }
                                },
                            ) { Text("用浏览器打开") }
                        }
                    }
                    if (diagnostics.isNotBlank()) {
                        TextButton(
                            onClick = {
                                val clip = context.getSystemService(ClipboardManager::class.java)
                                clip?.setPrimaryClip(
                                    ClipData.newPlainText("课件诊断", diagnostics)
                                )
                                Toast.makeText(context, "诊断已复制，发给作者就行", Toast.LENGTH_SHORT).show()
                            },
                        ) { Text("复制诊断信息") }
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
                                // 这两行是「手机上看不对」的关键：WebView 默认
                                // `useWideViewPort = false`，会**忽略**页面的
                                // `<meta name="viewport">`，按 980px 桌面宽度排版再整体缩小，
                                // 表现就是字小、面板被压扁、滑杆挤成一条。
                                // 打开它、关掉 overview，页面才真的按 device-width 排。
                                settings.useWideViewPort = true
                                settings.loadWithOverviewMode = false
                                // 双指缩放交给课件自己（OrbitControls 做相机缩放），
                                // 页面级缩放会把画面拽花。
                                settings.setSupportZoom(false)
                                settings.builtInZoomControls = false
                                settings.displayZoomControls = false
                                // 深色底，避免首帧白闪
                                setBackgroundColor(android.graphics.Color.parseColor("#0B0F14"))
                                webViewClient = object : WebViewClient() {
                                    override fun onPageFinished(view: WebView?, url: String?) {
                                        loading = false
                                        // 给页面 2.6 秒：它自己要先判定 WebGL 是否可用，
                                        // 并把「黑屏」换成静态图 + 说明（app-mobile.js）。
                                        // 我们晚一步再看，避免用一层系统提示盖掉页面更好的兜底。
                                        view?.postDelayed({
                                            view.evaluateJavascript(PROBE_JS) { raw ->
                                                val probed = raw?.trim('"')?.replace("\\u003c", "<").orEmpty()
                                                diagnostics = buildString {
                                                    append("课件：").append(item.id).append('\n')
                                                    append("探测：").append(probed).append('\n')
                                                    if (consoleTail.isNotEmpty()) {
                                                        append("控制台：\n")
                                                        consoleTail.forEach { append("  ").append(it).append('\n') }
                                                    }
                                                    append("UA：").append(
                                                        System.getProperty("http.agent").orEmpty()
                                                    )
                                                }
                                                val status = probed.substringBefore('|')
                                                // 页面已经自己换成静态图的两种状态：不再盖一层，
                                                // 只把诊断留在「复制诊断信息」里。
                                                val pageHandled = status == "no-webgl" || status == "stale-engine"
                                                if (!pageHandled && status.isNotBlank() &&
                                                    status != "ready" && status != "(none)"
                                                ) {
                                                    pageProblem = "课件页面加载了，但没跑起来（状态：$status）。" +
                                                        "点「用浏览器打开」试试；不行就把诊断信息发给我。"
                                                }
                                            }
                                        }, 2600)
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
                                // 控制台尾部：真跑不起来时，报错信息在这一屏之外，
                                // 用户能复制的只有它。
                                webChromeClient = object : WebChromeClient() {
                                    override fun onConsoleMessage(msg: ConsoleMessage): Boolean {
                                        val line = "${msg.message()} (${msg.sourceId()}:${msg.lineNumber()})"
                                        if (consoleTail.size >= 12) consoleTail.removeAt(0)
                                        consoleTail.add(line)
                                        return true
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
/**
 * 页面自报状态：`data-status` 由课件的 ui.js 在初始化成功后改写成 `ready`；
 * 顺带探一次 WebGL——手机上打不开多半是这一条为假。
 */
private const val PROBE_JS = """(function(){try{
  var s=document.body.getAttribute('data-status')||'(none)';
  var c=document.createElement('canvas');
  var gl=c.getContext('webgl')||c.getContext('experimental-webgl');
  return s+'|'+(gl?gl.getParameter(gl.VERSION):'no-webgl');
}catch(e){return 'probe-error|'+e;}})()"""