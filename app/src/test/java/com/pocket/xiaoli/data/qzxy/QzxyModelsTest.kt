package com.pocket.xiaoli.data.qzxy

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 趣智校园接入的纯逻辑回归测试。
 *
 * ## 为什么这几条值得测
 *
 * 这些规则**全部来自参考实现踩过的坑**，而且错了都不会编译失败、也不会立刻看出来：
 *
 * - `secret` 算错 → 短信根本发不出去，服务端只回一句含糊的错
 * - 金额单位搞错 → 界面显示「本次消费 40 元」（实际 0.04 元）
 * - 开阀判据搞错 → 明明成功了却提示失败，用户会重复点，重复扣费
 * - 三态压成布尔 → 网络异常时显示「空闲」，而水还在流、钱还在扣
 */
class QzxySignTest {

    @Test
    fun `secret 算法与官方样例一致`() {
        // 这是整个接入的锚点：对不上就说明算法被改过，登录会全部失败
        assertEquals(
            QzxySign.SAMPLE_SECRET,
            QzxySign.smsSecret(QzxySign.SAMPLE_PHONE),
        )
    }

    @Test
    fun `secret 只取前3位与后4位`() {
        // 13800138000 → "138" + "8000" + "klcx"
        // 中间 4 位（0013）不参与，改中间不该影响结果
        val a = QzxySign.smsSecret("13800138000")
        val b = QzxySign.smsSecret("13899998000")
        assertEquals("中间四位不参与计算，结果应当相同", a, b)
    }

    @Test
    fun `不同手机号得到不同 secret`() {
        assertTrue(QzxySign.smsSecret("13800138000") != QzxySign.smsSecret("13900139000"))
    }

    @Test
    fun `secret 是32位小写十六进制`() {
        val s = QzxySign.smsSecret("18800000000")
        assertEquals(32, s.length)
        assertTrue("应当全是小写十六进制字符", s.all { it in "0123456789abcdef" })
    }

    @Test(expected = IllegalArgumentException::class)
    fun `手机号不是11位时直接抛错`() {
        QzxySign.smsSecret("1380013800")
    }
}

class QzxyModelsTest {

    @Test
    fun `消费金额按厘换算成元`() {
        // ⚠️ consumeOrder/result/query 的单位是「厘」：同一笔 0.04 元的账返回 40
        val r = QzxyCloseResult(consumeMoney = 40.0)
        assertEquals(0.04, r.consumeYuan!!, 1e-9)
    }

    @Test
    fun `字符串形式的金额按元处理`() {
        // 账单列表接口返回的字符串就是元，不能再除 1000
        val r = QzxyCloseResult(consumeMoneyStr = "0.04")
        assertEquals(0.04, r.consumeYuan!!, 1e-9)
    }

    @Test
    fun `字符串优先于数字字段`() {
        val r = QzxyCloseResult(consumeMoney = 40.0, consumeMoneyStr = "0.04")
        assertEquals(0.04, r.consumeYuan!!, 1e-9)
    }

    @Test
    fun `金额字段都缺失时返回 null 而不是 0`() {
        // 返回 0 会让界面显示「本次消费 ¥0.00」，而实际上金额还没结算出来
        assertNull(QzxyCloseResult().consumeYuan)
    }

    @Test
    fun `开阀成功判据是 state=0 或 result=0 或 orderNo 非空`() {
        // 参考实现实测后保留的判断：success && (state == 0 || result == 0 || orderNo != null)
        // 三个条件各自都算成功 —— 不同学校给出的字段不一样
        assertTrue("state=1 是文档样本", QzxyDownRateResult(state = 1, result = 0).isSuccess)
        assertTrue("state=0 也在别的学校出现过", QzxyDownRateResult(state = 0).isSuccess)
        assertTrue("只看 result", QzxyDownRateResult(result = 0).isSuccess)
        assertTrue(
            "拿到 orderNo 就说明单子开出来了 —— 这是最可靠的兜底",
            QzxyDownRateResult(orderNo = "13202609181506275230").isSuccess,
        )
    }

    @Test
    fun `三个字段都缺失时不算成功`() {
        assertFalse(QzxyDownRateResult().isSuccess)
    }

    @Test
    fun `orderNo 是空串时不当作成功依据`() {
        // isNullOrBlank 而不是 != null：空串等于没拿到
        assertFalse(QzxyDownRateResult(orderNo = "").isSuccess)
    }

    @Test
    fun `响应壳的 displayMessage 优先取 errorMessage`() {
        assertEquals(
            "手机号不能为空",
            QzxyResponse<Unit>(success = false, errorMessage = "手机号不能为空", msg = "其它").displayMessage,
        )
    }

    @Test
    fun `响应壳 errorMessage 为空串时回退到 msg`() {
        assertEquals(
            "其它",
            QzxyResponse<Unit>(success = false, errorMessage = "", msg = "其它").displayMessage,
        )
    }

    @Test
    fun `全部为空的响应壳 displayMessage 为 null`() {
        assertNull(QzxyResponse<Unit>(success = false).displayMessage)
    }

    @Test
    fun `余额字符串转元`() {
        assertEquals(16.76, QzxyCampusUserInfo(amount = "16.76").amountYuan!!, 1e-9)
        assertNull("服务端不给 amount 时应当是 null，而不是 0", QzxyCampusUserInfo().amountYuan)
    }

    @Test
    fun `未签约免密支付可识别`() {
        assertTrue(QzxyCampusUserInfo(signStatus = 1).isSigned)
        assertFalse(QzxyCampusUserInfo(signStatus = 0).isSigned)
        assertFalse("字段缺失按未签约处理", QzxyCampusUserInfo().isSigned)
    }

    @Test
    fun `钱包总额优先用 money 字符串`() {
        assertEquals(12.5, QzxyWallet(money = "12.50").moneyYuan, 1e-9)
    }

    @Test
    fun `钱包 money 为空时回退到两项相加`() {
        assertEquals(
            12.5,
            QzxyWallet(accountRealMoney = 10.0, accountGivenMoney = 2.5, money = "").moneyYuan,
            1e-9,
        )
    }

    @Test
    fun `认证参数必须同时带 telephone 与 telPhone`() {
        // ⚠️ 少 telPhone 会让余额接口回「手机号不能为空」，而且是 HTTP 200
        val q = QzxyAuth(loginCode = "x", telephone = "13800138000").asQuery()
        assertEquals("13800138000", q["telephone"])
        assertEquals("13800138000", q["telPhone"])
        assertEquals("android", q["phoneSystem"])
        assertEquals(QzxyApi.VERSION, q["version"])
    }

    @Test
    fun `GET query 与 POST form 字段一致`() {
        // 两处少任何一个都会「参数不全」，必须同源
        val auth = QzxyAuth(loginCode = "c", userId = "1", accountId = "2", projectId = "3", telephone = "138")
        assertEquals(auth.asQuery(), auth.asForm())
    }

    @Test
    fun `设备在线状态识别`() {
        assertTrue(QzxyDeviceInfo(onlineStatusId = 1).isOnline)
        assertFalse(QzxyDeviceInfo(onlineStatusId = 0).isOnline)
    }
}

/**
 * 设备名的**位置解析**。
 *
 * 服务端格式（API 文档原文）：`热水器-{校区}-{楼栋}-{楼层}-{房间}`。
 * 「哪栋楼、哪一层」本来就在这串里 —— 所以不需要另做一个「预约」功能，
 * 但也**必须解析对**：显示错楼层比不显示更糟。
 *
 * 全部样例都取自参考实现的 API 文档，不是我编的。
 */
class QzxyDeviceLocationTest {

    private fun device(name: String) = QzxyDeviceInfo(deviceName = name)

    @Test
    fun `解析标准五段设备名`() {
        val d = device("热水器-学生公寓-1号楼-3层-301")
        assertEquals("1号楼", d.building)
        assertEquals("3层", d.floor)
        assertEquals("301", d.room)
        assertEquals("热水器", d.typeLabel)
    }

    @Test
    fun `解析带房字与带南的楼栋`() {
        val d = device("热水器-龙川北苑-3号楼南-3层-320房")
        assertEquals("3号楼南", d.building)
        assertEquals("3层", d.floor)
        assertEquals("320房", d.room)
    }

    @Test
    fun `洗手台带编号也能识别类型与位置`() {
        val d = device("洗手台54-龙川北苑-3号楼南-3层-320洗手台")
        assertEquals("洗手台", d.typeLabel)
        assertEquals("3号楼南", d.building)
        assertEquals("3层", d.floor)
    }

    @Test
    fun `校区缺失时只有三段也能取对楼层`() {
        // ⚠️ 这是不能靠下标的原因：这一串只有 3 段，写死 parts[3] 会越界或取错
        val d = device("热水器-1号楼-3层-301")
        assertEquals("1号楼", d.building)
        assertEquals("3层", d.floor)
        assertEquals("301", d.room)
    }

    @Test
    fun `只剩一段时不猜房间`() {
        // 只剩一段时那一段可能是楼栋也可能是房间 —— 判不出来就不猜，
        // 猜错会把房间号当楼栋显示
        val d = device("热水器-301")
        assertNull(d.floor)
        assertNull(d.room)
        assertNull("没有『楼』字就不认楼栋", d.building)
    }

    @Test
    fun `没有楼层信息时 floor 为 null 而不是乱猜`() {
        val d = device("热水器-学生公寓-1号楼-301")
        assertNull(d.floor)
        assertEquals("301", d.room)
    }

    @Test
    fun `locationSummary 优先给楼层加房间`() {
        assertEquals("3层 301", device("热水器-学生公寓-1号楼-3层-301").locationSummary)
        assertEquals("3层", device("热水器-学生公寓-1号楼-3层").locationSummary)
        assertEquals("301", device("热水器-学生公寓-1号楼-301").locationSummary)
    }

    @Test
    fun `解析不出位置时 locationSummary 回退到原始名而不是空串`() {
        // 界面上出现一行空白比显示一串原始名字更糟
        val d = device("热水器")
        assertTrue("不能是空串", d.locationSummary.isNotBlank())
    }

    @Test
    fun `完整位置拼出楼栋楼层房间`() {
        assertEquals(
            "1号楼 3层 301",
            device("热水器-学生公寓-1号楼-3层-301").fullLocation,
        )
    }

    @Test
    fun `饮水机按 bigTypeId 识别`() {
        // 类型判据里 bigTypeId == 5 优先于名字
        assertEquals("饮水机", QzxyDeviceInfo(deviceName = "某设备", bigTypeId = 5).typeLabel)
    }
}

/**
 * 设备码（使用码）的模型语义。
 *
 * 三条都来自实测/抓包结论，错了都会让界面说谎：
 * - 把「没有码」显示成「加载中」→ 用户一直等
 * - 把「今天不能领取」当成「可以」→ 点了才被拒
 * - 把「换一个」当成已生效 → 用户以为码换了，实际还是旧的
 */
class QzxyUseCodeTest {

    @Test
    fun `没领过码的账号 useCode 为 null`() {
        // ⚠️ 服务端对从没领过码的账号返回 "useCode": null（不是空串、也不是 0）
        assertFalse(QzxyUseCode(useCode = null).hasCode)
        assertFalse("空串也不算有码", QzxyUseCode(useCode = "").hasCode)
    }

    @Test
    fun `有码时识别出来`() {
        assertTrue(QzxyUseCode(useCode = "27389960").hasCode)
    }

    @Test
    fun `开启状态按 useCodeStatus 判断`() {
        assertTrue(QzxyUseCode(useCode = "27389960", useCodeStatus = 1).isEnabled)
        assertFalse(QzxyUseCode(useCode = "27389960", useCodeStatus = 0).isEnabled)
        assertFalse("字段缺失按未开启处理", QzxyUseCode(useCode = "27389960").isEnabled)
    }

    @Test
    fun `今日能否重新领取只看 resetAvailability`() {
        assertTrue(QzxyUseCode(resetAvailability = 1).canResetToday)
        assertFalse(QzxyUseCode(resetAvailability = 0).canResetToday)
        assertFalse("字段缺失时按不能处理，避免点了才被拒", QzxyUseCode().canResetToday)
    }

    @Test
    fun `候选项与当前码是不同的东西`() {
        // 「换一个」换出来的是候选码，**未生效** —— 模型上必须能区分，
        // 否则界面可能把候选码当成"已经换好了"
        val candidate = QzxyUseCodeCandidate(useCode = "84663364", remainTimes = 15)
        assertEquals("84663364", candidate.useCode)
        assertEquals(15, candidate.remainTimes)
    }

    @Test
    fun `候选码剩余次数可为空`() {
        // 字段缺失不该崩，也不该显示成 0 次（那是"没额度了"的意思）
        assertNull(QzxyUseCodeCandidate(useCode = "84663364").remainTimes)
    }
}

/**
 * 机身二维码解析。
 *
 * ## 为什么单独盯这一块
 *
 * 走扫码就等于**绕开了蓝牙** —— 而蓝牙是这次接入里唯一没法在电脑上验证的部分
 *（广播名、Android 12 以下的定位权限、各厂商 ROM 差异）。
 * 换句话说：这条路上的每一步都是可离线验证的，那就必须真的验到位。
 *
 * 解析错的后果不是报错，而是**把别人的设备当成自己的**去开阀。
 */
class QzxyDeviceCodeTest {

    @Test
    fun `解析官方格式的机身二维码`() {
        // 参考实现文档里给的样例格式
        assertEquals("C47F0EDA85D8", QzxyDeviceCode.parseMac("KLCXKJ-Water,M,C47F0EDA85D8"))
    }

    @Test
    fun `带冒号分隔的 MAC 也能解析`() {
        assertEquals("C47F0EDA85D8", QzxyDeviceCode.parseMac("KLCXKJ-Water,M,C4:7F:0E:DA:85:D8"))
    }

    @Test
    fun `小写十六进制会被规范化成大写`() {
        assertEquals("C47F0EDA85D8", QzxyDeviceCode.parseMac("KLCXKJ-Water,M,c47f0eda85d8"))
    }

    @Test
    fun `纯 MAC 也能解析`() {
        assertEquals("C47F0EDA85D8", QzxyDeviceCode.parseMac("C47F0EDA85D8"))
        assertEquals("C47F0EDA85D8", QzxyDeviceCode.parseMac("C4:7F:0E:DA:85:D8"))
    }

    @Test
    fun `逗号前后有空格也能解析`() {
        assertEquals("C47F0EDA85D8", QzxyDeviceCode.parseMac("KLCXKJ-Water, M, C47F0EDA85D8 "))
    }

    @Test
    fun `位数不对时返回 null 而不是硬凑`() {
        assertNull(QzxyDeviceCode.parseMac("KLCXKJ-Water,M,C47F0EDA85D"))     // 11 位
        assertNull(QzxyDeviceCode.parseMac("KLCXKJ-Water,M,C47F0EDA85D8A"))   // 13 位
    }

    @Test
    fun `含非十六进制字符时返回 null`() {
        // G 不是十六进制字符 —— 这种字符串看起来像 MAC，必须拒绝
        assertNull(QzxyDeviceCode.parseMac("KLCXKJ-Water,M,C47F0EDA85DG"))
    }

    @Test
    fun `无关二维码返回 null`() {
        // 用户很可能扫到别的码：网址、别的 App、Wi-Fi 配置……
        // 返回 null 才能让界面给出「这不是热水器的码」，而不是拿垃圾串去请求接口
        assertNull(QzxyDeviceCode.parseMac("https://example.com/abc"))
        assertNull(QzxyDeviceCode.parseMac("WIFI:S:MyNet;T:WPA;P:123456;;"))
        assertNull(QzxyDeviceCode.parseMac(""))
        assertNull(QzxyDeviceCode.parseMac(null))
    }

    @Test
    fun `parseDeviceMac 对机身码与纯 MAC 都放行`() {
        assertEquals("C47F0EDA85D8", QzxyDeviceCode.parseDeviceMac("KLCXKJ-Water,M,C47F0EDA85D8"))
        assertEquals("C47F0EDA85D8", QzxyDeviceCode.parseDeviceMac("C47F0EDA85D8"))
        assertNull(QzxyDeviceCode.parseDeviceMac("https://example.com"))
    }

    @Test
    fun `MAC 格式化成分隔形式`() {
        assertEquals("C4:7F:0E:DA:85:D8", QzxyDeviceCode.formatMac("C47F0EDA85D8"))
    }
}
