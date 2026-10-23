package com.pocket.xiaoli.data.electricity

import com.pocket.xiaoli.data.model.DormInfo
import com.google.gson.Gson
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DormElectricityRepositoryTest {

    /**
     * 南校房号存的是 `s602` / `n602`（侧翼 + 楼层 + 房号，见 `DormRoomNumber`），
     * 而缴费系统的地址是后勤手写的中文方位（`北校10楼南602`）。
     *
     * ⚠️ 这两条是**必须成对存在**的：
     * - 第一条守"带方位的中文地址认得出"（`s602` ↔ `南602`）；
     * - 第二条守"地址**没写方位**时也认得出"（退回纯数字形态）——
     *   后勤的地址写法确实两种都有，少了这条，那类用户会看到"没有匹配到该宿舍的缴费账户"。
     *
     * 代价（刻意接受）：南侧 602 与北侧 602 在"地址不带方位"时会互相命中。
     * 这是无法避免的 —— 地址里根本没有这个信息，而缴费账户列表本来就只列用户自己名下的房。
     */
    @Test
    fun parseMatchesSouthCampusRoomByChineseSideInAddress() {
        val html = southCampusElectricityHtml(address = "北校10楼南602")

        val result = DormElectricityRepository.parse(html, DormInfo("10号宿舍楼", "s602"))
        assertEquals("21.86", result?.balance)
    }

    @Test
    fun parseMatchesSouthCampusRoomWhenAddressOmitsSide() {
        val html = southCampusElectricityHtml(address = "南校区10栋602")

        val result = DormElectricityRepository.parse(html, DormInfo("10号宿舍楼", "n602"))
        assertEquals("21.86", result?.balance)
    }

    @Test
    fun parseDoesNotMatchSouthCampusRoomInsideLargerNumber() {
        val html = southCampusElectricityHtml(address = "北校10楼南1602")

        // 1602 ≠ 602：纯数字形态也要走数字边界，不能被"包含"骗过去
        assertNull(DormElectricityRepository.parse(html, DormInfo("10号宿舍楼", "n602")))
    }

    /** 南校缴费页的最小可用片段（字段名与真实页面一致）。 */
    private fun southCampusElectricityHtml(address: String): String = """
        <div class="weui-panel">
          <div class="weui-panel__bd">
            <div class="weui-cells">
              <div class="weui-cell">
                <div class="weui-cell__bd">用户编号</div>
                <div class="weui-cell__ft">BS10S0602</div>
              </div>
              <div class="weui-cell">
                <div class="weui-cell__bd">用户地址</div>
                <div class="weui-cell__ft">$address</div>
              </div>
              <div class="weui-cell">
                <div class="weui-cell__bd">实时余额</div>
                <div class="weui-cell__ft">21.86</div>
              </div>
            </div>
            <a href="payItemIndex?accountId=4155&itemType=METER&itemId=5644">充值缴费</a>
          </div>
        </div>
    """.trimIndent()

    @Test
    fun parseMatchesBuildingAndRoom() {
        val html = """
            <div class="weui-panel">
              <div class="weui-panel__bd">
                <div class="weui-cells">
                  <div class="weui-cell">
                    <div class="weui-cell__bd">用户编号</div>
                    <div class="weui-cell__ft">BS10S0602</div>
                  </div>
                  <div class="weui-cell">
                    <div class="weui-cell__bd">用户地址</div>
                    <div class="weui-cell__ft">北校10楼南602</div>
                  </div>
                  <div class="weui-cell">
                    <div class="weui-cell__bd">实时余额</div>
                    <div class="weui-cell__ft">21.86</div>
                  </div>
                </div>
                <a href="payItemIndex?accountId=4155&itemType=METER&itemId=5644">充值缴费</a>
              </div>
            </div>
        """.trimIndent()

        val result = DormElectricityRepository.parse(html, DormInfo("10号宿舍楼", "602"))
        assertEquals("21.86", result?.balance)
        assertEquals("BS10S0602", result?.accountNo)
        assertEquals("4155", result?.accountId)
        assertEquals("5644", result?.itemId)
    }

    @Test
    fun parseReturnsNullWhenRoomDoesNotMatch() {
        val html = """
            <div class="weui-panel">
              <div class="weui-panel__bd">
                <div class="weui-cells">
                  <div class="weui-cell">
                    <div class="weui-cell__bd">用户编号</div>
                    <div class="weui-cell__ft">BS10S0602</div>
                  </div>
                  <div class="weui-cell">
                    <div class="weui-cell__bd">用户地址</div>
                    <div class="weui-cell__ft">北校10楼南602</div>
                  </div>
                  <div class="weui-cell">
                    <div class="weui-cell__bd">实时余额</div>
                    <div class="weui-cell__ft">21.86</div>
                  </div>
                </div>
              </div>
            </div>
        """.trimIndent()

        assertNull(DormElectricityRepository.parse(html, DormInfo("10号宿舍楼", "603")))
    }

    @Test
    fun parseSupportsRoomStartingWithLetter() {
        val html = """
            <div class="weui-panel">
              <div class="weui-panel__bd">
                <div class="weui-cells">
                  <div class="weui-cell">
                    <div class="weui-cell__bd">用户编号</div>
                    <div class="weui-cell__ft">BS11A0114</div>
                  </div>
                  <div class="weui-cell">
                    <div class="weui-cell__bd">用户地址</div>
                    <div class="weui-cell__ft">北校11楼A114</div>
                  </div>
                  <div class="weui-cell">
                    <div class="weui-cell__bd">实时余额</div>
                    <div class="weui-cell__ft">-30.18</div>
                  </div>
                </div>
              </div>
            </div>
        """.trimIndent()

        val result = DormElectricityRepository.parse(html, DormInfo("11号楼", "A114"))
        assertEquals("-30.18", result?.balance)
    }

    @Test
    fun parseAcceptsAlternateBalanceLabels() {
        val html = """
            <div class="weui-panel">
              <div class="weui-cell">
                <div class="weui-cell__bd">户号</div>
                <div class="weui-cell__ft">YT-10-602</div>
              </div>
              <div class="weui-cell">
                <div class="weui-cell__bd">房间地址</div>
                <div class="weui-cell__ft">南校区10栋602</div>
              </div>
              <div class="weui-cell">
                <div class="weui-cell__bd">电费余额</div>
                <div class="weui-cell__ft">8.50</div>
              </div>
              <a href="/pms/wechat/ytdx/payItemIndex?accountId=1&amp;itemId=2">缴费</a>
            </div>
        """.trimIndent()

        val result = DormElectricityRepository.parse(html, DormInfo("10号宿舍楼", "602"))
        assertEquals("8.50", result?.balance)
        assertEquals("YT-10-602", result?.accountNo)
        assertEquals("1", result?.accountId)
        assertEquals("2", result?.itemId)
    }

    @Test
    fun parseDoesNotMatchRoomInsideLargerNumber() {
        val html = """
            <div class="weui-panel">
              <div class="weui-cell">
                <div class="weui-cell__bd">用户编号</div>
                <div class="weui-cell__ft">BS10S1602</div>
              </div>
              <div class="weui-cell">
                <div class="weui-cell__bd">用户地址</div>
                <div class="weui-cell__ft">北校10楼南1602</div>
              </div>
              <div class="weui-cell">
                <div class="weui-cell__bd">实时余额</div>
                <div class="weui-cell__ft">21.86</div>
              </div>
            </div>
        """.trimIndent()

        assertNull(DormElectricityRepository.parse(html, DormInfo("10号宿舍楼", "602")))
    }

    @Test
    fun paymentUrlUsesMatchedAccount() {
        val detail = DormElectricityBalance(
            balance = "1.00",
            accountNo = "BS10S0602",
            address = "北校10楼南602",
            accountId = "4155",
            itemId = "5644",
        )
        val url = DormElectricityRepository.paymentUrl(detail)
        assertTrue(url.contains("payItemIndex"))
        assertTrue(url.contains("accountId=4155"))
        assertTrue(url.contains("itemId=5644"))
    }

    @Test
    fun paymentUrlFallsBackToAddPageWithoutAccount() {
        val url = DormElectricityRepository.paymentUrl(null)
        assertTrue(url.endsWith("payItemAdd"))
    }

    // ── matchedPaymentUrl：区分「认出了本人」与「没认出人」 ──
    //
    // 这个区分是「点缴费跳到绑定界面」那个问题的界面层依据：
    // 认出人 → 送微信直达本人缴费页；没认出人 → 说明该先去填宿舍编号，
    // 而不是把用户送进 payItemAdd（"增绑新用户"）—— 那正是用户看到的绑定界面。

    @Test
    fun matchedPaymentUrlReturnsNullWhenAccountUnmatched() {
        // 没认出人时**必须**返回 null，不能退到 payItemAdd。
        // 一旦这里退回 payItemAdd，界面就又会把用户送去绑定页。
        assertNull(DormElectricityRepository.matchedPaymentUrl(null))
        assertNull(
            DormElectricityRepository.matchedPaymentUrl(
                DormElectricityBalance(balance = "1.00", accountNo = "X", address = "Y")
            )
        )
        // 只有 accountId 或只有 itemId 也算没认全 —— 服务端两个都要
        // （实测：只给 accountId 会返回 `500 Incorrect result size`）
        assertNull(
            DormElectricityRepository.matchedPaymentUrl(
                DormElectricityBalance(
                    balance = "1.00", accountNo = "X", address = "Y", accountId = "4155"
                )
            )
        )
        assertNull(
            DormElectricityRepository.matchedPaymentUrl(
                DormElectricityBalance(
                    balance = "1.00", accountNo = "X", address = "Y", itemId = "5644"
                )
            )
        )
    }

    @Test
    fun matchedPaymentUrlGivesDirectPageWhenAccountMatched() {
        val url = DormElectricityRepository.matchedPaymentUrl(
            DormElectricityBalance(
                balance = "1.00",
                accountNo = "BS10S0602",
                address = "北校10楼南602",
                accountId = "4155",
                itemId = "5644",
            )
        )
        assertEquals(true, url != null)
        assertTrue("应直达本人缴费页", url!!.contains("payItemIndex"))
        assertTrue(url.contains("accountId=4155"))
        assertTrue(url.contains("itemId=5644"))
        // 与 paymentUrl 一致：认出人时两者必须是同一个地址
        assertEquals(
            url,
            DormElectricityRepository.paymentUrl(
                DormElectricityBalance(
                    balance = "1.00", accountNo = "BS10S0602", address = "北校10楼南602",
                    accountId = "4155", itemId = "5644",
                )
            ),
        )
    }

    // ── 宿舍编号（对接电费）：填了它就该按编号认人 ──

    @Test
    fun parseMatchesByDormCodeWhenAddressWordingDiffers() {
        // 地址写法是人写的、五花八门；这一条的写法按"楼号 + 房间号"完全对不上，
        // 只有宿舍编号能把本人这一间认出来 —— 这正是这个字段存在的理由。
        val html = """
            <div class="weui-panel">
              <div class="weui-cell">
                <div class="weui-cell__bd">用户编号</div>
                <div class="weui-cell__ft">BS10S0602</div>
              </div>
              <div class="weui-cell">
                <div class="weui-cell__bd">用户地址</div>
                <div class="weui-cell__ft">南区十号公寓 0602</div>
              </div>
              <div class="weui-cell">
                <div class="weui-cell__bd">实时余额</div>
                <div class="weui-cell__ft">33.20</div>
              </div>
              <a href="payItemIndex?accountId=9&amp;itemId=8">缴费</a>
            </div>
        """.trimIndent()

        val dorm = DormInfo(building = "10号宿舍楼", room = "602", dormCode = "BS10S0602")
        val result = DormElectricityRepository.parse(html, dorm)
        assertEquals("33.20", result?.balance)
        assertEquals("9", result?.accountId)
        assertTrue("应标注为按编号认出", result?.matchedByCode == true)
    }

    @Test
    fun parseDormCodeIgnoresCaseAndSeparators() {
        val html = """
            <div class="weui-panel">
              <div class="weui-cell">
                <div class="weui-cell__bd">用户编号</div>
                <div class="weui-cell__ft">BS11A0114</div>
              </div>
              <div class="weui-cell">
                <div class="weui-cell__bd">用户地址</div>
                <div class="weui-cell__ft">北校11楼A114</div>
              </div>
              <div class="weui-cell">
                <div class="weui-cell__bd">实时余额</div>
                <div class="weui-cell__ft">5.00</div>
              </div>
            </div>
        """.trimIndent()

        // 用户从网页上抄下来时常见的大小写 / 横线 / 井号差异，不该导致认不出
        val dorm = DormInfo(building = "11号楼", room = "A114", dormCode = "bs11a-0114")
        assertEquals("5.00", DormElectricityRepository.parse(html, dorm)?.balance)
    }

    @Test
    fun parseFallsBackToAddressWhenDormCodeDoesNotMatch() {
        val html = """
            <div class="weui-panel">
              <div class="weui-cell">
                <div class="weui-cell__bd">用户编号</div>
                <div class="weui-cell__ft">BS10S0602</div>
              </div>
              <div class="weui-cell">
                <div class="weui-cell__bd">用户地址</div>
                <div class="weui-cell__ft">北校10楼南602</div>
              </div>
              <div class="weui-cell">
                <div class="weui-cell__bd">实时余额</div>
                <div class="weui-cell__ft">21.86</div>
              </div>
            </div>
        """.trimIndent()

        // 编号填错了（或是别人宿舍的编号）不能把本人这一间弄丢：
        // 编号没对上时仍按楼栋 + 房间号匹配地址，且要如实标注"不是按编号认的"
        val dorm = DormInfo(building = "10号宿舍楼", room = "602", dormCode = "BS99X9999")
        val result = DormElectricityRepository.parse(html, dorm)
        assertEquals("21.86", result?.balance)
        assertTrue("未按编号认出时应标注", result?.matchedByCode == false)
    }

    @Test
    fun parseDormCodeNeverMatchesAnotherRoomBySubstring() {
        val html = """
            <div class="weui-panel">
              <div class="weui-cell">
                <div class="weui-cell__bd">用户编号</div>
                <div class="weui-cell__ft">BS10S1602</div>
              </div>
              <div class="weui-cell">
                <div class="weui-cell__bd">用户地址</div>
                <div class="weui-cell__ft">北校10楼南1602</div>
              </div>
              <div class="weui-cell">
                <div class="weui-cell__bd">实时余额</div>
                <div class="weui-cell__ft">21.86</div>
              </div>
            </div>
        """.trimIndent()

        // 编号必须是**整串相等**，不是包含 —— 否则 `602` 会把 `1602` 认成自己家
        val dorm = DormInfo(building = "10号宿舍楼", room = "602", dormCode = "602")
        assertNull(DormElectricityRepository.parse(html, dorm))
    }

    /**
     * 崩溃回归（第二现场）：宿舍是存在 JSON 里的，老存档没有 `dormCode` 这一栏时
     * release 包里它是 null。查询路径上 `dorm.room.trim()` / `normalizeCode(dorm.dormCode)`
     * 都会直接 NPE —— 这一条钉住"字段是 null 也要能跑，没编号就退回地址匹配"。
     */
    @Test
    fun parseSurvivesLegacyDormJsonWithNullFields() {
        val html = """
            <div class="weui-panel">
              <div class="weui-cell">
                <div class="weui-cell__bd">用户编号</div>
                <div class="weui-cell__ft">BS10S0602</div>
              </div>
              <div class="weui-cell">
                <div class="weui-cell__bd">用户地址</div>
                <div class="weui-cell__ft">北校10楼南602</div>
              </div>
              <div class="weui-cell">
                <div class="weui-cell__bd">实时余额</div>
                <div class="weui-cell__ft">21.86</div>
              </div>
            </div>
        """.trimIndent()

        val legacy = Gson().fromJson(
            """{"building":"10号宿舍楼","room":"602","serviceCampusId":null}""",
            DormInfo::class.java,
        )
        val result = DormElectricityRepository.parse(html, legacy)
        assertEquals("21.86", result?.balance)
        assertTrue(result?.matchedByCode == false)

        // 连楼栋都是 null 时只该返回 null（"没绑定"），不该抛
        val empty = Gson().fromJson("""{"building":null,"room":null}""", DormInfo::class.java)
        assertNull(DormElectricityRepository.parse(html, empty))
    }
}
