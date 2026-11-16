package com.fitos.app.domain.coaching

import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import java.security.MessageDigest
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * Strict-on-required, tolerant-on-extra parsing and validation of a pasted ChatGPT response.
 * Pure: no database, no clock. Returns every problem found so the user can fix the response in one go.
 */
object CoachingImport {

    sealed interface Result {
        data class Valid(
            val update: CoachingUpdate,
            /** Fully merged proposed targets (missing optional values filled from the current targets), or null. */
            val targets: TargetsDto?,
            val warnings: List<String>,
            /** SHA-256 of the parsed JSON, used to detect the same response being imported twice. */
            val hash: String
        ) : Result

        data class Invalid(val errors: List<String>) : Result
    }

    const val MAX_INPUT_CHARS = 200_000
    private const val MAX_TEXT_CHARS = 4_000

    private val json = Json { ignoreUnknownKeys = true }

    fun parse(raw: String, current: TargetsDto?): Result {
        if (raw.isBlank()) return Result.Invalid(listOf("Paste ChatGPT's JSON response first."))
        if (raw.length > MAX_INPUT_CHARS) return Result.Invalid(listOf("That text is too long to be a coaching response."))
        val body = extractJson(raw) ?: return Result.Invalid(listOf("No JSON object found. Paste the whole response, including the { and } braces."))

        val element = try {
            json.parseToJsonElement(body)
        } catch (e: SerializationException) {
            return Result.Invalid(listOf("This isn't valid JSON (${e.message?.lineSequence()?.first()?.take(120)}). Ask ChatGPT to return only valid JSON."))
        } catch (e: IllegalArgumentException) {
            return Result.Invalid(listOf("This isn't valid JSON. Ask ChatGPT to return only valid JSON."))
        }
        if (element !is JsonObject) return Result.Invalid(listOf("The response must be a JSON object."))

        val update = try {
            json.decodeFromJsonElement(CoachingUpdate.serializer(), element)
        } catch (e: SerializationException) {
            return Result.Invalid(listOf("A field has the wrong type or shape: ${e.message?.lineSequence()?.first()?.take(160)}"))
        } catch (e: IllegalArgumentException) {
            return Result.Invalid(listOf("A field has the wrong type or shape."))
        }

        val errors = mutableListOf<String>()
        val warnings = mutableListOf<String>()

        when {
            update.type != Contract.IMPORT_TYPE -> errors += "\"type\" must be \"${Contract.IMPORT_TYPE}\" (got ${update.type?.let { "\"${it.take(40)}\"" } ?: "nothing"})."
        }
        val version = update.schemaVersion
        if (version == null) errors += "\"schema_version\" is missing."
        else if (version.substringBefore('.') != Contract.SCHEMA_VERSION.substringBefore('.')) errors += "Unsupported schema_version \"${version.take(20)}\". This app understands ${Contract.SCHEMA_VERSION}."

        val allText = listOfNotNull(update.analysis?.summary, update.coachNotes, update.updatedTargets?.reason) +
            (update.analysis?.observations.orEmpty()) +
            (update.recommendations + update.workoutSuggestions + update.habitSuggestions).flatMap { listOfNotNull(it.title, it.detail) }
        if (allText.any { it.length > MAX_TEXT_CHARS }) errors += "A text field is longer than $MAX_TEXT_CHARS characters."

        val hasContent = !update.analysis?.summary.isNullOrBlank() || !update.coachNotes.isNullOrBlank() || update.updatedTargets != null ||
            update.recommendations.isNotEmpty() || update.workoutSuggestions.isNotEmpty() || update.habitSuggestions.isNotEmpty()
        if (!hasContent) errors += "The response has nothing to review (no summary, targets or suggestions)."

        val merged = update.updatedTargets?.let { mergeTargets(it, current, errors, warnings) }

        if (errors.isNotEmpty()) return Result.Invalid(errors)
        return Result.Valid(update, merged, warnings, sha256(element.toString()))
    }

    /** Takes the outermost {...}, which also handles ```json fences and chatter before/after the JSON. */
    internal fun extractJson(raw: String): String? {
        val start = raw.indexOf('{')
        val end = raw.lastIndexOf('}')
        return if (start in 0 until end) raw.substring(start, end + 1) else null
    }

    private fun mergeTargets(p: ProposedTargets, current: TargetsDto?, errors: MutableList<String>, warnings: MutableList<String>): TargetsDto? {
        if (p.caloriesKcal == null && p.proteinG == null && p.carbsG == null && p.fatG == null && p.fibreG == null && p.waterMl == null) {
            errors += "\"updated_targets\" is present but has no values."
            return null
        }
        if (current == null && (p.caloriesKcal == null || p.proteinG == null || p.carbsG == null || p.fatG == null)) {
            errors += "You have no targets yet, so \"updated_targets\" must include calories, protein, carbs and fat."
            return null
        }
        val before = errors.size
        fun check(name: String, value: Int?, range: IntRange) {
            if (value != null && value !in range) errors += "$name $value is outside the allowed range ${range.first}–${range.last}."
        }
        check("Calories", p.caloriesKcal, 800..6000)
        check("Protein", p.proteinG, 20..400)
        check("Carbs", p.carbsG, 0..800)
        check("Fat", p.fatG, 10..300)
        check("Fibre", p.fibreG, 0..150)
        check("Water", p.waterMl, 500..8000)
        if (errors.size > before) return null

        val merged = TargetsDto(
            caloriesKcal = p.caloriesKcal ?: current!!.caloriesKcal,
            proteinG = p.proteinG ?: current!!.proteinG,
            carbsG = p.carbsG ?: current!!.carbsG,
            fatG = p.fatG ?: current!!.fatG,
            fibreG = p.fibreG ?: current?.fibreG ?: 25,
            waterMl = p.waterMl ?: current?.waterMl ?: 2500
        )

        val macroKcal = merged.proteinG * 4 + merged.carbsG * 4 + merged.fatG * 9
        val gap = abs(macroKcal - merged.caloriesKcal)
        if (gap > merged.caloriesKcal * 0.15) {
            warnings += "The macros add up to about $macroKcal kcal but the calorie target is ${merged.caloriesKcal} kcal (${(gap * 100.0 / merged.caloriesKcal).roundToInt()}% apart)."
        }
        if (merged.caloriesKcal < 1200) warnings += "A target under 1200 kcal/day is very low. Consider checking with a professional."
        if (current != null && current.caloriesKcal > 0) {
            val change = abs(merged.caloriesKcal - current.caloriesKcal) * 100.0 / current.caloriesKcal
            if (change > 25) warnings += "This changes your calories by ${change.roundToInt()}%, which is a large jump."
        }
        return merged
    }

    private fun sha256(s: String): String =
        MessageDigest.getInstance("SHA-256").digest(s.toByteArray()).joinToString("") { "%02x".format(it) }
}
