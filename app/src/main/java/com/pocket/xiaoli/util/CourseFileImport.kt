package com.pocket.xiaoli.util

import android.content.Context
import android.net.Uri
import com.pocket.xiaoli.data.entity.CourseEntity
import com.pocket.xiaoli.data.repository.CourseRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import java.util.zip.ZipInputStream

/** Result of importing courses from a user-picked file. */
internal sealed interface CourseImportOutcome {
    data class Success(val added: Int, val parsed: Int) : CourseImportOutcome
    object Empty : CourseImportOutcome
    object AllDuplicates : CourseImportOutcome
    data class Failure(val message: String?) : CourseImportOutcome
}

/**
 * Shared file import path for Excel / HTML / CSV / JSON backup.
 * Normalizes backup courses to the target scheme and deduplicates by
 * (name, dayOfWeek, startSlot) against that scheme only.
 *
 * 解析判断本身在 [CourseFileParsers]（纯函数，可被固定样例驱动）；
 * 这里只负责**取字节**与**落库**。
 */
internal suspend fun importCoursesFromUri(
    context: Context,
    repository: CourseRepository,
    uri: Uri,
    type: String,
    schemeId: Long = 0L
): CourseImportOutcome = withContext(Dispatchers.IO) {
    try {
        val parsed = when (type) {
            "excel" -> readExcel(context, uri, schemeId)
            "html" -> readText(context, uri).let { CourseFileParsers.html(it, schemeId) }
            "csv" -> readText(context, uri).let { CourseFileParsers.csv(it, schemeId) }
            "backup" -> readText(context, uri).let { CourseFileParsers.backup(it, schemeId) }
            else -> emptyList()
        }
        if (parsed.isEmpty()) return@withContext CourseImportOutcome.Empty

        val normalized = if (type == "backup") {
            parsed.map { it.copy(id = 0, schemeId = schemeId) }
        } else {
            parsed
        }
        val existing = repository.getCoursesByScheme(schemeId).first()
        val newCourses = CourseFileParsers.mergeNew(existing, normalized)
        if (newCourses.isEmpty()) return@withContext CourseImportOutcome.AllDuplicates

        repository.insertAll(newCourses)
        CourseImportOutcome.Success(added = newCourses.size, parsed = normalized.size)
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        CourseImportOutcome.Failure(e.message)
    }
}

/** 以 xlsx 的 ZIP 内部结构读 Excel；拿不到 sheet1 就当作读不出内容。 */
private fun readExcel(context: Context, uri: Uri, schemeId: Long): List<CourseEntity> =
    try {
        var sharedStringsXml = ""
        var sheetXml = ""
        context.contentResolver.openInputStream(uri)?.use { inputStream ->
            val zip = ZipInputStream(inputStream)
            var entry = zip.nextEntry
            while (entry != null) {
                when (entry.name) {
                    "xl/sharedStrings.xml" -> sharedStringsXml = zip.bufferedReader().readText()
                    "xl/worksheets/sheet1.xml" -> sheetXml = zip.bufferedReader().readText()
                }
                entry = zip.nextEntry
            }
        }
        CourseFileParsers.xlsx(sharedStringsXml, sheetXml, schemeId)
    } catch (_: Exception) {
        emptyList()
    }

private fun readText(context: Context, uri: Uri): String =
    try {
        context.contentResolver.openInputStream(uri)?.use { it.bufferedReader().readText() } ?: ""
    } catch (_: Exception) {
        ""
    }
