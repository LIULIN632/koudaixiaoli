package com.pocket.xiaoli.data.legal

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * 同意记录的口径。
 *
 * 这里只有一条真正重要的规则：**判据是「同意过的版本 ≥ 当前版本」**。
 * 它同时承担两件事 —— 首启拦截，以及「条款改版后必须重新同意」。
 * 写错成 `==` 会让旧版本的用户永远被拦；写错成 `> 0` 则改了条款也不会再问，
 * 后者更糟：用户会在不知情的情况下"同意"一份没读过的协议。
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class AgreementStoreTest {

    private lateinit var context: Context

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        AgreementStore.reset(context)
    }

    @Test
    fun `从没同意过时不放行`() {
        assertEquals(0, AgreementStore.acceptedVersion(context))
        assertFalse(AgreementStore.isAccepted(context))
    }

    @Test
    fun `同意后记录当前版本与时间`() {
        AgreementStore.accept(context, now = 1_700_000_000_000L)
        assertTrue(AgreementStore.isAccepted(context))
        assertEquals(AgreementText.VERSION, AgreementStore.acceptedVersion(context))
        assertEquals(1_700_000_000_000L, AgreementStore.acceptedAt(context))
    }

    @Test
    fun `条款改版后旧同意不再放行`() {
        // 直接写一个"上一版"的版本号，模拟用户同意过 v(VERSION-1)
        context.getSharedPreferences("pocket_agreement", Context.MODE_PRIVATE)
            .edit().putInt("accepted_version", AgreementText.VERSION - 1).apply()

        assertFalse("旧版本的同意必须重新征得", AgreementStore.isAccepted(context))
    }

    @Test
    fun `同一条款重复同意只更新同意时间`() {
        AgreementStore.accept(context, now = 1L)
        AgreementStore.accept(context, now = 2L)
        assertEquals(AgreementText.VERSION, AgreementStore.acceptedVersion(context))
        assertEquals(2L, AgreementStore.acceptedAt(context))
    }

    @Test
    fun `reset 之后回到未同意`() {
        AgreementStore.accept(context)
        AgreementStore.reset(context)
        assertFalse(AgreementStore.isAccepted(context))
    }

    @Test
    fun `协议正文有足够的内容且每节都有段落`() {
        assertTrue("条款不能是空的", AgreementText.SECTIONS.size >= 8)
        assertTrue("导语不能为空", AgreementText.PREAMBLE.isNotBlank())
        AgreementText.SECTIONS.forEach { section ->
            assertTrue("「${section.title}」没有正文", section.paragraphs.isNotEmpty())
            section.paragraphs.forEach { assertTrue("空段落 @${section.title}", it.isNotBlank()) }
        }
        assertTrue("版本号必须从 1 开始", AgreementText.VERSION >= 1)
    }
}
