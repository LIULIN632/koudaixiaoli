package com.pocket.xiaoli.data.onboarding

import android.content.Context

/**
 * 「首次向导走完了没」的本机记账。
 *
 * ## 为什么要单独记一笔
 *
 * 首次启动的流程是：同意条款 → 选学校 → 用教务账号登录/导入课表。
 * 它**不能**用"有没有选过校区"来判断：默认校区本来就是烟台理工，用户不选也能用；
 * 也不该每次同意条款都重走一遍（条款改版会重新征得同意，但那不是新用户）。
 *
 * 所以只认这一个键。老用户升级上来没有这个键，由
 * `AppContainer.hasExistingSetup()` 兜底判定为"已经配置过"。
 */
object FirstRunStore {

    private const val PREFS = "pocket_first_run"
    private const val KEY_DONE = "onboarding_done"

    private fun prefs(context: Context) =
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun isDone(context: Context): Boolean = prefs(context).getBoolean(KEY_DONE, false)

    fun markDone(context: Context) {
        prefs(context).edit().putBoolean(KEY_DONE, true).apply()
    }
}
