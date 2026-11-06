package com.pocket.xiaoli.desktop.data

import com.google.gson.GsonBuilder
import com.google.gson.JsonParser
import java.io.File
import java.io.IOException
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.time.LocalDate

/**
 * 本地存储：单文件 JSON（`%APPDATA%\PocketXiaoli\pocket-desktop.json`）。
 *
 * ## 为什么不上 Room / SQLite
 *
 * 桌面端的数据量是「一学期几十门课 + 几百条待办」，**单文件足够**；
 * 而单文件换来的是「备份 = 复制一个文件」「导入 = 覆盖一个文件」，
 * 与移动端「不传服务器、只在本地」的约束一致，也不需要引入数据库依赖。
 *
 * ⚠️ 写入走同目录临时文件 + 原子改名（见 [persist] / [writeAtomic]）：写到一半崩溃时旧数据仍在，
 * 且盘上任何时刻都存在完整文件 —— 先删后改名会留一个「文件不见了」的空窗。
 * 单实例由 `InstanceGuard` 守住：两个进程各写整份 JSON 时，后写的会静默覆盖前者的全部改动。
 */
object Store {

    private val gson = GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create()

    val dataDir: File = run {
        val appData = System.getenv("APPDATA")
        val base = if (appData.isNullOrBlank()) {
            File(System.getProperty("user.home"), ".pocket-xiaoli")
        } else {
            File(appData, "PocketXiaoli")
        }
        base.mkdirs()
        base
    }

    val dataFile: File = File(dataDir, "pocket-desktop.json")

    private var state: AppData = loadInitial()

    val data: AppData get() = state

    private fun loadInitial(): AppData {
        val loaded = runCatching {
            if (!dataFile.exists()) null
            else gson.fromJson(dataFile.readText(Charsets.UTF_8), AppData::class.java)
        }.getOrNull()
        val normalized = normalize(loaded ?: seedData())
        if (!dataFile.exists()) persist(normalized)
        return normalized
    }

    private fun normalize(raw: AppData): AppData {
        val terms = raw.terms.ifEmpty { seedData().terms }
        val slots = raw.slots.ifEmpty { DefaultTimeSlots }
        val active = raw.activeTermId.ifBlank { terms.first().id }
        return raw.copy(
            version = 1,
            terms = terms,
            slots = slots,
            activeTermId = active,
            settings = raw.settings.copy(activeTermId = active),
        )
    }

    private fun persist(value: AppData) {
        writeAtomic(dataFile, gson.toJson(value)).onFailure {
            AppLog.error("Store", "写盘失败：${dataFile.absolutePath} :: ${it.javaClass.simpleName} ${it.message}（本次改动只在内存里，重启会丢）")
        }
    }

    /**
     * 同目录临时文件 + 原子改名。
     *
     * 不能「先删再改名」：那中间有一瞬间文件是不存在的，另一个进程此刻读盘就会拿到
     * 空文件并回落成示例数据；而且删掉后改名失败（目标被占用）就真把数据弄丢了。
     * 同目录内的 REPLACE_EXISTING 改名在 NTFS 上是原子的。
     *
     * 失败只返回结果、不写日志 —— 日志路径是用户的数据目录，单测不该往里写。
     */
    internal fun writeAtomic(target: File, text: String): Result<Unit> = runCatching {
        val temp = File(target.parentFile, target.name + ".tmp")
        target.parentFile?.mkdirs()
        temp.writeText(text, Charsets.UTF_8)
        try {
            Files.move(temp.toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING)
        } catch (e: IOException) {
            temp.delete()
            throw e
        }
    }

    fun update(block: (AppData) -> AppData) {
        val next = normalize(block(state))
        state = next
        persist(next)
    }

    fun replace(next: AppData) {
        val normalized = normalize(next)
        state = normalized
        persist(normalized)
    }

    /** 导出备份到用户选定的路径。 */
    fun exportTo(target: File): Result<Unit> = runCatching {
        target.parentFile?.mkdirs()
        target.writeText(gson.toJson(state), Charsets.UTF_8)
    }

    /** 从备份文件导入（整体替换）。 */
    fun importFrom(source: File): Result<Int> = runCatching {
        val parsed = gson.fromJson(source.readText(Charsets.UTF_8), AppData::class.java)
            ?: error("备份文件为空或格式不认识")
        replace(parsed)
        state.courses.size + state.todos.size
    }

    /** 恢复示例数据（用于第一次打开 / 清空后重新体验）。 */
    fun resetToSample() = replace(seedData().copy(origin = "seed"))

    /**
     * 覆盖数据前先备份到同目录（`pocket-desktop.bak-<时间>.json`），只留最近 10 份。
     *
     * 为什么值得留：清占位课表、教务导入替换、导入移动端 JSON 都是**不可逆**的整表覆盖，
     * 而用户发现「导入少了三门课」往往是半小时以后 —— 那时唯一的救命稻草就是这份备份。
     */
    fun backup(): File? = backupFile(dataFile, dataDir)

    /**
     * 备份的实现拆出来，是为了能在临时目录里测 —— 单测不该往用户的真实数据目录写文件。
     */
    internal fun backupFile(source: File, dir: File, keep: Int = 10): File? = runCatching {
        if (!source.exists()) return@runCatching null
        val stamp = java.text.SimpleDateFormat("yyyyMMdd-HHmmss").format(java.util.Date())
        val out = File(dir, "pocket-desktop.bak-$stamp.json")
        source.copyTo(out, overwrite = true)
        dir.listFiles { f: File -> f.name.startsWith("pocket-desktop.bak-") && f.name.endsWith(".json") }
            ?.sortedByDescending { it.name }
            ?.drop(keep)
            ?.forEach { runCatching { it.delete() } }
        out
    }.getOrNull()

    /** 内置示例课程的 id 集合（示例课 id 形如 `c-<hash>-<day>`，与手动 `c_…`、导入 `jw_…` 都不同）。 */
    fun sampleCourseIds(): Set<String> = seedData().courses.map { it.id }.toSet()

    /** 内置示例待办的 id 集合（`t1`…`t4`）。 */
    fun sampleTodoIds(): Set<String> = seedData().todos.map { it.id }.toSet()

    /** 内置示例课程的老师名（用来判断「导入进来的全是占位老师」）。 */
    fun sampleTeacherNames(): Set<String> = seedData().courses.map { it.teacher }.filter { it.isNotBlank() }.toSet()

    /** 当前数据的 JSON 文本（导出 / 剪贴板 / 备份都用这一份口径）。 */
    fun exportJson(): String = gson.toJson(state)

    /**
     * 解析导入的 JSON，**不落盘**（由调用方决定 replace）。
     *
     * 兼容两种形状：桌面端自己的 `AppData`，以及移动端备份的 `{ "data": {...} }` 包装。
     * 解析失败抛 [IllegalArgumentException]，消息是给用户看的白话。
     */
    fun parseImport(json: String): AppData {
        val root = runCatching { JsonParser.parseString(json).asJsonObject }
            .getOrElse { throw IllegalArgumentException("不是合法 JSON：${it.message}") }
        val dataObj = if (root.has("data") && root.get("data").isJsonObject) root.getAsJsonObject("data") else root
        val imported = runCatching { gson.fromJson(dataObj, AppData::class.java) }
            .getOrElse { throw IllegalArgumentException("JSON 结构不对：${it.message}") }
            ?: throw IllegalArgumentException("这份 JSON 是空的")
        if (imported.courses.isEmpty() && imported.todos.isEmpty() && imported.terms.isEmpty()) {
            throw IllegalArgumentException("这份 JSON 里没有课程 / 待办 / 学期数据")
        }
        return imported
    }

    /** 当前学期与「当前第几周」。 */
    fun currentWeek(today: LocalDate = LocalDate.now()): Int {
        val term = data.activeTerm ?: return 0
        val start = runCatching { LocalDate.parse(term.startDate) }.getOrNull() ?: return 0
        // 周次算法与手机端同一份（core/calendar/WeekCalendarCore）：开学前没有周次（0），
        // 超过学期总周数夹到最后一上周 —— 手机端一直是夹取的，桌面原来会一直涨。
        val raw = com.pocket.xiaoli.core.calendar.WeekCalendarCore.weekOf(
            startEpochDay = start.toEpochDay(),
            todayEpochDay = today.toEpochDay(),
        ) ?: return 0
        return raw.coerceIn(1, maxOf(1, term.totalWeeks))
    }
}
