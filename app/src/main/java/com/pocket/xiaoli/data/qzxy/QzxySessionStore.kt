package com.pocket.xiaoli.data.qzxy

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * 趣智校园登录态存储。
 *
 * ## 为什么用加密存储
 *
 * `loginCode` 等价于**会话令牌**：拿到它就能用这个账号开热水、扣钱。
 * 明文放在 `SharedPreferences` 里，任何能读到应用私有目录的东西（root / 备份 / 调试）都能直接冒用。
 * 项目里 `credPrefs`（教务凭据）已经是这个规格，这里保持一致。
 *
 * ## 为什么单独一个文件、不复用 AppContainer 的 credPrefs
 *
 * `credPrefs` 是 `private` 的（有意为之：避免别处绕过封装读写教务凭据）。
 * 拆开还有一个实际好处：**退出趣智校园账号只需清这一个文件**，
 * 不会碰到教务凭据、学习平台 cookie。
 *
 * ## 落盘字段
 *
 * 与 [QzxyAuth] 一一对应，外加手机号（界面显示用）和姓名（缓存，避免每次进页面都查）。
 */
class QzxySessionStore(context: Context) {

    private val prefs: SharedPreferences by lazy {
        val ctx = context.applicationContext
        val masterKey = MasterKey.Builder(ctx)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
        try {
            EncryptedSharedPreferences.create(
                ctx,
                FILE_NAME,
                masterKey,
                EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
            )
        } catch (e: Exception) {
            // 与 AppContainer 的处理一致：设备不支持时退回明文，**不让功能整个不可用**。
            // 退回时打日志，因为这意味着令牌会以明文落盘。
            Log.w(TAG, "EncryptedSharedPreferences 不可用，趣智校园登录态退化为明文存储", e)
            ctx.getSharedPreferences(FILE_NAME, Context.MODE_PRIVATE)
        }
    }

    /** 登录态变化信号，UI 用它切换「未登录 / 已登录」。 */
    private val _session = MutableStateFlow(load())
    val session: StateFlow<QzxySession?> = _session

    fun current(): QzxySession? = _session.value

    /** 保存刚登录成功的会话。 */
    fun save(login: QzxyLoginData, telephone: String) {
        val account = login.userAccount
        val s = QzxySession(
            auth = QzxyAuth(
                loginCode = login.loginCode,
                userId = login.userId.toString(),
                accountId = (account?.accountId ?: 0L).toString(),
                projectId = (account?.projectId ?: 0L).toString(),
                telephone = telephone,
            ),
            telephone = telephone,
            name = account?.name.orEmpty(),
        )
        prefs.edit()
            .putString(KEY_LOGIN_CODE, s.auth.loginCode)
            .putString(KEY_USER_ID, s.auth.userId)
            .putString(KEY_ACCOUNT_ID, s.auth.accountId)
            .putString(KEY_PROJECT_ID, s.auth.projectId)
            .putString(KEY_TELEPHONE, s.telephone)
            .putString(KEY_NAME, s.name)
            .apply()
        _session.value = s
    }

    /** 只更新姓名（`/account/info` 拿到后回填，免得每次进页面都查一遍）。 */
    fun updateName(name: String?) {
        val s = _session.value ?: return
        val trimmed = name?.trim().orEmpty()
        if (trimmed.isEmpty() || trimmed == s.name) return
        prefs.edit().putString(KEY_NAME, trimmed).apply()
        _session.value = s.copy(name = trimmed)
    }

    fun clear() {
        prefs.edit().clear().apply()
        _session.value = null
    }

    private fun load(): QzxySession? {
        val loginCode = prefs.getString(KEY_LOGIN_CODE, null).orEmpty()
        // 没有 loginCode 就等于没登录 —— 只剩半截字段（userId 之类）不构成可用会话。
        if (loginCode.isEmpty()) return null
        val telephone = prefs.getString(KEY_TELEPHONE, null).orEmpty()
        return QzxySession(
            auth = QzxyAuth(
                loginCode = loginCode,
                userId = prefs.getString(KEY_USER_ID, null).orEmpty(),
                accountId = prefs.getString(KEY_ACCOUNT_ID, null).orEmpty(),
                projectId = prefs.getString(KEY_PROJECT_ID, null).orEmpty(),
                telephone = telephone,
            ),
            telephone = telephone,
            name = prefs.getString(KEY_NAME, null).orEmpty(),
        )
    }

    companion object {
        private const val TAG = "QzxySessionStore"
        private const val FILE_NAME = "qzxy_session_encrypted"
        private const val KEY_LOGIN_CODE = "loginCode"
        private const val KEY_USER_ID = "userId"
        private const val KEY_ACCOUNT_ID = "accountId"
        private const val KEY_PROJECT_ID = "projectId"
        private const val KEY_TELEPHONE = "telephone"
        private const val KEY_NAME = "name"
    }
}

/** 一份可用的趣智校园会话。 */
data class QzxySession(
    val auth: QzxyAuth,
    val telephone: String,
    /** 姓名。可能为空 —— 学校没同步时服务端就是不给 */
    val name: String = "",
) {
    val displayName: String get() = name.ifBlank { telephone.replaceRange(3, 7, "****") }
}
