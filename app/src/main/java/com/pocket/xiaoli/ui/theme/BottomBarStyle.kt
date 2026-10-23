package com.pocket.xiaoli.ui.theme

import android.content.Context
import com.pocket.xiaoli.ui.glass.GlassCapability
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * 底栏样式。
 *
 * ## 为什么要给底栏单独一个设置
 *
 * 底栏是**全 App 常驻**的组件（5 个 tab 都在用），它的观感权重比任何一个页面都高；
 * 而它又与「配色主题」「皮肤风格」两个维度都正交 —— 同一套配色下，
 * 用户可以只想要玻璃底栏而不想动课表、也可以反过来。
 * 所以做成独立维度，而不是塞进 SkinStyle 里。
 */
enum class BottomBarStyle(val key: String, val label: String, val description: String) {

    /**
     * 液态玻璃：`io.github.kyant0` 的 backdrop（Apache-2.0，与酷安底栏同源）。
     *
     * **三档降级，但不换组件**：折射（Android 13+）/ 模糊（Android 12）/
     * 半透明磨砂（Android 10–11），形状与布局三档完全一致，用户只会感到"效果弱一点"，
     * 不会觉得"我选的样式变成了别的东西"。分档判定唯一真源见 `ui/glass/GlassCapability.kt`。
     */
    LIQUID_GLASS("liquid_glass", "液态玻璃", "半透明 · 折射 · 老系统自动降级"),

    /** 白底大圆角悬浮胶囊：不依赖任何系统能力，任何设备、任何主题下观感都一致。 */
    SOFT("soft", "悬浮胶囊", "白底 · 大圆角 · 轻投影");

    /**
     * 设置页副标题：如实说明**这台机器上**选它之后会看到什么。
     *
     * 不写这个，老系统用户选了玻璃看到截然不同的观感会当成 bug ——
     * 而"静默降级"恰恰是最容易让人误解成 bug 的表现形式。
     */
    fun tierNote(): String {
        if (this != LIQUID_GLASS) return ""
        val capability = GlassCapability.current
        return when {
            capability.canRefract -> "（本机 Android 13+，完整折射）"
            capability.canBlur -> "（本机 Android 12，有模糊、无折射）"
            else -> "（本机为 Android 10/11，只显示磨砂半透明）"
        }
    }

    companion object {

        /**
         * 新用户的默认样式。
         *
         * 选玻璃当默认：Android 13+ 的用户开箱即见完整效果 —— 这是本 App 现在最"现代"的一块，
         * 藏在设置里等于没做。低版本用户看到的是同一形状的磨砂版（不是另一种组件），
         * 观感仍然成立，且 [tierNote] 会如实告知差异。
         */
        val default: BottomBarStyle get() = LIQUID_GLASS

        fun fromKey(key: String?): BottomBarStyle =
            entries.firstOrNull { it.key == key } ?: default
    }
}

/**
 * 底栏样式的本地存储。
 *
 * 与其它外观设置一样放 SharedPreferences（标量配置不该为它升 Room 版本）。
 * 独立成一个类而不是塞进 `AppContainer` 的一堆 getter/setter 里，
 * 是为了让"底栏样式"这个维度有自己的归属，将来加第二项（如"隐藏标签"）不用改容器。
 */
class BottomBarStyleStore(context: Context) {

    private val prefs = context.getSharedPreferences("pocket_xiaoli_prefs", Context.MODE_PRIVATE)

    /**
     * 样式变化通知。
     *
     * 为什么要 Flow 而不是一个 getter：底栏在 `AppNavigation` 里读样式，
     * 而设置页在另一个界面改它。getter 不会触发重组 —— 用户改完返回，
     * 底栏还是旧样式，要杀进程重进才生效。
     * （同类的坑在会话列表的 `currentConversationId` 上踩过一次，那里靠 `conversationNonce` 绕过；
     *   这里用 Flow 更直接。）
     */
    private val _changes = MutableStateFlow(
        BottomBarStyle.fromKey(prefs.getString(KEY, null)),
    )

    /** 用户**选择**的样式（不做能力回落），设置页用它显示选中态。 */
    val selected: BottomBarStyle get() = _changes.value

    /** 可供 Compose 收集的样式流。 */
    fun observe(): StateFlow<BottomBarStyle> = _changes.asStateFlow()

    fun set(style: BottomBarStyle) {
        prefs.edit().putString(KEY, style.key).apply()
        _changes.value = style
    }

    private companion object {
        const val KEY = "bottom_bar_style"
    }
}
