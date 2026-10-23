package com.pocket.xiaoli.data.entity

import androidx.compose.runtime.Immutable
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.pocket.xiaoli.data.exam.ExamSchedule

/**
 * 一条已确认的考试安排。
 *
 * ## 为什么要独立建表，而不是塞进 tasks / homework
 *
 * - `TaskEntity` 只有 `name`，放不下日期/时间/考场/座位
 * - `HomeworkEntity` 语义是"作业"，混进考试会让首页的计数与文案都变得含糊
 *   （"3 项待完成"里到底有没有考试？）
 *
 * 考试有自己的字段结构（日期/时间/考场/座位），且**用户录入路径不同**
 * （来自通知文件解析，不是手输），独立成表最清晰。
 *
 * ## 与 [ExamSchedule] 的关系
 *
 * [ExamSchedule] 是**解析结果**（纯数据，无 id、无完成态）；
 * 本实体是**落库后**的形态（有 id、完成态、所属课表）。
 * 两者刻意分开：解析器不该知道数据库的存在，这样才能纯函数单测。
 *
 * ## 索引为什么是 `(schemeId, date, time)`
 *
 * 唯一的查询路径是 `ExamDao.getByScheme`：
 *
 * ```sql
 * SELECT * FROM exams WHERE schemeId = :schemeId ORDER BY date, time
 * ```
 *
 * 索引顺序**照抄 WHERE + ORDER BY**：等值列在前、排序列在后，这样 SQLite 可以
 * 直接顺着索引取有序结果，省掉一次临时 B 树排序。
 *
 * ⚠️ 历史遗留：`MIGRATION_14_15` 当初建的索引是 `(schemeId, completed, date)`，
 * `completed` 夹在中间 —— 这条索引对上面那句查询**用不上**（跳过中间列就断了前缀匹配），
 * 且**没有任何查询过滤 `completed`**（完成态是在内存里筛的）。所以 `MIGRATION_15_16`
 * 把它换成现在这个。实体与迁移必须一致：只写迁移不写实体，
 * Room 的预期 schema 里就没有索引，运行时校验对不上（Room 只警告、不崩，但会一直脏）。
 */
@Immutable
@Entity(
    tableName = "exams",
    indices = [Index(value = ["schemeId", "date", "time"])],
)
data class ExamEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val courseName: String,
    /** 日期原文，如 "2026-01-15" / "1月15日" */
    val date: String = "",
    /** 时间原文，如 "14:00-16:00" */
    val time: String = "",
    val location: String = "",
    val seat: String = "",
    val completed: Boolean = false,
    val schemeId: Long = 0,
) {
    /** 首页待办里显示的一行摘要 */
    val summary: String
        get() = listOf(date, time, location)
            .filter { it.isNotBlank() }
            .joinToString("  ")
            .ifBlank { "时间地点见通知" }
}

/** 由解析结果批量构造（供界面确认后一次写入）。 */
fun List<ExamSchedule>.toExamEntities(schemeId: Long): List<ExamEntity> = map { s ->
    ExamEntity(
        courseName = s.courseName,
        date = s.date,
        time = s.time,
        location = s.location,
        seat = s.seat,
        completed = false,
        schemeId = schemeId,
    )
}
