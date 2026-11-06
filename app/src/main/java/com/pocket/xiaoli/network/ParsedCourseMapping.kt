package com.pocket.xiaoli.network

import com.pocket.xiaoli.core.academic.ParsedCourse
import com.pocket.xiaoli.data.entity.CourseEntity

/** 共享解析结果 → Android 落库实体。字段一一对应，不做任何额外推导。 */
internal fun ParsedCourse.toEntity(): CourseEntity = CourseEntity(
    id = id,
    name = name,
    credits = credits,
    teacher = teacher,
    room = room,
    dayOfWeek = dayOfWeek,
    startSlot = startSlot,
    endSlot = endSlot,
    weekType = weekType,
    weeks = weeks,
    colorIndex = colorIndex,
    schemeId = schemeId,
    shortName = shortName,
    colorPinned = colorPinned,
    note = note,
    startMinute = startMinute,
    endMinute = endMinute,
    kind = kind,
)
