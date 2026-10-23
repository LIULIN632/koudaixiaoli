package com.pocket.xiaoli.ui.import_

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.PictureAsPdf
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pocket.xiaoli.ui.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PdfImportScreen(
    onBack: () -> Unit
) {
    val skin = LocalSkin.current
    val colors = LocalPocketXiaoliColors.current
    val isDark = LocalDarkMode.current
    val context = LocalContext.current
    var selectedFileName by remember { mutableStateOf<String?>(null) }
    var showResult by remember { mutableStateOf(false) }

    val pdfPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            val cursor = context.contentResolver.query(uri, null, null, null, null)
            val name = cursor?.use {
                val nameIndex = it.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                it.moveToFirst()
                if (nameIndex >= 0) it.getString(nameIndex) else "课表.pdf"
            } ?: "课表.pdf"
            selectedFileName = name
            showResult = true
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("PDF导入课表", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = {
                        showResult = false; onBack()
                    }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "返回")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = colors.surfaceCard)
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding).padding(skin.spaceLg),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            if (selectedFileName == null) {
                // ── Select PDF file ──
                Spacer(Modifier.height(skin.spaceHuge))
                Icon(Icons.Outlined.PictureAsPdf, null, tint = accentColor(), modifier = Modifier.size(64.dp))
                Spacer(Modifier.height(skin.spaceLg))
                Text("选择PDF课表文件", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = colors.textPrimary)
                Text("实验性功能 · 建议使用Excel/HTML导入", fontSize = 13.sp, color = colors.textTertiary)
                Spacer(Modifier.height(skin.spaceXxl))
                Button(
                    onClick = { pdfPickerLauncher.launch(arrayOf("application/pdf")) },
                    colors = ButtonDefaults.buttonColors(containerColor = accentColor()),
                    shape = RoundedCornerShape(skin.radiusMicro)
                ) {
                    Icon(Icons.Outlined.Description, null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(skin.spaceSm))
                    Text("选择文件")
                }

                Spacer(Modifier.height(skin.spaceHuge))
                Card(
                    shape = RoundedCornerShape(skin.radiusChip),
                    colors = CardDefaults.cardColors(containerColor = colors.surfaceCard)
                ) {
                    Column(modifier = Modifier.padding(skin.spaceLg)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Outlined.Info, null, tint = accentColor(), modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(skin.spaceSm))
                            Text("推荐方式", fontWeight = FontWeight.Bold, fontSize = 14.sp, color = colors.textPrimary)
                        }
                        Spacer(Modifier.height(skin.spaceSm))
                        Text(
                            "• Web导入：一键登录教务系统自动导入\n" +
                            "• Excel/HTML导入：WPS另存为即可\n" +
                            "• 截图AI识图：交给AI生成HTML导入",
                            fontSize = 12.sp, color = colors.textSecondary, lineHeight = 20.sp
                        )
                    }
                }

            } else {
                // ── Guidance result ──
                Spacer(Modifier.height(skin.spaceXxl))
                Card(
                    colors = CardDefaults.cardColors(containerColor = if (isDark) Color(0xFF3B3323) else Color(0xFFFFF8E1)),
                    shape = RoundedCornerShape(skin.radiusChip)
                ) {
                    Column(modifier = Modifier.padding(skin.spaceXl)) {
                        Text(
                            "⚠️ PDF解析暂未支持",
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp,
                            color = Color(0xFFE65100)
                        )
                        Spacer(Modifier.height(skin.spaceMd))
                        Text(
                            "仅有PDF课表？可通过以下方式导入：",
                            fontSize = 13.sp,
                            color = Color(0xFF795548)
                        )
                        Spacer(Modifier.height(10.dp))
                        GuidanceStep("1", "用WPS打开PDF，另存为Excel或HTML文件", "然后使用「Excel导入」或「HTML导入」")
                        Spacer(Modifier.height(skin.spaceSm))
                        GuidanceStep("2", "截图课表，交给AI识图", "让AI生成HTML表格，复制后使用「HTML导入」")
                        Spacer(Modifier.height(skin.spaceSm))
                        GuidanceStep("3", "推荐：网页教务导入", "一键登录，自动导入，无需处理文件格式")
                    }
                }

                Spacer(Modifier.height(skin.spaceLg))
                Row(horizontalArrangement = Arrangement.spacedBy(skin.spaceSm)) {
                    OutlinedButton(
                        onClick = { pdfPickerLauncher.launch(arrayOf("application/pdf")) },
                        shape = RoundedCornerShape(skin.radiusMicro)
                    ) { Text("重新选择") }
                    OutlinedButton(
                        onClick = { showResult = false; selectedFileName = null; onBack() },
                        shape = RoundedCornerShape(skin.radiusMicro)
                    ) { Text("其他导入方式") }
                }
            }
        }
    }
}

@Composable
private fun GuidanceStep(num: String, title: String, desc: String) {
    val skin = LocalSkin.current
    val colors = LocalPocketXiaoliColors.current
    Row(verticalAlignment = Alignment.Top) {
        Surface(
            shape = RoundedCornerShape(50),
            color = accentColor().copy(alpha = 0.15f)
        ) {
            Text(
                num, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = accentColor(),
                modifier = Modifier.padding(horizontal = skin.spaceSm, vertical = 2.dp)
            )
        }
        Spacer(Modifier.width(skin.spaceSm))
        Column {
            Text(title, fontSize = 13.sp, fontWeight = FontWeight.Medium, color = colors.textPrimary)
            Text(desc, fontSize = 11.sp, color = colors.textSecondary)
        }
    }
}
