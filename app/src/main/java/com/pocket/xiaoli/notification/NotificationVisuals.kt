package com.pocket.xiaoli.notification

import android.content.Context
import android.graphics.Bitmap
import androidx.core.content.ContextCompat
import androidx.core.graphics.drawable.toBitmap
import com.pocket.xiaoli.R

/**
 * 通知栏图标的唯一取处。
 *
 * 以前每条通知各写各的：`android.R.drawable.ic_dialog_info`（系统那个"i"）、
 * `stat_sys_download`、`stat_notify_error`……于是通知栏里看不到本 App 的样子，
 * 换成锦鲤图标之后更是"软件是新的、通知还是旧的"。
 *
 * 两条规矩：
 * 1. **小图标必须单色**（[R.drawable.ic_stat_fish]）：Android 只取 alpha 通道，
 *    彩色图标会被系统涂成一团白块；
 * 2. **大图标用应用图标**（[R.mipmap.ic_launcher]）：折叠态不显示，展开态会出现在
 *    右侧，用户一眼知道是哪款 App 的通知。
 */
internal object NotificationVisuals {

    /** 兜底品牌图标：不知道该用哪个时用这条鱼（一般不该用到）。 */
    const val SMALL_ICON = R.drawable.ic_stat_fish

    /** 公告 / 消息（铃铛）。 */
    const val ANNOUNCE = R.drawable.ic_stat_announce

    /** 有新版本可装 / 正在下载。 */
    const val UPDATE = R.drawable.ic_stat_update

    /** 下载完成、安装包就绪。 */
    const val DONE = R.drawable.ic_stat_download_done

    /** 下载失败、权限缺失等需要用户处理。 */
    const val ERROR = R.drawable.ic_stat_error

    /** 上课提醒（时钟）。 */
    const val SCHEDULE = R.drawable.ic_stat_schedule

    /** 考试提醒（卷面）。 */
    const val EXAM = R.drawable.ic_stat_exam

    /** 番茄钟进行中 / 结束。 */
    const val TIMER = R.drawable.ic_stat_timer
    /** 通知大图标建议边长（dp）；系统会按需缩放，这里只保证不是 1:1 的原始小图。 */
    private const val LARGE_ICON_DP = 64

    /**
     * 通知大图标：应用图标转成位图。
     *
     * 走 [ContextCompat.getDrawable] 而不是 `BitmapFactory`，因为 `mipmap/ic_launcher`
     * 在 API 26+ 是"背景层 + 前景层"的自适应图标（XML），直接 decode 会拿到 null；
     * `AdaptiveIconDrawable.draw(canvas)` 才会把两层合成出来。
     *
     * 失败返回 null —— `setLargeIcon(null)` 只是不显示大图，不会让通知发不出去。
     */
    fun largeIcon(context: Context): Bitmap? = runCatching {
        val size = (LARGE_ICON_DP * context.resources.displayMetrics.density).toInt()
        ContextCompat.getDrawable(context, R.mipmap.ic_launcher)?.toBitmap(size, size)
    }.getOrNull()
}
