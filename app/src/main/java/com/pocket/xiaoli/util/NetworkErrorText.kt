package com.pocket.xiaoli.util

import java.io.IOException

/**
 * 把网络异常翻译成**用户能看懂的一句话**。
 *
 * 原本这段判断 `private` 在 `AssistantLlmClient` 里，后果有两个：
 * 一是只有「鲤鱼」那条线会说人话，教务 / 校园卡 / 浴室等路径失败时只能把
 * 原始异常抛给界面（区块一第 3 项要的正是「断网时提示可理解」）；
 * 二是这段最该被钉住的判断**一条测试都没有**，因为它是 private。
 *
 * 抽到这里之后它是纯函数，`NetworkErrorTextTest` 直接覆盖；
 * 各条网络路径只要传自己的 [label]（如 `"模型"` / `"教务"`）即可，
 * 文案格式由这一处统一。
 */
internal object NetworkErrorText {

    fun networkFailure(label: String, e: IOException): String = when {
        e is java.net.UnknownHostException -> "连不上${label}服务，检查网络或接口地址是否正确。"
        e is java.net.SocketTimeoutException -> "${label}响应超时，稍后再试。"
        e is javax.net.ssl.SSLException -> "HTTPS 握手失败，检查接口地址是否为 https。"
        else -> "网络异常：${e.message ?: e.javaClass.simpleName}"
    }
}
