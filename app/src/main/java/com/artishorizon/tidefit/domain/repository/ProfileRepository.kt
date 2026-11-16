package com.artishorizon.tidefit.domain.repository

import com.artishorizon.tidefit.domain.model.DailyTargets
import com.artishorizon.tidefit.domain.model.UserProfile
import kotlinx.coroutines.flow.Flow

interface ProfileRepository {
    fun observeProfile(): Flow<UserProfile?>
    fun observeCurrentTargets(): Flow<DailyTargets?>

    /**
     * Saves the profile, records the initial weight and goal, and (only if [startingTargets] is
     * non-null, i.e. the user explicitly accepted the suggestion) stores starting targets.
     * All writes happen in one transaction.
     */
    suspend fun completeOnboarding(profile: UserProfile, startingTargets: DailyTargets?)

    /** Updates profile fields; records a goal-history row when the goal changed. */
    suspend fun updateProfile(profile: UserProfile)

    /** Appends a new targets row (history is preserved, never overwritten). */
    suspend fun saveTargets(targets: DailyTargets)
}
