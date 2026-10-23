package com.pocket.xiaoli.data.service

import android.content.Context
import com.pocket.xiaoli.data.CampusInfo
import com.pocket.xiaoli.data.CampusServiceKeys

/**
 * 校园卡（易校园）怎么打开。
 *
 * ## 为什么不能只做「拉起 App / 打开官网」
 *
 * 需求方原话：「校园卡用的易校园，你能不能把这个原生功能做好」。
 * 现状是：装了易校园就拉起 App；**没装**就打开 `www.yixiaoyuan.com` ——
 * 那是个宣传页，既看不到余额也不能充值，等于把用户丢进死胡同。
 *
 * 现在按这个顺序走：
 * 1. 装了易校园 → 拉起 App（充值和挂失这些还是原生 App 最稳）；
 * 2. 没装 → **门户里的易校园入口**（学校把易校园作为第三方应用嵌在智慧校园里，
 *    统一认证过去之后是免登的）—— 这才是"今日校园内置了易校园"的用法；
 * 3. 连入口都没找到（未登录 / 学校没配置）→ 门户首页，登录后自己就能看到。
 *
 * ⚠️ **余额类原生卡片还做不到**：那要易校园自己那套接口（云马平台，`cn.com.yunma.school.app`），
 * 它**不走学校 CAS**，与参考项目里的慧新易校是两回事。需要一次真实抓包才能接，
 * 见 `docs/优化清单.md` 的 B2 项 —— 这里不凭猜写解析。
 */
object CampusCardLauncher {

    /** 门户里那条入口可能叫什么（学校配置的名字不完全统一）。 */
    private val NAME_HINTS = arrayOf("易校园", "校园卡", "一卡通")

    /**
     * 打开校园卡。
     *
     * @param webViewSink 需要走内置网页时的出口（各页面的 `onOpenServiceWeb`）
     * @return 实际走了哪条路，供调用方决定要不要再提示
     */
    suspend fun open(
        context: Context,
        campus: CampusInfo,
        webViewSink: (url: String, title: String) -> Unit,
    ): Route {
        if (SchoolServiceLauncher.isAppInstalled(CampusServiceRegistry.CAMPUS_CARD_PACKAGE, context) &&
            SchoolServiceLauncher.launchApp(CampusServiceRegistry.CAMPUS_CARD_PACKAGE, context)
        ) {
            return Route.APP
        }

        val entry = runCatching { TodayCampusPortal.findApp(*NAME_HINTS) }.getOrNull()
        if (entry != null) {
            webViewSink(TodayCampusPortal.serviceUrl(entry.appId), entry.name)
            return Route.PORTAL_APP
        }

        val fallback = campus.serviceUrls[CampusServiceKeys.CAMPUS_CARD]
            ?: TodayCampusPortal.ORIGIN + "/new/index.html"
        webViewSink(fallback, "校园卡")
        return Route.PORTAL_HOME
    }

    enum class Route {
        /** 拉起了易校园 App。 */
        APP,

        /** 打开了门户里的易校园入口（免登）。 */
        PORTAL_APP,

        /** 只开到门户首页，需要用户自己在里面找。 */
        PORTAL_HOME,
    }
}
