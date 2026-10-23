package com.pocket.xiaoli.data

/**
 * 开发者实验室里的 Mock 数据场景。
 *
 * 只影响首页展示，便于在没有真实课表 / 网络异常复现条件时快速预览 UI 状态；
 * 不会写入或修改用户真实数据。
 */
enum class MockScenario(
    val key: String,
    val label: String,
    val description: String,
) {
    NONE(
        key = "none",
        label = "关闭",
        description = "使用真实数据",
    ),
    TODAY_HAS_CLASS(
        key = "today_has_class",
        label = "今天有课",
        description = "首页显示一门正在上的 Mock 课程",
    ),
    TODAY_NO_CLASS(
        key = "today_no_class",
        label = "今天无课",
        description = "首页显示无课，并顺延到明天",
    ),
    EXAM_WEEK(
        key = "exam_week",
        label = "考试周",
        description = "首页显示考试周提示",
    ),
    NETWORK_ERROR(
        key = "network_error",
        label = "网络异常",
        description = "首页显示网络异常状态（仅 UI 演示）",
    ),
    ;

    companion object {
        fun fromKey(key: String?): MockScenario {
            return entries.firstOrNull { it.key == key } ?: NONE
        }
    }
}
