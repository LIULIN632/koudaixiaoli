package com.pocket.xiaoli.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.pocket.xiaoli.data.entity.CampusMapCorrectionEntity

@Dao
interface CampusMapCorrectionDao {

    @Query("SELECT * FROM campus_map_corrections WHERE campus_id = :campusId ORDER BY updated_at DESC")
    suspend fun getAll(campusId: String): List<CampusMapCorrectionEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: CampusMapCorrectionEntity)

    @Query("DELETE FROM campus_map_corrections WHERE id = :id")
    suspend fun delete(id: String)

    @Query("DELETE FROM campus_map_corrections")
    suspend fun clear()
}