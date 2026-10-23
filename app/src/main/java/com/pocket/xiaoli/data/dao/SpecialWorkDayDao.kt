package com.pocket.xiaoli.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.pocket.xiaoli.data.entity.SpecialWorkDayEntity

/** 调休上班日映射，参考「聚在工大」SpecialWorkDayDao 裁剪（去掉对 DateTimeManager 的默认参数耦合）。 */
@Dao
interface SpecialWorkDayDao {

    @Query("SELECT * FROM special_work_day")
    suspend fun getAll(): List<SpecialWorkDayEntity>

    @Query("SELECT * FROM special_work_day WHERE origin_date = :originDate")
    suspend fun find(originDate: String): SpecialWorkDayEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: SpecialWorkDayEntity)

    @Query("DELETE FROM special_work_day WHERE origin_date = :originDate")
    suspend fun delete(originDate: String)

    @Query("DELETE FROM special_work_day")
    suspend fun clear()
}