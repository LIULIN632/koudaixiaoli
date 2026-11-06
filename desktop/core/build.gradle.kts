plugins {
    kotlin("jvm")
}

kotlin {
    jvmToolchain(21)
}

/**
 * 纯逻辑层：模型 / 存储 / 课表算法 / 知识库检索 / AI 客户端。
 *
 * **硬约束**：这里不许出现任何 `androidx.compose.*` 或 `org.jetbrains.compose.*` 的 import
 * （由根项目的 `verifyCoreBoundary` 任务在 `gradlew check` 时拦截）。
 */
dependencies {
    // 阶段 1：KMP 共享核心（课表周次 / 默认节次 / 课名简称）。
    // 坐标由 includeBuild("..") 从 Android 仓库根构建里解析，不是发布物。
    api("com.pocket.xiaoli:core:1.0")

    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("com.google.code.gson:gson:2.11.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.9.0")

    testImplementation(kotlin("test"))
    testImplementation("com.squareup.okhttp3:mockwebserver:4.12.0")
}

tasks.test {
    useJUnitPlatform()
}

// 移动端的本地资产（知识库 / 校历 / 快问快答 / 地图）编进 :core 的资源：
// 读取口径是 `Assets::class.java.getResourceAsStream("/assets/...")`，逻辑层自己拥有它更干净，
// 也避免 UI 模块变成「数据层的一部分」。仍然是从 Android 工程直接取，不复制第二份真源。
tasks.processResources {
    from("../../app/src/main/assets") {
        include("knowledge.json", "calendar/**", "quickanswer/**", "campus_map/**", "library/**")
        into("assets")
    }
}
