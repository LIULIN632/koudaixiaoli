pluginManagement {
    repositories {
        gradlePluginPortal()
        mavenCentral()
        google()
        maven("https://maven.pkg.jetbrains.space/public/p/compose/dev")
    }
}

dependencyResolutionManagement {
    repositories {
        mavenCentral()
        google()
        maven("https://maven.pkg.jetbrains.space/public/p/compose/dev")
    }
}

rootProject.name = "pocket-xiaoli-desktop"

// 阶段 1：反向引用 Android 仓库根构建里的 :core（KMP 共享核心）。
// 桌面端从这里取「与 Android 同一份」的纯逻辑（课表周次 / 默认节次 / 课名简称）；
// 本仓库自己的 :core 仍在过渡期，后续阶段逐个把里面的第二份实现换成对
// com.pocket.xiaoli.core 的调用。
includeBuild("..")

// 两个模块，边界是硬约束（见根目录 build.gradle.kts 里的架构守门任务）：
//   :core —— 纯 Kotlin 逻辑（数据/算法/AI 客户端），不依赖任何 Compose
//   :app  —— Compose Desktop 界面 + 应用状态
include(":core", ":app")
