package com.fitos.app.domain.repository

import com.fitos.app.domain.model.BeverageKind
import com.fitos.app.domain.model.Food
import com.fitos.app.domain.model.FoodEntry
import com.fitos.app.domain.model.Meal
import com.fitos.app.domain.model.Nutrition
import com.fitos.app.domain.model.RecipeDraft
import com.fitos.app.domain.model.WaterEntry
import kotlinx.coroutines.flow.Flow
import java.time.LocalDate

interface FoodRepository {
    fun search(query: String): Flow<List<Food>>
    fun recent(): Flow<List<Food>>
    fun favourites(): Flow<List<Food>>
    fun recipes(): Flow<List<Food>>
    fun observe(id: Long): Flow<Food?>
    suspend fun get(id: Long): Food?

    /** Inserts when [Food.id] is 0, otherwise updates. Returns the food id. */
    suspend fun save(food: Food): Long

    /** Deletes a food. Logged entries keep their snapshot. */
    suspend fun delete(id: Long)
    suspend fun setFavourite(id: Long, favourite: Boolean)
}

interface FoodLogRepository {
    fun observeDay(date: LocalDate): Flow<List<FoodEntry>>
    fun observeRange(from: LocalDate, to: LocalDate): Flow<List<FoodEntry>>
    suspend fun get(id: Long): FoodEntry?

    /** Logs [grams] of [food], snapshotting the nutrition at this moment. Throws on invalid grams. */
    suspend fun logFood(food: Food, grams: Double, quantityLabel: String?, date: LocalDate, meal: Meal): Long
    suspend fun logManual(name: String, nutrition: Nutrition, date: LocalDate, meal: Meal): Long

    /** Changes the quantity of an entry that came from a food, rescaling from its own snapshot. */
    suspend fun updateQuantity(id: Long, grams: Double, quantityLabel: String?, meal: Meal)
    suspend fun updateManual(id: Long, name: String, nutrition: Nutrition, meal: Meal)
    suspend fun delete(id: Long)
}

interface WaterRepository {
    fun observeDay(date: LocalDate): Flow<List<WaterEntry>>
    fun observeRange(from: LocalDate, to: LocalDate): Flow<List<WaterEntry>>
    suspend fun add(date: LocalDate, amountMl: Int, kind: BeverageKind, label: String?): Long
    suspend fun update(id: Long, amountMl: Int, kind: BeverageKind, label: String?)
    suspend fun delete(id: Long)
}

interface RecipeRepository {
    /** Creates or updates the recipe and its generated food in one transaction; returns the food id. */
    suspend fun save(draft: RecipeDraft): Long
    suspend fun load(foodId: Long): RecipeDraft?
}
