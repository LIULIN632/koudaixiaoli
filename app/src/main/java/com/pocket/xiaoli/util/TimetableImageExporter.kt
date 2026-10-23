package com.pocket.xiaoli.util

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.net.Uri
import androidx.core.content.FileProvider
import com.pocket.xiaoli.data.entity.CourseEntity
import com.pocket.xiaoli.data.entity.TimeSlotEntity
import com.pocket.xiaoli.notification.ClassReminderScheduler
import java.io.File
import java.io.FileOutputStream

/** 把当前周课表绘制成 PNG 并返回可分享的 content:// Uri。 */
object TimetableImageExporter {

    private val BG_COLORS = intArrayOf(
        0xFFFFE8E5.toInt(), 0xFFDEE8FF.toInt(), 0xFFD8F0E2.toInt(), 0xFFEDE2F8.toInt(),
        0xFFFFEAD6.toInt(), 0xFFFFDEE0.toInt(), 0xFFD6EDF2.toInt(), 0xFFD0EBD8.toInt(),
        0xFFFFF0D6.toInt(), 0xFFE2E2F6.toInt(), 0xFFFFD8D6.toInt(), 0xFFD2F0D2.toInt(),
        0xFFFFF8D6.toInt(), 0xFFF0DCF6.toInt(), 0xFFD6E6FF.toInt()
    )
    private val TEXT_COLORS = intArrayOf(
        0xFF8B5A5E.toInt(), 0xFF4A5C82.toInt(), 0xFF4A6C56.toInt(), 0xFF584A78.toInt(),
        0xFF826448.toInt(), 0xFF824E50.toInt(), 0xFF486A80.toInt(), 0xFF486A5C.toInt(),
        0xFF826448.toInt(), 0xFF4E4E7A.toInt(), 0xFF824A4C.toInt(), 0xFF3E6A3E.toInt(),
        0xFF826E40.toInt(), 0xFF6A4680.toInt(), 0xFF3E6280.toInt()
    )
    private val WEEK_DAYS = listOf("周一", "周二", "周三", "周四", "周五", "周六", "周日")

    fun export(
        context: Context,
        courses: List<CourseEntity>,
        slots: List<TimeSlotEntity>,
        week: Int,
    ): Uri? {
        if (slots.isEmpty()) return null
        val density = context.resources.displayMetrics.density
        val leftW = 76f * density
        val cellW = 108f * density
        val cellH = 84f * density
        val headerH = 56f * density
        val padding = 8f * density

        val width = (leftW + cellW * 7).toInt()
        val height = (headerH + cellH * slots.size).toInt()
        if (width <= 0 || height <= 0) return null

        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.drawColor(Color.WHITE)

        val gridPaint = Paint().apply {
            color = 0xFFEEEEEE.toInt()
            style = Paint.Style.STROKE
            strokeWidth = 1f * density
        }
        val titlePaint = Paint().apply {
            color = 0xFF1F2937.toInt()
            textSize = 13f * density
            typeface = Typeface.DEFAULT_BOLD
            isAntiAlias = true
        }
        val subPaint = Paint().apply {
            color = 0xFF6B7280.toInt()
            textSize = 11f * density
            isAntiAlias = true
        }

        // 表头
        canvas.drawText("第 $week 周", padding, headerH / 2f + titlePaint.textSize / 3f, titlePaint)
        WEEK_DAYS.forEachIndexed { index, label ->
            val cx = leftW + cellW * index + cellW / 2f
            val textWidth = subPaint.measureText(label)
            canvas.drawText(label, cx - textWidth / 2f, headerH / 2f + subPaint.textSize / 3f, subPaint)
        }

        // 网格 + 节次
        for (row in slots.indices) {
            val top = headerH + cellH * row
            canvas.drawText(
                slots[row].startTime,
                padding,
                top + cellH / 2f + subPaint.textSize / 3f,
                subPaint
            )
        }
        for (col in 0..7) {
            val x = leftW + cellW * col
            canvas.drawLine(x, 0f, x, height.toFloat(), gridPaint)
        }
        for (row in 0..slots.size) {
            val y = headerH + cellH * row
            canvas.drawLine(0f, y, width.toFloat(), y, gridPaint)
        }
        canvas.drawLine(0f, headerH, width.toFloat(), headerH, gridPaint)

        // 课程块
        val coursePaint = Paint().apply { style = Paint.Style.FILL }
        val courseTextPaint = Paint().apply {
            textSize = 11f * density
            typeface = Typeface.DEFAULT_BOLD
            isAntiAlias = true
        }
        val roomPaint = Paint().apply {
            textSize = 9f * density
            isAntiAlias = true
        }
        courses
            .filter { ClassReminderScheduler.isActiveWeek(it, week) }
            .forEach { course ->
                val col = (course.dayOfWeek - 1).coerceIn(0, 6)
                val startRow = (course.startSlot - 1).coerceIn(0, slots.size - 1)
                val endRow = (course.endSlot - 1).coerceIn(startRow, slots.size - 1)
                // ⚠️ 必须用 effectiveColorIndex（按课名派生 / 用户钉住），不能直接读 colorIndex：
                // 后者是导入时按位置分配的占位值，直接读就会出现"同一门课导出成两种颜色"。
                val colorIndex = ((course.effectiveColorIndex % BG_COLORS.size) + BG_COLORS.size) % BG_COLORS.size
                coursePaint.color = BG_COLORS[colorIndex]
                courseTextPaint.color = TEXT_COLORS[colorIndex]
                roomPaint.color = TEXT_COLORS[colorIndex]

                val rect = RectF(
                    leftW + cellW * col + 2f * density,
                    headerH + cellH * startRow + 2f * density,
                    leftW + cellW * (col + 1) - 2f * density,
                    headerH + cellH * (endRow + 1) - 2f * density
                )
                canvas.drawRoundRect(rect, 8f * density, 8f * density, coursePaint)

                val textLeft = rect.left + 5f * density
                val maxTextWidth = rect.width() - 10f * density
                var nameY = rect.top + 14f * density
                splitLines(course.name, courseTextPaint, maxTextWidth).take(2).forEach { line ->
                    canvas.drawText(line, textLeft, nameY, courseTextPaint)
                    nameY += 13f * density
                }
                if (course.room.isNotBlank()) {
                    canvas.drawText(
                        fitText(course.room, roomPaint, maxTextWidth),
                        textLeft,
                        nameY,
                        roomPaint
                    )
                }
            }

        val file = File(context.cacheDir, "timetable_week_$week.png")
        return try {
            FileOutputStream(file).use { out ->
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
            }
            FileProvider.getUriForFile(context, context.packageName + ".fileprovider", file)
        } catch (_: Exception) {
            null
        } finally {
            bitmap.recycle()
        }
    }

    private fun splitLines(text: String, paint: Paint, maxWidth: Float): List<String> {
        val lines = mutableListOf<String>()
        var current = StringBuilder()
        text.forEach { ch ->
            val candidate = current.toString() + ch
            if (paint.measureText(candidate) > maxWidth && current.isNotEmpty()) {
                lines.add(current.toString())
                current = StringBuilder()
            }
            current.append(ch)
        }
        if (current.isNotEmpty()) lines.add(current.toString())
        return lines.ifEmpty { listOf(text) }
    }

    private fun fitText(text: String, paint: Paint, maxWidth: Float): String {
        if (paint.measureText(text) <= maxWidth) return text
        var value = text
        while (value.length > 1 && paint.measureText("$value…") > maxWidth) {
            value = value.dropLast(1)
        }
        return "$value…"
    }
}