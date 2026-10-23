package com.pocket.xiaoli.util

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri

/**
 * 读取图片 URI 并按最大边长降采样解码，防止相册大图（4000×3000 ≈ 48MB）直接 OOM。
 * 先 inJustDecodeBounds 读尺寸计算 2 的幂采样率，再重新打开流解码。
 */
fun decodeScaledWallpaper(context: Context, uri: Uri, maxDim: Int = 2048): Bitmap? {
    return try {
        // 1) 只读边界，不占内存
        val boundsOpts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        context.contentResolver.openInputStream(uri)?.use { input ->
            BitmapFactory.decodeStream(input, null, boundsOpts)
        }
        val longest = maxOf(boundsOpts.outWidth, boundsOpts.outHeight)
        // 2) 计算采样率（2 的幂），让最长边不超过 maxDim
        var sampleSize = 1
        while (longest / sampleSize > maxDim && sampleSize < 64) sampleSize *= 2
        // 3) 重新打开流解码（inJustDecodeBounds 会消费掉流）
        context.contentResolver.openInputStream(uri)?.use { input ->
            val opts = BitmapFactory.Options().apply { inSampleSize = sampleSize }
            BitmapFactory.decodeStream(input, null, opts)
        }
    } catch (_: Exception) {
        null
    }
}
