package com.fitos.app.data

import androidx.room.withTransaction
import com.fitos.app.core.database.AppDatabase
import com.fitos.app.core.database.FoodEntity
import com.fitos.app.core.database.FoodEntryEntity
import com.fitos.app.core.database.RecipeEntity
import com.fitos.app.core.database.RecipeIngredientEntity
import com.fitos.app.core.database.WaterEntryEntity
import com.fitos.app.domain.calc.PortionCalculator
import com.fitos.app.domain.calc.RecipeCalculator
import com.fitos.app.domain.model.BeverageKind
import com.fitos.app.domain.model.Food
import com.fitos.app.domain.model.FoodEntry
import com.fitos.app.domain.model.Meal
import com.fitos.app.domain.model.Nutrition
import com.fitos.app.domain.model.NutritionSource
import com.fitos.app.domain.model.RecipeDraft
import com.fitos.app.domain.model.RecipeIngredient
import com.fitos.app.domain.model.WaterEntry
import com.fitos.app.domain.repository.FoodLogRepository
import com.fitos.app.domain.repository.FoodRepository
import com.fitos.app.domain.repository.RecipeRepository
import com.fitos.app.domain.repository.WaterRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import javax.inject.Inject
import javax.inject.Singleton

private const val LIST_LIMIT = 100

@Singleton
class FoodRepositoryImpl @Inject constructor(private val db: AppDatabase, private val clock: Clock) : FoodRepository {
    private val dao get() = db.foodDao()

    override fun search(query: String): Flow<List<Food>> = dao.search(query.trim(), LIST_LIMIT).map { l -> l.map { it.toDomain() } }
    override fun recent(): Flow<List<Food>> = dao.observeRecent(30).map { l -> l.map { it.toDomain() } }
    override fun favourites(): Flow<List<Food>> = dao.observeFavourites().map { l -> l.map { it.toDomain() } }
    override fun recipes(): Flow<List<Food>> = dao.observeRecipes().map { l -> l.map { it.toDomain() } }
    override fun observe(id: Long): Flow<Food?> = dao.observe(id).map { it?.toDomain() }
    override suspend fun get(id: Long): Food? = dao.get(id)?.toDomain()

    override suspend fun save(food: Food): Long {
        val now = Instant.now(clock)
        if (food.id == 0L) {
            return dao.insert(food.toEntity(createdAt = now, updatedAt = now, isSeed = false, lastUsedAt = null, useCount = 0))
        }
        val existing = dao.get(food.id) ?: error("Food ${food.id} not found")
        dao.update(
            food.toEntity(createdAt = existing.createdAt, updatedAt = now, isSeed = existing.isSeed, lastUsedAt = existing.lastUsedAt, useCount = existing.useCount)
        )
        return food.id
    }

    override suspend fun delete(id: Long) = dao.delete(id)
    override suspend fun setFavourite(id: Long, favourite: Boolean) = dao.setFavourite(id, favourite)
}

@Singleton
class FoodLogRepositoryImpl @Inject constructor(private val db: AppDatabase, private val clock: Clock) : FoodLogRepository {
    private val dao get() = db.foodEntryDao()

    override fun observeDay(date: LocalDate): Flow<List<FoodEntry>> = dao.observeForDate(date).map { l -> l.map { it.toDomain() } }
    override fun observeRange(from: LocalDate, to: LocalDate): Flow<List<FoodEntry>> =
        dao.observeRange(from, to).map { l -> l.map { it.toDomain() } }

    override suspend fun get(id: Long): FoodEntry? = dao.get(id)?.toDomain()

    override suspend fun logFood(food: Food, grams: Double, quantityLabel: String?, date: LocalDate, meal: Meal): Long {
        require(grams > 0) { "Quantity must be positive" }
        val total = requireNotNull(PortionCalculator.forGrams(food.per100, grams)) { "Invalid quantity" }
        val now = Instant.now(clock)
        return db.withTransaction {
            val id = dao.insert(
                FoodEntryEntity(
                    date = date, meal = meal.name, foodId = food.id.takeIf { it != 0L }, name = food.name, brand = food.brand,
                    quantityG = grams, quantityLabel = quantityLabel, total = total, source = food.source.name,
                    loggedAt = now, updatedAt = now
                )
            )
            if (food.id != 0L) db.foodDao().markUsed(food.id, now)
            id
        }
    }

    override suspend fun logManual(name: String, nutrition: Nutrition, date: LocalDate, meal: Meal): Long {
        require(name.isNotBlank()) { "Name required" }
        require(nutrition.calories >= 0 && nutrition.protein >= 0 && nutrition.carbs >= 0 && nutrition.fat >= 0) { "Negative values" }
        val now = Instant.now(clock)
        return dao.insert(
            FoodEntryEntity(
                date = date, meal = meal.name, foodId = null, name = name.trim(), brand = null, quantityG = null, quantityLabel = null,
                total = nutrition, source = NutritionSource.ESTIMATED.name, loggedAt = now, updatedAt = now
            )
        )
    }

    override suspend fun updateQuantity(id: Long, grams: Double, quantityLabel: String?, meal: Meal) {
        require(grams > 0) { "Quantity must be positive" }
        db.withTransaction {
            val e = dao.get(id) ?: error("Entry not found")
            val oldGrams = e.quantityG ?: error("Entry has no quantity")
            val total = requireNotNull(PortionCalculator.rescale(e.total, oldGrams, grams)) { "Invalid quantity" }
            dao.update(e.copy(quantityG = grams, quantityLabel = quantityLabel, total = total, meal = meal.name, updatedAt = Instant.now(clock)))
        }
    }

    override suspend fun updateManual(id: Long, name: String, nutrition: Nutrition, meal: Meal) {
        require(name.isNotBlank()) { "Name required" }
        db.withTransaction {
            val e = dao.get(id) ?: error("Entry not found")
            dao.update(e.copy(name = name.trim(), total = nutrition, meal = meal.name, updatedAt = Instant.now(clock)))
        }
    }

    override suspend fun delete(id: Long) = dao.delete(id)
}

@Singleton
class WaterRepositoryImpl @Inject constructor(private val db: AppDatabase, private val clock: Clock) : WaterRepository {
    private val dao get() = db.waterEntryDao()

    override fun observeDay(date: LocalDate): Flow<List<WaterEntry>> = dao.observeForDate(date).map { l -> l.map { it.toDomain() } }
    override fun observeRange(from: LocalDate, to: LocalDate): Flow<List<WaterEntry>> =
        dao.observeRange(from, to).map { l -> l.map { it.toDomain() } }

    override suspend fun add(date: LocalDate, amountMl: Int, kind: BeverageKind, label: String?): Long {
        require(amountMl in 1..MAX_ML) { "Amount out of range" }
        return dao.insert(WaterEntryEntity(date = date, amountMl = amountMl, kind = kind.name, label = label?.trim()?.ifEmpty { null }, loggedAt = Instant.now(clock)))
    }

    override suspend fun update(id: Long, amountMl: Int, kind: BeverageKind, label: String?) {
        require(amountMl in 1..MAX_ML) { "Amount out of range" }
        val e = dao.get(id) ?: return
        dao.update(e.copy(amountMl = amountMl, kind = kind.name, label = label?.trim()?.ifEmpty { null }))
    }

    override suspend fun delete(id: Long) = dao.delete(id)

    companion object {
        const val MAX_ML = 5000
    }
}

@Singleton
class RecipeRepositoryImpl @Inject constructor(private val db: AppDatabase, private val clock: Clock) : RecipeRepository {

    override suspend fun save(draft: RecipeDraft): Long {
        require(draft.name.isNotBlank()) { "Recipe name required" }
        val result = requireNotNull(RecipeCalculator.calculate(draft.ingredients, draft.servings, draft.cookedWeightG)) { "Invalid recipe" }
        val now = Instant.now(clock)
        return db.withTransaction {
            val foodDao = db.foodDao()
            val recipeDao = db.recipeDao()
            val food = Food(
                id = draft.foodId, name = draft.name.trim(), servingSize = 1.0, servingUnit = "serving",
                servingWeightG = result.servingWeightG, per100 = result.per100, source = NutritionSource.ESTIMATED,
                sourceNote = "Calculated from the recipe's ingredients.", isCustom = true, isRecipe = true
            )
            val foodId: Long
            val recipeId: Long
            if (draft.foodId == 0L) {
                foodId = foodDao.insert(food.toEntity(now, now, isSeed = false, lastUsedAt = null, useCount = 0))
                recipeId = recipeDao.insert(RecipeEntity(foodId = foodId, name = food.name, servings = draft.servings, cookedWeightG = draft.cookedWeightG, createdAt = now, updatedAt = now))
            } else {
                val existing = foodDao.get(draft.foodId) ?: error("Recipe food not found")
                foodId = draft.foodId
                foodDao.update(food.toEntity(existing.createdAt, now, existing.isSeed, existing.lastUsedAt, existing.useCount).copy(isFavourite = existing.isFavourite))
                val r = recipeDao.getByFoodId(foodId) ?: error("Recipe not found")
                recipeDao.update(r.copy(name = food.name, servings = draft.servings, cookedWeightG = draft.cookedWeightG, updatedAt = now))
                recipeDao.deleteIngredients(r.id)
                recipeId = r.id
            }
            recipeDao.insertIngredients(
                draft.ingredients.mapIndexed { i, ing ->
                    RecipeIngredientEntity(recipeId = recipeId, sourceFoodId = ing.sourceFoodId, name = ing.name, grams = ing.grams, per100 = ing.per100, position = i)
                }
            )
            foodId
        }
    }

    override suspend fun load(foodId: Long): RecipeDraft? {
        val r = db.recipeDao().getByFoodId(foodId) ?: return null
        return RecipeDraft(
            foodId = foodId, name = r.name, servings = r.servings, cookedWeightG = r.cookedWeightG,
            ingredients = db.recipeDao().getIngredients(r.id).map { RecipeIngredient(it.sourceFoodId, it.name, it.grams, it.per100) }
        )
    }
}

internal fun FoodEntity.toDomain() = Food(
    id = id, name = name, brand = brand, servingSize = servingSize, servingUnit = servingUnit, servingWeightG = servingWeightG,
    per100 = per100, source = runCatching { NutritionSource.valueOf(source) }.getOrDefault(NutritionSource.ESTIMATED),
    sourceNote = sourceNote, isFavourite = isFavourite, isCustom = isCustom, isRecipe = isRecipe
)

internal fun Food.toEntity(createdAt: Instant, updatedAt: Instant, isSeed: Boolean, lastUsedAt: Instant?, useCount: Int) = FoodEntity(
    id = id, name = name.trim(), brand = brand?.trim()?.ifEmpty { null }, servingSize = servingSize, servingUnit = servingUnit,
    servingWeightG = servingWeightG, per100 = per100, source = source.name, sourceNote = sourceNote, isFavourite = isFavourite,
    isCustom = isCustom, isSeed = isSeed, isRecipe = isRecipe, lastUsedAt = lastUsedAt, useCount = useCount,
    createdAt = createdAt, updatedAt = updatedAt
)

private fun FoodEntryEntity.toDomain() = FoodEntry(
    id = id, date = date, meal = runCatching { Meal.valueOf(meal) }.getOrDefault(Meal.OTHER), foodId = foodId, name = name, brand = brand,
    quantityG = quantityG, quantityLabel = quantityLabel, total = total,
    source = runCatching { NutritionSource.valueOf(source) }.getOrDefault(NutritionSource.ESTIMATED), loggedAt = loggedAt
)

private fun WaterEntryEntity.toDomain() = WaterEntry(
    id = id, date = date, amountMl = amountMl, kind = runCatching { BeverageKind.valueOf(kind) }.getOrDefault(BeverageKind.WATER),
    label = label, loggedAt = loggedAt
)
