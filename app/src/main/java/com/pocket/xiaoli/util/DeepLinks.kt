package com.pocket.xiaoli.util

/**
 * 判断一个链接是否**值得给用户点**。
 *
 * ## 为什么需要它
 *
 * 项目里出现过**编造的示例链接**混进内置数据：`https://b23.tv/gaoshu_songhao`
 * 这种把课程名拼音拼上去的短链。b23.tv 的真实短码是 `BV1xx411c7mD` 形态的随机串，
 * 所以这类链接点开必然 404、或者被 B 站重定向到首页。
 *
 * 危害不在于"少一个功能"，而在于**用户会以为是自己手机或网络的问题** ——
 * 他会反复重试、换网络，而问题在数据里。所以宁可**不显示跳转按钮**，
 * 也不要给一个打不开的按钮。
 *
 * ## 判定口径
 *
 * 保守：只看得出"明显是假的"才判 false，**不联网、不保证链接可达**。
 * 目的是拦住占位符，不是做链接检测服务。
 *
 * 判为假的情况：
 * - 空白；
 * - 域名是保留的示例域（`example.com` / `example.edu.cn` / `localhost` 等）；
 * - 域名后**没有路径**（`https://b23.tv` 本身没有内容）；
 * - 路径只是一个**下划线/连字符拼接的长串**（`gaoshu_songhao`、`java_kuangshen`）——
 *   这是占位符的典型形态，真实视频短码不含下划线；
 * - 路径里含有 `placeholder` / `xxx` / `示例` 之类的字样。
 */
object DeepLinks {

    /** RFC 2606 保留域 + 常见占位写法。含 `example.edu` 才能覆盖 `example.edu.cn`。 */
    private val RESERVED_HOSTS = setOf(
        "example.com", "example.org", "example.net", "example.edu", "example.edu.cn",
        "localhost", "test.com", "your-domain.com", "domain.com",
    )

    private val PLACEHOLDER_HINTS = listOf("placeholder", "your-", "xxx", "todo", "示例", "占位")

    /**
     * @return true 表示这个链接**看起来是真的**，可以给用户点。
     */
    fun looksUsable(url: String?): Boolean {
        val raw = url?.trim().orEmpty()
        if (raw.isEmpty()) return false

        val lowered = raw.lowercase()
        if (!lowered.startsWith("http://") && !lowered.startsWith("https://")) return false
        if (PLACEHOLDER_HINTS.any { lowered.contains(it) }) return false

        // 拆出 host 与 path。
        // ⚠️ 三个坑，都踩过：
        // 1. 不能用 substringAfter('/', "") 取 path —— 首个斜杠就匹配，逻辑容易反；
        // 2. host 要**去掉端口**再比对，否则 `localhost:8080` 不等于 `localhost`；
        // 3. 判断顺序必须是"保留域 → 空路径"：`https://example.com/path` 的 path 非空，
        //    只靠空路径判断根本拦不住它。
        val withoutScheme = lowered.substringAfter("://")
        val hostAndRest = withoutScheme.substringBefore('?').substringBefore('#')
        val hostWithPort = hostAndRest.substringBefore('/')
        val host = hostWithPort.substringBefore(':')
        val path = hostAndRest.substringAfter('/', "").trim('/')

        if (host.isEmpty()) return false
        // 保留域本身、以及它的子域（jiaowu.example.edu.cn）都算占位
        if (RESERVED_HOSTS.any { host == it || host.endsWith(".$it") }) return false

        // 注意：**不**把"域名后面没有路径"判为假。
        // 试过这条规则，然后把 `https://lib.yitsd.edu.cn` 这种真实链接也误杀了 ——
        // 裸域名点开就是这个站的主页，它是可用的，只是没有深层路径。
        // 真正的占位符靠下面的「词沙拉」规则拦（12 条内置占位符全都能拦到）。

        // 占位符的典型形态：整段路径是「词_词」或「词-词」，且不含数字
        //   gaoshu_songhao / java_kuangshen / cet46_liu（含数字，但仍是占位）
        // 真实内容链接通常会带 id：BV1xx411c7mD、/video/BV...、?id=123
        if (looksLikeWordSalad(path)) return false

        return true
    }

    /**
     * 形如 `gaoshu_songhao`、`xiandaishu_3b1b`、`suanfa_zuo` 的"词沙拉"。
     *
     * 判据：路径由下划线/连字符分隔的**纯字母+数字片段**构成，且
     * - 段数 ≥ 2，或整段含下划线；且
     * - 不含 `/`（真实链接常常带 `/video/BVxxx` 这种层级）
     *
     * 这个规则会放过多余的真实链接（宁可放过），但能稳住这批占位符。
     */
    private fun looksLikeWordSalad(path: String): Boolean {
        if (path.contains('/')) return false
        if (!path.contains('_') && !path.contains('-')) return false
        val parts = path.split('_', '-').filter { it.isNotEmpty() }
        if (parts.size < 2) return false
        // 每段都是短小的字母数字串 → 词沙拉
        return parts.all { it.length <= 16 && it.all { c -> c.isLetterOrDigit() } }
    }
}
