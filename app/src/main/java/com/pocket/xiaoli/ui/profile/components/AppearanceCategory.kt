package com.pocket.xiaoli.ui.profile.components

/**
 * 外观设置的分类。
 *
 * ## 判据
 *
 * **这个设置影响哪个 tab**。全局生效的（配色主题、深色模式、皮肤风格、界面风格）
 * 不属于任何单个 tab，所以不在这里 —— 它们留在「我的 → 设置」。
 * 硬把全局项塞进某个 tab，用户会以为"改了首页的配色，课表也应该跟着变"。
 *
 * 「底栏」单列：它是 5 个 tab 共用的常驻组件，观感权重比任何单页都高。
 */
enum class AppearanceCategory(val key: String, val label: String, val description: String) {
    HOME("home", "首页", "问候语 · 显示模块"),
    TIMETABLE("timetable", "课表", "课程卡片 · 格子 · 背景"),
    ASSISTANT("assistant", "鲤鱼", "回答字号 · 思考链"),
    SERVICES("services", "服务", "网格列数 · 图标"),
    PROFILE("profile", "我的", "列表密度 · 底部信息"),
    BOTTOM_BAR("bottom_bar", "底栏", "悬浮胶囊 · 液态玻璃"),
}
