package com.pocket.xiaoli.data.electricity

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

/**
 * 「把缴费链接送去微信」的入参校验与空 Context 兜底。
 *
 * ⚠️ 涉及真实 Context 的分支（剪贴板、拉起微信）无法在纯 JVM 单测里跑，
 * 这里只锁两件**在 JVM 下就能确证**的事：
 *
 * 1. **非 http(s) 的串一律不接受** —— 这个字符串会被写进剪贴板、由用户粘到微信里，
 *    不该出现 `javascript:` / `file://` / 任意 `xxx://` 这类东西。
 * 2. **Context 为 null 时不崩**，且如实返回失败态 —— 界面据此给提示，
 *    而不是抛异常或静默什么都不做。
 *
 * 拉起微信本身依赖 `getLaunchIntentForPackage`，属于设备行为，只能在真机验证。
 */
class WeChatUrlLauncherTest {

    private val payUrl = "https://ydny.ytu.edu.cn/pms/wechat/ytdx/payItemIndex" +
        "?accountId=6237&itemType=METER&itemId=8392"

    @Test
    fun `非 http 链接一律判为非法 —— 不写进剪贴板`() {
        // 这些串一旦进了剪贴板、被用户粘到微信，行为不可预期
        for (bad in listOf(
            null, "", "   ",
            "weixin://evil", "javascript:alert(1)", "file:///etc/passwd",
            "content://settings/system", "intent://x#Intent;end",
        )) {
            assertEquals(
                "应判为 InvalidUrl：$bad",
                WeChatUrlLauncher.Outcome.InvalidUrl,
                WeChatUrlLauncher.sendToWeChat(null, bad),
            )
        }
    }

    @Test
    fun `合法链接但 Context 为空时返回失败态而不是抛异常`() {
        // 界面拿到失败态才能给提示；抛异常会把点击变成崩溃
        assertEquals(
            WeChatUrlLauncher.Outcome.CopyFailed,
            WeChatUrlLauncher.sendToWeChat(null, payUrl),
        )
    }

    @Test
    fun `Context 为空时探测与拉起都返回 false`() {
        assertFalse(WeChatUrlLauncher.isWeChatInstalled(null))
        assertFalse(WeChatUrlLauncher.launchWeChat(null))
    }

    @Test
    fun `http 与 https 都算合法 大小写不敏感`() {
        // 只验证"没被判成 InvalidUrl"：Context 为 null 时合法链接会走到 CopyFailed
        for (ok in listOf("http://a.cn/x", "https://a.cn/x", "HTTPS://A.CN/x", "  https://a.cn/x  ")) {
            assertEquals(
                "应通过 URL 校验：$ok",
                WeChatUrlLauncher.Outcome.CopyFailed,
                WeChatUrlLauncher.sendToWeChat(null, ok),
            )
        }
    }

    @Test
    fun `copyLink 与 sendToWeChat 的入参校验必须一致`() {
        // 两个入口接受/拒绝的串必须一样，否则会出现"主按钮说地址非法、
        // 复制按钮却把非法串写进剪贴板"这种不一致。
        for (bad in listOf(null, "", "  ", "weixin://x", "javascript:alert(1)", "file:///etc/passwd")) {
            assertEquals(
                "sendToWeChat 应拒绝：$bad",
                WeChatUrlLauncher.Outcome.InvalidUrl,
                WeChatUrlLauncher.sendToWeChat(null, bad),
            )
            assertEquals(
                "copyLink 应拒绝：$bad",
                WeChatUrlLauncher.Outcome.InvalidUrl,
                WeChatUrlLauncher.copyLink(null, bad),
            )
        }
        for (ok in listOf("http://a.cn/x", "https://a.cn/x")) {
            assertEquals(
                WeChatUrlLauncher.Outcome.CopyFailed,
                WeChatUrlLauncher.sendToWeChat(null, ok),
            )
            assertEquals(
                WeChatUrlLauncher.Outcome.CopyFailed,
                WeChatUrlLauncher.copyLink(null, ok),
            )
        }
    }

    @Test
    fun `结果枚举覆盖全部四种去向 —— 界面必须逐种给提示`() {
        // 这里断言的是"枚举本身没被删项"。四种去向各自对应不同的用户提示，
        // 少一种就意味着某种失败被静默吞掉（用户看到按钮没反应）。
        val names = WeChatUrlLauncher.Outcome.entries.map { it.name }.toSet()
        assertEquals(
            setOf("CopiedAndOpened", "CopiedOnly", "CopyFailed", "InvalidUrl"),
            names,
        )
    }
}
