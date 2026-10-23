package com.pocket.xiaoli.data.quickanswer

import android.content.Context

/**
 * 「会了 / 还不会」的本地账本。
 *
 * ## 为什么只存在本机
 *
 * 这是学习状态，比课表还私密：能反推你哪门课在挂科。所以**不联网、不上报**，
 * 连 key 都不带课程之外的信息（只有题目 id）。清一次缓存就重置 —— 可以接受。
 *
 * ## 为什么用两个集合而不是一个 Map
 *
 * 三态（标记会 / 标记不会 / 没标记）用两个 Set 表达最直白；
 * 用 Map<String, Boolean> 会让"没标记"和"标了 false"混在一起，
 * 界面上「全都不确定」和「都不确定」就分不开了。
 */
class QuickAnswerStore(context: Context) {

    private val prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun knownIds(): Set<String> = prefs.getStringSet(KEY_KNOWN, emptySet()).orEmpty()

    fun unknownIds(): Set<String> = prefs.getStringSet(KEY_UNKNOWN, emptySet()).orEmpty()

    /** 标记一道题；传 null 表示取消标记。会与不会互斥。 */
    fun mark(questionId: String, known: Boolean?) {
        val knownSet = knownIds().toMutableSet()
        val unknownSet = unknownIds().toMutableSet()
        knownSet.remove(questionId)
        unknownSet.remove(questionId)
        when (known) {
            true -> knownSet.add(questionId)
            false -> unknownSet.add(questionId)
            null -> Unit
        }
        prefs.edit()
            .putStringSet(KEY_KNOWN, knownSet)
            .putStringSet(KEY_UNKNOWN, unknownSet)
            .apply()
    }

    fun clear() {
        prefs.edit().remove(KEY_KNOWN).remove(KEY_UNKNOWN).apply()
    }

    companion object {
        private const val PREFS = "quickanswer_marks"
        private const val KEY_KNOWN = "known"
        private const val KEY_UNKNOWN = "unknown"
    }
}
