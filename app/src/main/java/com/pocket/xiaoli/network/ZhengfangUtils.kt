package com.pocket.xiaoli.network

import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.google.gson.JsonParser

/**
 * 正方教务公共工具：会话失效判断、学期标签、JSON 字段取值、响应数组提取。
 * 供 ZhengfangGradeApi / ZhengfangAcademicApi / ZhengfangCourseSelectApi / ZhengfangClient 共用，
 * 避免各 API 重复实现。
 */
object ZhengfangUtils {

    /** 响应为登录页 → 会话已失效（兼容不同正方版本的登录页特征） */
    fun isSessionDead(text: String): Boolean =
        text.contains("login_slogin") || text.contains("userLogin") ||
            text.contains("userPassword") || text.contains("用户登录")

    /**
     * 学期 → 中文标签。
     *
     * 同时吃三种写法，且**幂等**：
     * - 编码 `3` / `12`（正方老口径）
     * - 序号 `1` / `2`（烟理这个部署的 `XQMMC` / `JYXDXQMC` 就是 `1` / `2`）
     * - 已经是名称（含「学期」二字）→ **原样返回**
     *
     * ⚠️ 第三条是踩过的坑：老实现无条件套 `第${x}学期`，遇到已经是「第一学期」的值会拼出
     * 「第第一学期学期」—— 而它看起来只是"名字有点怪"，没人会当故障报上来。
     */
    fun termLabel(xqm: String): String {
        val v = xqm.trim()
        if (v.isEmpty()) return ""
        if (v.contains("学期")) return v
        return when (v) {
            "3", "1" -> "第1学期"
            "12", "2" -> "第2学期"
            else -> "第${v}学期"
        }
    }

    /**
     * 大小写不敏感的 JSON 字段取值：按 [names] 顺序取第一个非 null/非空字段值；
     * 数字原样转字符串；全部缺失返回空串。
     */
    fun jsonField(o: JsonObject, vararg names: String): String {
        val expected = names.mapTo(HashSet()) { it.lowercase() }
        for (key in o.keySet()) {
            if (key.lowercase() !in expected) continue
            val el = o.get(key) ?: continue
            if (el.isJsonNull) continue
            if (el.isJsonPrimitive) {
                val s = el.asString.trim()
                if (s.isNotEmpty()) return s
            }
        }
        return ""
    }

    /**
     * 每个数据行「折平」后的视图：既含外层字段，也含内层对象的标量字段。
     *
     * 为什么需要它：正方同一份数据在不同部署里的嵌套深度不一样 —— 见过
     * `{"kcmc":"高等数学","xf":"4"}`，也见过
     * `{"jxb_id":"1","kcmc":"高等数学","detail":{"xf":"4"}}`。
     * 如果只按顶层取，第二种部署的学分永远是空的，而**界面看起来完全正常**（只是少几个字）。
     * 折平之后两种形态用同一套别名表就能读到，不必给每个字段写两遍。
     *
     * 约定：内层字段**不覆盖**外层同名字段（外层离数据源更近，更可能是这一行自己的值）。
     * 内层是数组时跳过 —— 数组元素的归属需要调用方自己判断，折平会造出假字段。
     */
    fun flattenRow(o: JsonObject): JsonObject {
        val flat = JsonObject()
        for (key in o.keySet()) {
            val el = o.get(key) ?: continue
            if (el.isJsonPrimitive) flat.add(key, el)
        }
        for (key in o.keySet()) {
            val el = o.get(key) ?: continue
            if (!el.isJsonObject) continue
            val inner = el.asJsonObject
            for (innerKey in inner.keySet()) {
                if (flat.has(innerKey)) continue
                val innerEl = inner.get(innerKey) ?: continue
                if (innerEl.isJsonPrimitive) flat.add(innerKey, innerEl)
            }
        }
        return flat
    }

    /** 常见的数据容器键名，按概率从高到低排列（正方各模块用过的都在这儿） */
    private val CONTAINER_KEYS = listOf(
        "items", "data", "rows", "list", "result", "results",
        "xx", "kck", "kbList", "jxbList", "xkxx", "lsxx",
    )

    /**
     * 从正方响应里取出「数据行数组」。
     *
     * 为什么不能像 `ZhengfangGradeApi` 那样只认 `items`：那是**这一个接口**验过的形状。
     * 选课模块（`zzxkyzb`）的容器名没有公开可信的文档，写死一个键名的后果是
     * 「登录成功、页面不报错、列表空」—— 最难查的一类故障。
     * 所以这里按三层降级，并且**任何一层成功都返回**：
     *
     * 1. 常见容器键名（大小写不敏感：`items` / `Items` / `ITEMS` 都能命中）；
     * 2. 响应本身就是裸数组 `[ {...} ]`；
     * 3. **深度搜索第一个元素为对象的数组**（兜底，覆盖没见过的容器名）。
     *
     * 第 3 层是刻意保留的：它让「容器名猜错」从"功能不可用"降级为"功能可用但字段可能不全"，
     * 而字段层面的偏差由 `jsonField` 的别名表 + 开发者实验室的原始响应对照页兜住。
     *
     * 返回 `null` 表示**确实没找到数组**（空响应、HTML 登录页、`{"items":"oops"}`），
     * 由调用方决定是报错还是当空列表。
     */
    fun findRowArray(text: String): JsonArray? {
        val trimmed = text.trimStart()
        if (trimmed.isEmpty()) return null

        // 裸数组
        if (trimmed.startsWith("[")) {
            return runCatching { JsonParser.parseString(text).asJsonArray }.getOrNull()
        }
        if (!trimmed.startsWith("{")) return null

        val root = runCatching { JsonParser.parseString(text).asJsonObject }.getOrNull() ?: return null

        // 第 1 层：常见容器键名
        val lowerToKey = root.keySet().associateBy { it.lowercase() }
        for (candidate in CONTAINER_KEYS) {
            val key = lowerToKey[candidate] ?: continue
            val el = root.get(key) ?: continue
            if (el.isJsonArray) return el.asJsonArray
            // 一层嵌套：{"data":{"items":[...]}}
            if (el.isJsonObject) {
                val inner = el.asJsonObject
                val innerLower = inner.keySet().associateBy { it.lowercase() }
                for (innerCandidate in CONTAINER_KEYS) {
                    val innerKey = innerLower[innerCandidate] ?: continue
                    val innerEl = inner.get(innerKey) ?: continue
                    if (innerEl.isJsonArray) return innerEl.asJsonArray
                }
            }
        }

        // 第 3 层：深度搜索第一个「元素是对象」的数组
        return firstObjectArray(root)
    }

    /** 深度优先找第一个元素为 JSON 对象的数组；找不到返回 null */
    private fun firstObjectArray(el: com.google.gson.JsonElement): JsonArray? {
        when {
            el.isJsonArray -> {
                val arr = el.asJsonArray
                if (arr.size() > 0 && arr[0].isJsonObject) return arr
                // 元素不是对象（如字符串数组、纯 id 列表）→ 继续往下找
                for (child in arr) firstObjectArray(child)?.let { return it }
                return null
            }
            el.isJsonObject -> {
                for (key in el.asJsonObject.keySet()) {
                    val child = el.asJsonObject.get(key) ?: continue
                    firstObjectArray(child)?.let { return it }
                }
                return null
            }
            else -> return null
        }
    }
}
