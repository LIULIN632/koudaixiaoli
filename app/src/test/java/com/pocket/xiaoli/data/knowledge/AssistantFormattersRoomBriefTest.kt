package com.pocket.xiaoli.data.knowledge

import com.pocket.xiaoli.data.map.RoomCell
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AssistantFormattersRoomBriefTest {

    @Test
    fun roomBriefMatchesCourseRoom() {
        val cell = RoomCell(
            id = "A101",
            campusId = "ytlg_main",
            building = "3 号教学楼",
            floor = "2 层",
            room = "A101",
        )
        assertEquals("3 号教学楼 2 层", AssistantFormatters.roomBrief(listOf(cell), "A101"))
    }

    @Test
    fun roomBriefReturnsNullWhenNoRoom() {
        assertNull(AssistantFormatters.roomBrief(emptyList(), "A101"))
        assertNull(AssistantFormatters.roomBrief(emptyList(), ""))
    }
}
