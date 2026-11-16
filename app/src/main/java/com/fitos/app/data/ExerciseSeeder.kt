package com.fitos.app.data

import com.fitos.app.core.database.ExerciseDao
import com.fitos.app.core.database.ExerciseEntity
import com.fitos.app.domain.model.EquipmentType
import com.fitos.app.domain.model.EquipmentType.BARBELL
import com.fitos.app.domain.model.EquipmentType.BODYWEIGHT
import com.fitos.app.domain.model.EquipmentType.CABLE
import com.fitos.app.domain.model.EquipmentType.DUMBBELL
import com.fitos.app.domain.model.EquipmentType.KETTLEBELL
import com.fitos.app.domain.model.EquipmentType.MACHINE
import com.fitos.app.domain.model.MuscleGroup
import com.fitos.app.domain.model.MuscleGroup.BACK
import com.fitos.app.domain.model.MuscleGroup.BICEPS
import com.fitos.app.domain.model.MuscleGroup.CALVES
import com.fitos.app.domain.model.MuscleGroup.CARDIO
import com.fitos.app.domain.model.MuscleGroup.CHEST
import com.fitos.app.domain.model.MuscleGroup.CORE
import com.fitos.app.domain.model.MuscleGroup.FOREARMS
import com.fitos.app.domain.model.MuscleGroup.FULL_BODY
import com.fitos.app.domain.model.MuscleGroup.GLUTES
import com.fitos.app.domain.model.MuscleGroup.HAMSTRINGS
import com.fitos.app.domain.model.MuscleGroup.QUADS
import com.fitos.app.domain.model.MuscleGroup.SHOULDERS
import com.fitos.app.domain.model.MuscleGroup.TRICEPS
import java.time.Clock
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton

/** A starter exercise library of common lifts. Users can add their own; nothing here is a prescription. */
@Singleton
class ExerciseSeeder @Inject constructor(private val dao: ExerciseDao, private val clock: Clock) {

    suspend fun seedIfNeeded() {
        if (dao.countSeed() > 0) return
        val now = Instant.now(clock)
        dao.insertAll(SEED.map { (name, primary, equipment, secondary) ->
            ExerciseEntity(
                name = name, primaryMuscle = primary.name, secondaryMuscles = secondary.map { it.name }, equipment = equipment.name,
                notes = null, isCustom = false, isSeed = true, isArchived = false, createdAt = now
            )
        })
    }

    internal data class Seed(val name: String, val primary: MuscleGroup, val equipment: EquipmentType, val secondary: List<MuscleGroup>)

    companion object {
        private fun e(name: String, primary: MuscleGroup, equipment: EquipmentType, vararg secondary: MuscleGroup) =
            Seed(name, primary, equipment, secondary.toList())

        internal val SEED: List<Seed> = listOf(
            // Chest
            e("Barbell bench press", CHEST, BARBELL, TRICEPS, SHOULDERS),
            e("Incline barbell bench press", CHEST, BARBELL, SHOULDERS, TRICEPS),
            e("Dumbbell bench press", CHEST, DUMBBELL, TRICEPS, SHOULDERS),
            e("Incline dumbbell press", CHEST, DUMBBELL, SHOULDERS, TRICEPS),
            e("Push-up", CHEST, BODYWEIGHT, TRICEPS, SHOULDERS),
            e("Chest dip", CHEST, BODYWEIGHT, TRICEPS),
            e("Cable fly", CHEST, CABLE),
            e("Dumbbell fly", CHEST, DUMBBELL),
            e("Machine chest press", CHEST, MACHINE, TRICEPS),
            // Back
            e("Pull-up", BACK, BODYWEIGHT, BICEPS),
            e("Chin-up", BACK, BODYWEIGHT, BICEPS),
            e("Lat pulldown", BACK, CABLE, BICEPS),
            e("Barbell row", BACK, BARBELL, BICEPS),
            e("One-arm dumbbell row", BACK, DUMBBELL, BICEPS),
            e("Seated cable row", BACK, CABLE, BICEPS),
            e("Deadlift", BACK, BARBELL, HAMSTRINGS, GLUTES),
            e("Face pull", SHOULDERS, CABLE, BACK),
            // Shoulders
            e("Overhead press", SHOULDERS, BARBELL, TRICEPS),
            e("Dumbbell shoulder press", SHOULDERS, DUMBBELL, TRICEPS),
            e("Lateral raise", SHOULDERS, DUMBBELL),
            e("Rear delt fly", SHOULDERS, DUMBBELL, BACK),
            e("Barbell shrug", BACK, BARBELL),
            // Arms
            e("Barbell curl", BICEPS, BARBELL, FOREARMS),
            e("Dumbbell curl", BICEPS, DUMBBELL),
            e("Hammer curl", BICEPS, DUMBBELL, FOREARMS),
            e("Cable curl", BICEPS, CABLE),
            e("Triceps pushdown", TRICEPS, CABLE),
            e("Overhead triceps extension", TRICEPS, DUMBBELL),
            e("Skull crusher", TRICEPS, BARBELL),
            e("Close-grip bench press", TRICEPS, BARBELL, CHEST),
            e("Bench dip", TRICEPS, BODYWEIGHT),
            e("Wrist curl", FOREARMS, DUMBBELL),
            // Legs
            e("Back squat", QUADS, BARBELL, GLUTES, HAMSTRINGS),
            e("Front squat", QUADS, BARBELL, GLUTES),
            e("Goblet squat", QUADS, DUMBBELL, GLUTES),
            e("Leg press", QUADS, MACHINE, GLUTES),
            e("Bulgarian split squat", QUADS, DUMBBELL, GLUTES),
            e("Walking lunge", QUADS, DUMBBELL, GLUTES),
            e("Leg extension", QUADS, MACHINE),
            e("Romanian deadlift", HAMSTRINGS, BARBELL, GLUTES, BACK),
            e("Leg curl", HAMSTRINGS, MACHINE),
            e("Hip thrust", GLUTES, BARBELL, HAMSTRINGS),
            e("Glute bridge", GLUTES, BODYWEIGHT, HAMSTRINGS),
            e("Standing calf raise", CALVES, MACHINE),
            e("Seated calf raise", CALVES, MACHINE),
            // Core
            e("Plank", CORE, BODYWEIGHT),
            e("Hanging leg raise", CORE, BODYWEIGHT),
            e("Cable crunch", CORE, CABLE),
            e("Ab wheel rollout", CORE, BODYWEIGHT),
            e("Russian twist", CORE, BODYWEIGHT),
            // Full body / conditioning
            e("Kettlebell swing", FULL_BODY, KETTLEBELL, GLUTES, HAMSTRINGS),
            e("Burpee", FULL_BODY, BODYWEIGHT),
            e("Farmer's carry", FULL_BODY, DUMBBELL, FOREARMS, CORE),
            e("Running", CARDIO, BODYWEIGHT),
            e("Cycling", CARDIO, MACHINE),
            e("Rowing machine", CARDIO, MACHINE, BACK),
            e("Skipping rope", CARDIO, EquipmentType.OTHER, CALVES)
        )
    }
}
