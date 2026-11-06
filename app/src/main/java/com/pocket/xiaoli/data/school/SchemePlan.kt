package com.pocket.xiaoli.data.school

/**
 * 换学校时**课表方案该怎么走**的判据（纯函数）。
 *
 * ## 为什么单独抽出来
 *
 * `SchemeEntity` 里没有学校字段，导入是按 `课程名 + 星期 + 节次` 合并进**当前方案**的。
 * 所以"换学校"这一步如果什么都不做，两所学校的课表会交错进同一张表 ——
 * 不是覆盖，也不是并存，而是周三上午同时出现两门不同的课。
 * 这个后果在编译期、在页面上、在快照里都看不出来，只有把判据抽成纯函数才能被单测钉住。
 *
 * 判据本身很简单：**课表跟着学校走，一所学校一个方案**。
 * 麻烦的是别制造垃圾方案 —— 装机后第一次选学校时当前方案是空的，
 * 那时应该改名沿用，而不是新建一个空的"默认课表"留在列表里让人清理。
 */
internal sealed interface SchemePlan {
    /** 已经在以这所学校命名的方案里，什么都不用做。 */
    data object Keep : SchemePlan

    /** 当前方案还没有课表：直接改名沿用（不新建，也不留下一个空的"默认课表"）。 */
    data class Rename(val schemeId: Long) : SchemePlan

    /** 之前已经为这所学校建过方案：切回去，不重复建。 */
    data class Reuse(val schemeId: Long) : SchemePlan

    /** 当前方案有别的学校的课表：新建一个以学校命名的方案，旧的原样留着。 */
    data object Create : SchemePlan
}

/**
 * @param activeSchemeId 当前方案 id
 * @param activeSchemeName 当前方案名
 * @param activeHasCourses 当前方案里有没有课表数据
 * @param existingByName 按名字建过索引的既有方案（名字 → id）
 * @param targetSchoolName 要切去的那所学校的名字
 */
internal fun schemePlanFor(
    activeSchemeId: Long,
    activeSchemeName: String,
    activeHasCourses: Boolean,
    existingByName: Map<String, Long>,
    targetSchoolName: String,
): SchemePlan {
    if (activeSchemeName == targetSchoolName) return SchemePlan.Keep
    if (!activeHasCourses) return SchemePlan.Rename(activeSchemeId)
    val existing = existingByName[targetSchoolName]
    return if (existing != null) SchemePlan.Reuse(existing) else SchemePlan.Create
}
