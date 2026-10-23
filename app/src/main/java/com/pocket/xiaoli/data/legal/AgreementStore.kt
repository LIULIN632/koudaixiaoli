package com.pocket.xiaoli.data.legal

import android.content.Context

/**
 * 「同意过哪一版条款」的本机记账。
 *
 * 只有一个判据：[isAccepted] = 已同意的版本 ≥ [AgreementText.VERSION]。
 * 条款改版时把 `AgreementText.VERSION` +1，老用户下次启动会重新看到同意页 ——
 * 这是「改了条款必须重新征得同意」唯一可执行的实现方式（见 `AgreementText` 的注释）。
 *
 * 存 SharedPreferences 而不是缓存目录：清缓存不该让用户重新同意一遍条款。
 */
object AgreementStore {

    private const val PREFS = "pocket_agreement"
    private const val KEY_VERSION = "accepted_version"
    private const val KEY_AT = "accepted_at"

    private fun prefs(context: Context) =
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    /** 已同意的条款版本；0 = 从没同意过。 */
    fun acceptedVersion(context: Context): Int = prefs(context).getInt(KEY_VERSION, 0)

    /** 同意时间（Unix 毫秒，0 = 没同意过）。只用于展示/排查，不参与判定。 */
    fun acceptedAt(context: Context): Long = prefs(context).getLong(KEY_AT, 0L)

    /** 当前这份条款是否已经同意过。 */
    fun isAccepted(context: Context): Boolean = acceptedVersion(context) >= AgreementText.VERSION

    /** 记录「同意当前这一版」。 */
    fun accept(context: Context, now: Long = System.currentTimeMillis()) {
        prefs(context).edit()
            .putInt(KEY_VERSION, AgreementText.VERSION)
            .putLong(KEY_AT, now)
            .apply()
    }

    /** 清掉记录（开发者实验室的「模拟首次启动」用；正常路径不该调用）。 */
    fun reset(context: Context) {
        prefs(context).edit().clear().apply()
    }
}
