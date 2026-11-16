package com.fitos.app.data.health

import android.content.Context
import androidx.activity.result.contract.ActivityResultContract
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.PermissionController
import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.SleepSessionRecord
import androidx.health.connect.client.records.WeightRecord
import androidx.health.connect.client.request.ReadRecordsRequest
import androidx.health.connect.client.time.TimeRangeFilter
import com.fitos.app.domain.calc.HealthMapping
import com.fitos.app.domain.repository.SleepRepository
import com.fitos.app.domain.repository.WeightRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import javax.inject.Inject
import javax.inject.Singleton

enum class HealthAvailability { AVAILABLE, NOT_INSTALLED, UPDATE_REQUIRED, UNSUPPORTED }

data class HealthImportResult(val weightsChanged: Int, val nightsAdded: Int)

/**
 * Read-only, on-demand import of weight and sleep from Health Connect (a local on-device store; no network involved).
 * Nothing is written back to Health Connect and nothing syncs in the background.
 */
@Singleton
class HealthConnectSource @Inject constructor(
    @ApplicationContext private val context: Context,
    private val weights: WeightRepository,
    private val sleep: SleepRepository,
    private val clock: Clock
) {
    val permissions: Set<String> = setOf(
        HealthPermission.getReadPermission(WeightRecord::class),
        HealthPermission.getReadPermission(SleepSessionRecord::class)
    )

    fun requestPermissionContract(): ActivityResultContract<Set<String>, Set<String>> =
        PermissionController.createRequestPermissionResultContract()

    fun availability(): HealthAvailability = when (HealthConnectClient.getSdkStatus(context)) {
        HealthConnectClient.SDK_AVAILABLE -> HealthAvailability.AVAILABLE
        HealthConnectClient.SDK_UNAVAILABLE_PROVIDER_UPDATE_REQUIRED -> HealthAvailability.UPDATE_REQUIRED
        HealthConnectClient.SDK_UNAVAILABLE -> HealthAvailability.NOT_INSTALLED
        else -> HealthAvailability.UNSUPPORTED
    }

    suspend fun hasPermissions(): Boolean {
        if (availability() != HealthAvailability.AVAILABLE) return false
        return HealthConnectClient.getOrCreate(context).permissionController.getGrantedPermissions().containsAll(permissions)
    }

    /** Imports the last [days] days. Entries the user logged by hand are never overwritten. */
    suspend fun importRecent(days: Int = 30): HealthImportResult {
        val client = HealthConnectClient.getOrCreate(context)
        val zone = ZoneId.systemDefault()
        val end = Instant.now(clock)
        val range = TimeRangeFilter.between(end.minus(Duration.ofDays(days.toLong())), end)

        val weightReadings = readAll(client, WeightRecord::class, range) { r -> HealthMapping.WeightReading(r.time, r.weight.inKilograms) }
        val sessions = readAll(client, SleepSessionRecord::class, range) { r ->
            val asleep = if (r.stages.isEmpty()) Duration.between(r.startTime, r.endTime)
            else r.stages.filter { it.stage !in NOT_ASLEEP }.fold(Duration.ZERO) { acc, s -> acc + Duration.between(s.startTime, s.endTime) }
            HealthMapping.SleepSession(r.endTime, asleep)
        }

        val w = weights.importExternal(HealthMapping.dailyWeights(weightReadings, zone), SOURCE)
        val s = sleep.importExternal(HealthMapping.dailySleepMinutes(sessions, zone))
        return HealthImportResult(w, s)
    }

    private suspend fun <R : androidx.health.connect.client.records.Record, T> readAll(
        client: HealthConnectClient,
        type: kotlin.reflect.KClass<R>,
        range: TimeRangeFilter,
        map: (R) -> T
    ): List<T> {
        val out = mutableListOf<T>()
        var token: String? = null
        do {
            val page = client.readRecords(ReadRecordsRequest(type, range, pageToken = token))
            out += page.records.map(map)
            token = page.pageToken
        } while (token != null)
        return out
    }

    companion object {
        const val SOURCE = "HEALTH_CONNECT"
        private val NOT_ASLEEP = setOf(
            SleepSessionRecord.STAGE_TYPE_AWAKE, SleepSessionRecord.STAGE_TYPE_AWAKE_IN_BED, SleepSessionRecord.STAGE_TYPE_OUT_OF_BED
        )
    }
}
