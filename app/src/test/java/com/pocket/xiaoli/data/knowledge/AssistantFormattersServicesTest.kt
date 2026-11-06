package com.pocket.xiaoli.data.knowledge

import com.pocket.xiaoli.data.service.CampusServiceSpec
import com.pocket.xiaoli.data.service.CampusServiceType
import org.junit.Assert.assertTrue
import org.junit.Test

class AssistantFormattersServicesTest {

    @Test
    fun servicesMatchByNameAndProvider() {
        val specs = listOf(
            CampusServiceSpec(
                id = "campus_card",
                name = "校园卡",
                type = CampusServiceType.EXTERNAL_APP,
                provider = "易校园",
            ),
            CampusServiceSpec(
                id = "electricity",
                name = "宿舍电费",
                type = CampusServiceType.WEBVIEW,
                provider = "烟大后勤",
            ),
        )
        val answer = AssistantFormatters.services(specs, "校园卡在哪")
        assertTrue(answer.contains("校园卡"))
        assertTrue(answer.contains("易校园"))
    }

    /**
     * 空表只有一种来路了：**这所学校的校园服务还没收录**（`campusForCurrentSchool()` 为 null）。
     *
     * 改造前那句写"当前校区还没有配置"，而当时它拿的是 `selectedCampus` —— 对学校没收录的设备，
     * 那个校区是兜底出来的烟台理工本校区，于是这张表**从来不会为空**，这句也从未被真正读到。
     * 现在判据在学校这一层，这句话第一次变成用户真能听到的一句回答，所以措辞要说对主语：
     * 不是"你选的校区没配"，是"这所学校还没接进来"。
     */
    @Test
    fun servicesUnmatchedGivesHint() {
        val answer = AssistantFormatters.services(emptyList(), "校园卡")
        assertTrue(answer, answer.contains("这所学校"))
        assertTrue(answer, answer.contains("还没收录"))
        assertTrue(answer, !answer.contains("校区"))
    }
}
