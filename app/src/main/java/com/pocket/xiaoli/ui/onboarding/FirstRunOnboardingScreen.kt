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
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.outlined.School
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.pocket.xiaoli.core.campus.CampusConfig
import com.pocket.xiaoli.data.school.SchoolOption
import com.pocket.xiaoli.data.school.schoolOptions
import com.pocket.xiaoli.di.AppContainer
import com.pocket.xiaoli.ui.components.AppCard
import com.pocket.xiaoli.ui.components.SectionCaption
import com.pocket.xiaoli.ui.import_.ImportScreen
import com.pocket.xiaoli.ui.theme.LocalPocketXiaoliColors
import com.pocket.xiaoli.ui.theme.LocalSkin
import kotlinx.coroutines.launch

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
    val scope = rememberCoroutineScope()
    val registrySchools = container.schoolRegistry.allSchools
    val customSchools by container.customSchools.collectAsStateWithLifecycle()
    // 从第二步返回时要能看出"刚才选的是哪所"——否则用户以为没选成功，再点一次、再登录一次。
    // 读的是真源（学校已落盘），不是引导自己记的副本。
    val selectedSchoolId by container.academicSchoolId.collectAsStateWithLifecycle()

    val options = remember(registrySchools, customSchools) {
        schoolOptions(registrySchools + customSchools, CampusConfig.campuses)
    }

    if (step == 1) {
        ImportScreen(
            onBack = { step = 0 },
            onImported = onDone,
            // 这一趟本来就是来绑定账号的：默认勾上「记住密码」，用户仍可取消。
            rememberPasswordByDefault = true,
            successActionLabel = "开始使用",
        )
        return
    }

    FirstRunSchoolPicker(
        options = options,
        selectedSchoolId = selectedSchoolId,
        onPick = { option ->
            val school = container.resolveSchoolById(option.id) ?: return@FirstRunSchoolPicker
            scope.launch {
                container.switchAcademicSchool(school)
                step = 1
            }
        },
        onSkip = onDone,
    )
}

/**
 * 第一步的纯 UI：可接入学校列表 + 跳过。
 *
 * 判据不在这里 —— 列表由 [schoolOptions] 给出（选校白名单 ∩ 教务系统真支持）。
 * 引导与「绑定信息 → 换学校」必须共用同一份判据，否则一边能选、一边不能选。
 *
 * 标 `internal` 是为了快照测试能直接渲它：不碰 AppContainer，也不碰名单 IO。
 *
 * @param selectedSchoolId 已经选过的学校；高亮它，让"从第二步返回"不像是白走一趟。
 */
@Composable
internal fun FirstRunSchoolPicker(
    options: List<SchoolOption>,
    selectedSchoolId: String? = null,
    onPick: (SchoolOption) -> Unit,
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

        // 分两组而不是平铺十几张卡：目前只有两所配了校园服务与 3D 地图，
        // 混在一起会让人以为每所都有电费、洗衣这些入口。
        val configured = options.filter { it.hasCampusConfig }
        val rest = options.filter { !it.hasCampusConfig }
        if (configured.isNotEmpty()) {
            SectionCaption("校园服务与地图已收录")
            configured.forEach { option ->
                SchoolCard(option, selected = option.id == selectedSchoolId, onPick = onPick)
            }
        }
        if (rest.isNotEmpty()) {
            SectionCaption("可导入课表与成绩 · 校园服务暂未收录")
            rest.forEach { option ->
                SchoolCard(option, selected = option.id == selectedSchoolId, onPick = onPick)
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

@Composable
private fun SchoolCard(
    option: SchoolOption,
    selected: Boolean,
    onPick: (SchoolOption) -> Unit,
) {
    val skin = LocalSkin.current
    val colors = LocalPocketXiaoliColors.current
    // 图标底色按"校园服务收没收录"分两档：已收录用主色，未收录用中性次要色 ——
    // 一眼能看出点进去的差别，又不用多占一行小字。
    val accent = if (option.hasCampusConfig) colors.accentMain else colors.textSecondary

    AppCard(
        onClick = { onPick(option) },
        // 已选中的那张描一圈主色边：从第二步返回时，这一圈就是"刚才没白选"的证据
        border = if (selected) BorderStroke(2.dp, colors.accentMain) else null,
    ) {
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
                    .background(accent.copy(alpha = if (option.hasCampusConfig) 0.14f else 0.08f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Outlined.School,
                    contentDescription = null,
                    tint = accent,
                    modifier = Modifier.size(22.dp),
                )
            }
            Spacer(Modifier.width(skin.spaceMd))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    option.name,
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Bold,
                    color = colors.textPrimary,
                )
                Text(
                    option.subtitle,
                    fontSize = 12.sp,
                    color = colors.textSecondary,
                )
            }
            if (selected) {
                Icon(
                    imageVector = Icons.Filled.Check,
                    contentDescription = "已选择",
                    tint = colors.accentMain,
                    modifier = Modifier.size(22.dp),
                )
            } else {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                    contentDescription = null,
                    tint = colors.textTertiary,
                    modifier = Modifier.size(20.dp),
                )
            }
        }
    }
}
