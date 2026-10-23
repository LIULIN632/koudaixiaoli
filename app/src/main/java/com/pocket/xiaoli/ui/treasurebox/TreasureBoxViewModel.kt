package com.pocket.xiaoli.ui.treasurebox

import android.app.Application
import android.content.Context
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.pocket.xiaoli.data.entity.CourseEntity
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

class TreasureBoxViewModel(application: Application) : AndroidViewModel(application) {
    private val prefs = application.getSharedPreferences("egg_rice_treasure", Context.MODE_PRIVATE)
    private val gson = Gson()

    // ── Learning Resources ──
    private val _resources = MutableStateFlow(loadResources())
    val resources: StateFlow<List<LearningResource>> = _resources

    // ── Food Options ──
    private val _foodOptions = MutableStateFlow(loadFoodOptions())
    val foodOptions: StateFlow<List<FoodOption>> = _foodOptions

    // ── Favorites ──
    private val _favoriteIds = MutableStateFlow(loadFavorites())
    val favoriteIds: StateFlow<Set<String>> = _favoriteIds

    fun toggleFavorite(resourceId: String) {
        val current = _favoriteIds.value.toMutableSet()
        if (current.contains(resourceId)) current.remove(resourceId) else current.add(resourceId)
        _favoriteIds.value = current
        saveFavorites(current)
    }

    fun isFavorite(resourceId: String): Boolean = resourceId in _favoriteIds.value

    private fun loadFavorites(): Set<String> {
        val json = prefs.getString("favorites", null) ?: return emptySet()
        return try {
            val type = object : TypeToken<Set<String>>() {}.type
            gson.fromJson(json, type)
        } catch (_: Exception) { emptySet() }
    }

    private fun saveFavorites(set: Set<String>) {
        prefs.edit().putString("favorites", gson.toJson(set)).apply()
    }

    // ── Food picker state ──
    private val _pickedFood = MutableStateFlow<FoodOption?>(null)
    val pickedFood: StateFlow<FoodOption?> = _pickedFood

    private val _isRolling = MutableStateFlow(false)
    val isRolling: StateFlow<Boolean> = _isRolling

    // ── Learning Resources ──

    fun addResource(resource: LearningResource) {
        val list = _resources.value.toMutableList()
        list.add(resource.copy(id = java.util.UUID.randomUUID().toString(), isCustom = true))
        _resources.value = list
        saveResources(list)
    }

    fun deleteResource(id: String) {
        val list = _resources.value.filter { it.id != id }
        _resources.value = list
        saveResources(list)
    }

    /** Create a CourseEntity from a learning resource for timetable import. */
    fun resourceToCourse(resource: LearningResource): CourseEntity {
        return CourseEntity(
            name = resource.courseName,
            teacher = resource.blogger,
            room = resource.description.take(20),
            dayOfWeek = resource.dayOfWeek,
            startSlot = resource.startSlot,
            endSlot = resource.endSlot,
            colorIndex = (resource.dayOfWeek * 3 + resource.startSlot) % 15
        )
    }

    private fun loadResources(): List<LearningResource> {
        val json = prefs.getString("custom_resources", null) ?: return DEFAULT_LEARNING_RESOURCES
        return try {
            val type = object : TypeToken<List<LearningResource>>() {}.type
            val custom: List<LearningResource> = gson.fromJson(json, type)
            DEFAULT_LEARNING_RESOURCES + custom
        } catch (_: Exception) { DEFAULT_LEARNING_RESOURCES }
    }

    private fun saveResources(list: List<LearningResource>) {
        val custom = list.filter { it.isCustom }
        prefs.edit().putString("custom_resources", gson.toJson(custom)).apply()
    }

    // ── Food Options ──

    fun addFoodOption(food: FoodOption) {
        val list = _foodOptions.value.toMutableList()
        list.add(food.copy(id = java.util.UUID.randomUUID().toString(), isCustom = true))
        _foodOptions.value = list
        saveFoodOptions(list)
    }

    fun deleteFoodOption(id: String) {
        val list = _foodOptions.value.filter { it.id != id }
        _foodOptions.value = list
        saveFoodOptions(list)
    }

    fun updateFoodOption(updated: FoodOption) {
        val list = _foodOptions.value.toMutableList()
        val idx = list.indexOfFirst { it.id == updated.id }
        if (idx >= 0) {
            list[idx] = updated
            _foodOptions.value = list
            saveFoodOptions(list)
        }
    }

    fun exportFoodOptionsJson(includeDefaults: Boolean = false): String {
        val list = if (includeDefaults) _foodOptions.value else _foodOptions.value.filter { it.isCustom }
        return gson.toJson(list)
    }

    fun importFoodOptionsJson(json: String): Int {
        return try {
            val type = object : TypeToken<List<FoodOption>>() {}.type
            val imported: List<FoodOption> = gson.fromJson(json, type)
            val existing = _foodOptions.value.toMutableList()
            val existingNames = existing.map { it.name to it.category }.toMutableSet()
            var added = 0
            for (food in imported) {
                if ((food.name to food.category) !in existingNames) {
                    existing.add(food.copy(id = java.util.UUID.randomUUID().toString(), isCustom = true))
                    existingNames += (food.name to food.category)
                    added++
                }
            }
            if (added > 0) {
                _foodOptions.value = existing
                saveFoodOptions(existing)
            }
            added
        } catch (_: Exception) { 0 }
    }

    fun pickRandomFood() {
        _isRolling.value = true
        val list = _foodOptions.value
        if (list.isEmpty()) return
        // Flash effect: pick a few times quickly
        var count = 0
        val totalFlashes = 8
        val handler = android.os.Handler(android.os.Looper.getMainLooper())
        val runnable = object : Runnable {
            override fun run() {
                if (count < totalFlashes) {
                    _pickedFood.value = list.random()
                    count++
                    handler.postDelayed(this, 80)
                } else {
                    _pickedFood.value = list.random()
                    _isRolling.value = false
                }
            }
        }
        handler.post(runnable)
    }

    fun clearPick() {
        _pickedFood.value = null
    }

    private fun loadFoodOptions(): List<FoodOption> {
        val json = prefs.getString("custom_foods", null) ?: return DEFAULT_FOOD_OPTIONS
        return try {
            val type = object : TypeToken<List<FoodOption>>() {}.type
            val custom: List<FoodOption> = gson.fromJson(json, type)
            DEFAULT_FOOD_OPTIONS + custom
        } catch (_: Exception) { DEFAULT_FOOD_OPTIONS }
    }

    private fun saveFoodOptions(list: List<FoodOption>) {
        val custom = list.filter { it.isCustom }
        prefs.edit().putString("custom_foods", gson.toJson(custom)).apply()
    }

    class Factory(private val app: Application) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T =
            TreasureBoxViewModel(app) as T
    }
}

// ── MVI — FoodPickerUiState + FoodPickerIntent ──
data class FoodPickerUiState(
    val foodOptions: List<FoodOption> = emptyList(),
    val pickedFood: FoodOption? = null,
    val isRolling: Boolean = false
)

sealed interface FoodPickerIntent {
    data object PickRandom : FoodPickerIntent
    data object ClearPick : FoodPickerIntent
    data class AddFood(val food: FoodOption) : FoodPickerIntent
    data class DeleteFood(val id: String) : FoodPickerIntent
    data class ImportFoods(val json: String) : FoodPickerIntent
}
