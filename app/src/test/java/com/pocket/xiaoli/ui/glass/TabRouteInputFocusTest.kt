package com.pocket.xiaoli.ui.glass

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 「tab 路由上**露在页面里**的输入框必须上报焦点」的源码静态守卫。
 *
 * ## 守的是什么 bug
 *
 * `AndroidManifest` 用的是 `adjustResize`：键盘弹出时**整个窗口变矮**，
 * 贴着窗口底部对齐的底栏会被一起顶上去。用户看到的是
 * **"输入的时候，底部 tab 会一起升起来"**（2026-10 用户实际反馈）。
 *
 * 导航层的对策是"有输入框获得焦点就把底栏收起来"
 *（`bottomBarVisible = !hasOpenSubPage && isTabRoute && !textInputFocused`，见 `AppNavigation.kt`）。
 * 而上报通道是 `LocalTextInputFocusReporter` —— **输入框必须自己调
 * `Modifier.reportTextInputFocus()`，漏了不报错、只是底栏上升**。
 *
 * 这正是"静默失败"的典型：编译过、单测过、只有用户看得见。
 * 本项目对同类问题已有先例（`TreasureBoxSubPageCoverageTest` 守 `subPageVisible` 的完备性、
 * `ProfileScreen.hasSubPage` 也栽过同样的跟头），所以这里照做。
 *
 * ## 判据（三条，缺一不可）
 *
 * 1. **tab 路由主页面**里，**露在 `Dialog` / `AlertDialog` 之外**的文本输入调用，
 *    必须在自己的 modifier 链附近出现 `reportTextInputFocus`；
 * 2. **已知在 tab 路由上渲染的、自带输入框的子组件**同样要满足第 1 条；
 * 3. 这两份清单里的文件必须**真的含输入框**（否则守卫名存实亡）。
 *
 * ### 为什么必须把弹窗排除掉（这条不是宽松，是正确）
 *
 * `AlertDialog` / `Dialog` 渲染在**独立窗口**里，`adjustResize` 缩的是那个窗口 ——
 * tab 底栏所在的主窗口不动，所以弹窗里的输入框**不需要**上报。
 * 实测例子：`TimetableScreen.kt` 有 9 处文本输入调用，全部位于
 * `CourseEditorDialog` 的两个 `AlertDialog` 内，其中 3 处还是 `readOnly = true`
 * 的 `menuAnchor` 下拉字段（根本不弹键盘）。
 * 若不做这个区分，这条守卫就会**逼着人往弹窗里加没意义的修饰符**，
 * 或者在 clist 里把这个文件删掉了事 —— 两种都是把一个正确的守卫改成错的。
 *
 * ⚠️ **它抓不到什么**（别指望它兜住）：
 * - 往 tab 页面**之外**新建一个子组件、由 tab 页面渲染、且没有上报 ——
 *   静态扫不出来，第 2 条那份清单必须**手工补**。
 * - 括号配对是**启发式**的：按字符扫，跳过 `"` 字符串与 `//` `/* */` 注释，
 *   但**不处理**原始字符串 `"""…"""` 与字符字面量 `'{'`。若将来有弹窗在这两者里
 *   塞了不对称的花括号，配对会偏。真发生了就把本测试改成解析 AST 或换成
 *   `AlertDialog` 的参数化断言。
 *
 * ⚠️ 读源码而非反射：`textInputFocused` 是 Composable 内的局部状态，JVM 单测拿不到。
 * 路径按 Gradle Test worker 的工作目录（模块根 `app/`）解析，并兼容从仓库根跑。
 */
class TabRouteInputFocusTest {

    private companion object {
        /**
         * 文本输入组件的调用。用**标识符边界**而不是裸子串：
         * `AlertDialog(` 里含 `Dialog(`、`OutlinedTextField(` 里含 `TextField(`，
         * 裸子串会把一处调用重复计数。
         */
        val INPUT_CALL = Regex("""(?<![A-Za-z0-9_])(OutlinedTextField|BasicTextField|TextField)\(""")

        /**
         * 弹窗调用。同样带标识符边界 —— 这样 `TaskPreviewDialog(` / `LearningManualImportDialog(`
         * 这类**自定义**弹窗组件不会被误当成 `Dialog(`（它们内部若真画弹窗，
         * 那个真正的 `AlertDialog(` 在定义它们的文件里，不在本文件）。
         */
        val DIALOG_CALL = Regex("""(?<![A-Za-z0-9_])(AlertDialog|BasicAlertDialog|Dialog)\(""")

        /** 上报修饰符的调用名。 */
        const val REPORTER = "reportTextInputFocus"

        /**
         * 五个 tab 路由各自的主页面文件。
         * 路由名见 `AppNavigation.bottomNavItems`：home / timetable / assistant / services / profile。
         */
        val tabRouteScreens = listOf(
            "src/main/java/com/pocket/xiaoli/ui/home/HomeScreen.kt",
            "src/main/java/com/pocket/xiaoli/ui/timetable/TimetableScreen.kt",
            "src/main/java/com/pocket/xiaoli/ui/assistant/AssistantScreen.kt",
            "src/main/java/com/pocket/xiaoli/ui/treasurebox/TreasureBoxScreen.kt",
            "src/main/java/com/pocket/xiaoli/ui/profile/ProfileScreen.kt",
        )

        /**
         * 已知在 **tab 路由上**渲染、且**自带输入框**的子组件。
         *
         * ⚠️ 这是**手工清单** —— 新增同类组件时必须补进来，否则本测试不知道它存在。
         *
         * ⚠️ 2026-10：首页的 `TodoRows.kt`（「添加待办」行内输入框）**已从清单移除** ——
         * 那个输入框没了：录入改到右下角 FAB 弹出的 `AddTodoDialog` 里，而弹窗是独立窗口，
         * 不受 `adjustResize` 顶底栏的影响，本就不需要上报（与本测试的弹窗豁免同一条判据）。
         */
        val tabRouteChildInputs = listOf(
            // 服务页搜索框（TreasureBoxScreen 渲染）。全局搜索页也用它，
            // 但那条不是 tab 路由，此时上报是无害的 no-op —— 所以接在组件内部是对的。
            "src/main/java/com/pocket/xiaoli/ui/components/campusui/CampusSearchBar.kt",
        )
    }

    private fun readSource(relative: String): String {
        val candidates = listOf(File(relative), File("app/$relative"))
        val hit = candidates.firstOrNull { it.exists() }
        assertTrue(
            "找不到源文件 $relative；已尝试：${candidates.joinToString { it.absolutePath }}\n" +
                "若文件被移动/改名，请同步更新本测试的清单。",
            hit != null,
        )
        return hit!!.readText()
    }

    /** 每个文本输入调用在源码里的起始偏移。 */
    private fun inputOffsets(source: String): List<Int> =
        INPUT_CALL.findAll(source).map { it.range.first }.toList()

    /**
     * 每个 `Dialog` / `AlertDialog` **整个调用**的字符区间。
     *
     * ⚠️ 必须配对**圆括号**，不能"从调用处往后找第一个 `{`"。
     * `AlertDialog(title = { … }, text = { … }, confirmButton = { … })` 里
     * 第一个 `{` 只是 `title` 那个 lambda，它一合区间就结束了 ——
     * 按花括号配对会把区间算**小**，真正的输入框落在区间之外、被误判成"露在页面上"。
     * （本测试第一次跑就是这样把 `TimetableScreen` 里 9 处**弹窗内**输入框全部误报的。）
     *
     * 启发式配对：跳过 `"` 字符串与 `//`、`/* */` 注释。局限见类 KDoc。
     */
    private fun dialogRanges(source: String): List<IntRange> =
        DIALOG_CALL.findAll(source).mapNotNull { match ->
            val open = source.indexOf('(', match.range.first)
            if (open < 0) return@mapNotNull null
            matchingParen(source, open)
        }.toList()

    /** 返回 [openIndex] 处 `(` 到其配对 `)` 的区间；配对失败返回 null。 */
    private fun matchingParen(source: String, openIndex: Int): IntRange? {
        var depth = 0
        var i = openIndex
        var inString = false
        var inLineComment = false
        var inBlockComment = false
        while (i < source.length) {
            val c = source[i]
            val next = source.getOrNull(i + 1)
            when {
                inLineComment -> if (c == '\n') inLineComment = false
                inBlockComment -> if (c == '*' && next == '/') { inBlockComment = false; i++ }
                inString -> when {
                    c == '\\' -> i++
                    c == '"' -> inString = false
                }
                c == '/' && next == '/' -> inLineComment = true
                c == '/' && next == '*' -> { inBlockComment = true; i++ }
                c == '"' -> inString = true
                c == '(' -> depth++
                c == ')' -> {
                    depth--
                    if (depth == 0) return openIndex..i
                }
            }
            i++
        }
        return null
    }

    /**
     * 露在页面里（不在任何弹窗内）、却没在**自己的参数列表内**上报焦点的输入调用位置。
     *
     * ⚠️ 判据是"上报出现在该调用的圆括号内"，**不是**"调用处之后 N 个字符内"。
     * 曾经用过 700 字符窗口，结果是：给输入框多写几行注释就能把上报挤出窗口，
     * 守卫随即**误报**（2026-10 改鲤鱼输入框时真的踩到了）。
     * 用括号配对后，判据与"这个修饰符是不是挂在它身上"结构等价，也没有魔法数字。
     */
    private fun offencesIn(path: String): List<Int> {
        val source = readSource(path)
        val dialogs = dialogRanges(source)
        return INPUT_CALL.findAll(source).map { it.range.first }.filter { offset ->
            if (dialogs.any { offset in it }) return@filter false // 弹窗里的不用上报
            val open = source.indexOf('(', offset)
            val call = if (open >= 0) matchingParen(source, open) else null
            val body = call?.let { source.substring(it.first, it.last + 1) } ?: return@filter true
            !body.contains(REPORTER)
        }.toList()
    }

    private fun assertNoOffences(paths: List<String>, hint: String) {
        val offenders = paths.mapNotNull { path ->
            val hits = offencesIn(path)
            if (hits.isEmpty()) null else "$path（$hits 处未上报）"
        }
        assertTrue(
            "这些文件里有**露在页面里**的文本输入框、却没有调用 `Modifier.reportTextInputFocus()`：\n" +
                offenders.joinToString("\n") { "  - $it" } +
                "\n\n后果：键盘弹出时底栏会被 adjustResize 一起顶上去（用户可见）。\n" +
                "修法：$hint",
            offenders.isEmpty(),
        )
    }

    @Test
    fun `tab 路由页面里露在页面上的输入框都必须上报焦点`() {
        assertNoOffences(
            tabRouteScreens,
            "在输入框的 modifier 链上加 `.reportTextInputFocus()`。" +
                "若它其实在弹窗里（独立窗口，不顶底栏），本测试本就会跳过它 —— " +
                "报错说明它确实露在页面上。",
        )
    }

    @Test
    fun `tab 路由上渲染的子组件输入框也必须上报焦点`() {
        assertNoOffences(
            tabRouteChildInputs,
            "在输入框的 modifier 链上加 `.reportTextInputFocus()`（共享组件接在内部即可覆盖所有使用方）。",
        )
    }

    /**
     * 反向断言：**子组件清单**里的文件要真的含输入框。
     *
     * 否则文件重构掉输入框之后，上面那条会**因为"扫不到输入框"而永远通过** ——
     * 那是"测试变假"的典型。本项目在别处栽过同型的坑：`AppCard` 的 KDoc 曾声称
     * "`skinShapes()` 覆盖 77 处 `Card(`"，实测那些调用点**全部自己传了 `shape=`**，
     * 于是那句话是虚的。
     *
     * ⚠️ **只对子组件清单做这条断言，不对 `tabRouteScreens` 做**：
     * 五个 tab 页面里，`HomeScreen` / `TreasureBoxScreen` / `ProfileScreen`
     * **本来就不含输入框**（它们的输入框在 `TodoRows` / `CampusSearchBar` 等子组件里），
     * 这是正常结构、不是"守卫失效"。对它们套这条会把一个正确的守卫改成错的。
     */
    @Test
    fun `子组件清单里的文件必须真的含输入框，否则守卫是假的`() {
        val stale = tabRouteChildInputs.filter { inputOffsets(readSource(it)).isEmpty() }
        assertTrue(
            "这些文件已不含任何文本输入调用，应从 `tabRouteChildInputs` 里移除" +
                "（否则对应的守卫名存实亡）：\n" + stale.joinToString("\n") { "  - $it" },
            stale.isEmpty(),
        )
    }
}
