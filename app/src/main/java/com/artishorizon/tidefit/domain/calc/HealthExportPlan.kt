package com.artishorizon.tidefit.domain.calc

/**
 * Decides what to send to Health Connect so repeated syncs never create duplicates. Each local item has a stable client id
 * and a fingerprint of its content; only new or changed items are written, and items that disappeared are deleted.
 * Pure, so it is unit-testable without Health Connect.
 */
object HealthExportPlan {
    data class Plan(val upsert: Set<String>, val delete: Set<String>)

    /**
     * @param current id to fingerprint for everything inside the sync window right now.
     * @param previous id to fingerprint as of the last successful sync (ids older than the window were already dropped by the caller).
     */
    fun diff(current: Map<String, String>, previous: Map<String, String>): Plan = Plan(
        upsert = current.filter { (id, hash) -> previous[id] != hash }.keys,
        delete = previous.keys - current.keys
    )
}
