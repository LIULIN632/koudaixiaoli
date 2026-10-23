package com.pocket.xiaoli.data.qzxy

/**
 * 趣智校园**热水器机身二维码**的解析。
 *
 * ## 二维码长什么样
 *
 * ```
 * KLCXKJ-Water,M,C47F0EDA85D8
 * └── 设备前缀 ──┘ │ └─ 12 位十六进制
 *                 └─ 固定单字母段
 * ```
 *
 * 最后一段就是**设备 MAC**（`/device/info/mac` 要的 `macAddress`）。
 * 字段间用 `,` 分隔，位与位之间可能带 `:`（`C4:7F:0E:DA:85:D8`），两种都要认。
 *
 * ## 为什么值得单独做一个解析器 + 单测
 *
 * 走扫码这条路**可以完全不用蓝牙** —— 而蓝牙是这次接入里唯一无法在电脑上验证的部分
 * （广播名 `KLCXKJ-Water`、Android 12 以下的定位权限、各厂商 ROM 差异）。
 * 把「设备身份」的来源换成二维码，就等于把开阀功能里最不确定的一环换掉了。
 *
 * 解析错了的后果不是报错，而是**把别人的设备当成自己的**去查询/开阀。
 * 所以格式校验必须严：只要不是 12 位十六进制就返回 null，绝不做"尽力猜测"。
 *
 * 出处：[`yehu-imei/linyu`](https://github.com/yehu-imei/linyu) 的 `QrScanActivity`（MIT）。
 */
object QzxyDeviceCode {

    /** 热水器机身二维码的设备前缀。 */
    const val DEVICE_PREFIX = "KLCXKJ-Water"

    /** MAC 的规范形态：12 位大写十六进制，无分隔符。 */
    private val MAC_REGEX = Regex("[0-9A-F]{12}")

    /**
     * 从二维码原文里取出设备 MAC。
     *
     * @return 12 位大写十六进制 MAC；格式不符时返回 null（**不做推测**）
     */
    fun parseMac(raw: String?): String? {
        if (raw.isNullOrBlank()) return null
        val last = raw.split(',').lastOrNull()?.trim().orEmpty()
        if (last.isEmpty()) return null
        // 去掉可能存在的分隔符（`:` / `-`），再统一大写
        val normalized = last.replace(":", "").replace("-", "").uppercase()
        return normalized.takeIf { MAC_REGEX.matches(it) }
    }

    /**
     * 判断这段文本是不是**本服务商的设备码**，是则给出 MAC。
     *
     * 扫码页会拿到各种各样的二维码（别的 App 的、网址、Wi-Fi 配置……）。
     * 这个函数用来区分"这不是我们要的码"，好给出一句有用的提示，
     * 而不是让用户对着一个解析不出来的串反复扫。
     *
     * ⚠️ 注意**不要求**前缀当且仅当 —— 先看有没有 `KLCXKJ-Water`，
     * 有则按格式解析；没有也允许纯 MAC / 纯带分隔符 MAC，
     * 因为有些机型的二维码只印了 MAC 那一行。
     */
    fun parseDeviceMac(raw: String?): String? {
        if (raw.isNullOrBlank()) return null
        if (raw.contains(DEVICE_PREFIX, ignoreCase = true)) return parseMac(raw)
        // 整串就是一个（可能带分隔符的）MAC 的情形
        return parseMac(raw)
    }

    /** 给用户看的设备提示：`KLCXKJ-Water,M,C47F0EDA85D8` → `C4:7F:0E:DA:85:D8`。 */
    fun formatMac(mac: String): String =
        mac.chunked(2).joinToString(":")
}
