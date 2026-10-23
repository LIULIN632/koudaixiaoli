package com.pocket.xiaoli.appwidget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.view.View
import android.widget.RemoteViews
import com.pocket.xiaoli.MainActivity
import com.pocket.xiaoli.PocketXiaoliApplication
import com.pocket.xiaoli.R
import com.pocket.xiaoli.data.entity.CourseEntity
import com.pocket.xiaoli.data.entity.TimeSlotEntity
import com.pocket.xiaoli.notification.ClassReminderScheduler
import com.pocket.xiaoli.util.parseSemesterStart
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.temporal.ChronoUnit

internal data class WidgetCourse(val name: String, val time: String, val room: String)

internal fun todayCourses(
    courses: List<CourseEntity>,
    slots: List<TimeSlotEntity>,
    semesterStart: String,
    now: LocalDateTime,
): List<WidgetCourse> {
    if (courses.isEmpty() || slots.isEmpty()) return emptyList()
    val slotMap = slots.associateBy { it.slot }
    val startDate = parseSemesterStart(semesterStart)
    val weekNo = if (startDate != null) {
        (ChronoUnit.DAYS.between(startDate, now.toLocalDate()).toInt() / 7) + 1
    } else {
        1
    }
    return courses
        .filter { it.dayOfWeek == now.dayOfWeek.value }
        .filter { startDate == null || ClassReminderScheduler.isActiveWeek(it, weekNo) }
        .sortedBy { it.startSlot }
        .mapNotNull { course ->
            val slot = slotMap[course.startSlot] ?: return@mapNotNull null
            WidgetCourse(name = course.name, time = slot.startTime, room = course.room)
        }
}

class ClassWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray,
    ) {
        val pendingResult = goAsync()
        val appContext = context.applicationContext
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val app = appContext as? PocketXiaoliApplication
                val ids = if (appWidgetIds.isNotEmpty()) {
                    appWidgetIds
                } else {
                    appWidgetManager.getAppWidgetIds(
                        ComponentName(appContext, ClassWidgetProvider::class.java)
                    )
                }
                val container = app?.appContainer
                val enabled = container?.showWidget?.value ?: false
                val courses = if (app != null && enabled) {
                    app.repository.getCoursesByScheme(container!!.activeSchemeId.value).first()
                } else {
                    emptyList()
                }
                val slots = if (app != null && enabled) app.repository.allTimeSlots.first() else emptyList()
                val now = LocalDateTime.now()
                val today = todayCourses(courses, slots, container?.semesterStart?.value.orEmpty(), now)
                ids.forEach { id ->
                    appWidgetManager.updateAppWidget(id, buildViews(appContext, enabled, today, now))
                }
            } catch (_: Exception) {
                // 小组件失败不影响 App
            } finally {
                pendingResult.finish()
            }
        }
    }

    companion object {
        fun refresh(context: Context) {
            val manager = AppWidgetManager.getInstance(context)
            val ids = manager.getAppWidgetIds(ComponentName(context, ClassWidgetProvider::class.java))
            if (ids.isEmpty()) return
            val intent = Intent(context, ClassWidgetProvider::class.java).apply {
                action = AppWidgetManager.ACTION_APPWIDGET_UPDATE
                putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, ids)
            }
            context.sendBroadcast(intent)
        }
    }
}

private val WIDGET_ROW_IDS = intArrayOf(R.id.widget_row_1, R.id.widget_row_2, R.id.widget_row_3)

private fun buildViews(
    context: Context,
    enabled: Boolean,
    today: List<WidgetCourse>,
    now: LocalDateTime,
): RemoteViews {
    val views = RemoteViews(context.packageName, R.layout.widget_class)
    val openIntent = PendingIntent.getActivity(
        context,
        0,
        Intent(context, MainActivity::class.java),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
    )
    views.setOnClickPendingIntent(R.id.widget_root, openIntent)

    if (!enabled) {
        views.setTextViewText(R.id.widget_title, "口袋小鲤")
        views.setTextViewText(R.id.widget_next, "在设置中开启桌面小组件")
        WIDGET_ROW_IDS.forEach { views.setViewVisibility(it, View.GONE) }
        return views
    }
    if (today.isEmpty()) {
        views.setTextViewText(R.id.widget_title, "今日课程")
        views.setTextViewText(R.id.widget_next, "今天没有课,好好休息")
        WIDGET_ROW_IDS.forEach { views.setViewVisibility(it, View.GONE) }
        return views
    }

    val nowTime = now.toLocalTime()
    val next = today.firstOrNull {
        runCatching { LocalTime.parse(it.time) }.getOrNull()?.let { t -> !t.isBefore(nowTime) } == true
    } ?: today.first()
    views.setTextViewText(R.id.widget_title, "下一节 · ${next.time}")
    views.setTextViewText(
        R.id.widget_next,
        next.name + if (next.room.isNotBlank()) "  @${next.room}" else ""
    )
    WIDGET_ROW_IDS.forEachIndexed { index, rowId ->
        val item = today.getOrNull(index)
        if (item == null) {
            views.setViewVisibility(rowId, View.GONE)
        } else {
            views.setViewVisibility(rowId, View.VISIBLE)
            views.setTextViewText(rowId, "${item.time}  ${item.name}")
        }
    }
    return views
}