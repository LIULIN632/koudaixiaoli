package com.pocket.xiaoli.data.entity

import androidx.compose.runtime.Immutable
import androidx.room.Entity
import androidx.room.PrimaryKey
import com.pocket.xiaoli.util.CourseColorIndex
import com.pocket.xiaoli.util.CourseShortName

@Immutable
@Entity(tableName = "courses")
data class CourseEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val credits: String = "",
    val teacher: String = "",
    val room: String = "",
    val dayOfWeek: Int = 1,       // 1-7 (Mon-Sun)
    val startSlot: Int = 1,       // 1-12
    val endSlot: Int = 2,         // 1-12
    val weekType: String = "all", // all, odd, even
    val weeks: String = "",       // comma-separated week numbers, empty = all
    val colorIndex: Int = 0,      // 0-14 palette index（自动分配的占位值 / 用户钉住的值，见 colorPinned）
    val schemeId: Long = 0,       // 0=default scheme

    /**
     * 课表格子里显示的**简称**（可空 = 用 [name] 全名）。
     *
     * 三层来源见 [CourseShortName]：用户手改 > 内置表 > 规则兜底。
     * ⚠️ 只有**格子**用简称；详情弹窗 / 导出图片 / 搜索结果一律显示 [name] 全名 ——
     * 简称是"放不下时的退让"，不是"这门课的正式名字"。
     */
    val shortName: String = "",

    /**
     * 用户是否**手动指定过**颜色。
     *
     * `false`（默认）= 颜色由**课名**派生（[autoColorIndex]）：同一门课在任何一天、任何一节都同色。
     * `true` = 用户在课程编辑器里点过调色板，从此不再自动改。
     *
     * ⚠️ 判定"自动还是手选"没法从 [colorIndex] 本身看出来 —— 改造前所有解析器都写
     * `(day * 3 + startSlot) % 15`，而用户手选的颜色也可能**恰好**等于那个值。
     * 所以迁移 `MIGRATION_16_17` 用"像不像位置值"来猜（猜错的最坏后果只是颜色被重算一次，
     * 不是丢数据），此后新数据一律靠这一列区分。
     */
    val colorPinned: Boolean = false,

    /**
     * 备注。日程类条目存用户写的那句话；课程类默认空串。
     * 照岱宗 `CustomCourseEntity.note` 设计。
     */
    val note: String = "",

    /**
     * 分钟级开始时间，单位：距 00:00 的分钟。`-1` = 用 [startSlot] 推导
     * （照岱宗 `CustomCourseEntity.startMinuteOfDay`）。
     */
    val startMinute: Int = -1,

    /** 分钟级结束时间，单位：距 00:00 的分钟。`-1` = 用 [endSlot] 推导。 */
    val endMinute: Int = -1,

    /**
     * 条目类型： [KIND_COURSE] = 教务/用户添加的课；[KIND_AGENDA] = 用户自建日程。
     * 照岱宗 `CustomCourseEntity.courseType`（"日程" / "自定义"）设计。
     */
    val kind: String = KIND_COURSE,
) {
    /** 是否是用户自建日程（不是课）。 */
    val isAgenda: Boolean get() = kind == KIND_AGENDA

    /** 按课名派生的**稳定**颜色下标（0–14）：同一门课永远同色。 */
    val autoColorIndex: Int get() = CourseColorIndex.forCourseName(name)

    /**
     * **实际生效**的课程颜色下标。
     *
     * ⚠️ 所有取色的地方（课表格子、导出图片、合并判断、编辑器初值）都必须走这里，
     * 不要直接读 [colorIndex] —— 自动分配的那个值只是占位，直接读就会出现
     * "周二粉色、周五橙色"（2026-10 用户实际报的 P0）。
     */
    val effectiveColorIndex: Int get() = if (colorPinned) colorIndex else autoColorIndex

    /** 格子里显示的名字：有简称用简称，否则全名。 */
    val displayName: String get() = shortName.ifBlank { name }

    /** 给这门课补上默认简称（导入 / 迁移时用）：用户已填过就不动。 */
    fun withSuggestedShortName(): CourseEntity =
        if (shortName.isNotBlank()) this else copy(shortName = CourseShortName.suggest(name).orEmpty())

    companion object {
        const val KIND_COURSE = "course"
        const val KIND_AGENDA = "agenda"
    }
}

@Immutable
@Entity(tableName = "time_slots")
data class TimeSlotEntity(
    @PrimaryKey val slot: Int,    // 1-12
    val startTime: String,        // "08:00"
    val endTime: String           // "08:45"
)
