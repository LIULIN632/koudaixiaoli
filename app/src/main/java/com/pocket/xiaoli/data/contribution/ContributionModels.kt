package com.pocket.xiaoli.data.contribution

/**
 * 校园共建榜的客户端口径。
 *
 * ## 谁是权威
 *
 * **服务端**（`server/.../ContributionRules.kt`）才是权威：动作、每日上限、昵称规则
 * 都在那边判。这里的 [ContributionAction] 只是为了让界面能说清「哪些动作会被记上一次」，
 * 以及减少无意义的请求。客户端改这里**不会**多拿分 —— 服务端按动作名查自己的表，
 * 请求里根本没有分数字段。
 *
 * ## 2026-10-24：积分制下线，榜单只按昵称列人
 *
 * 用户要求“积分制先去掉，按名字排序”。所以：
 * - 榜单**不展示分数、不展示名次**，列表按昵称排序（中文按拼音，见 [ContributionOrder]）；
 * - 服务端仍然把每条动作的分值写进 `contribution_events`（历史数据不丢、以后要恢复也不用迁移），
 *   但**不再汇总、不再返回**；
 * - [ContributionAction.points] 因此只剩“服务端历史账本里记多少”这一个用途，界面不许读它。
 *
 * ## 为什么分成这几个榜
 *
 * 学生能贡献的东西不一样：有人会整份课表导入，有人只顺手补一间教室，有人写笔记。
 * 全塞进一个总分会变成"谁用得久谁第一"；分类之后，每一类都能看到具体的贡献面。
 */
enum class ContributionCategory(val wire: String, val label: String) {
    ALL("all", "总榜"),
    TIMETABLE("timetable", "课程表"),
    CAMPUS("campus", "教室与地图"),
    NOTES("notes", "资料笔记"),
    FEEDBACK("feedback", "反馈纠错"),
    ;

    companion object {
        fun fromWire(raw: String?): ContributionCategory =
            entries.firstOrNull { it.wire == raw } ?: ALL
    }
}

enum class ContributionPeriod(val wire: String, val label: String) {
    /** 含今天在内的最近 7 天。与服务端 `ContributionRules.since` 同口径。 */
    WEEK("week", "本周"),
    ALL("all", "全部"),
    ;

    companion object {
        fun fromWire(raw: String?): ContributionPeriod =
            entries.firstOrNull { it.wire == raw } ?: WEEK
    }
}

enum class ContributionAction(
    val wire: String,
    val category: ContributionCategory,
    /** **仅服务端历史账本用**。2026-10-24 起积分不再排序/展示，界面不要读这个字段。 */
    val points: Int,
    /** 本机预检 + 服务端共用的每日次数上限，避免重复动作白跑一趟网络。 */
    val dailyCap: Int,
) {
    COURSE_IMPORT("course_import", ContributionCategory.TIMETABLE, 10, 1),
    ROOM_CELL_ADD("room_cell_add", ContributionCategory.CAMPUS, 5, 5),
    ROOM_CELL_EDIT("room_cell_edit", ContributionCategory.CAMPUS, 2, 10),
    NOTE_SAVE("note_save", ContributionCategory.NOTES, 5, 3),
    FEEDBACK_SEND("feedback_send", ContributionCategory.FEEDBACK, 5, 1),
    ;

    companion object {
        fun fromWire(raw: String?): ContributionAction? = entries.firstOrNull { it.wire == raw }
    }
}

/**
 * 榜单上的一行。
 *
 * [rank] 是**按昵称排序后的序号**（1 起），只用来做列表 key —— 它不是分数排名，
 * 界面也不显示它。字段名保留是因为服务端的历史协议里就叫 rank。
 */
data class ContributionEntry(
    val rank: Int,
    val nickname: String,
    val mine: Boolean = false,
)

/** 「我」在不在榜上。只要非 null 就说明这一期/这一类有记录；不携带名次与分数。 */
data class ContributionMe(val nickname: String)

/**
 * 榜单排序：**按昵称**。
 *
 * 用 `Collator(Locale.CHINA)` 而不是直接比字符串：中文按拼音（"安"在"白"前），
 * 直接比码点会排成"按 Unicode 块"的顺序，用户看着就是乱的。
 *
 * 客户端自己也排一次，而不是只信服务端顺序：
 * - 升级前缓存的榜单是按分数排的，离线打开时不能还是旧顺序；
 * - 服务端没来得及重新部署时，至少客户端这边先按名字显示。
 *
 * 排序后**重排 [ContributionEntry.rank]**，保证列表 key 与显示顺序一致。
 */
object ContributionOrder {

    fun sortByName(entries: List<ContributionEntry>): List<ContributionEntry> {
        val collator = java.text.Collator.getInstance(java.util.Locale.CHINA)
        return entries
            .sortedWith { a, b -> collator.compare(a.nickname, b.nickname) }
            .mapIndexed { index, entry -> entry.copy(rank = index + 1) }
    }
}

/** 一屏榜单数据。`fromCache` 表示这是上次成功拉到的离线副本。 */
data class ContributionBoard(
    val category: ContributionCategory,
    val period: ContributionPeriod,
    val entries: List<ContributionEntry>,
    val me: ContributionMe?,
    val fetchedAt: Long,
    /**
     * 参与总人数（服务端不受 `limit` 截断的计数）。
     *
     * 0 = 旧服务端 / 旧缓存没带这个字段 —— 界面用 [entries] 的长度兜底，
     * 绝不要直接把 0 当成"没人"。
     */
    val total: Int = 0,
    val fromCache: Boolean = false,
)

data class ContributionUiState(
    val enabled: Boolean = false,
    val nickname: String = "",
    /** 服务端地址或 key 没配时为 false：这时连「加入」都不该出现。 */
    val configured: Boolean = false,
    val category: ContributionCategory = ContributionCategory.ALL,
    val period: ContributionPeriod = ContributionPeriod.WEEK,
    val board: ContributionBoard? = null,
    val loading: Boolean = false,
    val error: String? = null,
)

/**
 * 昵称清洗。规则与服务端一致（1–12 个码点、无控制字符、不冒用官方身份），
 * 但**只用于即时提示**；真正落库前服务端会再判一次。
 */
object ContributionNickname {

    const val MAX = 12

    private val RESERVED = setOf(
        "admin", "administrator", "system", "official",
        "管理员", "官方", "客服", "口袋小鲤", "小鲤",
    )

    fun normalize(raw: String?): String? {
        val trimmed = raw?.trim().orEmpty()
        if (trimmed.isEmpty()) return null
        if (trimmed.codePointCount(0, trimmed.length) > MAX) return null
        if (trimmed.any { it.isISOControl() || it == '\u200B' || it == '\uFEFF' }) return null
        if (trimmed.lowercase() in RESERVED) return null
        return trimmed
    }

    fun errorText(raw: String?): String? = when {
        raw.isNullOrBlank() -> "先起一个昵称"
        normalize(raw) == null -> "昵称 1–12 个字，不能含换行或冒用官方身份"
        else -> null
    }
}
