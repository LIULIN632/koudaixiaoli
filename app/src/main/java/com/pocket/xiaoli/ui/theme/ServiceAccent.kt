package com.pocket.xiaoli.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb

/**
 * 服务入口的**语义强调色分类**。
 *
 * ## 为什么要分类，而不是每个服务一个颜色
 *
 * 改造前 43 个服务入口各自硬编码一对颜色（`Color(0xFF6A5AE0)` + `Color(0xFFEAE7FF)`…），
 * 累计 **86 个任意色值**。结果是服务页成了全 App 最花的一页 ——
 * 颜色一多，用户记不住任何颜色的含义，视觉上就是"功能大杂烩"。
 *
 * 改造后收敛到 **6 个语义类别**：颜色数量从 86 → 6，但**保留辨识度**，
 * 因为同一类内的服务本来就应该看起来是一家人（都是"生活服务"，没有理由一个蓝一个黄）。
 *
 * ## 颜色怎么来：从主题主色派生，而不是硬编码
 *
 * 每个类别 = 主题主色（`accentMain`）旋转一个**固定色相偏移**。
 * 好处是 8 套配色主题下分类色自动跟随 —— 换成马卡龙粉，整套分类色都变成粉调，
 * 而不是硬编码的蓝色在粉色主题里显得突兀。
 *
 * ⚠️ 那为什么不用"完全中性 + 单一强调色"？因为那会让 43 个入口**完全无法区分**，
 * 用户找"快递"和找"校园卡"要一个个读文字。6 个类别是"克制"与"可辨识"之间的折中。
 */
enum class ServiceAccent(
    /** 色相偏移（度）。0 表示与主题主色一致。 */
    val hueShift: Float,
) {
    /** 教务学业：课表、成绩、选课、评教（应与主色一致，最"正"的一类） */
    ACADEMIC(0f),

    /** 生活服务：快递、洗衣、洗澡、热水、报修 */
    LIFE(150f),

    /** 查询工具：空教室、图书馆、教室分布 */
    QUERY(210f),

    /** 校园卡与缴费：校园卡、电费、乘车码 */
    PAYMENT(40f),

    /** 学习资源：学习通、知到、学习笔记、考研 */
    STUDY(280f),

    /** 信息公告：通知、就业、迎新、地图 */
    INFO(320f),
}

/**
 * 取某个类别在当前主题下的强调色。
 *
 * 实现：把主题主色转到 HSV，按 [ServiceAccent.hueShift] 旋转色相，
 * 再把饱和度/明度收敛到一个区间。
 *
 * ## 2026-10：从「低饱和粉彩」改成「宝石色」
 *
 * 旧区间 S≤0.55 / V 0.55–0.80 会把所有分类色压成灰紫灰绿，叠加 14% 衬底后
 * 整页像蒙了一层灰。新区间提高饱和度、压低明度（浅色态 V≤0.72）：
 * 颜色更浓，且图标与浅衬底之间的对比反而更好。
 *
 * 深色主题下额外提亮，保证在暗底上的对比度。
 */
@Composable
fun serviceAccentColor(accent: ServiceAccent): Color {
    val base = LocalPocketXiaoliColors.current.accentMain
    val isDark = LocalDarkMode.current
    return remember(accent, base, isDark) {
        val hsv = FloatArray(3)
        android.graphics.Color.colorToHSV(base.toArgb(), hsv)
        // 旋转色相
        hsv[0] = (hsv[0] + accent.hueShift) % 360f
        // 饱和度下限防止自定义强调色本身偏灰；上限防止霓虹感
        hsv[1] = hsv[1].coerceIn(0.58f, 0.72f)
        hsv[2] = if (isDark) hsv[2].coerceIn(0.82f, 0.96f) else hsv[2].coerceIn(0.60f, 0.72f)
        Color(android.graphics.Color.HSVToColor(hsv))
    }
}

/** 该类别强调色的柔和衬底（用于卡片 / 图标底 / 常用服务格）。 */
@Composable
fun serviceAccentContainer(accent: ServiceAccent): Color =
    serviceAccentColor(accent).copy(alpha = if (LocalDarkMode.current) 0.26f else 0.20f)
