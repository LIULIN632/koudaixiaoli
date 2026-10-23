# Room
-keep class * extends androidx.room.RoomDatabase
-keep @androidx.room.Entity class *
-dontwarn androidx.room.paging.**
-keepclassmembers class * {
    @androidx.room.* <fields>;
    @androidx.room.* <methods>;
}

# Gson
-keepattributes Signature
-keepattributes *Annotation*
-keepclassmembers class com.pocket.xiaoli.** { <fields>; }
-keep class com.pocket.xiaoli.data.entity.** { *; }
-keep class com.pocket.xiaoli.network.** { *; }
-keep class com.pocket.xiaoli.ui.import_.ImportCourseJson { *; }

# 趣智校园（洗澡）的响应模型 —— 同样全靠 Gson 反射反序列化。
#
# ⚠️ 下面那条 `-keepclassmembers com.pocket.xiaoli.** { <fields>; }` 只管住**字段名不被改**，
# 但**管不住 R8 把"看起来没人读"的字段整个裁掉** —— 而这些模型恰恰是这样：
# 字段由 Gson 通过反射赋值，代码里没有直接读写，R8 静态分析看不见这个用途。
# 字段一旦被裁，反序列化会静默拿到 null（本项目 `DormInfo` 就是被这类问题坑过，
# 见 CLAUDE.md「给存在 SharedPreferences 里的 JSON 类加字段」一节）。
#
# 所以照 entity / network 的先例，把这套模型整体 keep 住。
-keep class com.pocket.xiaoli.data.qzxy.** { *; }

# OkHttp + Okio
-dontwarn okhttp3.**
-dontwarn okio.**

# Coroutines
-keepnames class kotlinx.coroutines.internal.MainDispatcherFactory {}
-keepnames class kotlinx.coroutines.CoroutineExceptionHandler {}
-keepclassmembernames class kotlinx.coroutines.** {
    volatile <fields>;
}
-keep class kotlin.coroutines.jvm.internal.** { *; }

# Compose
-dontwarn androidx.compose.**

# Keep data classes used in network/DB
-keepclassmembers class com.pocket.xiaoli.** {
    !transient <fields>;
}

# ── WorkManager：Worker 是靠反射实例化的 ────────────────────────────────────
# 为什么必须写：WorkManager 把 Worker 的**类名当成字符串**存进自己的数据库
# （WorkSpec.workerClassName）。R8 若把类改名，老版本排进队的任务
# 在新版本里就 new 不出来 → 运行时 NoClassDefFoundError。
# 2026-10-17 的内测反馈里就出现了这个异常，而本文件此前**没有任何**
# 针对 worker / 通知层的 keep 规则（network/data.* 是保住的，notification 不是）。
-keepnames class * extends androidx.work.ListenableWorker
-keep class com.pocket.xiaoli.notification.** { *; }

# 公告那一层：prefs 里存的是 JSON 文本，解析走 Gson 反射读字段
-keep class com.pocket.xiaoli.data.announcement.** { *; }

# ── kotlinx-coroutines-android：主线程调度器是靠**类名**加载的 ──────────────
# `Dispatchers.Main` 的工厂写在 jar 的 META-INF/services 里，加载方式是
# `Class.forName("kotlinx.coroutines.android.AndroidDispatcherFactory")`。
# R8 把类改名而 services 文件没跟着改，运行时就是 `NoClassDefFoundError`
# （或被 coroutines 吞成 MissingMainCoroutineDispatcher）。改写只保名字，代价极小。
-keepnames class kotlinx.coroutines.android.AndroidDispatcherFactory
-keepnames class kotlinx.coroutines.android.AndroidExceptionPreHandler