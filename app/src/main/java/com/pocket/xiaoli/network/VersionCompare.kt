/*
 * 移植自：岱宗盒子 / XJTU Toolbox（https://github.com/yeliqin666/xjtu-toolbox-android）
 * 原文件：app/src/main/java/com/xjtu/toolbox/bulletin/Bulletin.kt:254-312（tag v5.1.1 / efc78be）
 * 原许可：GPL-3.0，与本项目 LICENSE 相同 —— 保留原作者版权声明，本项目同样以 GPL-3.0 发布。
 * 改动：①拆成独立 object；②吃得住 `v` 前缀；③当前版本解析不出数字时一律判为「不是新版」。
 *
 * 为什么需要这份实现（而不是简单比三段整数）：
 *   - 发布 tag 里有预发布后缀（`0.1.41-dev.3`），而 `0.1.41-dev.3` 必须小于 `0.1.41`；
 *   - 老版本号有两段写法（`4.72` 意为 `4.7.2`）；
 *   - 段数不同（`1.2` 与 `1.2.0`）要能相等。
 */
package com.pocket.xiaoli.network

object VersionCompare {

    /**
     * 比较两个版本号：v1 > v2 返回正数，相等返回 0，v1 < v2 返回负数。
     *
     * 规则：
     *   1. 按 `-` 切成「核心版本」与「预发布标识」；
     *   2. 核心版本逐段比整数，缺的段按 0 补（`1.2` == `1.2.0`）；
     *   3. 核心相同则比预发布：有预发布的小（`0.1.41-dev.3` < `0.1.41`）；
     *   4. 两边都有预发布时按 `.` 分段，能转数字的按数字比，否则按字典序。
     */
    fun compare(v1: String, v2: String): Int {
        val s1 = v1.trim()
        val s2 = v2.trim()
        if (s1 == s2) return 0

        val idx1 = s1.indexOf('-')
        val core1 = if (idx1 >= 0) s1.substring(0, idx1) else s1
        val pre1 = if (idx1 >= 0) s1.substring(idx1 + 1) else null

        val idx2 = s2.indexOf('-')
        val core2 = if (idx2 >= 0) s2.substring(0, idx2) else s2
        val pre2 = if (idx2 >= 0) s2.substring(idx2 + 1) else null

        val parts1 = versionParts(core1)
        val parts2 = versionParts(core2)
        val maxLen = maxOf(parts1.size, parts2.size)
        for (i in 0 until maxLen) {
            val p1 = parts1.getOrElse(i) { 0 }
            val p2 = parts2.getOrElse(i) { 0 }
            if (p1 != p2) return p1.compareTo(p2)
        }

        // 核心版本相同，比较预发布标识
        if (pre1 == null && pre2 == null) return 0
        // 有 pre 的比没有 pre 的小（例如 0.1.41-dev.3 < 0.1.41）
        if (pre1 != null && pre2 == null) return -1
        if (pre1 == null && pre2 != null) return 1

        // 两者都有 pre，按 '.' 分段逐段比较
        val segs1 = pre1!!.split('.')
        val segs2 = pre2!!.split('.')
        val minSegs = minOf(segs1.size, segs2.size)
        for (i in 0 until minSegs) {
            val seg1 = segs1[i]
            val seg2 = segs2[i]
            val n1 = seg1.toLongOrNull()
            val n2 = seg2.toLongOrNull()
            if (n1 != null && n2 != null) {
                val cmp = n1.compareTo(n2)
                if (cmp != 0) return cmp
            } else {
                val cmp = seg1.compareTo(seg2)
                if (cmp != 0) return cmp
            }
        }
        return segs1.size.compareTo(segs2.size)
    }

    /**
     * `candidate` 是否比 `current` 新。
     *
     * `current` 解析不出数字时一律返回 false —— 宁可不提示更新，也不要拿一个坏版本号
     * 骗用户去装包（发布侧写错一次就会变成全量误报）。
     */
    fun isNewer(candidate: String, current: String): Boolean {
        if (!looksLikeVersion(current)) return false
        if (!looksLikeVersion(candidate)) return false
        return compare(candidate, current) > 0
    }

    private fun looksLikeVersion(raw: String): Boolean =
        versionParts(raw.substringBefore('-')).isNotEmpty()

    /**
     * 兼容旧写法：`4.72` / `4.71` / `4.61` 分别当成 `4.7.2` / `4.7.1` / `4.6.1`。
     * 已经带第三段的（如 `4.7.3`、`4.5.3`）原样比较。
     *
     * `v` 前缀在切段前去掉 —— 原实现没有这一步，因为它拿到的 tag 已经去过 v。
     */
    private fun versionParts(raw: String): List<Int> {
        val cleaned = raw.trim().removePrefix("v").removePrefix("V")
        val parts = cleaned.split('.').mapNotNull { it.trim().toIntOrNull() }
        if (parts.size == 2 && parts[1] >= 10) {
            return listOf(parts[0], parts[1] / 10, parts[1] % 10)
        }
        return parts
    }
}
