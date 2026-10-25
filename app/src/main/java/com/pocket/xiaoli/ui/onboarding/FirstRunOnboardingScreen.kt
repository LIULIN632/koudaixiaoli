package com.pocket.xiaoli.ui.onboarding

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.outlined.School
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pocket.xiaoli.di.AppContainer
import com.pocket.xiaoli.ui.components.AppCard
import com.pocket.xiaoli.ui.import_.ImportScreen
import com.pocket.xiaoli.ui.theme.LocalPocketXiaoliColors
import com.pocket.xiaoli.ui.theme.LocalSkin
import com.pocket.xiaoli.ui.theme.ServiceAccent
import com.pocket.xiaoli.ui.theme.serviceAccentColor
import com.pocket.xiaoli.ui.theme.serviceAccentContainer

/**
 * 首次启动的向导：**选学校 → 用教务账号登录并导入课表**。
 *
 * ## 为什么值得拦这一下
 *
 * 同意条款后直接进首页，新用户的首页几乎是空的（没有课表、没有绑定），
 * 而"我是哪个学校的"决定后面所有入口（教务地址、电费、洗浴、成绩）。
 * 与其让用户以后在设置里找「当前校区」，不如第一次就问清楚。
 *
 * ## 只做两步，且第二步复用真实导入页
 *
 * 第二步**没有另写一套登录表单**：直接把 `ImportScreen` 嵌进来（校区已在第一步
 * 用 `setSelectedCampus` 落盘，它自己会按校区锁定学校）。验证码、旧版正方的兜底、
 * 失败重试、导入进度全都只有一份实现。
 *
 * ## 可以跳过
 *
 * 「先随便看看」直接进 App（默认校区是烟台理工）。首次引导是为了省事，不是为了
 * 把人挡在门外；跳过之后仍可在「我的 → 绑定信息」里绑定。
 */
@Composable
fun FirstRunOnboardingScreen(
    container: AppContainer,
    onDone: () -> Unit,
) {
    var step by rememberSaveable { mutableIntStateOf(0) }

    when (step) {
        0 -> FirstRunSchoolPicker(
            onPick = { campusId ->
                // 先落盘再进第二步：ImportViewModel 在构造时读 currentCampusSchool()，
                // 顺序反了就会读到上一个校区。
                container.setSelectedCampus(campusId)
                step = 1
            },
            onSkip = onDone,
        )

        else -> ImportScreen(
            onBack = { step = 0 },
            onImported = onDone,
            // 这一趟本来就是来绑定账号的：默认勾上「记住密码」，用户仍可取消。
            rememberPasswordByDefault = true,
            successActionLabel = "开始使用",
        )
    }
}

/** 向导里可选的两所学校（校区 id 必须与 `CampusConfig` 对得上）。 */
internal data class FirstRunSchool(
    val campusId: String,
    val title: String,
    val subtitle: String,
    val accent: ServiceAccent,
)

internal val FIRST_RUN_SCHOOLS = listOf(
    FirstRunSchool(
        campusId = "ytlg_main",
        title = "烟台理工学院",
        subtitle = "本校区 · 正方教务",
        accent = ServiceAccent.ACADEMIC,
    ),
    FirstRunSchool(
        campusId = "ytu_south",
        title = "烟台大学",
        subtitle = "南校区 · 正方教务",
        accent = ServiceAccent.QUERY,
    ),
)

/**
 * 第一步的纯 UI：两张学校卡 + 跳过。
 *
 * 标 `internal` 是为了快照测试能直接渲它（不碰 AppContainer）。
 */
@Composable
internal fun FirstRunSchoolPicker(
    onPick: (String) -> Unit,
    onSkip: () -> Unit,
) {
    val skin = LocalSkin.current
    val colors = LocalPocketXiaoliColors.current

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.surfaceBase)
            .statusBarsPadding()
            .navigationBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = skin.screenH, vertical = skin.spaceXl),
        verticalArrangement = Arrangement.spacedBy(skin.spaceMd),
    ) {
        Spacer(Modifier.height(skin.spaceXl))

        Text(
            "口袋小鲤",
            fontSize = 13.sp,
            fontWeight = FontWeight.Medium,
            color = colors.accentMain,
        )
        Text(
            "先选你的学校",
            fontSize = 26.sp,
            lineHeight = 34.sp,
            fontWeight = FontWeight.ExtraBold,
            color = colors.textPrimary,
        )
        Text(
            "课表、成绩、电费、洗浴都会按学校匹配；以后可以在设置里切换。",
            fontSize = 13.sp,
            lineHeight = 20.sp,
            color = colors.textSecondary,
        )

        Spacer(Modifier.height(skin.spaceSm))

        FIRST_RUN_SCHOOLS.forEach { school ->
            // AppCard 的 onClick 自带 MIUIX 按压反馈（与首页卡片同一套），
            // 大块卡片不适合用水波纹。
            AppCard(onClick = { onPick(school.campusId) }) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(skin.cardPaddingLarge),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(
                        modifier = Modifier
                            .size(44.dp)
                            .clip(RoundedCornerShape(skin.radiusChip))
                            .background(serviceAccentContainer(school.accent)),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.School,
                            contentDescription = null,
                            tint = serviceAccentColor(school.accent),
                            modifier = Modifier.size(22.dp),
                        )
                    }
                    Spacer(Modifier.width(skin.spaceMd))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            school.title,
                            fontSize = 17.sp,
                            fontWeight = FontWeight.Bold,
                            color = colors.textPrimary,
                        )
                        Text(
                            school.subtitle,
                            fontSize = 12.sp,
                            color = colors.textSecondary,
                        )
                    }
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                        contentDescription = null,
                        tint = colors.textTertiary,
                        modifier = Modifier.size(20.dp),
                    )
                }
            }
        }

        Spacer(Modifier.height(skin.spaceSm))

        Text(
            "下一步用教务账号登录一次，导入课表后就能用了。学号和密码只加密保存在本机。",
            fontSize = 12.sp,
            lineHeight = 18.sp,
            color = colors.textTertiary,
        )

        TextButton(
            onClick = onSkip,
            modifier = Modifier.align(Alignment.CenterHorizontally),
        ) {
            Text("先随便看看", fontSize = 13.sp, color = colors.textSecondary)
        }
    }
}
