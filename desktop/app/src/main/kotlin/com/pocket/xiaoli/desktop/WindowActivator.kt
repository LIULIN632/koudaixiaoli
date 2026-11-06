package com.pocket.xiaoli.desktop

import com.pocket.xiaoli.desktop.data.AppLog
import java.awt.Frame
import javax.swing.SwingUtilities

/**
 * 「唤起已有窗口」的线程桥。
 *
 * 单实例守卫的激活请求来自回环 socket 的接收线程，而 AWT 窗口只能在事件分发线程上改，
 * 所以窗口建好后把动作挂到这里，请求方只负责投递。
 */
object WindowActivator {

    @Volatile
    var action: (() -> Unit)? = null

    fun request() {
        val current = action
        if (current == null) {
            AppLog.info("Instance", "收到激活请求，但窗口还没建好")
            return
        }
        SwingUtilities.invokeLater {
            runCatching(current).onFailure {
                AppLog.error("Instance", "唤起窗口失败：${it.javaClass.simpleName} ${it.message}")
            }
        }
    }

    /** 从隐藏（收进托盘）/最小化状态里把窗口拉到前台。 */
    fun showWindow(window: java.awt.Window) {
        window.isVisible = true
        (window as? Frame)?.extendedState = Frame.NORMAL
        window.toFront()
        window.requestFocus()
    }
}
