package com.artishorizon.tidefit.core.database

import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.artishorizon.tidefit.domain.model.Nutrition
import java.time.Instant
import java.time.LocalDate

@Entity(tableName = "food", indices = [Index("name"), Index("isFavourite"), Index("lastUsedAt")])
data class FoodEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val brand: String?,
    val servingSize: Double,
    val servingUnit: String,
    val servingWeightG: Double,
    /** Nutrition per 100 g. */
    @Embedded(prefix = "p_") val per100: Nutrition,
    val source: String,
    val sourceNote: String?,
    val isFavourite: Boolean,
    val isCustom: Boolean,
    val isSeed: Boolean,
    val isRecipe: Boolean,
    val lastUsedAt: Instant?,
    val useCount: Int,
    val createdAt: Instant,
    val updatedAt: Instant
)

/** A logged food. [total] is an immutable snapshot of what was eaten; the food row may later change or vanish. */
@Entity(
    tableName = "food_entry",
    foreignKeys = [ForeignKey(FoodEntity::class, parentColumns = ["id"], childColumns = ["foodId"], onDelete = ForeignKey.SET_NULL)],
    indices = [Index("date"), Index("foodId"), Index("meal")]
)
data class FoodEntryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val date: LocalDate,
    val meal: String,
    val foodId: Long?,
    val name: String,
    val brand: String?,
    val quantityG: Double?,
    val quantityLabel: String?,
    @Embedded(prefix = "t_") val total: Nutrition,
    val source: String,
    val loggedAt: Instant,
    val updatedAt: Instant
)

@Entity(tableName = "water_entry", indices = [Index("date")])
data class WaterEntryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val date: LocalDate,
    val amountMl: Int,
    val kind: String,
    val label: String?,
    val loggedAt: Instant
)

@Entity(
    tableName = "recipe",
    foreignKeys = [ForeignKey(FoodEntity::class, parentColumns = ["id"], childColumns = ["foodId"], onDelete = ForeignKey.CASCADE)],
    indices = [Index("foodId", unique = true)]
)
data class RecipeEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val foodId: Long,
    val name: String,
    val servings: Int,
    val cookedWeightG: Double?,
    val createdAt: Instant,
    val updatedAt: Instant
)

@Entity(
    tableName = "recipe_ingredient",
    foreignKeys = [ForeignKey(RecipeEntity::class, parentColumns = ["id"], childColumns = ["recipeId"], onDelete = ForeignKey.CASCADE)],
    indices = [Index("recipeId")]
)
data class RecipeIngredientEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val recipeId: Long,
    val sourceFoodId: Long?,
    val name: String,
    val grams: Double,
    @Embedded(prefix = "p_") val per100: Nutrition,
    val position: Int
)
