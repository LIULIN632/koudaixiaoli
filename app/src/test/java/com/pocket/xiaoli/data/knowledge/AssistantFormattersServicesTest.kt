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

    @Test
    fun servicesUnmatchedGivesHint() {
        val answer = AssistantFormatters.services(emptyList(), "校园卡")
        assertTrue(answer.contains("还没有配置"))
    }
}
