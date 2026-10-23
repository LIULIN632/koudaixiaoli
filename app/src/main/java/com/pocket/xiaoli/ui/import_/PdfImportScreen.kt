package com.pocket.xiaoli.ui.import_

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.PictureAsPdf
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pocket.xiaoli.ui.components.AppCard
import com.pocket.xiaoli.ui.components.SectionCaption
import com.pocket.xiaoli.ui.theme.LocalDarkMode
import com.pocket.xiaoli.ui.theme.LocalPocketXiaoliColors
import com.pocket.xiaoli.ui.theme.LocalSkin

/**
 * 「PDF 导入」：PDF 本身解析不了，所以这一页只做一件事 —— **把 PDF 换成能导入的格式**。
 *
 * ## 2026-10 改版
 *
 * 旧实现有三类问题：
 * 1. 结果卡写死了琥珀色（`0xFFFFF8E1` / `0xFFE65100` / `0xFF795548`），
 *    深色模式下也是浅黄底，换个配色主题完全不跟；
 * 2. 整页不可滚，大字号或小屏上「推荐方式」卡会被挤出去；
 * 3. 选了文件之后**不显示文件名**，用户不知道刚才选的是哪一份。
 *
 * 现在：配色全走 `LocalPocketXiaoliColors`（警示色用 `warning`），页面可滚动，
 * 结果卡顶部写明文件名；「选择文件」之外还给一条明确的下一步「换个格式导入」。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PdfImportScreen(
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val colors = LocalPocketXiaoliColors.current
    var pickedName by remember { mutableStateOf<String?>(null) }

    val pdfPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            pickedName = context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                val nameIndex = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                if (cursor.moveToFirst() && nameIndex >= 0) cursor.getString(nameIndex) else null
            } ?: "课表.pdf"
            Toast.makeText(context, "PDF 解析暂未支持，请按页面提示转换格式", Toast.LENGTH_SHORT).show()
        }
    }

    Scaffold(
        containerColor = colors.surfaceBase,
        topBar = {
            TopAppBar(
                title = { Text("PDF 导入", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "返回")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = colors.surfaceCard)
            )
        }
    ) { padding ->
        PdfImportPage(
            pickedName = pickedName,
            onChooseFile = { pdfPickerLauncher.launch(arrayOf("application/pdf")) },
            onBack = onBack,
            modifier = Modifier.padding(padding),
        )
    }
}

/** 页面正文（与系统文件选择器解耦，只为能单独截图与测语义）。 */
@Composable
internal fun PdfImportPage(
    pickedName: String?,
    onChooseFile: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val skin = LocalSkin.current
    val colors = LocalPocketXiaoliColors.current

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(colors.surfaceBase)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = skin.spaceLg, vertical = skin.spaceMd),
        verticalArrangement = Arrangement.spacedBy(skin.spaceSm),
    ) {
        if (pickedName == null) {
            PdfHeroCard()
            Button(
                onClick = onChooseFile,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 46.dp),
                shape = RoundedCornerShape(skin.radiusChip),
                colors = ButtonDefaults.buttonColors(containerColor = colors.accentMain),
            ) {
                Icon(Icons.Outlined.PictureAsPdf, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text("选择 PDF 文件", fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
            }
        } else {
            PickedFileCard(fileName = pickedName)
        }

        Spacer(Modifier.height(skin.spaceMicro))
        SectionCaption(if (pickedName == null) "推荐做法" else "按这三步转一下")
        GuidanceCard()

        if (pickedName != null) {
            Spacer(Modifier.height(skin.spaceSm))
            Row(horizontalArrangement = Arrangement.spacedBy(skin.spaceSm)) {
                Button(
                    onClick = onBack,
                    modifier = Modifier
                        .weight(1f)
                        .heightIn(min = 44.dp),
                    shape = RoundedCornerShape(skin.radiusChip),
                    colors = ButtonDefaults.buttonColors(containerColor = colors.accentMain),
                ) { Text("换个格式导入", fontSize = 14.sp) }
                OutlinedButton(
                    onClick = onChooseFile,
                    modifier = Modifier
                        .weight(1f)
                        .heightIn(min = 44.dp),
                    shape = RoundedCornerShape(skin.radiusChip),
                ) { Text("重新选择", fontSize = 14.sp) }
            }
        }

        Spacer(Modifier.height(skin.spaceMicro))
        Text(
            text = "PDF 解析仍在实验阶段，识别准确率不稳定，所以先给你最快的手动路线。",
            fontSize = 11.sp,
            color = colors.textTertiary,
        )
        Spacer(Modifier.height(skin.spaceSm))
    }
}

@Composable
private fun PdfHeroCard() {
    val skin = LocalSkin.current
    val colors = LocalPocketXiaoliColors.current
    val isDark = LocalDarkMode.current
    val tint = colors.warning

    AppCard(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(skin.spaceLg),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(RoundedCornerShape(15.dp))
                    .background(tint.copy(alpha = if (isDark) 0.22f else 0.14f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Outlined.PictureAsPdf,
                    contentDescription = null,
                    tint = tint,
                    modifier = Modifier.size(24.dp),
                )
            }
            Spacer(Modifier.width(skin.spaceMd))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "PDF 课表先转个格式",
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Bold,
                    color = colors.textPrimary,
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    text = "我们暂时不直接解析 PDF，转成 Excel 或 HTML 最稳",
                    fontSize = 12.sp,
                    color = colors.textSecondary,
                )
            }
        }
    }
}

@Composable
private fun PickedFileCard(fileName: String) {
    val skin = LocalSkin.current
    val colors = LocalPocketXiaoliColors.current
    val isDark = LocalDarkMode.current
    val tint = colors.warning

    AppCard(
        modifier = Modifier.fillMaxWidth(),
        containerColor = tint.copy(alpha = if (isDark) 0.14f else 0.07f),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(skin.spaceMd),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(38.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(tint.copy(alpha = if (isDark) 0.24f else 0.14f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Outlined.Description,
                    contentDescription = null,
                    tint = tint,
                    modifier = Modifier.size(20.dp),
                )
            }
            Spacer(Modifier.width(skin.spaceSm))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = fileName,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = colors.textPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = "这个 PDF 还不能直接解析",
                    fontSize = 11.sp,
                    color = colors.textSecondary,
                )
            }
            ImportTag(text = "需转换", tint = tint)
        }
    }
}

@Composable
private fun GuidanceCard() {
    val skin = LocalSkin.current
    val colors = LocalPocketXiaoliColors.current

    AppCard(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = skin.spaceMd, vertical = skin.spaceMd),
            verticalArrangement = Arrangement.spacedBy(skin.spaceMd),
        ) {
            GuidanceStep(
                num = "1",
                title = "用 WPS 另存为 Excel 或 HTML",
                desc = "回到「文件导入」，选对应格式即可",
            )
            GuidanceStep(
                num = "2",
                title = "截图课表交给鲤鱼识图",
                desc = "让它生成 HTML 表格，再走「HTML 导入」",
            )
            GuidanceStep(
                num = "3",
                title = "直接网页教务导入",
                desc = "登录一次自动识别，不用处理文件格式",
            )
        }
    }
}

@Composable
private fun GuidanceStep(num: String, title: String, desc: String) {
    val skin = LocalSkin.current
    val colors = LocalPocketXiaoliColors.current

    Row(verticalAlignment = Alignment.Top) {
        Box(
            modifier = Modifier
                .size(22.dp)
                .clip(RoundedCornerShape(percent = 50))
                .background(colors.accentMain.copy(alpha = 0.14f)),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = num,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                color = colors.accentMain,
            )
        }
        Spacer(Modifier.width(skin.spaceSm))
        Column {
            Text(title, fontSize = 13.sp, fontWeight = FontWeight.Medium, color = colors.textPrimary)
            Spacer(Modifier.height(1.dp))
            Text(desc, fontSize = 11.sp, color = colors.textSecondary)
        }
    }
}
