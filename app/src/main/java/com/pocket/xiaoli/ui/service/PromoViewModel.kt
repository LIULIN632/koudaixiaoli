package com.pocket.xiaoli.ui.service

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.pocket.xiaoli.data.promo.Promo
import com.pocket.xiaoli.data.promo.PromoAction
import com.pocket.xiaoli.data.promo.PromoRepository
import com.pocket.xiaoli.data.promo.PromoSlot
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * 推广位内容。
 *
 * 只在**进程内加载一次**（[loaded] 记录），之后各服务介绍页复用同一份结果：
 * 用户从"专升本"页退出去再进"返乡大巴"页，不该再拉一次网络。
 * 需要强制刷新时调 [refresh]。
 *
 * 加载失败**不报错、不重试、不提示**：推广位是附加内容，
 * 拉不到就退化成"这一页没有推广"，绝不能因为推广拉取失败给用户弹错误
 * （那会把一个纯广告位的故障升级成产品故障）。
 */
class PromoViewModel(private val repository: PromoRepository) : ViewModel() {

    private val _promos = MutableStateFlow<List<Promo>>(emptyList())
    val promos: StateFlow<List<Promo>> = _promos.asStateFlow()

    private var loaded = false

    /** 首次进入任意服务介绍页时调用；已加载过则直接返回。 */
    fun loadOnce() {
        if (loaded) return
        loaded = true
        viewModelScope.launch { _promos.value = repository.load().promos }
    }

    /** 强制重新拉取（目前没有 UI 入口，留给"检查推广更新"这类将来需求）。 */
    fun refresh() {
        viewModelScope.launch { _promos.value = repository.load().promos }
    }

    /**
     * 取某个 slot 的推广，并转换成界面模型。
     *
     * ⚠️ [all] 必须由调用方从 [promos] **收集后传入**，不要在这里读 `_promos.value`：
     * 读一个 StateFlow 的当前值**不会**建立重组依赖，加载完成后页面不会刷新
     * （表现为"第一次进没有推广，退出再进才有"）。传参才能让 Compose 正确追踪变化。
     *
     * 域模型 → 界面模型的映射放在这里，而不是让 `TreasureBoxScreen` 认识
     * `PromoAction` 的封闭类型 —— 界面只需要知道"能不能点、点了给什么"。
     */
    fun forSlot(all: List<Promo>, slot: PromoSlot): List<ServiceInfoPromo> =
        all.filter { it.slot == slot }.map { it.toUi() }

    private fun Promo.toUi(): ServiceInfoPromo = ServiceInfoPromo(
        title = title,
        body = body,
        imageUrl = imageUrl,
        actionUrl = (action as? PromoAction.External)?.url,
        actionRoute = (action as? PromoAction.Route)?.route,
    )

    class Factory(private val repository: PromoRepository) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T =
            PromoViewModel(repository) as T
    }
}
