package com.pocket.xiaoli.ui.glass

import android.os.Build

/**
 * 液态玻璃在 Android 上的**三档能力**。
 *
 * 这不是本项目的取舍，是系统 API 的硬边界 —— 它决定了"同一份代码在不同机器上到底能看到什么"，
 * 也决定了设置页必须如实说明到什么程度。把它单独拎出来当一个类型，
 * 是为了让**判定只有一处**：底栏、顶栏、卡片投影都读它，而不是各自写一遍 `SDK_INT >= 33`。
 *
 * | 档位 | 系统 | 底层依据 | 用户实际看到 |
 * |---|---|---|---|
 * | [REFRACTION] | Android 13+（API 33） | `RuntimeShader` 可用 | 完整液态玻璃：折射 + 模糊 + 高光 |
 * | [BLUR] | Android 12（API 31–32） | `RenderEffect` 可用 | 模糊 + 半透明，**没有折射** |
 * | [MATTE] | Android 10–11（API 29–30） | 两者都不可用 | 磨砂半透明 + 描边 |
 *
 * 阈值与 `miuix-squircle` 同源：那个库内部也是 `SDK_INT >= 33` 才走 `RuntimeShader`，
 * 低于它自动退回普通圆角。两处阈值要是分叉了，就会出现"圆角是超椭圆但玻璃不会折射"的怪状态。
 *
 * ⚠️ 本项目 `minSdk = 29`，所以**三档都会真实发生**。
 * 任何"液态玻璃效果"的实现都必须先问自己：下面两档降级成什么？
 */
enum class GlassCapability {

    /** API 33+：能做真折射，这是液态玻璃的"灵魂"档。 */
    REFRACTION,

    /** API 31–32：能做真模糊，但折射要做的话只能伪造（本档一律不伪造）。 */
    BLUR,

    /** API 29–30：既不能模糊也不能折射，只能靠半透明 + 描边表意。 */
    MATTE;

    /** 本机是否至少能做模糊。低于它时"玻璃"只能是半透明。 */
    val canBlur: Boolean get() = this != MATTE

    /** 本机是否能做真折射。低版本拿不到，不要在 UI 上承诺。 */
    val canRefract: Boolean get() = this == REFRACTION

    companion object {

        /**
         * 按系统版本判定能力。
         *
         * 参数带默认值是为了**可测**：JVM 单测里 `Build.VERSION.SDK_INT` 恒为 0，
         * 不传参就没法验证三档分支，只能验证"和 0 比较的结果"（等于没测）。
         */
        fun of(sdkInt: Int = Build.VERSION.SDK_INT): GlassCapability = when {
            sdkInt >= Build.VERSION_CODES.TIRAMISU -> REFRACTION
            sdkInt >= Build.VERSION_CODES.S -> BLUR
            else -> MATTE
        }

        /** 当前设备的能力档。 */
        val current: GlassCapability get() = of()
    }
}
