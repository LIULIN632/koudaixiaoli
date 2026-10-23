package com.pocket.xiaoli.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pocket.xiaoli.R
import com.pocket.xiaoli.ui.theme.*

@Composable
fun EmptyStatePlaceholder(
    icon: ImageVector,
    message: String,
    modifier: Modifier = Modifier,
) {
    val skin = LocalSkin.current
    val colors = LocalPocketXiaoliColors.current
    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(
                icon,
                contentDescription = null,
                tint = colors.textTertiary.copy(alpha = 0.3f),
                modifier = Modifier.size(64.dp)
            )
            Spacer(Modifier.height(skin.spaceMd))
            Text(
                message,
                fontSize = 14.sp,
                color = colors.textTertiary.copy(alpha = 0.5f),
                textAlign = TextAlign.Center,
                lineHeight = 22.sp
            )
        }
    }
}

@Composable
fun TimetableEmptyState(
    onImport: () -> Unit,
    onAddCourse: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val skin = LocalSkin.current
    val colors = LocalPocketXiaoliColors.current

    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            // 主色光晕：让插画与纯白/纯灰页底之间有一层过渡，而不是直接贴上去。
            // 半径由**插画自己的绘制尺寸**推出来 —— 以前是另写一个 210dp 的圆，
            // 比 240dp 宽的插画还小一圈，轮廓边缘根本罩不住，"贴上去"的观感就来自这里。
            // 挂在 `drawBehind` 上还有一层好处：插画尺寸以后怎么改，光晕都跟着走。
            Image(
                painter = painterResource(id = R.drawable.empty_state_dog),
                contentDescription = null,
                modifier = Modifier
                    .width(240.dp)
                    .drawBehind {
                        val radius = maxOf(size.width, size.height) * 0.95f
                        drawCircle(
                            brush = Brush.radialGradient(
                                colors = listOf(
                                    colors.accentMain.copy(alpha = 0.12f),
                                    colors.accentMain.copy(alpha = 0f),
                                ),
                                radius = radius,
                            ),
                            radius = radius,
                        )
                    },
                contentScale = ContentScale.Fit
            )
            Spacer(Modifier.height(skin.spaceXxl))
            Text(
                "还没有课程哦～",
                fontSize = 18.sp,
                fontWeight = FontWeight.SemiBold,
                color = colors.textPrimary,
                textAlign = TextAlign.Center
            )
            Spacer(Modifier.height(skin.spaceSm))
            Text(
                "导入你的课程表，马上开始使用校园助手",
                fontSize = 14.sp,
                color = colors.textSecondary,
                textAlign = TextAlign.Center
            )
            Spacer(Modifier.height(28.dp))
            Button(
                onClick = onImport,
                modifier = Modifier
                    .width(160.dp)
                    .height(52.dp),
                shape = RoundedCornerShape(skin.radiusLarge),
                colors = ButtonDefaults.buttonColors(
                    containerColor = colors.accentMain,
                    contentColor = onAccentColor()
                )
            ) {
                Text(
                    "导入课表",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }
            if (onAddCourse != null) {
                Spacer(Modifier.height(10.dp))
                TextButton(onClick = onAddCourse) {
                    Text("手动添加课程", color = colors.textSecondary, fontSize = 14.sp)
                }
            }
        }
    }
}