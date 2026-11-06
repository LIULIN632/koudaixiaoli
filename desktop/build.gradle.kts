plugins {
    base
    kotlin("jvm") version "2.4.20" apply false
    id("org.jetbrains.kotlin.plugin.compose") version "2.4.20" apply false
    id("org.jetbrains.compose") version "1.12.1" apply false
}

group = "com.pocket.xiaoli"
version = "0.1.0-desktop"

/**
 * 架构守门：`:core` 是纯 Kotlin 逻辑层，**不得** import 任何 Compose 包。
 *
 * 为什么用 Gradle 任务而不是肉眼检查：这条边界一旦破，逻辑层就再也拆不出来了
 * （Compose 的 state/collectAsState 会顺着 import 长进来）。本任务挂在根项目的
 * `check` 上，`gradlew check` / CI 都会跑到；报错信息直接给出「文件:行 + 违规 import」。
 */
val verifyCoreBoundary = tasks.register("verifyCoreBoundary") {
    group = "verification"
    description = "确保 :core 源码里没有 androidx.compose.* / org.jetbrains.compose.* 的 import"

    val coreSources = fileTree("core/src") { include("**/*.kt") }
    inputs.files(coreSources)
    doLast {
        val forbidden = Regex("^\\s*import\\s+(androidx\\.compose|org\\.jetbrains\\.compose)\\.")
        val violations = mutableListOf<String>()
        coreSources.forEach { file ->
            file.readLines().forEachIndexed { index, line ->
                if (forbidden.containsMatchIn(line)) {
                    violations += "${file.relativeTo(projectDir)}:${index + 1}: ${line.trim()}"
                }
            }
        }
        if (violations.isNotEmpty()) {
            throw GradleException(
                "架构边界被破坏：逻辑层（:core）不得依赖 Compose。\n" +
                    violations.joinToString("\n") +
                    "\n修法：把这段状态/交互上提到 :app（UI 层），或改成纯 Kotlin 的回调/Flow。",
            )
        }
    }
}

tasks.named("check") { dependsOn(verifyCoreBoundary) }
