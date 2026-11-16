package com.fitos.app.core.di

import android.content.Context
import androidx.room.Room
import com.fitos.app.core.database.AppDatabase
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import java.time.Clock
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides @Singleton
    fun database(@ApplicationContext context: Context): AppDatabase =
        Room.databaseBuilder(context, AppDatabase::class.java, AppDatabase.NAME)
            // Migrations are added explicitly per schema version; never destructive.
            .build()

    @Provides fun profileDao(db: AppDatabase) = db.userProfileDao()
    @Provides fun goalDao(db: AppDatabase) = db.fitnessGoalDao()
    @Provides fun targetsDao(db: AppDatabase) = db.dailyTargetsDao()
    @Provides fun weightDao(db: AppDatabase) = db.weightEntryDao()
    @Provides fun foodDao(db: AppDatabase) = db.foodDao()
    @Provides fun foodEntryDao(db: AppDatabase) = db.foodEntryDao()
    @Provides fun waterDao(db: AppDatabase) = db.waterEntryDao()
    @Provides fun recipeDao(db: AppDatabase) = db.recipeDao()
    @Provides fun exerciseDao(db: AppDatabase) = db.exerciseDao()
    @Provides fun routineDao(db: AppDatabase) = db.routineDao()
    @Provides fun workoutDao(db: AppDatabase) = db.workoutDao()

    /** Injectable clock so time-dependent logic is testable. */
    @Provides @Singleton
    fun clock(): Clock = Clock.systemDefaultZone()
}
