package com.pocket.xiaoli.core.calendar

/**
 * 内置节假日数据（2026 年），来源：国务院办公厅放假通知（经 NateScarlet/holiday-cn 结构化）。
 *
 * 生成方式：scripts/gen-holidays.ps1 从 `2026.json` 直出，**不要手改**。
 * 原始公告：https://www.gov.cn/zhengce/zhengceku/202511/content_7047091.htm
 *
 * `isOff = true` 是放假；`false` 是**调休上班**（周末补班）。
 */
val BUILTIN_HOLIDAYS_2026: List<HolidayDay> = listOf(
    HolidayDay(epochDay = 20454L, name = "元旦", isOff = true), // 2026-01-01
    HolidayDay(epochDay = 20455L, name = "元旦", isOff = true), // 2026-01-02
    HolidayDay(epochDay = 20456L, name = "元旦", isOff = true), // 2026-01-03
    HolidayDay(epochDay = 20457L, name = "元旦", isOff = false), // 2026-01-04
    HolidayDay(epochDay = 20498L, name = "春节", isOff = false), // 2026-02-14
    HolidayDay(epochDay = 20499L, name = "春节", isOff = true), // 2026-02-15
    HolidayDay(epochDay = 20500L, name = "春节", isOff = true), // 2026-02-16
    HolidayDay(epochDay = 20501L, name = "春节", isOff = true), // 2026-02-17
    HolidayDay(epochDay = 20502L, name = "春节", isOff = true), // 2026-02-18
    HolidayDay(epochDay = 20503L, name = "春节", isOff = true), // 2026-02-19
    HolidayDay(epochDay = 20504L, name = "春节", isOff = true), // 2026-02-20
    HolidayDay(epochDay = 20505L, name = "春节", isOff = true), // 2026-02-21
    HolidayDay(epochDay = 20506L, name = "春节", isOff = true), // 2026-02-22
    HolidayDay(epochDay = 20507L, name = "春节", isOff = true), // 2026-02-23
    HolidayDay(epochDay = 20512L, name = "春节", isOff = false), // 2026-02-28
    HolidayDay(epochDay = 20547L, name = "清明节", isOff = true), // 2026-04-04
    HolidayDay(epochDay = 20548L, name = "清明节", isOff = true), // 2026-04-05
    HolidayDay(epochDay = 20549L, name = "清明节", isOff = true), // 2026-04-06
    HolidayDay(epochDay = 20574L, name = "劳动节", isOff = true), // 2026-05-01
    HolidayDay(epochDay = 20575L, name = "劳动节", isOff = true), // 2026-05-02
    HolidayDay(epochDay = 20576L, name = "劳动节", isOff = true), // 2026-05-03
    HolidayDay(epochDay = 20577L, name = "劳动节", isOff = true), // 2026-05-04
    HolidayDay(epochDay = 20578L, name = "劳动节", isOff = true), // 2026-05-05
    HolidayDay(epochDay = 20582L, name = "劳动节", isOff = false), // 2026-05-09
    HolidayDay(epochDay = 20623L, name = "端午节", isOff = true), // 2026-06-19
    HolidayDay(epochDay = 20624L, name = "端午节", isOff = true), // 2026-06-20
    HolidayDay(epochDay = 20625L, name = "端午节", isOff = true), // 2026-06-21
    HolidayDay(epochDay = 20716L, name = "国庆节", isOff = false), // 2026-09-20
    HolidayDay(epochDay = 20721L, name = "中秋节", isOff = true), // 2026-09-25
    HolidayDay(epochDay = 20722L, name = "中秋节", isOff = true), // 2026-09-26
    HolidayDay(epochDay = 20723L, name = "中秋节", isOff = true), // 2026-09-27
    HolidayDay(epochDay = 20727L, name = "国庆节", isOff = true), // 2026-10-01
    HolidayDay(epochDay = 20728L, name = "国庆节", isOff = true), // 2026-10-02
    HolidayDay(epochDay = 20729L, name = "国庆节", isOff = true), // 2026-10-03
    HolidayDay(epochDay = 20730L, name = "国庆节", isOff = true), // 2026-10-04
    HolidayDay(epochDay = 20731L, name = "国庆节", isOff = true), // 2026-10-05
    HolidayDay(epochDay = 20732L, name = "国庆节", isOff = true), // 2026-10-06
    HolidayDay(epochDay = 20733L, name = "国庆节", isOff = true), // 2026-10-07
    HolidayDay(epochDay = 20736L, name = "国庆节", isOff = false), // 2026-10-10
)
