package com.pocket.xiaoli.desktop.data

/**
 * 校区与校园服务入口 —— **唯一实现在共享核心** `com.pocket.xiaoli.core.campus`
 * （Android 与桌面端读同一张表：多一处地址就两边都有）。
 *
 * 这里保留同名 typealias，桌面端既有 UI 调用点零改动。
 */
typealias CampusInfo = com.pocket.xiaoli.core.campus.CampusInfo
typealias CampusServiceKeys = com.pocket.xiaoli.core.campus.CampusServiceKeys
typealias CampusConfig = com.pocket.xiaoli.core.campus.CampusConfig

/** 教务系统展示名（与 Android 同一实现）。 */
val CampusInfo.jwTypeLabel: String get() = com.pocket.xiaoli.core.campus.jwTypeLabelOf(jwType)
