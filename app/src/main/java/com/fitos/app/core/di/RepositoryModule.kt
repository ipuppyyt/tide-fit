package com.fitos.app.core.di

import com.fitos.app.core.datastore.SettingsRepository
import com.fitos.app.data.FoodLogRepositoryImpl
import com.fitos.app.data.FoodRepositoryImpl
import com.fitos.app.data.ProfileRepositoryImpl
import com.fitos.app.data.RecipeRepositoryImpl
import com.fitos.app.data.WaterRepositoryImpl
import com.fitos.app.domain.repository.FoodLogRepository
import com.fitos.app.domain.repository.FoodRepository
import com.fitos.app.domain.repository.RecipeRepository
import com.fitos.app.domain.repository.WaterRepository
import com.fitos.app.domain.repository.AppSettings
import com.fitos.app.domain.repository.ProfileRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class RepositoryModule {
    @Binds @Singleton
    abstract fun profileRepository(impl: ProfileRepositoryImpl): ProfileRepository

    @Binds @Singleton
    abstract fun appSettings(impl: SettingsRepository): AppSettings

    @Binds @Singleton abstract fun reminderSettings(impl: SettingsRepository): com.fitos.app.domain.repository.ReminderSettings
    @Binds @Singleton abstract fun foods(impl: FoodRepositoryImpl): FoodRepository
    @Binds @Singleton abstract fun foodLog(impl: FoodLogRepositoryImpl): FoodLogRepository
    @Binds @Singleton abstract fun water(impl: WaterRepositoryImpl): WaterRepository
    @Binds @Singleton abstract fun recipes(impl: RecipeRepositoryImpl): RecipeRepository
    @Binds @Singleton abstract fun exercises(impl: com.fitos.app.data.ExerciseRepositoryImpl): com.fitos.app.domain.repository.ExerciseRepository
    @Binds @Singleton abstract fun routines(impl: com.fitos.app.data.RoutineRepositoryImpl): com.fitos.app.domain.repository.RoutineRepository
    @Binds @Singleton abstract fun weights(impl: com.fitos.app.data.WeightRepositoryImpl): com.fitos.app.domain.repository.WeightRepository
    @Binds @Singleton abstract fun measurements(impl: com.fitos.app.data.MeasurementRepositoryImpl): com.fitos.app.domain.repository.MeasurementRepository
    @Binds @Singleton abstract fun sleep(impl: com.fitos.app.data.SleepRepositoryImpl): com.fitos.app.domain.repository.SleepRepository
    @Binds @Singleton abstract fun recovery(impl: com.fitos.app.data.RecoveryRepositoryImpl): com.fitos.app.domain.repository.RecoveryRepository
    @Binds @Singleton abstract fun supplements(impl: com.fitos.app.data.SupplementRepositoryImpl): com.fitos.app.domain.repository.SupplementRepository
    @Binds @Singleton abstract fun coaching(impl: com.fitos.app.data.CoachingRepositoryImpl): com.fitos.app.domain.repository.CoachingRepository
    @Binds @Singleton abstract fun workouts(impl: com.fitos.app.data.WorkoutRepositoryImpl): com.fitos.app.domain.repository.WorkoutRepository
}
