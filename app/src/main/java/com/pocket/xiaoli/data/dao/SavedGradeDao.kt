package com.pocket.xiaoli.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.pocket.xiaoli.data.entity.SavedGradeEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface SavedGradeDao {
    @Query("SELECT * FROM saved_grades ORDER BY savedAt DESC")
    fun getAll(): Flow<List<SavedGradeEntity>>

    /** 批量保存；返回的 id 中 -1 表示因唯一索引冲突被跳过（已存在） */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertAll(items: List<SavedGradeEntity>): List<Long>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(item: SavedGradeEntity): Long

    @Query("DELETE FROM saved_grades WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("DELETE FROM saved_grades")
    suspend fun deleteAll()
}
