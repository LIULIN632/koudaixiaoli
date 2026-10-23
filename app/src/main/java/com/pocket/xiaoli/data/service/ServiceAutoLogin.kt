package com.pocket.xiaoli.data.service

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * 「服务自动登录」的三档。
 *
 * ## 为什么是开关而不是直接自动登录
 *
 * 校园服务（校园卡 / 电费 / 校园网 / 洗澡 / 热水 / 快递 / 报修）都走**统一身份认证**，
 * 每次进去都要重登一遍 —— 而账号密码其实早就绑在本机了（「我的 → 绑定信息」，
 * 加密存储在 `EncryptedSharedPreferences`，只有本机能解）。
 *
 * 但"要不要连密码也自动提交"不该由我们替用户决定：
 * 密码虽然只存在本机，**自动提交意味着把它写进网页表单**，比"只存在加密存储里"多暴露一层。
 * 所以做成三档，默认只填学号 —— 保守的一档仍然省掉一半手工输入。
 */
enum class ServiceAutoLoginMode(val key: String, val label: String, val description: String) {

    /** 什么都不做。 */
    OFF("off", "关闭", "打开校园服务时不自动填充"),

    /** 只把学号填好，密码由用户自己输（默认）。 */
    FILL_USERNAME("username", "只填学号", "填好绑定信息里的学号，密码你自己输"),

    /** 学号与密码都填并自动提交。 */
    AUTO_LOGIN("auto", "自动登录", "学号密码都填入并自动提交。密码只在本机使用，不会上传");

    companion object {

        /** 默认**只填学号**：既省掉一半手工输入，又不必把密码写进网页表单。 */
        val default: ServiceAutoLoginMode get() = FILL_USERNAME

        fun fromKey(key: String?): ServiceAutoLoginMode =
            entries.firstOrNull { it.key == key } ?: default
    }
}

/**
 * 模式存储。与其它外观类设置一样放 SharedPreferences（标量配置不该为它升 Room 版本）。
 *
 * 用 Flow 而不是 getter：设置页在另一屏改它，而消费方是服务页的内置 WebView ——
 * getter 不会触发重组/重读，用户改完回来还是旧行为（这个坑在底栏样式上踩过）。
 */
class ServiceAutoLoginStore(context: Context) {

    private val prefs = context.getSharedPreferences("pocket_xiaoli_prefs", Context.MODE_PRIVATE)

    private val _mode = MutableStateFlow(ServiceAutoLoginMode.fromKey(prefs.getString(KEY, null)))

    val mode: ServiceAutoLoginMode get() = _mode.value

    fun observe(): StateFlow<ServiceAutoLoginMode> = _mode.asStateFlow()

    fun set(mode: ServiceAutoLoginMode) {
        prefs.edit().putString(KEY, mode.key).apply()
        _mode.value = mode
    }

    private companion object {
        const val KEY = "service_auto_login_mode"
    }
}
