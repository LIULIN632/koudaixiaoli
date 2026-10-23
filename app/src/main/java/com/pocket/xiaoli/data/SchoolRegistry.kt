package com.pocket.xiaoli.data

import android.content.Context
import android.util.Log
import com.pocket.xiaoli.network.SchoolIndex
import com.pocket.xiaoli.network.SchoolIndexUpdater
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import java.io.InputStreamReader

enum class JwSystemType(val label: String, val description: String) {
    ZHENGFANG("正方教务", "支持大多数高校"),
    QIANGZHI("强智教务", "支持走班制课程"),
    QINGGUO("青果教务", "支持新高考排课"),
    CHAOXING("超星学习通", "支持学习通同步"),
    URP("URP教务", "支持URP教务系统")
}

data class School(
    val id: String,
    val name: String,
    val city: String,
    val jwType: JwSystemType,
    val baseUrl: String,
    val isV8: Boolean = true
)

fun isJwSystemAvailable(type: JwSystemType): Boolean =
    type == JwSystemType.ZHENGFANG || type == JwSystemType.QIANGZHI

/**
 * 本 App 的主场城市。
 *
 * 这个软件已从「什么学校都能接」收窄成**烟台理工学院的校园助手**：
 * 选校列表只列烟台本地院校，用户不再面对 200+ 所全国学校的噪音。
 * 内置数据仍保留全国名单（后续铺开烟台、或用户手动添加外地学校都用得上），
 * 这里只控制**默认展示范围**。
 */
const val HOME_CITY = "烟台"

/** 置顶学校：先当前对接的烟台理工学院，再下一步优先覆盖的烟台大学。 */
private val PINNED_SCHOOL_IDS = listOf("YIT", "YTU")

/** 是否进入选校列表：本地院校，或用户自己添加的学校（`custom_` 前缀）。 */
fun School.isPickerSchool(): Boolean = city == HOME_CITY || id.startsWith("custom_")

/** 选校列表的置顶位次：越小越靠前，非置顶学校统一排在后面。 */
fun School.pickerRank(): Int = PINNED_SCHOOL_IDS.indexOf(id).let { if (it >= 0) it else PINNED_SCHOOL_IDS.size }

data class SchoolJson(
    val id: String,
    val name: String,
    val city: String,
    val jwType: String,
    val baseUrl: String,
    val isV8: Boolean? = true
)

class SchoolRegistry(private val context: Context) {
    // Load schools on demand by type: 本地热更新索引优先，回退内置 assets
    private val schoolsCache = mutableMapOf<JwSystemType, List<School>>()
    private var localIndex: SchoolIndex? = null

    fun getSchools(type: JwSystemType): List<School> = schoolsCache.getOrPut(type) {
        try {
            loadIndexSchools(type).ifEmpty { loadSchoolsFromAsset(type) }
        } catch (e: Exception) {
            // 索引/解析任何异常都不允许崩 UI → 回退内置 assets
            Log.e("SchoolRegistry", "Index load failed for ${type.name}, fallback to asset: ${e.message}", e)
            loadSchoolsFromAsset(type)
        }
    }

    fun reload() {
        synchronized(schoolsCache) {
            schoolsCache.clear()
            localIndex = null
        }
    }

    /** 本地热更新索引中的学校（仅当索引版本存在且该类型有数据）；任何异常回退空列表走 assets */
    private fun loadIndexSchools(type: JwSystemType): List<School> {
        return try {
            val index = localIndex ?: SchoolIndexUpdater(context).loadLocalIndex()?.also { localIndex = it }
                ?: return emptyList()
            val list = index.schools?.get(type.name.lowercase()) ?: return emptyList()
            list.mapNotNull { raw ->
                // 空字段防御：Gson 不填 Kotlin 默认值，缺字段会得到 null，逐字段兜底
                if (raw.id.isNullOrBlank() || raw.name.isNullOrBlank()) return@mapNotNull null
                School(
                    id = raw.id,
                    name = raw.name,
                    city = raw.city ?: "",
                    jwType = parseJwType(raw.jwType),
                    baseUrl = raw.baseUrl ?: "",
                    isV8 = raw.isV8 ?: true
                )
            }
        } catch (e: Exception) {
            Log.e("SchoolRegistry", "loadIndexSchools failed for ${type.name}: ${e.message}", e)
            emptyList()
        }
    }

    private fun loadSchoolsFromAsset(type: JwSystemType): List<School> {
        return try {
            val fileName = "schools_${type.name.lowercase()}.json"
            val result = loadSchoolsFromJson(fileName).map { raw ->
                School(
                    id = raw.id,
                    name = raw.name,
                    city = raw.city,
                    jwType = parseJwType(raw.jwType),
                    baseUrl = raw.baseUrl,
                    isV8 = raw.isV8 ?: true
                )
            }
            if (result.isNotEmpty()) {
                Log.d("SchoolRegistry", "Loaded ${result.size} ${type.name} schools from $fileName")
                result
            } else {
                throw IllegalStateException("File $fileName is empty")
            }
        } catch (e: Exception) {
            Log.e("SchoolRegistry", "Failed to load ${type.name} schools: ${e.message}", e)
            emptyList()
        }
    }

    private fun loadSchoolsFromJson(fileName: String): List<SchoolJson> {
        val input = context.assets.open(fileName)
        val reader = InputStreamReader(input, Charsets.UTF_8)
        val result: List<SchoolJson> = Gson().fromJson(
            reader,
            object : TypeToken<List<SchoolJson>>() {}.type
        )
        reader.close()
        return result
    }

    // Lazy load all schools for search/display (backwards compatibility)
    val allSchools: List<School> by lazy {
        val result = JwSystemType.values().flatMap { getSchools(it) }
        if (result.isNotEmpty()) {
            Log.d("SchoolRegistry", "Total schools: ${result.size}")
            result
        } else {
            Log.w("SchoolRegistry", "All per-type files failed, using fallback")
            fallbackSchools
        }
    }

    fun getById(id: String): School? =
        allSchools.firstOrNull { it.id.equals(id, ignoreCase = true) }

    fun filter(type: JwSystemType, query: String): List<School> {
        val q = query.trim()
        return getSchools(type).filter {
            q.isEmpty() || it.name.contains(q, ignoreCase = true) || it.city.contains(q, ignoreCase = true)
        }
    }

    private fun parseJwType(raw: String): JwSystemType = when (raw.uppercase()) {
        "QIANGZHI" -> JwSystemType.QIANGZHI
        "QINGGUO" -> JwSystemType.QINGGUO
        "CHAOXING" -> JwSystemType.CHAOXING
        "URP" -> JwSystemType.URP
        else -> JwSystemType.ZHENGFANG
    }

    companion object {
        val fallbackSchools = listOf(
            School("yitsd", "烟台理工学院", "烟台", JwSystemType.ZHENGFANG, "https://jwxt.yitsd.edu.cn/jwglxt", isV8 = true),
            School("ytu", "烟台大学", "烟台", JwSystemType.ZHENGFANG, "http://210.47.245.4/", isV8 = false),
            School("cust", "长春理工大学", "长春", JwSystemType.ZHENGFANG, "https://jw.cust.edu.cn/jwglxt"),
            School("cquit", "重庆理工大学", "重庆", JwSystemType.ZHENGFANG, "https://jw.cqut.edu.cn/jwglxt"),
            School("cqu", "重庆大学", "重庆", JwSystemType.ZHENGFANG, "https://jw.cqu.edu.cn/jwglxt"),
            School("scut", "华南理工大学", "广州", JwSystemType.ZHENGFANG, "https://jw.scut.edu.cn/jwglxt"),
            School("tongji", "同济大学", "上海", JwSystemType.ZHENGFANG, "https://jw.tongji.edu.cn/jwglxt"),
            School("jnu", "暨南大学", "广州", JwSystemType.ZHENGFANG, "https://jw.jnu.edu.cn/jwglxt"),
            School("suda", "苏州大学", "苏州", JwSystemType.ZHENGFANG, "https://jw.suda.edu.cn/jwglxt"),
            School("fosu", "佛山大学", "佛山", JwSystemType.QIANGZHI, "https://jw.fosu.edu.cn"),
            School("jxiust", "江西理工大学", "赣州", JwSystemType.QIANGZHI, "https://jw.jxust.edu.cn"),
            School("gdpu", "广东药科大学", "广州", JwSystemType.QIANGZHI, "https://jw.gdpu.edu.cn"),
            School("gdit", "广东科技学院", "东莞", JwSystemType.CHAOXING, "https://jw.gdit.edu.cn"),
            School("hnswzy", "湖南商务职业技术学院", "长沙", JwSystemType.QINGGUO, "https://jw.hnswzy.com"),
            School("sdnu", "山东师范大学", "济南", JwSystemType.URP, "https://jw.sdnu.edu.cn"),
        )
    }
}
