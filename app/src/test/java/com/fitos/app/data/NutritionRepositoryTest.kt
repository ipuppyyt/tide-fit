package com.fitos.app.data

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.fitos.app.core.database.AppDatabase
import com.fitos.app.domain.model.BeverageKind
import com.fitos.app.domain.model.Food
import com.fitos.app.domain.model.Meal
import com.fitos.app.domain.model.Nutrition
import com.fitos.app.domain.model.NutritionSource
import com.fitos.app.domain.model.RecipeDraft
import com.fitos.app.domain.model.RecipeIngredient
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

@RunWith(RobolectricTestRunner::class)
class NutritionRepositoryTest {
    private lateinit var db: AppDatabase
    private lateinit var foods: FoodRepositoryImpl
    private lateinit var log: FoodLogRepositoryImpl
    private lateinit var water: WaterRepositoryImpl
    private lateinit var recipes: RecipeRepositoryImpl
    private val clock = Clock.fixed(Instant.parse("2026-11-16T10:00:00Z"), ZoneOffset.UTC)
    private val day = LocalDate.of(2026, 11, 16)

    private val chicken = Food(
        name = "Chicken", servingSize = 100.0, servingUnit = "g", servingWeightG = 100.0,
        per100 = Nutrition(200.0, 20.0, 0.0, 10.0), source = NutritionSource.VERIFIED
    )

    @Before fun setUp() {
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext<Context>(), AppDatabase::class.java)
            .allowMainThreadQueries().build()
        foods = FoodRepositoryImpl(db, clock); log = FoodLogRepositoryImpl(db, clock)
        water = WaterRepositoryImpl(db, clock); recipes = RecipeRepositoryImpl(db, clock)
    }

    @After fun tearDown() = db.close()

    @Test fun `seeding inserts starter foods once`() = runBlocking {
        val seeder = FoodSeeder(db.foodDao(), clock)
        seeder.seedIfNeeded(); seeder.seedIfNeeded()
        val all = foods.search("").first()
        assertTrue(all.size >= 50)
        assertTrue(all.all { it.source == NutritionSource.ESTIMATED })
        assertTrue(foods.search("matta").first().isNotEmpty())
        assertTrue(foods.search("idli").first().isNotEmpty())
        assertEquals(FoodSeeder.SEED.size, db.foodDao().countSeed())
    }

    @Test fun `logging computes 150 g correctly and marks the food as used`() = runBlocking {
        val id = foods.save(chicken)
        val entryId = log.logFood(foods.get(id)!!, 150.0, "150 g", day, Meal.LUNCH)

        val e = log.get(entryId)!!
        assertEquals(300.0, e.total.calories, 1e-9)
        assertEquals(30.0, e.total.protein, 1e-9)
        assertEquals(Meal.LUNCH, e.meal)
        assertEquals(listOf("Chicken"), foods.recent().first().map { it.name })
    }

    @Test fun `editing a food does not change history`() = runBlocking {
        val id = foods.save(chicken)
        val entryId = log.logFood(foods.get(id)!!, 100.0, null, day, Meal.DINNER)

        foods.save(foods.get(id)!!.copy(per100 = Nutrition(999.0, 99.0, 0.0, 9.0)))

        assertEquals(200.0, log.get(entryId)!!.total.calories, 1e-9) // snapshot intact
        assertEquals(999.0, foods.get(id)!!.per100.calories, 1e-9)
    }

    @Test fun `deleting a food keeps its logged entries`() = runBlocking {
        val id = foods.save(chicken)
        val entryId = log.logFood(foods.get(id)!!, 100.0, null, day, Meal.DINNER)
        foods.delete(id)

        val e = log.get(entryId)!!
        assertNull(e.foodId)
        assertEquals("Chicken", e.name)
        assertEquals(200.0, e.total.calories, 1e-9)
    }

    @Test fun `editing quantity rescales from the entry's snapshot even after the food changed`() = runBlocking {
        val id = foods.save(chicken)
        val entryId = log.logFood(foods.get(id)!!, 100.0, null, day, Meal.LUNCH)
        foods.save(foods.get(id)!!.copy(per100 = Nutrition(999.0, 99.0, 0.0, 9.0)))

        log.updateQuantity(entryId, 200.0, "200 g", Meal.DINNER)

        val e = log.get(entryId)!!
        assertEquals(400.0, e.total.calories, 1e-9)
        assertEquals(Meal.DINNER, e.meal)
    }

    @Test fun `invalid quantities and manual values are rejected`() = runBlocking {
        val food = foods.get(foods.save(chicken))!!
        assertFails { log.logFood(food, 0.0, null, day, Meal.LUNCH) }
        assertFails { log.logFood(food, -5.0, null, day, Meal.LUNCH) }
        assertFails { log.logManual(" ", Nutrition(1.0, 1.0, 1.0, 1.0), day, Meal.LUNCH) }
        assertFails { log.logManual("x", Nutrition(-1.0, 1.0, 1.0, 1.0), day, Meal.LUNCH) }
        assertTrue(log.observeDay(day).first().isEmpty())
    }

    @Test fun `manual entries can be edited and deleted`() = runBlocking {
        val id = log.logManual("Restaurant meal", Nutrition(700.0, 30.0, 80.0, 25.0), day, Meal.DINNER)
        log.updateManual(id, "Restaurant meal 2", Nutrition(650.0, 30.0, 70.0, 25.0), Meal.LUNCH)
        assertEquals(650.0, log.get(id)!!.total.calories, 1e-9)
        log.delete(id)
        assertNull(log.get(id))
    }

    @Test fun `day queries only return that date`() = runBlocking {
        val food = foods.get(foods.save(chicken))!!
        log.logFood(food, 100.0, null, day, Meal.LUNCH)
        log.logFood(food, 100.0, null, day.plusDays(1), Meal.LUNCH)
        assertEquals(1, log.observeDay(day).first().size)
        assertEquals(2, log.observeRange(day, day.plusDays(1)).first().size)
    }

    @Test fun `water entries validate and aggregate by kind`() = runBlocking {
        water.add(day, 250, BeverageKind.WATER, null)
        water.add(day, 330, BeverageKind.OTHER, "Cola")
        assertFails { water.add(day, 0, BeverageKind.WATER, null) }
        assertFails { water.add(day, 6000, BeverageKind.WATER, null) }

        val entries = water.observeDay(day).first()
        assertEquals(250, entries.filter { it.kind == BeverageKind.WATER }.sumOf { it.amountMl })
        assertEquals(2, entries.size)

        water.update(entries.first().id, 500, BeverageKind.WATER, null)
        water.delete(entries.last().id)
        assertEquals(1, water.observeDay(day).first().size)
    }

    @Test fun `recipe save creates a loggable food and can be updated`() = runBlocking {
        val draft = RecipeDraft(
            name = "Chicken rice", servings = 2, cookedWeightG = null,
            ingredients = listOf(
                RecipeIngredient(null, "Rice", 200.0, Nutrition(130.0, 3.0, 28.0, 0.3)),
                RecipeIngredient(null, "Chicken", 100.0, Nutrition(165.0, 31.0, 0.0, 3.6))
            )
        )
        val foodId = recipes.save(draft)
        val food = foods.get(foodId)!!
        assertTrue(food.isRecipe)
        assertEquals(150.0, food.servingWeightG, 1e-9)

        val entryId = log.logFood(food, food.servingWeightG, "1 serving", day, Meal.LUNCH)
        assertEquals(212.5, log.get(entryId)!!.total.calories, 1e-9)

        recipes.save(recipes.load(foodId)!!.copy(servings = 4))
        assertEquals(75.0, foods.get(foodId)!!.servingWeightG, 1e-9)
        assertEquals(212.5, log.get(entryId)!!.total.calories, 1e-9) // history unchanged
        assertEquals(2, recipes.load(foodId)!!.ingredients.size)
        assertNotNull(foods.recipes().first().firstOrNull())
    }

    @Test fun `invalid recipe is rejected without writing anything`() = runBlocking {
        assertFails { recipes.save(RecipeDraft(name = "Empty", servings = 1, cookedWeightG = null, ingredients = emptyList())) }
        assertTrue(foods.recipes().first().isEmpty())
    }

    private inline fun assertFails(block: () -> Unit) {
        try { block() } catch (e: IllegalArgumentException) { return } catch (e: IllegalStateException) { return }
        throw AssertionError("Expected failure")
    }
}
