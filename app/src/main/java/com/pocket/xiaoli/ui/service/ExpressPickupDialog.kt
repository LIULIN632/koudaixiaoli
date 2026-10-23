package com.pocket.xiaoli.ui.service

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.LocalMall
import androidx.compose.material.icons.filled.LocalShipping
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pocket.xiaoli.data.service.ExpressPickup
import com.pocket.xiaoli.ui.glass.glassSurface
import com.pocket.xiaoli.ui.overlay.AppOverlayDialog
import com.pocket.xiaoli.ui.theme.BrandColors
import com.pocket.xiaoli.ui.theme.LocalDarkMode
import com.pocket.xiaoli.ui.theme.LocalPocketXiaoliColors
import com.pocket.xiaoli.ui.theme.LocalSkin

/**
 * 快递取件码弹窗。
 *
 * ## 为什么是弹窗而不是子页
 *
 * 2026-10-18 曾把它做成整页（`ExpressPickupPage`，为了让玻璃有可折的背景色斑），
 * 需求方否掉了：这个功能只有两行平台入口，为它占满一屏不值当 ——
 * 「做一个弹窗就行，不用专门做二级界面」。整页版本已删除，别再捡回来。
 *
 * 现在的形态照搬兄弟项目「聚在工大」的 `ExpressWindow`：
 * 居中圆角卡 + 左上标题 + 右上圆形关闭键 + 两条平台行（图标块 + 名称 + 说明）。
 * 那是 Material 3 的 `ListItem` 语法，不是自定义控件，读一眼就知道点哪。
 *
 * ## 为什么是页内浮层，而不是 `Dialog`
 *
 * 系统窗口（`Dialog`/`AlertDialog`）一旦弹出，背景就不归我们管：部分 ROM 会给
 * "带 dim 的弹窗窗口"自动加背景模糊，弹窗后面整页糊掉。见 `ui/overlay/AppOverlay.kt`。
 *
 * ## 为什么 `glassSurface` 这里传 `backdrop = null`
 *
 * 玻璃的折射需要**同一次绘制里有可采样的兄弟层**（见 `ui/glass/AppBackdrop.kt`）。
 * 本卡片现在是页内浮层，理论上有条件采样（它在 `glassSource` 录制层之外）。
 * 但仍然刻意不采：这次的诉求是「背景那块原界面要清晰可见」，
 * 一旦让卡片去采样，卡背后那一圈就会糊掉 —— 与诉求相反。
 * 所以走**磨砂档**（不透明底 + 白描边），与 29-30 上的降级形态一致，观感是稳定的。
 *
 * 卡片外的两团品牌色柔光负责"液态玻璃"的呼吸感：光在卡外，卡仍是实的。
 */
@Composable
fun ExpressPickupOverlay(onDismiss: () -> Unit) {
    AppOverlayDialog(onDismiss = onDismiss) { dismiss ->
        ExpressPickupCard(onDismiss = dismiss)
    }
}

/**
 * 弹窗的**卡片本体**（不含浮层宿主）。
 *
 * 把卡片单独抽出来的第二个理由：浮层宿主由 `AppNavigation` 持有，
 * 页面里只挂一层壳；截图测试直接画卡片就能截到完整形态。
 */
@Composable
internal fun ExpressPickupCard(onDismiss: () -> Unit) {
    val skin = LocalSkin.current
    val colors = LocalPocketXiaoliColors.current
    val dark = LocalDarkMode.current
    val shape = RoundedCornerShape(skin.radiusLarge)
    val swallow = remember { MutableInteractionSource() }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = if (dark) 0.52f else 0.36f))
            // 点空白处关闭。没有这层时，弹窗外那片遮罩是点不动的 —— 用户会以为卡死。
            .clickable(interactionSource = swallow, indication = null, onClick = onDismiss)
            .padding(horizontal = 20.dp, vertical = 32.dp),
        contentAlignment = Alignment.Center,
    ) {
        ExpressHalo(dark = dark)

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .glassSurface(backdrop = null, shape = shape, surfaceColor = colors.surfaceCard)
                // 卡片自己吞掉点击，否则点在卡片上也会走上面的关闭
                .clickable(interactionSource = swallow, indication = null) {}
                .padding(top = 14.dp, bottom = 12.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    "快递取件码",
                    fontSize = 17.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = colors.textPrimary,
                    modifier = Modifier.weight(1f),
                )
                CloseButton(onClick = onDismiss)
            }
            Spacer(Modifier.height(4.dp))
            Text(
                "取件码在购物 App 里，这里只做一步直达。",
                fontSize = 12.sp,
                lineHeight = 17.sp,
                color = colors.textTertiary,
                modifier = Modifier.padding(horizontal = 16.dp),
            )
            Spacer(Modifier.height(12.dp))

            val context = LocalContext.current
            ExpressRow(
                brand = BrandColors.Taobao,
                icon = Icons.Filled.LocalMall,
                name = "淘宝 / 菜鸟",
                hint = "直达身份码页 · 驿站取件用",
                onClick = { ExpressPickup.openTaobao(context) },
            )
            Spacer(Modifier.height(6.dp))
            ExpressRow(
                brand = BrandColors.Pinduoduo,
                icon = Icons.Filled.LocalShipping,
                name = "拼多多",
                hint = "直达身份码页 · 驿站取件用",
                onClick = { ExpressPickup.openPinduoduo(context) },
            )

            Spacer(Modifier.height(12.dp))
            Text(
                "若 App 版本不支持直达，会自动退回 App 首页；其他平台请在对应 App 内查看取件码。",
                fontSize = 11.sp,
                lineHeight = 16.sp,
                color = colors.textTertiary,
                modifier = Modifier.padding(horizontal = 16.dp),
            )
        }
    }
}

/** 右上角圆形关闭键。44dp 的点击区，30dp 的视觉块 —— 点得着但不抢标题。 */
@Composable
private fun CloseButton(onClick: () -> Unit) {
    val colors = LocalPocketXiaoliColors.current
    val dark = LocalDarkMode.current
    val interaction = remember { MutableInteractionSource() }
    // 浅色下 surfaceAlt 与卡面几乎同色，圆钮会"消失"；这里改用文字色的极低透明度打底，
    // 深色反过来用白 —— 两档都是"看得见但不抢戏"。
    val chip = if (dark) Color.White.copy(alpha = 0.12f) else colors.textPrimary.copy(alpha = 0.06f)
    Box(
        modifier = Modifier
            .size(44.dp)
            .clip(CircleShape)
            .clickable(interactionSource = interaction, indication = null, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier.size(30.dp).clip(CircleShape).background(chip),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                Icons.Filled.Close,
                contentDescription = "关闭",
                tint = colors.textSecondary,
                modifier = Modifier.size(16.dp),
            )
        }
    }
}

/**
 * 一条平台入口：品牌色实色图标块 + 名称 + 说明 + 右向箭头。
 *
 * 图标块用**实色**：品牌识别靠色块本身，半透明会让橙红两色在深色模式下一起发脏；
 * emoji（改造前是 🟠 / 🔴）在部分 ROM 上会被替换成系统表情，和这里的材质不是一路。
 */
@Composable
private fun ExpressRow(
    brand: Color,
    icon: ImageVector,
    name: String,
    hint: String,
    onClick: () -> Unit,
) {
    val skin = LocalSkin.current
    val colors = LocalPocketXiaoliColors.current
    val rowShape = RoundedCornerShape(skin.radiusCard)
    val interaction = remember { MutableInteractionSource() }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp)
            .clip(rowShape)
            .background(colors.surfaceAlt)
            .clickable(interactionSource = interaction, indication = null, onClick = onClick)
            .heightIn(min = 64.dp)
            .padding(horizontal = 10.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier.size(40.dp).clip(RoundedCornerShape(12.dp)).background(brand),
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, contentDescription = null, tint = Color.White, modifier = Modifier.size(20.dp))
        }
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(name, fontSize = 15.sp, fontWeight = FontWeight.Medium, color = colors.textPrimary)
            Spacer(Modifier.height(2.dp))
            Text(hint, fontSize = 11.5.sp, color = colors.textTertiary)
        }
        Icon(
            Icons.AutoMirrored.Filled.KeyboardArrowRight,
            contentDescription = null,
            tint = colors.textTertiary,
            modifier = Modifier.size(18.dp),
        )
    }
}

/**
 * 卡片背后两团品牌色柔光（淘宝橙 / 拼多多红）。
 *
 * 作用不是装饰：卡片是实色不透明的，四周留一圈有颜色的光晕，视线才会读到
 * "这是一块有厚度的玻璃"，而不是一张贴在灰底上的白纸。透明度刻意压得很低，
 * 深色模式再减半 —— 否则会在遮罩上糊成两坨脏斑。
 */
@Composable
private fun ExpressHalo(dark: Boolean) {
    val taobao = BrandColors.Taobao
    val pinduoduo = BrandColors.Pinduoduo
    Canvas(modifier = Modifier.fillMaxSize()) {
        val w = size.width
        val h = size.height
        fun glow(cx: Float, cy: Float, radius: Float, color: Color, alpha: Float) {
            val center = Offset(cx, cy)
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(color.copy(alpha = alpha), color.copy(alpha = 0f)),
                    center = center,
                    radius = radius,
                ),
                radius = radius,
                center = center,
            )
        }
        glow(w * 0.30f, h * 0.40f, w * 0.55f, taobao, if (dark) 0.10f else 0.18f)
        glow(w * 0.72f, h * 0.60f, w * 0.50f, pinduoduo, if (dark) 0.08f else 0.15f)
    }
}