package com.pocket.xiaoli.ui.profile

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.pocket.xiaoli.data.School
import com.pocket.xiaoli.di.AppContainer
import com.pocket.xiaoli.network.ZhengfangClient
import com.pocket.xiaoli.data.SchoolRegistry
import com.pocket.xiaoli.ui.zhengfang.ZhengfangLoginViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * 「绑定信息 → 教务账号」里那个**验证账号**用的登录器：只回答一句话 ——
 * **这对学号 / 密码能不能登进本校教务**。
 *
 * ## 为什么继承 `ZhengfangLoginViewModel` 而不是自己写一遍
 *
 * 登录这件小事在项目里已经有一份实现：`ui/zhengfang/` 的抽象基类负责
 * 「锁定当前校区 → 提交账号密码 → 探测验证码 → 挂起等用户输入 → 刷新验证码 → 重试」。
 * `CLAUDE.md` 明确要求新增的同类流程**必须继承复用、禁止复制粘贴**，
 * 所以这里只做两件事：
 *
 * 1. 把**输入框里当前的值**送进基类（基类默认预填的是"本机已保存的那份"）；
 * 2. 在 [afterLogin] 里立一个"验证成功"的旗标给界面用。
 *
 * ## 一个容易被漏掉的既有行为（它决定了这里的交互）
 *
 * 基类在登录成功时**顺手把凭据写进本机**（`ZhengfangLoginViewModel:
 * appContainer.saveCredential(school.baseUrl, u, p)`）。
 * 也就是说"验证通过"与"已经保存"是同一件事 —— 所以界面上主按钮叫
 * **「验证并保存」**，而不是"验证→再点一次保存"。离线时仍留一条「仅保存」的退路
 *（见 `AcademicAccountSection` 的说明）。
 */
class AcademicVerifyViewModel(
    client: ZhengfangClient,
    schoolRegistry: SchoolRegistry,
    appContainer: AppContainer,
) : ZhengfangLoginViewModel(client, schoolRegistry, appContainer) {

    private val _verified = MutableStateFlow(false)

    /** 本次验证是否已成功过。失败原因走基类的 `error`（界面直接渲染）。 */
    val verified: StateFlow<Boolean> = _verified.asStateFlow()

    /**
     * 用**当前输入框里的**账号密码验证一次。
     *
     * ⚠️ 必须在 `startLogin()` 之前把值灌进去：基类的构造里预填的是"本机已保存的凭据"，
     * 而用户在表单里改过的那份才是要验证的对象。
     */
    fun verify(studentId: String, password: String) {
        _verified.value = false
        clearError()
        updateUsername(studentId)
        updatePassword(password)
        startLogin()
    }

    /** 退出表单（或改动字段）时把上一次的验证结果清干净，免得旧结论挂在界面上误导人。 */
    fun reset() {
        _verified.value = false
        clearError()
    }

    override suspend fun afterLogin(school: School) {
        // 不做任何数据加载 —— 只要能走到这里，就说明账号密码是对的。
        _verified.value = true
    }

    class Factory(
        private val client: ZhengfangClient,
        private val schoolRegistry: SchoolRegistry,
        private val appContainer: AppContainer,
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T =
            AcademicVerifyViewModel(client, schoolRegistry, appContainer) as T
    }
}
