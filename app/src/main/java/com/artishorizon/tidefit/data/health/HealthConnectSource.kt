package com.artishorizon.tidefit.data.health

import android.content.Context
import androidx.activity.result.contract.ActivityResultContract
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.PermissionController
import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.ActiveCaloriesBurnedRecord
import androidx.health.connect.client.records.ExerciseSessionRecord
import androidx.health.connect.client.records.HydrationRecord
import androidx.health.connect.client.records.MealType
import androidx.health.connect.client.records.NutritionRecord
import androidx.health.connect.client.records.Record
import androidx.health.connect.client.records.SleepSessionRecord
import androidx.health.connect.client.records.StepsRecord
import androidx.health.connect.client.records.WeightRecord
import androidx.health.connect.client.records.metadata.Metadata
import androidx.health.connect.client.request.AggregateGroupByPeriodRequest
import androidx.health.connect.client.request.ReadRecordsRequest
import androidx.health.connect.client.time.TimeRangeFilter
import androidx.health.connect.client.units.Energy
import androidx.health.connect.client.units.Mass
import androidx.health.connect.client.units.Volume
import com.artishorizon.tidefit.domain.calc.HealthExportPlan
import com.artishorizon.tidefit.domain.calc.HealthMapping
import com.artishorizon.tidefit.domain.model.ActivityDay
import com.artishorizon.tidefit.domain.model.Meal
import com.artishorizon.tidefit.domain.repository.ActivityRepository
import com.artishorizon.tidefit.domain.repository.FoodLogRepository
import com.artishorizon.tidefit.domain.repository.HealthSyncSettings
import com.artishorizon.tidefit.domain.repository.SleepRepository
import com.artishorizon.tidefit.domain.repository.WaterRepository
import com.artishorizon.tidefit.domain.repository.WeightRepository
import com.artishorizon.tidefit.domain.repository.WorkoutRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.first
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.Period
import java.time.ZoneId
import java.time.ZoneOffset
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.reflect.KClass

enum class HealthAvailability { AVAILABLE, NOT_INSTALLED, UPDATE_REQUIRED, UNSUPPORTED }

/** What one sync did, for the summary line on the Data screen. */
data class HealthSyncResult(
    val weightsChanged: Int = 0,
    val nightsAdded: Int = 0,
    val activityDays: Int = 0,
    val written: Int = 0,
    val removed: Int = 0,
    /** Things that could not run (missing permission, background access not granted, a failed call). */
    val notes: List<String> = emptyList()
) {
    fun summary(): String {
        val parts = buildList {
            if (weightsChanged > 0) add("$weightsChanged weigh-in(s)")
            if (nightsAdded > 0) add("$nightsAdded night(s) of sleep")
            if (activityDays > 0) add("activity for $activityDays day(s)")
        }
        val imported = if (parts.isEmpty()) "Nothing new to import" else "Imported ${parts.joinToString(", ")}"
        val sent = when {
            written > 0 && removed > 0 -> ". Sent $written item(s), removed $removed"
            written > 0 -> ". Sent $written item(s) to Health Connect"
            removed > 0 -> ". Removed $removed item(s) from Health Connect"
            else -> ""
        }
        val extra = if (notes.isEmpty()) "" else " (${notes.joinToString("; ")})"
        return "$imported$sent$extra."
    }
}

/**
 * Two-way sync with Health Connect, a local on-device store (no network involved).
 *
 *  - Import: weight and sleep, plus daily steps, active energy and exercise time. Anything you logged by hand is never overwritten,
 *    and data TIDE Fit itself wrote is skipped so it does not come back as a duplicate.
 *  - Export (optional): your weight, water, meals and finished workouts. Each item has a stable id, so syncing again updates
 *    rather than duplicates, and deleting an item in TIDE Fit removes it from Health Connect too.
 */
@Singleton
class HealthConnectSource @Inject constructor(
    @ApplicationContext private val context: Context,
    private val weights: WeightRepository,
    private val sleep: SleepRepository,
    private val activity: ActivityRepository,
    private val water: WaterRepository,
    private val foodLog: FoodLogRepository,
    private val workouts: WorkoutRepository,
    private val settings: HealthSyncSettings,
    private val clock: Clock
) {
    private val readPermissions: Set<String> = setOf(
        HealthPermission.getReadPermission(WeightRecord::class),
        HealthPermission.getReadPermission(SleepSessionRecord::class),
        HealthPermission.getReadPermission(StepsRecord::class),
        HealthPermission.getReadPermission(ActiveCaloriesBurnedRecord::class),
        HealthPermission.getReadPermission(ExerciseSessionRecord::class)
    )
    private val writePermissions: Set<String> = setOf(
        HealthPermission.getWritePermission(WeightRecord::class),
        HealthPermission.getWritePermission(HydrationRecord::class),
        HealthPermission.getWritePermission(NutritionRecord::class),
        HealthPermission.getWritePermission(ExerciseSessionRecord::class)
    )

    /** Everything TIDE Fit may ask for. Health Connect lets the user grant any subset, and sync only uses what was granted. */
    val permissions: Set<String> = readPermissions + writePermissions + HealthPermission.PERMISSION_READ_HEALTH_DATA_IN_BACKGROUND

    fun requestPermissionContract(): ActivityResultContract<Set<String>, Set<String>> =
        PermissionController.createRequestPermissionResultContract()

    fun availability(): HealthAvailability = when (HealthConnectClient.getSdkStatus(context)) {
        HealthConnectClient.SDK_AVAILABLE -> HealthAvailability.AVAILABLE
        HealthConnectClient.SDK_UNAVAILABLE_PROVIDER_UPDATE_REQUIRED -> HealthAvailability.UPDATE_REQUIRED
        HealthConnectClient.SDK_UNAVAILABLE -> HealthAvailability.NOT_INSTALLED
        else -> HealthAvailability.UNSUPPORTED
    }

    suspend fun granted(): Set<String> {
        if (availability() != HealthAvailability.AVAILABLE) return emptySet()
        return HealthConnectClient.getOrCreate(context).permissionController.getGrantedPermissions()
    }

    fun canRead(granted: Set<String>) = granted.any { it in readPermissions }
    fun canWrite(granted: Set<String>) = granted.any { it in writePermissions }
    fun canReadInBackground(granted: Set<String>) = HealthPermission.PERMISSION_READ_HEALTH_DATA_IN_BACKGROUND in granted

    /**
     * Runs one sync. [background] is true when called from the scheduled job: Health Connect only allows reading in the background
     * with its own permission, so without it the import part is skipped (writing is still allowed) and the next time the app is
     * open it catches up.
     */
    suspend fun sync(background: Boolean = false, days: Int = WINDOW_DAYS): HealthSyncResult {
        if (availability() != HealthAvailability.AVAILABLE) return HealthSyncResult(notes = listOf("Health Connect isn't available"))
        val client = HealthConnectClient.getOrCreate(context)
        val granted = client.permissionController.getGrantedPermissions()
        val prefs = settings.current()
        val notes = mutableListOf<String>()
        val zone = ZoneId.systemDefault()
        val today = LocalDate.now(clock)
        val windowStart = today.minusDays(days.toLong())

        var weightsChanged = 0
        var nights = 0
        var activityDays = 0
        if (canRead(granted)) {
            if (background && !canReadInBackground(granted)) {
                notes += "background reading isn't allowed, so it will import next time you open the app"
            } else {
                runCatching { importWeightAndSleep(client, granted, zone, days) }
                    .onSuccess { weightsChanged = it.first; nights = it.second }
                    .onFailure { notes += "couldn't read weight or sleep" }
                runCatching { importActivity(client, granted, windowStart) }
                    .onSuccess { activityDays = it }
                    .onFailure { notes += "couldn't read activity" }
            }
        }

        var written = 0
        var removed = 0
        if (prefs.writeBack) {
            if (!canWrite(granted)) notes += "writing to Health Connect isn't allowed yet"
            else runCatching { export(client, granted, zone, windowStart) }
                .onSuccess { written = it.first; removed = it.second }
                .onFailure { notes += "couldn't write to Health Connect" }
        }

        val result = HealthSyncResult(weightsChanged, nights, activityDays, written, removed, notes)
        settings.recordSync(Instant.now(clock), result.summary())
        return result
    }

    // ---------------------------------------------------------------- import

    private suspend fun importWeightAndSleep(client: HealthConnectClient, granted: Set<String>, zone: ZoneId, days: Int): Pair<Int, Int> {
        val end = Instant.now(clock)
        val range = TimeRangeFilter.between(end.minus(Duration.ofDays(days.toLong())), end)
        var w = 0
        var s = 0
        if (HealthPermission.getReadPermission(WeightRecord::class) in granted) {
            val readings = readAll(client, WeightRecord::class, range) { r ->
                if (r.metadata.dataOrigin.packageName == context.packageName) null else HealthMapping.WeightReading(r.time, r.weight.inKilograms)
            }
            w = weights.importExternal(HealthMapping.dailyWeights(readings, zone), SOURCE)
        }
        if (HealthPermission.getReadPermission(SleepSessionRecord::class) in granted) {
            val sessions = readAll(client, SleepSessionRecord::class, range) { r ->
                val asleep = if (r.stages.isEmpty()) Duration.between(r.startTime, r.endTime)
                else r.stages.filter { it.stage !in NOT_ASLEEP }.fold(Duration.ZERO) { acc, st -> acc + Duration.between(st.startTime, st.endTime) }
                HealthMapping.SleepSession(r.endTime, asleep)
            }
            s = sleep.importExternal(HealthMapping.dailySleepMinutes(sessions, zone))
        }
        return w to s
    }

    /** Uses Health Connect's own aggregation so a phone and a watch counting the same steps are not added together. */
    private suspend fun importActivity(client: HealthConnectClient, granted: Set<String>, from: LocalDate): Int {
        val range = TimeRangeFilter.between(from.atStartOfDay(), LocalDateTime.now(clock).plusDays(1).toLocalDate().atStartOfDay())
        val steps = mutableMapOf<LocalDate, Int>()
        val kcal = mutableMapOf<LocalDate, Int>()
        val minutes = mutableMapOf<LocalDate, Int>()

        if (HealthPermission.getReadPermission(StepsRecord::class) in granted) {
            client.aggregateGroupByPeriod(AggregateGroupByPeriodRequest(setOf(StepsRecord.COUNT_TOTAL), range, Period.ofDays(1))).forEach {
                it.result[StepsRecord.COUNT_TOTAL]?.let { n -> steps[it.startTime.toLocalDate()] = n.toInt() }
            }
        }
        if (HealthPermission.getReadPermission(ActiveCaloriesBurnedRecord::class) in granted) {
            client.aggregateGroupByPeriod(AggregateGroupByPeriodRequest(setOf(ActiveCaloriesBurnedRecord.ACTIVE_CALORIES_TOTAL), range, Period.ofDays(1))).forEach {
                it.result[ActiveCaloriesBurnedRecord.ACTIVE_CALORIES_TOTAL]?.let { e -> kcal[it.startTime.toLocalDate()] = e.inKilocalories.toInt() }
            }
        }
        if (HealthPermission.getReadPermission(ExerciseSessionRecord::class) in granted) {
            client.aggregateGroupByPeriod(AggregateGroupByPeriodRequest(setOf(ExerciseSessionRecord.EXERCISE_DURATION_TOTAL), range, Period.ofDays(1))).forEach {
                it.result[ExerciseSessionRecord.EXERCISE_DURATION_TOTAL]?.let { d -> minutes[it.startTime.toLocalDate()] = d.toMinutes().toInt() }
            }
        }

        val rows = (steps.keys + kcal.keys + minutes.keys).sorted()
            .map { ActivityDay(it, steps[it] ?: 0, kcal[it] ?: 0, minutes[it] ?: 0) }
            .filter { it.steps > 0 || it.activeKcal > 0 || it.exerciseMinutes > 0 }
        activity.upsert(rows)
        return rows.size
    }

    private suspend fun <R : Record, T : Any> readAll(
        client: HealthConnectClient,
        type: KClass<R>,
        range: TimeRangeFilter,
        map: (R) -> T?
    ): List<T> {
        val out = mutableListOf<T>()
        var token: String? = null
        do {
            val page = client.readRecords(ReadRecordsRequest(type, range, pageToken = token))
            out += page.records.mapNotNull(map)
            token = page.pageToken
        } while (token != null)
        return out
    }

    // ---------------------------------------------------------------- export

    /** One thing to send: its fingerprint, and a builder so the record is only created when it actually has to be written. */
    private class Item(val prefix: String, val dayKey: Long, val hash: String, val build: (version: Long) -> Record)

    private suspend fun export(client: HealthConnectClient, granted: Set<String>, zone: ZoneId, windowStart: LocalDate): Pair<Int, Int> {
        val today = LocalDate.now(clock)
        val startInstant = windowStart.atStartOfDay(zone).toInstant()
        val items = linkedMapOf<String, Item>()
        val permitted = mutableSetOf<String>()

        fun offset(i: Instant): ZoneOffset = zone.rules.getOffset(i)
        fun meta(id: String, v: Long) = Metadata.manualEntry(clientRecordId = id, clientRecordVersion = v)

        if (HealthPermission.getWritePermission(WeightRecord::class) in granted) {
            permitted += P_WEIGHT
            weights.observeAll().first().filter { it.date >= windowStart && it.source != SOURCE }.forEach { e ->
                val id = "$P_WEIGHT${e.id}"
                val at = if (e.recordedAt != Instant.EPOCH && e.recordedAt.atZone(zone).toLocalDate() == e.date) e.recordedAt
                else e.date.atTime(12, 0).atZone(zone).toInstant()
                items[id] = Item(P_WEIGHT, e.date.toEpochDay(), "${e.weightKg}|${e.date}") { v ->
                    WeightRecord(at, offset(at), Mass.kilograms(e.weightKg), meta(id, v))
                }
            }
        }
        if (HealthPermission.getWritePermission(HydrationRecord::class) in granted) {
            permitted += P_WATER
            water.observeRange(windowStart, today).first().forEach { e ->
                val id = "$P_WATER${e.id}"
                items[id] = Item(P_WATER, e.date.toEpochDay(), "${e.amountMl}|${e.loggedAt.toEpochMilli()}") { v ->
                    HydrationRecord(e.loggedAt, offset(e.loggedAt), e.loggedAt.plusSeconds(1), offset(e.loggedAt), Volume.milliliters(e.amountMl.toDouble()), meta(id, v))
                }
            }
        }
        if (HealthPermission.getWritePermission(NutritionRecord::class) in granted) {
            permitted += P_MEAL
            foodLog.observeRange(windowStart, today).first().forEach { e ->
                val id = "$P_MEAL${e.id}"
                val n = e.total
                items[id] = Item(P_MEAL, e.date.toEpochDay(), "${e.name}|${e.meal}|${n.calories}|${n.protein}|${n.carbs}|${n.fat}|${n.fibre}|${e.loggedAt.toEpochMilli()}") { v ->
                    NutritionRecord(
                        startTime = e.loggedAt, startZoneOffset = offset(e.loggedAt),
                        endTime = e.loggedAt.plusSeconds(60), endZoneOffset = offset(e.loggedAt),
                        name = e.name,
                        mealType = mealType(e.meal),
                        energy = Energy.kilocalories(n.calories.coerceAtLeast(0.0)),
                        protein = Mass.grams(n.protein.coerceAtLeast(0.0)),
                        totalCarbohydrate = Mass.grams(n.carbs.coerceAtLeast(0.0)),
                        totalFat = Mass.grams(n.fat.coerceAtLeast(0.0)),
                        dietaryFiber = n.fibre?.let { Mass.grams(it.coerceAtLeast(0.0)) },
                        sugar = n.sugar?.let { Mass.grams(it.coerceAtLeast(0.0)) },
                        saturatedFat = n.saturatedFat?.let { Mass.grams(it.coerceAtLeast(0.0)) },
                        sodium = n.sodiumMg?.let { Mass.milligrams(it.coerceAtLeast(0.0)) },
                        metadata = meta(id, v)
                    )
                }
            }
        }
        if (HealthPermission.getWritePermission(ExerciseSessionRecord::class) in granted) {
            permitted += P_WORKOUT
            workouts.observeHistory().first().filter { it.endedAt != null && it.startedAt >= startInstant }.forEach { w ->
                val end = w.endedAt ?: return@forEach
                if (!end.isAfter(w.startedAt)) return@forEach
                val id = "$P_WORKOUT${w.id}"
                items[id] = Item(P_WORKOUT, w.startedAt.atZone(zone).toLocalDate().toEpochDay(), "${w.name}|${w.startedAt.toEpochMilli()}|${end.toEpochMilli()}|${w.setCount}") { v ->
                    ExerciseSessionRecord(
                        startTime = w.startedAt, startZoneOffset = offset(w.startedAt), endTime = end, endZoneOffset = offset(end),
                        exerciseType = ExerciseSessionRecord.EXERCISE_TYPE_STRENGTH_TRAINING, title = w.name, metadata = meta(id, v)
                    )
                }
            }
        }

        // Compare with what the last sync sent, ignoring anything that has aged out of the window or belongs to a type we may not touch now.
        val stored = settings.exported()
        val storedInScope = stored.filter { (id, v) -> permitted.any { id.startsWith(it) } && (v.substringBefore('#').toLongOrNull() ?: 0L) >= windowStart.toEpochDay() }
            .mapValues { it.value.substringAfter('#') }
        val plan = HealthExportPlan.diff(items.mapValues { it.value.hash }, storedInScope)

        val version = Instant.now(clock).toEpochMilli()
        val records = plan.upsert.mapNotNull { items[it]?.build?.invoke(version) }
        records.chunked(500).forEach { client.insertRecords(it) }

        var removed = 0
        plan.delete.groupBy { id -> typeFor(id) }.forEach { (type, ids) ->
            if (type != null) { client.deleteRecords(type, emptyList(), ids.toList()); removed += ids.size }
        }

        // Remember what is now in Health Connect: this window's items, plus untouched entries for types we could not process.
        val kept = stored.filter { (id, v) -> permitted.none { id.startsWith(it) } && (v.substringBefore('#').toLongOrNull() ?: 0L) >= windowStart.toEpochDay() }
        settings.setExported(kept + items.mapValues { "${it.value.dayKey}#${it.value.hash}" })
        return records.size to removed
    }

    private fun typeFor(id: String): KClass<out Record>? = when {
        id.startsWith(P_WEIGHT) -> WeightRecord::class
        id.startsWith(P_WATER) -> HydrationRecord::class
        id.startsWith(P_MEAL) -> NutritionRecord::class
        id.startsWith(P_WORKOUT) -> ExerciseSessionRecord::class
        else -> null
    }

    private fun mealType(m: Meal): Int = when (m) {
        Meal.BREAKFAST -> MealType.MEAL_TYPE_BREAKFAST
        Meal.LUNCH -> MealType.MEAL_TYPE_LUNCH
        Meal.DINNER -> MealType.MEAL_TYPE_DINNER
        Meal.MORNING_SNACK, Meal.EVENING_SNACK -> MealType.MEAL_TYPE_SNACK
        Meal.OTHER -> MealType.MEAL_TYPE_UNKNOWN
    }

    companion object {
        const val SOURCE = "HEALTH_CONNECT"
        const val WINDOW_DAYS = 30
        private const val P_WEIGHT = "tf-weight-"
        private const val P_WATER = "tf-water-"
        private const val P_MEAL = "tf-meal-"
        private const val P_WORKOUT = "tf-workout-"
        private val NOT_ASLEEP = setOf(
            SleepSessionRecord.STAGE_TYPE_AWAKE, SleepSessionRecord.STAGE_TYPE_AWAKE_IN_BED, SleepSessionRecord.STAGE_TYPE_OUT_OF_BED
        )
    }
}
