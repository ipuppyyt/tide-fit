package com.fitos.app.core.database

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow
import java.time.Instant
import java.time.LocalDate

@Dao
interface FoodDao {
    @Query(
        "SELECT * FROM food WHERE name LIKE '%' || :q || '%' OR brand LIKE '%' || :q || '%' " +
            "ORDER BY isFavourite DESC, useCount DESC, name COLLATE NOCASE LIMIT :limit"
    )
    fun search(q: String, limit: Int): Flow<List<FoodEntity>>

    @Query("SELECT * FROM food WHERE lastUsedAt IS NOT NULL ORDER BY lastUsedAt DESC LIMIT :limit")
    fun observeRecent(limit: Int): Flow<List<FoodEntity>>

    @Query("SELECT * FROM food WHERE isFavourite = 1 ORDER BY name COLLATE NOCASE")
    fun observeFavourites(): Flow<List<FoodEntity>>

    @Query("SELECT * FROM food WHERE isRecipe = 1 ORDER BY name COLLATE NOCASE")
    fun observeRecipes(): Flow<List<FoodEntity>>

    @Query("SELECT * FROM food WHERE id = :id")
    fun observe(id: Long): Flow<FoodEntity?>

    @Query("SELECT * FROM food WHERE id = :id")
    suspend fun get(id: Long): FoodEntity?

    @Insert suspend fun insert(food: FoodEntity): Long
    @Insert suspend fun insertAll(foods: List<FoodEntity>)
    @Update suspend fun update(food: FoodEntity)

    @Query("DELETE FROM food WHERE id = :id")
    suspend fun delete(id: Long)

    @Query("SELECT COUNT(*) FROM food WHERE isSeed = 1")
    suspend fun countSeed(): Int

    @Query("UPDATE food SET lastUsedAt = :at, useCount = useCount + 1 WHERE id = :id")
    suspend fun markUsed(id: Long, at: Instant)

    @Query("UPDATE food SET isFavourite = :favourite WHERE id = :id")
    suspend fun setFavourite(id: Long, favourite: Boolean)
}

@Dao
interface FoodEntryDao {
    @Query("SELECT * FROM food_entry WHERE date = :date ORDER BY loggedAt")
    fun observeForDate(date: LocalDate): Flow<List<FoodEntryEntity>>

    @Query("SELECT * FROM food_entry WHERE date BETWEEN :from AND :to ORDER BY date, loggedAt")
    fun observeRange(from: LocalDate, to: LocalDate): Flow<List<FoodEntryEntity>>

    @Query("SELECT * FROM food_entry WHERE id = :id")
    suspend fun get(id: Long): FoodEntryEntity?

    @Insert suspend fun insert(entry: FoodEntryEntity): Long
    @Update suspend fun update(entry: FoodEntryEntity)

    @Query("DELETE FROM food_entry WHERE id = :id")
    suspend fun delete(id: Long)
}

@Dao
interface WaterEntryDao {
    @Query("SELECT * FROM water_entry WHERE date = :date ORDER BY loggedAt DESC")
    fun observeForDate(date: LocalDate): Flow<List<WaterEntryEntity>>

    @Query("SELECT * FROM water_entry WHERE date BETWEEN :from AND :to ORDER BY date, loggedAt")
    fun observeRange(from: LocalDate, to: LocalDate): Flow<List<WaterEntryEntity>>

    @Query("SELECT * FROM water_entry WHERE id = :id")
    suspend fun get(id: Long): WaterEntryEntity?

    @Insert suspend fun insert(entry: WaterEntryEntity): Long
    @Update suspend fun update(entry: WaterEntryEntity)

    @Query("DELETE FROM water_entry WHERE id = :id")
    suspend fun delete(id: Long)
}

@Dao
interface RecipeDao {
    @Insert suspend fun insert(recipe: RecipeEntity): Long
    @Update suspend fun update(recipe: RecipeEntity)
    @Insert suspend fun insertIngredients(items: List<RecipeIngredientEntity>)

    @Query("DELETE FROM recipe_ingredient WHERE recipeId = :recipeId")
    suspend fun deleteIngredients(recipeId: Long)

    @Query("SELECT * FROM recipe WHERE foodId = :foodId")
    suspend fun getByFoodId(foodId: Long): RecipeEntity?

    @Query("SELECT * FROM recipe_ingredient WHERE recipeId = :recipeId ORDER BY position")
    suspend fun getIngredients(recipeId: Long): List<RecipeIngredientEntity>
}
