package com.pocket.xiaoli.desktop.data

/** 新建空实体（新建课程/待办时的初始值，只在数据层造对象）。 */
object NewEntity {

    fun course(day: Int = 1, slot: Int = 1): Course = Course(
        id = "c_" + System.currentTimeMillis().toString(36) + (100..999).random(),
        name = "",
        dayOfWeek = day,
        startSlot = slot,
        endSlot = slot,
        colorIndex = 0,
        colorPinned = false,
        shortName = "",
    )

    fun todo(): Todo = Todo(
        id = "t_" + System.currentTimeMillis(),
        title = "",
        due = "",
        priority = 0,
        createdAt = System.currentTimeMillis(),
    )
}
