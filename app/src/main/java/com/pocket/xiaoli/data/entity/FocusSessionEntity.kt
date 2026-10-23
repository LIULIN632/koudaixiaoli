package com.pocket.xiaoli.data.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * 一段**已经结束**的番茄钟（专注段或休息段）。
 *
 * ## 为什么它只记"结束了的"，不记"正在跑的"
 *
 * 正在跑的那一段由 `FocusStore` 的 SharedPreferences 快照持有，它是**唯一真源**
 *（理由写在 `FocusStore` 的 KDoc 里）。这张表是**已结算的历史**：
 * 统计图表、今日番茄数都从它读，而它的写入只发生在"这一段尘埃落定"的那一刻。
 *
 * 好处是**不会有一条永远停在 `completed = 0` 的僵尸行** ——
 * 那种行要么让统计把一次没做完的专注算成做完了，要么得再养一个"清理僵尸"的后台任务。
 *
 * ## 休息段也存
 *
 * 只记专注段的话，"今天专注了多久"够用，但"这一段是怎么排的"就没了 ——
 * 当日分段图要按时间轴还原"几点到几点在专注、几点在休息"，缺了休息段就画不出间隔。
 * 所以两种段都落库，靠 [phase] 区分；统计时光过滤 [phase] 即可。
 *
 * 索引 `endedAtWallMs` 服务两个查询：取最近若干天（`>=`）与取最后一段（`ORDER BY … DESC LIMIT 1`）。
 */
@Entity(
    tableName = "focus_sessions",
    indices = [Index(value = ["endedAtWallMs"])],
)
data class FocusSessionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    /** [com.pocket.xiaoli.focus.FocusPhase] 的名字。存字符串而不是 ordinal：加一个阶段不该让旧数据错位。 */
    val phase: String,
    /** 本会话第几个专注段，从 1 起。休息段沿用起它的那一轮，用于"第几轮"这类分组统计。 */
    val roundIndex: Int = 0,
    /** 计划时长（毫秒）。 */
    val plannedMs: Long = 0,
    /**
     * 实际跑了多久（毫秒）。**中途放弃的段 `actualMs < plannedMs`**，
     * 所以按它求和才是"真的专注了多久"，不能拿 `plannedMs × 条数` 顶替。
     */
    val actualMs: Long = 0,
    /** 起止都用墙钟：历史记录要显示"今天 14:03"，而单调钟跨重启没有可比性。 */
    val startedAtWallMs: Long = 0,
    val endedAtWallMs: Long = 0,
    /**
     * 是不是**自然走完**的（用户没有中途放弃）。
     *
     * 番茄数的口径是"只数自然走完的专注段" —— 放弃也给分的话，
     * 起钟 30 秒再放弃一次就能刷一个番茄，这个数就没有意义了。
     */
    val completed: Boolean = false,
    /** 关联的待办 id；非空且本段是**跑完的专注段**时，回写它的番茄计数。 */
    val linkedTaskId: Long? = null,
)
