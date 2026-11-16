package com.artishorizon.tidefit.navigation

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.QueryStats
import androidx.compose.material.icons.filled.Restaurant
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.QueryStats
import androidx.compose.material.icons.outlined.Restaurant
import androidx.compose.material.icons.outlined.FitnessCenter
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import com.artishorizon.tidefit.core.designsystem.components.FloatingTabBar
import com.artishorizon.tidefit.core.designsystem.components.TabItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.artishorizon.tidefit.feature.settings.DataScreen
import com.artishorizon.tidefit.feature.coach.CoachHistoryScreen
import com.artishorizon.tidefit.feature.coach.CoachScreen
import com.artishorizon.tidefit.feature.body.MeasurementsScreen
import com.artishorizon.tidefit.feature.body.ProgressScreen
import com.artishorizon.tidefit.feature.body.RecoveryScreen
import com.artishorizon.tidefit.feature.body.SleepScreen
import com.artishorizon.tidefit.feature.body.SupplementsScreen
import com.artishorizon.tidefit.feature.body.WeightScreen
import com.artishorizon.tidefit.feature.home.HomeScreen
import androidx.navigation.toRoute
import com.artishorizon.tidefit.feature.nutrition.AddFoodScreen
import com.artishorizon.tidefit.feature.nutrition.FoodEditorScreen
import com.artishorizon.tidefit.feature.nutrition.LogEntryScreen
import com.artishorizon.tidefit.feature.nutrition.NutritionScreen
import com.artishorizon.tidefit.feature.nutrition.RecipeEditorScreen
import com.artishorizon.tidefit.feature.nutrition.TargetsEditScreen
import com.artishorizon.tidefit.feature.nutrition.WaterScreen
import com.artishorizon.tidefit.feature.workouts.ActiveWorkoutScreen
import com.artishorizon.tidefit.feature.workouts.ExerciseLibraryScreen
import com.artishorizon.tidefit.feature.workouts.HistoryScreen
import com.artishorizon.tidefit.feature.workouts.RoutineEditorScreen
import com.artishorizon.tidefit.feature.workouts.StatsScreen
import com.artishorizon.tidefit.feature.workouts.WorkoutDetailScreen
import com.artishorizon.tidefit.feature.workouts.WorkoutsScreen
import com.artishorizon.tidefit.feature.profile.ProfileEditScreen
import com.artishorizon.tidefit.feature.profile.ProfileScreen
import kotlinx.serialization.Serializable

// Type-safe routes (Navigation Compose + kotlinx.serialization).
@Serializable data object HomeRoute
@Serializable data object NutritionRoute
@Serializable data object WorkoutsRoute
@Serializable data object ProgressRoute
@Serializable data object ProfileRoute
@Serializable data object ProfileEditRoute
@Serializable data class AddFoodRoute(val epochDay: Long, val meal: String)
@Serializable data class LogEntryRoute(val epochDay: Long, val meal: String, val foodId: Long = 0, val entryId: Long = 0)
@Serializable data class FoodEditorRoute(val foodId: Long = 0)
@Serializable data class RecipeEditorRoute(val foodId: Long = 0)
@Serializable data class ActiveWorkoutRoute(val sessionId: Long)
@Serializable data class RoutineEditorRoute(val routineId: Long = 0)
@Serializable data object ExerciseLibraryRoute
@Serializable data object WorkoutHistoryRoute
@Serializable data class WorkoutDetailRoute(val sessionId: Long)
@Serializable data object TrainingStatsRoute
@Serializable data object WaterRoute
@Serializable data object TargetsEditRoute
@Serializable data object WeightRoute
@Serializable data object MeasurementsRoute
@Serializable data object SleepRoute
@Serializable data object RecoveryRoute
@Serializable data object SupplementsRoute
@Serializable data object CoachRoute
@Serializable data object DataRoute
@Serializable data object CoachHistoryRoute

private data class TopLevel(
    val route: Any,
    val label: String,
    val icon: ImageVector,
    val selectedIcon: ImageVector,
    val matches: (androidx.navigation.NavDestination?) -> Boolean
)

private val topLevel = listOf(
    TopLevel(HomeRoute, "Home", Icons.Outlined.Home, Icons.Filled.Home) { it?.hasRoute<HomeRoute>() == true },
    TopLevel(NutritionRoute, "Nutrition", Icons.Outlined.Restaurant, Icons.Filled.Restaurant) { it?.hasRoute<NutritionRoute>() == true },
    TopLevel(WorkoutsRoute, "Workouts", Icons.Outlined.FitnessCenter, Icons.Filled.FitnessCenter) { it?.hasRoute<WorkoutsRoute>() == true },
    TopLevel(ProgressRoute, "Progress", Icons.Outlined.QueryStats, Icons.Filled.QueryStats) { it?.hasRoute<ProgressRoute>() == true },
    TopLevel(ProfileRoute, "Profile", Icons.Outlined.Person, Icons.Filled.Person) { it?.hasRoute<ProfileRoute>() == true }
)

@Composable
fun MainNavigation() {
    val nav = rememberNavController()
    val backStack by nav.currentBackStackEntryAsState()
    val destination = backStack?.destination
    val showBar = topLevel.any { it.matches(destination) }

    Scaffold(
        bottomBar = {
            if (showBar) {
                FloatingTabBar(
                    items = topLevel.map { TabItem(it.label, it.icon, it.selectedIcon) },
                    selectedIndex = topLevel.indexOfFirst { it.matches(destination) }.coerceAtLeast(0),
                    onSelect = { index ->
                        nav.navigate(topLevel[index].route) {
                            popUpTo(nav.graph.findStartDestination().id) { saveState = true }
                            launchSingleTop = true
                            restoreState = true
                        }
                    }
                )
            }
        }
    ) { padding: PaddingValues ->
        // Only top/bottom insets from the scaffold are forwarded; each screen applies its own horizontal padding.
        val content = PaddingValues(top = padding.calculateTopPadding(), bottom = padding.calculateBottomPadding())
        NavHost(nav, startDestination = HomeRoute, modifier = Modifier) {
            composable<HomeRoute> {
                HomeScreen(
                    content,
                    onAddFood = { nav.navigate(AddFoodRoute(java.time.LocalDate.now().toEpochDay(), com.artishorizon.tidefit.domain.model.Meal.OTHER.name)) },
                    onOpenWater = { nav.navigate(WaterRoute) },
                    onOpenNutrition = {
                        nav.navigate(NutritionRoute) {
                            popUpTo(nav.graph.findStartDestination().id) { saveState = true }
                            launchSingleTop = true
                            restoreState = true
                        }
                    },
                    onOpenWorkouts = {
                        nav.navigate(WorkoutsRoute) {
                            popUpTo(nav.graph.findStartDestination().id) { saveState = true }
                            launchSingleTop = true
                            restoreState = true
                        }
                    },
                    onOpenWeight = { nav.navigate(WeightRoute) }
                )
            }
            composable<NutritionRoute> {
                NutritionScreen(
                    contentPadding = content,
                    onAddFood = { day, meal -> nav.navigate(AddFoodRoute(day.toEpochDay(), meal.name)) },
                    onEditEntry = { day, entry -> nav.navigate(LogEntryRoute(day.toEpochDay(), entry.meal.name, entryId = entry.id)) },
                    onOpenWater = { nav.navigate(WaterRoute) },
                    onSetTargets = { nav.navigate(TargetsEditRoute) }
                )
            }
            composable<AddFoodRoute> { entry ->
                val r = entry.toRoute<AddFoodRoute>()
                AddFoodScreen(
                    onBack = { nav.popBackStack() },
                    onOpenFood = { food -> nav.navigate(LogEntryRoute(r.epochDay, r.meal, foodId = food.id)) },
                    onCreateFood = { nav.navigate(FoodEditorRoute()) },
                    onCreateRecipe = { nav.navigate(RecipeEditorRoute()) }
                )
            }
            composable<LogEntryRoute> {
                LogEntryScreen(
                    onBack = { nav.popBackStack() },
                    onEditFood = { id, isRecipe -> nav.navigate(if (isRecipe) RecipeEditorRoute(id) else FoodEditorRoute(id)) }
                )
            }
            composable<FoodEditorRoute> { FoodEditorScreen(onBack = { nav.popBackStack() }) }
            composable<RecipeEditorRoute> { RecipeEditorScreen(onBack = { nav.popBackStack() }) }
            composable<WaterRoute> { WaterScreen(onBack = { nav.popBackStack() }) }
            composable<TargetsEditRoute> { TargetsEditScreen(onBack = { nav.popBackStack() }) }
            composable<WorkoutsRoute> {
                WorkoutsScreen(
                    contentPadding = content,
                    onOpenWorkout = { nav.navigate(ActiveWorkoutRoute(it)) },
                    onEditRoutine = { nav.navigate(RoutineEditorRoute(it)) },
                    onOpenExercises = { nav.navigate(ExerciseLibraryRoute) },
                    onOpenHistory = { nav.navigate(WorkoutHistoryRoute) },
                    onOpenStats = { nav.navigate(TrainingStatsRoute) },
                    onOpenSummary = { nav.navigate(WorkoutDetailRoute(it)) }
                )
            }
            composable<ActiveWorkoutRoute> {
                ActiveWorkoutScreen(
                    onClose = { nav.popBackStack() },
                    onFinished = { id ->
                        // Replace the live workout screen with its summary so Back returns to the Workouts tab.
                        nav.navigate(WorkoutDetailRoute(id)) { popUpTo<ActiveWorkoutRoute> { inclusive = true } }
                    }
                )
            }
            composable<RoutineEditorRoute> { RoutineEditorScreen(onBack = { nav.popBackStack() }) }
            composable<ExerciseLibraryRoute> { ExerciseLibraryScreen(onBack = { nav.popBackStack() }) }
            composable<WorkoutHistoryRoute> { HistoryScreen(onBack = { nav.popBackStack() }, onOpen = { nav.navigate(WorkoutDetailRoute(it)) }) }
            composable<WorkoutDetailRoute> { WorkoutDetailScreen(onBack = { nav.popBackStack() }) }
            composable<TrainingStatsRoute> { StatsScreen(onBack = { nav.popBackStack() }) }
            composable<ProgressRoute> {
                ProgressScreen(
                    contentPadding = content,
                    onOpenWeight = { nav.navigate(WeightRoute) },
                    onOpenMeasurements = { nav.navigate(MeasurementsRoute) },
                    onOpenSleep = { nav.navigate(SleepRoute) },
                    onOpenRecovery = { nav.navigate(RecoveryRoute) },
                    onOpenSupplements = { nav.navigate(SupplementsRoute) },
                    onOpenTraining = { nav.navigate(TrainingStatsRoute) },
                    onOpenCoach = { nav.navigate(CoachRoute) }
                )
            }
            composable<CoachRoute> { CoachScreen(onBack = { nav.popBackStack() }, onOpenHistory = { nav.navigate(CoachHistoryRoute) }) }
            composable<CoachHistoryRoute> { CoachHistoryScreen(onBack = { nav.popBackStack() }) }
            composable<WeightRoute> { WeightScreen(onBack = { nav.popBackStack() }) }
            composable<MeasurementsRoute> { MeasurementsScreen(onBack = { nav.popBackStack() }) }
            composable<SleepRoute> { SleepScreen(onBack = { nav.popBackStack() }) }
            composable<RecoveryRoute> { RecoveryScreen(onBack = { nav.popBackStack() }) }
            composable<SupplementsRoute> { SupplementsScreen(onBack = { nav.popBackStack() }) }
            composable<ProfileRoute> { ProfileScreen(onEditProfile = { nav.navigate(ProfileEditRoute) }, onOpenData = { nav.navigate(DataRoute) }, contentPadding = content) }
            composable<DataRoute> { DataScreen(onBack = { nav.popBackStack() }) }
            composable<ProfileEditRoute> { ProfileEditScreen(onDone = { nav.popBackStack() }) }
        }
    }
}
