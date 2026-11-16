package com.artishorizon.tidefit.feature.profile

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.artishorizon.tidefit.R
import com.artishorizon.tidefit.core.designsystem.FitTheme
import com.artishorizon.tidefit.core.designsystem.Radii
import com.artishorizon.tidefit.core.designsystem.Spacing
import com.artishorizon.tidefit.core.designsystem.components.EstimateBadge
import com.artishorizon.tidefit.core.designsystem.components.FitCard
import com.artishorizon.tidefit.core.designsystem.components.FitTopBar
import com.artishorizon.tidefit.core.designsystem.components.IconBadge
import com.artishorizon.tidefit.core.designsystem.components.IconButton
import com.artishorizon.tidefit.core.designsystem.components.LoadingState
import com.artishorizon.tidefit.core.designsystem.components.PhotoCard
import com.artishorizon.tidefit.core.designsystem.components.PrimaryButton
import com.artishorizon.tidefit.core.designsystem.components.SectionHeader
import com.artishorizon.tidefit.core.designsystem.components.Switch
import com.artishorizon.tidefit.core.designsystem.components.lucide
import com.artishorizon.tidefit.core.designsystem.components.pressable
import com.artishorizon.tidefit.domain.calc.BodyMetrics
import com.artishorizon.tidefit.domain.calc.Units
import com.artishorizon.tidefit.domain.calc.calorieLimitsFor
import com.artishorizon.tidefit.domain.model.DailyTargets
import com.artishorizon.tidefit.domain.model.ThemeMode
import com.artishorizon.tidefit.domain.model.UserProfile
import com.artishorizon.tidefit.domain.model.ValueSource
import com.artishorizon.tidefit.feature.profile.form.BasicsSection
import com.artishorizon.tidefit.feature.profile.form.BodySection
import com.artishorizon.tidefit.feature.profile.form.DietSection
import com.artishorizon.tidefit.feature.profile.form.EquipmentSection
import com.artishorizon.tidefit.feature.profile.form.GoalSection
import com.artishorizon.tidefit.feature.profile.form.TrainingSection
import java.util.Locale

@Composable
fun ProfileScreen(onBack: () -> Unit, onEditProfile: () -> Unit, onOpenData: () -> Unit, viewModel: ProfileViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    if (state.loading) { LoadingState(); return }

    Column(Modifier.fillMaxSize()) {
        FitTopBar("Profile", onBack)
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = Spacing.screenHorizontal, vertical = Spacing.md),
            verticalArrangement = Arrangement.spacedBy(Spacing.lg)
        ) {
            state.profile?.let { p ->
                ProfileHero(p, onEditProfile)
                JourneyCard(p)
                TargetsBento(p, state.targets)
            }

            Group("Your data") {
                SettingRow("layers", "Data & backup", "Export, import and restore everything", Color(0xFFC6F135), onOpenData)
                SettingRow("bell", "Reminders", "Water, meals, workouts and weigh-ins", Color(0xFFFFB84D), onOpenData)
                SettingRow("activity", "Health Connect", "Import steps, sleep and weight", Color(0xFFFF6B6B), onOpenData, last = true)
            }

            ThemeCard(state.themeMode, { viewModel.setTheme(it) }, state.dynamicColor, viewModel::setDynamicColor)
            PrivacyBanner()
            Spacer(Modifier.height(Spacing.lg))
        }
    }
}

/* ───────────────────────────── Hero ───────────────────────────── */

@Composable
private fun ProfileHero(p: UserProfile, onEdit: () -> Unit) {
    val accent = MaterialTheme.colorScheme.primary
    val bmi = BodyMetrics.bmi(p.currentWeightKg, p.heightCm)
    PhotoCard(R.drawable.photo_gym, height = 340.dp) {
        Box(Modifier.fillMaxSize()) {
            // Avatar with ring and pencil badge
            Box(Modifier.align(Alignment.TopStart)) {
                Box(
                    Modifier.size(84.dp).clip(CircleShape).border(3.dp, accent, CircleShape).padding(5.dp).clip(CircleShape).background(accent),
                    contentAlignment = Alignment.Center
                ) {
                    Text(p.name.firstOrNull()?.uppercase() ?: "?", style = MaterialTheme.typography.headlineLarge, color = MaterialTheme.colorScheme.onPrimary)
                }
                Box(
                    Modifier.align(Alignment.BottomEnd).size(30.dp).clip(CircleShape).background(Color.White).pressable(onClick = onEdit),
                    contentAlignment = Alignment.Center
                ) { Icon(lucide("pencil"), "Edit profile", tint = Color(0xFF0B0D10), modifier = Modifier.size(15.dp)) }
            }
            Box(
                Modifier.align(Alignment.TopEnd).clip(CircleShape).background(Color.Black.copy(alpha = 0.35f))
                    .border(1.dp, Color.White.copy(alpha = 0.25f), CircleShape).padding(horizontal = 12.dp, vertical = 7.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Icon(lucide("flame"), null, tint = accent, modifier = Modifier.size(14.dp))
                    Text("${p.trainingDaysPerWeek} days / week", style = MaterialTheme.typography.labelMedium, color = Color.White)
                }
            }
            Column(Modifier.align(Alignment.BottomStart), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(p.name, style = MaterialTheme.typography.displaySmall, color = Color.White)
                Box(Modifier.clip(CircleShape).background(accent).padding(horizontal = 14.dp, vertical = 6.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Icon(lucide("target"), null, tint = MaterialTheme.colorScheme.onPrimary, modifier = Modifier.size(14.dp))
                        Text(p.goal.label, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onPrimary)
                    }
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    GlassStat("user", "${p.age}", "years", Modifier.weight(1f))
                    GlassStat("ruler", "%.0f".format(Locale.US, p.heightCm), "cm", Modifier.weight(1f))
                    GlassStat("scale", "%.1f".format(Locale.US, bmi), "BMI", Modifier.weight(1f))
                }
            }
        }
    }
}

@Composable
private fun GlassStat(icon: String, value: String, label: String, modifier: Modifier) {
    Row(
        modifier.clip(RoundedCornerShape(16.dp)).background(Color.White.copy(alpha = 0.14f))
            .border(1.dp, Color.White.copy(alpha = 0.18f), RoundedCornerShape(16.dp)).padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Icon(lucide(icon), null, tint = Color.White, modifier = Modifier.size(16.dp))
        Column {
            Text(value, style = MaterialTheme.typography.titleMedium, color = Color.White)
            Text(label, style = MaterialTheme.typography.labelSmall, color = Color.White.copy(alpha = 0.75f))
        }
    }
}

/* ───────────────────────────── Journey ───────────────────────────── */

@Composable
private fun JourneyCard(p: UserProfile) {
    val u = p.weightUnit
    fun w(kg: Double) = "%.1f".format(Locale.US, Units.fromKg(kg, u))
    val total = p.initialWeightKg - p.targetWeightKg
    val done = p.initialWeightKg - p.currentWeightKg
    val progress = if (kotlin.math.abs(total) < 0.05) 1f else (done / total).toFloat().coerceIn(0f, 1f)
    val remaining = kotlin.math.abs(p.currentWeightKg - p.targetWeightKg)
    val accent = MaterialTheme.colorScheme.primary
    FitCard {
        Column(verticalArrangement = Arrangement.spacedBy(Spacing.md)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("YOUR JOURNEY", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(w(remaining), style = MaterialTheme.typography.displaySmall)
                        Text("${u.label} to go", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(bottom = 6.dp))
                    }
                }
                Box(Modifier.clip(CircleShape).background(accent.copy(alpha = 0.16f)).padding(horizontal = 12.dp, vertical = 7.dp)) {
                    Text("${(progress * 100).toInt()}%", style = MaterialTheme.typography.titleSmall, color = accent)
                }
            }
            // Track with a milestone dot at current position
            BoxWithTrack(progress)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                JourneyEnd("Start", "${w(p.initialWeightKg)} ${u.label}", Alignment.Start)
                JourneyEnd("Now", "${w(p.currentWeightKg)} ${u.label}", Alignment.CenterHorizontally, highlight = true)
                JourneyEnd("Goal", "${w(p.targetWeightKg)} ${u.label}", Alignment.End)
            }
        }
    }
}

@Composable
private fun BoxWithTrack(progress: Float) {
    val accent = MaterialTheme.colorScheme.primary
    androidx.compose.foundation.layout.BoxWithConstraints(Modifier.fillMaxWidth().height(20.dp)) {
        Box(Modifier.align(Alignment.CenterStart).fillMaxWidth().height(8.dp).clip(CircleShape).background(MaterialTheme.colorScheme.surfaceContainerHighest))
        Box(
            Modifier.align(Alignment.CenterStart).fillMaxWidth(progress.coerceAtLeast(0.02f)).height(8.dp).clip(CircleShape)
                .background(Brush.horizontalGradient(listOf(accent.copy(alpha = 0.5f), accent)))
        )
        val x = (maxWidth - 20.dp) * progress
        Box(
            Modifier.padding(start = x).size(20.dp).clip(CircleShape).background(accent)
                .border(4.dp, MaterialTheme.colorScheme.surface, CircleShape)
        )
    }
}

@Composable
private fun JourneyEnd(label: String, value: String, align: Alignment.Horizontal, highlight: Boolean = false) {
    Column(horizontalAlignment = align) {
        Text(value, style = MaterialTheme.typography.titleSmall, color = if (highlight) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface)
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/* ───────────────────────────── Targets bento ───────────────────────────── */

@Composable
fun TargetsCard(targets: DailyTargets?) {
    // Kept for callers that only have targets; the profile screen uses TargetsBento.
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
        SectionHeader("Daily targets") { if (targets?.source == ValueSource.FORMULA_ESTIMATE) EstimateBadge() }
        if (targets == null) EmptyTargets() else MacroRow(targets)
    }
}

@Composable
private fun EmptyTargets() = FitCard {
    Text(
        "No targets yet. Open Nutrition to calculate an estimate from your profile.",
        style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant
    )
}

@Composable
private fun TargetsBento(p: UserProfile, targets: DailyTargets?) {
    val c = FitTheme.colors
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
        SectionHeader("Daily targets") { if (targets?.source == ValueSource.FORMULA_ESTIMATE) EstimateBadge() }
        if (targets == null) { EmptyTargets(); return@Column }
        val rule = calorieLimitsFor(p, targets.caloriesKcal)
        // Big calorie tile
        val accent = MaterialTheme.colorScheme.primary
        Box(
            Modifier.fillMaxWidth().clip(Radii.xl)
                .background(Brush.linearGradient(listOf(Color(0xFFC6F135), Color(0xFF8FD11F))))
                .padding(20.dp)
        ) {
            Icon(lucide("flame"), null, tint = Color.Black.copy(alpha = 0.10f), modifier = Modifier.size(104.dp).align(Alignment.CenterEnd))
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(rule.kind.label.uppercase(), style = MaterialTheme.typography.labelSmall, color = Color(0xCC0B0D10))
                Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("${targets.caloriesKcal}", style = MaterialTheme.typography.displayMedium, color = Color(0xFF0B0D10))
                    Text("kcal", style = MaterialTheme.typography.titleMedium, color = Color(0xCC0B0D10), modifier = Modifier.padding(bottom = 8.dp))
                }
                Text(rule.ruleText, style = MaterialTheme.typography.bodyMedium, color = Color(0xCC0B0D10))
            }
        }
        MacroRow(targets)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            SmallTile("leaf", MaterialTheme.colorScheme.tertiary, "${targets.fibreG}", "g fibre", Modifier.weight(1f))
            SmallTile("droplet", c.water, "${targets.waterMl}", "ml water", Modifier.weight(1f))
        }
    }
}

@Composable
private fun MacroRow(t: DailyTargets) {
    val c = FitTheme.colors
    val kcal = (t.proteinG * 4 + t.carbohydratesG * 4 + t.fatG * 9).coerceAtLeast(1)
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
        MacroTile("Protein", t.proteinG, t.proteinG * 4f / kcal, c.protein, Modifier.weight(1f))
        MacroTile("Carbs", t.carbohydratesG, t.carbohydratesG * 4f / kcal, c.carbs, Modifier.weight(1f))
        MacroTile("Fat", t.fatG, t.fatG * 9f / kcal, c.fat, Modifier.weight(1f))
    }
}

@Composable
private fun MacroTile(label: String, grams: Int, share: Float, tint: Color, modifier: Modifier) {
    Column(
        modifier.clip(Radii.lg).background(MaterialTheme.colorScheme.surfaceContainerHigh).padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(label, style = MaterialTheme.typography.labelMedium, color = tint)
        Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(2.dp)) {
            Text("$grams", style = MaterialTheme.typography.headlineSmall)
            Text("g", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(bottom = 3.dp))
        }
        Box(Modifier.fillMaxWidth().height(6.dp).clip(CircleShape).background(tint.copy(alpha = 0.18f))) {
            Box(Modifier.fillMaxWidth(share.coerceIn(0.04f, 1f)).height(6.dp).clip(CircleShape).background(tint))
        }
        Text("${(share * 100).toInt()}% of kcal", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun SmallTile(icon: String, tint: Color, value: String, label: String, modifier: Modifier) {
    Row(
        modifier.clip(Radii.lg).background(MaterialTheme.colorScheme.surfaceContainerHigh).padding(14.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        IconBadge(lucide(icon), tint, size = 40.dp)
        Column {
            Text(value, style = MaterialTheme.typography.titleLarge)
            Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

/* ───────────────────────────── Settings ───────────────────────────── */

@Composable
private fun Group(title: String, content: @Composable () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
        SectionHeader(title)
        Column(Modifier.fillMaxWidth().clip(Radii.xl).background(MaterialTheme.colorScheme.surfaceContainerHigh)) { content() }
    }
}

@Composable
private fun SettingRow(icon: String, title: String, subtitle: String, tint: Color, onClick: () -> Unit, last: Boolean = false) {
    Column {
        Row(
            Modifier.fillMaxWidth().pressable(onClick = onClick).padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Box(Modifier.size(42.dp).clip(RoundedCornerShape(14.dp)).background(tint.copy(alpha = 0.16f)), contentAlignment = Alignment.Center) {
                Icon(lucide(icon), null, tint = tint, modifier = Modifier.size(21.dp))
            }
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleSmall)
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Icon(lucide("chevron-right"), null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(20.dp))
        }
        if (!last) Box(Modifier.padding(start = 72.dp).fillMaxWidth().height(1.dp).background(MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)))
    }
}

@Composable
private fun ThemeCard(mode: ThemeMode, onMode: (ThemeMode) -> Unit, dynamic: Boolean, onDynamic: (Boolean) -> Unit) {
    val accent = MaterialTheme.colorScheme.primary
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
        SectionHeader("Appearance")
        Column(
            Modifier.fillMaxWidth().clip(Radii.xl).background(MaterialTheme.colorScheme.surfaceContainerHigh).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(Spacing.md)
        ) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                ThemeMode.entries.forEach { m ->
                    val selected = m == mode
                    val (icon, bg, fg) = when (m.label) {
                        "Dark" -> Triple("moon", Color(0xFF14171C), Color.White)
                        "Light" -> Triple("sun", Color(0xFFF4F5F0), Color(0xFF14171C))
                        else -> Triple("sliders-horizontal", Brush.linearGradient(listOf(Color(0xFF14171C), Color(0xFFF4F5F0))), Color(0xFF8A8F98))
                    }
                    Column(
                        Modifier.weight(1f).clip(RoundedCornerShape(18.dp))
                            .border(if (selected) 2.dp else 1.dp, if (selected) accent else MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(18.dp))
                            .pressable { onMode(m) }.padding(bottom = 10.dp),
                        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Box(
                            Modifier.fillMaxWidth().height(64.dp).let { if (bg is Brush) it.background(bg) else it.background(bg as Color) },
                            contentAlignment = Alignment.Center
                        ) { Icon(lucide(icon), null, tint = fg, modifier = Modifier.size(24.dp)) }
                        Text(m.label, style = MaterialTheme.typography.labelLarge, color = if (selected) accent else MaterialTheme.colorScheme.onSurface)
                    }
                }
            }
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                Box(Modifier.size(42.dp).clip(RoundedCornerShape(14.dp)).background(Color(0xFFB78CFF).copy(alpha = 0.16f)), contentAlignment = Alignment.Center) {
                    Icon(lucide("palette"), null, tint = Color(0xFFB78CFF), modifier = Modifier.size(21.dp))
                }
                Column(Modifier.weight(1f)) {
                    Text("Dynamic colour", style = MaterialTheme.typography.titleSmall)
                    Text("Match your wallpaper (Android 12+)", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Switch(dynamic, onDynamic)
            }
        }
    }
}

@Composable
private fun PrivacyBanner() {
    val accent = MaterialTheme.colorScheme.primary
    Column(
        Modifier.fillMaxWidth().clip(Radii.xl)
            .background(Brush.linearGradient(listOf(accent.copy(alpha = 0.18f), accent.copy(alpha = 0.04f))))
            .border(BorderStroke(1.dp, accent.copy(alpha = 0.35f)), Radii.xl).padding(18.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            IconBadge(lucide("shield-check"), accent, size = 44.dp)
            Column {
                Text("Private by design", style = MaterialTheme.typography.titleMedium)
                Text("Your data never leaves this phone.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf("On-device", "No account", "No telemetry").forEach {
                Box(Modifier.clip(CircleShape).background(accent.copy(alpha = 0.14f)).padding(horizontal = 12.dp, vertical = 6.dp)) {
                    Text(it, style = MaterialTheme.typography.labelMedium, color = accent)
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileEditScreen(onDone: () -> Unit, viewModel: ProfileEditViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    LaunchedEffect(state.saved) { if (state.saved) onDone() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Edit profile") },
                navigationIcon = {
                    IconButton(onDone) { Icon(lucide("arrow-left"), contentDescription = "Back") }
                }
            )
        }
    ) { padding ->
        if (state.loading) { LoadingState(Modifier.padding(padding)); return@Scaffold }
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = Spacing.screenHorizontal, vertical = Spacing.lg),
            verticalArrangement = Arrangement.spacedBy(Spacing.xl)
        ) {
            val f = state.form
            val u = viewModel::updateForm
            Text("About you", style = MaterialTheme.typography.titleLarge); BasicsSection(f, u)
            Text("Body", style = MaterialTheme.typography.titleLarge); BodySection(f, u)
            Text("Goal", style = MaterialTheme.typography.titleLarge); GoalSection(f, u)
            Text("Training", style = MaterialTheme.typography.titleLarge); TrainingSection(f, u)
            Text("Food", style = MaterialTheme.typography.titleLarge); DietSection(f, u)
            Text("Equipment", style = MaterialTheme.typography.titleLarge); EquipmentSection(f, u)
            state.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            PrimaryButton("Save changes", viewModel::save, Modifier.fillMaxWidth(), enabled = !state.saving)
        }
    }
}
