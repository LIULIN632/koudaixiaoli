package com.pocket.xiaoli.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pocket.xiaoli.ui.theme.LocalPocketXiaoliColors
import com.pocket.xiaoli.ui.theme.LocalSkin

/**
 * **全项目唯一的密码变换实例**。
 *
 * ⚠️ 不要在调用点写 `PasswordVisualTransformation()` —— 那会**每次重组都新建一个实例**，
 * 而 `VisualTransformation` 参与文本偏移映射（`offsetMapping`）的缓存键：
 * 实例一变，输入框就得重建映射，**中文输入法正在组合（composing）的那几个字会抖、会丢、
 * 候选条也可能不提交**（2026-10 用户报"绑定界面输入有问题"，见 `CLAUDE.md`）。
 * 一个 `val` 实例活到进程结束，问题就不存在。
 *
 * 守门人：`InlinePasswordTransformationTest`（源码扫描，禁止调用点再写内联的 `PasswordVisualTransformation()`）。
 */
val PocketPasswordTransformation: VisualTransformation = PasswordVisualTransformation()

/**
 * 「字段」—— 全项目统一的输入框外观：**白底 + `borderDivider` 描边 + 满圆角**。
 *
 * 这套观感先落在首页搜索入口与鲤鱼输入框上（见 `HomeSearchEntry` / `AssistantComposer`），
 * 现在提到共享层，好让其它页面直接复用，而不是各自再拼一遍 `OutlinedTextField`。
 * （`docs/优化清单.md` 的 D 项「104 个 `OutlinedTextField` 里自己传了颜色的那些还没统一」，
 * 就是靠这个组件逐个收编。）
 *
 * ## 三个刻意的选择
 *
 * - **标题放在框外**，框内只留 placeholder。满圆角（`RoundedCornerShape(50)`）下，
 *   `OutlinedTextField` 自带的浮动 label 会横切那条描边，看起来像"字压在线上"；
 * - **字段自身的描边置为透明**，底色与描边由外层 `Surface` 提供。两层各画一次会变成"框套框"；
 * - **[keyboardOptions] 必须由调用方显式给**：这一页填的是学号 / 密码 / 手机号这类
 *   **不能有中文联想、更不能走输入法组词**的东西，而默认的文本键盘在中文输入法下
 *   既会弹候选条、又会和 [visualTransformation] 打架。约定见下方参数说明。
 *
 * @param label 字段标题（框外，12sp 二级字色 —— 见下方正文里的取色理由）
 * @param placeholder 框内提示语
 * @param trailingIcon 右侧图标位（如密码的显示 / 隐藏切换）
 * @param error 非 null 时把描边换成 danger 并在框下写出**具体原因**。
 *   调用方负责决定"什么时候开始报错"（一般是点了保存之后，或该字段失焦之后）——
 *   组件本身不猜，也不做实时校验。
 * @param keyboardOptions **IME 类型**（默认 `KeyboardOptions.Default` = 普通文本键盘）。
 *   ⚠️ 约定：账号/学号 → `KeyboardType.Ascii`（不要中文候选）、
 *   密码 → `KeyboardType.Password` + [PocketPasswordTransformation]、
 *   手机号 → `KeyboardType.Phone`、纯数字 → `KeyboardType.Number`。
 *   项目里两个导入页（`ImportScreen` / `WebImportScreen`）一直是这么做的，
 *   本组件补上这个参数就是为了让别处不再漏。
 */
@Composable
fun PocketField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier,
    visualTransformation: VisualTransformation = VisualTransformation.None,
    trailingIcon: (@Composable () -> Unit)? = null,
    error: String? = null,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
) {
    val skin = LocalSkin.current
    val colors = LocalPocketXiaoliColors.current
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(skin.spaceXs),
    ) {
        // 12sp / 15sp：绑定信息页（本组件的主要使用方）整体上浮一档 ——
        // 用户在那一页要逐字核对学号与账号，字段标签 11sp 太小。
        //
        // ⚠️ 字色用 **textSecondary** 而不是 textTertiary（2026-10 改）：
        // 三级字色实测只有 2.15:1，用来当**字段名**等于让用户看不清"这一格填什么"；
        // 而这一页正是要逐字核对学号与密码的地方。占位语仍留在三级字色 ——
        // 它是"还没填时"的提示，本来就不该和已填内容抢注意力。
        Text(label, fontSize = 12.sp, color = colors.textSecondary)
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(50),
            color = colors.surfaceCard,
            // 有错时只把**描边**换成 danger：`CLAUDE.md` 明令不要用语义色低透明度当底色
            //（深色下那点红只偏移约两个色阶，"标了红却看不出来"），与 `ErrorCard` 同一套做法。
            border = BorderStroke(1.dp, if (error != null) colors.danger else colors.borderDivider),
        ) {
            OutlinedTextField(
                value = value,
                onValueChange = onValueChange,
                placeholder = { Text(placeholder, fontSize = 15.sp, color = colors.textTertiary) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(50),
                visualTransformation = visualTransformation,
                trailingIcon = trailingIcon,
                isError = error != null,
                keyboardOptions = keyboardOptions,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = Color.Transparent,
                    unfocusedBorderColor = Color.Transparent,
                    disabledBorderColor = Color.Transparent,
                    focusedContainerColor = Color.Transparent,
                    unfocusedContainerColor = Color.Transparent,
                    disabledContainerColor = Color.Transparent,
                    cursorColor = colors.accentMain,
                ),
            )
        }
        // 具体原因写在字段正下方（而不是弹 Toast）：用户要能一边看提示一边改这一个字段。
        if (error != null) {
            Text(error, fontSize = 11.sp, color = colors.danger, lineHeight = 15.sp)
        }
    }
}
