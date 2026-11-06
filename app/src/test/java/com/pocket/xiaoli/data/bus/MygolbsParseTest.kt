package com.pocket.xiaoli.data.bus

import com.pocket.xiaoli.core.bus.BusArrival
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 掌上公交响应的解析测试 —— 夹具是 **2026-11-04 实测抓到的真实报文节选**（本校站「烟台理工学院」）。
 *
 * ## 为什么用真实报文而不是自己编
 *
 * 这一层的风险不是「算错」，而是**读错字段名/字段类型**：上游的 `hasReal` 是字符串 `"1"`、
 * `stationID` 是字符串且**从 0 起**、CMD=106 的经度键叫 `lon` 而 CMD=110 叫 `station_lon`。
 * 这类错在编译期与"看起来合理"的假数据上都照不出来 —— 只有当天真抓回来的报文能。
 *
 * 只有一条[合成]的样本（未开通实时）：烟台实测的九条线路全都开了实时，
 * 而 `hasReal=0` 恰恰是必须正确降级成「未开通实时」的那条路径。
 */
class MygolbsParseTest {

    /** CMD=105 本校站真实响应节选（保留我们读的字段，其余按原样省略）。 */
    private val boardResponse = """
        {
          "status": "1",
          "msg": "success",
          "data": [
            {
              "lineName": "17路", "upperOrDown": "2",
              "from": "山东商务职业学院南门", "to": "火车站",
              "beginTime": "05:30", "endTime": "21:30", "comments": "票价:1元",
              "stationName": "烟台理工学院", "stationID": "16",
              "neartext": "已到站 [到达 烟台理工学院]", "nexttext": "", "nearnum": 0,
              "distance": 0.0,
              "routeBean": {"id": 53585, "cityName": "烟台市", "hasReal": "1", "runState": 0,
                            "company": "烟台市公交集团高新区有限公司"}
            },
            {
              "lineName": "68路", "upperOrDown": "2",
              "from": "中医药高专场站", "to": "烟台人才市场",
              "beginTime": "06:00", "endTime": "19:00", "comments": "票价:1元",
              "stationName": "烟台理工学院", "stationID": "38",
              "neartext": "2站 [开往 蓝色智谷]", "nexttext": "", "nearnum": 2,
              "distance": 0.0,
              "routeBean": {"id": 308, "cityName": "烟台市", "hasReal": "1", "runState": 0,
                            "company": "烟台市公交集团牟平有限公司"}
            },
            {
              "lineName": "67路文登整骨区间", "upperOrDown": "2",
              "from": "牟平中医医院", "to": "文登整骨烟台医院场站",
              "beginTime": "06:00", "endTime": "14:30", "comments": "票价:1元",
              "stationName": "烟台理工学院", "stationID": "45",
              "neartext": "等待发车", "nexttext": "", "nearnum": 100,
              "distance": 0.0,
              "routeBean": {"id": 53990, "cityName": "烟台市", "hasReal": "1", "runState": 0}
            },
            {
              "lineName": "km116路", "upperOrDown": "1",
              "from": "烟台汽车总站", "to": "开发区 TIMER城",
              "beginTime": "06:00", "endTime": "19:30", "comments": "票价:2-5元",
              "stationName": "烟台理工学院", "stationID": "7",
              "neartext": "4站 [开往 烟台理工学院]", "nexttext": "8站", "nearnum": 4,
              "distance": 0.0,
              "routeBean": {"id": 54321, "cityName": "烟台市", "hasReal": "0", "runState": 0}
            }
          ]
        }
    """.trimIndent()

    private val nearbyResponse = """
        {
          "status": "1", "msg": "success",
          "data": [
            {"name": "凤凰大街华庭路西", "lat": "37.4532748539", "lon": "121.4693828155", "dis": 205, "sameNum": 0},
            {"name": "华庭路", "lat": "37.4506011213", "lon": "121.4716101611", "dis": 358, "sameNum": 0},
            {"name": "烟台理工学院", "lat": "37.4576738327", "lon": "121.4782500637", "dis": 725, "sameNum": 0}
          ]
        }
    """.trimIndent()

    @Test
    fun `真实响应的四条线路全部解析出来`() {
        val lines = BusRepository.parseLines(boardResponse)
        assertEquals(4, lines.size)
        val route17 = lines.first { it.lineName == "17路" }
        assertEquals("山东商务职业学院南门", route17.from)
        assertEquals("火车站", route17.to)
        // 首末班来自本方向的 beginTime/endTime，不是 routeBean 里的四段值
        assertEquals("05:30-21:30", route17.firstLastText)
        assertEquals("1元", route17.fare)
        assertEquals("开往 火车站", route17.directionText)
        // 17 路方向：下行（upperOrDown=2），界面用它区分往返两趟
        assertEquals(2, route17.direction)
    }

    @Test
    fun `到站文案按真实形态归类`() {
        val byName = BusRepository.parseLines(boardResponse).associateBy { it.lineName }
        assertEquals(BusArrival.Arrived, byName.getValue("17路").arrival)
        assertEquals(BusArrival.StopsAway(2), byName.getValue("68路").arrival)
        assertEquals(BusArrival.WaitingDeparture, byName.getValue("67路文登整骨区间").arrival)
        // km116路 这条是合成的：烟台实测九条全开了实时，但 hasReal=0 必须正确降级
        assertEquals(BusArrival.NoRealtime, byName.getValue("km116路").arrival)
    }

    @Test
    fun `hasReal 是字符串 1 而不是布尔`() {
        val lines = BusRepository.parseLines(boardResponse)
        // 真实报文里它是 "1"；写成 optBoolean 会把全部实时线路误判成「未开通实时」
        assertTrue(lines.first { it.lineName == "17路" }.hasRealtime)
        assertFalse(lines.first { it.lineName == "km116路" }.hasRealtime)
    }

    @Test
    fun `第二班只在上游真给了的时候才有`() {
        val lines = BusRepository.parseLines(boardResponse).associateBy { it.lineName }
        assertEquals("", lines.getValue("68路").nextBusText)
        assertEquals("8站", lines.getValue("km116路").nextBusText)
    }

    @Test
    fun `看板排序把能等的放前面`() {
        val sorted = BusRepository.sortLines(BusRepository.parseLines(boardResponse))
        assertEquals(listOf("17路", "68路", "67路文登整骨区间", "km116路"), sorted.map { it.lineName })
    }

    @Test
    fun `停运的线路压到最后`() {
        val suspended = BusLine(lineName = "566路", nearText = "1站", nearNum = 1, hasRealtime = true, runState = 1)
        val running = BusLine(lineName = "17路", nearText = "9站", nearNum = 9, hasRealtime = true, runState = 0)
        assertEquals(listOf("17路", "566路"), BusRepository.sortLines(listOf(suspended, running)).map { it.lineName })
        assertEquals(BusArrival.Suspended, suspended.arrival)
    }

    @Test
    fun `线路号按数值而不是字典序排`() {
        val lines = listOf("105路", "17路", "2路", "快51路").map {
            BusLine(lineName = it, nearText = "", nearNum = 0, hasRealtime = false, runState = 0)
        }
        assertEquals(
            listOf("2路", "17路", "105路", "快51路"),
            BusRepository.sortLines(lines).map { it.lineName },
        )
    }

    @Test
    fun `附近站按真实经纬度解析并按距离排`() {
        val stations = BusRepository.parseStations(nearbyResponse)
        assertEquals(3, stations.size)
        assertEquals("凤凰大街华庭路西", stations.first().name)
        assertEquals(205, stations.first().distanceMeters)
        // ⚠️ 键名是 lon 不是 lng
        assertEquals(121.4693828155, stations.first().lng, 1e-9)
        assertEquals(37.4532748539, stations.first().lat, 1e-9)
        assertEquals(listOf(205, 358, 725), stations.map { it.distanceMeters })
    }

    @Test
    fun `坏报文一律当解析失败而不是抛`() {
        assertEquals(emptyList<BusLine>(), BusRepository.parseLines("""{"status":"0","msg":"error"}"""))
        assertEquals(emptyList<BusLine>(), BusRepository.parseLines("<html>被运营商拦了</html>"))
        assertEquals(emptyList<BusStation>(), BusRepository.parseStations(""))
        // status=1 但 data 缺失：给空列表，界面据此显示「这个站没查到线路」
        assertEquals(emptyList<BusLine>(), BusRepository.parseLines("""{"status":"1"}"""))
    }

    @Test
    fun `票价取第一行不被发车时刻表撑爆`() {
        // 真实报文：105路的 comments 是 "票价:1元\n发车时间：石家疃6:00、6:40……"（两百多字）
        val longComments = "票价:1元\n发车时间：石家疃6:00、6:40、7:35、8:25、9:20、10:20、11:20、12:15"
        assertEquals("1元", BusRepository.fareOf(longComments))
        assertEquals("1元", BusRepository.fareOf("票价:1元"))
        assertEquals("", BusRepository.fareOf(""))
        assertEquals("2-5元", BusRepository.fareOf("票价:2-5元"))
        // 没有「票价:」前缀时只拿第一行，绝不把整段发车时间塞进卡片
        assertEquals("全程5元", BusRepository.fareOf("全程5元\n第二段不该出现"))
    }

    @Test
    fun `同一站查到空线路时不许冲掉已有看板`() {
        val current = BusRepository.State(
            stationName = "烟台理工学院",
            lines = listOf(BusLine(lineName = "17路", nearText = "3站", nearNum = 3, hasRealtime = true)),
            capturedAtMillis = 1_000L,
        )
        val emptySameStation = BusBoard(stationName = "烟台理工学院", lines = emptyList())
        assertNull(BusRepository.acceptReason(emptySameStation.copy(lines = listOf(BusLine(lineName = "2路"))), current))
        assertEquals("这个站暂时没查到线路，先显示上次刷新到的信息", BusRepository.acceptReason(emptySameStation, current))
        // 换站查空是另一回事：那时无脑把空板子换上去
        assertNull(BusRepository.acceptReason(emptySameStation.copy(stationName = "华庭路"), current))
    }

    @Test
    fun `缓存往返不丢到站判据`() {
        val board = BusBoard(
            stationName = "烟台理工学院",
            lines = BusRepository.parseLines(boardResponse),
            stations = BusRepository.parseStations(nearbyResponse),
            capturedAtMillis = 1_730_000_000_000L,
        )
        val restored = BusBoard.fromJson(board.toJson())
        assertEquals(board.stationName, restored.stationName)
        assertEquals(board.lines.map { it.arrival }, restored.lines.map { it.arrival })
        assertEquals(board.lines.map { it.fare }, restored.lines.map { it.fare })
        assertEquals(board.stations, restored.stations)
        assertEquals(board.capturedAtMillis, restored.capturedAtMillis)
    }

    @Test
    fun `请求里不许出现用户坐标`() {
        val board = BusRepository.stationBoardForm("烟台理工学院")
        assertEquals("105", board.getValue("CMD"))
        // 不带「市」上游查不到这座城市
        assertEquals("烟台市", board.getValue("CITYNAME"))
        assertEquals("烟台理工学院", board.getValue("STATIONNAME"))
        // 这两个键是上游收「用户当前坐标」的位置。本页不申请定位权限，必须留空 ——
        // 填进去就等于往第三方接口传用户位置，撞红线（memory/01-constraints.md §2.2）
        assertEquals("", board.getValue("MYLAT"))
        assertEquals("", board.getValue("MYLNG"))

        val nearby = BusRepository.nearbyStationForm()
        assertEquals("106", nearby.getValue("CMD"))
        // 两处坐标都填学校锚点，不是设备位置
        assertEquals(BusRepository.CAMPUS_LAT.toString(), nearby.getValue("MYLAT"))
        assertEquals(BusRepository.CAMPUS_LNG.toString(), nearby.getValue("LNG"))
    }

    @Test
    fun `更新时间文案只在真有时间时出现`() {
        assertEquals("", BusRepository.capturedLabel(0L))
        val label = BusRepository.capturedLabel(1_730_000_000_000L)
        assertTrue(label, label.startsWith("更新于 "))
        // 只格式化传入的毫秒，不读当前时钟 —— 基准图因此不会天天漂红
        assertEquals(label, BusRepository.capturedLabel(1_730_000_000_000L))
    }
}
