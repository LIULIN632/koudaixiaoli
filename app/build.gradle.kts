import java.io.FileInputStream
import java.util.Properties

plugins {
    id("com.android.application")
    // AGP 9.0 起内置 Kotlin 支持，不再需要 org.jetbrains.kotlin.android 插件
    // （AGP 会直接报错要求移除）。Compose 与 KSP 插件仍然需要。
    id("org.jetbrains.kotlin.plugin.compose")
    id("com.google.devtools.ksp")
}

// ── Load keystore properties ──
val keystorePropsFile = rootProject.file("keystore.properties")
val keystoreProps = Properties().apply {
    if (keystorePropsFile.exists()) {
        load(FileInputStream(keystorePropsFile))
    }
}

android {
    namespace = "com.pocket.xiaoli"
    // compileSdk 37：MIUIX 的 AAR 元数据声明 minCompileSdk=37，低于它 CheckAarMetadata 直接失败。
    // 这只决定"用哪套 SDK 编译"，不影响设备兼容 —— minSdk 29 / targetSdk 34 均保持不变。
    compileSdk = 37

    signingConfigs {
        if (keystorePropsFile.exists()) {
            create("release") {
                storeFile = rootProject.file(keystoreProps["storeFile"] as String)
                storePassword = keystoreProps["storePassword"] as String
                keyAlias = keystoreProps["keyAlias"] as String
                keyPassword = keystoreProps["keyPassword"] as String
            }
        }
    }

    defaultConfig {
        applicationId = "com.pocket.xiaoli"
        minSdk = 29
        targetSdk = 34
        /**
         * 版本号来自 `gradle.properties`，**不是**写死在这里。
         *
         * 原因见那份文件的注释：写死在 kts 里会让"改版本号"变成"改构建脚本"，
         * 每次发版都触发全量重编（实测：改 kts 约 72.5s vs 只改 properties 约 53.3s，
         * 空转只要 2s）。递增动作由 `build.ps1` / `build.sh` 完成。
         *
         * 缺值时**直接报错**而不是兜底成 1 —— 静默产出一个 versionCode=1 的包更难查。
         *
         * ⚠️ 两个打包脚本都会在动手前检查本文件是否又出现了版本号，出现就拒绝打包。
         */
        versionCode = (project.findProperty("versionCode") as String?)?.trim()?.toIntOrNull()
            ?: error("gradle.properties 缺少 versionCode")
        versionName = (project.findProperty("versionName") as String?)?.trim()?.takeIf { it.isNotBlank() }
            ?: error("gradle.properties 缺少 versionName")
        resourceConfigurations.addAll(listOf("zh-rCN", "zh"))
        ndk {
            // 2026-10-14：只留 arm64-v8a（arm64 自 2015 年起普及，32 位真机已少见）。
            abiFilters += "arm64-v8a"
        }

        // 反馈的直传地址不在这里：它由 `announce/feed.json` 的 `endpoints.feedback`
        // 运行时提供（换服务器不用发版），编译期只留 `SelfHost.TELEMETRY_URL` 作兜底。
        // APK 里因此**没有任何**反馈相关的密钥 —— 旧版飞书那四个凭据已随 0.1.90 删除。

        // ── F-Droid 构建开关 ────────────────────────────────────────
        // `./gradlew assembleRelease -Pfdroid=true` 生成「F-Droid 版」：
        // 匿名统计**完全不初始化**（不建队列、不发请求），上架时不带 Tracking 反特性。
        // 普通构建（不带这个属性）行为完全不变。
        val fdroidBuild = (project.findProperty("fdroid") as String?)?.toBoolean() == true
        buildConfigField("boolean", "FDROID", fdroidBuild.toString())
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            if (keystorePropsFile.exists()) {
                signingConfig = signingConfigs.getByName("release")
            }
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }

    /**
     * Android Lint 配置。
     *
     * ## 背景（2026-09-30）
     *
     * 在那之前 `lintDebug` **三个任务全部失败**，报的是 lint 内部崩溃：
     * `NoClassDefFoundError: ComposableDestinationInComposeScopeDetector...`
     * （出处：`docs/优化审计-20260930.md` 第 1 节）。那次崩溃的后果是
     * **整个项目跑不了任何 lint 检查**。
     *
     * ⚠️ 2026-10-02 实测：**当前 lint 能正常跑**（`BUILD SUCCESSFUL`，
     * 报告 55 warnings / 0 errors）。依赖里 `navigation-compose` 仍是 **2.8.5**，
     * 并不是靠升级它修好的 —— 崩溃的原始成因（lint 规则与 AGP 的版本组合）
     * 已随构建环境变化而不再复现。**不要在注释里替它编一个"已升到 X.X.X"的因果**：
     * 那是错的，且会误导下一个来查的人。
     * 真正的判断依据只有一条：**跑一次 `:app:lintDebug` 看结果**。
     * 首跑就报出了真问题（`docs/安全审计-TLS.md` 开头有记录），所以这道防线要留住。
     *
     * ## 2026-10-02：`abortOnError` 由 `false` 改为 `true`，并把安全类提升为 error
     *
     * ### 实测事实（先看清楚再改，别凭印象）
     *
     * 改之前跑 `:app:lintDebug`，报告是 **55 warnings / 0 errors**。
     * 即：**单把 `abortOnError` 改成 `true` 几乎拦不住任何东西** ——
     * 连安全类的 `TrustAllX509TrustManager`(6) / `CustomX509TrustManager`(3)
     * 在这个配置下都只是 **warning** 级。
     *
     * 所以「改一个布尔值就接上警报」是错的。真正有效的是把值得拦的规则
     * 显式提成 error（见下方 `error += setOf(...)`）。
     *
     * ### 为什么只提安全类，不整片 `warningsAsErrors = true`
     *
     * 其余 45 条绝大多数是风格/优化建议（`ModifierParameter`、`ChromeOsAbiSupport`、
     * `ObsoleteSdkInt`、`UnusedResources`、`DefaultLocale`…）。全部提成 error
     * 会让构建失去可用性 —— 每改一行 UI 都要先还一批历史债。
     *
     * 被提升的这几条有共同特征：**指向"用户凭据/数据可能被泄露"，
     * 而不是"代码不够漂亮"**。这类问题回归的代价远大于构建被拦一次。
     *
     * ### 已提升的 4 条当前命中 7 处，全是既有项 → 用 baseline 冻结
     *
     * | 位置 | 性质 |
     * |---|---|
     * | `QiangZhiClient.kt:54/56/57` | TLS 放宽 |
     * | `ZhengfangClient.kt:120/122/123` | TLS 放宽 |
     * | `WebImportViewModel.kt:288` | `StaticFieldLeak`，**误报** |
     *
     * **为什么不直接把构建变绿**：
     * - **TLS 6 处**：`docs/安全审计-TLS.md` 已实测「六台学校主机默认校验全部通过」，
     *   「信任一切」本属多余；但**不能直接改严** —— `*.yitsd.edu.cn` 证书
     *   2026-11-18 到期、`ydny.ytu.edu.cn` 2026-10-25 就到期，贸然改严会打断
     *   教务导入（项目第一优先级）。正解是方案 B（白名单兜底），且必须先补单测
     *   + 真机回归 —— **这两步没做完就不该动那几行代码**。
     * - **`StaticFieldLeak`**：`WebImportViewModel.onCleared()` 已显式
     *   `webView = null`（源码注释就写着 `lint: StaticFieldLeak`）。lint 看不到
     *   跨方法置空，与 `JavascriptInterface` 同属**已知误报**。
     *
     * 用 baseline 冻结这 7 处后，门禁**只拦新增** —— 既拿到「回归即红」的保护，
     * 又不必为了让构建变绿去做一件尚未验证（TLS）或根本不该做（误报）的事。
     *
     * ⚠️ **baseline 是欠债清单，不是免罪符**：TLS 那 6 条应随方案 B 一起消掉。
     *    `lint-baseline.xml` **变大 = 有人偷偷加债**，要审。
     *
     * ⚠️ **关掉的几条都是"风格偏好"或"用不上的方案"，不是缺陷**：
     * - `UseKtx`(108)：建议 `prefs.edit {}` 而非 `prefs.edit().putX().apply()`。
     *   纯口味问题，改了要动 108 处。
     * - `LogNotTimber`(47)：建议改用 Timber。**本项目没有引入 Timber**，
     *   这条规则在没有该依赖时纯属噪声。
     * - `AutoboxingStateCreation`(24)：建议 `mutableIntStateOf` 之类。
     *   属于微优化，不值得动 24 处。
     * - `NewerVersionAvailable` / `GradleDependency`(15)：纯"有新版本可用"。
     *   升级决策不该由 lint 驱动（本项目对依赖版本很敏感，
     *   例如 navigation 的版本直接决定 lint 能否运行）。
     *
     * ⚠️ **安全类规则一律不关** —— 它们正是修好 lint 后抓出真问题的规则。
     *    处置见下方 `error += setOf(...)`。
     */
    lint {
        abortOnError = true
        checkDependencies = false
        warningsAsErrors = false
        baseline = file("lint-baseline.xml")
        // 安全类：提升为 error（既有 7 处已冻结进 baseline，故只拦新增）
        error += setOf(
            "TrustAllX509TrustManager",
            "CustomX509TrustManager",
            "WebViewClientOnReceivedSslError",
            "StaticFieldLeak",
        )
        disable += setOf(
            "UseKtx",
            "LogNotTimber",
            "AutoboxingStateCreation",
            "NewerVersionAvailable",
            "GradleDependency",
        )
    }

    // AGP 9 移除了 kotlinOptions，改用 kotlin.compilerOptions（注意是 JvmTarget 枚举，不是字符串）
    kotlin {
        compilerOptions {
            jvmTarget = org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_21
        }
    }

    buildFeatures {
        compose = true
        // 反馈渠道的凭据走 BuildConfig（见 defaultConfig 里的 buildConfigField）。
        buildConfig = true
    }

    testOptions {
        // Compose 截图测试（Roborazzi）跑在 JVM 上，但 Robolectric 仍需要读到
        // **合并后的资源与 Manifest**，否则 `createComposeRule()` 起不来测试 Activity。
        // 方案出处见 docs/优化与美化清单-2026-10.md 的 P4-3。
        unitTests {
            isIncludeAndroidResources = true
        }
        // 2175 条单测里有 200+ 张 Robolectric 截图，默认 512m 堆会在批量截图时 OOM：
        // 2026-10-24 实测 23 条 `OutOfMemoryError: Java heap space`（全是截图类，非断言失败）。
        unitTests.all {
            it.maxHeapSize = "2048m"
        }
    }
}

ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
}

/**
 * 校验「编译期」与「运行期」解析到的 androidx.compose 版本是否一致。
 *
 * ## 为什么需要它
 *
 * v1.0.64 出过一次线上致命崩溃（`NoSuchMethodError: ExposedDropdownMenuBox`）：
 * MIUIX 的传递依赖把 material3 从 BOM 的 1.3.1 顶到 1.4.0，
 * 于是**代码照 1.3.1 编译、APK 里装的是 1.4.0**，签名对不上。
 *
 * 这种错配**不会导致编译失败**，也不会被任何单元测试抓到 ——
 * debug 包还经常不崩（R8 不做那么多内联），只有 release 才炸。
 * 唯一可靠的发现方式就是拿两个 configuration 的解析结果对一遍，所以做成任务。
 *
 * 接入：`renameApk` 依赖它，即**正式打包一定会跑**。也可以单独执行：
 *     .\gradlew :app:verifyComposeAlignment
 */
tasks.register("verifyComposeAlignment") {
    group = "verification"
    description = "确保 release 的编译期与运行期使用同一套 androidx.compose 版本"

    doLast {
        fun resolved(configName: String): Map<String, String> {
            val cfg = configurations.findByName(configName)
                ?: throw GradleException("找不到 configuration: $configName")
            return cfg.resolvedConfiguration.resolvedArtifacts
                .map { it.moduleVersion.id }
                .filter { it.group.startsWith("androidx.compose") }
                .associate { "${it.group}:${it.name}" to it.version }
        }

        val compile = resolved("releaseCompileClasspath")
        val runtime = resolved("releaseRuntimeClasspath")
        val mismatches = mutableListOf<String>()

        (compile.keys + runtime.keys).sorted().forEach { key ->
            val c = compile[key]
            val r = runtime[key]
            if (c != null && r != null && c != r) {
                mismatches += "  $key\n      编译期: $c\n      运行期: $r"
            }
        }

        if (mismatches.isNotEmpty()) {
            throw GradleException(
                buildString {
                    appendLine("androidx.compose 的编译期/运行期版本不一致，会产生 NoSuchMethodError（线上崩溃）：")
                    mismatches.forEach { appendLine(it) }
                    appendLine()
                    appendLine("修法：把 app/build.gradle.kts 的 compose-bom 换到与运行期对齐的版本。")
                    appendLine("当前运行期实际使用的版本：")
                    runtime.entries
                        .filter { it.key.startsWith("androidx.compose.material3") || it.key == "androidx.compose.ui:ui" }
                        .sortedBy { it.key }
                        .forEach { appendLine("  ${it.key} = ${it.value}") }
                }
            )
        }
        logger.lifecycle("[OK] androidx.compose 编译期与运行期版本一致（共比对 ${compile.size} 个模块）")
    }
}

tasks.register("renameApk") {
    dependsOn("assembleRelease")
    dependsOn("verifyComposeAlignment")
    doLast {
        val releaseDir = file("build/outputs/apk/release")
        val apk = releaseDir.listFiles { f -> f.isFile && f.extension.equals("apk", ignoreCase = true) }
            ?.maxByOrNull { it.lastModified() }
            ?: throw GradleException("未找到 Release APK，请先确认 assembleRelease 产物：${releaseDir.absolutePath}")

        val dest = rootProject.file("口袋小鲤_v${android.defaultConfig.versionName}.apk")
        apk.copyTo(dest, overwrite = true)

        // 根目录只保留最近 3 个版本；更旧的**归档到 release/archive/** 而不是删除。
        //
        // ⚠️ 这里原来是 `it.delete()` —— 结果 1.0.79 / 1.0.80 已经彻底找不回来了
        //（根目录只留 3 个，而 release/archive/ 又只到 1.0.28）。
        // 打包脚本顺手毁掉别人再也拿不到的历史版本，代价和收益完全不成比例，所以改成归档。
        val keep = 3
        val archiveDir = rootProject.file("release/archive")
        rootProject.projectDir.listFiles { f ->
            f.isFile && f.extension.equals("apk", ignoreCase = true) && f.name.startsWith("口袋小鲤_v")
        }
            ?.sortedByDescending { it.lastModified() }
            ?.drop(keep)
            ?.forEach { old ->
                archiveDir.mkdirs()
                val target = archiveDir.resolve(old.name)
                if (target.exists()) target.delete()
                if (!old.renameTo(target)) {
                    old.copyTo(target, overwrite = true)
                    old.delete()
                }
            }
    }
}

dependencies {
    // Compose BOM
    //
    // ⚠️ 这个版本号**不能随便改小**，它必须 ≥ MIUIX 强制要求的那一套 Compose。
    //
    // 起因（v1.0.64 线上 NoSuchMethodError 崩溃）：原本写的是 2024.12.01
    // （material3 1.3.1 / ui 1.7.6），但 MIUIX 0.9.3 是 Kotlin Multiplatform 的
    // JetBrains Compose 1.11.1 构建，它会带来：
    //
    //     org.jetbrains.compose.material3:material3-window-size-class:1.9.0
    //       └── androidx.compose.material3:material3-window-size-class:1.4.0
    //
    // material3 是 Gradle 的「原子组」，1.4.0 会把 material3 本体一起顶上去。
    // 结果是**编译与运行不一致**：
    //
    //     releaseCompileClasspath: material3 1.3.1   ← 代码照这个编译
    //     releaseRuntimeClasspath: material3 1.4.0   ← APK 里装的是这个
    //
    // 而 ExposedDropdownMenuBox 恰好是 1.4.0 改过签名的 API → 运行到那一步
    // 抛 NoSuchMethodError（链接期致命崩溃）。debug 不崩、release 崩，
    // 就是因为这个错配只在打 release 时被 R8 固化下来。
    //
    // 2026.05.01 恰好对应 material3 1.4.0 + ui/runtime 1.11.2，与 MIUIX 实际解析出的版本一字不差。
    //
    // 2026.08.00 是"液态玻璃"要求的版本（backdrop 依赖 JetBrains Compose 1.12.0）：
    //     ui / foundation / runtime  1.11.2 → 1.12.0
    //     material3                  **1.4.0 → 1.4.0（原地不动）**
    // material3 不动意味着本文件里 196 处 Scaffold/TopAppBar 的 API 面一行不变，
    // 所以这次升级的风险面只有 ui/foundation 的小版本差异，而不是"换一套 UI 框架"。
    //
    // 想升级 BOM 时的正确做法：升完后跑
    //     .\gradlew :app:dependencies --configuration releaseCompileClasspath
    //     .\gradlew :app:dependencies --configuration releaseRuntimeClasspath
    // 逐条比对 androidx.compose.* 的版本，**两边必须相同**。
    val composeBom = platform("androidx.compose:compose-bom:2026.08.00")
    implementation(composeBom)
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.activity:activity-compose:1.9.3")
    debugImplementation("androidx.compose.ui:ui-tooling")

    // Navigation
    implementation("androidx.navigation:navigation-compose:2.8.5")

    // Lifecycle + ViewModel
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.7")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.7")

    // Room — 2.6.1 的编译器与 Kotlin 2.4 字节码不兼容（KSP 报 "unexpected jvm signature V"），
    // 必须随工具链升到 2.8.x。三个坐标版本要保持一致。
    implementation("androidx.room:room-runtime:2.8.3")
    implementation("androidx.room:room-ktx:2.8.3")
    ksp("androidx.room:room-compiler:2.8.3")

    // OkHttp
    implementation("com.squareup.okhttp3:okhttp:4.12.0")

    // 后台周期任务：只给「更新 + 公告」这一条链路用（见 notification/AppFeedScheduler.kt）。
    // 选 WorkManager 而不是 AlarmManager，是因为它自带约束、指数退避、重启后自动恢复；
    // 而班级/考试提醒那种**到点必须响**的场景仍然走 AlarmManager，不要混用同一套。
    implementation("androidx.work:work-runtime-ktx:2.10.1")

    // MIUIX — Apache-2.0（独立开源库，与参考仓库的 GPL 无关），提供玻璃/大圆角/超椭圆的原生观感。
    // 注意：其 AAR 声明 minCompileSdk=37，所以 compileSdk 必须 ≥37（见上方 android.compileSdk）。
    implementation("top.yukonga.miuix.kmp:miuix-ui-android:0.9.3")
    implementation("top.yukonga.miuix.kmp:miuix-preference-android:0.9.3")
    implementation("top.yukonga.miuix.kmp:miuix-icons-android:0.9.3")
    // 超椭圆圆角依赖 RuntimeShader（API 33+）；minSdk 29 上库内部自动降级为普通圆角，安全。
    implementation("top.yukonga.miuix.kmp:miuix-squircle-android:0.9.3")
    // MIUIX 的窗口级组件（WindowListPopup / WindowBottomSheet）在 Dialog 里要读
    // LocalNavigationEventDispatcherOwner；它只以 implementation 传递出来，不显式声明就用不了。
    // 1.1.2 是 MIUIX 0.9.3 解析出的那一版（`gradlew :app:dependencies` 可复核）。
    implementation("androidx.navigationevent:navigationevent-compose:1.1.2")

    // ⚠️ 2026-10-14 移除 MapLibre Native：两个 ABI 的 libmaplibre.so 合计 18.77 MB =
    //    当时 APK 的 72%，而它只服务「校园地图 → 开源地图」那一个备用视图（主地图是自绘
    //    CampusMapCanvas）。移除后 26.19 MB → 7.4 MB。要加回来前，先想清楚值不值这 19 MB。

    // ── 液态玻璃（Liquid Glass）──
    //
    // `io.github.kyant0:backdrop` = `Kyant0/AndroidLiquidGlass`，Apache-2.0，酷安底栏同源库。
    //
    // 曾经撤掉过它，理由是"会把 Compose 从 1.11.2 拉到 1.12.0、破坏 verifyComposeAlignment，
    // 为一个装饰性底栏不值得"。**那条判断已经不作数**（2026-09-27 复核）：
    //   * BOM 2026.08.00 与 2026.05.01 的 **material3 都是 1.4.0**（只抬 ui/foundation/runtime），
    //     所以 196 处 Scaffold/TopAppBar 依赖的 API 面一行不动；
    //   * 它的 minCompileSdk=37 已被满足（compileSdk 已经是 37）；
    //   * backdrop 与 compose-bom 2026.08.00 都已在本地 Gradle 缓存，可离线升级。
    //
    // ⚠️ 唯一不变的约束：**BOM 与这个库必须在同一次改动里一起升**，
    // 让编译期与运行期都落在 1.12.0（否则 verifyComposeAlignment 会拦下）。
    //
    // 另注（仍然成立）：该库**只提供底层原语**（Backdrop / blur / lens / highlight / capsule），
    // 不含任何高层组件。README 里的 `LiquidBottomTabs` 是 demo 代码，依赖三个不随库发布的文件
    // （DampedDragAnimation / InteractiveHighlight / LocalLiquidBottomTabScale）。
    //
    // 折射的**采样源**由布局提供：底栏已从 Scaffold 的 bottomBar 槽搬成内容之上的浮层，
    // 页面内容能从栏背后滚过去（见 docs/UI2.0-液态玻璃底栏方案.md 的 P0）。
    implementation("io.github.kyant0:backdrop-android:2.0.1")

    // Gson
    implementation("com.google.code.gson:gson:2.11.0")

    // Jsoup — HTML parsing for schedule import
    implementation("org.jsoup:jsoup:1.18.1")

    // Coroutines
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.9.0")

    // Core
    implementation("androidx.core:core-ktx:1.13.1")

    // CameraX + ZXing — 位置二维码相机扫描（LOC-02）
    implementation("androidx.camera:camera-core:1.4.2")
    implementation("androidx.camera:camera-camera2:1.4.2")
    implementation("androidx.camera:camera-lifecycle:1.4.2")
    implementation("androidx.camera:camera-view:1.4.2")
    implementation("com.google.zxing:core:3.5.1")

    // Encrypted SharedPreferences
    implementation("androidx.security:security-crypto:1.1.0-alpha06")

    // Unit tests
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.json:json:20240303")
    // 匿名统计的上报断言：验"关掉时 0 次请求"必须真起一个 HTTP 服务端来数，
    // 而不是相信我们自己写的 if（见 `TelemetryUploaderTest`）。
    testImplementation("com.squareup.okhttp3:mockwebserver:4.12.0")

    // ── Compose 截图测试（JVM 快照，**不需要模拟器、不需要真机、不需要点击**）──
    // 目的：让"两套皮肤 × 用色强度"的观感回归脱离设备运行；也补上真机够不到的地方
    //（例如只能靠点击打开的预览弹窗 —— 而本项目的设备 MIUI 禁止 ADB 注入输入事件）。
    // 方案出处：docs/优化与美化清单-2026-10.md 的 P4-3，Roborazzi 被标为 ⭐ 最适合本项目
    //（无 instrumentation 环境）。版本号已对 Maven Central 核实为当前最新。
    //
    // ⚠️ 新增测试依赖后**必须跑一次全量** `:app:testDebugUnitTest`：本文件末尾那段
    // Windows 中文路径的 junction 重映射，是"引入新测试依赖后最容易被它打回"的地方。
    testImplementation("org.robolectric:robolectric:4.17")
    testImplementation("androidx.compose.ui:ui-test-junit4")
    testImplementation("io.github.takahirom.roborazzi:roborazzi:1.76.0")
    testImplementation("io.github.takahirom.roborazzi:roborazzi-compose:1.76.0")
    // 只有 debug 变体需要：它提供 Compose 测试用的空 Activity 声明。
    // 不影响 release 产物（用户装的是 release）。
    debugImplementation("androidx.compose.ui:ui-test-manifest")
}

// ─────────────────────────────────────────────────────────────
// Roborazzi 截图测试：**默认 verify（门禁）**，`-PrecordScreenshots` 切 record
//
// ⚠️ 两个属性都必须**显式给**：不传任何一个时 `captureRoboImage` 是**静默 no-op** ——
// 它既不录制也不校验，于是测试**通过**、却什么也没发生。这个坑我实际踩过一次
//（测试绿、目录空、白排查），所以这里写死而不是留给调用方记得加参数。
//
// 属性名取自 roborazzi-core 字节码里的字符串常量，不是从文档转述来的：
//   roborazzi.test.record / roborazzi.test.verify / roborazzi.test.compare /
//   roborazzi.output.dir / roborazzi.record.namingStrategy …
//
// 基准图是 `app/src/test/screenshots/`（**随仓库提交**；不放 build/ 是因为会被 clean 清掉）。
// 有意改了观感之后要重录，否则门禁会一直红：
//     .\gradlew :app:testDebugUnitTest -PrecordScreenshots
// ─────────────────────────────────────────────────────────────
val recordScreenshots = providers.gradleProperty("recordScreenshots").isPresent
tasks.withType<org.gradle.api.tasks.testing.Test>().configureEach {
    if (recordScreenshots) {
        systemProperty("roborazzi.test.record", "true")
    } else {
        systemProperty("roborazzi.test.verify", "true")
    }
}

// ─────────────────────────────────────────────────────────────
// Windows 中文路径兼容:
// Gradle 的 Test worker 在项目路径含非 ASCII 字符时,无法从目录 classpath 加载
// 测试类(ClassNotFoundException)。若能在系统临时目录创建 ASCII junction,则只对
// 单元测试任务的 testClassesDirs / classpath 做路径重映射;创建失败则自动跳过,
// 不影响编译与打包。
// ─────────────────────────────────────────────────────────────
afterEvaluate {
    val osName = System.getProperty("os.name").orEmpty()
    val projectPath = rootProject.projectDir.absolutePath
    val hasNonAscii = projectPath.any { it.code > 127 }
    if (osName.startsWith("Windows", ignoreCase = true) && hasNonAscii) {
        val suffix = Integer.toHexString(projectPath.hashCode())
        val tempRoot = File(System.getProperty("java.io.tmpdir"), "pocket_xiaoli_test_path")
        tempRoot.mkdirs()
        val linkDir = File(tempRoot, "pxy_ascii_$suffix")
        val linkPath = linkDir.absolutePath
        if (linkPath.all { it.code < 128 }) {
            if (!linkDir.exists()) {
                try {
                    ProcessBuilder("cmd", "/c", "mklink", "/J", linkPath, projectPath)
                        .redirectErrorStream(true)
                        .start()
                        .waitFor()
                } catch (_: Exception) {
                    // junction 创建失败时静默回退
                }
            }
            if (linkDir.exists()) {
                tasks.withType<org.gradle.api.tasks.testing.Test>().configureEach {
                    fun remap(file: File): File =
                        if (file.path.startsWith(projectPath, ignoreCase = true)) {
                            File(linkPath + file.path.substring(projectPath.length))
                        } else {
                            file
                        }
                    val remappedTestClasses = project.files(testClassesDirs.files.map(::remap))
                    testClassesDirs = testClassesDirs + remappedTestClasses
                    val remappedClasspath = project.files(classpath.files.map(::remap))
                    classpath = classpath + remappedClasspath
                    val variant = name.removePrefix("test").removeSuffix("UnitTest")
                    if (variant.isNotBlank()) {
                        dependsOn("compile${variant}UnitTestKotlin")
                    }
                }
            }
        }
    }
}
