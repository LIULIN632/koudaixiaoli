package com.pocket.xiaoli.data.qzxy

import com.google.gson.annotations.SerializedName

/**
 * 趣智校园（QZXY / 趣智校园）接口的数据模型。
 *
 * ⚠️ **字段一律可空、且都要给默认值。** 这是参考实现（`yehu-imei/linyu`，MIT）
 * 用踩坑换来的纪律，本项目直接继承：
 *
 * - 各校部署不同，**服务端会按学校改字段名**（如关阀结果有的用 `state`、有的用 `status`）
 * - 学校没同步的信息服务端真的返回 `null`（姓名 / 年级 / 班级）
 * - **Gson 反序列化绕过 Kotlin 的非空约定** —— 声明成非空只会在取用时 NPE
 *   （这与本项目 `DormInfo` 那条老坑同源，见 CLAUDE.md）
 */

/**
 * 服务端统一响应壳。
 *
 * ⚠️ **不能只看 HTTP 状态码**：业务失败也是 HTTP 200。
 * 参考实现里踩过 —— `/settlement/campus/userInfo` 少传 `telPhone` 时返回
 * `手机号不能为空`，但状态码是 200，只看状态码完全看不出来。
 */
data class QzxyResponse<T>(
    val success: Boolean = false,
    val data: T? = null,
    @SerializedName("errorCode") val errorCode: Int = 0,
    @SerializedName("errorMessage") val errorMessage: String? = null,
    val msg: String? = null,
) {
    /** 给用户看的错误文案。`errorMessage` 优先，其次 `msg`。 */
    val displayMessage: String?
        get() = errorMessage?.takeIf { it.isNotBlank() } ?: msg?.takeIf { it.isNotBlank() }
}

/** `POST /user/login` 与 `POST /user/registerAndLogin` 的 data —— 两个接口返回结构完全一致。 */
data class QzxyLoginData(
    val userId: Long = 0L,
    /** 会话令牌（32 位十六进制）。后续所有认证请求都要它。 */
    val loginCode: String = "",
    val userAccount: QzxyUserAccount? = null,
)

data class QzxyUserAccount(
    val accountId: Long = 0L,
    /** 姓名。学校没同步时服务端可能返回 null */
    val name: String? = null,
    /** 项目 / 学校 ID。**由服务端下发**，项目里不硬编码任何学校 */
    val projectId: Long = 0L,
    val accountRealMoney: Double = 0.0,
)

/** 认证参数。GET 拼 query、POST 拼 form body（见 [QzxyApi]）。 */
data class QzxyAuth(
    val loginCode: String = "",
    val userId: String = "",
    val accountId: String = "",
    val projectId: String = "",
    val telephone: String = "",
) {
    /** GET 请求用的 query 参数。 */
    fun asQuery(): Map<String, String> = mapOf(
        "loginCode" to loginCode,
        "userId" to userId,
        "accountId" to accountId,
        "projectId" to projectId,
        "telephone" to telephone,
        // ⚠️ `telPhone` 必须也带上。绝大多数接口只认 `telephone`，
        // 但 `/settlement/campus/userInfo`（一卡通余额）要的是 `telPhone` ——
        // 少了它服务端回「手机号不能为空」，而且是 HTTP 200。
        "telPhone" to telephone,
        "phoneSystem" to "android",
        "version" to QzxyApi.VERSION,
    )

    /** POST 请求用的 form 字段（与 [asQuery] 同源，少一个字段就会「参数不全」）。 */
    fun asForm(): Map<String, String> = asQuery()
}

/** `GET /device/info/mac` —— 用蓝牙 MAC 换设备详情。 */
data class QzxyDeviceInfo(
    val deviceId: Int = 0,
    /**
     * 设备名，**里面含完整位置信息**。
     *
     * 服务端格式（API 文档原文）：
     * ```
     * 热水器-{校区}-{楼栋}-{楼层}-{房间}
     * 洗手台{编号}-{校区}-{楼栋}-{楼层}-{房间}
     * ```
     * 例：`热水器-学生公寓-1号楼-3层-301`、`热水器-龙川北苑-3号楼南-3层-320房`
     *
     * 所以「哪栋楼、哪一层、哪个房间」不需要另做「预约」功能 —— 它本来就在这串里，
     * 见 [building] / [floor] / [room] / [locationSummary]。
     */
    val deviceName: String = "",
    /** 设备序列号 —— **控制设备的关键标识**（开阀/关阀都传它） */
    val snCode: String = "",
    val macAddress: String = "",
    /** 预扣金额（元） */
    val withholdMoney: Double = 0.0,
    val onlineStatusId: Int = 0,
    /** 设备大类：**5 = 直饮水机**。缺失时回退到名称判断 */
    val bigTypeId: Int? = null,
    val bigTypeName: String? = null,
) {
    val isOnline: Boolean get() = onlineStatusId == 1

    /**
     * 设备类型标签：`热水器` / `洗手台 / 其它`。
     *
     * 取名字第一段。`洗手台54-…` 这种带编号的也要认出来，所以用 `startsWith` 而不是相等。
     */
    val typeLabel: String
        get() = when {
            deviceName.startsWith("洗手台") -> "洗手台"
            deviceName.startsWith("热水器") -> "热水器"
            deviceName.startsWith("热水表") -> "热水表"
            deviceName.contains("饮水") || bigTypeId == 5 -> "饮水机"
            else -> "热水器"
        }

    /**
     * 去掉设备类型前缀后的**纯位置**部分。
     *
     * `热水器-学生公寓-1号楼-3层-301` → `学生公寓-1号楼-3层-301`
     */
    private val locationRaw: String
        get() = deviceName
            .replace(Regex("^(热水器|热水表|洗手台\\d*|直饮(开水|冷水|热水|温水)?|开水机?|饮水机)"), "")
            .trimStart('-', ' ')

    /** 按 `-` 拆出的位置段（至少要有两段才认为名字里真带位置）。 */
    private val locationParts: List<String>
        get() = locationRaw.split('-').map { it.trim() }.filter { it.isNotEmpty() }

    /**
     * 楼层，如 `3层`。
     *
     * ⚠️ 用「含『层』」来找，**不靠下标** —— `{校区}` 有时缺失
     *（`热水器-1号楼-3层-301` 只有 3 段），写死下标会取错。
     */
    val floor: String?
        get() = locationParts.lastOrNull { it.contains("层") }

    /** 去掉楼层段后的其余段，供 [building] / [room] 判断。 */
    private val nonFloorParts: List<String>
        get() = locationParts.filterNot { it.contains("层") }

    /**
     * 房间，如 `301` / `320房`。
     *
     * 判据按优先级（**都是模式识别，不是下标**）：
     * 1. 以「房」结尾的段（`320房`）
     * 2. 纯数字段，取**最后一个**
     *
     * ⚠️ 第一段不参与房间判定：`热水器-301` 这种只剩一段的名字，
     * 那一段可能是楼栋也可能是房间，**判不出来就不猜**（返回 null）——
     * 猜错会把房间号当楼栋显示给用户。
     */
    val room: String?
        get() {
            val candidates = nonFloorParts.drop(1)
            return candidates.lastOrNull { it.endsWith("房") }
                ?: candidates.lastOrNull { it.all { c -> c.isDigit() } }
        }

    /**
     * 楼栋，如 `1号楼` / `3号楼南`。
     *
     * 判据：**第一个含「楼」的段**。这个特征最可靠 ——
     * 它同时避开了校区、楼层、房间（房间号里一般不含「楼」）。
     */
    val building: String?
        get() = locationParts.firstOrNull { it.contains("楼") }

    /**
     * 一句话位置，用于界面显示：`3层 301`。
     *
     * 拿不到就回退到原始名字（**绝不返回空串**，否则界面上会出现一行空白）。
     */
    val locationSummary: String
        get() {
            val f = floor
            val r = room
            return when {
                f != null && r != null -> "$f $r"
                f != null -> f
                r != null -> r
                else -> locationRaw.ifBlank { deviceName }
            }
        }

    /** 楼栋 + 楼层 + 房间，如 `1号楼 3层 301`。 */
    val fullLocation: String
        get() = listOfNotNull(building, floor, room).joinToString(" ").ifBlank { locationRaw }
}

/** `POST /order/tcpDevice/query/downRateResult`（开阀结果）与 `downRate` 的返回。 */
data class QzxyDownRateResult(
    /** **开阀确认后这里就有**，不用等 MQTT / queryUsing */
    val orderNo: String? = null,
    /** 自动关停秒数（客户端拿它显示倒计时）。⚠️ 参考实现没真正抓到过这个字段，缺失时不要假设 */
    val autoDisConTime: Int? = null,
    val state: Int? = null,
    val result: Int? = null,
    /** 预扣金额。单位见 [QzxyCloseResult.consumeYuan] 附近的说明 */
    val preDeductMoney: Double? = null,
    val rate: Double? = null,
    val snCode: String? = null,
) {
    /**
     * 是否开阀成功。
     *
     * ⚠️ 判据必须**三个条件取或**，这是参考实现实测后保留下来的写法：
     * `success && (state == 0 || result == 0 || orderNo != null)`
     *
     * - API 文档记的样本是 `state=1, result=0`，但**只看那个会漏** ——
     *   不同学校/不同设备的 `state` 取值不一致，而 `orderNo` 一出现就说明单子真的开出来了
     * - `state == 0` 也要算成功：它和 `state == 1` 在不同学校分别出现过
     *
     * 判失败会让用户重复点「开始」→ **重复扣费**，所以宁可宽一点。
     * 真正的兜底是 [orderNo]：拿到它就一定能关阀、能结算。
     */
    val isSuccess: Boolean
        get() = state == 0 || result == 0 || !orderNo.isNullOrBlank()
}

/**
 * `POST /order/tcpDevice/query/rateOrder/using`（查询进行中的订单）。
 *
 * ⚠️ 语义陷阱（参考实现踩过）：
 * - `errorCode == 307` 表示**设备正在使用中**，此时 `success` 为 **false** —— 不能只判 success
 * - `isOwner == false` 时**绝不能当成"自己的订单"恢复**：那会把别人的单记成自己的，
 *   卡片开始计时，用户点停止时还会拿别人的 orderNo 去关阀
 */
data class QzxyOrderStatus(
    val orderNo: String? = null,
    val state: Int? = null,
    val snCode: String? = null,
    val isOwner: Boolean = true,
    val preDeductMoney: Double? = null,
    val expireTimes: Long? = null,
)

/** `POST /order/tcpDevice/closeOrder/result/query` 与 `consumeOrder/result/query` 的返回。 */
data class QzxyCloseResult(
    val orderNo: String? = null,
    /** 1 = 使用中，0 = 已关闭（部分学校用 `status`） */
    val state: Int? = null,
    /** 部分学校用 `status` */
    val status: Int? = null,
    val result: Int? = null,
    /**
     * 消费金额。
     *
     * ⚠️ **`consumeOrder/result/query` 这个接口的单位是「厘」不是「元」**
     *（1 元 = 1000 厘）。同一笔 0.04 元的账：本接口返回数字 `40`，
     * 而账单列表接口返回字符串 `"0.04"`。**两个接口的字段同名、类型和单位都不同**，
     * 拿错就会显示「本次消费 40 元」。
     */
    val consumeMoney: Double? = null,
    /** 部分学校返回字符串形式的金额，单位是元 */
    val consumeMoneyStr: String? = null,
    val consumeTime: String? = null,
    val deviceSnCode: String? = null,
) {
    /** 转成「元」。厘 ÷ 1000；字符串形式原样解析。 */
    val consumeYuan: Double?
        get() = consumeMoneyStr?.toDoubleOrNull() ?: consumeMoney?.let { it / 1000.0 }
}

/** `GET /account/info` —— 拿姓名和学号最干净的接口。 */
data class QzxyAccountInfo(
    val name: String? = null,
    /** **学号**（不是身份证号） */
    val idCardNumber: String? = null,
    val telephone: String? = null,
    val genderName: String? = null,
    /** 校园卡绑定状态：-1 = 未绑定 */
    val cardStatus: Int? = null,
    val cardStatusName: String? = null,
    val gradeName: String? = null,
    val className: String? = null,
)

/**
 * `GET /settlement/campus/userInfo` —— 一卡通真实余额。
 *
 * 趣智校园把易校园的接口**代理了**，所以拿余额不必去破易校园那套 HMAC-SHA256 原生签名。
 * ⚠️ 前提是学生**签约过校园卡免密支付**，没签约时服务端不给 `amount`。
 */
data class QzxyCampusUserInfo(
    val studentNumber: String? = null,
    val studentName: String? = null,
    /** 一卡通余额，**字符串**形式（如 `"16.76"`），要自己转 Double */
    val amount: String? = null,
    /** 1 = 已签约免密支付 */
    val signStatus: Int? = null,
) {
    val amountYuan: Double? get() = amount?.toDoubleOrNull()
    val isSigned: Boolean get() = signStatus == 1
}

/** `GET /account/wallet` —— 趣智校园钱包余额。 */
data class QzxyWallet(
    val accountRealMoney: Double = 0.0,
    val accountGivenMoney: Double = 0.0,
    /** 总余额（字符串格式） */
    val money: String = "",
) {
    val moneyYuan: Double get() = money.toDoubleOrNull() ?: (accountRealMoney + accountGivenMoney)
}

/** `GET /project/info/triple` —— 只取学校名。 */
data class QzxyProjectInfo(
    val projectId: Int? = null,
    val projectName: String? = null,
    /** 服务电话。实测有值，报修时用得上 */
    val serverPhone: String? = null,
    /** 投资方 / 运营公司。实测「郑州睿凯新能源科技有限公司」 */
    val investorName: String? = null,
    val tenantId: Int? = null,
)

/**
 * `GET /user/project/list` —— **这个账号名下的全部项目**。
 *
 * ⚠️ 一个账号可以有**多个项目**，而它们的钱包、账单、设备是**分开**的。
 * 实测（2026-09-30，烟大南校区洗浴）：
 *
 * ```
 * [{"userId":17359012,"projectId":3000,"accountId":22202},   // 烟台大学南校区洗浴热水
 *  {"userId":17359012,"projectId":3900,"accountId":18310}]   // 另一个项目
 * ```
 *
 * 而登录响应只下发**其中一个**（`userAccount.projectId`）。
 * 所以「余额 / 账单」这类接口如果不显式带对 `projectId`，
 * **很可能查的是另一个项目的数据** —— 这是真实的数据归属 bug，不是理论风险。
 */
data class QzxyUserProject(
    val userId: Long? = null,
    val projectId: Long? = null,
    val accountId: Long? = null,
)

/** `GET /order/query/account/bill/list` 的单条（外面套了一层 `consumeBillDTO`）。 */
data class QzxyBillItem(
    val consumeBillDTO: QzxyBill? = null,
)

data class QzxyBill(
    /** 账单**序号**（7 位）。⚠️ 和 `orderNo` 不是一回事，别拿来互相比 */
    val orderId: String? = null,
    /** 下单时返回的 `orderNo`（20 位）—— **这才是关阀用的那个** */
    val orderNo: String? = null,
    val consumeDate: String? = null,
    /** 字符串形式的金额，单位**元** */
    val consumeMoney: String? = null,
    val description: String? = null,
) {
    val moneyYuan: Double? get() = consumeMoney?.toDoubleOrNull()
}

/**
 * `GET /account/useCode/new` —— 当前使用码。
 *
 * 使用码是给热水器**物理键盘**用的：不想掏手机开阀时，在设备上直接输 8 位码。
 *
 * ⚠️ 字段一律可空是有实测依据的：**从没领过使用码的用户，`useCode` 返回的是 `null`**
 *（不是空串、也不是 0）。客户端必须能把「还没加载」和「服务端说这人没有码」分开，
 * 否则没领过码的人会永远卡在「加载中」。
 */
data class QzxyUseCode(
    /** 8 位数字的使用码。null = 服务端说这个账号还没有码 */
    val useCode: String? = null,
    /** `1` = 已开启，`0` = 已关闭 */
    val useCodeStatus: Int? = null,
    /** 后三位，与手机号后三位相同 */
    val useCodeRandom: String? = null,
    /** 当前码的生效时间，**每次 `set` 都会被改写** */
    val useCodeStartTime: String? = null,
    /**
     * 今天还能**重新领取**吗：`1` = 能，`0` = 不能。
     *
     * ⚠️ 它与「换一个」的 `remainTimes` **不是同一个东西**：
     * 这里是「今天还能不能领取」（1 次/天），那边是「今天还能换几次」（20 次/天）。
     * 把两者混起来会让用户以为额度还有，点了却被告知「1天只能领取一次」。
     */
    val resetAvailability: Int? = null,
    /** `resetAvailability == 0` 时的原因文案，可直接展示 */
    val resetAvailabilityWarMark: String? = null,
) {
    val hasCode: Boolean get() = !useCode.isNullOrBlank()
    val isEnabled: Boolean get() = useCodeStatus == 1
    val canResetToday: Boolean get() = resetAvailability == 1
}

/**
 * `POST /account/useCode/new/generate` —— **换**出来的候选码。
 *
 * ⚠️ 它**还没生效**，要再调 `set` 才作数；3 分钟内不领取就作废。
 * 这一点是参考实现抓包才确认的：连换 7 次期间，服务端生效的码一直没变。
 * 所以「先预览、再决定领不领」是安全的 —— **取消是零成本的**。
 */
data class QzxyUseCodeCandidate(
    /** 候选码，未生效 */
    val useCode: String? = null,
    /** 本次「换一个」还剩几次。每天 20 次，从 20 起每换一次减 1 */
    val remainTimes: Int? = null,
)

/** `GET /device/info/mac` 的失败语义：设备不存在时服务端会给这个码。 */
const val QZXY_ERR_DEVICE_USING = 307
