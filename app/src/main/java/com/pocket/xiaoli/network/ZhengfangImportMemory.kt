package com.pocket.xiaoli.network

import android.content.Context
import android.content.SharedPreferences
import android.util.Log

/**
 * 正方教务课表导入「成功组合」跨会话记忆。
 *
 * fetchScheduleV8 用「URL 模式 × body 模板」三重循环试错最多 30 次请求，
 * 这里把该校上次成功的组合持久化，下次导入优先试记忆组合 → 通常 1 次请求完成。
 *
 * 存储格式：prefs["zf_import_probe_{host}"] = "urlPattern\u0001bodyTemplate\u0001savedAtMs"
 * TTL 30 天：学校系统升级/改版后旧组合自动作废，回退全量试错。
 */
object ZhengfangImportMemory {

    private const val PREFS_NAME = "egg_rice_import_memory"
    private const val TTL_MS = 30L * 24 * 60 * 60 * 1000 // 30 天
    private const val SEP = "\u0001"

    private var prefs: SharedPreferences? = null

    fun init(context: Context) {
        prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    /**
     * 记录该校课表接口的成功组合。
     * @param baseUrl 学校教务地址（如 https://jwxt.yitsd.edu.cn/jwglxt）
     * @param urlPattern 成功请求的 URL 模式（如 .../kbcx/xskbcx_cxXsgrkb.html?gnmkdm=N2151）
     * @param bodyTemplate 成功请求的 body 模板（含 {xnm}/{xqm}/{xsdm} 占位符）
     */
    fun save(baseUrl: String, urlPattern: String, bodyTemplate: String) {
        val p = prefs ?: return
        val key = keyOf(baseUrl)
        try {
            p.edit()
                .putString(key, "$urlPattern$SEP$bodyTemplate$SEP${System.currentTimeMillis()}")
                .apply()
        } catch (e: Exception) {
            Log.w("ZhengfangImportMemory", "save failed", e)
        }
    }

    /**
     * 读取该校记忆组合；超过 TTL 或数据损坏时返回 null。
     * @return Pair(urlPattern, bodyTemplate)
     */
    fun load(baseUrl: String): Pair<String, String>? {
        val p = prefs ?: return null
        val raw = try {
            p.getString(keyOf(baseUrl), null)
        } catch (e: Exception) {
            null
        } ?: return null
        val parts = raw.split(SEP)
        if (parts.size < 3) return null
        val savedAt = parts[2].toLongOrNull() ?: return null
        if (System.currentTimeMillis() - savedAt > TTL_MS) {
            // 过期记忆作废，顺便清理
            p.edit().remove(keyOf(baseUrl)).apply()
            return null
        }
        val url = parts[0]
        val body = parts[1]
        if (url.isEmpty() || body.isEmpty()) return null
        return Pair(url, body)
    }

    /** 清除某校记忆（调试/主动重置用） */
    fun clear(baseUrl: String) {
        prefs?.edit()?.remove(keyOf(baseUrl))?.apply()
    }

    private fun keyOf(baseUrl: String): String {
        // 用 host 区分学校，避免路径差异导致同一学校多条记忆
        val trimmed = baseUrl.trimEnd('/')
        val host = trimmed.substringAfter("://").substringBefore("/")
            .ifBlank { trimmed }
        return "zf_import_probe_$host"
    }
}
