package com.pocket.xiaoli.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.pocket.xiaoli.data.entity.TeacherEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface TeacherDao {
    @Query("SELECT * FROM teachers WHERE schoolId = (SELECT schoolId FROM app_state WHERE id = 1) ORDER BY name")
    fun getAllTeachers(): Flow<List<TeacherEntity>>

    @Query("SELECT * FROM teachers WHERE name = :name AND schoolId = (SELECT schoolId FROM app_state WHERE id = 1) LIMIT 1")
    suspend fun getTeacherByName(name: String): TeacherEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTeacher(teacher: TeacherEntity)

    @Query("DELETE FROM teachers WHERE name = :name AND schoolId = (SELECT schoolId FROM app_state WHERE id = 1)")
    suspend fun deleteTeacher(name: String)

    @Query("SELECT DISTINCT teacher FROM courses WHERE schemeId = :schemeId AND teacher != '' AND schoolId = (SELECT schoolId FROM app_state WHERE id = 1)")
    fun getDistinctTeacherNames(schemeId: Long): Flow<List<String>>
}
