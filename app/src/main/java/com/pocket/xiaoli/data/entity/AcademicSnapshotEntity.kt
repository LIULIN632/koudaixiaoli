package com.pocket.xiaoli.data.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * 「修课情况」（培养方案进度）的**整份快照**。
 *
 * ## 为什么是一行 JSON，而不是两张结构化表
 *
 * 这份数据的用法是**整体替换 + 整体读取**：教务那边每次查都返回全量，
 * 我们只需要"最近一次看到的样子"（给鲤鱼答"还差多少学分 / 哪些课没修"）。
 * 拆成 `academic_types` + `academic_courses` 两张表能多做的事（按类型联表查），
 * 在 Kotlin 里对 `List` 过滤一次就够，而代价是两张表 + 两条迁移 ——
 * 本仓库**没有引 `room-testing`**，迁移在单测里跑不起来，
 * 所以这里的取舍是：**把不可测的部分压到最薄**（同 `MIGRATION_16_17` 的思路）。
 *
 * ⚠️ `typesJson` / `coursesJson` 是 Gson 序列化的 `AcademicSnapshotDto`，
 * 那个类的**每个字段都必须有默认值**（同 `DormInfo` 的教训：漏一个就丢无参构造，
 * 于是所有默认值一起失效）。守门人是 `AcademicSnapshotDtoTest` 里那条无参构造断言。
 *
 * `id` 恒为 [SINGLETON_ID]：单行表，不存在第二条。
 */
@Entity(tableName = "academic_snapshot")
data class AcademicSnapshotEntity(
    @PrimaryKey val id: Int = SINGLETON_ID,
    /** 快照时间：鲤鱼回答里要写"更新于…"，用户才知道这是不是刚查的 */
    val updatedAt: Long = 0,
    /** 培养方案的类型树（含各级要求/已获/未获得学分、门次） */
    val typesJson: String = "",
    /** 各类型的课程明细（含成绩/学时/补考/重修/建议修读学年学期） */
    val coursesJson: String = "",
) {
    companion object {
        const val SINGLETON_ID = 1
    }
}
