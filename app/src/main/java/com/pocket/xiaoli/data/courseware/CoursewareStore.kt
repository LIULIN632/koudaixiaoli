package com.pocket.xiaoli.data.courseware

import android.content.Context
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 课件下载态的**本地账本**：装了什么、多大、上次用是什么时候。
 *
 * 存在 SharedPreferences（`courseware_store`）而不是缓存目录 —— 理由与反馈页一致：
 * 清缓存不该把用户已经下好的课件"清成未下载"，那等于让他白等一次下载。
 * 但**解压出来的文件**放 `filesDir/courseware/`，所以"清除数据"会一起清掉，
 * 这时账本也一并不存在了（两者必须同生共死，否则会出现"账本说装了、文件没了"）。
 *
 * 淘汰按**最近使用**（[touch] 在每次打开时调用），不是按下载时间：
 * 一个学期下来，用得少的那几个先走才是对的。
 */
internal class CoursewareStore(context: Context) {

    private val prefs = context.getSharedPreferences("courseware_store", Context.MODE_PRIVATE)
    private val gson = Gson()

    fun manifestText(): String? = prefs.getString(KEY_MANIFEST, null)

    fun manifestAt(): Long = prefs.getLong(KEY_MANIFEST_AT, 0L)

    fun saveManifest(text: String, at: Long) {
        prefs.edit().putString(KEY_MANIFEST, text).putLong(KEY_MANIFEST_AT, at).apply()
    }

    fun installed(): Map<String, Installed> {
        val raw = prefs.getString(KEY_INSTALLED, null) ?: return emptyMap()
        val type = object : TypeToken<Map<String, Installed>>() {}.type
        return runCatching { gson.fromJson<Map<String, Installed>>(raw, type) }.getOrNull().orEmpty()
    }

    fun installedOf(id: String): Installed? = installed()[id]

    fun markInstalled(id: String, version: Int, bytes: Long) {
        val next = installed().toMutableMap()
        next[id] = Installed(version = version, bytes = bytes, usedAt = now())
        write(next)
    }

    fun forget(id: String) {
        val next = installed().toMutableMap()
        next.remove(id)
        write(next)
    }

    /** 记一次"用过"，用于 LRU。写盘是 `apply()`，不阻塞打开课件的路径。 */
    fun touch(id: String) {
        val current = installed()
        val entry = current[id] ?: return
        if (now() - entry.usedAt < TOUCH_THROTTLE_MS) return
        val next = current.toMutableMap()
        next[id] = entry.copy(usedAt = now())
        write(next)
    }

    /**
     * 淘汰到 [limitBytes] 以下，返回被删掉的 id。
     *
     * [protect] 里的 id 永不淘汰（正在打开的那个课件、以及 `vendor` 这种共享包 ——
     * 删了它所有课件都会白屏，而它的收益是"所有课件共用一份"）。
     */
    fun evict(limitBytes: Long, protect: Set<String>): List<String> {
        val current = installed()
        var total = current.values.sumOf { it.bytes }
        if (total <= limitBytes) return emptyList()
        val removed = mutableListOf<String>()
        current.entries
            .filter { it.key !in protect }
            .sortedBy { it.value.usedAt }
            .forEach { (id, entry) ->
                if (total <= limitBytes) return@forEach
                total -= entry.bytes
                removed += id
            }
        if (removed.isEmpty()) return emptyList()
        val next = current.toMutableMap()
        removed.forEach { next.remove(it) }
        write(next)
        return removed
    }

    private fun write(map: Map<String, Installed>) {
        prefs.edit().putString(KEY_INSTALLED, gson.toJson(map)).apply()
    }

    private fun now(): Long = System.currentTimeMillis()

    data class Installed(val version: Int = 0, val bytes: Long = 0L, val usedAt: Long = 0L) {
        fun usedAtText(): String =
            SimpleDateFormat("MM-dd HH:mm", Locale.getDefault()).format(Date(usedAt))
    }

    private companion object {
        const val KEY_MANIFEST = "manifest"
        const val KEY_MANIFEST_AT = "manifest_at"
        const val KEY_INSTALLED = "installed"

        /** 5 分钟内重复打开不重复写盘 —— 打开课件是高频动作，没必要每次都落盘。 */
        const val TOUCH_THROTTLE_MS = 5 * 60 * 1000L
    }
}

/** 下载目录：`filesDir/courseware/<id>/`，与 [CoursewareServer] 的解析规则一一对应。 */
internal fun coursewareRoot(context: Context): File =
    File(context.filesDir, "courseware").apply { mkdirs() }
