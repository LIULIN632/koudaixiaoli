package com.pocket.xiaoli.data.knowledge

/**
 * 助手可调用的只读工具清单。
 *
 * 这里只定义「有哪些工具」这个稳定标识；**参数 schema 与执行体**在
 * `data/agent/AssistantToolRegistry.kt` 的 `AssistantToolBindings` 里，
 * 由 LLM 通过 function calling 调用。
 *
 * 历史：本枚举原先定义在 `AssistantToolRouter.kt` 中，与一个正则关键词分发器
 * （`AssistantToolRouter.match()`）同处一文件。改用 LLM 工具调用后，正则路由被删除，
 * 枚举因仍被 `AssistantTools` 与 `AssistantToolBindings` 使用而迁移到本文件。
 */
enum class AssistantTool(
    /**
     * 暴露给模型的函数名。
     *
     * ⚠️ 只此一份：注册表里的 spec 名与界面「本轮用了哪些工具」的反查都读这里。
     * 两边各写一份的话，改一个名字会静默变成"工具能调、但追问永远匹配不上"。
     */
    val functionName: String,
) {
    TODAY_COURSES("get_today_courses"),
    NEXT_COURSE("get_next_course"),
    TODOS("get_pending_todos"),

    /** 本地已存档成绩（我的课程） */
    MY_GRADES("get_grades"),

    /**
     * 培养方案进度（「服务 → 修课情况」查询时落下的本地快照）。
     *
     * 回答的是"我还差多少学分 / 哪些课没修 / 这门课修了没"。
     * ⚠️ 只读快照，**不联网**：教务查询需要登录+验证码，工具调用里没法弹验证码；
     * 快照为空时要如实告诉用户"先去「服务 → 修课情况」查一次"。
     */
    ACADEMIC_PROGRESS("get_academic_progress"),

    /** 教室 / 房间位置（房间号 → 楼栋楼层） */
    CAMPUS_LOCATION("find_classroom"),

    /** 校园服务目录（校园卡 / 电费 / 洗衣 / 图书馆等入口） */
    CAMPUS_SERVICE("find_campus_service"),

    /** 已确认的考试安排（来自通知解析，与首页待办同一份数据） */
    EXAMS("get_exams"),

    /**
     * 烟台天气（Open-Meteo，城市级坐标）。
     *
     * 与其它只读工具不同，这一个**允许联网**：用户主动问天气时，读到 30 分钟内的缓存
     * 就直接答，过期了才拉一次。它也是唯一一个会写入共享快照
     *（`WeatherRepository.snapshot`）的工具 —— 首页胶囊与主动提醒都看那一份。
     */
    WEATHER("get_weather"),

    /**
     * 校园知识库检索（Obsidian 维护的校园事实：制度 / 场馆 / 办事流程 / 通知）。
     *
     * 与内置的「App 帮助 FAQ」是两回事：那是"这个 App 怎么用"，硬编码在
     * `LocalFaqKnowledgeSource`；这里查的是"学校怎么规定"。
     */
    SEARCH_KNOWLEDGE("search_knowledge"),
    ;

    companion object {
        /** 按模型侧函数名反查；未知名字（旧历史 / 未来的新工具）返回 null。 */
        fun fromFunctionName(name: String): AssistantTool? =
            entries.firstOrNull { it.functionName == name }
    }
}
