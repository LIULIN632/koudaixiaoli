package com.pocket.xiaoli

import android.app.Application
import android.util.Log
import com.pocket.xiaoli.core.academic.AcademicLog
import com.pocket.xiaoli.core.academic.ImportMemoryBridge
import com.pocket.xiaoli.core.academic.ImportMemoryStore
import com.pocket.xiaoli.core.academic.NetworkLogBridge
import com.pocket.xiaoli.data.database.PocketXiaoliDatabase
import com.pocket.xiaoli.data.learning.ChaoxingWorkLinks
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
        // 共享教务解析器的日志转发到 Logcat（解析代码在 :core，不能直接依赖 android.util.Log）
        AcademicLog.sink = { level, tag, message, error ->
            when (level) {
                'd' -> Log.d(tag, message)
                'w' -> Log.w(tag, message)
                else -> if (error != null) Log.e(tag, message, error) else Log.e(tag, message)
            }
        }
        CookieStore.init(this)
        // 学习通「作业列表签名链接」缓存（见 ChaoxingWorkLinks 的注释：不逆向签名，缓存现取的那条 URL）
        ChaoxingWorkLinks.init(this)
        ZhengfangImportMemory.init(this)
        // 共享客户端只认接口：把「上次成功的课表接口组合」接到 Android 的 SharedPreferences
        ImportMemoryBridge.impl = object : ImportMemoryStore {
            override fun load(baseUrl: String) = ZhengfangImportMemory.load(baseUrl)

            override fun save(baseUrl: String, urlPattern: String, bodyTemplate: String) =
                ZhengfangImportMemory.save(baseUrl, urlPattern, bodyTemplate)
        }
        NetworkLogBridge.interceptor = com.pocket.xiaoli.network.NetworkLogInterceptor
        ClassReminderScheduler.ensureChannel(this)
        ClassReminderScheduler.rescheduleAsync(this)
        appContainer.refreshHolidays()
        // 匿名运行统计：默认开、可在设置里关；没配地址时它自己什么都不做。
        // 放在最后，别让统计的初始化拖慢冷启动（它内部已经切到 IO 线程）。
        Telemetry.init(this)
    }
}
