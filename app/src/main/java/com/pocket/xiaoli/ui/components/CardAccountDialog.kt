package com.pocket.xiaoli.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pocket.xiaoli.ui.theme.LocalPocketXiaoliColors
import com.pocket.xiaoli.ui.theme.LocalSkin

/**
 * 就地绑定校园卡（易校园）账号。
 *
 * ## 为什么要有这个弹窗
 *
 * 需求方反馈：没绑校园卡账号时，点「校园卡」**只能跳到门户网页**里去，很别扭。
 * 问题是实在的：余额只有易校园自己的接口能查（见 `YxyCardRepository`），
 * 而那个接口要易校园账号（**手机号 + 密码**，与学号、智慧校园都不是同一套）。
 * 所以这里把"缺账号"这件事当场解决：就地填、就地存、就地查，
 * 而不是把用户丢进网页让他自己找。**不填也行** —— 旁边就给装官方 App 的入口。
 */
@Composable
fun CardAccountDialog(
    initialPhone: String = "",
    onSave: (phone: String, password: String) -> Unit,
    onInstallApp: () -> Unit,
    onDismiss: () -> Unit,
) {
    val skin = LocalSkin.current
    val colors = LocalPocketXiaoliColors.current
    var phone by remember { mutableStateOf(initialPhone) }
    var password by remember { mutableStateOf("") }
    var visible by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("绑定校园卡（易校园）", fontWeight = FontWeight.Bold) },
        text = {
            Column {
                Text(
                    "校园卡余额要走易校园自己的账号（手机号 + 密码），不是学号、" +
                        "也不是智慧校园那份。填一次，之后点校园卡直接看余额。",
                    fontSize = 12.sp,
                    color = colors.textSecondary,
                    lineHeight = 18.sp,
                )
                Spacer(Modifier.height(skin.spaceMd))
                PocketField(
                    label = "手机号",
                    value = phone,
                    onValueChange = { phone = it },
                    placeholder = "易校园登录手机号",
                    // 与绑定信息页同一套 IME 约定（见 PocketField 的 KDoc）：
                    // 账号类字段不要中文候选，密码类字段要输入法的"密码模式"
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                )
                Spacer(Modifier.height(skin.spaceMd))
                PocketField(
                    label = "易校园密码",
                    value = password,
                    onValueChange = { password = it },
                    placeholder = "首次登录一般是身份证后 6 位",
                    visualTransformation = if (visible) {
                        VisualTransformation.None
                    } else {
                        PocketPasswordTransformation
                    },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    trailingIcon = {
                        IconButton(onClick = { visible = !visible }) {
                            Icon(
                                if (visible) Icons.Outlined.VisibilityOff else Icons.Outlined.Visibility,
                                contentDescription = if (visible) "隐藏密码" else "显示密码",
                                tint = colors.textTertiary,
                            )
                        }
                    },
                )
                Spacer(Modifier.height(skin.spaceSm))
                Text(
                    "账号密码只存本机加密存储，只在查余额时用一次。",
                    fontSize = 11.sp,
                    color = colors.textTertiary,
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { onSave(phone.trim(), password) },
                enabled = phone.isNotBlank() && password.isNotBlank(),
                shape = RoundedCornerShape(50),
                colors = ButtonDefaults.buttonColors(containerColor = colors.accentMain),
            ) {
                Text("保存并查余额")
            }
        },
        dismissButton = {
            Column(horizontalAlignment = androidx.compose.ui.Alignment.End) {
                TextButton(onClick = onInstallApp) {
                    Text("去应用商店装易校园", color = colors.accentMain, fontSize = 13.sp)
                }
                TextButton(onClick = onDismiss) { Text("取消") }
            }
        },
    )
}
