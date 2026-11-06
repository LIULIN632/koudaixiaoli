import java.util.Properties
import org.jetbrains.compose.desktop.application.dsl.TargetFormat

plugins {
    kotlin("jvm")
    id("org.jetbrains.kotlin.plugin.compose")
    id("org.jetbrains.compose")
}

/**
 * 版本号唯一真源：仓库根 `gradle.properties` 的 `versionName`。
 *
 * 桌面端跟随 Android 的 versionName，不再自己抄一份（0.1.92 / 1.0.0 / 1.0.92-desktop.1 三处漂移
 * 就是这么来的）。jpackage 的 `--app-version` 只收 x.y.z，根上的 versionName 正是这个格式。
 */
val appVersion: String = run {
    val propsFile = rootProject.file("../gradle.properties")
    require(propsFile.isFile) { "找不到版本真源：${propsFile.absolutePath}" }
    val props = Properties().apply { propsFile.inputStream().use(::load) }
    val name = props.getProperty("versionName")?.trim().orEmpty()
    require(Regex("""^\d+\.\d+\.\d+$""").matches(name)) {
        "gradle.properties 的 versionName 必须是 x.y.z（jpackage 限制），实际为「$name」"
    }
    name
}

/** 把版本号生成成 Kotlin 常量，界面层用它，避免第二份手抄。 */
val generateVersionSource by tasks.registering {
    val version = appVersion
    // 更新检查要比大小，所以把仓库根 gradle.properties 的 versionCode 也生成进来（同一真源）。
    val code = run {
        val props = Properties().apply { rootProject.file("../gradle.properties").inputStream().use(::load) }
        props.getProperty("versionCode")?.trim()?.toIntOrNull()
            ?: error("gradle.properties 缺 versionCode")
    }
    val outDir = layout.buildDirectory.dir("generated/version/kotlin")
    inputs.property("version", version)
    inputs.property("code", code)
    outputs.dir(outDir)
    doLast {
        val file = outDir.get().asFile.resolve("com/pocket/xiaoli/desktop/DesktopVersion.kt")
        file.parentFile.mkdirs()
        file.writeText(
            """
            |// 由 :app 的 generateVersionSource 生成，不要手改；真源是仓库根 gradle.properties 的 versionName。
            |package com.pocket.xiaoli.desktop
            |
            |const val DESKTOP_VERSION: String = "$version"
            |const val DESKTOP_VERSION_CODE: Int = $code
            |""".trimMargin(),
        )
    }
}

kotlin {
    jvmToolchain(21)
    sourceSets["main"].kotlin.srcDir(generateVersionSource)
}

/** 界面层：Compose Desktop + 应用状态（`AppState`）。只做渲染与事件转发。 */
dependencies {
    implementation(project(":core"))
    implementation(compose.desktop.currentOs)
    implementation(compose.material3)
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-swing:1.9.0")

    testImplementation(kotlin("test"))
}

tasks.test {
    useJUnitPlatform()
}

compose.desktop {
    application {
        mainClass = "com.pocket.xiaoli.desktop.MainKt"
        nativeDistributions {
            targetFormats(TargetFormat.Msi, TargetFormat.Exe)
            packageVersion = appVersion

            packageName = "PocketXiaoli"
            description = "口袋小鲤 · 桌面版"
            vendor = "口袋小鲤"
            windows {
                menuGroup = "口袋小鲤"
                shortcut = true
                perUserInstall = true
                // 固定一个升级 UUID，之后同名安装可覆盖升级
                upgradeUuid = "9f4b2c1a-7d3e-4a51-9c8b-2f6a0d1e5b74"
            }
        }
    }
}

// 生成可直接双击运行的 fat jar（安装包以外的便携形态）。
// 依赖在 :app 汇总，所以 :core 的产物自动被包含进来。
val fatJar = tasks.register<Jar>("fatJar") {
    archiveFileName.set("pocket-xiaoli-desktop.jar")
    manifest { attributes["Main-Class"] = "com.pocket.xiaoli.desktop.MainKt" }
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
    from(sourceSets.main.get().output)
    dependsOn(configurations.runtimeClasspath)
    from({
        configurations.runtimeClasspath.get()
            .filter { it.name.endsWith("jar") }
            .map { zipTree(it) }
    })
}
