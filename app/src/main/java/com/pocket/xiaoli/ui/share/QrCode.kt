package com.pocket.xiaoli.ui.share

import android.graphics.Bitmap
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import com.google.zxing.BarcodeFormat
import com.google.zxing.qrcode.QRCodeWriter

/**
 * 把一个字符串编成二维码位图（**离线，不联网**）。
 *
 * ## 为什么没有新增依赖
 *
 * `com.google.zxing:core:3.5.1` 早就在 `app/build.gradle.kts` 里（CameraX 扫码用），
 * 它本身是纯 JVM 库，`QRCodeWriter` 生成同样走它 —— 不需要另外引 `zxing-android-embedded`。
 *
 * ## 失败返回 null，不抛
 *
 * 二维码只是"方便对方扫码"的锦上添花。编不出来（内容过长、OOM）时调用方直接不画这一块，
 * 下面的两条链接照样能复制 —— 一个附加功能不该把整个气泡搞崩。
 *
 * @param content 要编码的文本（这里是一条发行页 URL）
 * @param sizePx 边长（像素）。ZXing 自己会留静区，调用方不要再额外加白边
 */
fun encodeQr(content: String, sizePx: Int): Bitmap? {
    if (content.isBlank() || sizePx <= 0) return null
    return runCatching {
        val matrix = QRCodeWriter().encode(content, BarcodeFormat.QR_CODE, sizePx, sizePx)
        val width = matrix.width
        val height = matrix.height
        val pixels = IntArray(width * height)
        for (y in 0 until height) {
            val row = y * width
            for (x in 0 until width) {
                pixels[row + x] = if (matrix.get(x, y)) {
                    android.graphics.Color.BLACK
                } else {
                    android.graphics.Color.WHITE
                }
            }
        }
        Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888).apply {
            setPixels(pixels, 0, width, 0, 0, width, height)
        }
    }.getOrNull()
}

/**
 * 记住一次的二维码位图。
 *
 * 生成一次约几毫秒，但气泡每帧重组都会调到这里，所以必须 `remember`
 * （键 = 内容 + 边长：换链接才会重算）。
 */
@Composable
fun rememberQrCode(content: String, sizePx: Int): Bitmap? =
    remember(content, sizePx) { encodeQr(content, sizePx) }
