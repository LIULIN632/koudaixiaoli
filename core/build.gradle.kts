import org.jetbrains.kotlin.gradle.dsl.JvmTarget

// 供桌面端以 com.pocket.xiaoli:core 坐标引用（includeBuild 做替换，不发布到仓库）
plugins {
    kotlin("multiplatform")
    // AGP 9 起 KMP 的 Android 目标走这个插件（替代 kotlin("android") + androidTarget()）
    id("com.android.kotlin.multiplatform.library")
}

group = "com.pocket.xiaoli"
version = "1.0"

kotlin {
    // 桌面端（JVM）
    jvm {
        compilerOptions { jvmTarget.set(JvmTarget.JVM_21) }
    }

    // Android 端（只作为库被 :app 依赖）
    androidLibrary {
        namespace = "com.pocket.xiaoli.core"
        compileSdk = 37
        minSdk = 29
        // 让 commonTest 也能在 Android host 上跑一份（现在只有 jvmTest 在跑）
        withHostTest {}
    }

    sourceSets {
        // JVM + Android 共同的 JVM 源集：OkHttp / Gson 两端用的是同一组构件（okhttp 4.12
        // 同时发布 android 与 jvm 变体），SSE 解析这类纯 JVM 逻辑放这里，不必引 Ktor。
        val jvmAndroidMain by creating {
            dependsOn(commonMain.get())
        }
        jvmMain.get().dependsOn(jvmAndroidMain)
        androidMain.get().dependsOn(jvmAndroidMain)

        commonMain.dependencies {
            // 阶段 1 只放纯函数，刻意不引任何第三方依赖
        }
        commonTest.dependencies {
            implementation(kotlin("test"))
        }
        jvmTest.dependencies {
            implementation("com.squareup.okhttp3:mockwebserver:4.12.0")
        }
        jvmAndroidMain.dependencies {
            // Delta 里公开了 JsonObject，所以 Gson 必须是 api；OkHttp 不外露，用 implementation。
            api("com.google.code.gson:gson:2.11.0")
            implementation("com.squareup.okhttp3:okhttp:4.12.0")
            // 教务页面解析：Android 本来就在用 jsoup。Element 出现在公共解析函数签名里，故用 api。
            api("org.jsoup:jsoup:1.18.1")
            // 登录/抓课表是 suspend 函数，两端都要协程运行时
            implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.9.0")
        }
    }
}

// 守门 v2：共享核心的「源码边界 + 依赖边界」两层检查，进 check 默认跑。
//
// 规则（为什么是这几条）：
// 1) commonMain 是唯一被两端共用的代码，**不得**出现 Android / Compose / AWT-Swing
//    任何一方 —— 出现即意味着另一端起不来（Android 没有 AWT，桌面没有 android.*）。
// 2) androidMain / jvmMain / jvmAndroidMain 可以有平台 import，但仍然不能有 Compose UI：
//    UI 必须留在各自端。
// 3) commonMain 不得声明第三方依赖；jvmAndroidMain 的依赖必须命中白名单。
//    okhttp/gson 是两端本来就在用的同一组 JVM 构件（不是新引入的第二套网络栈）；
//    别的库要加进来，先改白名单再谈，避免两端传递依赖悄悄分叉。
val verifyCoreScope by tasks.registering {
    val commonMainSources = fileTree("src/commonMain") { include("**/*.kt") }
    val allSources = fileTree("src") { include("**/*.kt") }
    val buildScript = layout.projectDirectory.file("build.gradle.kts")

    val forbiddenInCommonMain = listOf(
        "import android.",
        "import androidx.",
        "import org.jetbrains.compose",
        "import javax.swing",
        "import java.",
        "import javax.",
        "import com.pocket.xiaoli.desktop",
    )
    val forbiddenAnywhere = listOf(
        "import androidx.compose",
        "import org.jetbrains.compose",
    )

    doLast {
        val hits = mutableListOf<String>()
        commonMainSources.forEach { file ->
            file.readLines().forEachIndexed { index, line ->
                val trimmed = line.trim()
                if (!trimmed.startsWith("import ")) return@forEachIndexed
                if (forbiddenInCommonMain.any { trimmed.startsWith(it) }) {
                    hits += "${file.relativeTo(projectDir)}:${index + 1} → commonMain 不得 import 平台类型：$trimmed"
                }
            }
        }
        allSources.forEach { file ->
            file.readLines().forEachIndexed { index, line ->
                val trimmed = line.trim()
                if (!trimmed.startsWith("import ")) return@forEachIndexed
                if (forbiddenAnywhere.any { trimmed.startsWith(it) }) {
                    hits += "${file.relativeTo(projectDir)}:${index + 1} → 核心模块不得 import Compose：$trimmed"
                }
            }
        }

        // 依赖边界：commonMain.dependencies { } 里只能留空
        val script = buildScript.asFile.readText()
        val block = Regex("commonMain\\.dependencies\\s*\\{([\\s\\S]*?)\\n\\s*\\}").find(script)?.groupValues?.get(1)
        if (block == null) {
            hits += "core/build.gradle.kts → 找不到 commonMain.dependencies 块（守门脚本需要同步更新）"
        } else if (block.contains("implementation(") || block.contains("api(")) {
            hits += "core/build.gradle.kts → commonMain 不得声明第三方依赖，当前内容：\n$block"
        }

        // 依赖边界：jvmAndroidMain 只能是两端已经在用的 JVM 构件
        val allowedSharedDeps = listOf(
            "com.google.code.gson:gson:",
            "com.squareup.okhttp3:okhttp:",
            "org.jsoup:jsoup:",
            "org.jetbrains.kotlinx:kotlinx-coroutines-core:",
        )
        val sharedBlock = Regex("jvmAndroidMain\\.dependencies\\s*\\{([\\s\\S]*?)\\n\\s*\\}")
            .find(script)?.groupValues?.get(1)
        if (sharedBlock != null) {
            Regex("""(?:api|implementation)\("([^"]+)"\)""").findAll(sharedBlock).forEach { match ->
                val coord = match.groupValues[1]
                if (allowedSharedDeps.none { coord.startsWith(it) }) {
                    hits += "core/build.gradle.kts → jvmAndroidMain 依赖不在白名单：$coord"
                }
            }
        }

        if (hits.isNotEmpty()) {
            throw GradleException(
                "共享核心边界被破坏（commonMain 必须零平台依赖）：\n" + hits.joinToString("\n")
            )
        }
    }
}

tasks.named("check") {
    dependsOn(verifyCoreScope)
}
