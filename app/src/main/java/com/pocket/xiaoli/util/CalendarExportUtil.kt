package com.pocket.xiaoli.util

import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.provider.CalendarContract
import com.pocket.xiaoli.data.entity.CourseEntity
import com.pocket.xiaoli.data.entity.defaultTimeSlots
import java.time.LocalTime
import java.util.*

object CalendarExportUtil {

    private const val CALENDAR_NAME = "口袋小鲤"
    private const val CALENDAR_COLOR = 0xFF8B95A8.toInt()

    /**
     * Export courses to system calendar. Returns the number of events created.
     */
    fun exportToCalendar(
        context: Context,
        courses: List<CourseEntity>,
        semesterStart: Long,
        semesterEnd: Long
    ): Int {
        val calendarId = getOrCreateCalendar(context)

        // Delete existing events from this calendar to avoid duplicates
        context.contentResolver.delete(
            CalendarContract.Events.CONTENT_URI,
            "${CalendarContract.Events.CALENDAR_ID} = ?",
            arrayOf(calendarId.toString())
        )

        // 节次时间取自默认节次表（唯一真源）。⚠️ 这条路径拿不到用户的 time_slots，
        // 所以自定义过节次的用户导出的是默认时间 —— 以前这里还另写了一份"近似表"
        // （slot 5 = 11:50 之类），与默认节次表对不上，属于第二个真源，已删。
        val slotByNumber = defaultTimeSlots.associateBy { it.slot }

        var count = 0

        for (course in courses) {
            val startSlot = slotByNumber[course.startSlot] ?: defaultTimeSlots.first()
            val endSlot = slotByNumber[course.endSlot] ?: defaultTimeSlots.last()
            val start = parseTimeOrNull(startSlot.startTime) ?: LocalTime.of(8, 0)
            val end = parseTimeOrNull(endSlot.endTime) ?: start.plusMinutes(45)
            val startH = start.hour
            val startM = start.minute
            val endH = end.hour
            val endM = end.minute

            // Calculate the first occurrence on the target day of week
            val cal = Calendar.getInstance().apply {
                timeInMillis = semesterStart
                // Adjust to the target day of week
                val targetDay = (course.dayOfWeek - 1) % 7 + 1 // 1=Sun, ..., 7=Sat
                while (get(Calendar.DAY_OF_WEEK) != targetDay) {
                    add(Calendar.DAY_OF_MONTH, 1)
                }
            }

            val values = ContentValues().apply {
                put(CalendarContract.Events.CALENDAR_ID, calendarId)
                put(CalendarContract.Events.TITLE, course.name)
                put(CalendarContract.Events.EVENT_LOCATION, course.room)
                put(CalendarContract.Events.DESCRIPTION, buildString {
                    if (course.teacher.isNotEmpty()) append("教师: ${course.teacher}")
                    if (course.teacher.isNotEmpty() && course.room.isNotEmpty()) append("\n")
                    if (course.room.isNotEmpty()) append("教室: ${course.room}")
                })
                put(CalendarContract.Events.DTSTART, cal.apply {
                    set(Calendar.HOUR_OF_DAY, startH)
                    set(Calendar.MINUTE, startM)
                }.timeInMillis)
                put(CalendarContract.Events.DTEND, cal.apply {
                    set(Calendar.HOUR_OF_DAY, endH)
                    set(Calendar.MINUTE, endM)
                }.timeInMillis)
                put(CalendarContract.Events.RRULE,
                    "FREQ=WEEKLY;UNTIL=${formatRRuleDate(semesterEnd)}")
                put(CalendarContract.Events.EVENT_TIMEZONE, TimeZone.getDefault().id)
                put(CalendarContract.Events.EVENT_COLOR, CALENDAR_COLOR)
            }

            context.contentResolver.insert(CalendarContract.Events.CONTENT_URI, values)
            count++
        }

        return count
    }

    private fun getOrCreateCalendar(context: Context): Long {
        // Look for existing calendar
        val projection = arrayOf(
            CalendarContract.Calendars._ID,
            CalendarContract.Calendars.NAME
        )
        val selection = "${CalendarContract.Calendars.NAME} = ?"
        val cursor = context.contentResolver.query(
            CalendarContract.Calendars.CONTENT_URI,
            projection, selection, arrayOf(CALENDAR_NAME), null
        )
        cursor?.use {
            if (it.moveToFirst()) {
                return it.getLong(0)
            }
        }

        // Create new calendar
        val values = ContentValues().apply {
            put(CalendarContract.Calendars.NAME, CALENDAR_NAME)
            put(CalendarContract.Calendars.CALENDAR_DISPLAY_NAME, CALENDAR_NAME)
            put(CalendarContract.Calendars.CALENDAR_COLOR, CALENDAR_COLOR)
            put(CalendarContract.Calendars.CALENDAR_ACCESS_LEVEL,
                CalendarContract.Calendars.CAL_ACCESS_OWNER)
            put(CalendarContract.Calendars.OWNER_ACCOUNT, "")
            put(CalendarContract.Calendars.VISIBLE, 1)
            put(CalendarContract.Calendars.SYNC_EVENTS, 1)
        }

        val uri = CalendarContract.Calendars.CONTENT_URI.buildUpon()
            .appendQueryParameter(CalendarContract.CALLER_IS_SYNCADAPTER, "true")
            .appendQueryParameter(CalendarContract.Calendars.ACCOUNT_NAME, "")
            .appendQueryParameter(CalendarContract.Calendars.ACCOUNT_TYPE, "")
            .build()

        val newUri = context.contentResolver.insert(uri, values)
        return newUri?.lastPathSegment?.toLongOrNull() ?: 1L
    }

    private fun formatRRuleDate(timestamp: Long): String {
        val cal = Calendar.getInstance().apply { timeInMillis = timestamp }
        return String.format(
            Locale.US, "%04d%02d%02dT235959Z",
            cal.get(Calendar.YEAR),
            cal.get(Calendar.MONTH) + 1,
            cal.get(Calendar.DAY_OF_MONTH)
        )
    }
}
