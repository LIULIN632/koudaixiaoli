package com.pocket.xiaoli.ui.components

import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.io.File

/**
 * 守两条**只有源码扫描才守得住**的输入框规矩（2026-10「绑定界面输入有问题」的复盘）。
 *
 * ## 背景：一个真实报障
 *
 * 用户："绑定学号和密码，在绑定界面输入有问题，再导入课表界面就没有问题了，是不是输入法的问题。"
 * 查下来是两条**对比出来的**差异（两个导入页一直是对的，绑定页两样都缺）：
 *
 * 1. **密码框没设 `KeyboardType.Password`** —— 中文输入法仍处于联想 / 组词状态，
 *    而字段又把内容转成圆点：组合区与变换后的显示打架，表现为吞字、候选不上屏、光标乱跳。
 * 2. **`PasswordVisualTransformation()` 内联在调用点** —— 每次重组都新建实例，
 *    它是文本偏移映射（`offsetMapping`）的缓存键，实例一变映射就得重建，
 *    **正在组合（composing）的那几个字会抖、会丢**。
 *
 * 两条都不会让任何测试变红、也不会让任何截图变化 —— 只有真机 + 中文输入法才暴露。
 * 所以按本项目的既有做法：**把判据落在源码上**（同 `TabRouteInputFocusTest`）。
 *
 * ## ⚠️ 它守不住什么
 *
 * 它不检查"某个密码框有没有设 `KeyboardType.Password`"（那要解析 `PocketField(...)` 的实参，
 * 源码扫描做不可靠）。所以规矩的第二半靠人：**新增密码框时照着
 * `PocketField` 的 KDoc 里那张表给 `keyboardOptions`**。
 */
class InlinePasswordTransformationTest {

    /** 定义共享实例的文件：只有它允许出现 `PasswordVisualTransformation()`。 */
    private val definitionFile = "PocketField.kt"

    @Test
    fun `密码变换只有一个共享实例，调用点不许内联新建`() {
        val root = mainSourceRoot() ?: return
        val offenders = root.walkTopDown()
            .filter { it.isFile && it.extension == "kt" }
            .filter { it.name != definitionFile }
            .filter { it.readText().contains("PasswordVisualTransformation()") }
            .map { it.relativeTo(root).path }
            .sorted()
            .toList()

        assertTrue(
            "这些文件在调用点内联新建了 `PasswordVisualTransformation()`：\n" +
                offenders.joinToString("\n") { "  - $it" } + "\n\n" +
                "为什么不行：它是文本偏移映射的缓存键，每次重组新建实例会让映射重建 ——\n" +
                "中文输入法**正在组合**的字会抖、会丢（2026-10 用户报的\"输入有问题\"）。\n" +
                "怎么改：用共享实例 `com.pocket.xiaoli.ui.components.PocketPasswordTransformation`，\n" +
                "并给该字段 `keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password)`。",
            offenders.isEmpty(),
        )
    }

    @Test
    fun `PocketField 必须把 keyboardOptions 传给输入框`() {
        val root = mainSourceRoot() ?: return
        val source = File(root, "com/pocket/xiaoli/ui/components/$definitionFile")
        assertTrue("找不到 $definitionFile；若改名请同步本测试", source.isFile)
        val text = source.readText()

        assertTrue(
            "PocketField 的参数里有 keyboardOptions，就必须**真的传下去** —— " +
                "否则调用方辛苦设的 IME 类型（Ascii / Password / Phone）会被静默丢掉，\n" +
                "而界面上完全看不出来（键盘弹出的是哪种类型只有真机才感觉得到）。",
            text.contains("keyboardOptions = keyboardOptions"),
        )
    }

    private fun mainSourceRoot(): File? {
        val candidates = listOf(File("app/src/main/java"), File("src/main/java"))
        val hit = candidates.firstOrNull { it.isDirectory }
        if (hit == null) {
            fail("找不到主源码目录；已尝试：${candidates.joinToString { it.absolutePath }}")
        }
        return hit
    }
}
