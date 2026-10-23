package com.pocket.xiaoli.data.model

import com.pocket.xiaoli.data.entity.CourseEntity

/** Data transfer object for JSON backup and share code. */
internal data class CourseBackup(
    val version: Int = 1,
    val exportTime: String = "",
    val school: String = "",
    val courses: List<CourseEntity> = emptyList()
)