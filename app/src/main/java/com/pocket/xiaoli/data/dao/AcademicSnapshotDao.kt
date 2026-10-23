package com.pocket.xiaoli.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.pocket.xiaoli.data.entity.AcademicSnapshotEntity
import kotlinx.coroutines.flow.Flow

/**
 * 「修课情况」快照：单行表，读一条、写一条、清一条。
 *
 * 用 `REPLACE` 语义（[upsert]）而不是"先删后插"：主键固定为
 * [AcademicSnapshotEntity.SINGLETON_ID]，替换即覆盖，且**只有一次写** ——
 * 分两步写在两次写之间被杀进程会留下空表，用户看到的就是"鲤鱼说没有培养方案数据"。
 */
@Dao
interface AcademicSnapshotDao {

    @Query("SELECT * FROM academic_snapshot WHERE id = 1 LIMIT 1")
    fun observe(): Flow<AcademicSnapshotEntity?>

    @Query("SELECT * FROM academic_snapshot WHERE id = 1 LIMIT 1")
    suspend fun get(): AcademicSnapshotEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: AcademicSnapshotEntity)

    @Query("DELETE FROM academic_snapshot")
    suspend fun clear()
}
