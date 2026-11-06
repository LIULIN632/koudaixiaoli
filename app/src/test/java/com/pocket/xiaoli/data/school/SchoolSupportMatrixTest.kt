package com.pocket.xiaoli.data.school

import com.pocket.xiaoli.core.weather.weatherAnchorForCity
import com.pocket.xiaoli.data.ANCHORED_SCHOOL_IDS
import com.pocket.xiaoli.data.CampusConfig
import com.pocket.xiaoli.data.JwSystemType
import com.pocket.xiaoli.data.School
import java.io.File
import org.json.JSONArray
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * `docs/学校接入矩阵.md` 那张表的**生成器与漂移守卫**。
 *
 * ## 为什么要一张会红的表
 *
 * 用户问过「现在适配多少学校」。前两片的回答是散文：「可接入 12 所，其中只有 2 所配了
 * 校园服务与 3D 地图」。散文的问题在于它**当天就对、下周就错** —— 接一所新学校要同时动
 * 好几处（名单资产、`CampusConfig`、天气城市表、`ANCHORED_SCHOOL_IDS`、地图资产），
 * 少改一处不会崩，只是那所学校变成"半只能"，而**没有任何地方会因此变红**。
 * 这正是本轮之前已经发生过的事：山东理工与燕京理工进了选校列表，但服务与地图一格都没有，
 * 靠用户点名才发现。
 *
 * 所以这张表不是文档装饰，是一条门禁：每一格都从真源现算，与文件里那一行不一致就测红。
 *
 * ## 怎么重录
 *
 * 接入状态**有意变更**之后，带环境变量 `SCHOOL_MATRIX_RECORD=1` 跑一次本测试，表会被重写，
 * 然后连同代码改动一起提交。这与截图基线的 `-PrecordScreenshots` 是同一套纪律：
 * 默认只核对，记录是一次显式的动作。
 *
 * ## 每一列的真源（见 [rows]）
 *
 * 名单与顺序 = [schoolOptions]；教务系统 = `School.jwType`；协议实测 = `SchoolOption.protocolVerified`；
 * 本校实测 = [LOGIN_PROVEN_SCHOOL_IDS]；
 * 校园服务 = `CampusConfig.forSchoolId()`；3D 地图 = 那个校区有没有 `campus_3d.json`；
 * 天气 = [weatherAnchorForCity]；公交与馆藏 = [ANCHORED_SCHOOL_IDS]。
 */
class SchoolSupportMatrixTest {

    /** 一行的原始事实 —— 断言写在事实上，不写在字符串上，否则整张表一改排版测试就全红。 */
    private data class Row(
        val name: String,
        val city: String,
        val system: String,
        val protocolVerified: Boolean,
        val loginProven: Boolean,
        val campusServices: Boolean,
        val campusMap: Boolean,
        val weather: Boolean,
        val anchoredSources: Boolean,
    ) {
        fun toMarkdown(): String = listOf(
            name,
            city,
            system,
            tick(protocolVerified),
            tick(loginProven),
            tick(campusServices),
            tick(campusMap),
            tick(weather),
            tick(anchoredSources),
            tick(anchoredSources),
        ).joinToString(prefix = "| ", postfix = " |", separator = " | ")

        private fun tick(ready: Boolean) = if (ready) "✅" else "—"
    }

    private val header = listOf(
        "| 学校 | 城市 | 教务系统 | 协议实测 | 本校实测 | 校园服务 | 3D 地图 | 天气 | 公交到站 | 图书馆馆藏 |",
        "| --- | --- | --- | --- | --- | --- | --- | --- | --- | --- |",
    )

    private fun school(id: String, schools: List<School>): School =
        schools.firstOrNull { it.id == id } ?: error("名单里没有 $id")

    private fun rows(): List<Row> {
        val schools = builtInSchools()
        val options = schoolOptions(schools, CampusConfig.campuses)
        return options.map { option ->
            val school = school(option.id, schools)
            val campus = CampusConfig.forSchoolId(school.id)
            Row(
                name = option.name,
                city = option.city,
                system = school.jwType.label,
                protocolVerified = option.protocolVerified,
                loginProven = school.isLoginProven(),
                campusServices = campus != null,
                campusMap = campus?.let { mapAsset(it.id) } == true,
                weather = weatherAnchorForCity(option.city) != null,
                anchoredSources = school.id in ANCHORED_SCHOOL_IDS,
            )
        }
    }

    private fun mapAsset(campusId: String): Boolean =
        File("src/main/assets/campus_map/$campusId/campus_3d.json").isFile

    private fun table(): String = (header + rows().map { it.toMarkdown() }).joinToString("\n")

    /** 名单资产从 `app/src/main/assets/` 读，测试工作目录就是 `app/`（与 `SchoolOptionsTest` 同一套）。 */
    private fun builtInSchools(): List<School> {
        val out = mutableListOf<School>()
        for (system in listOf("zhengfang", "qiangzhi")) {
            val file = File("src/main/assets/schools_$system.json")
            assertTrue("找不到 $system 名单资产（测试工作目录是 app/）", file.isFile)
            val items = JSONArray(file.readText(Charsets.UTF_8))
            for (i in 0 until items.length()) {
                val o = items.getJSONObject(i)
                out += School(
                    id = o.getString("id"),
                    name = o.getString("name"),
                    city = o.optString("city"),
                    jwType = if (o.optString("jwType").uppercase() == "QIANGZHI") {
                        JwSystemType.QIANGZHI
                    } else {
                        JwSystemType.ZHENGFANG
                    },
                    baseUrl = o.optString("baseUrl"),
                    isV8 = o.optBoolean("isV8", true),
                )
            }
        }
        return out
    }

    @Test
    fun `接入矩阵与文档逐字一致`() {
        val rendered = table()
        if (System.getenv("SCHOOL_MATRIX_RECORD") == "1") {
            matrixFile().writeText(replaceTable(matrixFile().readText(), rendered), Charsets.UTF_8)
            println("学校接入矩阵已重录：${matrixFile().absolutePath}")
            return
        }
        assertTrue("找不到 ${matrixFile().absolutePath}", matrixFile().isFile)
        assertEquals(
            "代码里的接入状态与 `docs/学校接入矩阵.md` 不一致。" +
                "若这次变更是有意的（接了新学校 / 配了地图 / 验证了新链路），" +
                "带 SCHOOL_MATRIX_RECORD=1 重跑一次本测试再连同代码提交；否则按下面的 diff 改代码。",
            documentTable(matrixFile().readText(Charsets.UTF_8)),
            rendered,
        )
    }

    /** 表必须真的列了这些学校 —— 名单读空时"一致"会变得毫无意义。 */
    @Test
    fun `表覆盖四所点名的学校`() {
        val names = rows().map { it.name }
        listOf("烟台理工学院", "烟台大学", "山东理工大学", "燕京理工学院").forEach {
            assertTrue("矩阵里少了「$it」", it in names)
        }
        assertEquals("可接入学校就是 12 所（改动这条请连 README 那句一起改）", 12, names.size)
    }

    /**
     * **有校区 ≠ 全能用** —— 这张表最有信息量的一格。
     *
     * 烟台大学在 `CampusConfig` 里（校园服务与地图都有），但公交站点锚点与图书馆 OPAC
     * 仍写死在烟台理工，所以那两列必须是「—」。如果哪天有人图省事把它们一并打勾，
     * 这条断言会拦住 —— 那正是"用别人的公交站牌当本校的"那类事故。
     */
    @Test
    fun `有校区的学校不等于公交与馆藏也可用`() {
        val ytu = rows().first { it.name == "烟台大学" }
        assertTrue(ytu.campusServices)
        assertTrue(ytu.campusMap)
        assertTrue(ytu.weather)
        assertEquals("公交到站仍写死在烟台理工", false, ytu.anchoredSources)
    }

    /** 未收录校园服务的学校，那一列与地图列必须一起是「—」（判据同源，不许一格勾一半）。 */
    @Test
    fun `没有校区的学校服务与地图两列同步`() {
        for (row in rows().filter { !it.campusServices }) {
            assertEquals("${row.name} 没有校区配置，地图列也必须空着", false, row.campusMap)
        }
    }

    /** 协议那一列不许全绿：强智那条从未用真账号跑通过。 */
    @Test
    fun `强智那条协议仍未实测`() {
        val yjit = rows().first { it.name == "燕京理工学院" }
        assertEquals("强智教务", yjit.system)
        assertEquals("协议实测 = false，界面与这张表都得说实话", false, yjit.protocolVerified)
    }

    /**
     * **逐校实测目前只有烟台理工一所** —— 这条断言故意写死，不是方便，是闸门。
     *
     * 想在表里多打一个 ✅，得先改 `LOGIN_PROVEN_SCHOOL_IDS`；而那个名单的 KDoc 写明了加一个
     * id 需要什么证据（真账号登录成功 + 课表拉到且周次对得上）。
     * 于是"这所学校我们测过了"这句话必须经过一次显式的、可查证的改动，
     * 而不是从"正方协议在别处能用"顺手推出来 —— 上一轮就是靠这张表才发现那种推法。
     */
    @Test
    fun `逐校实测只有烟台理工`() {
        val proven = rows().filter { it.loginProven }.map { it.name }
        assertEquals(listOf("烟台理工学院"), proven)
    }

    /** 实测名单必须是可接入名单的子集 —— 否则表里会出现一行没人选得到的"已验证"。 */
    @Test
    fun `实测名单不能列出进不了选校列表的学校`() {
        val pickerIds = rows().map { it.name }
        for (id in LOGIN_PROVEN_SCHOOL_IDS) {
            val name = builtInSchools().firstOrNull { it.id == id }?.name ?: id
            assertTrue("$name 不在可接入名单里，却挂着实测名单", name in pickerIds)
        }
    }

    private fun matrixFile(): File = File(rootDir(), "docs/学校接入矩阵.md")

    /** 测试工作目录是 `app/`，文档在仓库根 —— 从这儿往上找一次，别写死绝对路径。 */
    private fun rootDir(): File {
        var dir: File? = File(".").absoluteFile
        while (dir != null) {
            if (File(dir, "settings.gradle.kts").isFile && File(dir, "docs").isDirectory) return dir
            dir = dir.parentFile
        }
        error("找不到仓库根（应有 settings.gradle.kts 与 docs/）")
    }

    private fun documentTable(markdown: String): String {
        val start = markdown.indexOf(BEGIN)
        val end = markdown.indexOf(END)
        assertTrue("文档里找不到标记对 $BEGIN，表无法被自动核对", start >= 0 && end > start)
        return markdown.substring(start + BEGIN.length, end).trim()
    }

    private fun replaceTable(markdown: String, rendered: String): String {
        val start = markdown.indexOf(BEGIN)
        val end = markdown.indexOf(END)
        check(start >= 0 && end > start) { "文档里缺少标记对，先手工加上再重录" }
        return markdown.substring(0, start + BEGIN.length) + "\n" + rendered + "\n" + markdown.substring(end)
    }

    private companion object {
        const val BEGIN = "<!-- 接入矩阵：由 SchoolSupportMatrixTest 核对，勿手改 -->"
        const val END = "<!-- /接入矩阵 -->"
    }
}
