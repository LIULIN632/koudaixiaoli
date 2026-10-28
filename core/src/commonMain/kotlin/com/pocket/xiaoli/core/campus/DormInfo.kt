package com.pocket.xiaoli.core.campus

/** 宿舍绑定（按校区本地保存）：楼栋 + 房间号 */
data class DormInfo(
    val building: String = "",
    val room: String = "",
    /** 可选：用户手动覆盖的服务校区；为空时按楼栋默认映射。 */
    val serviceCampusId: String? = null,
    /**
     * 「宿舍编号」—— 后勤缴费系统里这间宿舍的编号（缴费页上写着「用户编号」，形如 `BS10S0602`）。
     *
     * ## 为什么要单独存一个
     *
     * 电费余额是从缴费系统的**账户列表**里捞出来的，而那个列表里只有两样东西能用：
     * 「用户地址」和这个编号。地址写法五花八门（`北校10楼南602` / `南校区10栋602`），
     * 靠它匹配是在猜；编号则是身份证式的**唯一标识**。
     * 填了编号就按编号精确认人，没填时仍然按原来的「楼号 + 房间号」匹配 —— 行为不变。
     *
     * 与 [room] 的区别：[room] 是人写的房间号（`602` / `A114`，用于展示和地址匹配），
     * 本字段是系统给的编号（用于认缴费账户），两者不是一回事，别互相覆盖。
     */
    val dormCode: String = "",
) {
    /** 展示用文本，如「3 号楼 401」 */
    val label: String
        // `orEmpty()` 不是多余的：一个 DormInfo 可能是 Gson 反射赋值造出来的，
        // 那时声明成 String 的字段**真的可能是 null**（见 [sanitized]）
        get() = listOf(building.orEmpty(), room.orEmpty())
            .filter { it.isNotBlank() }
            .joinToString(" ")

    val isBound: Boolean get() = building.orEmpty().isNotBlank() || room.orEmpty().isNotBlank()

    /**
     * 电费余额快照的"认人"键：主动气泡与首页低余额提醒共用。
     *
     * 带上校区覆盖与宿舍编号 —— 换了宿舍 / 改了编号之后，旧余额对新账户没有意义，
     * 必须自然失配而不是继续被念出来。两处各拼一份的话，迟早会有一处漏掉某个字段。
     *
     * `orEmpty()` 同样是为 Gson 反射赋值留下的 `null` 兜底（见 [sanitized]）。
     */
    val balanceIdentity: String
        get() = listOf(
            serviceCampusId.orEmpty(),
            building.orEmpty(),
            room.orEmpty(),
            dormCode.orEmpty(),
        ).joinToString("|")

    /**
     * 把 Gson 可能留下的 `null` 归零。
     *
     * ## 为什么必须有这一步（v1.0.93 服务页崩溃的根因）
     *
     * 这个类是存在 SharedPreferences 里的 JSON。**Gson 反序列化不走 Kotlin 的默认值**：
     * 它只有在能用上「无参构造」时才会填默认值，否则退回 `UnsafeAllocator` 直接反射赋值 ——
     * 那时 JSON 里**没有的字段就是 JVM 默认值 `null`**（Kotlin 的非空类型在反射路径上不兜底）。
     *
     * 于是「给这种类加一个字段」= 「给所有老用户的存档里塞一个 null」：
     * 老版本写下的 JSON 没有 `dormCode` 这一栏，新版本一读就是 null，
     * 服务页那句 `dormCode.isNotBlank()` 当场 NPE（`StringsKt.isBlank` 里抛的
     * `getClass() on a null object reference`）。debug/单测跑不出来，因为不混淆的包
     * 恰好走得上无参构造 —— 这个坑只在 release 包上炸。
     *
     * 结论：**读取边界必须先归零，再判 isBound / 再交给业务**。
     */
    fun sanitized(): DormInfo = DormInfo(
        building = building.orEmpty(),
        room = room.orEmpty(),
        serviceCampusId = serviceCampusId,
        dormCode = dormCode.orEmpty(),
    )
}
