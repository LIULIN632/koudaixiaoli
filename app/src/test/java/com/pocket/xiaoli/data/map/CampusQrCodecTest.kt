package com.pocket.xiaoli.data.map

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CampusQrCodecTest {

    @Test
    fun encodeDecodeRoundTrip() {
        val location = CampusQrLocation(
            campusId = "ytlg_main",
            buildingId = "b_teaching_a",
            floorId = "f_a_1",
            nodeId = "n_a_101",
        )

        val raw = CampusQrCodec.encode(location)
        val decoded = CampusQrCodec.decode(raw)

        assertEquals(location, decoded)
    }

    @Test
    fun rejectsWrongScheme() {
        assertNull(CampusQrCodec.decode("https://example.com/campus-location?v=1&campus=ytlg_main&building=b1"))
    }

    @Test
    fun rejectsMissingBuilding() {
        assertNull(CampusQrCodec.decode("pocketxiaoli://campus-location?v=1&campus=ytlg_main"))
    }
}