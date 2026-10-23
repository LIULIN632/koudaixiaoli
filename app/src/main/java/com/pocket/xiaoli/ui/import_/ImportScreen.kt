package com.pocket.xiaoli.ui.import_

import android.graphics.BitmapFactory
import android.util.Base64
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.SyncAlt
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.pocket.xiaoli.PocketXiaoliApplication
import com.pocket.xiaoli.data.JwSystemType
import com.pocket.xiaoli.data.School
import com.pocket.xiaoli.data.isJwSystemAvailable
import com.pocket.xiaoli.network.ZhengfangSchool
import com.pocket.xiaoli.ui.components.AppCard
import com.pocket.xiaoli.ui.components.SchoolFavoriteButton
import com.pocket.xiaoli.ui.components.PocketPasswordTransformation
import com.pocket.xiaoli.ui.components.SectionCaption
import com.pocket.xiaoli.ui.profile.components.SchoolRequestDialog
import com.pocket.xiaoli.ui.theme.*
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.foundation.lazy.items
import com.pocket.xiaoli.ui.components.SchoolRow

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ImportScreen(
    onBack: () -> Unit,
    onGoSettings: (() -> Unit)? = null
) {
    val skin = LocalSkin.current
    val colors = LocalPocketXiaoliColors.current
    val context = LocalContext.current
    val app = context.applicationContext as PocketXiaoliApplication
    val viewModel: ImportViewModel = viewModel(
        factory = ImportViewModel.Factory(
            app.repository,
            app.appContainer.zhengfangClient,
            app.appContainer.qiangzhiClient,
            app.appContainer.schoolRegistry,
            app.appContainer
        )
    )
    val selectedSystem by viewModel.selectedSystem.collectAsStateWithLifecycle()
    val selectedSchool by viewModel.selectedSchool.collectAsStateWithLifecycle()
    val searchQuery by viewModel.searchQuery.collectAsStateWithLifecycle()
    val progress by viewModel.progress.collectAsStateWithLifecycle()
    val isLoading by viewModel.isLoading.collectAsStateWithLifecycle()
    val result by viewModel.result.collectAsStateWithLifecycle()
    val captchaBase64 by viewModel.captchaBase64.collectAsStateWithLifecycle()
    val showCaptcha by viewModel.showCaptcha.collectAsStateWithLifecycle()

    var username by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var captchaCode by remember { mutableStateOf("") }
    var rememberPassword by remember { mutableStateOf(false) }
    var showPassword by remember { mutableStateOf(false) }
    var showAddCustomSchool by remember { mutableStateOf(false) }
    var showSchoolRequest by remember { mutableStateOf(false) }
    var customSchoolName by remember { mutableStateOf("") }
    var customSchoolUrl by remember { mutableStateOf("") }
    var pendingCustomSchool by remember { mutableStateOf<School?>(null) }
    var editingCustomSchool by remember { mutableStateOf<School?>(null) }
    var schoolToDelete by remember { mutableStateOf<School?>(null) }
    var customJwType by remember { mutableStateOf<JwSystemType?>(null) }

    val filteredSchools by viewModel.filteredSchools.collectAsStateWithLifecycle()
    val favoriteIds by viewModel.favoriteIds.collectAsStateWithLifecycle()
    val campusOnly = viewModel.campusOnly

    // 逐级返回：成功卡片 → 退出；失败卡片 → 回登录表单；表单/选学校 → 选学校/选系统；初始页 → 退出
    fun handleBack() {
        val res = result
        if (isLoading) viewModel.cancelCaptcha()
        when {
            res?.success == true -> {
                // 导入成功：直接退出导入页
                viewModel.reset()
                onBack()
            }
            res != null -> {
                // 导入失败：回到登录表单，保留已选学校
                viewModel.reset()
            }
            campusOnly -> {
                // 专用客户端：本校锁定，不再退回学校列表
                viewModel.reset()
                onBack()
            }
            selectedSystem != null || selectedSchool != null -> {
                viewModel.goBack()
            }
            else -> {
                viewModel.reset()
                onBack()
            }
        }
    }
    BackHandler { handleBack() }

    // 点击学校：内置未收录地址的学校（baseUrl 空）→ 弹窗完善信息
    fun onSchoolClick(school: School) {
        if (school.baseUrl.isBlank()) {
            pendingCustomSchool = school
            customSchoolName = school.name
            customSchoolUrl = ""
            customJwType = selectedSystem
            showAddCustomSchool = true
        } else {
            viewModel.selectSchool(school)
        }
    }

    // 打开自定义学校编辑弹窗（预填名称/地址/类型）
    fun openEditCustomSchool(school: School) {
        editingCustomSchool = school
        customSchoolName = school.name
        customSchoolUrl = school.baseUrl.trimEnd('/')
            .removePrefix("https://").removePrefix("http://")
        customJwType = school.jwType
        showAddCustomSchool = true
    }

    // Auto-load bound / saved credentials when school changes
    LaunchedEffect(selectedSchool) {
        val school = selectedSchool ?: return@LaunchedEffect
        val saved = if (viewModel.campusOnly) {
            viewModel.boundCredentials
        } else {
            viewModel.loadSavedCredentials(school.baseUrl.trimEnd('/'))
        }
        if (saved != null) {
            username = saved.first
            password = saved.second
            rememberPassword = true
        }
    }

    // ⚠️ 这里**不**自动导入（2026-10 用户报"调入课表点进去自动开始"）。
    // 下面 `LockedSchoolCard` 里那个「一键导入」按钮就是同一条路，只是**由用户按**：
    // 进页面自动开始的三条代价（没要求就联网登录教务 / 立刻弹验证码框打断 / 慢网像卡死）
    // 与「成绩查询」那边完全一样，说明记在 `ZhengfangLoginViewModel.autoLoginIfBound` 的 KDoc。

    Scaffold(
        containerColor = colors.surfaceBase,
        topBar = {
            TopAppBar(
                title = { Text("导入本校课表", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = { handleBack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "返回")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = colors.surfaceCard)
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(horizontal = skin.spaceLg, vertical = skin.spaceMd),
            verticalArrangement = Arrangement.spacedBy(skin.spaceMd)
        ) {
            // Step 1: Select教务 system（仅非本校锁定时出现）
            if (!campusOnly && selectedSystem == null) {
                item { SectionCaption("选择教务系统") }
                item {
                    JwSystemGrid(
                        onSelect = { type ->
                            if (isJwSystemAvailable(type)) {
                                viewModel.selectSystem(type)
                            } else {
                                Toast.makeText(context, "该教务系统暂不支持原生导入，请使用「Web导入」方式", Toast.LENGTH_LONG).show()
                            }
                        }
                    )
                }
                return@LazyColumn
            }

            // Step 2: Select school（仅非本校锁定时出现）
            if (!campusOnly && selectedSchool == null) {
                val system = selectedSystem ?: return@LazyColumn
                item {
                    SectionCaption(
                        "选择学校 · ${system.label}",
                        Modifier.padding(top = skin.spaceSm),
                    )
                }
                item {
                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = { viewModel.updateSearch(it) },
                        placeholder = { Text("搜索学校名称或城市") },
                        leadingIcon = { Icon(Icons.Outlined.Search, null, tint = colors.textTertiary) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(skin.radiusMicro),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = accentColor(),
                            unfocusedBorderColor = colors.borderDivider
                        )
                    )
                }
                item {
                    OutlinedButton(
                        onClick = { showAddCustomSchool = true },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(skin.radiusMicro),
                        border = BorderStroke(1.dp, accentColor())
                    ) {
                        Icon(Icons.Outlined.Add, null, tint = accentColor(), modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(skin.spaceSmall))
                        Text("添加自定义学校", color = accentColor(), fontSize = 14.sp, fontWeight = FontWeight.Medium)
                    }
                }
                if (filteredSchools.isEmpty()) {
                    item {
                        Column(
                            modifier = Modifier.fillMaxWidth().padding(vertical = skin.spaceXxl),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                if (searchQuery.isEmpty()) "暂无可用学校" else "未找到「${searchQuery}」",
                                fontSize = 13.sp,
                                color = colors.textTertiary
                            )
                            if (searchQuery.isNotEmpty()) {
                                Spacer(Modifier.height(skin.spaceSmall))
                                TextButton(onClick = { showSchoolRequest = true }) {
                                    Text("不是你要找的学校？反馈", color = accentColor(), fontSize = 13.sp)
                                }
                            }
                        }
                    }
                } else {
                    val favSchools = filteredSchools.filter { it.id in favoriteIds }
                    val otherSchools = filteredSchools.filter { it.id !in favoriteIds }

                    // ── Favorites section ──
                    if (favSchools.isNotEmpty()) {
                        item {
                            SectionCaption(
                                "我的收藏",
                                Modifier.padding(top = skin.spaceSm, bottom = skin.spaceMicro),
                            )
                        }
                        items(favSchools, key = { it.id }) { school ->
                            val isSelected = selectedSchool == school
                            val isCustom = school.id.startsWith("custom_")
                            SchoolRow(
                                school = school,
                                isSelected = isSelected,
                                isFavorite = true,
                                onSelect = { onSchoolClick(school) },
                                onToggleFavorite = { viewModel.toggleFavorite(school) },
                                onEdit = if (isCustom) { { openEditCustomSchool(school) } } else null,
                                onDelete = if (isCustom) { { schoolToDelete = school } } else null
                            )
                            HorizontalDivider(color = colors.borderDivider, thickness = 0.5.dp)
                        }
                        item {
                            HorizontalDivider(
                                color = colors.textTertiary.copy(alpha = 0.3f),
                                thickness = 1.dp,
                                modifier = Modifier.padding(vertical = skin.spaceSm)
                            )
                        }
                        item {
                            SectionCaption(
                                "全部学校",
                                Modifier.padding(top = skin.spaceSm, bottom = skin.spaceMicro),
                            )
                        }
                    }

                    // ── All schools section ──
                    items(otherSchools, key = { it.id }) { school ->
                        val isSelected = selectedSchool == school
                        val isCustom = school.id.startsWith("custom_")
                        SchoolRow(
                            school = school,
                            isSelected = isSelected,
                            isFavorite = school.id in favoriteIds,
                            onSelect = { onSchoolClick(school) },
                            onToggleFavorite = { viewModel.toggleFavorite(school) },
                            onEdit = if (isCustom) { { openEditCustomSchool(school) } } else null,
                            onDelete = if (isCustom) { { schoolToDelete = school } } else null
                        )
                        HorizontalDivider(color = colors.borderDivider, thickness = 0.5.dp)
                    }
                }
                return@LazyColumn
            }

            if (campusOnly && selectedSchool != null) {
                item {
                    LockedSchoolCard(
                        school = selectedSchool!!,
                        boundUser = viewModel.boundCredentials?.first,
                        onOneTap = {
                            viewModel.boundCredentials?.let { (user, pass) ->
                                username = user
                                password = pass
                                viewModel.startLogin(user, pass)
                            }
                        }
                    )
                }
            }

            // Step 3: Login
            item {
                SectionCaption("登录教务系统", Modifier.padding(top = skin.spaceSm))
            }
            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = accentSoftColor()),
                    shape = RoundedCornerShape(skin.radiusMicro)
                ) {
                    Row(
                        modifier = Modifier.padding(skin.spaceMd),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Outlined.School, null, tint = accentColor(), modifier = Modifier.size(20.dp))
                        Spacer(modifier = Modifier.width(10.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(selectedSchool!!.name, fontWeight = FontWeight.Bold, fontSize = 14.sp, color = colors.textPrimary)
                            Text(selectedSchool!!.baseUrl, fontSize = 11.sp, color = colors.textTertiary)
                        }
                    }
                }
            }
            item {
                OutlinedTextField(
                    value = username,
                    onValueChange = { username = it },
                    label = { Text("学号") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !isLoading,
                    shape = RoundedCornerShape(skin.radiusMicro),
                    // ⚠️ 学号必须用 Ascii 键盘（不联想、不纠错）：默认文本键盘在中文输入法下会弹候选条，
                    // 打数字要走候选，而学号要跟系统**逐字**对上 —— 多一个自动纠错就是"登不上但看不出为什么"。
                    // 规矩与理由见 `ui/components/PocketField.kt` 的那张表（这里在补一处漏网的）。
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Ascii)
                )
            }
            item {
                OutlinedTextField(
                    value = password,
                    onValueChange = { password = it },
                    label = { Text("密码") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !isLoading,
                    shape = RoundedCornerShape(skin.radiusMicro),
                    visualTransformation = if (showPassword) VisualTransformation.None else PocketPasswordTransformation,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    trailingIcon = {
                        IconButton(onClick = { showPassword = !showPassword }) {
                            Icon(
                                if (showPassword) Icons.Outlined.VisibilityOff else Icons.Outlined.Visibility,
                                contentDescription = if (showPassword) "隐藏密码" else "显示密码",
                                tint = colors.textTertiary
                            )
                        }
                    }
                )
            }
            item {
                Row(
                    modifier = Modifier.fillMaxWidth().clickable { rememberPassword = !rememberPassword }.padding(vertical = skin.spaceXs),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Checkbox(
                        checked = rememberPassword,
                        onCheckedChange = { rememberPassword = it },
                        colors = CheckboxDefaults.colors(checkedColor = accentColor())
                    )
                    Spacer(Modifier.width(skin.spaceXs))
                    Text("记住密码", fontSize = 14.sp, color = colors.textSecondary)
                }
            }
            if (isLoading && progress.isNotEmpty()) {
                item {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = accentSoftColor()),
                        shape = RoundedCornerShape(skin.radiusMicro)
                    ) {
                        Row(
                            modifier = Modifier.padding(skin.spaceMd),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(18.dp),
                                color = accentColor(),
                                strokeWidth = 2.dp
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Text(progress, fontSize = 13.sp, color = accentColor())
                        }
                    }
                }
            }
            item {
                Button(
                    onClick = {
                        if (username.isNotBlank() && password.isNotBlank()) {
                            viewModel.startLogin(username, password)
                        }
                    },
                    modifier = Modifier.fillMaxWidth().height(48.dp),
                    enabled = !isLoading && username.isNotBlank() && password.isNotBlank(),
                    colors = ButtonDefaults.buttonColors(containerColor = accentColor()),
                    shape = RoundedCornerShape(skin.radiusMicro)
                ) {
                    Text(
                        if (isLoading) "导入中……" else "登录并导入",
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp
                    )
                }
            }
            if (result != null) {
                // 登录完成后处理密码记忆
                item {
                    LaunchedEffect(result) {
                        val school = viewModel.selectedSchool.value
                        if (school != null) {
                            val baseUrl = school.baseUrl.trimEnd('/')
                            if (result?.success == true && rememberPassword) {
                                viewModel.saveCredentials(baseUrl, username, password)
                            } else if (result?.success == true && !rememberPassword) {
                                viewModel.deleteCredentials(baseUrl)
                            }
                        }
                    }
                }
                item {
                    ImportResultCard(
                        success = result!!.success,
                        error = result!!.error,
                        courseCount = result!!.courses?.size ?: 0,
                        onOpenSettings = onGoSettings?.let { go -> { viewModel.reset(); go() } },
                        onBackToTimetable = { viewModel.reset(); onBack() },
                        onRetry = { viewModel.reset() },
                    )
                }
            }
        }
    }

    // 申请收录学校弹窗（搜索无结果时：预填关键词，走邮件申请）
    if (showSchoolRequest) {
        SchoolRequestDialog(
            context = context,
            initialName = searchQuery,
            onDismiss = { showSchoolRequest = false }
        )
    }

    // Add-custom-school dialog（手动添加 / 完善内置未收录地址的学校）
    if (showAddCustomSchool) {
        val system = selectedSystem
        AlertDialog(
            onDismissRequest = {
                showAddCustomSchool = false
                pendingCustomSchool = null
                editingCustomSchool = null
            },
            title = {
                Text(
                    when {
                        editingCustomSchool != null -> "编辑自定义学校"
                        pendingCustomSchool != null -> "完善学校信息"
                        else -> "添加自定义学校"
                    },
                    fontWeight = FontWeight.ExtraBold
                )
            },
            text = {
                Column {
                    Text(
                        when {
                            editingCustomSchool != null -> "修改学校名称或教务系统地址，保存后立即生效。"
                            pendingCustomSchool != null -> "该学校暂未收录教务地址，填写后即可导入。"
                            else -> "学校未收录？手动填写教务系统地址即可添加。"
                        },
                        fontSize = 12.sp,
                        color = colors.textTertiary
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    OutlinedTextField(
                        value = customSchoolName,
                        onValueChange = { customSchoolName = it },
                        label = { Text("学校名称") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = customSchoolUrl,
                        onValueChange = { customSchoolUrl = it },
                        label = { Text("教务系统地址") },
                        placeholder = { Text("例如 jw.example.edu.cn") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    Text("教务系统类型", fontSize = 12.sp, color = colors.textSecondary, fontWeight = FontWeight.Medium)
                    Spacer(modifier = Modifier.height(6.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(skin.spaceSmall)
                    ) {
                        JwSystemType.values().forEach { t ->
                            val selected = (customJwType ?: system) == t
                            Surface(
                                shape = RoundedCornerShape(skin.radiusChip),
                                color = if (selected) accentColor() else colors.surfaceAlt,
                                modifier = Modifier.clickable { customJwType = t }
                            ) {
                                Text(
                                    t.label,
                                    fontSize = 11.sp,
                                    color = if (selected) Color.White else colors.textSecondary,
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                                )
                            }
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val type = customJwType ?: system ?: JwSystemType.ZHENGFANG
                        when {
                            editingCustomSchool != null -> {
                                val updated = viewModel.updateCustomSchool(editingCustomSchool!!, customSchoolName, customSchoolUrl)
                                if (updated != null) {
                                    viewModel.selectSchool(updated)
                                    Toast.makeText(context, "已更新自定义学校", Toast.LENGTH_SHORT).show()
                                }
                            }
                            else -> {
                                val newSchool = viewModel.addCustomSchool(customSchoolName, customSchoolUrl, type)
                                if (newSchool != null) {
                                    viewModel.selectSchool(newSchool)
                                    Toast.makeText(context, "已添加自定义学校", Toast.LENGTH_SHORT).show()
                                }
                            }
                        }
                        customSchoolName = ""
                        customSchoolUrl = ""
                        customJwType = null
                        pendingCustomSchool = null
                        editingCustomSchool = null
                        showAddCustomSchool = false
                    },
                    enabled = customSchoolName.isNotBlank() && customSchoolUrl.isNotBlank(),
                    colors = ButtonDefaults.buttonColors(containerColor = accentColor())
                ) { Text(if (editingCustomSchool != null) "保存" else "添加") }
            },
            dismissButton = {
                TextButton(onClick = {
                    showAddCustomSchool = false
                    pendingCustomSchool = null
                    editingCustomSchool = null
                }) { Text("取消") }
            }
        )
    }

    // Delete-custom-school confirm dialog
    if (schoolToDelete != null) {
        val target = schoolToDelete!!
        AlertDialog(
            onDismissRequest = { schoolToDelete = null },
            title = { Text("删除自定义学校", fontWeight = FontWeight.ExtraBold) },
            text = { Text("确定要删除「${target.name}」吗？删除后需重新添加才能使用。") },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.removeCustomSchool(target)
                        if (selectedSchool?.id == target.id) viewModel.selectSchool(null)
                        Toast.makeText(context, "已删除自定义学校", Toast.LENGTH_SHORT).show()
                        schoolToDelete = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = colors.danger)
                ) { Text("删除", color = Color.White) }
            },
            dismissButton = {
                TextButton(onClick = { schoolToDelete = null }) { Text("取消") }
            }
        )
    }

    // Captcha dialog
    if (showCaptcha && captchaBase64 != null) {
        AlertDialog(
            onDismissRequest = { viewModel.cancelCaptcha() },
            title = { Text("安全验证", fontWeight = FontWeight.ExtraBold) },
            text = {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("请输入图片中的验证码", fontSize = 13.sp, color = colors.textTertiary)
                    Spacer(modifier = Modifier.height(12.dp))
                    val captchaBitmap = remember(captchaBase64) {
                        try {
                            val bytes = Base64.decode(captchaBase64, Base64.DEFAULT)
                            BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                        } catch (e: Exception) { null }
                    }
                    if (captchaBitmap != null) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Image(
                                bitmap = captchaBitmap.asImageBitmap(),
                                contentDescription = "验证码",
                                modifier = Modifier.weight(1f).height(70.dp).clip(RoundedCornerShape(skin.radiusMicro)),
                                contentScale = ContentScale.Fit
                            )
                            IconButton(
                                onClick = { viewModel.refreshCaptcha() },
                                modifier = Modifier.size(40.dp).invisibleTouchTarget()
                            ) {
                                Icon(
                                    Icons.Filled.SyncAlt,
                                    "刷新验证码",
                                    tint = accentColor(),
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                    } else {
                        Box(
                            modifier = Modifier.fillMaxWidth().height(70.dp).clip(RoundedCornerShape(skin.radiusMicro)).background(colors.surfaceAlt),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text("验证码加载失败", fontSize = 12.sp, color = colors.textTertiary)
                                TextButton(onClick = { viewModel.refreshCaptcha() }) {
                                    Text("点击重试", fontSize = 12.sp, color = accentColor())
                                }
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                    OutlinedTextField(
                        value = captchaCode,
                        onValueChange = { captchaCode = it },
                        label = { Text("验证码") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = { viewModel.submitCaptcha(captchaCode); captchaCode = "" },
                    enabled = captchaCode.isNotBlank(),
                    colors = ButtonDefaults.buttonColors(containerColor = accentColor())
                ) { Text("确认登录") }
            },
            dismissButton = {
                TextButton(onClick = { viewModel.cancelCaptcha() }) { Text("取消") }
            }
        )
    }
}

@Composable
internal fun JwSystemGrid(onSelect: (JwSystemType) -> Unit) {
    val skin = LocalSkin.current
    val colors = LocalPocketXiaoliColors.current
    val isDark = LocalDarkMode.current
    val types = JwSystemType.values().toList()
    Column(verticalArrangement = Arrangement.spacedBy(skin.spaceSm)) {
        for (row in types.chunked(2)) {
            Row(horizontalArrangement = Arrangement.spacedBy(skin.spaceSm)) {
                for (type in row) {
                    val available = isJwSystemAvailable(type)
                    val accent = systemAccent(type)
                    val tint = serviceAccentColor(accent)
                    AppCard(
                        onClick = { onSelect(type) },
                        modifier = Modifier
                            .weight(1f)
                            .height(96.dp),
                        shape = RoundedCornerShape(skin.radiusChip),
                        containerColor = if (available) {
                            serviceAccentContainer(accent)
                        } else {
                            colors.surfaceAlt
                        },
                        border = BorderStroke(
                            1.dp,
                            if (available) {
                                tint.copy(alpha = if (isDark) 0.42f else 0.30f)
                            } else {
                                colors.borderDivider
                            },
                        ),
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = skin.spaceCompact, vertical = skin.spaceMd),
                            verticalArrangement = Arrangement.Center,
                        ) {
                            Text(
                                type.label,
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (available) colors.textPrimary else colors.textTertiary,
                            )
                            Spacer(Modifier.height(skin.spaceXs))
                            Text(
                                if (available) type.description else "请用 Web 导入",
                                fontSize = 11.sp,
                                color = if (available) colors.textSecondary else colors.warning,
                            )
                        }
                    }
                }
                // Pad uneven row
                if (row.size < 2) {
                    Spacer(modifier = Modifier.weight(1f))
                }
            }
        }
    }
}

/** 教务系统 → 服务分类色的映射：同一类教务在服务页里也是同一个色相，跨页面对得上。 */
private fun systemAccent(type: JwSystemType): ServiceAccent = when (type) {
    JwSystemType.ZHENGFANG -> ServiceAccent.ACADEMIC
    JwSystemType.QIANGZHI -> ServiceAccent.LIFE
    JwSystemType.QINGGUO -> ServiceAccent.QUERY
    JwSystemType.CHAOXING -> ServiceAccent.STUDY
    JwSystemType.URP -> ServiceAccent.PAYMENT
}

/**
 * 导入结果卡。
 *
 * 旧版把成功/失败底色写死成 `#E8F5E9` / `#FFEBEE`（深色另写一档），标题不跟主题；
 * 失败时**没有任何按钮**，只能按返回键退回表单。现在配色走 `success` / `danger`，
 * 失败卡直接给「重新登录」。
 */
@Composable
internal fun ImportResultCard(
    success: Boolean,
    error: String?,
    courseCount: Int,
    onOpenSettings: (() -> Unit)?,
    onBackToTimetable: () -> Unit,
    onRetry: () -> Unit,
) {
    val skin = LocalSkin.current
    val colors = LocalPocketXiaoliColors.current
    val isDark = LocalDarkMode.current
    val tint = if (success) colors.success else colors.danger

    AppCard(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(skin.radiusChip),
        containerColor = tint.copy(alpha = if (isDark) 0.16f else 0.10f),
        border = BorderStroke(1.dp, tint.copy(alpha = if (isDark) 0.42f else 0.26f)),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(skin.spaceLg),
            verticalArrangement = Arrangement.spacedBy(skin.spaceSm),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(32.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(tint.copy(alpha = if (isDark) 0.26f else 0.16f)),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = if (success) Icons.Outlined.CheckCircle else Icons.Outlined.ErrorOutline,
                        contentDescription = null,
                        tint = tint,
                        modifier = Modifier.size(18.dp),
                    )
                }
                Spacer(Modifier.width(skin.spaceSm))
                Text(
                    text = if (success) "导入成功" else "导入失败",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = colors.textPrimary,
                )
                Spacer(Modifier.weight(1f))
                if (success) ImportTag(text = "已完成", tint = tint)
            }

            if (success) {
                Text(
                    "已导入 $courseCount 门课程",
                    fontSize = 13.sp,
                    color = colors.textSecondary,
                )
                if (onOpenSettings != null) {
                    Text(
                        "上课提醒与桌面小组件默认关闭，可在「我的 → 设置」中开启",
                        fontSize = 12.sp,
                        color = colors.textTertiary,
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(skin.spaceSm)) {
                    if (onOpenSettings != null) {
                        Button(
                            onClick = onOpenSettings,
                            modifier = Modifier.heightIn(min = 40.dp),
                            shape = RoundedCornerShape(skin.radiusChip),
                            colors = ButtonDefaults.buttonColors(containerColor = accentColor()),
                        ) { Text("开启提醒/小组件", fontSize = 13.sp) }
                        OutlinedButton(
                            onClick = onBackToTimetable,
                            modifier = Modifier.heightIn(min = 40.dp),
                            shape = RoundedCornerShape(skin.radiusChip),
                            border = BorderStroke(1.dp, accentColor().copy(alpha = 0.35f)),
                        ) { Text("返回课表", fontSize = 13.sp) }
                    } else {
                        Button(
                            onClick = onBackToTimetable,
                            modifier = Modifier.heightIn(min = 40.dp),
                            shape = RoundedCornerShape(skin.radiusChip),
                            colors = ButtonDefaults.buttonColors(containerColor = accentColor()),
                        ) { Text("返回课表", fontSize = 13.sp) }
                    }
                }
            } else {
                Text(
                    error ?: "登录没有通过，请检查学号、密码与验证码后重试。",
                    fontSize = 13.sp,
                    color = colors.textSecondary,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(skin.spaceSm)) {
                    Button(
                        onClick = onRetry,
                        modifier = Modifier.heightIn(min = 40.dp),
                        shape = RoundedCornerShape(skin.radiusChip),
                        colors = ButtonDefaults.buttonColors(containerColor = accentColor()),
                    ) { Text("重新登录", fontSize = 13.sp) }
                }
            }
        }
    }
}

@Composable
private fun LockedSchoolCard(
    school: School,
    boundUser: String?,
    onOneTap: () -> Unit,
) {
    val skin = LocalSkin.current
    val colors = LocalPocketXiaoliColors.current
    Card(
        colors = CardDefaults.cardColors(containerColor = accentSoftColor()),
        shape = RoundedCornerShape(skin.radiusChip),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(skin.spaceCompact)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Outlined.School, null, tint = accentColor(), modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(10.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(school.name, fontWeight = FontWeight.Bold, fontSize = 15.sp, color = colors.textPrimary)
                    Text(
                        "${school.jwType.label} · 已按当前校区锁定",
                        fontSize = 12.sp,
                        color = colors.textTertiary
                    )
                }
            }
            if (boundUser != null) {
                Spacer(Modifier.height(10.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "已绑定账号 $boundUser",
                        fontSize = 12.sp,
                        color = colors.textSecondary,
                        modifier = Modifier.weight(1f)
                    )
                    TextButton(onClick = onOneTap) {
                        Text("一键导入", color = accentColor(), fontWeight = FontWeight.Bold)
                    }
                }
            } else {
                Spacer(Modifier.height(skin.spaceSmall))
                Text(
                    "未绑定账号，可在「我的 → 绑定学号」后一键导入",
                    fontSize = 12.sp,
                    color = colors.textTertiary
                )
            }
        }
    }
}
