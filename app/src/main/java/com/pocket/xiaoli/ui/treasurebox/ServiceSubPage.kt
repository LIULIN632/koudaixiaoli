package com.pocket.xiaoli.ui.treasurebox

import com.pocket.xiaoli.data.courseware.Courseware

/**
 * 服务页（`TreasureBoxScreen`）的子页目的地。
 *
 * ## 为什么要有这个类型
 *
 * 2026-10 之前，这个页面用 **24 个互相独立的布尔量**表示"当前停在哪个子页"：
 *
 * ```kotlin
 * var showLearningPage by remember { mutableStateOf(false) }
 * var showFoodPage by remember { mutableStateOf(false) }
 * // … 共 24 个
 * ```
 *
 * 于是**同一个子页概念要在三处各写一遍**：
 *
 * 1. 声明（24 行）
 * 2. 汇总成 `subPageVisible`（一条 24 项的 `||` 链）
 * 3. `BackHandler` 里复位（24 行 `= false`）
 *
 * ⚠️ 漏掉第 2 处的后果不是"返回键没反应"，而是 `subPageVisible = false` →
 * `BackHandler` **根本没装上** → 系统返回键**直接退出整个 App**，同时底栏也不会隐藏。
 * 这个事故真实发生过（2026-10-02 补漏了 `QuickServiceEdit` / `ExpressPickup` /
 * `GymInfo` / `DrivingInfo` / `CardAccountDialog` 五个）。
 *
 * 换成这一个类型之后，"漏不漏"从**靠纪律**变成**靠编译器**：
 * `subPage != null` 就是"有子页"，`subPage = null` 就是"复位"，不可能再漏。
 *
 * ## 取值大多是无参 `data object`
 *
 * 原实现里 24 个都是纯布尔量，没有任何一个用可空状态携带数据（例如"当前看的是哪张卡"），
 * 所以这里绝大多数是无参对象 —— `subPage == ServiceSubPage.X` 的比较语义因此是安全的。
 *
 * ⚠️ **唯一的例外**是 [CoursewarePage]（带一个课件 id）。带参数的那一条**必须用 `is` 或
 * `when` 比较**，不能用 `==`：`subPage == ServiceSubPage.CoursewarePage` 在 Kotlin 里
 * 拿类型和实例比，编译不过；而 `subPage == ServiceSubPage.CoursewarePage(x)` 一旦
 * 参数不同就悄悄不相等，表现是"进去了但分发没命中 → 空白页"。
 * 配套的守卫 `TreasureBoxSubPageCoverageTest` 因此同时接受 `==` 与 `is` 两种写法。
 */
sealed interface ServiceSubPage {

    // ── 学习 / 教务 ──
    data object Learning : ServiceSubPage
    /** 学习通账号页：课程与待办作业（原生，复用 WebView 登录后的 cookie）。 */
    data object Chaoxing : ServiceSubPage
    /** 到梦空间入口页：网页版只能电脑扫码，这一页负责把话说清 + 给可行的动作。 */
    data object SecondClass : ServiceSubPage
    data object GradeQuery : ServiceSubPage
    data object SavedGrades : ServiceSubPage
    data object AcademicQuery : ServiceSubPage
    data object TeacherLookup : ServiceSubPage
    data object CourseSelect : ServiceSubPage

    // ── 图书馆 / 自习 ──
    data object Library : ServiceSubPage
    data object LibrarySeat : ServiceSubPage
    data object Notes : ServiceSubPage
    data object TaskChecklist : ServiceSubPage

    // ── 生活服务 ──
    data object Weather : ServiceSubPage
    data object Electricity : ServiceSubPage
    data object CampusCard : ServiceSubPage
    data object CardAccountDialog : ServiceSubPage
    data object ExpressPickup : ServiceSubPage
    data object TodayCampus : ServiceSubPage
    data object FoodPicker : ServiceSubPage
    /** 麦当劳蛋白榜：离线快照 + 预算凑蛋白（见 `McProteinPage`）。 */
    data object McProtein : ServiceSubPage
    data object GymInfo : ServiceSubPage
    data object GoodItems : ServiceSubPage
    data object TreeHole : ServiceSubPage

    // ── 信息页 ──
    data object UpgradeInfo : ServiceSubPage
    data object BusInfo : ServiceSubPage
    data object KaoyanInfo : ServiceSubPage
    data object DrivingInfo : ServiceSubPage

    /**
     * 内置交互课件（Three.js，见 `data/courseware/`）。
     *
     * ⚠️ 这是本sealed 层级里**唯一带参数**的一员，而且是刻意如此：课件清单
     * （[com.pocket.xiaoli.data.courseware.Courseware]）以后会长，若每个课件写一个
     * `data object`，加第三个课件就得同时改三处（枚举、入口、分发），
     * 而漏掉任何一处的表现都是"格子点不动"或"进去是空白"。
     * 带参数之后，加课件只改清单那一处。
     */
    data class CoursewarePage(val item: Courseware) : ServiceSubPage

    // ── 编辑类 ──
    data object QuickServiceEdit : ServiceSubPage
}
