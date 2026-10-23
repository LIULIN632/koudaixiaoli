package com.pocket.xiaoli.data.service

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri

/**
 * 快递取件码 / 身份码直达。
 *
 * 两条深链来自兄弟项目「聚在工大」实测可用的写法（`Constant.PDD_PACKAGE_URL` /
 * `Constant.TAO_BAO_PACKAGE_ID_URL`），校内驿站用它扫码取件：
 * - 拼多多：显式 ComponentName(MainFrameActivity) + 身份码页 URL
 * - 淘宝：ACTION_VIEW + setPackage(淘宝) + 身份码页 URL
 *
 * ## 为什么不先判断「是否已安装」
 * Android 11+ 的包可见性会让 `getLaunchIntentForPackage` 对未在 `<queries>` 声明的包返回 null，
 * 直接 gate 在「已安装」上会把装了的 App 误判成没装、跳去应用商店。
 * 所以这里**先发 Intent**（能发出去就说明装得了），发不出去再逐级降级：
 * 深链 → 对应 App 首页 → 应用商店搜索。
 */
object ExpressPickup {

    const val PDD_PACKAGE = "com.xunmeng.pinduoduo"
    const val TAOBAO_PACKAGE = "com.taobao.taobao"

    private const val PDD_MAIN_ACTIVITY = "com.xunmeng.pinduoduo.ui.activity.MainFrameActivity"
    /** 拼多多身份码页（tab=ID_CODE） */
    const val PDD_ID_CODE_URL = "https://m.pinduoduo.net/mdkd/package?tab=ID_CODE"
    /** 淘宝身份码页（末公里取件码） */
    const val TAOBAO_ID_CODE_URL =
        "https://pages-fast.m.taobao.com/wow/z/uniapp/1011717/last-mile-fe/end-collect-platform/identity-code"

    /** 打开拼多多身份码页 */
    fun openPinduoduo(context: Context) {
        val uri = Uri.parse(PDD_ID_CODE_URL)

        // 1) 显式 Activity（「聚在工大」用法，不依赖包可见性）
        if (startSafely(context) {
                Intent().apply {
                    component = ComponentName(PDD_PACKAGE, PDD_MAIN_ACTIVITY)
                    data = uri
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
            }
        ) return

        // 2) 包名 + ACTION_VIEW
        if (startSafely(context) {
                Intent(Intent.ACTION_VIEW, uri)
                    .setPackage(PDD_PACKAGE)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
        ) return

        // 3) App 首页 → 4) 应用商店
        if (SchoolServiceLauncher.launchApp(PDD_PACKAGE, context)) return
        SchoolServiceLauncher.openMarketSearch("拼多多", context)
    }

    /** 打开淘宝身份码页 */
    fun openTaobao(context: Context) {
        val uri = Uri.parse(TAOBAO_ID_CODE_URL)

        if (startSafely(context) {
                Intent(Intent.ACTION_VIEW, uri)
                    .setPackage(TAOBAO_PACKAGE)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
        ) return

        if (SchoolServiceLauncher.launchApp(TAOBAO_PACKAGE, context)) return
        SchoolServiceLauncher.openMarketSearch("淘宝", context)
    }

    /** startActivity 且吞掉 ActivityNotFoundException 之类的异常，返回是否成功发出 */
    private inline fun startSafely(context: Context, build: () -> Intent): Boolean =
        runCatching { context.startActivity(build()) }.isSuccess
}
