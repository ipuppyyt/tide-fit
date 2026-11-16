package com.artishorizon.tidefit.domain.coaching

import com.artishorizon.tidefit.domain.calc.BodyCalc
import com.artishorizon.tidefit.domain.model.BeverageKind
import com.artishorizon.tidefit.domain.model.DailyTargets
import com.artishorizon.tidefit.domain.model.FoodEntry
import com.artishorizon.tidefit.domain.model.RecoveryEntry
import com.artishorizon.tidefit.domain.model.SleepEntry
import com.artishorizon.tidefit.domain.model.Supplement
import com.artishorizon.tidefit.domain.model.SupplementLog
import com.artishorizon.tidefit.domain.model.UserProfile
import com.artishorizon.tidefit.domain.model.WaterEntry
import com.artishorizon.tidefit.domain.model.WeightEntry
import com.artishorizon.tidefit.domain.model.WorkoutSummary
import kotlinx.serialization.json.Json
import java.time.LocalDate
import java.time.ZoneId
import kotlin.math.roundToInt

/** Builds the export document from app data. Pure, so the exact JSON is unit-testable. */
object CoachingExport {
    const val DEFAULT_DAYS = 14

    private val pretty = Json { prettyPrint = true; encodeDefaults = true }

    fun build(
        today: LocalDate,
        days: Int,
        profile: UserProfile?,
        targets: DailyTargets?,
        weights: List<WeightEntry>,
        food: List<FoodEntry>,
        water: List<WaterEntry>,
        workouts: List<WorkoutSummary>,
        sleep: List<SleepEntry>,
        recovery: List<RecoveryEntry>,
        supplements: List<Supplement>,
        supplementLogs: List<SupplementLog>,
        zone: ZoneId = ZoneId.systemDefault()
    ): ExportDocument {
        val from = today.minusDays(days.toLong() - 1)
        fun inRange(d: LocalDate) = !d.isBefore(from) && !d.isAfter(today)

        val weightsInRange = weights.filter { inRange(it.date) }.sortedBy { it.date }
        val foodByDay = food.filter { inRange(it.date) }.groupBy { it.date }
        val waterByDay = water.filter { inRange(it.date) && it.kind == BeverageKind.WATER }.groupBy { it.date }
        val nutrition = foodByDay.toSortedMap().map { (date, entries) ->
            val total = entries.map { it.total }.reduce { a, b -> a + b }
            ExportNutritionDay(
                date.toString(), total.calories.roundToInt(), total.protein.roundToInt(), total.carbs.roundToInt(), total.fat.roundToInt(),
                waterByDay[date]?.sumOf { it.amountMl }
            )
        }
        val workoutRows = workouts.filter { it.endedAt != null }
            .map { it to it.startedAt.atZone(zone).toLocalDate() }
            .filter { inRange(it.second) }
            .sortedBy { it.first.startedAt }
            .map { (w, d) -> ExportWorkout(d.toString(), w.name, w.exerciseCount, w.setCount, w.volumeKg.roundToInt()) }
        val sleepRows = sleep.filter { inRange(it.date) }.sortedBy { it.date }.map { ExportSleep(it.date.toString(), it.durationMinutes, it.quality) }
        val recoveryRows = recovery.filter { inRange(it.date) }.sortedBy { it.date }.map { ExportRecovery(it.date.toString(), it.soreness, it.energy, it.stress) }
        val adherence = BodyCalc.supplementAdherence(supplements, supplementLogs, today, days)

        return ExportDocument(
            generatedOn = today.toString(),
            period = ExportPeriod(from.toString(), today.toString(), days),
            profile = profile?.let {
                ExportProfile(
                    it.age, it.sex?.name, it.heightCm, it.currentWeightKg, it.targetWeightKg, it.goal.name, it.experience.name,
                    it.trainingDaysPerWeek, it.activityLevel.name, it.dietaryPreferences, it.foodRestrictions, it.allergies, it.equipment
                )
            },
            currentTargets = targets?.let { TargetsDto(it.caloriesKcal, it.proteinG, it.carbohydratesG, it.fatG, it.fibreG, it.waterMl) },
            weight = ExportWeight(
                weightsInRange.map { ExportWeightEntry(it.date.toString(), it.weightKg) },
                BodyCalc.weightTrend(weights).lastOrNull()?.trendKg?.let { Math.round(it * 10) / 10.0 },
                BodyCalc.weeklyRateKg(weights, today)?.let { Math.round(it * 100) / 100.0 }
            ),
            nutrition = nutrition,
            training = workoutRows,
            sleep = sleepRows,
            recovery = recoveryRows,
            supplementAdherencePercent = adherence?.let { (it.fraction * 100).roundToInt() },
            dataQuality = ExportDataQuality(days, nutrition.size, weightsInRange.map { it.date }.distinct().size, workoutRows.size, sleepRows.size)
        )
    }

    fun toJson(doc: ExportDocument): String = pretty.encodeToString(ExportDocument.serializer(), doc)

    /** The instructions the user pastes into ChatGPT together with the export. */
    val PROMPT: String = """
You are my fitness and nutrition coach. Below is a JSON export of my own tracked data from the TIDE Fit app.

How to read it:
- Days, nights or weigh-ins that are missing were NOT LOGGED. Never treat a missing day as zero intake, zero sleep or a rest day.
- "data_quality" tells you how complete the data is. If it is thin, say so and be cautious instead of guessing.
- Weights are in kg, lengths in cm.

What I want from you:
1. Analyse my adherence, weight trend, nutrition, training, sleep and recovery.
2. Only if the data justifies it, propose new daily targets. If nothing should change, set "updated_targets" to null.
3. Give practical recommendations. Do not give medical advice or diagnoses.

Reply with ONLY one JSON object, no other text, in exactly this shape (schema ${Contract.SCHEMA_VERSION}):
{
  "schema_version": "${Contract.SCHEMA_VERSION}",
  "type": "${Contract.IMPORT_TYPE}",
  "analysis": { "summary": "2-4 sentences", "observations": ["short point", "..."] },
  "updated_targets": { "calories_kcal": 0, "protein_g": 0, "carbs_g": 0, "fat_g": 0, "fibre_g": 0, "water_ml": 0, "reason": "why" },
  "recommendations": [ { "title": "short", "detail": "one or two sentences" } ],
  "workout_suggestions": [ { "title": "short", "detail": "one or two sentences" } ],
  "habit_suggestions": [ { "title": "short", "detail": "one or two sentences" } ],
  "coach_notes": "anything else worth saying",
  "requires_user_confirmation": true
}
Whole numbers only for targets. Use null for "updated_targets" when no change is needed.
""".trimIndent()
}
