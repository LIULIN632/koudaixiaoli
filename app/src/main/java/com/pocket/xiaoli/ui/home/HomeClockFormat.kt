package com.pocket.xiaoli.ui.home

import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * 首页时钟的两个格式器。
 *
 * 单独放一个文件是为了让 `HomeScreen.kt` 停在 800 行门槛以内，但**留在这儿**的理由是：
 * [HomeClockHeader] 由走秒驱动，每秒重组一次 —— 原来那两行**每秒新建两个
 * `DateTimeFormatter`**。它不可变、线程安全，全 App 共用一份就够，
 * 而首页是停留最久的一页，别在这儿给垃圾回收交税。
 */
internal val clockTimeFormat = DateTimeFormatter.ofPattern("HH:mm", Locale.CHINA)
internal val clockDateFormat = DateTimeFormatter.ofPattern("M月d日 EEEE", Locale.CHINA)
