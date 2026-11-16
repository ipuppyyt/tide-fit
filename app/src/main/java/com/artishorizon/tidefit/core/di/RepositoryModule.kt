package com.artishorizon.tidefit.core.di

import com.artishorizon.tidefit.core.datastore.SettingsRepository
import com.artishorizon.tidefit.data.FoodLogRepositoryImpl
import com.artishorizon.tidefit.data.FoodRepositoryImpl
import com.artishorizon.tidefit.data.ProfileRepositoryImpl
import com.artishorizon.tidefit.data.RecipeRepositoryImpl
import com.artishorizon.tidefit.data.WaterRepositoryImpl
import com.artishorizon.tidefit.domain.repository.FoodLogRepository
import com.artishorizon.tidefit.domain.repository.FoodRepository
import com.artishorizon.tidefit.domain.repository.RecipeRepository
import com.artishorizon.tidefit.domain.repository.WaterRepository
import com.artishorizon.tidefit.domain.repository.AppSettings
import com.artishorizon.tidefit.domain.repository.ProfileRepository
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

    @Binds @Singleton abstract fun reminderSettings(impl: SettingsRepository): com.artishorizon.tidefit.domain.repository.ReminderSettings
    @Binds @Singleton abstract fun healthSyncSettings(impl: SettingsRepository): com.artishorizon.tidefit.domain.repository.HealthSyncSettings
    @Binds @Singleton abstract fun activity(impl: com.artishorizon.tidefit.data.ActivityRepositoryImpl): com.artishorizon.tidefit.domain.repository.ActivityRepository
    @Binds @Singleton abstract fun foods(impl: FoodRepositoryImpl): FoodRepository
    @Binds @Singleton abstract fun foodLog(impl: FoodLogRepositoryImpl): FoodLogRepository
    @Binds @Singleton abstract fun water(impl: WaterRepositoryImpl): WaterRepository
    @Binds @Singleton abstract fun recipes(impl: RecipeRepositoryImpl): RecipeRepository
    @Binds @Singleton abstract fun exercises(impl: com.artishorizon.tidefit.data.ExerciseRepositoryImpl): com.artishorizon.tidefit.domain.repository.ExerciseRepository
    @Binds @Singleton abstract fun routines(impl: com.artishorizon.tidefit.data.RoutineRepositoryImpl): com.artishorizon.tidefit.domain.repository.RoutineRepository
    @Binds @Singleton abstract fun weights(impl: com.artishorizon.tidefit.data.WeightRepositoryImpl): com.artishorizon.tidefit.domain.repository.WeightRepository
    @Binds @Singleton abstract fun measurements(impl: com.artishorizon.tidefit.data.MeasurementRepositoryImpl): com.artishorizon.tidefit.domain.repository.MeasurementRepository
    @Binds @Singleton abstract fun sleep(impl: com.artishorizon.tidefit.data.SleepRepositoryImpl): com.artishorizon.tidefit.domain.repository.SleepRepository
    @Binds @Singleton abstract fun recovery(impl: com.artishorizon.tidefit.data.RecoveryRepositoryImpl): com.artishorizon.tidefit.domain.repository.RecoveryRepository
    @Binds @Singleton abstract fun supplements(impl: com.artishorizon.tidefit.data.SupplementRepositoryImpl): com.artishorizon.tidefit.domain.repository.SupplementRepository
    @Binds @Singleton abstract fun coaching(impl: com.artishorizon.tidefit.data.CoachingRepositoryImpl): com.artishorizon.tidefit.domain.repository.CoachingRepository
    @Binds @Singleton abstract fun workouts(impl: com.artishorizon.tidefit.data.WorkoutRepositoryImpl): com.artishorizon.tidefit.domain.repository.WorkoutRepository
}
