package com.artishorizon.tidefit.feature.coach

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import com.artishorizon.tidefit.core.designsystem.components.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import com.artishorizon.tidefit.core.designsystem.components.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.artishorizon.tidefit.core.designsystem.Spacing
import com.artishorizon.tidefit.core.designsystem.components.FitCard
import com.artishorizon.tidefit.core.designsystem.components.FitTopBar
import com.artishorizon.tidefit.domain.coaching.TargetsDto
import com.artishorizon.tidefit.domain.repository.CoachingRepository
import com.artishorizon.tidefit.domain.repository.CoachingSession
import com.artishorizon.tidefit.domain.repository.CoachingStatus
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import javax.inject.Inject

@HiltViewModel
class CoachHistoryViewModel @Inject constructor(private val repo: CoachingRepository) : ViewModel() {
    val sessions: StateFlow<List<CoachingSession>?> =
        repo.observeHistory().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    fun rollback(id: Long) { viewModelScope.launch { repo.rollback(id) } }
}

private fun TargetsDto.line() = "$caloriesKcal kcal · P $proteinG g · C $carbsG g · F $fatG g"

@Composable
fun CoachHistoryScreen(onBack: () -> Unit, vm: CoachHistoryViewModel = hiltViewModel()) {
    val sessions by vm.sessions.collectAsStateWithLifecycle()
    var confirm by remember { mutableStateOf<CoachingSession?>(null) }
    val fmt = remember { DateTimeFormatter.ofLocalizedDateTime(FormatStyle.MEDIUM, FormatStyle.SHORT).withZone(ZoneId.systemDefault()) }

    Scaffold(topBar = { FitTopBar("Coaching history", onBack) }) { padding ->
        val list = sessions
        LazyColumn(
            Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(horizontal = Spacing.screenHorizontal, vertical = Spacing.sm),
            verticalArrangement = Arrangement.spacedBy(Spacing.md)
        ) {
            if (list != null && list.isEmpty()) item { Text("No imports yet. Reviewed and applied replies appear here.", color = MaterialTheme.colorScheme.onSurfaceVariant) }
            items(list.orEmpty(), key = { it.id }) { s ->
                FitCard {
                    Column(verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                        Text(fmt.format(s.importedAt), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(
                            when (s.status) {
                                CoachingStatus.APPLIED -> "Targets applied"
                                CoachingStatus.ROLLED_BACK -> "Targets rolled back"
                                CoachingStatus.REVIEWED -> "Reviewed, nothing applied"
                            }, style = MaterialTheme.typography.titleSmall
                        )
                        s.summary?.let { Text(it, style = MaterialTheme.typography.bodyMedium) }
                        s.previousTargets?.let { Text("Before: ${it.line()}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                        s.appliedTargets?.let { Text("Applied: ${it.line()}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                        s.rolledBackAt?.let { Text("Rolled back ${fmt.format(it)}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                        if (s.canRollBack) TextButton({ confirm = s }) { Text("Roll back targets") }
                    }
                }
            }
        }
    }

    confirm?.let { s ->
        AlertDialog(
            onDismissRequest = { confirm = null },
            title = { Text("Roll back targets?") },
            text = { Text("Your daily targets will be set back to ${s.previousTargets?.line()}. This adds a new entry to your target history; nothing is deleted.") },
            confirmButton = { TextButton({ vm.rollback(s.id); confirm = null }) { Text("Roll back") } },
            dismissButton = { TextButton({ confirm = null }) { Text("Cancel") } }
        )
    }
}
