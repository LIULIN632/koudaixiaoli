package com.pocket.xiaoli.data.electricity

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri

/**
 * 把缴费页链接送到**微信**里去打开。
 *
 * ## 为什么必须去微信
 *
 * 烟大南校后勤缴费（`ydny.ytu.edu.cn/pms/wechat/ytdx/`）的支付入口**只在微信
 * openid 会话里才出现**。实测（详见 `docs/电费缴费排查.md`）：
 *
 * - `payItemIndex?accountId=..&itemId=..` 能正常显示余额/户号（这步不依赖微信）
 * - 点「立即缴费」→ 跳 `YDPost`，而 `YDPost` **在我们发起的任何请求里都只返回空壳**
 *   （2432 字节，唯一可点元素是「增绑新用户」→ `payItemAdd`）
 *   —— 这正是用户看到的"点缴费跳到绑定界面"
 * - 已排除：会话 Cookie / 微信 UA / `Referer: servicewechat.com` /
 *   `X-Requested-With: com.tencent.mm` / GET 与 POST，**结果全部一样**
 *
 * ⇒ 我们从 App 侧拿不到 openid，这一层修不了。**正确做法是把链接交给微信。**
 *
 * ## 为什么不直接用 `weixin://` 深链（v1.1.3 的做法，已废弃）
 *
 * v1.1.3 试的是 `weixin://dl/business/?t=<urlencoded>`，用户实测**不能正常跳转**。
 * 两个原因：
 *
 * 1. `weixin://dl/business/` 是**微信支付的回跳格式**，并不保证用于"打开任意网页"
 * 2. 即便拉起成功，目标 URL 在传输中被整体 URL-encode（`:` → `%3A`、`/` → `%2F`），
 *    微信未必会先解码再打开
 *
 * ## 现在这条路：复制 + 拉起微信（项目里已验证可行）
 *
 * 与 `SchoolServiceLauncher.openWeChatMiniProgram` 同构 —— 那是本项目**已经在用、
 * 已验证能拉起微信**的实现。两处细节都照它来：
 *
 * - **检测安装**用 `getLaunchIntentForPackage` 而不是 `getPackageInfo`：
 *   前者在目标包没有可启动 Activity 时返回 null（安全），
 *   后者在部分 ROM 上因包可见性限制抛 `NameNotFoundException`，
 *   会把"装了微信"误判成"没装"，于是代码根本不去拉微信。
 * - **拉起**也用 `getLaunchIntentForPackage` + `FLAG_ACTIVITY_NEW_TASK`。
 *
 * 然后靠剪贴板把链接带过去：用户粘进微信（任意聊天窗口或"文件传输助手"），
 * 点开就是**微信内置浏览器**，那里带着 openid 会话，缴费流程才成立。
 *
 * ⚠️ 这条路的代价是多一次粘贴。之所以接受：另一条路（深链直接开网页）
 * 实测不成立，而**在微信外打开这个页面会退化成绑定页**，那更糟。
 */
object WeChatUrlLauncher {

    /** 微信包名。抽成常量，避免各处硬编码写错。 */
    const val WECHAT_PACKAGE = "com.tencent.mm"

    /**
     * 微信是否可用。
     *
     * ⚠️ 用 `getLaunchIntentForPackage` 而不是 `getPackageInfo`：
     * 后者在 Android 11+ 的包可见性限制下、或某些 ROM 上会抛异常，
     * 导致"装了微信却判定为没装"——那样按钮就永远走不到拉起微信这一步。
     */
    fun isWeChatInstalled(context: Context?): Boolean {
        if (context == null) return false
        return runCatching {
            context.packageManager.getLaunchIntentForPackage(WECHAT_PACKAGE) != null
        }.getOrDefault(false)
    }

    /**
     * 打开微信。
     *
     * @return 是否成功拉起（失败时调用方应给用户兜底提示）
     */
    fun launchWeChat(context: Context?): Boolean {
        if (context == null) return false
        return runCatching {
            val intent = context.packageManager.getLaunchIntentForPackage(WECHAT_PACKAGE)
                ?: return@runCatching false
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)
            true
        }.getOrDefault(false)
    }

    /**
     * 把 [targetUrl] 复制到剪贴板，并拉起微信。
     *
     * 只接受 http(s)：这个字符串会被用户粘到微信里，不该把任意 scheme 塞进剪贴板。
     *
     * @return 结果，供界面决定提示什么
     */
    fun sendToWeChat(context: Context?, targetUrl: String?): Outcome {
        val url = targetUrl?.trim().orEmpty()
        if (!url.startsWith("http://", ignoreCase = true) &&
            !url.startsWith("https://", ignoreCase = true)
        ) {
            return Outcome.InvalidUrl
        }
        if (context == null) return Outcome.CopyFailed

        val copied = runCatching {
            val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            cm.setPrimaryClip(ClipData.newPlainText("缴费链接", url))
            true
        }.getOrDefault(false)
        if (!copied) return Outcome.CopyFailed

        return if (launchWeChat(context)) Outcome.CopiedAndOpened else Outcome.CopiedOnly
    }

    /**
     * 只复制链接、不拉起微信。
     *
     * 用于"微信装了但拉起失败"以及"没装微信"两种情况 —— 用户至少能自己把链接带到微信去。
     */
    fun copyLink(context: Context?, targetUrl: String?): Outcome {
        val url = targetUrl?.trim().orEmpty()
        if (!url.startsWith("http://", ignoreCase = true) &&
            !url.startsWith("https://", ignoreCase = true)
        ) {
            return Outcome.InvalidUrl
        }
        if (context == null) return Outcome.CopyFailed
        val copied = runCatching {
            val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            cm.setPrimaryClip(ClipData.newPlainText("缴费链接", url))
            true
        }.getOrDefault(false)
        return if (copied) Outcome.CopiedOnly else Outcome.CopyFailed
    }

    /** [sendToWeChat] 的结果。界面据此给不同提示，避免"做了但没说清"。 */
    enum class Outcome {
        /** 已复制并成功拉起微信 —— 提示用户粘贴即可 */
        CopiedAndOpened,

        /** 已复制，但微信没拉起来（未安装 / 被系统拦） —— 提示手动打开微信 */
        CopiedOnly,

        /** 复制失败 —— 只能让用户自己想办法 */
        CopyFailed,

        /** 传入的不是 http(s) 链接 —— 属于调用方 bug，界面不应出现这种情况 */
        InvalidUrl,
    }
}
