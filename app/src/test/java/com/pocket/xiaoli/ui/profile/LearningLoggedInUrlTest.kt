package com.pocket.xiaoli.ui.profile

import com.pocket.xiaoli.data.learning.LearningPlatform
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 「登录了没」的判据只认域名。
 *
 * 这条判据决定要不要弹「已登录，点保存」那条提示 —— 判宽了会在登录页上就喊成功（用户以为登好了），
 * 判窄了则永远不提示（等于没做）。所以两种错都钉住。
 */
class LearningLoggedInUrlTest {

    @Test
    fun 进了学习通业务域算登录成功() {
        assertTrue(isLoggedInUrl(LearningPlatform.CHAOXING, "https://i.chaoxing.com/base"))
        assertTrue(isLoggedInUrl(LearningPlatform.CHAOXING, "https://mooc1.chaoxing.com/mycourse"))
        assertTrue(isLoggedInUrl(LearningPlatform.CHAOXING, "https://mooc2-ans.chaoxing.com/visit"))
    }

    @Test
    fun 还在登录页不算() {
        assertFalse(
            isLoggedInUrl(
                LearningPlatform.CHAOXING,
                "https://passport2.chaoxing.com/login?fid=&newversion=true",
            )
        )
        // 业务域里带 /login 的重定向（例如未登录被踢回）也不能算成功
        assertFalse(isLoggedInUrl(LearningPlatform.CHAOXING, "https://i.chaoxing.com/login?refer=x"))
    }

    @Test
    fun 别的域名不算() {
        assertFalse(isLoggedInUrl(LearningPlatform.CHAOXING, "https://www.baidu.com/"))
        assertFalse(isLoggedInUrl(LearningPlatform.CHAOXING, ""))
        // 智慧树的域名不该被学习通误判
        assertFalse(isLoggedInUrl(LearningPlatform.CHAOXING, "https://www.zhihuishu.com/"))
    }

    @Test
    fun 智慧树用自己的域名() {
        assertTrue(isLoggedInUrl(LearningPlatform.ZHIHUISHU, "https://onlineweb.zhihuishu.com/studentHome"))
        assertFalse(
            isLoggedInUrl(
                LearningPlatform.ZHIHUISHU,
                "https://passport.zhihuishu.com/login?service=x",
            )
        )
    }

    @Test
    fun 大小写不影响判据() {
        assertTrue(isLoggedInUrl(LearningPlatform.CHAOXING, "HTTPS://I.CHAOXING.COM/Base"))
    }
}
