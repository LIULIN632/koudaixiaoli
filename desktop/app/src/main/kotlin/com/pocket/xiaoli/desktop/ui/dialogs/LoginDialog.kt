package com.pocket.xiaoli.desktop.ui.dialogs

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.toComposeImageBitmap
import androidx.compose.ui.unit.dp
import com.pocket.xiaoli.desktop.data.CampusConfig
import com.pocket.xiaoli.desktop.data.jwTypeLabel
import com.pocket.xiaoli.desktop.state.AppState
import com.pocket.xiaoli.desktop.ui.components.PocketModal
import com.pocket.xiaoli.desktop.ui.components.PocketTextField
import com.pocket.xiaoli.desktop.ui.components.PrimaryButton
import com.pocket.xiaoli.desktop.ui.components.SecondaryButton
import com.pocket.xiaoli.desktop.ui.theme.PocketTypography
import com.pocket.xiaoli.desktop.ui.theme.accentSoftColor
import com.pocket.xiaoli.desktop.ui.theme.pocketColors
import java.io.ByteArrayInputStream
import java.util.Base64
import javax.imageio.ImageIO

/**
 * 从教务登录并导入课表。
 *
 * 协议与解析在 `:core`（与 Android 同一份）；这里只处理输入、验证码展示和结果提示。
 * 密码不落盘：只用于本次登录，登录结束后输入框随弹窗一起销毁。
 */
@Composable
fun LoginDialog(state: AppState) {
    if (!state.showLogin) return
    val colors = pocketColors()
    val campus = CampusConfig.byId(state.data.settings.campusId)
    val captcha = state.loginCaptcha

    PocketModal(
        title = "从教务导入",
        subtitle = listOfNotNull(campus?.name, campus?.jwTypeLabel).joinToString(" · ").ifBlank { "教育系统" },
        onDismiss = { state.closeLogin() },
        width = 540.dp,
        footer = {
            SecondaryButton("取消") { state.closeLogin() }
            Spacer(Modifier.weight(1f))
            PrimaryButton(
                text = when {
                    captcha != null -> "提交验证码"
                    state.loginBusy -> "登录中…"
                    else -> "登录"
                },
                enabled = captcha != null || !state.loginBusy,
                onClick = {
                    if (captcha != null) state.submitCaptcha() else state.startLogin()
                },
            )
        },
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(
                "登录成功后会替换当前学期的课程；你自建的日程会保留。密码只用于这次登录，不保存。",
                style = PocketTypography.caption,
                color = colors.textTertiary,
            )
            PocketTextField(
                value = state.loginUser,
                onValueChange = { state.loginUser = it },
                modifier = Modifier.fillMaxWidth(),
                placeholder = "学号",
                leadingIcon = com.pocket.xiaoli.desktop.ui.icons.PocketIcons.Profile,
                enabled = !state.loginBusy,
            )
            PocketTextField(
                value = state.loginPass,
                onValueChange = { state.loginPass = it },
                modifier = Modifier.fillMaxWidth(),
                placeholder = "密码",
                password = true,
                enabled = !state.loginBusy,
                onImeAction = { state.startLogin() },
            )

            if (state.loginProgress.isNotBlank()) {
                Text(state.loginProgress, style = PocketTypography.caption, color = colors.textSecondary)
            }
            state.loginError?.let {
                Text(it, style = PocketTypography.caption, color = colors.danger)
            }

            if (captcha != null) {
                Row(
                    Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    val bitmap = remember(captcha.base64) {
                        runCatching {
                            val bytes = Base64.getDecoder().decode(captcha.base64)
                            ImageIO.read(ByteArrayInputStream(bytes))?.toComposeImageBitmap()
                        }.getOrNull()
                    }
                    if (bitmap != null) {
                        Image(
                            bitmap = bitmap,
                            contentDescription = "验证码",
                            modifier = Modifier
                                .height(42.dp)
                                .width(128.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(accentSoftColor()),
                        )
                    } else {
                        Box(
                            Modifier
                                .height(42.dp)
                                .width(128.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(colors.surfaceAlt),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text("验证码", style = PocketTypography.label, color = colors.textTertiary)
                        }
                    }
                    SecondaryButton("换一张") { state.refreshLoginCaptcha() }
                    PocketTextField(
                        value = state.loginCaptchaCode,
                        onValueChange = { state.loginCaptchaCode = it },
                        modifier = Modifier.weight(1f),
                        placeholder = "验证码",
                        onImeAction = { state.submitCaptcha() },
                    )
                }
            }
        }
    }
}
