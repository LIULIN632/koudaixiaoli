package com.pocket.xiaoli.ui.profile

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pocket.xiaoli.ui.components.PocketField
import com.pocket.xiaoli.ui.components.PocketPasswordTransformation
import com.pocket.xiaoli.ui.theme.LocalPocketXiaoliColors
import com.pocket.xiaoli.ui.theme.LocalSkin
import com.pocket.xiaoli.util.YitAccountDefaults

/**
 * 「今日校园」「校园卡（易校园）」两张绑定表单，以及三张表单共用的**登录结果提示**。
 *
 * ## 为什么从 `AccountBindingScreen` 里抽出来
 *
 * 与 [AcademicAccountSection] 同一个理由：整页要吃 `AppContainer`（单测造不出来），
 * 所以这两张表单此前**一张视觉基线都没有** —— "绑定界面好不好看"只有教务那张被验过，
 * 改坏了另两张也看不出来。抽成纯组件后，快照渲的是生产代码本体。
 *
 * ## 校验规则仍归页面
 *
 * 错误文案由调用方用 `BindingValidators` 算好传进来；"点过提交才报错"的开关也留在页面。
 * 组件不猜、不实时校验 —— 与教务表单同一条约定。
 */

/**
 * 一次「验证 / 登录 / 查询」的界面状态（教务验证、今日校园一键登录、校园卡查余额共用）。
 *
 * 单独一个类型（而不是三组布尔）的理由：这四态**互斥且有序**，
 * 用布尔表达会出现"正在登录但已经显示成功"这种组合，而它没有任何意义。
 *
 * [Success.detail] / [Failed.hint] 是**第二行**的小字：前者说"接下来能做什么"，
 * 后者说"为什么可能失败 / 还能怎么救"。两条都只能是补充，
 * 主结论必须在第一行（[Success.message] / [Failed.message]）里说完。
 */
internal sealed interface BindingActionUi {
    data object Idle : BindingActionUi
    data class Running(val message: String) : BindingActionUi
    data class Success(val message: String, val detail: String? = null) : BindingActionUi
    data class Failed(val message: String, val hint: String? = null) : BindingActionUi
}

/**
 * 登录结果提示条 —— 三张表单共用，保证"成功 / 失败 / 进行中"在三个页面上长得一样。
 *
 * ⚠️ **不用**语义色低透明度当底色（`CLAUDE.md` 记过：深色下那点色差读不出来）。
 * 状态由**图标 + 字色**表达；失败的第二行用 `textSecondary`，
 * 那是用户"决定要不要再试一次"的依据，不能用最弱的 `textTertiary`。
 *
 * ⚠️ 进行中必须有**转圈**，不能只有一行灰字：验证 / 登录都要等网络，
 * 一行静态文字在慢网下看起来像"没反应"，用户会重复点（按钮虽禁用，但心里没底）。
 */
@Composable
internal fun BindingActionNotice(state: BindingActionUi) {
    val skin = LocalSkin.current
    val colors = LocalPocketXiaoliColors.current
    when (state) {
        BindingActionUi.Idle -> Unit

        is BindingActionUi.Running -> Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            CircularProgressIndicator(
                modifier = Modifier.size(16.dp),
                strokeWidth = 2.dp,
                color = colors.accentMain,
            )
            Spacer(Modifier.width(skin.spaceSm))
            Text(
                state.message,
                fontSize = 12.sp,
                lineHeight = 17.sp,
                color = colors.textSecondary,
            )
        }

        is BindingActionUi.Success -> BindingNoticeRow(
            icon = Icons.Outlined.CheckCircle,
            tint = colors.success,
            title = state.message,
            detail = state.detail,
        )

        is BindingActionUi.Failed -> BindingNoticeRow(
            icon = Icons.Outlined.ErrorOutline,
            tint = colors.danger,
            title = state.message,
            detail = state.hint,
        )
    }
}

@Composable
private fun BindingNoticeRow(
    icon: ImageVector,
    tint: Color,
    title: String,
    detail: String?,
) {
    val skin = LocalSkin.current
    val colors = LocalPocketXiaoliColors.current
    Row(modifier = Modifier.fillMaxWidth()) {
        // 图标与第一行文字顶对齐：两行文案时，图标挂在句子开头比垂直居中更像"这段的结论"
        Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(skin.spaceSm))
        Column(verticalArrangement = Arrangement.spacedBy(skin.spaceMicro)) {
            Text(
                title,
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
                lineHeight = 18.sp,
                color = tint,
            )
            if (!detail.isNullOrBlank()) {
                Text(
                    detail,
                    fontSize = 11.sp,
                    lineHeight = 16.sp,
                    color = colors.textSecondary,
                )
            }
        }
    }
}

/**
 * 今日校园（智慧校园统一认证）表单。
 *
 * 与教务账号**分开存**：两个系统初始密码规则一样，但改过密码后经常只改一边
 *（见 `CasLogin.CREDENTIAL_KEY` 的说明）。共用一份密码会出现"教务能登、今日校园登不上"。
 *
 * ## 两个动作的语义（2026-10 统一）
 *
 * | 动作 | 做什么 | 什么时候用 |
 * |---|---|---|
 * | **保存并登录**（主） | 真跑一次 CAS；成功后账号一并落盘 | 默认路径 —— 能确定账号对不对 |
 * | **仅保存账号**（次） | 只落盘，不联网 | 门户维护 / 暂时不想登录 |
 *
 * ⚠️ "登录成功 = 已保存"是刻意的：改造前登录成功只写 Cookie、不存账号，
 * 用户下次打开还是"未绑定"，得再输一遍 —— 那是三张表单里最莫名其妙的一条。
 *
 * @param hintStudentId 教务这边已填的学号，仅用于「学号」框的占位提示（"通常与教务学号相同"）
 * @param loginState 一键登录的结果（进行中 / 成功 / 需要验证码 / 失败）
 */
@Composable
internal fun CasAccountSection(
    studentId: String,
    onStudentIdChange: (String) -> Unit,
    studentIdError: String?,
    password: String,
    onPasswordChange: (String) -> Unit,
    passwordError: String?,
    passwordVisible: Boolean,
    onTogglePasswordVisible: () -> Unit,
    loginState: BindingActionUi,
    portalLoggedIn: Boolean?,
    hintStudentId: String,
    onSave: () -> Unit,
    onLogin: () -> Unit,
) {
    val skin = LocalSkin.current
    val colors = LocalPocketXiaoliColors.current
    val running = loginState is BindingActionUi.Running
    PocketField(
        label = "学号",
        value = studentId,
        onValueChange = onStudentIdChange,
        placeholder = if (hintStudentId.isNotBlank()) "通常与教务学号相同" else "输入学号",
        error = studentIdError,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Ascii),
    )
    PocketField(
        label = "智慧校园密码",
        value = password,
        onValueChange = onPasswordChange,
        placeholder = "与教务密码可能不同",
        error = passwordError,
        visualTransformation = if (passwordVisible) {
            VisualTransformation.None
        } else {
            PocketPasswordTransformation
        },
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
        trailingIcon = {
            PasswordVisibilityButton(passwordVisible, onTogglePasswordVisible)
        },
    )
    Text(
        "初始密码与教务同规则（${YitAccountDefaults.PREFIX} + 学号后 6 位）。"
            + "这份密码只存本机，只在你自己点「保存并登录」时用一次。",
        fontSize = 11.sp,
        color = colors.textTertiary,
        lineHeight = 16.sp,
    )

    // 登录态来自门户自己的接口（不是我们猜的）：读不到才写"未知"。
    // 收进一块 `surfaceHighlight` 底：它回答的是"现在能不能用"，
    // 与上面的输入、下面的动作都不是一类信息，靠留白分不开。
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(skin.radiusChip))
            .background(colors.surfaceHighlight)
            .padding(horizontal = skin.spaceCompact, vertical = skin.spaceSmall),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text("登录态", fontSize = 12.sp, color = colors.textSecondary, modifier = Modifier.weight(1f))
        StateDot(
            when {
                running -> BindState.UNKNOWN
                portalLoggedIn == true -> BindState.OK
                portalLoggedIn == false -> BindState.TODO
                else -> BindState.UNKNOWN
            }
        )
        Spacer(Modifier.width(skin.spaceXs))
        Text(
            when {
                running -> "登录中"
                portalLoggedIn == true -> "已登录"
                portalLoggedIn == false -> "未登录"
                else -> "未知"
            },
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium,
            // 已登录用成功色：这一行是"能不能用"的结论，不该是一条灰字
            color = if (portalLoggedIn == true && !running) colors.success else colors.textSecondary,
        )
    }

    BindingActionNotice(loginState)

    BindingPrimaryButton(
        text = when {
            running -> "正在登录…"
            // 「重新登录」只对已登录成立：存过账号但门户没登录时，说「重新」会让人以为已经登着
            portalLoggedIn == true -> "重新登录今日校园"
            else -> "保存并登录今日校园"
        },
        onClick = onLogin,
        enabled = !running,
    )
    BindingSecondaryButton(
        text = "仅保存账号，不登录",
        onClick = onSave,
        enabled = !running,
    )
}

/**
 * 校园卡（易校园）表单。
 *
 * 易校园是它自己的账号体系（手机号 + 密码），既不等于学号也不等于智慧校园密码。
 * 填了它，校园卡才有原生余额页；不填也不影响用（会退回拉起 App / 门户入口）。
 *
 * ## 两个动作的语义（与今日校园同一套）
 *
 * | 动作 | 做什么 | 什么时候用 |
 * |---|---|---|
 * | **保存并查询余额**（主） | 真登一次易校园；成功后账号与余额一起确认 | 默认路径 |
 * | **仅保存账号**（次） | 只落盘，不联网 | 就想先存着 |
 *
 * @param checkState 上一次「查余额」的结果（进行中 / 成功带余额 / 失败带原因）
 */
@Composable
internal fun CardAccountSection(
    phone: String,
    onPhoneChange: (String) -> Unit,
    phoneError: String?,
    password: String,
    onPasswordChange: (String) -> Unit,
    passwordError: String?,
    passwordVisible: Boolean,
    onTogglePasswordVisible: () -> Unit,
    bound: Boolean,
    checkState: BindingActionUi,
    onSave: () -> Unit,
    onCheck: () -> Unit,
) {
    val colors = LocalPocketXiaoliColors.current
    val running = checkState is BindingActionUi.Running
    PocketField(
        label = "手机号",
        value = phone,
        onValueChange = onPhoneChange,
        placeholder = "易校园登录手机号",
        error = phoneError,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
    )
    PocketField(
        label = "易校园密码",
        value = password,
        onValueChange = onPasswordChange,
        placeholder = "与教务 / 智慧校园都不是同一个",
        error = passwordError,
        visualTransformation = if (passwordVisible) {
            VisualTransformation.None
        } else {
            PocketPasswordTransformation
        },
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
        trailingIcon = {
            PasswordVisibilityButton(passwordVisible, onTogglePasswordVisibility = onTogglePasswordVisible)
        },
    )
    Text(
        "校园卡余额在易校园（小富宝）平台上，用的是它自己的登录账号。"
            + "密码只存本机，只在查余额时用一次；不填就只能进 App / 门户自己看。",
        fontSize = 11.sp,
        color = colors.textTertiary,
        lineHeight = 16.sp,
    )
    // 分隔线：上面是"填账号"，下面是"提交 + 查询"。
    HorizontalDivider(color = colors.borderDivider, thickness = 0.5.dp)

    BindingActionNotice(checkState)

    BindingPrimaryButton(
        text = when {
            running -> "正在查询…"
            bound -> "再查一次余额"
            else -> "保存并查询余额"
        },
        onClick = onCheck,
        enabled = !running,
    )
    BindingSecondaryButton(
        text = "仅保存账号，不查询",
        onClick = onSave,
        enabled = !running,
    )
}

/** 密码框右侧的显示 / 隐藏切换 —— 两张表单共用，图标与提示语只写一次。 */
@Composable
private fun PasswordVisibilityButton(visible: Boolean, onTogglePasswordVisibility: () -> Unit) {
    val colors = LocalPocketXiaoliColors.current
    IconButton(onClick = onTogglePasswordVisibility) {
        Icon(
            if (visible) Icons.Outlined.VisibilityOff else Icons.Outlined.Visibility,
            contentDescription = if (visible) "隐藏密码" else "显示密码",
            tint = colors.textTertiary,
        )
    }
}

/**
 * 绑定页的主动作按钮（实心主色、48dp 高、满圆角）。
 *
 * 三张表单此前各自抄了一遍同样的 `Button` + `ButtonDefaults`（含禁用态的两个显式色），
 * 抽在一起，改一次三处同步。禁用态**显式给色**：不给会退回 Material 默认的
 * `onSurface` 12% —— 一枚与页面无关的灰疙瘩。
 */
@Composable
internal fun BindingPrimaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    val colors = LocalPocketXiaoliColors.current
    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.fillMaxWidth().height(48.dp),
        shape = RoundedCornerShape(50),
        colors = ButtonDefaults.buttonColors(
            containerColor = colors.accentMain,
            disabledContainerColor = colors.surfaceHighlight,
            disabledContentColor = colors.textTertiary,
        ),
    ) {
        Text(text, fontWeight = FontWeight.Bold)
    }
}

/** 绑定页的次动作按钮（描边、46dp 高），用于"仅保存 / 查一下余额"这类辅助动作。 */
@Composable
internal fun BindingSecondaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    OutlinedButton(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.fillMaxWidth().height(46.dp),
        shape = RoundedCornerShape(50),
    ) {
        Text(text, fontSize = 15.sp)
    }
}
