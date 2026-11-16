package com.artishorizon.tidefit.feature.nutrition

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.artishorizon.tidefit.core.designsystem.FitTheme
import com.artishorizon.tidefit.core.designsystem.Spacing
import com.artishorizon.tidefit.core.designsystem.components.ChipGroup
import com.artishorizon.tidefit.core.designsystem.components.FitCard
import com.artishorizon.tidefit.core.designsystem.components.FitLinearProgress
import com.artishorizon.tidefit.core.designsystem.components.FitTextField
import com.artishorizon.tidefit.core.designsystem.components.FitTopBar
import com.artishorizon.tidefit.core.designsystem.components.PrimaryButton
import com.artishorizon.tidefit.core.designsystem.components.SecondaryButton
import com.artishorizon.tidefit.core.designsystem.components.SectionHeader
import com.artishorizon.tidefit.core.util.parseDecimal
import com.artishorizon.tidefit.domain.model.BeverageKind
import com.artishorizon.tidefit.domain.model.WaterEntry
import com.artishorizon.tidefit.domain.repository.AppSettings
import com.artishorizon.tidefit.domain.repository.ProfileRepository
import com.artishorizon.tidefit.domain.repository.WaterRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.Clock
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.TextStyle
import java.util.Locale
import javax.inject.Inject

data class WaterDayTotal(val date: LocalDate, val ml: Int, val hasData: Boolean)

data class WaterUiState(
    val loading: Boolean = true,
    val today: LocalDate = LocalDate.EPOCH,
    val entries: List<WaterEntry> = emptyList(),
    val waterMl: Int = 0,
    val otherMl: Int = 0,
    val targetMl: Int? = null,
    val glassMl: Int = 250,
    val week: List<WaterDayTotal> = emptyList()
) {
    /** Average over days that have data only, so untracked days don't drag the average down. */
    val weekAverageMl: Int? get() = week.filter { it.hasData }.takeIf { it.isNotEmpty() }?.let { d -> d.sumOf { it.ml } / d.size }
}

@HiltViewModel
class WaterViewModel @Inject constructor(
    private val water: WaterRepository,
    profiles: ProfileRepository,
    private val settings: AppSettings,
    private val clock: Clock
) : ViewModel() {
    private val today = LocalDate.now(clock)

    val state: StateFlow<WaterUiState> = combine(
        water.observeRange(today.minusDays(13), today), profiles.observeCurrentTargets(), settings.defaultGlassMl
    ) { range, targets, glass ->
        val todays = range.filter { it.date == today }.sortedByDescending { it.loggedAt }
        val week = (6 downTo 0).map { back ->
            val d = today.minusDays(back.toLong())
            val day = range.filter { it.date == d && it.kind == BeverageKind.WATER }
            WaterDayTotal(d, day.sumOf { it.amountMl }, day.isNotEmpty())
        }
        WaterUiState(
            loading = false, today = today, entries = todays,
            waterMl = todays.filter { it.kind == BeverageKind.WATER }.sumOf { it.amountMl },
            otherMl = todays.filter { it.kind == BeverageKind.OTHER }.sumOf { it.amountMl },
            targetMl = targets?.waterMl, glassMl = glass, week = week
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), WaterUiState())

    var error by mutableStateOf<String?>(null)
        private set

    fun add(ml: Int, kind: BeverageKind = BeverageKind.WATER, label: String? = null) = launchCatching { water.add(today, ml, kind, label) }
    fun update(id: Long, ml: Int, kind: BeverageKind) = launchCatching { water.update(id, ml, kind, null) }
    fun delete(id: Long) = launchCatching { water.delete(id) }
    fun setGlass(ml: Int) = launchCatching { settings.setDefaultGlassMl(ml) }
    fun clearError() { error = null }

    private fun launchCatching(block: suspend () -> Unit) {
        viewModelScope.launch {
            runCatching { block() }.onFailure { error = it.message?.takeIf { _ -> it is IllegalArgumentException } ?: "Couldn't save. Please try again." }
        }
    }
}

@Composable
fun WaterScreen(onBack: () -> Unit, viewModel: WaterViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var customOpen by rememberSaveable { mutableStateOf(false) }
    var editing by remember { mutableStateOf<WaterEntry?>(null) }

    Scaffold(topBar = { FitTopBar("Hydration", onBack) }) { padding ->
        LazyColumn(
            Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(horizontal = Spacing.screenHorizontal, vertical = Spacing.sm),
            verticalArrangement = Arrangement.spacedBy(Spacing.md)
        ) {
            item {
                FitCard {
                    Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                        Text("Today", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(
                            "${state.waterMl} ml" + (state.targetMl?.let { " / $it ml" } ?: ""),
                            style = MaterialTheme.typography.headlineMedium
                        )
                        state.targetMl?.let { FitLinearProgress(state.waterMl.toFloat() / it, color = FitTheme.colors.water) }
                            ?: Text("No water target set yet.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        if (state.otherMl > 0) Text("+ ${state.otherMl} ml other beverages (not counted towards water)", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
            item {
                Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                    SectionHeader("Quick add")
                    Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                        listOf(150, 250, 500, 750).forEach { ml ->
                            SecondaryButton("$ml", { viewModel.add(ml) }, Modifier.weight(1f))
                        }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                        PrimaryButton("Add glass (${state.glassMl} ml)", { viewModel.add(state.glassMl) }, Modifier.weight(1f))
                        SecondaryButton("Custom", { customOpen = true }, Modifier.weight(1f))
                    }
                    Text("Default glass size", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    ChipGroup(listOf("150", "200", "250", "300", "400"), setOf(state.glassMl.toString()), { viewModel.setGlass(it.toInt()) })
                }
            }
            viewModel.error?.let { msg -> item { Text(msg, color = MaterialTheme.colorScheme.error) } }
            item {
                Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                    SectionHeader("Last 7 days")
                    FitCard {
                        Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                            WeekChart(state.week, state.targetMl)
                            Text(
                                state.weekAverageMl?.let { "Average on tracked days: $it ml" } ?: "No water logged this week.",
                                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            if (state.week.any { !it.hasData }) Text("Dots mark days with nothing logged, not zero intake.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
            item { SectionHeader("Today's drinks") }
            if (state.entries.isEmpty()) {
                item { Text("Nothing logged yet today.", color = MaterialTheme.colorScheme.onSurfaceVariant) }
            }
            items(state.entries, key = { it.id }) { e ->
                FitCard(onClick = { editing = e }) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text("${e.amountMl} ml · ${e.kind.label}", style = MaterialTheme.typography.titleSmall)
                            Text(
                                java.time.LocalTime.ofInstant(e.loggedAt, ZoneId.systemDefault()).let { "%02d:%02d".format(it.hour, it.minute) },
                                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        IconButton({ viewModel.delete(e.id) }) { Icon(Icons.Default.Delete, contentDescription = "Delete ${e.amountMl} ml entry") }
                    }
                }
            }
        }
    }

    if (customOpen) {
        AmountDialog("Add drink", null, BeverageKind.WATER, onDismiss = { customOpen = false }) { ml, kind -> viewModel.add(ml, kind); customOpen = false }
    }
    editing?.let { e ->
        AmountDialog("Edit drink", e.amountMl, e.kind, onDismiss = { editing = null }) { ml, kind -> viewModel.update(e.id, ml, kind); editing = null }
    }
}

@Composable
private fun AmountDialog(title: String, initial: Int?, initialKind: BeverageKind, onDismiss: () -> Unit, onSave: (Int, BeverageKind) -> Unit) {
    var text by rememberSaveable { mutableStateOf(initial?.toString().orEmpty()) }
    var kind by rememberSaveable { mutableStateOf(initialKind) }
    val ml = text.parseDecimal()?.takeIf { it > 0 }?.toInt()
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                FitTextField(text, { text = it }, "Amount", keyboardType = KeyboardType.Number, suffix = "ml", error = if (text.isNotBlank() && ml == null) "Enter a number greater than zero." else null)
                ChipGroup(BeverageKind.entries.map { it.label }, setOf(kind.label), { l -> kind = BeverageKind.entries.first { it.label == l } })
            }
        },
        confirmButton = { TextButton({ ml?.let { onSave(it, kind) } }, enabled = ml != null) { Text("Save") } },
        dismissButton = { TextButton(onDismiss) { Text("Cancel") } }
    )
}

@Composable
private fun WeekChart(week: List<WaterDayTotal>, target: Int?) {
    val bar = FitTheme.colors.water
    val faint = MaterialTheme.colorScheme.outline
    val tint = MaterialTheme.colorScheme.onSurfaceVariant
    val max = maxOf(week.maxOfOrNull { it.ml } ?: 0, target ?: 0, 1).toFloat()
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
        Canvas(Modifier.fillMaxWidth().height(120.dp)) {
            val slot = size.width / week.size.coerceAtLeast(1)
            val barW = slot * 0.5f
            target?.let { t ->
                val y = size.height * (1f - t / max)
                drawLine(faint, Offset(0f, y), Offset(size.width, y), strokeWidth = 1.dp.toPx())
            }
            week.forEachIndexed { i, d ->
                val x = i * slot + (slot - barW) / 2
                if (d.hasData) {
                    val h = size.height * (d.ml / max)
                    drawRoundRect(bar, Offset(x, size.height - h), Size(barW, h.coerceAtLeast(2.dp.toPx())), CornerRadius(4.dp.toPx()))
                } else {
                    drawCircle(tint.copy(alpha = 0.5f), 3.dp.toPx(), Offset(x + barW / 2, size.height - 3.dp.toPx()))
                }
            }
        }
        Row(Modifier.fillMaxWidth()) {
            week.forEach { d ->
                Text(
                    d.date.dayOfWeek.getDisplayName(TextStyle.NARROW, Locale.getDefault()),
                    Modifier.weight(1f), style = MaterialTheme.typography.labelSmall, color = tint,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                )
            }
        }
    }
}
