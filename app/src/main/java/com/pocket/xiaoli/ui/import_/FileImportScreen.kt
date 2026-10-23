package com.pocket.xiaoli.ui.import_

import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.DataObject
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.Html
import androidx.compose.material.icons.outlined.PictureAsPdf
import androidx.compose.material.icons.outlined.TableChart
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.pocket.xiaoli.PocketXiaoliApplication
import com.pocket.xiaoli.ui.components.SectionCaption
import com.pocket.xiaoli.ui.theme.LocalPocketXiaoliColors
import com.pocket.xiaoli.ui.theme.ServiceAccent
import com.pocket.xiaoli.util.CourseImportOutcome
import com.pocket.xiaoli.util.importCoursesFromUri
import kotlinx.coroutines.launch
import com.pocket.xiaoli.ui.theme.LocalSkin

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FileImportScreen(
    onBack: () -> Unit,
    onPdfImport: () -> Unit,
    onImported: () -> Unit = {},
) {
    val skin = LocalSkin.current
    val context = LocalContext.current
    val app = context.applicationContext as PocketXiaoliApplication
    val container = app.appContainer
    val scope = rememberCoroutineScope()
    val colors = LocalPocketXiaoliColors.current

    fun runImport(type: String, uri: Uri) {
        scope.launch {
            when (val outcome = importCoursesFromUri(
                context = context,
                repository = app.repository,
                uri = uri,
                type = type,
                schemeId = container.activeSchemeId.value
            )) {
                is CourseImportOutcome.Success -> {
                    container.recordImport("文件导入", outcome.added, true)
                    Toast.makeText(context, "成功导入 ${outcome.added} 门课程", Toast.LENGTH_SHORT).show()
                    onImported()
                }
                is CourseImportOutcome.AllDuplicates ->
                    Toast.makeText(context, "所有课程已存在，无需重复导入", Toast.LENGTH_SHORT).show()
                is CourseImportOutcome.Empty ->
                    Toast.makeText(context, "未识别到有效课程数据，请检查文件格式", Toast.LENGTH_LONG).show()
                is CourseImportOutcome.Failure -> {
                    container.recordImport("文件导入", 0, false, outcome.message)
                    Toast.makeText(context, "导入失败：${outcome.message ?: "文件格式不正确"}", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    val excelLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let { runImport("excel", it) }
    }
    val htmlLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let { runImport("html", it) }
    }
    val csvLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let { runImport("csv", it) }
    }
    val jsonLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let { runImport("backup", it) }
    }

    Scaffold(
        containerColor = colors.surfaceBase,
        topBar = {
            TopAppBar(
                title = { Text("文件导入", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "返回")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = colors.surfaceCard)
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = skin.spaceLg, vertical = skin.spaceMd),
            verticalArrangement = Arrangement.spacedBy(skin.spaceSm)
        ) {
            SectionCaption("课表文件")
            ImportOptionCard(
                icon = Icons.Outlined.TableChart,
                title = "Excel 课表",
                description = "支持 .xlsx / .xls，表头需包含课程名称等列",
                tag = "最常用",
                accent = ServiceAccent.LIFE,
                onClick = {
                    excelLauncher.launch(
                        arrayOf(
                            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                            "application/vnd.ms-excel"
                        )
                    )
                }
            )
            ImportOptionCard(
                icon = Icons.Outlined.Html,
                title = "HTML 课表",
                description = "支持 .html 表格文件",
                tag = "网页保存",
                accent = ServiceAccent.QUERY,
                onClick = { htmlLauncher.launch(arrayOf("text/html")) }
            )
            ImportOptionCard(
                icon = Icons.Outlined.Description,
                title = "CSV 课表",
                description = "支持 CSV 导出文件",
                tag = "轻量",
                accent = ServiceAccent.ACADEMIC,
                onClick = { csvLauncher.launch(arrayOf("text/csv", "text/comma-separated-values")) }
            )
            ImportOptionCard(
                icon = Icons.Outlined.DataObject,
                title = "JSON 备份",
                description = "恢复口袋小鲤导出的课表备份",
                tag = "备份恢复",
                accent = ServiceAccent.STUDY,
                onClick = { jsonLauncher.launch(arrayOf("application/json")) }
            )

            Spacer(Modifier.height(skin.spaceXs))
            SectionCaption("PDF")
            ImportOptionCard(
                icon = Icons.Outlined.PictureAsPdf,
                title = "PDF 课表",
                description = "PDF 解析暂未支持，提供转换指引",
                tag = "需转换",
                accent = ServiceAccent.INFO,
                onClick = onPdfImport
            )
            Text(
                text = "提示：导入会写入当前课表方案，并自动跳过重复课程。",
                fontSize = 12.sp,
                color = colors.textTertiary
            )
        }
    }
}