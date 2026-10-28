package com.pocket.xiaoli.core.campus


/**
 * 按宿舍楼决定生活服务走哪个校区。
 *
 * 例如：选了「烟台理工本校区」但宿舍是「7号宿舍楼」，洗澡/洗衣/电费应走
 * 烟大南校区；宿舍是「3号宿舍楼」则走烟理本校区。
 */
object DormServiceRouter {

    val dormServiceKeys: Set<String> = setOf(
        CampusServiceKeys.ELECTRICITY,
        CampusServiceKeys.SHOWER,
        CampusServiceKeys.HOT_WATER,
        CampusConfig.SERVICE_LAUNDRY,
    )

    fun isDormService(key: String): Boolean = key in dormServiceKeys

    /** 根据宿舍楼匹配服务校区；无法识别时回退到当前校区。 */
    fun serviceCampus(dorm: DormInfo?, fallback: CampusInfo): CampusInfo {
        val overrideId = dorm?.serviceCampusId?.takeIf { id ->
            CampusConfig.campuses.any { it.id == id }
        }
        if (overrideId != null) return CampusConfig.byId(overrideId)

        val campusId = dorm?.building
            ?.let(DormBuildingCatalog::serviceCampusId)
            ?: return fallback
        return CampusConfig.byId(campusId)
    }

    fun serviceCampusId(building: String): String? =
        DormBuildingCatalog.serviceCampusId(building)
}