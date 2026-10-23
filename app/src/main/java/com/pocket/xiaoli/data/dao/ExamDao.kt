package com.pocket.xiaoli.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.pocket.xiaoli.data.entity.ExamEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ExamDao {

    @Query("SELECT * FROM exams WHERE schemeId = :schemeId ORDER BY date, time")
    fun getByScheme(schemeId: Long): Flow<List<ExamEntity>>

    @Query("SELECT * FROM exams ORDER BY date, time")
    fun getAll(): Flow<List<ExamEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(exam: ExamEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(exams: List<ExamEntity>): List<Long>

    @Query("UPDATE exams SET completed = :completed WHERE id = :id")
    suspend fun setCompleted(id: Long, completed: Boolean)

    @Query("DELETE FROM exams WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("DELETE FROM exams WHERE schemeId = :schemeId")
    suspend fun deleteByScheme(schemeId: Long)

    /**
     * 已存在的「课程+日期+时间」组合。
     *
     * 用于**导入去重**：同一份通知可能被用户粘贴两次，或分批导入。
     * 在 SQL 层查已存在的键，比查全表再在内存里比更省 —— 且避免
     * "两次导入之间数据变了"导致的内存/FK 不一致。
     */
    @Query("SELECT courseName || '|' || date || '|' || time FROM exams")
    suspend fun existingKeys(): List<String>
}
