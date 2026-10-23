package com.pocket.xiaoli.data.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "teachers")
data class TeacherEntity(
    @PrimaryKey val name: String,
    val office: String = "",
    val officeHours: String = "",
    val phone: String = "",
    val title: String = "",
    val lastUpdated: Long = System.currentTimeMillis()
)
