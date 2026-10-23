package com.pocket.xiaoli.ui.share

import android.graphics.Bitmap
import android.graphics.Color
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * 二维码是「推荐给同学」里唯一**看不见就对**的部分：白底黑码画歪了照样"像二维码"，
 * 但扫不出来。这里只钉住三件最容易被改坏的事：
 * 尺寸（静区不能被裁）、静区是白的（深色背景下必须成码）、有黑模块（不是一张白图）。
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34])
class QrCodeTest {

    @Test
    fun `边长按请求生成`() {
        val bitmap = encodeQr(AppShare.GITEE_URL, 256)
        assertNotNull(bitmap)
        assertEquals(256, bitmap!!.width)
        assertEquals(256, bitmap.height)
    }

    @Test
    fun `四角是白色静区`() {
        val bitmap = encodeQr(AppShare.GITEE_URL, 256)!!
        // ZXing 自己留 4 模块静区；角上必然是白的。若哪天有人"裁掉白边"这里会红
        assertEquals(Color.WHITE, bitmap.getPixel(0, 0))
        assertEquals(Color.WHITE, bitmap.getPixel(255, 0))
        assertEquals(Color.WHITE, bitmap.getPixel(0, 255))
    }

    @Test
    fun `中心区域有黑色模块`() {
        val bitmap = encodeQr(AppShare.GITEE_URL, 256)!!
        val mid = 128
        val hasBlack = (mid - 30 until mid + 30).any { y ->
            (mid - 30 until mid + 30).any { x -> bitmap.getPixel(x, y) == Color.BLACK }
        }
        assertTrue("中间应有定位/数据模块", hasBlack)
    }

    @Test
    fun `同样内容生成同样像素`() {
        val a = encodeQr(AppShare.GITEE_URL, 128)!!
        val b = encodeQr(AppShare.GITEE_URL, 128)!!
        val pa = IntArray(128 * 128).also { a.getPixels(it, 0, 128, 0, 0, 128, 128) }
        val pb = IntArray(128 * 128).also { b.getPixels(it, 0, 128, 0, 0, 128, 128) }
        assertTrue(pa.contentEquals(pb))
    }

    @Test
    fun `空内容或非法边长返回 null`() {
        assertNull(encodeQr("  ", 128))
        assertNull(encodeQr(AppShare.GITEE_URL, 0))
        assertNull(encodeQr(AppShare.GITEE_URL, -8))
    }

    @Test
    fun `位图可回收`() {
        val bitmap: Bitmap = encodeQr(AppShare.GITEE_URL, 64)!!
        bitmap.recycle()
        assertTrue(bitmap.isRecycled)
    }
}
