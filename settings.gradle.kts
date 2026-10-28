pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "PocketXiaoli"
include(":app")
// KMP 共享核心：Android 与 Windows 桌面端共用同一份纯 Kotlin 逻辑（阶段 1 起）
include(":core")
