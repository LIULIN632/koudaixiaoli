package com.pocket.xiaoli.ui.theme

import android.content.Context
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.view.ContextThemeWrapper
import androidx.test.core.app.ApplicationProvider
import com.pocket.xiaoli.R
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * 对话框 / 底部抽屉的窗口底色必须是**透明**。
 *
 * 2026-10 的 bug：Activity 主题的 `windowBackground` 是不透明的 `@color/status_bar`，
 * `Dialog` 窗口继承它之后，MIUIX 画的 30% 黑色遮罩底下是一块白板 ——
 * 视觉上就是"纯灰实色遮罩、原页面完全消失"，用户以为跳到了新页面。
 *
 * 这条测试守的是"窗口透明"这个前提；真正显示效果仍需真机看一眼。
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class DialogWindowThemeTest {

    @Test
    fun `对话框窗口背景透明`() {
        val base = ApplicationProvider.getApplicationContext<Context>()
        val themed = ContextThemeWrapper(base, R.style.Theme_PocketXiaoli_Dialog)

        val attrs = themed.obtainStyledAttributes(intArrayOf(android.R.attr.windowBackground))
        val drawable = attrs.getDrawable(0)
        attrs.recycle()

        // `@android:color/transparent` 会被解析成一个 alpha=0 的 ColorDrawable
        assertEquals(
            "Dialog 窗口必须透明，否则遮罩会盖在不透明底色上",
            Color.TRANSPARENT,
            (drawable as? ColorDrawable)?.color,
        )
    }
}
