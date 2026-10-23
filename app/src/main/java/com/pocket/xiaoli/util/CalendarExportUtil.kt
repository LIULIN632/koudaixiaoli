package com.pocket.xiaoli.util

import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.provider.CalendarContract
import com.pocket.xiaoli.data.entity.CourseEntity
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

        // Time slot mapping: slot N → approximate time
        // Typical university: slot 1 = 8:00, slot 2 = 8:55, slot 3 = 9:50, ...
        val slotHour = mapOf(
            1 to 8, 2 to 8, 3 to 10, 4 to 10, 5 to 11,
            6 to 14, 7 to 14, 8 to 16, 9 to 16, 10 to 19,
            11 to 19, 12 to 20
        )
        val slotMinute = mapOf(
            1 to 0, 2 to 55, 3 to 0, 4 to 55, 5 to 50,
            6 to 0, 7 to 55, 8 to 0, 9 to 55, 10 to 0,
            11 to 55, 12 to 0
        )
        val durationMin = 45 // each slot = 45 min class

        var count = 0

        for (course in courses) {
            val startH = slotHour[course.startSlot] ?: 8
            val startM = slotMinute[course.startSlot] ?: 0
            val endSlots = course.endSlot - course.startSlot + 1
            val endTotalMin = startH * 60 + startM + endSlots * durationMin
            val endH = endTotalMin / 60
            val endM = endTotalMin % 60

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
