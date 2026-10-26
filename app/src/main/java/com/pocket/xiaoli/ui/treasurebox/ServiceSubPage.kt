package com.pocket.xiaoli.ui.treasurebox

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
 * ## 取值全为 `data object`（无参）
 *
 * 原实现里 24 个都是纯布尔量，没有任何一个用可空状态携带数据（例如"当前看的是哪张卡"），
 * 所以这里全部是无参对象。**将来若某个子页需要参数**，把对应那条改成
 * `data class Xxx(val id: String) : ServiceSubPage` 即可 —— 但要记住
 * `subPage == Xxx(id)` 的比较语义会随之变化，届时改用 `when (val p = subPage)` 更稳。
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

    // ── 编辑类 ──
    data object QuickServiceEdit : ServiceSubPage
}
