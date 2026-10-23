package com.pocket.xiaoli.ui.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pocket.xiaoli.ui.theme.LocalPocketXiaoliColors
import com.pocket.xiaoli.ui.theme.LocalSkin

/**
 * 「添加待办」表单的状态。
 *
 * ## 为什么要有个状态类，而不是把 `remember` 写在对话框里
 *
 * 「保存」按钮在 `AlertDialog` 的 `confirmButton` 槽里，**不在** `text` 槽里 ——
 * 表单字段与按钮分处两棵树，状态必须提升到它们的共同祖先（[AddTodoDialog]）。
 * 提出一个 [Stable] 状态类比往下传 12 个参数干净，也让表单正文
 *（[AddTodoForm]）能**脱离对话框单独渲染** —— 那正是快照测试需要的。
 */
@Stable
internal class AddTodoFormState(existingCourses: List<String>) {

    /** 去重后的课程建议；空列表表示"没有建议、直接手填"。 */
    val courses: List<String> = existingCourses.distinct()

    var content by mutableStateOf("")
    var dueDate by mutableStateOf("")
    var course by mutableStateOf("")

    /** 有建议课程时默认走下拉（少打字、且课名一致），点了「+ 自定义输入」就切成手填。 */
    var usePicker by mutableStateOf(courses.isNotEmpty())
    var courseExpanded by mutableStateOf(false)
    var contentError by mutableStateOf(false)
}

/**
 * 首页右下角 FAB 点开的**唯一**录入对话框。
 *
 * ## 为什么只有这一个
 *
 * 改造前首页有两个录入入口：标题右侧的「新增 / 管理」（走作业对话框）与列表里的
 * 行内输入框（走自定义待办）。用户 2026-10 的要求是
 * **「创建任务和作业改成一个，只保留待办」** —— 于是两处合成这一处。
 *
 * ## 字段
 *
 * - **待办内容（必填）**：只有它拦人 —— 一条没有内容的待办没有任何意义；
 * - **课程（可选）**：有建议课时给下拉（避免"课程名写得不一样"导致挂不到一起），
 *   没有就直接填。纯自定义待办（如"取快递"）留空即可；
 * - **截止日期（可选）**：自由文本，与教务导入的作业同一个字段、同一种写法
 *   （`6月15日` / `下周一` 都行）。能不能解析成日期是另一回事，见 [isOverdue]。
 *
 * ## ⚠️ 它不解释"存哪张表"
 *
 * 「填了课程或截止日期 → 落 `homework` 表」是存储细节（`tasks` 表没有那个字段），
 * 界面上只有「待办」一个概念。见 `TodoRows.kt` 文件头的说明。
 *
 * ## 为什么不需要 `Modifier.reportTextInputFocus()`
 *
 * `AlertDialog` 渲染在**独立窗口**里，`adjustResize` 缩的是那个窗口 ——
 * 首页 tab 底栏所在的主窗口不动。判据与 `TabRouteInputFocusTest` 的弹窗豁免一致。
 */
@Composable
internal fun AddTodoDialog(
    existingCourses: List<String>,
    onSave: (content: String, dueDate: String, courseName: String) -> Unit,
    onDismiss: () -> Unit,
) {
    val skin = LocalSkin.current
    val colors = LocalPocketXiaoliColors.current
    val state = remember(existingCourses) { AddTodoFormState(existingCourses) }

    AlertDialog(
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(skin.radiusCard),
        title = { Text("添加待办", fontWeight = FontWeight.ExtraBold, fontSize = 18.sp) },
        text = { AddTodoForm(state) },
        confirmButton = {
            Button(
                onClick = {
                    if (state.content.isBlank()) {
                        state.contentError = true
                        return@Button
                    }
                    onSave(state.content.trim(), state.dueDate.trim(), state.course.trim())
                },
                colors = ButtonDefaults.buttonColors(containerColor = colors.accentMain),
                shape = RoundedCornerShape(skin.radiusChip)
            ) { Text("保存") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
        }
    )
}

/**
 * 「添加待办」表单的**正文**（三个字段）。
 *
 * ⚠️ `internal` 而不是 private：`AlertDialog` 渲染在独立窗口里，`captureRoboImage`
 * 抓的是 Compose 根节点，**抓不到那个窗口**（同一个坑在 `SkinPreviewContent` 上踩过）。
 * 抽出来才能给它录基准图 —— 见 `HomeTodoScreenshotTest` 的弹窗那一张。
 * 同 `NextCourseCard` / `SoftCard`："为了可测而开可见性"不要改回 private。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun AddTodoForm(
    state: AddTodoFormState,
    modifier: Modifier = Modifier,
) {
    val skin = LocalSkin.current
    val colors = LocalPocketXiaoliColors.current

    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        OutlinedTextField(
            value = state.content,
            onValueChange = { state.content = it; state.contentError = false },
            label = { Text("待办内容 *") },
            placeholder = { Text("例如：交实验报告") },
            singleLine = true,
            isError = state.contentError,
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(skin.radiusChip)
        )
        if (state.contentError) {
            Text("请填写待办内容", color = colors.danger, fontSize = 11.sp)
        }

        if (state.courses.isNotEmpty() && state.usePicker) {
            ExposedDropdownMenuBox(
                expanded = state.courseExpanded,
                onExpandedChange = { state.courseExpanded = it }
            ) {
                OutlinedTextField(
                    value = state.course.ifEmpty { "不关联课程" },
                    onValueChange = {},
                    readOnly = true,
                    label = { Text("课程（可选）") },
                    trailingIcon = {
                        ExposedDropdownMenuDefaults.TrailingIcon(expanded = state.courseExpanded)
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable, enabled = true),
                    shape = RoundedCornerShape(skin.radiusChip)
                )
                ExposedDropdownMenu(
                    expanded = state.courseExpanded,
                    onDismissRequest = { state.courseExpanded = false }
                ) {
                    DropdownMenuItem(
                        text = { Text("不关联课程", fontWeight = FontWeight.Medium) },
                        onClick = { state.course = ""; state.courseExpanded = false }
                    )
                    HorizontalDivider()
                    state.courses.forEach { name ->
                        DropdownMenuItem(
                            text = { Text(name, fontWeight = FontWeight.Medium) },
                            onClick = { state.course = name; state.courseExpanded = false }
                        )
                    }
                    HorizontalDivider()
                    DropdownMenuItem(
                        text = { Text("+ 自定义输入", color = colors.accentMain, fontSize = 13.sp) },
                        onClick = {
                            state.usePicker = false
                            state.course = ""
                            state.courseExpanded = false
                        }
                    )
                }
            }
        } else {
            OutlinedTextField(
                value = state.course,
                onValueChange = { state.course = it },
                label = { Text("课程（可选）") },
                placeholder = { Text("例如：高等数学") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(skin.radiusChip)
            )
            if (state.courses.isNotEmpty()) {
                TextButton(
                    onClick = { state.usePicker = true; state.course = "" },
                    contentPadding = PaddingValues(0.dp)
                ) {
                    Text("从已有课程选择 →", color = colors.accentMain, fontSize = 12.sp)
                }
            }
        }

        OutlinedTextField(
            value = state.dueDate,
            onValueChange = { state.dueDate = it },
            label = { Text("截止日期（可选）") },
            placeholder = { Text("例如：下周一 / 6月15日") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(skin.radiusChip)
        )
    }
}
