package com.pocket.xiaoli.data.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "campus_map_corrections")
data class CampusMapCorrectionEntity(
    @PrimaryKey
    @ColumnInfo(name = "id") val id: String,
    @ColumnInfo(name = "campus_id") val campusId: String,
    @ColumnInfo(name = "kind") val kind: String,
    @ColumnInfo(name = "target_id") val targetId: String,
    @ColumnInfo(name = "name") val name: String = "",
    @ColumnInfo(name = "lat") val lat: Double? = null,
    @ColumnInfo(name = "lng") val lng: Double? = null,
    @ColumnInfo(name = "building_id") val buildingId: String? = null,
    @ColumnInfo(name = "floor_id") val floorId: String? = null,
    @ColumnInfo(name = "payload") val payload: String = "",
    @ColumnInfo(name = "updated_at") val updatedAt: Long = 0L,
)