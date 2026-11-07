package com.pocket.xiaoli.ui.profile

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.sp
import com.pocket.xiaoli.data.legal.groupLicensesByLicense
import com.pocket.xiaoli.data.legal.ossLicenses
import com.pocket.xiaoli.ui.theme.LocalPocketXiaoliColors
import com.pocket.xiaoli.ui.theme.ServiceAccent

/**
 * 开源许可清单（整页）。
 *
 * 与致谢页的分工：致谢回答"这个 App 站在谁的肩膀上"（代码、接口、数据的出处），
 * 这一页回答"我装的东西里有哪些开源库、各是什么许可证、去哪儿核对"。
 * 两页分开是因为后者是**可逐条点开的清单**，几十行塞回致谢页会把那页压成一堵墙。
 *
 * ⚠️ 数据在 `data/legal/OssLicenses.kt`，由 `OssLicensesTest` 对着 build.gradle.kts 卡住 ——
 * **加依赖不登记会测红**，所以这张清单不会像散文那样悄悄过期。
 */
@Composable
internal fun LicensesContent() {
    val context = LocalContext.current
    val colors = LocalPocketXiaoliColors.current
    Text(
        "下面是我们主动选用的开源库，每条都能点开去项目主页核对许可证原文。" +
            "这些库自己带进来的传递依赖由各自的 POM 声明许可证，不在这张表里。",
        fontSize = 12.sp,
        color = colors.textTertiary,
        lineHeight = 18.sp,
    )
    groupLicensesByLicense(ossLicenses).forEach { (license, libs) ->
        SettingsSectionHeader("$license · ${libs.size} 个")
        SettingsCard {
            libs.forEachIndexed { index, lib ->
                SettingsMenuItem(
                    icon = Icons.Filled.Star,
                    title = lib.name,
                    // 坐标是给人核对用的：group:artifact 能直接搜到 Maven 页面。
                    // 多构件（Compose / CameraX / Room…）只列第一个 + 个数 ——
                    // 四行坐标会把一行信息撑成四行，反而没人看
                    subtitle = if (lib.coordinates.size == 1) {
                        lib.coordinates.first()
                    } else {
                        lib.coordinates.first() + "（共 ${lib.coordinates.size} 个构件）"
                    },
                    onClick = { openUrl(context, lib.url) },
                    accent = ServiceAccent.QUERY,
                )
                if (index != libs.lastIndex) SettingsDivider()
            }
        }
    }
}
