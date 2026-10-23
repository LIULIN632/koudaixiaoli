package com.pocket.xiaoli.data.service

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast

/**
 * 校园服务跳转分发器 —— 移植自「聚在工大」SchoolServiceLauncher。
 *
 * 按 [ServiceChannel] 决定怎么打开，并为每条路准备兜底：
 *  - APP       : 按包名拉起第三方 App；未装 → 提示 + 走 fallback（否则若 target 是网址则开网页）
 *  - ALIPAY    : alipays:// 一步拉起支付宝小程序
 *  - WEBVIEW   : 交给调用方（[WebViewSink]）在 App 内打开；没提供 sink 则退回外部浏览器
 *  - WECHAT_MP : 复制「#小程序://...」到剪贴板 + 拉起微信，让用户粘贴进入（目前唯一通用方案）
 */
object SchoolServiceLauncher {

    /** 微信包名（用于小程序引导） */
    private const val PKG_WECHAT = "com.tencent.mm"

    /** 由 UI 层提供的「在 App 内打开」落地实现 */
    fun interface WebViewSink {
        fun open(url: String, title: String)
    }

    fun open(
        service: SchoolService,
        context: Context,
        webViewSink: WebViewSink? = null,
        cookie: String? = null,
    ) {
        when (service.channel) {
            ServiceChannel.APP -> {
                if (!launchApp(service.target, context)) {
                    Toast.makeText(context, "未安装「${service.provider}」", Toast.LENGTH_SHORT).show()
                    when {
                        service.fallback.isNotBlank() -> openFallback(
                            url = service.fallback,
                            title = service.title,
                            context = context,
                            sink = webViewSink,
                            cookie = cookie,
                        )
                        service.target.startsWith("http") -> openWeb(
                            service.target,
                            service.title,
                            context,
                            webViewSink,
                            cookie,
                        )
                    }
                }
            }

            ServiceChannel.ALIPAY -> openAlipay(service.target, context)

            ServiceChannel.WEBVIEW -> openWeb(service.target, service.title, context, webViewSink, cookie)

            ServiceChannel.WECHAT_MP -> openWeChatMiniProgram(service.provider, service.target, context)
        }
    }

    /** APP 未安装时的兜底：支付宝 / 小程序 / 应用商店 / 官网分流。 */
    private fun openFallback(
        url: String,
        title: String,
        context: Context,
        sink: WebViewSink?,
        cookie: String?,
    ) {
        when {
            url.startsWith("alipays://", ignoreCase = true) -> openAlipay(url, context)
            url.startsWith("weixin://", ignoreCase = true) -> openExternal(url, context)
            url.startsWith("market://", ignoreCase = true) -> openExternal(url, context)
            url.startsWith("http://", ignoreCase = true) ||
                url.startsWith("https://", ignoreCase = true) -> openWeb(url, title, context, sink, cookie)
            else -> openExternal(url, context)
        }
    }

    /** 内置 WebView 优先；无 sink 时退回外部浏览器 */
    private fun openWeb(
        url: String,
        title: String,
        context: Context,
        sink: WebViewSink?,
        cookie: String?,
    ) {
        if (url.isBlank()) {
            Toast.makeText(context, "该服务暂未配置入口", Toast.LENGTH_SHORT).show()
            return
        }
        if (sink != null) sink.open(url, title) else openExternal(url, context)
    }

    /** 按包名拉起第三方 App，返回是否成功 */
    fun launchApp(pkg: String, context: Context): Boolean = runCatching {
        val intent = context.packageManager.getLaunchIntentForPackage(pkg)
            ?: return@runCatching false
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(intent)
        true
    }.getOrDefault(false)

    /** 打开支付宝小程序（[url] 需为完整 alipays:// 链接） */
    fun openAlipay(url: String, context: Context) {
        if (url.isBlank()) {
            Toast.makeText(context, "暂未配置支付宝入口", Toast.LENGTH_SHORT).show()
            return
        }
        val ok = runCatching {
            context.startActivity(
                Intent(Intent.ACTION_VIEW, Uri.parse(url))
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
        }.isSuccess
        if (!ok) Toast.makeText(context, "打开失败，请确认已安装支付宝", Toast.LENGTH_SHORT).show()
    }

    /** 用外部浏览器打开（兜底） */
    fun openExternal(url: String, context: Context) {
        val ok = runCatching {
            context.startActivity(
                Intent(Intent.ACTION_VIEW, Uri.parse(url))
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
        }.isSuccess
        if (!ok) Toast.makeText(context, "未找到可用浏览器", Toast.LENGTH_SHORT).show()
    }

    /**
     * 微信小程序引导：复制「#小程序://名称/xxxx」到剪贴板 → 拉起微信 → 提示用户粘贴。
     * 微信会把这段文本识别成可点击的小程序卡片。
     */
    fun openWeChatMiniProgram(name: String, link: String, context: Context) {
        val text = link.ifBlank { "#小程序://$name" }
        val copied = runCatching {
            val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            cm.setPrimaryClip(ClipData.newPlainText("mini_program", text))
            true
        }.getOrDefault(false)
        Toast.makeText(
            context,
            if (copied) "已复制「$name」，打开微信粘贴即可进入" else "复制失败，请手动打开微信搜索「$name」",
            Toast.LENGTH_LONG
        ).show()
        launchApp(PKG_WECHAT, context)
    }

    /** 检查包名对应的 App 是否可拉起（有启动 Intent 即视为已安装）。 */
    fun isAppInstalled(pkg: String, context: Context): Boolean =
        runCatching { context.packageManager.getLaunchIntentForPackage(pkg) != null }
            .getOrDefault(false)

    /**
     * 打开应用商店搜索关键词（今日校园未安装时的兜底）。
     * 各厂商应用商店均实现标准 market://search，失败则提示手动搜索。
     */
    fun openMarketSearch(keyword: String, context: Context) {
        val uri = Uri.parse("market://search?q=" + Uri.encode(keyword))
        val ok = runCatching {
            context.startActivity(Intent(Intent.ACTION_VIEW, uri).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        }.isSuccess
        if (!ok) {
            Toast.makeText(context, "未找到应用商店，请手动搜索「$keyword」", Toast.LENGTH_LONG).show()
        }
    }
}