package com.pocket.xiaoli.data.map

import android.content.Context
import com.pocket.xiaoli.data.dao.CampusMapCorrectionDao
import com.pocket.xiaoli.data.entity.CampusMapCorrectionEntity
import com.pocket.xiaoli.data.map.model.CampusMapValidator

/**
 * 校园地图仓库。
 *
 * - 原始数据：assets
 * - 覆盖数据：Room `campus_map_corrections`
 * - 进程内缓存：assets 结果
 */
class CampusMapRepository(
    context: Context,
    private val correctionDao: CampusMapCorrectionDao? = null,
) {
    private val dataSource = CampusMapDataSource.fromAssets(context)
    private val cache = mutableMapOf<String, CampusMapLoadResult>()

    @Synchronized
    fun load(campusId: String): CampusMapLoadResult =
        cache.getOrPut(campusId) { dataSource.load(campusId) }

    /**
     * 合并 Room 用户纠错后的完整加载；UI 建议放在协程里调用。
     *
     * [roomCells] 是「教室分布图」用户数据；先桥接成地图实体，再应用纠错，
     * 这样 `room_alias` / `room` 纠错也能命中用户自己录入的教室。
     */
    suspend fun loadWithCorrections(
        campusId: String,
        roomCells: List<RoomCell> = emptyList(),
    ): CampusMapLoadResult {
        val base = load(campusId)
        val corrections = correctionDao
            ?.getAll(campusId)
            .orEmpty()
            .map { it.toDomain() }
        val bridgedData = if (roomCells.isEmpty()) {
            base.data
        } else {
            CampusMapRoomBridge.overlay(base.data, campusId, roomCells)
        }

        if (corrections.isEmpty()) {
            return base.copy(
                data = bridgedData,
                issues = if (roomCells.isEmpty()) base.issues else CampusMapValidator.validate(bridgedData),
            )
        }

        val correctedData = CampusMapCorrectionApplier.apply(bridgedData, corrections)
        return base.copy(
            data = correctedData,
            issues = CampusMapValidator.validate(correctedData),
        )
    }

    @Synchronized
    fun clear() {
        cache.clear()
    }

    /** 保存一条本地地图纠错；没有 Room DAO（测试 / 降级）时静默丢弃。 */
    suspend fun saveCorrection(correction: CampusMapCorrection) {
        correctionDao?.upsert(correction.toEntity())
    }

    /** 删除一条纠错。 */
    suspend fun deleteCorrection(id: String) {
        correctionDao?.delete(id)
    }
}

private fun CampusMapCorrection.toEntity(): CampusMapCorrectionEntity =
    CampusMapCorrectionEntity(
        id = id,
        campusId = campusId,
        kind = kind,
        targetId = targetId,
        name = name,
        lat = lat,
        lng = lng,
        buildingId = buildingId,
        floorId = floorId,
        payload = payload,
        updatedAt = System.currentTimeMillis(),
    )

private fun CampusMapCorrectionEntity.toDomain(): CampusMapCorrection =
    CampusMapCorrection(
        id = id,
        campusId = campusId,
        kind = kind,
        targetId = targetId,
        name = name,
        lat = lat,
        lng = lng,
        buildingId = buildingId,
        floorId = floorId,
        payload = payload,
    )