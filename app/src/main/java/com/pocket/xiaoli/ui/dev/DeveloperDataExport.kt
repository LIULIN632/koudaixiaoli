package com.pocket.xiaoli.ui.dev

import androidx.sqlite.db.SupportSQLiteDatabase
import com.google.gson.GsonBuilder
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.pocket.xiaoli.data.database.BackupFormat
import com.pocket.xiaoli.data.entity.CourseEntity
import com.pocket.xiaoli.data.entity.TimeSlotEntity
import com.pocket.xiaoli.util.CourseIcsExporter
import java.time.LocalDateTime

/**
 * 开发者实验室的数据导入 / 导出工具。
 *
 * JSON 用于整库排查，CSV / ICS 面向课程表数据。
 * 只做导出，不直接修改数据库；导入沿用现有 [com.pocket.xiaoli.util.importCoursesFromUri]。
 */
object DeveloperDataExport {

    private val gson = GsonBuilder()
        .setPrettyPrinting()
        .serializeNulls()
        .disableHtmlEscaping()
        .create()

    /** 导出所有业务表为单个 JSON 对象（每个表**全部行**，见 [RoomDataViewer.EXPORT_PAGE_SIZE]）。 */
    fun databaseToJson(db: SupportSQLiteDatabase): String {
        val root = JsonObject()
        root.addProperty(BackupFormat.EXPORTED_AT_KEY, System.currentTimeMillis())
        root.addProperty(BackupFormat.DATABASE_VERSION_KEY, db.version)

        val tables = JsonObject()
        RoomDataViewer.listTables(db).forEach { table ->
            val json = RoomDataViewer.exportJson(db, table.name)
            tables.add(table.name, JsonParser.parseString(json))
        }
        root.add(BackupFormat.TABLES_KEY, tables)
        return gson.toJson(root)
    }

    /** 当前方案课程导出为 CSV。 */
    fun coursesToCsv(courses: List<CourseEntity>): String {
        // ⚠️ 加列时**必须同步加值**（下面 cells 的顺序与这里一一对应）：
        // 只加表头不加值，导出的 CSV 会整体错位一格，而那种错误肉眼很难看出来。
        val header = listOf(
            "id", "name", "shortName", "credits", "teacher", "room",
            "dayOfWeek", "startSlot", "endSlot", "weekType", "weeks", "colorIndex", "colorPinned", "schemeId",
        )
        return buildString {
            append(header.joinToString(","))
            append('\n')
            courses.forEach { course ->
                val cells = listOf(
                    course.id, course.name, course.shortName, course.credits, course.teacher, course.room,
                    course.dayOfWeek, course.startSlot, course.endSlot, course.weekType,
                    course.weeks, course.colorIndex, if (course.colorPinned) 1 else 0, course.schemeId,
                )
                append(cells.joinToString(",") { csvEscape(it.toString()) })
                append('\n')
            }
        }
    }

    /** 当前方案课程导出为 ICS；具体格式见 [CourseIcsExporter]。 */
    fun coursesToIcs(
        courses: List<CourseEntity>,
        timeSlots: List<TimeSlotEntity>,
        semesterStart: String,
        totalWeeks: Int,
        now: LocalDateTime = LocalDateTime.now(),
    ): String {
        return CourseIcsExporter.export(courses, timeSlots, semesterStart, totalWeeks, now)
    }

    private fun csvEscape(value: String): String {
        if (value.none { it == ',' || it == '"' || it == '\n' || it == '\r' }) return value
        return "\"" + value.replace("\"", "\"\"") + "\""
    }
}
