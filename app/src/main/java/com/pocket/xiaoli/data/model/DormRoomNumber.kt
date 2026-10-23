package com.pocket.xiaoli.data.model

/**
 * 宿舍房间号的**构成**与**等价写法**（唯一真源）。
 *
 * ## 两种形态
 *
 * | 校区 | 形态 | 例子 | 含义 |
 * |---|---|---|---|
 * | 烟理本校区（1-6、13 号楼） | `楼层 + 两位房号` | `412` | 4 楼 12 间 |
 * | 烟大南校（7-12 号楼） | `侧翼 + 楼层 + 两位房号` | `n602` / `s1602` | 北侧 6 楼 02 间 / 南侧 16 楼 02 间 |
 *
 * 侧翼字母取自英文 north / south（`n` = 北、`s` = 南）—— 2026-10 由用户确认，
 * **不要照拼音猜**（拼音里"北"是 b，与 s 对不上，猜反等于把南边写成北边）。
 *
 * ## 为什么它必须是一个共用的纯对象
 *
 * 房号有两处消费者，而它们必须对同一个字符串有同一套理解：
 *
 * 1. **表单**（`DormBindingForm`）—— 把三个转盘拼成存进 `DormInfo.room` 的字符串；
 * 2. **电费匹配**（`DormElectricityRepository`）—— 拿这个字符串去缴费系统的账户地址里认人。
 *
 * ⚠️ 缴费系统的地址是**后勤手写**的，同一间房见过三种写法：
 * `北校10楼南602`（带中文方位）、`南校区10栋602`（不带方位）、`北校10楼南1602`。
 * 所以匹配不能只认一种形态 —— [matchForms] 把一份房号展开成**所有等价写法**，由匹配方逐个试。
 */
object DormRoomNumber {

    /** 侧翼：存储用前缀 → 转盘里显示的中文。顺序就是转盘顺序（北在上）。 */
    val SIDES: List<Pair<String, String>> = listOf("n" to "北侧", "s" to "南侧")

    /** 楼层与房号范围。⚠️ 是**保守超集**：各楼真实层数没有采集过（见 `docs/CampusMapFieldSurvey.md`），
     *  给足范围比给窄了导致"我住的房间选不到"安全得多。 */
    const val MIN_FLOOR = 1
    const val MAX_FLOOR = 18
    const val MIN_ROOM = 1
    const val MAX_ROOM = 40

    val floors: List<Int> = (MIN_FLOOR..MAX_FLOOR).toList()
    val rooms: List<Int> = (MIN_ROOM..MAX_ROOM).toList()

    /** 楼层的显示文案：`4` → `4 楼` */
    fun floorLabel(floor: Int): String = "$floor 楼"

    /** 房号的显示文案：`1` → `01 间` */
    fun roomLabel(room: Int): String = "%02d 间".format(room)

    /** 侧翼的显示文案：`n` → `北侧`；未知/空 → `—`。 */
    fun sideLabel(side: String?): String =
        SIDES.firstOrNull { it.first == side?.lowercase() }?.second ?: "—"

    /** 一份解析好的房号。`side == null` = 本校区那种不带侧翼的写法。 */
    data class Parsed(val side: String?, val floor: Int, val room: Int)

    /**
     * 拼出存进 [DormInfo.room] 的字符串。
     *
     * `compose(null, 4, 12)` → `412`；`compose("n", 6, 2)` → `n602`。
     */
    fun compose(side: String?, floor: Int, room: Int): String {
        val digits = "%d%02d".format(floor, room)
        val prefix = side?.lowercase()?.takeIf { it.isNotBlank() } ?: return digits
        return prefix + digits
    }

    /**
     * 反解一份房号（表单要用它把三个转盘拨到已有值上）。
     *
     * 认三种输入：`n602` / `N602`（大小写都认）、`北602` / `南602`（中文方位，老数据或手输可能长这样）、
     * `412` / `1840`（本校区形态）。
     *
     * ⚠️ 解析不出来时返回 **null 而不是瞎猜**（例如老数据里那种 `A114`）：
     * 调用方要保持原值不动，而不是把它改成一个看似合理、其实错误的房号。
     * 与本项目其它"读不到就别编"的地方（`BindState.UNKNOWN`、`formatImportTime`）同一条原则。
     */
    fun parse(raw: String): Parsed? {
        val text = raw.trim()
        if (text.isEmpty()) return null
        val lower = text.lowercase()

        val side = when {
            lower.startsWith("n") || text.startsWith("北") -> "n"
            lower.startsWith("s") || text.startsWith("南") -> "s"
            else -> null
        }
        // ⚠️ 除了开头那个侧翼字母，**不允许再出现任何非数字字符**：
        // 老数据里见过 `A114`（缴费页的地址写法），若放任它按"取末两位当房号"解析，
        // 会得到"1 楼 14 间"这种**看起来合理、其实错误**的房号 —— 用户不动转盘直接保存，
        // 存进去的就是一间不存在的房。宁可返回 null 让调用方保留原值。
        // （`it.isDigit()` 会认全角数字，所以这里只认 ASCII 数字。）
        val body = text.drop(if (side == null) 0 else 1)
        if (body.any { it !in '0'..'9' }) return null
        if (body.length < 3) return null
        val room = body.takeLast(2).toIntOrNull() ?: return null
        val floor = body.dropLast(2).toIntOrNull() ?: return null
        if (floor !in MIN_FLOOR..MAX_FLOOR || room !in MIN_ROOM..MAX_ROOM) return null
        return Parsed(side, floor, room)
    }

    /**
     * 把一份房号展开成**所有等价写法**，给电费地址匹配逐个试。
     *
     * | 输入 | 输出 |
     * |---|---|
     * | `412` | `412` |
     * | `n602` | `N602`、`北602`、`602` |
     * | `s1602` | `S1602`、`南1602`、`1602` |
     * | `A114`（解析不出） | `A114` |
     *
     * ⚠️ 末尾那个**纯数字形态是刻意保留的兜底**：缴费地址里确实存在不带方位的写法
     *（`南校区10栋602`），只认带方位的形态会让这类账户永远匹配不上。
     * 精度损失由调用方的数字边界匹配兜住（`602` 不会命中 `1602`）。
     */
    fun matchForms(raw: String): List<String> {
        val text = raw.trim()
        if (text.isEmpty()) return emptyList()
        val parsed = parse(text) ?: return listOf(text.uppercase())
        val digits = text.filter { it in '0'..'9' }
        if (parsed.side == null) return listOf(digits)
        val cn = if (parsed.side == "n") "北" else "南"
        return listOf(parsed.side.uppercase() + digits, cn + digits, digits)
    }
}
