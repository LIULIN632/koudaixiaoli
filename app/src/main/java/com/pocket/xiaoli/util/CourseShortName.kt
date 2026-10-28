package com.pocket.xiaoli.util

import com.pocket.xiaoli.core.CourseShortName as CoreShortName

/**
 * 课程**简称**（课表格子里显示的短名）。
 *
 * ⚠️ 规则与内置表都在共享核心 `com.pocket.xiaoli.core.CourseShortName`
 * （Windows 桌面端同一份）。这里只保留一个转发壳，是为了不惊动那批
 * `CourseShortName.suggest(...)` 调用点；**新增规则请改核心**。
 *
 * 三层来源按优先级：用户手改（`CourseEntity.shortName`）→ 内置表 → 规则兜底。
 * 规则兜底宁可返回 null 也不硬截断（详见核心里的注释）。
 */
object CourseShortName {

    /** 显示目标长度：超过它才考虑给简称。 */
    const val MAX_CHARS = CoreShortName.MAX_CHARS

    /** 人工维护的简称表（键 = 教务里的完整课名）。 */
    val BUILTIN: Map<String, String> = CoreShortName.BUILTIN

    /** 给一门课推荐简称：返回 `null` 表示不需要 / 推不出（调用方应显示原名）。 */
    fun suggest(fullName: String): String? = CoreShortName.suggest(fullName)
}
