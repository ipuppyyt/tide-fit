package com.artishorizon.tidefit.core.database

import androidx.room.AutoMigration
import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters

@Database(
    entities = [
        UserProfileEntity::class,
        FitnessGoalEntity::class,
        DailyTargetsEntity::class,
        WeightEntryEntity::class,
        FoodEntity::class,
        FoodEntryEntity::class,
        WaterEntryEntity::class,
        RecipeEntity::class,
        RecipeIngredientEntity::class,
        ExerciseEntity::class,
        RoutineEntity::class,
        RoutineExerciseEntity::class,
        WorkoutSessionEntity::class,
        WorkoutSetEntity::class,
        MeasurementEntity::class,
        SleepEntryEntity::class,
        RecoveryEntryEntity::class,
        SupplementEntity::class,
        SupplementLogEntity::class,
        CoachingSessionEntity::class
    ],
    version = AppDatabase.VERSION,
    exportSchema = true,
    autoMigrations = [
        AutoMigration(from = 1, to = 2), AutoMigration(from = 2, to = 3),
        AutoMigration(from = 3, to = 4), AutoMigration(from = 4, to = 5)
    ]
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun userProfileDao(): UserProfileDao
    abstract fun fitnessGoalDao(): FitnessGoalDao
    abstract fun dailyTargetsDao(): DailyTargetsDao
    abstract fun weightEntryDao(): WeightEntryDao
    abstract fun foodDao(): FoodDao
    abstract fun foodEntryDao(): FoodEntryDao
    abstract fun waterEntryDao(): WaterEntryDao
    abstract fun recipeDao(): RecipeDao
    abstract fun exerciseDao(): ExerciseDao
    abstract fun routineDao(): RoutineDao
    abstract fun workoutDao(): WorkoutDao
    abstract fun measurementDao(): MeasurementDao
    abstract fun sleepDao(): SleepDao
    abstract fun recoveryDao(): RecoveryDao
    abstract fun supplementDao(): SupplementDao
    abstract fun coachingDao(): CoachingDao

    companion object {
        const val NAME = "tidefit.db"

        /** Single source of truth for the schema version (also used to reject backups from newer app versions). */
        const val VERSION = 5
    }
}
