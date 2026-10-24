package com.pocket.xiaoli.ui.update

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Download
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.pocket.xiaoli.data.announcement.AppFeedRepository
import com.pocket.xiaoli.network.ApkArtifact
import com.pocket.xiaoli.network.ApkDownloader
import com.pocket.xiaoli.network.ApkInstaller
import com.pocket.xiaoli.network.ApkMirrors
import com.pocket.xiaoli.network.AppUpdateChecker
import com.pocket.xiaoli.network.DownloadProgressText
import com.pocket.xiaoli.network.NetworkLogStore
import com.pocket.xiaoli.telemetry.Telemetry
import com.pocket.xiaoli.telemetry.TelemetryEvent
import com.pocket.xiaoli.ui.components.SheetBackToDismiss
import com.pocket.xiaoli.ui.theme.LocalPocketXiaoliColors
import com.pocket.xiaoli.ui.theme.accentColor
import com.pocket.xiaoli.util.AppVersion
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import top.yukonga.miuix.kmp.window.WindowBottomSheet
import java.io.File

/**
 * 更新抽屉要展示的一份快照。
 *
 * 与 [AppFeedRepository.State] 分开，是因为「手动检查」还有一条老路
 *（`releases/latest`）拿不到 feed 的 notes / sha256 / 灰度信息；
 * 抽屉只认这份结构，两条来源就能共用同一个界面。
 */
internal data class AppUpdateInfo(
    val versionName: String,
    val badge: String,
    val notes: List<String>,
    val apkUrl: String,
    val sha256: String?,
    val force: Boolean,
    val message: String? = null,
) {
    val title: String get() = when {
        force -> "必须更新到 v$versionName$badge"
        versionName.isNotBlank() -> "发现新版本 v$versionName$badge"
        else -> "发现新版本"
    }
}

/**
 * 更新抽屉的开关与「什么时候该自动弹」。
 *
 * ## 为什么提到导航层
 *
 * 以前这套东西住在 `ProfileDialogHost` 里，只有「我的 → 设置 → 检查更新」能拉开它 ——
 * 更新发出来了，用户不主动去点就永远不知道。放到 [AppNavigation] 这一层之后，
 * **任何 tab 上都能弹**，手动检查与自动提示也共用同一份界面和下载逻辑。
 *
 * ## 自动弹的节流
 *
 * - 普通更新：**每次打开 App 主动提一次**（退到后台再回来也算一次新的打开）；
 *   同一次前台会话里关掉后不再重复，留红点和消息页。
 * - 强制更新：不节流也不许关（[AppUpdateInfo.force]），因为旧版本已经不受支持。
 *
 * 公开版本号（`versionCode == 0`）不会弹 —— 那是 feed 被灰度压掉 / 数据不完整的状态。
 */
internal class AppUpdatePromptController(
    context: Context,
    private val repository: AppFeedRepository,
    private val checker: AppUpdateChecker,
) {
    private val appContext = context.applicationContext

    var info by mutableStateOf<AppUpdateInfo?>(null)
        private set

    /** 手动检查进行中：防止连点把同一个请求发两遍 */
    var checking by mutableStateOf(false)
        private set

    /** 本次前台会话里已经主动弹过的版本 —— 状态反复刷新也不重复弹；重新打开 App 后再弹 */
    private var promptedThisRun = 0

    /**
     * feed 状态每次变化都来过一遍，够条件才开抽屉。
     *
     * 调用方只在**一级 Tab** 上调用：更新提示不该盖住专注计时、网页登录、导入向导这些
     * 正在办事的页面。
     */
    fun autoShow(state: AppFeedRepository.State) {
        if (info != null || checking || !state.loaded) return
        if (!state.updateAvailable && !state.forceUpdate) return
        // 有必须确认的公告时先让公告说话，关掉后状态还会再来一次
        if (state.blocking != null) return
        val code = state.latestVersionCode
        if (code <= 0) return
        if (!state.forceUpdate && code <= promptedThisRun) return
        promptedThisRun = code
        info = state.toInfo()
    }

    /** 关掉抽屉。普通更新只影响这次前台会话，下次打开 App 仍会主动提。 */
    fun dismiss() {
        info = null
    }

    /**
     * App 退到后台。下次回到前台时普通更新会重新主动弹一次 ——
     * 用户要的是「每次打开都弹」，而不是一个版本只提醒一次。
     */
    fun onBackground() {
        promptedThisRun = 0
    }

    /**
     * 「检查更新」：只查、不弹别的界面，查到新版才开抽屉。
     *
     * 首选 feed（同时带 notes / sha256）；feed 还没部署时退回 `releases/latest`，
     * 这条老路不能删 —— 两个仓都读不到 feed 的时候它是唯一能查到新版的路。
     */
    fun checkNow(scope: CoroutineScope) {
        if (checking) return
        checking = true
        val currentVersionName = AppVersion.versionName(appContext)
        scope.launch {
            try {
                val feedState = withContext(Dispatchers.IO) {
                    repository.refresh(force = true)
                }
                if (feedState.loaded) {
                    // 「有新版、但被『稍后提醒』压住」也要算有新版，否则用户会看到
                    // 「已是最新版本」，而版本号明明比他的新。
                    val snoozedNewer = feedState.latestVersionCode > AppVersion.versionCode(appContext)
                    when {
                        feedState.forceUpdate -> info = feedState.toInfo(
                            message = "此版本已不受支持，请更新到 v${feedState.latestVersionName}${feedState.updateBadge}",
                        )
                        feedState.updateAvailable -> info = feedState.toInfo()
                        snoozedNewer -> info = feedState.toInfo(
                            message = "有新版 v${feedState.latestVersionName}${feedState.updateBadge}（上次你选了稍后提醒）",
                        )
                        else -> {
                            // 缓存还在、但这一轮没联上：不能报「已是最新」
                            val failure = feedState.error
                                ?.let { "检查失败：$it（显示的是上次结果）" }
                            Toast.makeText(
                                appContext,
                                failure ?: "已是最新版本 v$currentVersionName",
                                if (failure != null) Toast.LENGTH_LONG else Toast.LENGTH_SHORT,
                            ).show()
                        }
                    }
                } else {
                    val result = withContext(Dispatchers.IO) {
                        // feed 这一轮刚在仓库层并行试过四个镜像，这里不再重拉
                        checker.checkLatest(currentVersion = currentVersionName, skipFeed = true)
                    }
                    when (result) {
                        is AppUpdateChecker.Result.Latest -> info = AppUpdateInfo(
                            versionName = result.version,
                            badge = "",
                            notes = emptyList(),
                            apkUrl = result.url,
                            sha256 = null,
                            force = false,
                        )
                        AppUpdateChecker.Result.UpToDate -> Toast.makeText(
                            appContext,
                            "已是最新版本 v$currentVersionName",
                            Toast.LENGTH_SHORT,
                        ).show()
                        is AppUpdateChecker.Result.Failed -> Toast.makeText(
                            appContext,
                            result.message,
                            Toast.LENGTH_LONG,
                        ).show()
                    }
                }
            } catch (t: Throwable) {
                val detail = t.message?.trim()?.take(180)
                val text = buildString {
                    append("检查失败：")
                    append(t.javaClass.simpleName)
                    if (!detail.isNullOrBlank()) {
                        append("（")
                        append(detail)
                        append("）")
                    }
                }
                // 请求根本没发出去时，网络日志里也要留一条可查的痕迹
                runCatching {
                    NetworkLogStore.record(
                        method = "CHECK",
                        url = "app://update-check",
                        status = null,
                        durationMs = 0L,
                        requestBytes = -1L,
                        responseBytes = -1L,
                        error = "${t.javaClass.name}: ${t.message.orEmpty()}",
                    )
                }
                Toast.makeText(appContext, text, Toast.LENGTH_LONG).show()
            } finally {
                checking = false
            }
        }
    }
}

private fun AppFeedRepository.State.toInfo(message: String? = null) = AppUpdateInfo(
    versionName = latestVersionName,
    badge = updateBadge,
    notes = notes,
    apkUrl = apkUrl,
    sha256 = sha256.takeIf { it.isNotBlank() },
    force = forceUpdate,
    message = message,
)

/**
 * 更新抽屉本体：全局只挂这一份，谁触发都走这里。
 *
 * 外壳是 MIUIX 的 [WindowBottomSheet]（自带窗口、圆角、下滑关闭），高度只包住内容。
 * 强更时 `allowDismiss = false`：点外面和下滑都关不掉，也没有「稍后」按钮 ——
 * 当前版本已经不受支持，留一个假出口只会让用户带着坏版本继续用。
 */
@Composable
internal fun AppUpdateSheetHost(controller: AppUpdatePromptController) {
    val info = controller.info ?: return
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var downloading by remember(info) { mutableStateOf(false) }
    var progress by remember(info) { mutableIntStateOf(0) }
    var label by remember(info) { mutableStateOf<String?>(null) }
    var message by remember(info) { mutableStateOf(info.message) }
    // 去「安装未知应用」授权时先把包记下来，回到前台自动接着装
    var pendingApk by remember(info) { mutableStateOf<File?>(null) }

    // 抽屉真的显示出来才记一次曝光（一天一次，见 TelemetryEvent.UpdateShown）
    LaunchedEffect(info) { Telemetry.track(TelemetryEvent.UpdateShown) }

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner, info) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                val apk = pendingApk
                if (apk != null && apk.isFile && ApkInstaller.canInstall(context)) {
                    pendingApk = null
                    if (!ApkInstaller.install(context, apk)) {
                        Toast.makeText(
                            context,
                            "打不开系统安装器，请到「我的 → 设置 → 检查更新」重新下载",
                            Toast.LENGTH_LONG,
                        ).show()
                    }
                }
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val startApkInstall: (String) -> Unit = remember(info, context, scope) {
        { releaseUrl ->
            if (!downloading) {
                scope.launch {
                    downloading = true
                    progress = 0
                    label = null
                    // 版本号已知就用它命名：同版本重试、换源重试都命中同一个文件
                    val targetName = info.versionName
                        .takeIf { it.isNotBlank() }
                        ?.let { "pocket-xiaoli-v${it.removePrefix("v")}.apk" }
                        ?: "pocket-xiaoli-${releaseUrl.hashCode()}.apk"
                    val target = File(ApkInstaller.directory(context), targetName)
                    try {
                        // 已经下好且校验过：直接进安装（「继续安装」不再跑一遍下载）
                        var error: String? = if (target.isFile) {
                            withContext(Dispatchers.IO) { ApkArtifact.verify(target, info.sha256) }
                        } else {
                            "还没下载"
                        }
                        if (error == null) {
                            progress = 100
                        } else {
                            // 两个仓是同一份字节，换地址不需要放宽 SHA-256 校验
                            val urls = ApkMirrors.candidates(info.versionName, releaseUrl)
                            for ((index, url) in urls.withIndex()) {
                                if (index > 0) message = "换 ${ApkMirrors.label(url)} 再试…"
                                progress = 0
                                label = null
                                error = withContext(Dispatchers.IO) {
                                    ApkDownloader()
                                        .download(url, target) { p ->
                                            // 总量未知记 -1（画不确定圈），不假装有 0%
                                            progress = p.percent ?: -1
                                            label = DownloadProgressText.label(p)
                                        }
                                        ?: ApkArtifact.verify(target, info.sha256)
                                }
                                if (error == null) {
                                    if (index > 0) message = null
                                    break
                                }
                                // 坏包不留在缓存里，否则下一次「继续安装」会装到它
                                target.delete()
                            }
                        }
                        if (error != null) {
                            message = error
                            Toast.makeText(context, error, Toast.LENGTH_LONG).show()
                        } else {
                            Telemetry.track(TelemetryEvent.UpdateDownload)
                            if (!ApkInstaller.canInstall(context)) {
                                pendingApk = target
                                if (ApkInstaller.openPermissionSettings(context)) {
                                    Toast.makeText(
                                        context,
                                        "允许本应用安装后会自动继续",
                                        Toast.LENGTH_LONG,
                                    ).show()
                                } else {
                                    pendingApk = null
                                    openInBrowser(context, releaseUrl)
                                }
                            } else if (!ApkInstaller.install(context, target)) {
                                Toast.makeText(
                                    context,
                                    "打不开系统安装器，改用浏览器下载",
                                    Toast.LENGTH_SHORT,
                                ).show()
                                openInBrowser(context, releaseUrl)
                            }
                        }
                    } catch (t: Throwable) {
                        Toast.makeText(
                            context,
                            "安装失败：${t.javaClass.simpleName}",
                            Toast.LENGTH_LONG,
                        ).show()
                    } finally {
                        downloading = false
                    }
                }
            }
        }
    }

    WindowBottomSheet(
        show = true,
        title = info.title,
        allowDismiss = !info.force,
        onDismissRequest = { if (!info.force) controller.dismiss() },
    ) {
        SheetBackToDismiss(enabled = !info.force) { controller.dismiss() }
        AppUpdateSheetBody(
            force = info.force,
            notes = info.notes,
            message = message,
            downloading = downloading,
            progress = progress,
            downloadLabel = label,
            pendingInstall = pendingApk != null,
            hasDownload = info.apkUrl.isNotBlank(),
            isApk = info.apkUrl.endsWith(".apk", ignoreCase = true),
            onDownload = {
                Telemetry.track(TelemetryEvent.UpdateClicked)
                if (info.apkUrl.endsWith(".apk", ignoreCase = true)) {
                    startApkInstall(info.apkUrl)
                } else {
                    openInBrowser(context, info.apkUrl)
                }
            },
            onLater = { controller.dismiss() },
        )
    }
}

/**
 * 抽屉内容（与窗口外壳分开，只为了能单独截图）。
 *
 * `WindowBottomSheet` 是独立窗口，JVM 快照抓不到它；这里只收纯展示参数，
 * 排版与文案的回归用 `AppUpdateSheetScreenshotTest` 盯着。
 */
@Composable
internal fun AppUpdateSheetBody(
    force: Boolean,
    notes: List<String>,
    message: String?,
    downloading: Boolean,
    progress: Int,
    downloadLabel: String?,
    pendingInstall: Boolean,
    hasDownload: Boolean,
    isApk: Boolean,
    onDownload: () -> Unit,
    onLater: () -> Unit,
) {
    val colors = LocalPocketXiaoliColors.current
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState()),
    ) {
        if (message != null) {
            Text(
                message,
                fontSize = 13.sp,
                color = if (force) colors.danger else colors.textSecondary,
            )
            Spacer(Modifier.height(8.dp))
        }
        // 改了什么：只取前三行 —— 气泡里放得下才有意义，全文去「更新日志」看
        notes.take(3).forEach { note ->
            Row(Modifier.padding(bottom = 3.dp)) {
                Text("•", fontSize = 13.sp, color = accentColor())
                Spacer(Modifier.width(7.dp))
                Text(
                    note,
                    fontSize = 13.sp,
                    color = colors.textSecondary,
                    modifier = Modifier.weight(1f),
                )
            }
        }
        Spacer(Modifier.height(12.dp))
        if (downloading) {
            // 下载中把按钮整块换成「转圈 + 百分比」，不再额外顶一条进度条出来
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 10.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (progress >= 0) {
                    CircularProgressIndicator(
                        progress = { progress / 100f },
                        modifier = Modifier.size(20.dp),
                        strokeWidth = 2.dp,
                        color = accentColor(),
                    )
                } else {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        strokeWidth = 2.dp,
                        color = accentColor(),
                    )
                }
                Spacer(Modifier.width(8.dp))
                Text(
                    downloadLabel
                        ?: if (progress >= 0) "$progress%" else "正在下载",
                    fontSize = 14.sp,
                    color = accentColor(),
                )
            }
        } else if (hasDownload) {
            Button(
                onClick = onDownload,
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(containerColor = accentColor()),
            ) {
                Icon(
                    Icons.Filled.Download,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp),
                )
                Spacer(Modifier.width(6.dp))
                Text(
                    when {
                        pendingInstall -> "继续安装"
                        isApk -> "下载并安装"
                        else -> "去浏览器下载"
                    },
                    fontSize = 14.sp,
                )
            }
        }
        // 强更不给「稍后提醒」：给了它，等于给了一条永远绕不过去的假出口
        if (!force) {
            Spacer(Modifier.height(6.dp))
            TextButton(
                onClick = onLater,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("稍后提醒", fontSize = 13.sp, color = colors.textSecondary)
            }
        }
        Spacer(Modifier.height(16.dp))
        Spacer(Modifier.navigationBarsPadding())
    }
}

/** 打开浏览器 —— 应用内安装走不通时的退路，绝不让更新这件事无路可走 */
private fun openInBrowser(context: Context, url: String) {
    if (url.isBlank()) return
    try {
        context.startActivity(
            Intent(Intent.ACTION_VIEW, Uri.parse(url))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        )
    } catch (_: Exception) {
        Toast.makeText(context, "无法打开浏览器", Toast.LENGTH_SHORT).show()
    }
}
