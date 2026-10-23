package com.pocket.xiaoli

import android.app.Application
import com.pocket.xiaoli.data.database.PocketXiaoliDatabase
import com.pocket.xiaoli.data.repository.CourseRepository
import com.pocket.xiaoli.di.AppContainer
import com.pocket.xiaoli.network.CookieStore
import com.pocket.xiaoli.network.ZhengfangImportMemory
import com.pocket.xiaoli.notification.ClassReminderScheduler
import com.pocket.xiaoli.telemetry.Telemetry
import com.pocket.xiaoli.util.CrashHandler

class PocketXiaoliApplication : Application() {
    val database: PocketXiaoliDatabase by lazy { PocketXiaoliDatabase.getInstance(this) }
    val repository: CourseRepository by lazy {
        CourseRepository(database.courseDao(), database.teacherDao(), database.examDao())
    }
    val appContainer: AppContainer by lazy { AppContainer(this) }
    lateinit var crashHandler: CrashHandler

    override fun onCreate() {
        super.onCreate()
        crashHandler = CrashHandler(this)
        CookieStore.init(this)
        ZhengfangImportMemory.init(this)
        ClassReminderScheduler.ensureChannel(this)
        ClassReminderScheduler.rescheduleAsync(this)
        appContainer.refreshHolidays()
        // 匿名运行统计：默认开、可在设置里关；没配地址时它自己什么都不做。
        // 放在最后，别让统计的初始化拖慢冷启动（它内部已经切到 IO 线程）。
        Telemetry.init(this)
    }
}
