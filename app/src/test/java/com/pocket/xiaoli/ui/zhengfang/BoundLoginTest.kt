package com.pocket.xiaoli.ui.zhengfang

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BoundLoginTest {

    @Test
    fun triggersOnlyWhenBoundAndIdle() {
        assertTrue(BoundLogin.shouldAutoLogin(false, false, false, true, true))
    }

    @Test
    fun skipsWhenAlreadyTriggeredOrLoggedInOrLoading() {
        assertFalse(BoundLogin.shouldAutoLogin(true, false, false, true, true))
        assertFalse(BoundLogin.shouldAutoLogin(false, true, false, true, true))
        assertFalse(BoundLogin.shouldAutoLogin(false, false, true, true, true))
    }

    @Test
    fun skipsWithoutSchoolOrCredential() {
        assertFalse(BoundLogin.shouldAutoLogin(false, false, false, false, true))
        assertFalse(BoundLogin.shouldAutoLogin(false, false, false, true, false))
    }

    @Test
    fun masksStudentId() {
        assertEquals("******1234", BoundLogin.maskStudentId("2023121234"))
        assertEquals("****", BoundLogin.maskStudentId("abcd"))
        assertEquals("", BoundLogin.maskStudentId("  "))
    }
}
