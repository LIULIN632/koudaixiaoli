package com.pocket.xiaoli.data.contribution

/**
 * 校园共建榜的客户端口径。
 *
 * ## 谁是权威
 *
 * **服务端**（`server/.../ContributionRules.kt`）才是权威：分值、每日上限、昵称规则
 * 都在那边判。这里的 [ContributionAction] 只是为了让界面能立刻说清「这次加几分、
 * 今天还能加几次」，以及减少无意义的请求。客户端改这里**不会**多拿分 ——
 * 服务端按动作名查自己的表，请求里根本没有分数字段。
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
    val points: Int,
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

data class ContributionEntry(
    val rank: Int,
    val nickname: String,
    val points: Int,
    val mine: Boolean = false,
)

data class ContributionMe(val rank: Int, val points: Int, val nickname: String)

/** 一屏榜单数据。`fromCache` 表示这是上次成功拉到的离线副本。 */
data class ContributionBoard(
    val category: ContributionCategory,
    val period: ContributionPeriod,
    val entries: List<ContributionEntry>,
    val me: ContributionMe?,
    val fetchedAt: Long,
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
