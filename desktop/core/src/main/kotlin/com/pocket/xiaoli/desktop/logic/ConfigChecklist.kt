package com.pocket.xiaoli.desktop.logic

/**
 * 「我的 → 数据」顶部的配置体检：一眼看清还缺什么，缺的那一项点进去就能补。
 *
 * 为什么要有这张卡：桌面端的功能全都「看起来能用」，但教务没登录、AI 没配 Key、
 * 课表还是内置示例时，用户遇到的是空页面而不是报错 —— 那就是「坏了」的体感。
 * 把这三件事摆在一排，每行都带动作，比在页面里散落提示有用。
 */
data class ChecklistRow(
    val label: String,
    val detail: String,
    val ok: Boolean,
    /** 右侧动作文案；`null` = 这一行不需要动作（已经就绪） */
    val action: String? = null,
)

data class ConfigChecklist(val rows: List<ChecklistRow>) {
    /** 还有几项要补（0 = 全部就绪） */
    val pending: Int get() = rows.count { !it.ok }
}

/**
 * @param jwUrl 当前校区配置的教务地址（null/空 = 该校没有地址）
 * @param jwLoggedIn 本次会话是否已登录教务（不保存密码，重启就要重新登录）
 * @param origin 数据来源标记：`seed` / `import` / `manual` / 空
 * @param startupMillis JVM 启动到界面就绪的毫秒数；`null` = 还没测到
 */
fun buildConfigChecklist(
    jwUrl: String?,
    jwLoggedIn: Boolean,
    aiConfigured: Boolean,
    aiModel: String,
    origin: String,
    courseCount: Int,
    startupMillis: Long?,
): ConfigChecklist {
    val hasJw = !jwUrl.isNullOrBlank()
    val sourceLabel = when (origin) {
        "seed" -> "内置示例数据"
        "import" -> "教务导入"
        "manual" -> "手动添加"
        else -> if (courseCount == 0) "还没有数据" else "本机数据"
    }
    val rows = listOf(
        ChecklistRow(
            label = "教务",
            detail = when {
                !hasJw -> "当前校区没有配置教务地址，导课表与查成绩都用不了"
                jwLoggedIn -> "已登录 · 成绩与课表可直接查（密码不保存）"
                else -> "未登录 · ${jwUrl!!.substringAfter("//").substringBefore('/')}"
            },
            ok = hasJw && jwLoggedIn,
            action = if (hasJw && !jwLoggedIn) "登录" else if (!hasJw) "换校区" else null,
        ),
        ChecklistRow(
            label = "AI",
            detail = if (aiConfigured) "$aiModel · Key 只存本机" else "没配 Key 时只有本地知识库与快问快答能答",
            ok = aiConfigured,
            action = if (!aiConfigured) "配置" else null,
        ),
        ChecklistRow(
            label = "数据来源",
            detail = "$sourceLabel · ${courseCount} 门课",
            ok = origin == "import" || (origin == "manual" && courseCount > 0),
            action = if (origin == "seed") "导入课表" else if (courseCount == 0) "新建/导入" else null,
        ),
        ChecklistRow(
            label = "启动",
            detail = if (startupMillis == null) "还没测到" else "界面就绪 %.1fs（从 JVM 起算）".format(startupMillis / 1000.0),
            ok = startupMillis != null && startupMillis <= 2_500,
            action = null,
        ),
    )
    return ConfigChecklist(rows)
}
