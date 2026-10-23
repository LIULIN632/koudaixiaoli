package com.pocket.xiaoli.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.pocket.xiaoli.data.entity.FocusSessionEntity
import kotlinx.coroutines.flow.Flow

/**
 * 番茄钟的历史段。
 *
 * ## 为什么读取用 `>= 某个时刻` 而不是 `BETWEEN 周一 AND 周日`
 *
 * 统计要的窗口（今天 / 本周 / 本月）**会自己往前走**：应用挂着过了午夜，
 * "今天"就换了一天。把窗口算成 SQL 里的固定区间，那个区间就冻在查询发起的那一刻，
 * 午夜之后界面还在显示昨天的数。
 *
 * 所以这里只暴露"从某时刻起"，**窗口在 Kotlin 里按本地日期现算**
 *（`FocusStats` 那几个纯函数），跨天只影响下一次重组，不会给出错的数。
 */
@Dao
interface FocusSessionDao {

    @Insert
    suspend fun insert(entity: FocusSessionEntity): Long

    /** 从 [fromWallMs] 起（含）的所有段，按时间正序。给当日分段图与柱状图用。 */
    @Query("SELECT * FROM focus_sessions WHERE endedAtWallMs >= :fromWallMs ORDER BY endedAtWallMs ASC")
    fun observeSince(fromWallMs: Long): Flow<List<FocusSessionEntity>>

    @Query("SELECT * FROM focus_sessions WHERE endedAtWallMs >= :fromWallMs ORDER BY endedAtWallMs ASC")
    suspend fun sessionsSince(fromWallMs: Long): List<FocusSessionEntity>

    /** 最近一段。用于"接着上次继续"这类提示，以及测试里的落库断言。 */
    @Query("SELECT * FROM focus_sessions ORDER BY endedAtWallMs DESC LIMIT 1")
    suspend fun latest(): FocusSessionEntity?

    /** 「我的 → 一键清空」走这里，与其它表的 clear 保持同一套语义。 */
    @Query("DELETE FROM focus_sessions")
    suspend fun clear()

    /**
     * 每条待办身上"已经完整跑完几个番茄"，按待办 id 聚合。首页待办行显示这个数。
     *
     * ## 为什么不往 `tasks` 表加一个计数字段
     *
     * 那等于**同一件事存两份**：`focus_sessions` 里明明记着每一段属于谁，
     * `tasks.focus_count` 只是它的投影。两份状态一定会漂（清空历史、删掉某段、
     * 迁移写坏），还要多一次 Room 迁移（19→20）。"能算出来的就别存"与
     * `memory/01-constraints.md` 里「学号脱敏只有一份实现」是同一条规矩。
     *
     * ⚠️ 只数 `completed = 1`：跳过的、放弃的都不给分 —— 与 `FocusStats` 里
     * 「番茄数只数跑满的」是同一个口径，两处必须一起变。
     */
    @Query(
        "SELECT linkedTaskId AS taskId, COUNT(*) AS count FROM focus_sessions " +
            "WHERE completed = 1 AND linkedTaskId IS NOT NULL GROUP BY linkedTaskId"
    )
    fun observeCompletedByTask(): Flow<List<TaskFocusCount>>
}

/** [FocusSessionDao.observeCompletedByTask] 的一行。 */
data class TaskFocusCount(
    val taskId: Long,
    val count: Int,
)
