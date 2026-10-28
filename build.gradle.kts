plugins {
    id("com.android.application") version "9.4.1" apply false
    id("org.jetbrains.kotlin.android") version "2.4.20" apply false
    id("org.jetbrains.kotlin.plugin.compose") version "2.4.20" apply false
    id("com.google.devtools.ksp") version "2.3.9" apply false
    // 阶段 1：KMP 共享核心（Android 库目标 + JVM 目标）
    id("org.jetbrains.kotlin.multiplatform") version "2.4.20" apply false
    id("com.android.kotlin.multiplatform.library") version "9.4.1" apply false
}
