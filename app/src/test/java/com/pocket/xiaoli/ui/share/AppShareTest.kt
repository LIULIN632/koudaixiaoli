package com.pocket.xiaoli.ui.share

import android.content.Intent
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * 「推荐给同学」里那些**纯逻辑**部分的判据。
 *
 * 为什么这些值得单独测而不是只靠截图：
 * 分享 Intent 漏一个 flag、文件名里混进一个 `/`，界面上一模一样（截图全绿），
 * 但同学那边收到的是一个打不开的文件 —— 只有断言能抓住。
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class AppShareTest {

    @Test
    fun `正常版本号原样保留`() {
        assertEquals("0.1.67", AppShare.safeVersion("0.1.67"))
    }

    @Test
    fun `路径分隔符与空格被替换成下划线`() {
        // 版本号来自 build.gradle.kts，理论上不会带这些字符；
        // 但它要拼进文件名，一旦出现就必须挡住（`/` 会让 File 直接抛）
        assertEquals("0.1.67-beta_2", AppShare.safeVersion("0.1.67-beta/2"))
        assertEquals("0.1.67_x", AppShare.safeVersion("0.1.67 x"))
    }

    @Test
    fun `全是非法字符时退回 unknown 而不是空串`() {
        assertEquals("unknown", AppShare.safeVersion(""))
        assertEquals("unknown", AppShare.safeVersion("   "))
    }

    @Test
    fun `文件名与 renameApk 同形`() {
        assertEquals("口袋小鲤_v0.1.67.apk", AppShare.apkFileName("0.1.67"))
    }

    @Test
    fun `分享 Intent 带类型 流和读权限`() {
        val uri = Uri.parse("content://com.pocket.xiaoli.fileprovider/share/x.apk")
        val intent = AppShare.apkShareIntent(uri, "0.1.67")

        assertEquals(Intent.ACTION_SEND, intent.action)
        // type 决定系统面板里出现哪些 App：写成 */* 会把相册也列进来
        assertEquals("application/vnd.android.package-archive", intent.type)
        assertEquals(uri, intent.getParcelableExtra(Intent.EXTRA_STREAM))
        // 没有这个 flag，接收方拿到 URI 也读不到内容（SecurityException）
        assertTrue(intent.flags and Intent.FLAG_GRANT_READ_URI_PERMISSION != 0)
    }

    @Test
    fun `分享文案同时带版本号与下载链接`() {
        val text = AppShare.recommendText("0.1.67")
        assertTrue(text.contains("v0.1.67"))
        assertTrue(text.contains(AppShare.GITEE_URL))
    }

    @Test
    fun `拷贝到缓存目录并按版本命名`() {
        // Robolectric 给的 applicationInfo.sourceDir 是测试用的那个包，够验证"拷出来"这条路
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val staged = AppShare.stageApkFile(context, "0.1.67")

        assertNotNull(staged)
        assertEquals("口袋小鲤_v0.1.67.apk", staged!!.name)
        assertTrue("必须落在 cache/share 下（FileProvider 只暴露了 cache 根）", staged.parentFile!!.name == "share")
        assertTrue(staged.length() > 0L)
    }

    @Test
    fun `换版本再拷时旧件被清掉`() {
        // 缓存只该留"本次要发的那一个"：不清的话每发一个版本就多 24MB
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val first = AppShare.stageApkFile(context, "0.1.67")!!
        val second = AppShare.stageApkFile(context, "0.1.68")

        assertNotNull(second)
        assertEquals("口袋小鲤_v0.1.68.apk", second!!.name)
        assertFalse("旧版本的包应被删除", first.exists())
    }
}
