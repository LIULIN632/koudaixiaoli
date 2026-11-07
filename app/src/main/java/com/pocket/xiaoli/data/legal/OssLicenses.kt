package com.pocket.xiaoli.data.legal

/**
 * 一条开源依赖的许可证登记。
 *
 * @param name 界面上显示的名字（用大家认得的叫法，不照抄 group 名）
 * @param coordinates `group:artifact`，**门禁比对用**：`OssLicensesTest` 会解析
 *   `app/build.gradle.kts` 里的 `implementation("…")`，任何一条直接依赖没在这里登记就测红。
 * @param license 许可证全名（见 [AllowedLicenses]）
 * @param url 项目主页 —— 用户点开能自己核对许可证原文
 */
internal data class OssLicense(
    val name: String,
    val coordinates: List<String>,
    val license: String,
    val url: String,
)

/**
 * 允许出现在依赖里的许可证白名单。
 *
 * ⚠️ 这不是"文档"，是**门禁的一部分**：`OssLicensesTest` 会拿它卡住每一条登记。
 * 本项目自身是 GPL-3.0，但这不等于"可以引入任何 GPL/AGPL 库" ——
 * 一旦引入 AGPL（SaaS 传染）或接入商业 SDK，发出去就收不回来。
 * 真要引入白名单外的许可证，先改这张表、并在提交说明里写清代价。
 */
internal val AllowedLicenses = setOf(
    "Apache-2.0",
    "MIT",
    "BSD-3-Clause",
    "ISC",
)

/**
 * 直接依赖的许可证清单（**不是**全量传递依赖）。
 *
 * 为什么不做"扫描全部传递依赖"：那需要引第三方 Gradle 插件并联网解析每个 POM
 *（本项目长期离线构建，且不为一张静态清单加运行时依赖）。这里列的是**我们主动选的库**，
 * 传递依赖的许可证由这些库各自的 POM 负责声明 —— 页面脚注把这句话说清楚，
 * 而不是假装自己列出了全部。
 *
 * ⚠️ 加依赖时**必须**同时在这里登记，否则 `OssLicensesTest` 会红 —— 这是这张清单不过期的唯一保证。
 */
internal val ossLicenses: List<OssLicense> = listOf(
    OssLicense(
        name = "Kotlin 协程",
        coordinates = listOf("org.jetbrains.kotlinx:kotlinx-coroutines-android"),
        license = "Apache-2.0",
        url = "https://github.com/Kotlin/kotlinx.coroutines",
    ),
    OssLicense(
        name = "Jetpack Compose（UI / Material 3 / 图标）",
        coordinates = listOf(
            "androidx.compose.ui:ui",
            "androidx.compose.ui:ui-graphics",
            "androidx.compose.ui:ui-tooling-preview",
            "androidx.compose.material3:material3",
            "androidx.compose.material:material-icons-extended",
        ),
        license = "Apache-2.0",
        url = "https://developer.android.com/jetpack/androidx/releases/compose",
    ),
    OssLicense(
        name = "Activity Compose",
        coordinates = listOf("androidx.activity:activity-compose"),
        license = "Apache-2.0",
        url = "https://developer.android.com/jetpack/androidx/releases/activity",
    ),
    OssLicense(
        name = "Navigation Compose",
        coordinates = listOf("androidx.navigation:navigation-compose"),
        license = "Apache-2.0",
        url = "https://developer.android.com/jetpack/androidx/releases/navigation",
    ),
    OssLicense(
        name = "NavigationEvent",
        coordinates = listOf("androidx.navigationevent:navigationevent-compose"),
        license = "Apache-2.0",
        url = "https://developer.android.com/jetpack/androidx/releases/navigationevent",
    ),
    OssLicense(
        name = "Lifecycle（ViewModel / runtime-compose）",
        coordinates = listOf(
            "androidx.lifecycle:lifecycle-runtime-compose",
            "androidx.lifecycle:lifecycle-viewmodel-compose",
        ),
        license = "Apache-2.0",
        url = "https://developer.android.com/jetpack/androidx/releases/lifecycle",
    ),
    OssLicense(
        name = "Room",
        coordinates = listOf("androidx.room:room-runtime", "androidx.room:room-ktx"),
        license = "Apache-2.0",
        url = "https://developer.android.com/jetpack/androidx/releases/room",
    ),
    OssLicense(
        name = "WorkManager",
        coordinates = listOf("androidx.work:work-runtime-ktx"),
        license = "Apache-2.0",
        url = "https://developer.android.com/jetpack/androidx/releases/work",
    ),
    OssLicense(
        name = "Core KTX",
        coordinates = listOf("androidx.core:core-ktx"),
        license = "Apache-2.0",
        url = "https://developer.android.com/jetpack/androidx/releases/core",
    ),
    OssLicense(
        name = "CameraX",
        coordinates = listOf(
            "androidx.camera:camera-core",
            "androidx.camera:camera-camera2",
            "androidx.camera:camera-lifecycle",
            "androidx.camera:camera-view",
        ),
        license = "Apache-2.0",
        url = "https://developer.android.com/jetpack/androidx/releases/camera",
    ),
    OssLicense(
        name = "Security Crypto",
        coordinates = listOf("androidx.security:security-crypto"),
        license = "Apache-2.0",
        url = "https://developer.android.com/jetpack/androidx/releases/security",
    ),
    OssLicense(
        name = "OkHttp",
        coordinates = listOf("com.squareup.okhttp3:okhttp"),
        license = "Apache-2.0",
        url = "https://square.github.io/okhttp/",
    ),
    OssLicense(
        name = "Gson",
        coordinates = listOf("com.google.code.gson:gson"),
        license = "Apache-2.0",
        url = "https://github.com/google/gson",
    ),
    OssLicense(
        name = "jsoup",
        coordinates = listOf("org.jsoup:jsoup"),
        license = "MIT",
        url = "https://jsoup.org/",
    ),
    OssLicense(
        name = "ZXing（扫码）",
        coordinates = listOf("com.google.zxing:core"),
        license = "Apache-2.0",
        url = "https://github.com/zxing/zxing",
    ),
    OssLicense(
        name = "MIUIX（KMP 组件）",
        coordinates = listOf(
            "top.yukonga.miuix.kmp:miuix-ui-android",
            "top.yukonga.miuix.kmp:miuix-preference-android",
            "top.yukonga.miuix.kmp:miuix-icons-android",
            "top.yukonga.miuix.kmp:miuix-squircle-android",
        ),
        license = "Apache-2.0",
        url = "https://github.com/miuix-kotlin-multiplatform/miuix",
    ),
    OssLicense(
        name = "Backdrop（液态玻璃）",
        coordinates = listOf("io.github.kyant0:backdrop-android"),
        license = "Apache-2.0",
        url = "https://github.com/Kyant0/AndroidLiquidGlass",
    ),
)

/**
 * 按许可证分组后的清单（纯函数，`OssLicensesTest` 钉住）：组内按名字排序，
 * 组按"第一条出现的顺序"排 —— 与 [ossLicenses] 的书写顺序一致（Apache 在前、MIT 在后）。
 */
internal fun groupLicensesByLicense(licenses: List<OssLicense>): List<Pair<String, List<OssLicense>>> {
    val order = LinkedHashSet<String>()
    licenses.forEach { order.add(it.license) }
    return order.map { license ->
        license to licenses.filter { it.license == license }.sortedBy { it.name }
    }
}
