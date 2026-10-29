package com.pocket.xiaoli.data.courseware

/**
 * 内置交互课件的清单 —— **这一份是唯一的真源**：服务页入口、WebView 标题、
 * 路由参数校验全部从这里读，不在 UI 里再抄一遍名字。
 *
 * ## 为什么课件是"内置"而不是"在线"
 *
 * 两个课件都是 Three.js 写的离线教具（源项目在仓库外的 `D:\AICAN\建模`，
 * 由 [tools/sync_courseware.py] 同步进 `assets/courseware/`）。不发网络请求，
 * 所以**没网也能打开** —— 这是它们能进课堂的前提，也是"不新增依赖"约束下
 * 唯一不用引入 WebView 资源加载框架的做法。
 *
 * @param dir `assets/courseware/` 下的目录名，同时是路由里的 id
 * @param subtitle 一句话说清"拖动能看到什么"，服务页那一格就说这一句
 * @param course 这门课的名字 —— 用户认课不认术语，标"材料力学"比标"梁单元"有用
 */
enum class Courseware(
    val dir: String,
    val title: String,
    val subtitle: String,
    val course: String,
) {
    BeamDeflection(
        dir = "beam-deflection",
        title = "简支梁挠度与弯矩",
        subtitle = "改荷载 P，看梁怎么弯、弯矩图 M(x) 跟着变",
        course = "材料力学",
    ),
    SoilThreePhase(
        dir = "soil-three-phase",
        title = "土的三相组成",
        subtitle = "拖孔隙率与含水率，看固相 / 水 / 气三层怎么涨落",
        course = "土力学",
    ),
    ;

    companion object {
        /** 路由参数 → 课件。**未知 id 返回 null**，由调用方决定怎么兜底（不猜、不默认打开第一个）。 */
        fun fromId(id: String?): Courseware? = entries.firstOrNull { it.dir == id }
    }
}
