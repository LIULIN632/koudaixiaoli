package com.pocket.xiaoli.data

/**
 * 功能开关（Feature Flags）。
 *
 * ## 收录原则
 * 只收录**真的能关掉某个行为**的开关 —— 不做「放着好看但什么都不控制」的假开关。
 * 一个开关如果没有任何代码读它，那它就是噪音，应该等对应功能落地时再加。
 *
 * ## 默认值
 * [defaultOn] 表示「新装用户」的初始状态。已经发布、用户能正常用的功能默认开；
 * 未完成 / 有风险的默认关，由开发者手动打开试。
 */
enum class FeatureFlag(
    val key: String,
    val label: String,
    val description: String,
    val defaultOn: Boolean,
) {
    BUILDING_FLOOR_MAP(
        key = "building_floor_map",
        label = "教学楼楼层分布图",
        description = "地图导航里点教学楼 POI 的「楼层」入口；关闭后该入口隐藏",
        defaultOn = true,
    ),
    ;

    companion object {
        fun fromKey(key: String): FeatureFlag? = entries.firstOrNull { it.key == key }

        /** 默认开启的开关集合（新装用户的初始状态） */
        val defaultOnKeys: Set<String>
            get() = entries.filter { it.defaultOn }.mapTo(mutableSetOf()) { it.key }
    }
}