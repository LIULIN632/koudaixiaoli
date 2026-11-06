package com.pocket.xiaoli.data.school

import android.content.Context
import android.content.SharedPreferences

/**
 * 按学校隔离的 SharedPreferences。
 *
 * 同一台设备可以切换学校，而这些 key（问答标记、导入记忆、URL 历史、服务页收藏）以前是全局的：
 * 换校后 A 校的“我这章会了”会显示在 B 校的同一章上，服务页收藏也会串。
 *
 * 做法是**文件名带学校后缀**（`<base>@<schoolId>`），而不是 key 加前缀：
 * key 前缀需要改所有读写点，文件名隔离则只改“拿 prefs”的那一行，而且清一所学校的数据时
 * 直接删它那个文件即可。
 *
 * 兼容：升级后第一次访问某个 base 时，如果旧的无后缀文件存在且带后缀的还没有数据，
 * 就把旧内容整份复制过来 —— 老用户不会因为升级丢掉“哪些章节标记过”这类本地状态。
 */
object SchoolScopedPrefs {

    /** 当前学校存在这个文件里（与数据库初始化读的是同一处）。 */
    private const val FILE = "pocket_xiaoli_prefs"
    private const val KEY = "academic_school_id"

    /** 没选过学校时的兜底，与 `PocketXiaoliDatabase.DEFAULT_SCHOOL_ID` 保持一致。 */
    internal const val FALLBACK_SCHOOL_ID = "YIT"

    fun currentSchoolId(context: Context): String =
        context.getSharedPreferences(FILE, Context.MODE_PRIVATE)
            .getString(KEY, null)
            ?.takeIf { it.isNotBlank() }
            ?: FALLBACK_SCHOOL_ID

    /** 例如 `quickanswer_marks@YIT`。 */
    fun nameFor(context: Context, base: String): String = "$base@${currentSchoolId(context)}"

    fun of(context: Context, base: String): SharedPreferences {
        val scoped = context.getSharedPreferences(nameFor(context, base), Context.MODE_PRIVATE)
        copyLegacyOnce(context, base, scoped)
        return scoped
    }

    /**
     * 老的全局文件 → 当前学校的文件，只做一次（用 `__legacy_copied` 记账）。
     * 只复制、不删除旧文件：万一判断错了还能找回。
     */
    private fun copyLegacyOnce(context: Context, base: String, scoped: SharedPreferences) {
        if (scoped.getBoolean(FLAG, false)) return
        val legacy = context.getSharedPreferences(base, Context.MODE_PRIVATE)
        val editor = scoped.edit()
        if (legacy.all.isNotEmpty()) {
            legacy.all.forEach { (key, value) ->
                @Suppress("UNCHECKED_CAST")
                when (value) {
                    is String -> editor.putString(key, value)
                    is Boolean -> editor.putBoolean(key, value)
                    is Int -> editor.putInt(key, value)
                    is Long -> editor.putLong(key, value)
                    is Float -> editor.putFloat(key, value)
                    is Set<*> -> editor.putStringSet(key, value as Set<String>)
                }
            }
        }
        editor.putBoolean(FLAG, true).apply()
    }

    private const val FLAG = "__legacy_copied"
}
