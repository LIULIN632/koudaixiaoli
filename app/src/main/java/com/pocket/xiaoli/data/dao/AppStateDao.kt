package com.pocket.xiaoli.data.dao

import androidx.room.Dao
import androidx.room.Query

/** 当前数据归属学校的唯一读写口。**只允许** [SchoolScope] 与切校流程调用。 */
@Dao
interface AppStateDao {
    @Query("SELECT schoolId FROM app_state WHERE id = 1")
    suspend fun currentSchoolId(): String?

    /** 切校：改这一行，所有按校查询的 Flow 自动重发。 */
    @Query("UPDATE app_state SET schoolId = :schoolId WHERE id = 1")
    suspend fun setSchoolId(schoolId: String)
}
