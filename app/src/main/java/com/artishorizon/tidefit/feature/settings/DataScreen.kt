package com.artishorizon.tidefit.feature.settings

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import com.artishorizon.tidefit.core.designsystem.components.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.ui.unit.dp
import com.artishorizon.tidefit.core.designsystem.components.Switch
import androidx.compose.material3.Text
import com.artishorizon.tidefit.core.designsystem.components.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.artishorizon.tidefit.core.designsystem.Spacing
import com.artishorizon.tidefit.core.designsystem.components.FitCard
import com.artishorizon.tidefit.core.designsystem.components.FitTopBar
import com.artishorizon.tidefit.core.designsystem.components.PrimaryButton
import com.artishorizon.tidefit.core.designsystem.components.SecondaryButton
import com.artishorizon.tidefit.core.designsystem.components.SectionHeader
import com.artishorizon.tidefit.data.BackupManager
import com.artishorizon.tidefit.data.health.HealthAvailability
import com.artishorizon.tidefit.data.health.HealthConnectSource
import com.artishorizon.tidefit.data.health.HealthSyncScheduler
import com.artishorizon.tidefit.domain.repository.HealthSyncPrefs
import com.artishorizon.tidefit.domain.repository.HealthSyncSettings
import com.artishorizon.tidefit.data.reminders.ReminderScheduler
import com.artishorizon.tidefit.data.reminders.hasNotificationPermission
import com.artishorizon.tidefit.domain.model.ReminderSetting
import com.artishorizon.tidefit.domain.repository.ReminderSettings
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDate
import javax.inject.Inject

data class DataUiState(
    val busy: Boolean = false,
    val message: String? = null,
    val error: String? = null,
    val healthAvailability: HealthAvailability = HealthAvailability.UNSUPPORTED,
    val healthPermitted: Boolean = false,
    val healthCanWrite: Boolean = false,
    val healthBackground: Boolean = false
)

@HiltViewModel
class DataViewModel @Inject constructor(
    private val reminderSettings: ReminderSettings,
    private val scheduler: ReminderScheduler,
    private val backup: BackupManager,
    private val health: HealthConnectSource,
    private val healthSettings: HealthSyncSettings,
    private val healthScheduler: HealthSyncScheduler
) : ViewModel() {
    val healthPrefs: StateFlow<HealthSyncPrefs> =
        healthSettings.observePrefs().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HealthSyncPrefs())

    val reminders: StateFlow<List<ReminderSetting>> =
        reminderSettings.observe().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _state = MutableStateFlow(DataUiState())
    val state: StateFlow<DataUiState> = _state.asStateFlow()

    val healthPermissions get() = health.permissions
    fun healthContract() = health.requestPermissionContract()

    fun setReminder(setting: ReminderSetting) {
        viewModelScope.launch {
            reminderSettings.set(setting)
            scheduler.apply(setting, replace = true)
        }
    }

    fun refreshHealth() {
        viewModelScope.launch {
            val a = health.availability()
            val g = if (a == HealthAvailability.AVAILABLE) health.granted() else emptySet()
            _state.update { it.copy(healthAvailability = a, healthPermitted = health.canRead(g) || health.canWrite(g), healthCanWrite = health.canWrite(g), healthBackground = health.canReadInBackground(g)) }
        }
    }

    fun syncHealth() = run("Syncing…") {
        val r = health.sync()
        _state.update { it.copy(message = r.summary()) }
    }

    fun setAutoSync(on: Boolean) {
        viewModelScope.launch {
            healthSettings.setAutoSync(on)
            healthScheduler.apply(on)
            if (on) health.sync().let { r -> _state.update { it.copy(message = r.summary()) } }
        }
    }

    fun setWriteBack(on: Boolean) {
        viewModelScope.launch {
            healthSettings.setWriteBack(on)
            if (on) health.sync().let { r -> _state.update { it.copy(message = r.summary()) } }
        }
    }

    fun createBackup(uri: Uri) = run("Saving backup…") {
        val bytes = backup.export(uri)
        _state.update { it.copy(message = "Backup saved (${bytes / 1024} KB). It contains all your data and is not encrypted, so keep it somewhere safe.") }
    }

    fun restore(uri: Uri) {
        viewModelScope.launch {
            _state.update { it.copy(busy = true, message = "Restoring…", error = null) }
            when (val r = backup.restore(uri)) {
                is BackupManager.RestoreResult.Failed -> _state.update { it.copy(busy = false, message = null, error = r.reason) }
                BackupManager.RestoreResult.Replaced -> backup.restartApp()
            }
        }
    }

    fun showError(m: String) = _state.update { it.copy(error = m, message = null) }
    fun showMessage(m: String) = _state.update { it.copy(message = m, error = null) }

    private fun run(label: String, block: suspend () -> Unit) {
        viewModelScope.launch {
            _state.update { it.copy(busy = true, message = label, error = null) }
            runCatching { block() }.onFailure { e -> _state.update { it.copy(error = e.message ?: "Something went wrong.", message = null) } }
            _state.update { it.copy(busy = false) }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DataScreen(onBack: () -> Unit, vm: DataViewModel = hiltViewModel()) {
    val reminders by vm.reminders.collectAsStateWithLifecycle()
    val state by vm.state.collectAsStateWithLifecycle()
    val healthPrefs by vm.healthPrefs.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var timeFor by remember { mutableStateOf<ReminderSetting?>(null) }
    var pendingRestore by remember { mutableStateOf<Uri?>(null) }
    var pendingEnable by remember { mutableStateOf<ReminderSetting?>(null) }

    LaunchedEffect(Unit) { vm.refreshHealth() }

    val notifPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        val s = pendingEnable
        pendingEnable = null
        if (granted && s != null) vm.setReminder(s.copy(enabled = true))
        else vm.showError("Notifications are blocked for TIDE Fit. Allow them in system settings to use reminders.")
    }
    val healthPermission = rememberLauncherForActivityResult(vm.healthContract()) { vm.refreshHealth() }
    val createDoc = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/octet-stream")) { uri -> uri?.let(vm::createBackup) }
    val openDoc = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri -> pendingRestore = uri }

    Scaffold(topBar = { FitTopBar("Data & backup", onBack) }) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState())
                .padding(horizontal = Spacing.screenHorizontal, vertical = Spacing.sm),
            verticalArrangement = Arrangement.spacedBy(Spacing.md)
        ) {
            state.message?.let { Text(it, color = MaterialTheme.colorScheme.primary) }
            state.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }

            IconHeader("bell", "Reminders", androidx.compose.ui.graphics.Color(0xFFFFB84D))
            FitCard {
                Column(verticalArrangement = Arrangement.spacedBy(Spacing.md)) {
                    Text(
                        "All off by default. A reminder is only sent when it's still useful, for example not if you've already logged a meal today.",
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    reminders.forEach { r ->
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(r.kind.label, style = MaterialTheme.typography.titleSmall)
                                Text(r.kind.description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                TextButton({ timeFor = r }, modifier = Modifier.semantics { contentDescription = "${r.kind.label} reminder time, ${r.timeLabel}. Change" }) { Text(r.timeLabel) }
                            }
                            Switch(
                                checked = r.enabled,
                                onCheckedChange = { on ->
                                    if (on && !hasNotificationPermission(context)) {
                                        pendingEnable = r
                                        notifPermission.launch(android.Manifest.permission.POST_NOTIFICATIONS)
                                    } else vm.setReminder(r.copy(enabled = on))
                                },
                                modifier = Modifier.semantics { contentDescription = "${r.kind.label} reminder" }
                            )
                        }
                    }
                }
            }

            IconHeader("activity", "Health Connect", androidx.compose.ui.graphics.Color(0xFFFF6B6B))
            FitCard {
                Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                    Text(
                        "Brings in weight, sleep, steps, active calories and exercise time from other apps, and can send your weight, water, meals and workouts back. " +
                            "Everything stays on your device. Anything you logged yourself is never overwritten.",
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    when (state.healthAvailability) {
                        HealthAvailability.AVAILABLE ->
                            if (!state.healthPermitted) PrimaryButton("Allow access", { healthPermission.launch(vm.healthPermissions) }, Modifier.fillMaxWidth())
                            else {
                                SyncRow("Keep in sync", "Updates in the background about every 6 hours, and when you open the app.", healthPrefs.autoSync, "Keep Health Connect in sync", vm::setAutoSync)
                                SyncRow(
                                    "Send my data to Health Connect", "Weight, water, meals and finished workouts from the last 30 days. Deleting one here removes it there too.",
                                    healthPrefs.writeBack, "Send data to Health Connect"
                                ) { on -> if (on && !state.healthCanWrite) healthPermission.launch(vm.healthPermissions) else vm.setWriteBack(on) }
                                if (healthPrefs.autoSync && !state.healthBackground)
                                    Text(
                                        "Background reading isn't allowed yet, so new data is imported when you open the app.",
                                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                healthPrefs.lastSyncAt?.let {
                                    Text(
                                        "Last synced ${java.time.format.DateTimeFormatter.ofPattern("d MMM, HH:mm").format(it.atZone(java.time.ZoneId.systemDefault()))}. ${healthPrefs.lastSummary.orEmpty()}",
                                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                PrimaryButton("Sync now", vm::syncHealth, Modifier.fillMaxWidth(), enabled = !state.busy)
                                if (!state.healthCanWrite || !state.healthBackground)
                                    SecondaryButton("Review permissions", { healthPermission.launch(vm.healthPermissions) }, Modifier.fillMaxWidth())
                            }
                        HealthAvailability.NOT_INSTALLED -> Text("Health Connect isn't installed on this device.")
                        HealthAvailability.UPDATE_REQUIRED -> Text("Health Connect needs an update before TIDE Fit can use it.")
                        HealthAvailability.UNSUPPORTED -> Text("Health Connect isn't supported on this device.")
                    }
                }
            }

            IconHeader("layers", "Backup & restore", MaterialTheme.colorScheme.primary)
            FitCard {
                Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                    Text(
                        "A backup is a single file with everything: profile and photo, food, workouts, body data and coaching history. It isn't encrypted. " +
                            "TIDE Fit has no cloud, so this file is how you move to a new phone or recover after a reset.",
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    PrimaryButton("Create backup", { createDoc.launch("tidefit-backup-${LocalDate.now()}.db") }, Modifier.fillMaxWidth(), enabled = !state.busy)
                    SecondaryButton("Restore from backup", { openDoc.launch(arrayOf("*/*")) }, Modifier.fillMaxWidth(), enabled = !state.busy)
                }
            }
        }
    }

    timeFor?.let { r ->
        var hh by remember(r) { mutableStateOf(r.hour) }; var mm by remember(r) { mutableStateOf(r.minute) }
        AlertDialog(
            onDismissRequest = { timeFor = null },
            title = { Text("${r.kind.label} reminder time") },
            text = { com.artishorizon.tidefit.core.designsystem.components.TimeStepper(hh, mm, { h, m -> hh = h; mm = m }) },
            confirmButton = { TextButton({ vm.setReminder(r.copy(minutes = hh * 60 + mm)); timeFor = null }) { Text("Save") } },
            dismissButton = { TextButton({ timeFor = null }) { Text("Cancel") } }
        )
    }

    pendingRestore?.let { uri ->
        AlertDialog(
            onDismissRequest = { pendingRestore = null },
            title = { Text("Replace all data?") },
            text = {
                Text(
                    "Everything currently in TIDE Fit will be replaced by this backup, and the app will restart. " +
                        "A copy of your current data is kept inside the app's private storage until the next restore."
                )
            },
            confirmButton = { TextButton({ pendingRestore = null; vm.restore(uri) }) { Text("Replace") } },
            dismissButton = { TextButton({ pendingRestore = null }) { Text("Cancel") } }
        )
    }
}

@Composable
private fun SyncRow(title: String, detail: String, checked: Boolean, description: String, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleSmall)
            Text(detail, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Switch(checked = checked, onCheckedChange = onChange, modifier = Modifier.semantics { contentDescription = description })
    }
}

@Composable
private fun IconHeader(icon: String, title: String, tint: androidx.compose.ui.graphics.Color) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.md)) {
        com.artishorizon.tidefit.core.designsystem.components.IconBadge(com.artishorizon.tidefit.core.designsystem.components.lucide(icon), tint, size = 36.dp)
        Text(title, style = MaterialTheme.typography.titleMedium)
    }
}
