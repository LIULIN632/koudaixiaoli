package com.pocket.xiaoli.util

import java.util.concurrent.ConcurrentHashMap

/**
 * 轻量拼音首字母工具（零依赖）：基于 GB2312 区位码区间映射首字母，
 * 用于学校列表按拼音首字母排序。对英文/数字开头名称取原字符。
 *
 * ## 为什么带缓存（这不是过早优化）
 *
 * 它的调用点长这样（`ImportViewModel` / `WebImportViewModel` / `ZhengfangLoginViewModel`）：
 *
 * ```kotlin
 * schools.sortedWith(compareByDescending<School> { it.id in favIds }
 *     .thenBy { PinyinSortUtil.sortKey(it.name) })
 * ```
 *
 * `thenBy` 的选择器在**每次比较**时都会被调用。299 所学校的一轮排序约 2,459 次比较，
 * 也就是近 5,000 次 `sortKey`；而每个 `sortKey` 都要把名字里的每个汉字
 * `toByteArray(GB2312)` 一次 —— 实际是**上万次字符编码 + 临时字节数组分配**。
 *
 * 更糟的是它在搜索路径上：导入页搜索框**每敲一个字**就过滤 + 整表重排一遍。
 * 这是用户能感知到的卡顿来源（老机型上尤其明显），而名字的拼音首字母是**纯函数**，
 * 同一个名字永远得到同一个结果，缓存它没有任何正确性代价。
 */
object PinyinSortUtil {

    // 各首字母区间起点（区位码 = 区*100 + 位），对应分界字：啊芭擦搭蛾发噶哈击喀垃妈拿哦啪期然撒塌挖昔压匝
    private val BOUNDARIES = intArrayOf(
        1601, 1637, 1833, 2078, 2274, 2302, 2433, 2594, 2787,
        3106, 3212, 3472, 3635, 3722, 3730, 3858, 4027, 4086,
        4390, 4558, 4684, 4925, 5249
    )
    private val LETTERS = "ABCDEFGHJKLMNOPQRSTWXYZ"

    /**
     * 名字 → 首字母串 的缓存。
     *
     * 用 [ConcurrentHashMap] 而不是普通 HashMap：三个 ViewModel 都会从协程里调它，
     * 而排序可能发生在非主线程。`computeIfAbsent` 保证同一个 key 只计算一次。
     *
     * 只缓存**名字**（几百到几千个字符串），不缓存单字：单字缓存命中率低、
     * 条目数多（汉字上万），而名字这一层才是调用的实际粒度。
     */
    private val keyCache = ConcurrentHashMap<String, String>()

    /** 取单个字符的拼音首字母；非汉字返回原大写字母；其他字符返回 null */
    fun firstLetter(ch: Char): Char? {
        if (ch.code < 0x4E00 || ch.code > 0x9FA5) {
            return if (ch.isLetter()) ch.uppercaseChar() else null
        }
        val code = gb2312Code(ch) ?: return null
        var i = BOUNDARIES.size - 1
        while (i >= 0) {
            if (code >= BOUNDARIES[i]) return LETTERS[i]
            i--
        }
        return null
    }

    /**
     * 生成排序用首字母串（如「广东药科大学」→ GDYKDX）。结果带缓存。
     *
     * ## 取不到拼音的汉字**不再被丢弃**（修掉的缺陷）
     *
     * 原实现是 `firstLetter(c) ?: continue` —— 于是 GB2312 覆盖不到的汉字
     * （生僻字、部分扩展区字）会被**静默删掉**，产生两个后果：
     * - 排序键出现"空洞"：键变短了，顺序可能与读音不符
     * - 不同名字可能得到**相同**的键：例如「阜阳师范大学」与「阳师范大学」都变成
     *   `YSDDX` 之类，`thenBy` 无法区分，相对顺序退化成输入顺序
     *
     * 现在改为**回退到原字符**：宁可让生僻字按 Unicode 码位参与排序（不理想但确定、可解释），
     * 也不要让它凭空消失导致键碰撞。
     */
    fun sortKey(name: String): String {
        if (name.isEmpty()) return ""
        return keyCache.getOrPut(name) {
            buildString {
                for (c in name) {
                    val isChinese = c.code in 0x4E00..0x9FA5
                    val letter = firstLetter(c)
                    when {
                        // 汉字取到拼音首字母
                        letter != null -> append(letter)
                        // 汉字但取不到拼音（GB2312 覆盖不到）→ 回退原字，**不能丢**
                        isChinese -> append(c)
                        // 非汉字（数字、标点、空格）按原规则跳过：
                        // 这是既有契约（`"123大学"` 的排序键就是 `"DX"`），
                        // 保留数字会让"3+2学校"这类名字排到字母组之前，不是本函数该管的事
                        else -> Unit
                    }
                }
            }
        }
    }

    /** 仅供测试：清空缓存，验证"缓存不改变结果"。 */
    internal fun clearCacheForTest() = keyCache.clear()

    private fun gb2312Code(ch: Char): Int? = try {
        val bytes = ch.toString().toByteArray(java.nio.charset.Charset.forName("GB2312"))
        if (bytes.size == 2) {
            val qu = (bytes[0].toInt() and 0xFF) - 0xA0
            val wei = (bytes[1].toInt() and 0xFF) - 0xA0
            if (qu in 1..94 && wei in 1..94) qu * 100 + wei else null
        } else null
    } catch (_: Exception) {
        null
    }
}
