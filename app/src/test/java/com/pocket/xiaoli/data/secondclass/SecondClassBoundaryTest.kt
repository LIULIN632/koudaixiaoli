package com.pocket.xiaoli.data.secondclass

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * **到梦空间模块的能力边界不许被拓宽**（架构文档 §1 的 B3/B5/B6 + §6 自检清单）。
 *
 * ## 为什么类型系统不够，还要一条源码扫描
 *
 * `SecondClassDataSource` 只有三个读方法，所以**调用方**没法报名、签到、提交 ——
 * 这一层由编译器守着，不需要本测试。真正没人守的是**下一个人往接口里加方法**：
 * 加一个 `suspend fun enroll(activityId: String)` 编译照样通过，
 * 而它一旦存在，就迟早会被接上 UI、被用来批量抢名额 —— 那正是文档要挡住的事。
 *
 * 文档的说法是「后来者想加就得先改接口，那一步是有意设计的摩擦点」。
 * 但"改接口"本身是**零成本**的，算不上摩擦。本测试把那一步变成**红了的构建**：
 * 想加写操作，就得先删掉/改写这条守卫，并在这里写明理由。
 *
 * ## 判据为什么落在源码上
 *
 * 与 `HomeNoBubbleTipTest` / `InlinePasswordTransformationTest` / `TabRouteInputFocusTest` 同一档次：
 * 被守的东西（一个方法签名、一个 `Captcha` 类名）**在截图与语义树里都不存在**，
 * 只有源码里有。断言接口声明的方法集合"精确等于白名单"，就没有含糊空间 ——
 * 加了写方法会红，**改了读方法名字**也会红（那是 UI 要跟着改的信号，值得被看见）。
 *
 * ## ⚠️ 换文件位置时要同步
 *
 * [INTERFACE_FILE] 与 [MODULE_DIR] 是手工清单。若 `data/secondclass/` 拆分或改名，
 * 本测试会以"找不到源文件"直接失败（不是静默变绿）—— 这是刻意的。
 */
class SecondClassBoundaryTest {

    @Test
    fun `数据源接口只暴露三个读方法`() {
        val declared = METHOD_NAME.findAll(interfaceBody(readSource(INTERFACE_FILE)))
            .map { it.groupValues[1] }
            .toSet()

        assertEquals(
            "`SecondClassDataSource` 的方法集合变了。\n" +
                "这个接口**只允许有读方法**（架构文档 B3/B5：浏览/查询可以，报名/取消/签到/提交一律不做）。\n" +
                "新增了写操作请撤掉 —— 想让接口有这个能力，必须先改本条守卫并写明理由，\n" +
                "因为它是「能力在类型系统里不存在」这条边界的**唯一自动化守门人**。\n" +
                "若只是改了读方法的名字，请同步 UI 与 Repository，并更新本处的白名单。",
            READ_ONLY_METHODS,
            declared,
        )
    }

    /**
     * B1/B4/B7：接口**不接受任何账号或凭据参数**。
     *
     * 凭据属于「当前登录用户本人」，由会话对象持有，不该从 UI 一层层传进数据源 ——
     * 一旦签名里出现 `studentId` / `token` 这类参数，就等于为「帮别人查」「多账号切换」开了口子，
     * 而那两个口子文档明确不做（无账号池、无凭据导入导出）。
     */
    @Test
    fun `数据源接口不接受账号或凭据参数`() {
        val body = interfaceBody(readSource(INTERFACE_FILE))

        val offenders = CREDENTIAL_PARAM.findAll(body).map { it.value }.toList()

        assertTrue(
            "接口签名里出现了账号/凭据类参数：$offenders\n" +
                "凭据由会话（`SecondClassSession`）持有，不经过数据源接口 ——\n" +
                "否则「帮朋友查」与多账号切换会从这里长出来（架构文档 B1/B4/B7）。",
            offenders.isEmpty(),
        )
    }

    /**
     * B6：**不做验证码相关逻辑**。
     *
     * 验证码一律交给 WebView 让用户手输。模块里出现 `Captcha` 字样，
     * 就意味着有人在往识别 / 打码平台那个方向走 —— 那是文档里明确划掉的一条路。
     */
    @Test
    fun `模块内不存在验证码识别相关代码`() {
        val offenders = moduleSources().filter { (_, code) -> CAPTCHA.containsMatchIn(code) }

        assertTrue(
            "到梦空间模块里出现了验证码相关代码：${offenders.map { it.first }}\n" +
                "本模块不处理验证码（架构文档 B6）：没有识别、没有打码平台、没有 `CaptchaSolver`，\n" +
                "验证码场景一律回退到 WebView 让用户手输。",
            offenders.isEmpty(),
        )
    }

    /**
     * 守卫**自己**的有效性：一条永远绿的源码扫描等于没有守卫
     * （正则写错、或者扫了个空集合，它照样每次都过）。
     *
     * 这里用合成的接口片段把三件事钉住：抓得到写方法、方法集合比较是精确的、
     * 且 `activityId` 这种**合法**参数不会被凭据检查误伤。
     */
    @Test
    fun `守卫本身有效`() {
        val withWriteMethod = """
            interface SecondClassDataSource {
                suspend fun fetchTranscript(): SecondClassResult<SecondClassTranscript>
                suspend fun enroll(activityId: String): SecondClassResult<Unit>
            }
        """.trimIndent()

        val declared = METHOD_NAME.findAll(interfaceBody(withWriteMethod))
            .map { it.groupValues[1] }
            .toSet()
        assertTrue("写方法 `enroll` 必须被抓到 —— 否则这条守卫等于没有", "enroll" in declared)
        assertFalse(
            "方法集合必须精确比较：多一个写方法就不能等于白名单",
            declared == READ_ONLY_METHODS,
        )

        assertTrue(
            "`activityId` 是合法参数，不该被凭据检查误伤",
            CREDENTIAL_PARAM.find("fun fetchActivityDetail(activityId: String)") == null,
        )
        assertTrue(
            "`studentId` 这类参数必须被抓到",
            CREDENTIAL_PARAM.find("fun fetchTranscript(studentId: String)") != null,
        )

        assertTrue("注释里的 `Captcha` 不该误报", !CAPTCHA.containsMatchIn(stripComments("// 没有 CaptchaSolver")))
        assertTrue("代码里的 `Captcha` 必须抓到", CAPTCHA.containsMatchIn(stripComments("class CaptchaSolver")))
    }

    // ───────────────────────────── 基础设施 ─────────────────────────────

    /** 接口声明的方法集合的**唯一白名单**。加写操作必须改这里，并在此写明理由。 */
    private val READ_ONLY_METHODS = setOf("fetchActivities", "fetchActivityDetail", "fetchTranscript")

    private val METHOD_NAME = Regex("""fun\s+([A-Za-z_][A-Za-z0-9_]*)\s*\(""")

    private val CREDENTIAL_PARAM = Regex(
        """\b(account|accountId|userName|userId|user|studentId|studentNo|openId|token|cookie|credential|credentials|password)\s*:""",
        RegexOption.IGNORE_CASE,
    )

    private val CAPTCHA = Regex("""Captcha""", RegexOption.IGNORE_CASE)

    /**
     * 取出 `interface SecondClassDataSource { … }` 的**方法体部分**（去掉外层大括号）。
     *
     * ⚠️ 必须**先剥注释再数大括号**：KDoc 里正大光明地写着
     * "没有 `enroll()` / `cancel()` / `signIn()` / `submit()`"，不剥掉的话
     * 那段说明本身就会被当成声明（这是个会让守卫**永远红**的假阳性）。
     */
    private fun interfaceBody(source: String): String {
        val clean = stripComments(source)
        val start = clean.indexOf("interface SecondClassDataSource")
        check(start >= 0) { "在源码里找不到 `interface SecondClassDataSource` 声明" }
        val open = clean.indexOf('{', start)
        var depth = 0
        for (i in open until clean.length) {
            when (clean[i]) {
                '{' -> depth++
                '}' -> {
                    depth--
                    if (depth == 0) return clean.substring(open + 1, i)
                }
            }
        }
        error("接口的大括号不配对 —— 先确认文件没被改坏")
    }

    /**
     * 模块下所有 `.kt` 的 (相对路径, 已剥注释的源码)。
     *
     * 只扫 `main` 源集：测试文件里出现"验证码"字样（比如本文件的说明）不该算违规。
     */
    private fun moduleSources(): List<Pair<String, String>> =
        resolve(MODULE_DIR)
            .walkTopDown()
            .filter { it.isFile && it.extension == "kt" }
            .map { it.relativeTo(resolve(MODULE_DIR)).path to stripComments(it.readText()) }
            .toList()

    /**
     * 粗暴剥掉 `//` 行注释与 `/* */` 块注释。
     *
     * 与 `HomeNoBubbleTipTest.stripComments` 同一档次的启发式：不做字符串字面量感知，
     * 只用来**减少假阳性**。判错的代价是"多报一次、人来确认"，不是漏放。
     */
    private fun stripComments(source: String): String =
        source
            .replace(Regex("""/\*[\s\S]*?\*/"""), "")
            .replace(Regex("""//[^\n]*"""), "")

    private fun readSource(relative: String): String = resolve(relative).readText()

    /** 测试的工作目录可能是模块根（`app/`），也可能是仓库根 —— 两种都试。 */
    private fun resolve(relative: String): File {
        val candidates = listOf(File(relative), File("app/$relative"))
        return candidates.firstOrNull { it.exists() }
            ?: error(
                "找不到 $relative；已尝试：${candidates.joinToString { it.absolutePath }}\n" +
                    "若 `data/secondclass/` 拆分或改名，请同步更新 SecondClassBoundaryTest 的清单。",
            )
    }

    private val INTERFACE_FILE = "src/main/java/com/pocket/xiaoli/data/secondclass/SecondClassDataSource.kt"
    private val MODULE_DIR = "src/main/java/com/pocket/xiaoli/data/secondclass"
}
