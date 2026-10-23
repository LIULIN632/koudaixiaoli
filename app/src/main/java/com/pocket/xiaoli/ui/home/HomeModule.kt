package com.pocket.xiaoli.ui.home

/**
 * 首页可配置模块。
 *
 * 现有首页只保留两块：课程卡片（正在上课 / 下一节课）与待办（含考试提醒）。
 * 顶栏问候、快捷动作、学习平台同步入口已移除；学习通同步改在
 * 「我的 → 绑定信息 → 学习平台」里做，避免首页堆太多入口。
 *
 * ⚠️ 2026-10：TODO 的文案从「待办作业」改成「待办」—— 首页不再有「作业」这个概念，
 * 只有待办（带截止日期的待办与不带的是同一种东西，见 `TodoRows.kt` 文件头）。
 * `key` 保持 `"todo"` 不变：改 key 会让老用户已保存的首页排序失效。
 *
 * 排序 / 显隐由 [HomeLayout] 维护，AppContainer 只负责持久化 key。
 */
enum class HomeModule(
    val key: String,
    val label: String,
    val description: String,
) {
    COURSE_CARD("course_card", "课程卡片", "正在上课 / 下一节课"),
    TODO("todo", "待办", "待办与考试提醒"),
    ;

    companion object {
        fun fromKey(key: String): HomeModule? = entries.firstOrNull { it.key == key }
    }
}

/** 纯函数：首页模块排序与显隐计算。 */
object HomeLayout {

    val defaultOrder: List<HomeModule> = listOf(
        HomeModule.COURSE_CARD,
        HomeModule.TODO,
    )

    /**
     * 把持久化的 key 顺序归一化为完整、无重复的模块列表。
     * 旧版本遗留的模块 key（header / quick_actions / learning_sync）会被自动丢弃。
     */
    fun normalize(orderKeys: List<String>): List<HomeModule> {
        val seen = mutableSetOf<String>()
        val result = mutableListOf<HomeModule>()
        orderKeys.forEach { key ->
            HomeModule.fromKey(key)?.let {
                if (seen.add(it.key)) result += it
            }
        }
        defaultOrder.forEach { module ->
            if (seen.add(module.key)) result += module
        }
        return result
    }

    /** 将 [from] 位置的模块移动到 [to] 位置；越界返回原列表。 */
    fun move(orderKeys: List<String>, from: Int, to: Int): List<String> {
        if (from !in orderKeys.indices || to !in orderKeys.indices || from == to) return orderKeys
        val mutable = orderKeys.toMutableList()
        val item = mutable.removeAt(from)
        mutable.add(to, item)
        return mutable
    }
}
