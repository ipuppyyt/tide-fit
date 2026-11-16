package com.artishorizon.tidefit.feature.profile.form

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.artishorizon.tidefit.core.designsystem.Sizes
import com.artishorizon.tidefit.core.designsystem.Spacing
import com.artishorizon.tidefit.core.designsystem.components.ChipGroup
import com.artishorizon.tidefit.core.designsystem.components.FitTextField
import com.artishorizon.tidefit.core.designsystem.components.OptionCard
import com.artishorizon.tidefit.domain.calc.Units
import com.artishorizon.tidefit.domain.model.ActivityLevel
import com.artishorizon.tidefit.domain.model.BiologicalSex
import com.artishorizon.tidefit.domain.model.FitnessExperience
import com.artishorizon.tidefit.domain.model.FitnessGoalType
import com.artishorizon.tidefit.domain.model.HeightUnit
import com.artishorizon.tidefit.domain.model.Options
import com.artishorizon.tidefit.domain.model.WeightUnit

typealias FormUpdate = ((ProfileForm) -> ProfileForm) -> Unit

@Composable
private fun Label(text: String) =
    Text(text, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)

@Composable
fun BasicsSection(form: ProfileForm, update: FormUpdate) {
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.lg)) {
        FitTextField(form.name, { v -> update { it.copy(name = v) } }, "Name")
        FitTextField(form.age, { v -> update { it.copy(age = v.filter(Char::isDigit).take(3)) } }, "Age", keyboardType = KeyboardType.Number)
        Label("Biological sex (optional)")
        Text(
            "Used only in the calorie estimate formula. Skip it and a neutral midpoint is used.",
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        ChipGroup(
            options = BiologicalSex.entries.map { it.label },
            selected = setOfNotNull(form.sex?.label),
            onToggle = { label ->
                val picked = BiologicalSex.entries.first { it.label == label }
                update { it.copy(sex = if (it.sex == picked) null else picked) }
            }
        )
    }
}

@Composable
fun BodySection(form: ProfileForm, update: FormUpdate) {
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.lg)) {
        Label("Units")
        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            ChipGroup(
                WeightUnit.entries.map { it.label },
                setOf(form.weightUnit.label),
                { label ->
                    val to = WeightUnit.entries.first { it.label == label }
                    update { it.convertWeightUnit(to) }
                }
            )
            ChipGroup(
                HeightUnit.entries.map { it.label },
                setOf(form.heightUnit.label),
                { label -> update { f -> f.copy(heightUnit = HeightUnit.entries.first { it.label == label }) } }
            )
        }
        if (form.heightUnit == HeightUnit.CM) {
            FitTextField(form.heightCm, { v -> update { it.copy(heightCm = v) } }, "Height", keyboardType = KeyboardType.Decimal, suffix = "cm")
        } else {
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.md)) {
                FitTextField(form.heightFeet, { v -> update { it.copy(heightFeet = v.filter(Char::isDigit).take(1)) } }, "Feet", Modifier.weight(1f), KeyboardType.Number, "ft")
                FitTextField(form.heightInches, { v -> update { it.copy(heightInches = v) } }, "Inches", Modifier.weight(1f), KeyboardType.Decimal, "in")
            }
        }
        val u = form.weightUnit.label
        FitTextField(form.initialWeight, { v -> update { it.copy(initialWeight = v) } }, "Initial weight (when you started)", keyboardType = KeyboardType.Decimal, suffix = u)
        FitTextField(form.currentWeight, { v -> update { it.copy(currentWeight = v) } }, "Current weight", keyboardType = KeyboardType.Decimal, suffix = u)
        FitTextField(form.targetWeight, { v -> update { it.copy(targetWeight = v) } }, "Target weight", keyboardType = KeyboardType.Decimal, suffix = u)
    }
}

/** Converts already-typed weights so switching units doesn't silently reinterpret the numbers. */
private fun ProfileForm.convertWeightUnit(to: WeightUnit): ProfileForm {
    if (to == weightUnit) return this
    fun conv(text: String): String =
        text.toNumberOrNull()?.let { Units.round1(Units.fromKg(Units.toKg(it, weightUnit), to)).toString() } ?: text
    return copy(
        weightUnit = to,
        initialWeight = conv(initialWeight),
        currentWeight = conv(currentWeight),
        targetWeight = conv(targetWeight)
    )
}

@Composable
fun GoalSection(form: ProfileForm, update: FormUpdate) {
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
        FitnessGoalType.entries.forEach { g ->
            OptionCard(g.label, g.hint, form.goal == g, { update { it.copy(goal = g) } })
        }
    }
}

@Composable
fun TrainingSection(form: ProfileForm, update: FormUpdate) {
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.lg)) {
        Label("Fitness experience")
        Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            FitnessExperience.entries.forEach { e ->
                OptionCard(e.label, e.hint, form.experience == e, { update { it.copy(experience = e) } })
            }
        }
        Label("Training days per week")
        ChipGroup(
            (0..7).map { it.toString() },
            setOf(form.trainingDays.toString()),
            { v -> update { it.copy(trainingDays = v.toInt()) } }
        )
        Label("Daily activity level (outside training)")
        Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            ActivityLevel.entries.forEach { a ->
                OptionCard(a.label, a.hint, form.activity == a, { update { it.copy(activity = a) } })
            }
        }
    }
}

private fun Set<String>.toggle(v: String) = if (v in this) this - v else this + v

@Composable
fun DietSection(form: ProfileForm, update: FormUpdate) {
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.lg)) {
        Label("Dietary preferences")
        ChipGroup(Options.dietaryPreferences, form.diet, { v -> update { it.copy(diet = it.diet.toggle(v)) } })
        Label("Food restrictions")
        ChipGroup(Options.restrictions, form.restrictions, { v -> update { it.copy(restrictions = it.restrictions.toggle(v)) } })
        FitTextField(form.restrictionsOther, { v -> update { it.copy(restrictionsOther = v) } }, "Other restrictions (comma separated)")
        Label("Food allergies")
        ChipGroup(Options.allergies, form.allergies, { v -> update { it.copy(allergies = it.allergies.toggle(v)) } })
        FitTextField(form.allergiesOther, { v -> update { it.copy(allergiesOther = v) } }, "Other allergies (comma separated)")
    }
}

@Composable
fun EquipmentSection(form: ProfileForm, update: FormUpdate) {
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.lg)) {
        Label("Equipment you can use")
        ChipGroup(Options.equipment, form.equipment, { v -> update { it.copy(equipment = it.equipment.toggle(v)) } })
    }
}
