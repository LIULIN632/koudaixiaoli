package com.pocket.xiaoli.ui.zhengfang

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pocket.xiaoli.data.School
import com.pocket.xiaoli.ui.components.PocketPasswordTransformation
import com.pocket.xiaoli.ui.theme.*
import androidx.lifecycle.compose.collectAsStateWithLifecycle

/**
 * 正方教务「只登录」流程的共享 UI 组件（选校列表 / 登录表单 / 验证码弹窗）。
 * 状态契约见 ZhengfangLoginViewModel 中的 [ZhengfangLoginState]。
 */

// ── 阶段 1：选择学校 ──

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ZhengfangSchoolList(
    state: ZhengfangLoginState,
    modifier: Modifier = Modifier,
    header: (@Composable () -> Unit)? = null
) {
    val skin = LocalSkin.current
    val colors = LocalPocketXiaoliColors.current
    val schools by state.filteredSchools.collectAsStateWithLifecycle()
    val query by state.searchQuery.collectAsStateWithLifecycle()

    Column(modifier = modifier.fillMaxSize()) {
        header?.invoke()
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = skin.spaceLg)
        ) {
            OutlinedTextField(
                value = query,
                onValueChange = { state.updateSearch(it) },
                placeholder = { Text("搜索学校名称……", color = colors.textTertiary) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = accentColor(),
                    cursorColor = accentColor()
                ),
                shape = RoundedCornerShape(skin.radiusChip)
            )
            Spacer(Modifier.height(skin.spaceXs))
            Text(
                "正方教务系统学校",
                fontSize = 12.sp,
                color = colors.textTertiary,
                modifier = Modifier.padding(start = skin.spaceXs)
            )
            Spacer(Modifier.height(skin.spaceSm))

            if (schools.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(
                        "未找到匹配的学校，可先在「教务导入」中添加自定义学校",
                        color = colors.textTertiary,
                        fontSize = 13.sp
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(skin.spaceSm)
                ) {
                    items(schools, key = { it.id }) { school ->
                        ZhengfangSchoolRow(school = school, onClick = { state.selectSchool(school) })
                    }
                }
            }
        }
    }
}

@Composable
fun ZhengfangSchoolRow(school: School, onClick: () -> Unit) {
    val skin = LocalSkin.current
    val colors = LocalPocketXiaoliColors.current
    Card(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        shape = RoundedCornerShape(skin.radiusChip),
        colors = CardDefaults.cardColors(containerColor = colors.surfaceCard)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = skin.spaceLg, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    school.name,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    color = colors.textPrimary
                )
                Spacer(Modifier.height(skin.spaceMicro))
                Text(
                    "${school.city} · ${school.jwType.label}",
                    fontSize = 12.sp,
                    color = colors.textTertiary
                )
            }
            Text(
                "选择",
                fontSize = 13.sp,
                color = accentColor(),
                fontWeight = FontWeight.Medium
            )
        }
    }
}

// ── 阶段 2：登录 ──

/**
 * 登录表单（学校卡片 + 学号/密码 + 进度/错误 + 登录按钮）。
 * [hint] 为登录说明文案，[buttonText] 为按钮文案；
 * [topBackLabel] 非空时在顶部显示返回按钮（如「返回列表」）。
 */
@Composable
fun ZhengfangLoginContent(
    state: ZhengfangLoginState,
    hint: String,
    buttonText: String,
    modifier: Modifier = Modifier,
    topBackLabel: String? = null,
    onTopBack: (() -> Unit)? = null
) {
    val skin = LocalSkin.current
    val colors = LocalPocketXiaoliColors.current
    val school by state.selectedSchool.collectAsStateWithLifecycle()
    val username by state.username.collectAsStateWithLifecycle()
    val password by state.password.collectAsStateWithLifecycle()
    val isLoading by state.isLoading.collectAsStateWithLifecycle()
    val progress by state.progress.collectAsStateWithLifecycle()
    val error by state.error.collectAsStateWithLifecycle()
    val boundStudentId by state.boundStudentId.collectAsStateWithLifecycle()
    val boundStatus by state.boundLoginStatus.collectAsStateWithLifecycle()
    var useOtherAccount by remember { mutableStateOf(false) }
    val useBound = !boundStudentId.isNullOrBlank() && !useOtherAccount

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(skin.spaceLg),
        verticalArrangement = Arrangement.spacedBy(skin.spaceMd)
    ) {
        if (topBackLabel != null && onTopBack != null) {
            TextButton(onClick = onTopBack) {
                Text(topBackLabel, fontSize = 13.sp)
            }
        }

        // 当前学校（可换）
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(skin.radiusChip),
            colors = CardDefaults.cardColors(containerColor = colors.surfaceCard)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = skin.spaceLg, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    school?.name ?: "",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = colors.textPrimary,
                    modifier = Modifier.weight(1f)
                )
                if (!state.campusOnly) {
                    TextButton(onClick = { state.backToSchoolList() }) {
                        Text("换学校", fontSize = 13.sp)
                    }
                }
            }
        }

        Text(
            hint,
            fontSize = 12.sp,
            color = colors.textTertiary
        )

        if (useBound) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(skin.radiusChip),
                colors = CardDefaults.cardColors(containerColor = colors.surfaceHighlight)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = skin.spaceCompact, end = 6.dp, top = 6.dp, bottom = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            "使用已绑定账号",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Medium,
                            color = colors.textPrimary
                        )
                        Spacer(Modifier.height(skin.spaceMicro))
                        Text(
                            "学号 ${BoundLogin.maskStudentId(boundStudentId.orEmpty())}",
                            fontSize = 12.sp,
                            color = colors.textSecondary
                        )
                    }
                    TextButton(onClick = { useOtherAccount = true }) {
                        Text("换个账号", fontSize = 12.sp)
                    }
                }
            }
        }

        if (!useBound) {  // 换账号时才显示手动输入
        OutlinedTextField(
            value = username,
            onValueChange = { state.updateUsername(it) },
            label = { Text("学号") },
            singleLine = true,
            enabled = !isLoading,
            modifier = Modifier.fillMaxWidth(),
            // 学号不要中文候选（见 PocketField 的 KDoc：账号类字段的 IME 约定）
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Ascii),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = accentColor(),
                cursorColor = accentColor()
            ),
            shape = RoundedCornerShape(skin.radiusChip)
        )
        OutlinedTextField(
            value = password,
            onValueChange = { state.updatePassword(it) },
            label = { Text("密码") },
            singleLine = true,
            enabled = !isLoading,
            // ⚠️ 共享实例 + `KeyboardType.Password`：内联新建变换实例会让输入法组合中的字抖/丢，
            // 而缺了键盘类型则输入法仍在组词、与圆点显示打架（2026-10「绑定界面输入有问题」的根因）
            visualTransformation = PocketPasswordTransformation,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
            modifier = Modifier.fillMaxWidth(),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = accentColor(),
                cursorColor = accentColor()
            ),
            shape = RoundedCornerShape(skin.radiusChip)
        )
        }  // end if (!useBound)

        if (error != null) {
            Text(
                if (useBound && boundStatus == BoundLoginStatus.FAILED) {
                    "已绑定账号自动登录未成功，可点下方按钮重试，或「换个账号」手动登录。${error}"
                } else error!!,
                color = colors.danger,
                fontSize = 13.sp
            )
        }

        if (isLoading) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                CircularProgressIndicator(
                    modifier = Modifier.size(18.dp),
                    strokeWidth = 2.dp,
                    color = accentColor()
                )
                Spacer(Modifier.width(10.dp))
                Text(
                    progress.ifBlank { "登录中……" },
                    fontSize = 13.sp,
                    color = colors.textTertiary
                )
            }
        }

        Button(
            onClick = { state.startLogin() },
            enabled = !isLoading && username.isNotBlank() && password.isNotBlank(),
            modifier = Modifier
                .fillMaxWidth()
                .height(46.dp),
            shape = RoundedCornerShape(skin.radiusChip),
            colors = ButtonDefaults.buttonColors(containerColor = accentColor())
        ) {
            Text(
                if (useBound) "使用已绑定账号登录" else buttonText,
                color = Color.White,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

// ── 验证码弹窗 ──

/** 监听 [state] 的验证码状态，需要时弹出验证码弹窗（三个查询页面共用） */
@Composable
fun ZhengfangCaptchaHost(state: ZhengfangLoginState) {
    val showCaptcha by state.showCaptcha.collectAsStateWithLifecycle()
    val captchaBase64 by state.captchaBase64.collectAsStateWithLifecycle()
    if (showCaptcha && captchaBase64 != null) {
        ZhengfangCaptchaDialog(
            captchaBase64 = captchaBase64!!,
            onConfirm = { state.submitCaptcha(it) },
            onRefresh = { state.refreshCaptcha() },
            onCancel = { state.cancelCaptcha() }
        )
    }
}

@Composable
fun ZhengfangCaptchaDialog(
    captchaBase64: String,
    onConfirm: (String) -> Unit,
    onRefresh: () -> Unit,
    onCancel: () -> Unit
) {
    val skin = LocalSkin.current
    val colors = LocalPocketXiaoliColors.current
    var code by remember { mutableStateOf("") }
    val bitmap = remember(captchaBase64) { decodeBase64Bitmap(captchaBase64) }

    AlertDialog(
        onDismissRequest = onCancel,
        title = { Text("输入验证码", fontWeight = FontWeight.ExtraBold) },
        text = {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                if (bitmap != null) {
                    Image(
                        bitmap = bitmap.asImageBitmap(),
                        contentDescription = "验证码",
                        modifier = Modifier
                            .height(72.dp)
                            .background(colors.surfaceCard, RoundedCornerShape(skin.radiusMicro))
                    )
                } else {
                    Text("验证码加载失败，点击刷新重试", fontSize = 12.sp, color = colors.textTertiary)
                }
                Spacer(Modifier.height(10.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(
                        value = code,
                        onValueChange = { code = it },
                        placeholder = { Text("请输入验证码") },
                        singleLine = true,
                        modifier = Modifier.weight(1f),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = accentColor(),
                            cursorColor = accentColor()
                        ),
                        shape = RoundedCornerShape(skin.radiusChip)
                    )
                    Spacer(Modifier.width(skin.spaceSm))
                    IconButton(onClick = onRefresh) {
                        Icon(Icons.Outlined.Refresh, "刷新验证码", tint = colors.textSecondary)
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { onConfirm(code.trim()) },
                enabled = code.isNotBlank(),
                colors = ButtonDefaults.buttonColors(containerColor = accentColor())
            ) { Text("确认", color = Color.White) }
        },
        dismissButton = {
            TextButton(onClick = onCancel) { Text("取消") }
        }
    )
}

// ── 公共小工具 ──

// 原 scoreColor()（成绩 → 硬编码色）已移除：它返回固定颜色，不随配色主题与
// 深浅模式变化，深色模式下"深绿"压在深色卡片上对比度不足。
// 已由 ui/components/infocard/ScoreBand.kt 的 scoreBandOf(...).color() 取代 ——
// 先判档位（FAIL/PASS/EXCELLENT），再由主题给语义色。

internal fun decodeBase64Bitmap(b64: String): Bitmap? = try {
    val bytes = android.util.Base64.decode(b64, android.util.Base64.DEFAULT)
    if (bytes.isEmpty()) null else BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
} catch (e: Exception) {
    null
}
