package com.pocket.xiaoli.ui.import_

import android.content.Context
import android.content.SharedPreferences
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.webkit.CookieManager
import android.webkit.JavascriptInterface
import android.webkit.WebView
import android.widget.Toast
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.pocket.xiaoli.data.JwSystemType
import com.pocket.xiaoli.data.School
import com.pocket.xiaoli.data.SchoolRegistry
import com.pocket.xiaoli.data.isPickerSchool
import com.pocket.xiaoli.data.pickerRank
import com.pocket.xiaoli.data.entity.CourseEntity
import com.pocket.xiaoli.data.repository.CourseRepository
import com.pocket.xiaoli.di.AppContainer
import com.pocket.xiaoli.network.CookieStore
import com.pocket.xiaoli.network.CourseFields
import com.pocket.xiaoli.network.parseSlotRange
import com.pocket.xiaoli.network.parseDayHeader
import com.pocket.xiaoli.network.isValidCourseName
import com.pocket.xiaoli.network.extractCourseFields
import com.pocket.xiaoli.network.splitCourseBlocks
import com.pocket.xiaoli.network.parseWeeks
import com.pocket.xiaoli.network.parseWeeksToString
import com.pocket.xiaoli.network.computeWeekType
import com.pocket.xiaoli.network.parseBrDelimitedElements
import com.pocket.xiaoli.network.parseBrOrNewlineElements
import com.pocket.xiaoli.network.detectVerticalLayout
import com.pocket.xiaoli.network.buildDayMap
import com.pocket.xiaoli.network.filterBodyRows
import com.pocket.xiaoli.network.parseHorizontalTable
import com.pocket.xiaoli.network.parseVerticalTable
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.pocket.xiaoli.network.NetworkLogInterceptor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import java.util.concurrent.TimeUnit

private const val TAG = "WebImport"

/** JSON model matching shiguangschedule's ImportCourseJsonModel from JS adapters. */
data class ImportCourseJson(
    val name: String? = "",
    val teacher: String? = "",
    val position: String? = "",
    val day: Int? = 1,
    val startSection: Int? = null,
    val endSection: Int? = null,
    val weeks: List<Int>? = emptyList(),
    val isCustomTime: Boolean = false,
    val customStartTime: String? = null,
    val customEndTime: String? = null,
    val color: String? = null,
    val remark: String? = null
)

/** JS polyfill injected into WebView before any adapter script runs.
 *  Mirrors shiguangschedule's WebViewUtils.injectAllJavaScript(). */
val ANDROID_BRIDGE_PROMISE_JS = """
(function() {
    window._androidPromiseResolvers = {};
    window._androidPromiseRejectors = {};

    window._resolveAndroidPromise = function(promiseId, result) {
        if (window._androidPromiseResolvers[promiseId]) {
            window._androidPromiseResolvers[promiseId](result);
            delete window._androidPromiseResolvers[promiseId];
            delete window._androidPromiseRejectors[promiseId];
        }
    };

    window._rejectAndroidPromise = function(promiseId, error) {
        if (window._androidPromiseRejectors[promiseId]) {
            window._androidPromiseRejectors[promiseId](new Error(error));
            delete window._androidPromiseResolvers[promiseId];
            delete window._androidPromiseRejectors[promiseId];
        }
    };

    window.AndroidBridgePromise = {
        showAlert: function(title, content, confirmText) {
            return new Promise((resolve, reject) => {
                const promiseId = 'alert_' + Date.now() + Math.random().toString(36).substring(2);
                window._androidPromiseResolvers[promiseId] = resolve;
                window._androidPromiseRejectors[promiseId] = reject;
                AndroidBridge.showAlert(title, content, confirmText, promiseId);
            });
        },
        showPrompt: function(title, tip, defaultText, validatorJsFunction) {
            return new Promise((resolve, reject) => {
                const promiseId = 'prompt_' + Date.now() + Math.random().toString(36).substring(2);
                window._androidPromiseResolvers[promiseId] = resolve;
                window._androidPromiseRejectors[promiseId] = reject;
                AndroidBridge.showPrompt(title, tip, defaultText, validatorJsFunction, promiseId);
            });
        },
        showSingleSelection: function(title, itemsJsonString, defaultSelectedIndex) {
            return new Promise((resolve, reject) => {
                const promiseId = 'singleSelect_' + Date.now() + Math.random().toString(36).substring(2);
                window._androidPromiseResolvers[promiseId] = resolve;
                window._androidPromiseRejectors[promiseId] = reject;
                AndroidBridge.showSingleSelection(title, itemsJsonString, defaultSelectedIndex, promiseId);
            });
        },
        saveImportedCourses: function(coursesJsonString) {
            return new Promise((resolve, reject) => {
                const promiseId = 'saveCourses_' + Date.now() + Math.random().toString(36).substring(2);
                window._androidPromiseResolvers[promiseId] = resolve;
                window._androidPromiseRejectors[promiseId] = reject;
                AndroidBridge.saveImportedCourses(coursesJsonString, promiseId);
            });
        },
        saveCourseConfig: function(configJsonString) {
            return new Promise((resolve, reject) => {
                const promiseId = 'saveConfig_' + Date.now() + Math.random().toString(36).substring(2);
                window._androidPromiseResolvers[promiseId] = resolve;
                window._androidPromiseRejectors[promiseId] = reject;
                AndroidBridge.saveCourseConfig(configJsonString, promiseId);
            });
        },
        savePresetTimeSlots: function(timeSlotsJsonString) {
            return new Promise((resolve, reject) => {
                const promiseId = 'saveTimeSlots_' + Date.now() + Math.random().toString(36).substring(2);
                window._androidPromiseResolvers[promiseId] = resolve;
                window._androidPromiseRejectors[promiseId] = reject;
                AndroidBridge.savePresetTimeSlots(timeSlotsJsonString, promiseId);
            });
        }
    };
})();
""".trimIndent()

class WebImportViewModel(
    private val repository: CourseRepository,
    private val schoolRegistry: SchoolRegistry,
    private val appContainer: AppContainer,
    private val schemeIdProvider: () -> Long = { 0L }
) : ViewModel() {

    private val gson = Gson()
    private val handler = Handler(Looper.getMainLooper())
    private val visitedHosts = LinkedHashSet<String>()

    /**
     * 记录本次导入访问过的主机（由 WebViewClient.onPageStarted 回调，主线程）。
     *
     * 为什么要记：离开导入页时需要清掉教务会话，但 [android.webkit.CookieManager]
     * 是**全 App 单例**，直接 removeAllCookies 会把「服务」页内置 WebView
     * （校园卡 / 图书馆 / 办事大厅等）以及学习平台的登录态一并清掉。
     * 所以改为只清本次导入真正接触过的主机。
     */
    fun recordVisitedHost(url: String?) {
        val host = url?.let { runCatching { java.net.URI(it).host }.getOrNull() }
        if (!host.isNullOrBlank()) visitedHosts.add(host)
    }

    /**
     * 定向清除本次导入涉及主机的 cookie，替代全局 removeAllCookies。
     * 做法是给同名 cookie 写一条 Max-Age=0 的过期指令（CookieManager 没有按域删除的 API）。
     * @return 实际清理过的主机数量
     */
    fun clearVisitedCookies(): Int {
        if (visitedHosts.isEmpty()) return 0
        val cm = CookieManager.getInstance()
        var clearedHosts = 0
        visitedHosts.forEach { host ->
            var touched = false
            listOf("https", "http").forEach { scheme ->
                val origin = "$scheme://$host/"
                val cookie = runCatching { cm.getCookie(origin) }.getOrNull().orEmpty()
                if (cookie.isNotBlank()) {
                    cookie.split(";").forEach { pair ->
                        val name = pair.substringBefore("=").trim()
                        if (name.isNotEmpty()) {
                            runCatching { cm.setCookie(origin, "$name=; Max-Age=0; Path=/") }
                        }
                    }
                    touched = true
                }
            }
            if (touched) clearedHosts++
        }
        runCatching { cm.flush() }
        visitedHosts.clear()
        return clearedHosts
    }

    private val okHttpClient = OkHttpClient.Builder()

        .addInterceptor(NetworkLogInterceptor)
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .followRedirects(true)
        .followSslRedirects(true)
        .build()

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery

    /** 专用客户端：当前校区锁定的学校（可能为空）。 */
    private val campusSchool: School? = appContainer.currentCampusSchool()
    val campusOnly: Boolean get() = campusSchool != null

    private val _selectedSchool = MutableStateFlow<School?>(campusSchool)
    val selectedSchool: StateFlow<School?> = _selectedSchool

    /** Custom URL override — when non-empty, this URL is used instead of school.baseUrl. */
    private val _customUrl = MutableStateFlow("")
    val customUrl: StateFlow<String> = _customUrl

    /** Free URL mode — WebView active without a selected school. */
    private val _freeUrlActive = MutableStateFlow(false)
    val freeUrlActive: StateFlow<Boolean> = _freeUrlActive

    /** URL history for free URL mode (max 10 entries). */
    private val _urlHistory = MutableStateFlow<List<String>>(emptyList())
    val urlHistory: StateFlow<List<String>> = _urlHistory

    private var urlHistoryPrefs: SharedPreferences? = null

    val favoriteIds: StateFlow<Set<String>> = appContainer.favoriteSchoolIds

    fun toggleFavorite(school: School) {
        appContainer.toggleFavoriteSchool(school.id)
    }

    val filteredSchools: StateFlow<List<School>> = combine(_searchQuery, _selectedSchool, appContainer.customSchools, appContainer.favoriteSchoolIds) { query, _, customSchools, favIds ->
        if (campusOnly) {
            return@combine listOfNotNull(campusSchool)
        }
        val all = schoolRegistry.allSchools + customSchools
        val base = (if (query.isBlank()) all
        else all.filter {
            it.name.contains(query, ignoreCase = true) ||
            it.city.contains(query, ignoreCase = true)
        }).filter { it.isPickerSchool() }
        base.sortedWith(
            compareByDescending<School> { it.id in favIds }
                .thenBy { it.pickerRank() }
                .thenBy { com.pocket.xiaoli.util.PinyinSortUtil.sortKey(it.name) }
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _importedCount = MutableStateFlow(0)
    val importedCount: StateFlow<Int> = _importedCount

    @JvmField val _toastMessage = MutableStateFlow<String?>(null)
    val toastMessage: StateFlow<String?> = _toastMessage

    @JvmField val _isImporting = MutableStateFlow(false)
    val isImporting: StateFlow<Boolean> = _isImporting

    private val _parseLogs = MutableStateFlow<List<String>>(emptyList())
    val parseLogs: StateFlow<List<String>> = _parseLogs

    private val _detectedCount = MutableStateFlow(0)
    val detectedCount: StateFlow<Int> = _detectedCount

    // ── Remember password ──
    var username by mutableStateOf("")
    var password by mutableStateOf("")
    var isRememberChecked by mutableStateOf(true)

    // Tracks whether HTML capture should auto-save (manual import) or only detect
    private var manualImportMode = false

    /** WebView reference set from UI layer. Used by bridge methods. */
    var webView: WebView? = null

    fun updateSearch(query: String) { _searchQuery.value = query }
    fun selectSchool(school: School) {
        if (campusOnly && school.id != campusSchool?.id) return
        _selectedSchool.value = school; _freeUrlActive.value = false; _importedCount.value = 0
    }
    fun clearSchool() {
        flushAutoSave()
        cancelPendingInjections()
        _selectedSchool.value = if (campusOnly) campusSchool else null; _freeUrlActive.value = false; _importedCount.value = 0; _detectedCount.value = 0
    }
    fun setCustomUrl(url: String) { _customUrl.value = url }
    fun toastShown() { _toastMessage.value = null }

    /** Initialize URL history from SharedPreferences (call once with app context). */
    fun initUrlHistory(context: Context) {
        urlHistoryPrefs = context.getSharedPreferences("web_import_urls", Context.MODE_PRIVATE)
        val json = urlHistoryPrefs?.getString("history", null)
        if (!json.isNullOrEmpty()) {
            try {
                val type = object : TypeToken<List<String>>() {}.type
                _urlHistory.value = Gson().fromJson(json, type)
            } catch (_: Exception) {
                _urlHistory.value = emptyList()
            }
        }
    }

    /** Enter free URL mode — no school selected, load arbitrary教务 URL directly. */
    fun enterFreeUrlMode(url: String) {
        val trimmed = url.trim()
        if (trimmed.isBlank()) return
        _selectedSchool.value = null
        _freeUrlActive.value = true
        _customUrl.value = trimmed
        _importedCount.value = 0
        saveUrlToHistory(trimmed)
    }

    /** Exit free URL mode and return to school list. */
    fun exitFreeUrlMode() {
        flushAutoSave()
        cancelPendingInjections()
        _freeUrlActive.value = false
        _customUrl.value = ""
        _selectedSchool.value = null
        _importedCount.value = 0
        _detectedCount.value = 0
    }

    /** Flush any pending debounced save immediately. Called on navigation. */
    fun flushAutoSave() {
        autoSaveJob?.cancel()
        if (isRememberChecked && username.isNotBlank() && password.isNotBlank()) {
            saveCredentials()
        }
    }

    private fun saveUrlToHistory(url: String) {
        val history = _urlHistory.value.toMutableList()
        history.removeAll { it.equals(url, ignoreCase = true) }
        history.add(0, url)
        if (history.size > 10) history.subList(10, history.size).clear()
        _urlHistory.value = history
        urlHistoryPrefs?.edit()?.putString("history", Gson().toJson(history))?.apply()
    }

    /** Remove a URL from history. */
    fun removeUrlFromHistory(url: String) {
        val history = _urlHistory.value.toMutableList()
        history.removeAll { it.equals(url, ignoreCase = true) }
        _urlHistory.value = history
        urlHistoryPrefs?.edit()?.putString("history", Gson().toJson(history))?.apply()
    }

    /** Add a custom school entered by the user, persisted to SharedPreferences. */
    fun addCustomSchool(name: String, url: String, jwType: JwSystemType) {
        val trimmedUrl = url.trim().trimEnd('/')
        val school = School(
            id = "custom_${System.currentTimeMillis()}",
            name = name.trim(),
            city = "自定义",
            jwType = jwType,
            baseUrl = if (trimmedUrl.startsWith("http")) trimmedUrl else "https://$trimmedUrl",
            isV8 = true
        )
        appContainer.addCustomSchool(school)
    }

    /** The effective URL to load: customUrl > school.baseUrl. */
    fun getEffectiveUrl(): String? {
        val custom = _customUrl.value.trim()
        if (custom.isNotEmpty()) return custom
        return _selectedSchool.value?.baseUrl?.trimEnd('/')
    }

    // ═══════════════════════════════════════════
    //  Remember password — save/load encrypted credentials
    // ═══════════════════════════════════════════

    private var autoSaveJob: kotlinx.coroutines.Job? = null
    private var loadJob: kotlinx.coroutines.Job? = null
    private val pendingInjectRunnables = mutableListOf<Runnable>()

    /** Whether the current school has saved credentials in Room. */
    var hasSavedCredentials by mutableStateOf(false)

    /** Debounced save — called on every keystroke when isRememberChecked is true. */
    fun scheduleAutoSave() {
        if (!isRememberChecked) return
        autoSaveJob?.cancel()
        autoSaveJob = viewModelScope.launch {
            kotlinx.coroutines.delay(1500)
            saveCredentials()
        }
    }

    fun loadSavedAccount(targetBaseUrl: String) {
        if (targetBaseUrl.isBlank()) return
        loadJob?.cancel()
        username = ""
        password = ""
        hasSavedCredentials = false
        credentialsReady = false
        loadJob = viewModelScope.launch {
            val saved = appContainer.loadCredential(targetBaseUrl)
                ?: appContainer.loadCredential("campus_bind")
                ?: run {
                    Log.d(TAG, "未找到保存的凭证: baseUrl=$targetBaseUrl")
                    return@launch
                }
            val (user, pass) = saved
            username = user
            password = pass
            hasSavedCredentials = true
            credentialsReady = true
            Log.d(TAG, "凭证已加载（学号与密码不落日志）baseUrl=$targetBaseUrl")
            // 只在登录页面自动填入：带当前 URL 触发 isLikelyLoginPage 检查，
            // 非登录页（主页/课表页等）不注入；页面尚未加载时交由 onPageFinished 处理。
            val currentUrl = webView?.url
            if (!currentUrl.isNullOrBlank()) {
                injectCredentialsToWebView(currentUrl)
            }
        }
    }

    fun saveCredentials() {
        autoSaveJob?.cancel()
        val baseUrl = getEffectiveUrl()?.trimEnd('/') ?: return
        val uname = username.trim()
        val pwd = password
        if (uname.isBlank() || pwd.isBlank()) return
        if (!isRememberChecked) {
            appContainer.deleteCredential(baseUrl)
            hasSavedCredentials = false
            return
        }
        appContainer.saveCredential(baseUrl, uname, pwd)
        hasSavedCredentials = true
        Log.d(TAG, "凭证已保存（不落日志）baseUrl=$baseUrl")
    }

    fun clearSavedCredentials() {
        val baseUrl = getEffectiveUrl()?.trimEnd('/') ?: return
        appContainer.deleteCredential(baseUrl)
        username = ""
        password = ""
        hasSavedCredentials = false
        credentialsReady = false
    }

    /** Cancel all pending handler callbacks — call from DisposableEffect. */
    fun cancelPendingInjections() {
        pendingInjectRunnables.forEach { handler.removeCallbacks(it) }
        pendingInjectRunnables.clear()
    }

    override fun onCleared() {
        super.onCleared()
        cancelPendingInjections()
        // ⚠️ 必须清掉 WebView 引用（lint: StaticFieldLeak）。
        //
        // ViewModel 会**跨配置变更存活**（旋转屏幕等），而它持有的 WebView 绑着
        // 一个 Activity Context。不清引用的话，旧 Activity 一直被 ViewModel 拉着
        // 无法回收 —— 这是本项目里唯一一处真实的内存泄漏（lint 全量跑出来的）。
        //
        // 顺带把凭据字段也归零：这一页收的是教务密码，ViewModel 走得比界面晚，
        // 没理由让它在内存里多留一份。
        webView = null
        password = ""
    }

    private var lastInjectedUrl: String? = null
    // Set by loadSavedAccount once credentials are loaded (prevents onPageFinished from injecting stale data)
    private var credentialsReady = false

    /** Inject saved credentials into the WebView's login form via JS, with retry.
     *  Uses native value setter + multiple events to work with React/Vue/jQuery based教务 pages. */
    fun injectCredentialsToWebView(url: String? = null, retriesLeft: Int = 5) {
        val uname = username.trim()
        val pwd = password
        if (uname.isBlank() && pwd.isBlank()) return
        if (webView == null) return

        // If called from onPageFinished (url != null) but credentials haven't loaded yet,
        // skip — loadSavedAccount will inject when ready.
        if (url != null && !credentialsReady) return

        // Skip injection on non-login pages
        if (url != null && !isLikelyLoginPage(url)) return

        // Skip repeated injections on same page (onPageFinished fires multiple times for redirects)
        if (url != null && url == lastInjectedUrl) return

        Log.d(TAG, "注入凭证（不落日志）url=${url?.take(60)}")

        val escapedUname = escapeJsString(uname)
        val escapedPwd = escapeJsString(pwd)
        val js = """
            (function(){
                var nativeSetter = Object.getOwnPropertyDescriptor(window.HTMLInputElement.prototype, 'value').set;

                // fillField: three strategies for dumping value into a controlled input
                function fillField(el, val) {
                    if (!el) return false;
                    el.focus();

                    // Strategy A: native setter + input/change events (works for Vue/jQuery)
                    nativeSetter.call(el, val);
                    el.dispatchEvent(new Event('input', {bubbles: true, cancelable: true}));
                    el.dispatchEvent(new Event('change', {bubbles: true, cancelable: true}));
                    if (el.value === val) return true;

                    // Strategy B: execCommand('insertText') — the React-approved way
                    try {
                        el.select();
                        var ok = document.execCommand('insertText', false, val);
                        if (ok && el.value === val) return true;
                    } catch(e) {}   // 注入 JS：本策略失败即交给下一策略，无需上报

                    // Strategy C: character-by-character via composition events (desperate measure)
                    nativeSetter.call(el, '');
                    el.dispatchEvent(new CompositionEvent('compositionstart', {bubbles: true}));
                    el.dispatchEvent(new CompositionEvent('compositionupdate', {bubbles: true, data: val}));
                    nativeSetter.call(el, val);
                    el.dispatchEvent(new CompositionEvent('compositionend', {bubbles: true, data: val}));
                    el.dispatchEvent(new InputEvent('input', {bubbles: true, cancelable: true, inputType: 'insertText', data: val}));
                    el.dispatchEvent(new Event('change', {bubbles: true}));
                    return el.value === val;
                }

                function isVisible(el) {
                    var r = el.getBoundingClientRect();
                    var cs = window.getComputedStyle(el);
                    return r.width > 0 && r.height > 0 && cs.display !== 'none' && cs.visibility !== 'hidden';
                }

                // Heuristic: is this input likely below another element on screen?
                function isBelow(el, otherEl) {
                    try {
                        return el.getBoundingClientRect().top > otherEl.getBoundingClientRect().top;
                    } catch(e) { return false; }
                }

                function tryFillInDoc(doc) {
                    var uEl = null, pEl = null;

                    // 安全护栏：页面无密码框（type=password 或已知密码字段名）即非登录表单，
                    // 一律不填充——防止 URL 误判时把账号填进主页搜索框、成绩页页数框等。
                    var hasPwAnchor = doc.querySelector('input[type="password"]') != null;
                    if (!hasPwAnchor) {
                        var pwAnchors = ['input[name="mm"]','input[name="password"]','input[name="txtPassword"]','input[name="txtMm"]','input[name="pwd"]','input[name="passwd"]','input[name="kl"]','input[name="txtPwd"]','input[name="txtPw"]','input[name="txtKl"]','input[placeholder*="密码"]','input[placeholder*="口令"]'];
                        for (var sa = 0; sa < pwAnchors.length; sa++) {
                            var pa = doc.querySelector(pwAnchors[sa]);
                            if (pa && isVisible(pa)) { hasPwAnchor = true; break; }
                        }
                    }
                    if (!hasPwAnchor) return 0;

                    // Username selectors
                    var uSelectors = ['input[name="yhm"]','input[id="yhm"]','input[name="xh"]','input[id="xh"]','input[name="username"]','input[id="username"]','input[name="txtUserName"]','input[id="txtUserName"]','input[name="txtYhm"]','input[id="txtYhm"]','input[name="userAccount"]','input[id="userAccount"]','input[name="UID"]','input[id="UID"]','input[name="uid"]','input[id="uid"]','input[name="userid"]','input[id="userid"]','input[placeholder*="学号"]','input[placeholder*="账号"]','input[placeholder*="工号"]','input[placeholder*="用户"]'];
                    for (var s = 0; s < uSelectors.length; s++) {
                        var el = doc.querySelector(uSelectors[s]);
                        if (el && isVisible(el)) { uEl = el; break; }
                    }

                    // Password selectors
                    var pSelectors = ['input[name="mm"]','input[id="mm"]','input[name="password"]','input[id="password"]','input[name="txtPassword"]','input[id="txtPassword"]','input[name="txtMm"]','input[id="txtMm"]','input[name="pwd"]','input[id="pwd"]','input[name="passwd"]','input[id="passwd"]','input[name="kl"]','input[id="kl"]','input[name="txtPwd"]','input[id="txtPwd"]','input[name="txtPw"]','input[id="txtPw"]','input[name="txtKl"]','input[id="txtKl"]','input[placeholder*="密码"]','input[placeholder*="口令"]','input[placeholder*="Password"]'];
                    for (var ps = 0; ps < pSelectors.length; ps++) {
                        var pel = doc.querySelector(pSelectors[ps]);
                        if (pel && isVisible(pel)) { pEl = pel; break; }
                    }

                    // Fallback: collect visible inputs
                    if (!uEl || !pEl) {
                        var allInputs = doc.querySelectorAll('input:not([readonly]):not([disabled]):not([type="hidden"]):not([type="submit"]):not([type="button"]):not([type="checkbox"]):not([type="radio"]):not([type="image"])');
                        var visibleInputs = [];
                        for (var i = 0; i < allInputs.length; i++) {
                            if (isVisible(allInputs[i])) visibleInputs.push(allInputs[i]);
                        }
                        if (!uEl && visibleInputs.length >= 1) uEl = visibleInputs[0];
                        // Find a likely password field: either type=password or positioned below username
                        if (!pEl) {
                            for (var vi = 0; vi < visibleInputs.length; vi++) {
                                var inp = visibleInputs[vi];
                                if (inp.type === 'password' || (uEl && isBelow(inp, uEl))) {
                                    if (inp !== uEl) { pEl = inp; break; }
                                }
                            }
                            // Last resort: 2nd visible input
                            if (!pEl && visibleInputs.length >= 2 && visibleInputs[1] !== uEl) {
                                pEl = visibleInputs[1];
                            }
                        }
                    }

                    var uOk = false, pOk = false;
                    if (uEl) uOk = fillField(uEl, '$escapedUname');
                    if (pEl) pOk = fillField(pEl, '$escapedPwd');
                    return (uOk && pOk) ? 2 : (uOk || pOk ? 1 : 0);
                }

                var totalFilled = tryFillInDoc(document);
                var iframes = document.querySelectorAll('iframe');
                for (var f = 0; f < iframes.length; f++) {
                    try {
                        var idoc = iframes[f].contentDocument || iframes[f].contentWindow.document;
                        if (idoc) totalFilled += tryFillInDoc(idoc);
                    } catch(e) {}   // 注入 JS：本策略失败即交给下一策略，无需上报
                }
                return totalFilled >= 2 ? 'OK' : 'NO_FIELDS';
            })();
        """.trimIndent()
        webView?.evaluateJavascript(js) { result ->
            val trimmed = result.trim('"').trim()
            if (trimmed == "OK") {
                if (lastInjectedUrl != url) {
                    _toastMessage.value = "已自动填充账号"
                }
                lastInjectedUrl = url
            } else if (retriesLeft > 0) {
                val delay = if (retriesLeft > 2) 2000L else 1000L
                val r = Runnable {
                    // 页面可能已跳转（如登录后进入成绩页/课表页），重试前必须用当前 URL
                    // 重新校验是否仍在登录页，否则会把账号密码注入到非登录页的输入框
                    // （典型症状：账号被填进成绩查询的"页数框"）。
                    val currentUrl = webView?.url
                    if (currentUrl.isNullOrBlank() || !isLikelyLoginPage(currentUrl)) return@Runnable
                    injectCredentialsToWebView(currentUrl, retriesLeft - 1)
                }
                pendingInjectRunnables.add(r)
                handler.postDelayed(r, delay)
                handler.postDelayed({ pendingInjectRunnables.remove(r) }, delay)
            }
        }
    }

    /** Escape a string for safe embedding in a JS single-quoted string literal. */
    private fun escapeJsString(s: String): String {
        val sb = StringBuilder(s.length + 8)
        for (ch in s) {
            when {
                ch == '\\' -> sb.append("\\\\")
                ch == '\'' -> sb.append("\\'")
                ch == '\n' -> sb.append("\\n")
                ch == '\r' -> sb.append("\\r")
                ch == '\t' -> sb.append("\\t")
                ch == '\b' -> sb.append("\\b")
                ch.code == 0 -> sb.append("\\0")
                ch.code == 0x0b -> sb.append("\\v")
                ch.code == 0x0c -> sb.append("\\f")
                ch.code == 0x2028 -> sb.append("\\u2028")
                ch.code == 0x2029 -> sb.append("\\u2029")
                else -> sb.append(ch)
            }
        }
        return sb.toString()
    }

    /** Heuristic: is this URL likely a login/auth page? Avoids injecting on course-schedule pages. */
    private fun isLikelyLoginPage(url: String): Boolean {
        val lower = url.lowercase()
        // 登录页强特征（歧义低），命中即判为登录页——必须先于 skip 判断。
        // 正方 v8 登录页 jwglxt/xtgl/login_slogin.html 含 "xtgl"+"login"，
        // 旧版 main/login.jsp、index.jsp 等也靠此兜住。
        val loginStrong = listOf(
            "login", "logon", "signin", "sign_in", "sso", "cas", "oauth", "auth", "yhm", "userlogin"
        )
        if (loginStrong.any { it in lower }) return true
        // 明确的数据/功能页，绝不注入：课表、成绩、个人信息、登录后主页等。
        // 注意：不要包含 xtgl/main/index/default/home 等词——大量登录页 URL 含它们。
        val skipKeywords = listOf(
            "kbcx", "kcb", "kb", "schedule", "course", "grade", "score",
            "chengji", "xscj", "xsxx", "xs_main", "profile", "timetable", "table",
            "portal", "welcome", "initmenu"
        )
        if (skipKeywords.any { it in lower }) return false
        // 弱特征：仅在没有功能页特征时视为登录页
        val loginWeak = listOf("password", "passwd", "token")
        return loginWeak.any { it in lower }
    }

    private fun addLog(msg: String) {
        Log.d(TAG, msg)
        _parseLogs.value = _parseLogs.value + msg
    }

    // ═══════════════════════════════════════════
    //  Route 1: JS adapter script execution (拾光课表 pattern)
    // ═══════════════════════════════════════════

    /** Inject the AndroidBridgePromise polyfill into the WebView. Call once after page load. */
    fun injectBridgePromise() {
        webView?.evaluateJavascript(ANDROID_BRIDGE_PROMISE_JS, null)
        addLog("已注入 AndroidBridgePromise JS 桥接层")
    }

    /** Load and execute the JS adapter script for the selected school's jwType.
     *  This is the "一键导入" pattern from shiguangschedule. */
    fun executeJsAdapter() {
        viewModelScope.launch {
            if (_isImporting.value) return@launch
            _isImporting.value = true
            _parseLogs.value = emptyList()
            addLog("正在执行 JS 适配器脚本导入……")

            // Safety timeout: reset _isImporting in case the JS adapter never calls back
            val timeoutJob = launch {
                delay(15_000)
                if (_isImporting.value) {
                    addLog("导入超时（15秒），已重置导入状态")
                    _toastMessage.value = "导入超时，请确认已进入课表页面后重试"
                    _isImporting.value = false
                }
            }

            try {
                val jwType = _selectedSchool.value?.jwType
                val scriptName = if (jwType != null) {
                    when (jwType) {
                        JwSystemType.ZHENGFANG -> "zhengfang_01.js"
                        // Qiangzhi systems share the same HTML structure as Zhengfang;
                        // no dedicated qiangzhi adapter exists, so fall back to zhengfang_01.js
                        JwSystemType.QIANGZHI -> {
                            addLog("强智教务无专用适配器，使用正方适配器作为兼容方案")
                            "zhengfang_01.js"
                        }
                        JwSystemType.QINGGUO -> "qingguo_jiaowu_qingguo_01.js"
                        JwSystemType.CHAOXING -> "chaoxing_jiaowu_chaoxing.js"
                        JwSystemType.URP -> "urp_jiaowu_urp_01.js"
                    }
                } else {
                    // Free URL mode — try all adapters, starting with zhengfang (most common)
                    addLog("通用导入模式: 未指定教务类型，使用正方适配器尝试")
                    "zhengfang_01.js"
                }
                val scriptPath = "adapters/scripts/$scriptName"
                addLog("加载适配器脚本: $scriptPath")

                val scriptContent = withContext(Dispatchers.IO) {
                    try {
                        val ctx = webView?.context ?: throw Exception("WebView context unavailable")
                        ctx.assets.open(scriptPath).bufferedReader().use { it.readText() }
                    } catch (e: Exception) {
                        addLog("JS适配器脚本加载失败: ${e.message}，回退到 HTML 解析")
                        null
                    }
                }

                if (scriptContent != null) {
                    addLog("适配器脚本已加载 (${scriptContent.length} 字符)，开始执行")
                    // Execute on main thread via WebView
                    withContext(Dispatchers.Main) {
                        webView?.evaluateJavascript(scriptContent) { result ->
                            addLog("适配器脚本执行完成: ${result ?: "无返回值"}")
                        }
                    }
                } else {
                    // Fallback: capture HTML and parse with Jsoup
                    withContext(Dispatchers.Main) {
                        webView?.evaluateJavascript(
                            "(function(){AndroidBridge.captureHtml(document.documentElement.outerHTML);})();",
                            null
                        )
                    }
                }
            } catch (e: Exception) {
                addLog("JS适配器执行失败: ${e.message}")
                _toastMessage.value = "导入失败：${e.message}"
                _isImporting.value = false
                timeoutJob.cancel()
            }
        }
    }

    // ═══════════════════════════════════════════
    //  Route 1b: OkHttp 直接抓取课表页面 (使用 WebView 提取的 Cookie)
    //  对应优化方案中的"WebView 登录 → Cookie → OkHttp 抓取"混合方案
    // ═══════════════════════════════════════════

    /** 使用 WebView 中提取的 Cookie，通过 OkHttp 直接抓取当前页面 HTML 并解析。
     *  比 WebView 内 JS 抓取更快更可靠，适合 JS 适配器失败时的回退方案。 */
    fun fetchCourseViaOkHttp(currentPageUrl: String) {
        viewModelScope.launch {
            if (_isImporting.value) return@launch
            _isImporting.value = true
            _parseLogs.value = emptyList()
            addLog("OkHttp: 尝试直接抓取课表页面……")

            try {
                val cookies = CookieStore.get()
                if (cookies.isNullOrEmpty()) {
                    addLog("OkHttp: 未找到已保存的 Cookie，请先在 WebView 中登录")
                    _toastMessage.value = "请先在教务页面中登录"
                    _isImporting.value = false
                    return@launch
                }
                addLog("OkHttp: 使用已保存的 Cookie (${cookies.length} 字符)")

                val url = currentPageUrl.ifBlank {
                    _selectedSchool.value?.baseUrl?.trimEnd('/') ?: ""
                }
                if (url.isBlank()) {
                    addLog("OkHttp: 无有效 URL")
                    _isImporting.value = false
                    return@launch
                }
                addLog("OkHttp: 请求 $url")

                val request = Request.Builder()
                    .url(url)
                    .addHeader("Cookie", cookies)
                    .addHeader("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
                    .build()

                val html = withContext(Dispatchers.IO) {
                    okHttpClient.newCall(request).execute().use { response ->
                        if (!response.isSuccessful) {
                            throw Exception("HTTP ${response.code}: ${response.message}")
                        }
                        response.body?.string() ?: throw Exception("响应体为空")
                    }
                }
                addLog("OkHttp: 成功获取页面 (${html.length} 字符)")

                val jwType = _selectedSchool.value?.jwType
                addLog("OkHttp: 教务系统类型: ${jwType?.label ?: "未知"}")
                val courses = withContext(Dispatchers.IO) { parseHtmlToCourses(html, jwType) }
                addLog("OkHttp: 解析完成，共识别 ${courses.size} 门课程")

                if (courses.isNotEmpty()) {
                    courses.forEachIndexed { i, c ->
                        addLog("  ${i + 1}. ${c.name} | ${c.teacher} | ${c.room} | 周${c.dayOfWeek} 第${c.startSlot}-${c.endSlot}节 | 周次=${c.weeks}")
                    }
                    val schemeId = schemeIdProvider()
                    val coursesWithScheme = courses.map { it.copy(schemeId = schemeId) }
                    val inserted = withContext(Dispatchers.IO) { insertCourses(coursesWithScheme) }
                    _importedCount.value = inserted
                    _detectedCount.value = 0
                    addLog("OkHttp: 成功导入 $inserted 门课程")
                    saveCredentials()
                } else {
                    addLog("OkHttp: 未检测到课表数据")
                    _toastMessage.value = "未检测到课表，请确认已进入课表页面"
                }
            } catch (e: Exception) {
                addLog("OkHttp 抓取失败: ${e.message}")
                _toastMessage.value = "抓取失败：${e.message}"
            } finally {
                _isImporting.value = false
            }
        }
    }

    // ═══════════════════════════════════════════
    //  Route 2: HTML capture + Jsoup parsing (fallback / "识别" flow)
    // ═══════════════════════════════════════════

    /** Called from JS bridge when HTML is captured. Routes to detect or manual-import based on mode. */
    fun onHtmlCaptured(html: String) {
        if (manualImportMode) {
            manualImportMode = false
            manualImportFromHtml(html)
        } else {
            detectFromHtml(html)
        }
    }

    /** Parse HTML and save directly (manual import flow). */
    private fun manualImportFromHtml(html: String) {
        viewModelScope.launch {
            try {
                addLog("手动导入: 开始解析 HTML (${html.length} 字符)")
                val jwType = _selectedSchool.value?.jwType
                addLog("手动导入: 教务系统类型: ${jwType?.label ?: "未知"}")
                val courses = withContext(Dispatchers.IO) { parseHtmlToCourses(html, jwType) }
                addLog("手动导入: 解析完成，共识别 ${courses.size} 门课程")
                if (courses.isNotEmpty()) {
                    courses.forEachIndexed { i, c ->
                        addLog("  ${i + 1}. ${c.name} | ${c.teacher} | ${c.room} | 周${c.dayOfWeek} 第${c.startSlot}-${c.endSlot}节 | 周次=${c.weeks}")
                    }
                    val schemeId = schemeIdProvider()
                    val coursesWithScheme = courses.map { it.copy(schemeId = schemeId) }
                    val inserted = withContext(Dispatchers.IO) { insertCourses(coursesWithScheme) }
                    _importedCount.value = inserted
                    _detectedCount.value = 0
                    addLog("手动导入: 成功导入 $inserted 门课程")
                    saveCredentials()
                } else {
                    _toastMessage.value = "未检测到课表，请确认已进入课表页面"
                }
            } catch (e: Exception) {
                addLog("手动导入失败: ${e.message}")
                _toastMessage.value = "导入失败：${e.message}"
            } finally {
                _isImporting.value = false
            }
        }
    }

    /** Start manual import: capture HTML then parse + save. */
    fun startManualImport() {
        if (_isImporting.value) return
        _isImporting.value = true
        _importedCount.value = 0
        _parseLogs.value = emptyList()
        manualImportMode = true
        addLog("手动导入: 捕获页面HTML……")
    }

    /** Parse-only detection (used as fallback when JS adapter fails). */
    private fun detectFromHtml(html: String) {
        viewModelScope.launch {
            _parseLogs.value = emptyList()
            _detectedCount.value = 0
            try {
                addLog("识别: 开始解析 HTML (${html.length} 字符)")
                val jwType = _selectedSchool.value?.jwType
                addLog("识别: 教务系统类型: ${jwType?.label ?: "未知"}")
                val courses = withContext(Dispatchers.IO) { parseHtmlToCourses(html, jwType) }
                addLog("识别: 解析完成，共识别 ${courses.size} 门课程")
                _detectedCount.value = courses.size
                if (courses.isNotEmpty()) {
                    courses.forEachIndexed { i, c ->
                        addLog("  ${i + 1}. ${c.name} | ${c.teacher} | ${c.room} | 周${c.dayOfWeek} 第${c.startSlot}-${c.endSlot}节 | 周次=${c.weeks}")
                    }
                    _toastMessage.value = "识别到 ${courses.size} 门课程，点击手动导入课表保存"
                } else {
                    _toastMessage.value = "未检测到课表，请确认已进入课表页面"
                }
            } catch (e: Exception) {
                addLog("识别失败: ${e.message}")
                _toastMessage.value = "识别失败：${e.message}"
            } finally {
                _isImporting.value = false
            }
        }
    }

    /** Import courses from JSON (called by JS bridge saveImportedCourses). */
    fun importFromJson(coursesJson: String) {
        viewModelScope.launch {
            try {
                addLog("JS桥接: 收到课程JSON (${coursesJson.length} 字符)")
                val type = object : TypeToken<List<ImportCourseJson>>() {}.type
                val importedCourses: List<ImportCourseJson> = gson.fromJson(coursesJson, type)
                addLog("JS桥接: 解析到 ${importedCourses.size} 门课程")

                val courses = importedCourses.mapNotNull { convertImportCourse(it) }
                if (courses.isEmpty()) {
                    addLog("JS桥接: 转换后无有效课程 (原始=${importedCourses.size}门)")
                    _toastMessage.value = "未识别到有效课程数据"
                    _isImporting.value = false
                    return@launch
                }

                addLog("JS桥接: 转换成功 ${courses.size} 门课程，写入数据库")
                val inserted = withContext(Dispatchers.IO) { insertCourses(courses) }
                _importedCount.value = inserted
                _detectedCount.value = 0
                addLog("JS桥接: 成功导入 $inserted 门课程")
                saveCredentials()
            } catch (e: Exception) {
                val detail = e.stackTraceToString()
                addLog("JS桥接: 导入异常: $detail")
                _toastMessage.value = "导入失败：${e.message ?: e.javaClass.simpleName}"
            } finally {
                _isImporting.value = false
            }
        }
    }

    /** Convert shiguangschedule-format ImportCourseJson to our CourseEntity.
     *  All field accesses are null-guarded because Gson can bypass Kotlin null-safety. */
    private fun convertImportCourse(json: ImportCourseJson): CourseEntity? {
        val name = json.name?.trim() ?: return null
        if (name.isEmpty() || name.length < 2) return null
        val day = json.day.takeIf { it in 1..7 } ?: return null

        val startSlot = json.startSection ?: 1
        val endSlot = json.endSection ?: startSlot
        val weeksList = json.weeks ?: emptyList()
        val weeks = weeksList.sorted().joinToString(",") { it.toString() }
        val weekType = computeWeekType(parseWeeks(weeks))

        return CourseEntity(
            name = name,
            teacher = (json.teacher ?: "").trim(),
            room = (json.position ?: "").trim(),
            dayOfWeek = day,
            startSlot = startSlot,
            endSlot = endSlot,
            weeks = weeks,
            weekType = weekType,
            colorIndex = (day * 3 + startSlot) % 15,
            schemeId = schemeIdProvider()
        )
    }

    private suspend fun insertCourses(courses: List<CourseEntity>): Int {
        if (courses.isEmpty()) return 0
        val schemeId = courses.first().schemeId
        val existing = repository.getCoursesByScheme(schemeId).first()
        val newCourses = courses.filter { new ->
            existing.none { it.name == new.name && it.dayOfWeek == new.dayOfWeek && it.startSlot == new.startSlot }
        }
        if (newCourses.isNotEmpty()) {
            repository.insertAll(newCourses)
            appContainer.recordImport("网页导入", newCourses.size, true)
            // Web 导入不携带学期信息 → 若用户未设过开学日期，课表周次会对不上；
            // 标记后由课表页弹出「学期周次校准」引导
            if (appContainer.semesterStart.value.isBlank()) {
                appContainer.requestSemesterCalibration()
            }
        }
        return newCourses.size
    }

    // ═══════════════════════════════════════════
    //  HTML → Jsoup parsers (fallback when JS adapters unavailable)
    // ═══════════════════════════════════════════

    private fun parseHtmlToCourses(html: String, jwType: JwSystemType?): List<CourseEntity> {
        val doc: Document = Jsoup.parse(html)
        return when (jwType) {
            JwSystemType.ZHENGFANG -> {
                addLog("使用正方教务解析策略")
                parseZhengfang(doc)
            }
            JwSystemType.QIANGZHI -> {
                addLog("使用强智教务解析策略")
                parseQiangzhi(doc)
            }
            JwSystemType.QINGGUO -> {
                addLog("使用青果教务解析策略")
                parseQingguo(doc)
            }
            JwSystemType.CHAOXING -> {
                addLog("使用超星教务解析策略")
                parseChaoxing(doc)
            }
            JwSystemType.URP -> {
                addLog("使用URP教务解析策略")
                parseURP(doc)
            }
            null -> {
                addLog("未指定教务类型，尝试自动检测")
                autoDetectAndParse(doc)
            }
        }
    }

    private fun autoDetectAndParse(doc: Document): List<CourseEntity> {
        val strategies = listOf(
            "正方教务" to { parseZhengfang(doc) },
            "强智教务" to { parseQiangzhi(doc) },
            "青果教务" to { parseQingguo(doc) },
            "超星教务" to { parseChaoxing(doc) },
            "URP教务" to { parseURP(doc) }
        )
        var best = emptyList<CourseEntity>()
        for ((name, parser) in strategies) {
            val result = parser()
            addLog("  $name 检测到 ${result.size} 门课程")
            if (result.size > best.size) best = result
        }
        if (best.isEmpty()) {
            addLog("  尝试通用解析……")
            best = parseGeneric(doc)
            addLog("  通用解析检测到 ${best.size} 门课程")
        }
        return best
    }

    // ── Zhengfang parser ──

    private fun parseZhengfang(doc: Document): List<CourseEntity> {
        val colors = listOf(0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11)

        val table = doc.select("table#Table1").firstOrNull()
            ?: doc.select("table[id*=Table1]").firstOrNull()
            ?: doc.select("table[id*=kbcx]").firstOrNull()
            ?: doc.select("table[id*=kb]").firstOrNull()
            ?: doc.select("table.datelist").firstOrNull()
            ?: doc.select("table.table").firstOrNull()
            ?: doc.select("table").firstOrNull()
            ?: return emptyList()

        addLog("正方: 找到课表表格 (class=${table.className()})")

        val allRows = table.select("tr")
        if (allRows.size < 2) return emptyList()

        val (dayMap, headerRowIdx) = buildDayMap(allRows.take(4))
        addLog("正方: dayMap cols=$dayMap, headerRow=$headerRowIdx")
        if (dayMap.size < 3) addLog("正方: 星期头不足，使用默认列映射 col1=周一..col7=周日")

        val colOffset = if (dayMap.containsKey(0)) 1 else 0
        val (bodyRows, rowSlotRanges) = filterBodyRows(allRows, headerRowIdx)
        if (bodyRows.isEmpty()) return emptyList()

        val isVertical = detectVerticalLayout(bodyRows)
        addLog("正方: ${if (isVertical) "竖排" else "横排"}布局, bodyRows=${bodyRows.size}")

        val courses = if (isVertical) parseVerticalTable(bodyRows, dayMap, colors)
        else parseHorizontalTable(bodyRows, dayMap, colOffset, colors, rowSlotRanges)

        addLog("正方: 解析到 ${courses.size} 门课程")
        return courses
    }

    private fun parseQiangzhi(doc: Document): List<CourseEntity> {
        val colors = listOf(0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11)

        val table = doc.select("table#kbtable").firstOrNull()
            ?: doc.select("table[id*=kbtable]").firstOrNull()
            ?: doc.select("table#gridview").firstOrNull()
            ?: doc.select("table.grid").firstOrNull()
            ?: doc.select("table").firstOrNull()
            ?: return emptyList()

        addLog("强智: 找到课表表格")

        val allRows = table.select("tr")
        if (allRows.size < 2) return emptyList()

        val dayMap = buildDayMap(listOf(allRows[0])).first.toMutableMap()
        if (dayMap.isEmpty()) { for (i in 1..7) dayMap[i] = i; addLog("强智: 未找到星期头，使用默认列映射") }

        val colOffset = if (dayMap.containsKey(0)) 1 else 0
        val rowsMinusHeader = allRows.drop(1)
        val (bodyRows, rowSlotRanges) = filterBodyRows(rowsMinusHeader, -1)

        val courses = parseHorizontalTable(bodyRows, dayMap, colOffset, colors, rowSlotRanges)
        addLog("强智: 解析到 ${courses.size} 门课程")
        return courses
    }

    // ── Qingguo parser ──

    private fun parseQingguo(doc: Document): List<CourseEntity> {
        val courses = mutableListOf<CourseEntity>()
        val colors = listOf(0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11)

        val tables = doc.select("table")
        for (table in tables) {
            if (table.select("tr").size < 3) continue
            for (cell in table.select("td")) {
                val text = cell.text().trim()
                if (text.length < 4) continue
                if (Regex("""^[\d\s.,;:：.，、；：]+$""").matches(text)) continue

                val textLines = parseBrOrNewlineElements(cell)
                if (textLines.size < 2) continue
                parseGenericCourse(textLines, text, colors, courses)
            }
            if (courses.isNotEmpty()) break
        }
        addLog("青果: 解析到 ${courses.size} 门课程")
        return courses
    }

    // ── URP parser ──

    private fun parseURP(doc: Document): List<CourseEntity> {
        val colors = listOf(0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11)

        val table = doc.select("table#Table1").firstOrNull()
            ?: doc.select("table#ctl00_ContentPlaceHolder1_Table1").firstOrNull()
            ?: doc.select("table#ctl00_ContentPlaceHolder1_gridView").firstOrNull()
            ?: doc.select("table.datelist").firstOrNull()
            ?: doc.select("table").firstOrNull()
            ?: return emptyList()

        addLog("URP: 找到课表表格")

        val allRows = table.select("tr")
        if (allRows.size < 2) return emptyList()

        val dayMap = buildDayMap(listOf(allRows[0])).first.toMutableMap()
        if (dayMap.isEmpty()) for (i in 1..7) dayMap[i] = i

        val colOffset = if (dayMap.containsKey(0)) 1 else 0
        val rowsMinusHeader = allRows.drop(1)
        val (bodyRows, rowSlotRanges) = filterBodyRows(rowsMinusHeader, -1)

        val courses = parseHorizontalTable(bodyRows, dayMap, colOffset, colors, rowSlotRanges)
        addLog("URP: 解析到 ${courses.size} 门课程")
        return courses
    }

    // ── Chaoxing parser ──

    private fun parseChaoxing(doc: Document): List<CourseEntity> {
        val courses = mutableListOf<CourseEntity>()
        val colors = listOf(0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11)

        val courseCards = doc.select("[class*=course], [class*=kc], [class*=schedule]")
        for (card in courseCards) {
            val text = card.text().trim()
            if (text.length < 4) continue
            val textLines = parseBrOrNewlineElements(card)
            if (textLines.size >= 2) parseGenericCourse(textLines, text, colors, courses)
        }

        if (courses.isEmpty()) courses.addAll(parseGeneric(doc))

        addLog("超星: 解析到 ${courses.size} 门课程")
        return courses
    }

    // ── Generic fallback ──

    private fun parseGeneric(doc: Document): List<CourseEntity> {
        val courses = mutableListOf<CourseEntity>()
        val colors = listOf(0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11)

        for (td in doc.select("td")) {
            val text = td.text().trim()
            if (text.length < 4) continue
            if (Regex("""^[\d\s.,;:：.\-/]+$""").matches(text)) continue

            val textLines = parseBrOrNewlineElements(td)
            if (textLines.size >= 2) parseGenericCourse(textLines, text, colors, courses)
        }
        return courses
    }

    private fun parseGenericCourse(
        lines: List<String>, fullText: String, colors: List<Int>, courses: MutableList<CourseEntity>
    ) {
        val (name, teacher, room, weeksRaw) = extractCourseFields(lines)
        if (name.isEmpty() || name.length < 2) return

        val wm = Regex("""(\d+)\s*[-–]\s*(\d+)\s*周""").find(fullText)
        val dm = Regex("""周([一二三四五六日])""").find(fullText)
        val sm = Regex("""第(\d+)[\s-]*(\d+)?节""").find(fullText)

        val day = when (dm?.groupValues?.get(1)) {
            "一" -> 1; "二" -> 2; "三" -> 3; "四" -> 4; "五" -> 5; "六" -> 6; "日" -> 7; else -> return
        }
        val startSlot = sm?.groupValues?.get(1)?.toIntOrNull() ?: return
        val endSlot = sm?.groupValues?.get(2)?.toIntOrNull() ?: startSlot
        val weeks = if (wm != null) {
            val ws = wm.groupValues[1].toIntOrNull() ?: 1
            val we = wm.groupValues[2].toIntOrNull() ?: 16
            (ws..we).joinToString(",")
        } else {
            parseWeeksToString(weeksRaw)
        }

        courses.add(CourseEntity(
            name = name, teacher = teacher, room = room,
            dayOfWeek = day, startSlot = startSlot, endSlot = endSlot,
            weeks = weeks, colorIndex = colors[courses.size % colors.size]
        ))
    }

    class Factory(
        private val repository: CourseRepository,
        private val schoolRegistry: SchoolRegistry,
        private val appContainer: AppContainer,
        private val schemeIdProvider: () -> Long = { 0L }
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T =
            WebImportViewModel(repository, schoolRegistry, appContainer, schemeIdProvider) as T
    }
}

// ═══════════════════════════════════════════
//  Enhanced ScheduleBridge — full JS-native bridge
//  Implements the shiguangschedule AndroidBridge pattern for two-way communication
// ═══════════════════════════════════════════

/** Bridge injected as "AndroidBridge" in WebView.
 *  Supports both simple HTML capture AND the full AndroidBridgePromise pattern. */
class ScheduleBridge(
    private val viewModel: WebImportViewModel? = null,
    private val onHtmlCaptured: ((String) -> Unit)? = null
) {
    private val handler = Handler(Looper.getMainLooper())
    private val visitedHosts = LinkedHashSet<String>()

    /**
     * 记录本次导入访问过的主机（由 WebViewClient.onPageStarted 回调，主线程）。
     *
     * 为什么要记：离开导入页时需要清掉教务会话，但 [android.webkit.CookieManager]
     * 是**全 App 单例**，直接 removeAllCookies 会把「服务」页内置 WebView
     * （校园卡 / 图书馆 / 办事大厅等）以及学习平台的登录态一并清掉。
     * 所以改为只清本次导入真正接触过的主机。
     */
    fun recordVisitedHost(url: String?) {
        val host = url?.let { runCatching { java.net.URI(it).host }.getOrNull() }
        if (!host.isNullOrBlank()) visitedHosts.add(host)
    }

    /**
     * 定向清除本次导入涉及主机的 cookie，替代全局 removeAllCookies。
     * 做法是给同名 cookie 写一条 Max-Age=0 的过期指令（CookieManager 没有按域删除的 API）。
     * @return 实际清理过的主机数量
     */
    fun clearVisitedCookies(): Int {
        if (visitedHosts.isEmpty()) return 0
        val cm = CookieManager.getInstance()
        var clearedHosts = 0
        visitedHosts.forEach { host ->
            var touched = false
            listOf("https", "http").forEach { scheme ->
                val origin = "$scheme://$host/"
                val cookie = runCatching { cm.getCookie(origin) }.getOrNull().orEmpty()
                if (cookie.isNotBlank()) {
                    cookie.split(";").forEach { pair ->
                        val name = pair.substringBefore("=").trim()
                        if (name.isNotEmpty()) {
                            runCatching { cm.setCookie(origin, "$name=; Max-Age=0; Path=/") }
                        }
                    }
                    touched = true
                }
            }
            if (touched) clearedHosts++
        }
        runCatching { cm.flush() }
        visitedHosts.clear()
        return clearedHosts
    }

    /** Simple HTML capture (legacy "识别" flow). */
    @JavascriptInterface
    fun captureHtml(html: String) {
        handler.post {
            onHtmlCaptured?.invoke(html)
        }
    }

    /** Show a toast from JS. */
    @JavascriptInterface
    fun showToast(message: String) {
        handler.post {
            viewModel?._toastMessage?.value = message
        }
    }

    /** Show alert dialog — resolves via promise. */
    @JavascriptInterface
    fun showAlert(titleText: String, contentText: String, confirmText: String, promiseId: String) {
        handler.post {
            // Auto-confirm in import context — user already chose to import
            resolveJsPromise(promiseId, "true")
        }
    }

    /** Show prompt dialog — auto-resolves with empty (cancel). */
    @JavascriptInterface
    fun showPrompt(
        titleText: String, tipText: String, defaultText: String,
        validatorJsFunction: String, promiseId: String
    ) {
        handler.post {
            resolveJsPromise(promiseId, "null")
        }
    }

    /** Show single selection — auto-resolves with -1 (cancel). */
    @JavascriptInterface
    fun showSingleSelection(
        titleText: String, itemsJsonString: String,
        defaultSelectedIndex: Int, promiseId: String
    ) {
        handler.post {
            resolveJsPromise(promiseId, "null")
        }
    }

    /** Save courses from JS adapter — the key bridge method. */
    @JavascriptInterface
    fun saveImportedCourses(coursesJsonString: String, promiseId: String) {
        Log.d(TAG, "Bridge: 收到 JS 课程数据 (${coursesJsonString.length} 字符)")
        handler.post {
            viewModel?.let { vm ->
                vm.importFromJson(coursesJsonString)
                // mark importing done after importFromJson processes
            }
            resolveJsPromise(promiseId, "true")
        }
    }

    @JavascriptInterface
    fun saveCourseConfig(configJsonString: String, promiseId: String) {
        handler.post {
            resolveJsPromise(promiseId, "true")
        }
    }

    @JavascriptInterface
    fun savePresetTimeSlots(timeSlotsJsonString: String, promiseId: String) {
        handler.post {
            resolveJsPromise(promiseId, "true")
        }
    }

    @JavascriptInterface
    fun notifyTaskCompletion() {
        // Delay reset so importFromJson has time to complete DB writes first.
        // If importFromJson was never called, this is the sole reset path.
        handler.postDelayed({
            viewModel?._isImporting?.value = false
        }, 2000)
    }

    private fun resolveJsPromise(promiseId: String, result: String) {
        viewModel?.webView?.evaluateJavascript(
            "window._resolveAndroidPromise('$promiseId', $result);", null
        )
    }
}
