package com.pocket.xiaoli.data.academic

import com.google.gson.Gson
import com.pocket.xiaoli.data.entity.AcademicSnapshotEntity
import com.pocket.xiaoli.network.AcademicCourseItem
import com.pocket.xiaoli.network.AcademicTypeInfo
import java.time.Instant
import java.time.ZoneId

/**
 * 存进 [AcademicSnapshotEntity] 的 JSON 形状。
 *
 * ## 为什么不让 `AcademicTypeInfo` / `AcademicCourseItem` 直接落库
 *
 * 那两个是**解析层**的模型，字段会随教务页面结构变；存储形状要有自己的名字，
 * 否则解析层改个字段名，老快照就会静默丢值（而"少一个字段"这件事在界面上看不出来）。
 *
 * ⚠️ **每个字段都必须有默认值**：Gson 反序列化时只要有一个字段没有默认值，
 * 这个类就丢掉无参构造，于是 Gson 改走 `UnsafeAllocator` 反射赋值，
 * **所有**默认值一起失效、缺字段读出来是 `null`（`DormInfo` v1.0.93 崩溃的成因）。
 * 守门人：`AcademicSnapshotCodecTest.每个字段都有默认值 —— 少一个就会让整份快照的默认值失效`。
 */
data class AcademicSnapshotDto(
    val types: List<TypeDto> = emptyList(),
    val courses: List<CourseDto> = emptyList(),
) {
    data class TypeDto(
        val name: String = "",
        val id: String = "",
        val parentId: String = "",
        val depth: Int = 0,
        val requiredCredit: String = "",
        val earnedCredit: String = "",
        val missedCredit: String = "",
        val requiredCount: String = "",
        val reachedCount: String = "",
        val missedCount: String = "",
        val totalCourses: String = "",
        val passedCourses: String = "",
    )

    data class CourseDto(
        val typeId: String = "",
        val typeName: String = "",
        val courseName: String = "",
        val credit: String = "",
        val nature: String = "",
        val category: String = "",
        val maxGrade: String = "",
        val score: String = "",
        val gradePoint: String = "",
        val hours: String = "",
        val makeupScore: String = "",
        val retakeScore: String = "",
        val status: String = "",
        val term: String = "",
        val suggestedTerm: String = "",
    )
}

/** 短别名：两个 DTO 嵌在 [AcademicSnapshotDto] 里，逐处写全名会淹没代码意图 */
private typealias TypeDto = AcademicSnapshotDto.TypeDto
private typealias CourseDto = AcademicSnapshotDto.CourseDto

/**
 * 「修课情况」快照的读写（纯逻辑部分）。
 *
 * 与 Room 解耦：这里只负责"模型 ↔ JSON 文本"，DAO 的调用在 `AppContainer` 那一侧。
 * 这样序列化往返可以在**纯 JVM 单测**里验（Room 那半边本仓库没引测试库）。
 */
object AcademicSnapshotCodec {

    /** 快照用的 Gson：字段名按类里的原样，不做任何改名（可读性优先，方便在 DevTools 里看） */
    private val gson = Gson()

    fun encode(
        types: List<AcademicTypeInfo>,
        courses: List<AcademicCourseItem>,
        updatedAt: Long,
    ): AcademicSnapshotEntity = AcademicSnapshotEntity(
        id = AcademicSnapshotEntity.SINGLETON_ID,
        updatedAt = updatedAt,
        typesJson = encodeTypes(types),
        // 类型树与课程明细分两列存：一份 JSON 里混着两种结构，在 DevTools 里根本看不清
        coursesJson = encodeCourses(courses),
    )

    fun encodeTypes(types: List<AcademicTypeInfo>): String =
        gson.toJson(types.map(::toDto))

    fun encodeCourses(courses: List<AcademicCourseItem>): String =
        gson.toJson(courses.map(::toDto))

    /** 从两列 JSON 读回；任一列坏掉都只丢那一半（不让一处脏数据废掉整份快照） */
    fun decode(entity: AcademicSnapshotEntity?): AcademicSnapshot? {
        if (entity == null) return null
        val types = runCatching { gson.fromJson(entity.typesJson, Array<TypeDto>::class.java) }
            .getOrNull().orEmpty().map(::fromDto)
        val courses = runCatching { gson.fromJson(entity.coursesJson, Array<CourseDto>::class.java) }
            .getOrNull().orEmpty().map(::fromDto)
        if (types.isEmpty() && courses.isEmpty()) return null
        return AcademicSnapshot(entity.updatedAt, types, courses)
    }

    /** 仅供测试：直接序列化/反序列化一份 DTO，验"全字段都有默认值"这条不变量 */
    internal fun roundTrip(dto: AcademicSnapshotDto): AcademicSnapshotDto =
        gson.fromJson(gson.toJson(dto), AcademicSnapshotDto::class.java)

    private fun toDto(t: AcademicTypeInfo) = AcademicSnapshotDto.TypeDto(
        name = t.name, id = t.id, parentId = t.parentId, depth = t.depth,
        requiredCredit = t.requiredCredit, earnedCredit = t.earnedCredit,
        missedCredit = t.missedCredit, requiredCount = t.requiredCount,
        reachedCount = t.reachedCount, missedCount = t.missedCount,
        totalCourses = t.totalCourses, passedCourses = t.passedCourses,
    )

    private fun fromDto(d: AcademicSnapshotDto.TypeDto) = AcademicTypeInfo(
        name = d.name, id = d.id, parentId = d.parentId, depth = d.depth,
        requiredCredit = d.requiredCredit, earnedCredit = d.earnedCredit,
        missedCredit = d.missedCredit, requiredCount = d.requiredCount,
        reachedCount = d.reachedCount, missedCount = d.missedCount,
        totalCourses = d.totalCourses, passedCourses = d.passedCourses,
    )

    private fun toDto(c: AcademicCourseItem) = AcademicSnapshotDto.CourseDto(
        typeId = c.typeId, typeName = c.typeName, courseName = c.courseName,
        credit = c.credit, nature = c.nature, category = c.category,
        maxGrade = c.maxGrade, score = c.score, gradePoint = c.gradePoint,
        hours = c.hours, makeupScore = c.makeupScore, retakeScore = c.retakeScore,
        status = c.status, term = c.term, suggestedTerm = c.suggestedTerm,
    )

    private fun fromDto(d: AcademicSnapshotDto.CourseDto) = AcademicCourseItem(
        typeId = d.typeId, typeName = d.typeName, courseName = d.courseName,
        credit = d.credit, nature = d.nature, category = d.category,
        maxGrade = d.maxGrade, score = d.score, gradePoint = d.gradePoint,
        hours = d.hours, makeupScore = d.makeupScore, retakeScore = d.retakeScore,
        status = d.status, term = d.term, suggestedTerm = d.suggestedTerm,
    )
}

/**
 * 一份读回来的修课情况快照。
 *
 * [updatedAt] 会出现在鲤鱼的回答里（"更新于 3 天前"）—— 少了它，
 * 用户会把三个月前的培养方案当成刚查的，而培养方案是**会变**的（学院每学期调要求）。
 */
data class AcademicSnapshot(
    val updatedAt: Long,
    val types: List<AcademicTypeInfo>,
    val courses: List<AcademicCourseItem>,
) {
    /** 「更新于 …」：只给相对时间，不报绝对时刻（后者对用户没意义，还要处理时区） */
    fun updatedLabel(now: Long = System.currentTimeMillis()): String {
        if (updatedAt <= 0L) return ""
        val minutes = (now - updatedAt) / 60_000
        return when {
            minutes < 1 -> "刚更新"
            minutes < 60 -> "${minutes} 分钟前更新"
            minutes < 60 * 24 -> "${minutes / 60} 小时前更新"
            minutes < 60 * 24 * 30 -> "${minutes / (60 * 24)} 天前更新"
            else -> Instant.ofEpochMilli(updatedAt).atZone(ZoneId.systemDefault())
                .toLocalDate().toString() + " 更新"
        }
    }
}
