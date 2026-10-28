package com.pocket.xiaoli.data

/**
 * 校区/服务数据的**唯一实现**在共享核心 `com.pocket.xiaoli.core.campus`
 * （Android 与 Windows 桌面端同一份），这里只保留同名 typealias，让既有调用方零改动。
 */
typealias CampusInfo = com.pocket.xiaoli.core.campus.CampusInfo
typealias CampusServiceKeys = com.pocket.xiaoli.core.campus.CampusServiceKeys
typealias CampusConfig = com.pocket.xiaoli.core.campus.CampusConfig

val CampusInfo.jwTypeLabel: String
    get() = com.pocket.xiaoli.core.campus.jwTypeLabelOf(jwType)