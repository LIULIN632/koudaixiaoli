package com.pocket.xiaoli.ui.glass

import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.Modifier
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.backdrops.LayerBackdrop
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop

/**
 * **折射的采样源**：整棵页面内容被录进这一层，玻璃组件从它里面采样。
 *
 * ## 为什么必须有它
 *
 * `backdrop`（Kyant0/AndroidLiquidGlass）的折射不是"读屏幕像素"，而是
 * **先把内容录进一个 `LayerBackdrop`，玻璃再去采样那一层**。
 * 所以没有采样源时，`lens` 折的是空气 —— 表现在屏幕上就是一块没有内容的色块。
 *
 * ## `null` 的语义是「不要玻璃」
 *
 * 与岱宗盒子同一条**零容错**规则：拿不到采样源就退回不透明样式，
 * **绝对不要为了"凑合一下"自己再建一个** —— 采样源互相嵌套成环会让
 * RenderThread 直接 SIGSEGV（原生崩溃，不是能 catch 的异常）。
 *
 * ## 谁录、谁读
 *
 * - **录**：`AppNavigation` 把 `NavHost` 挂上 [glassSource]。只录内容，不录底栏本身。
 * - **读**：玻璃外壳（底栏 / 将来的顶栏）读它。
 *
 * 底栏**不在**采样范围内 —— 它在 `NavHost` 之外单独绘制，这正是为了避免"录到自己"。
 */
val LocalAppBackdrop = compositionLocalOf<Backdrop?> { null }

/**
 * 建一个采样源。
 *
 * 调用点必须确保它被 [glassSource] 录进去，且录制范围**不包含**消费它的玻璃组件本身。
 */
@Composable
fun rememberAppBackdrop(): LayerBackdrop = rememberLayerBackdrop()

/**
 * 把这块内容录进采样源。
 *
 * `backdrop == null`（未启用玻璃 / 降级档）时**原样返回**，
 * 不产生任何额外图层 —— 低版本上这一步的代价应当恰好为零。
 */
fun Modifier.glassSource(backdrop: LayerBackdrop?): Modifier =
    if (backdrop == null) this else this.layerBackdrop(backdrop)
