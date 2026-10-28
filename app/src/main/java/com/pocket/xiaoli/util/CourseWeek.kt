package com.pocket.xiaoli.util

import com.pocket.xiaoli.core.isCourseInWeek as coreIsCourseInWeek
import com.pocket.xiaoli.data.entity.CourseEntity

/**
 * 判断课程是否在第 [week] 周出现。
 *
 * ⚠️ 规则本体在共享核心 `com.pocket.xiaoli.core.isCourseInWeek`（Windows 桌面端同一份），
 * 这里只是把 `CourseEntity` 的字段喂进去。**不要再往这里加判断逻辑** —— 加在这里就等于
 * 又造了一份真源，桌面端会看不到。
 *
 * 抽成公共函数是因为「首页下一节课」和「教室分布图·今日课程」都要用同一套判断。
 */
fun isCourseInWeek(course: CourseEntity, week: Int): Boolean =
    coreIsCourseInWeek(course.weekType, course.weeks, week)
