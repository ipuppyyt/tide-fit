package com.artishorizon.tidefit.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import androidx.compose.ui.graphics.toArgb
import com.artishorizon.tidefit.MainActivity
import com.artishorizon.tidefit.R
import com.artishorizon.tidefit.core.designsystem.resolve
import com.artishorizon.tidefit.data.StreakTracker
import com.artishorizon.tidefit.domain.model.BeverageKind
import com.artishorizon.tidefit.domain.repository.AppSettings
import com.artishorizon.tidefit.domain.repository.FoodLogRepository
import com.artishorizon.tidefit.domain.repository.ProfileRepository
import com.artishorizon.tidefit.domain.repository.WaterRepository
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.time.Clock
import java.time.LocalDate

@EntryPoint
@InstallIn(SingletonComponent::class)
interface WidgetEntryPoint {
    fun foodLog(): FoodLogRepository
    fun water(): WaterRepository
    fun profiles(): ProfileRepository
    fun appSettings(): AppSettings
    fun streaks(): StreakTracker
    fun clock(): Clock
}

/** Home screen widget: calories left, water so far with a one-tap add, and the streak. Everything is read from the local database. */
class TodayWidget : AppWidgetProvider() {
    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) {
        runAsync { refresh(context, manager, ids) }
    }

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == ACTION_ADD_WATER) {
            runAsync {
                val ep = entry(context)
                ep.water().add(LocalDate.now(ep.clock()), ep.appSettings().defaultGlassMl.first(), BeverageKind.WATER, null)
                updateAll(context)
            }
        } else super.onReceive(context, intent)
    }

    private fun runAsync(block: suspend () -> Unit) {
        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try { block() } finally { pending.finish() }
        }
    }

    companion object {
        const val ACTION_ADD_WATER = "com.artishorizon.tidefit.widget.ADD_WATER"

        private fun entry(context: Context) = EntryPointAccessors.fromApplication(context.applicationContext, WidgetEntryPoint::class.java)

        /** Redraws every placed instance. Cheap to call; does nothing when no widget is on a home screen. */
        suspend fun updateAll(context: Context) {
            val manager = AppWidgetManager.getInstance(context)
            val ids = manager.getAppWidgetIds(ComponentName(context, TodayWidget::class.java))
            if (ids.isNotEmpty()) refresh(context, manager, ids)
        }

        private suspend fun refresh(context: Context, manager: AppWidgetManager, ids: IntArray) {
            val ep = entry(context)
            val today = LocalDate.now(ep.clock())
            val targets = ep.profiles().observeCurrentTargets().first()
            val eaten = ep.foodLog().observeDay(today).first().sumOf { it.total.calories }.toInt()
            val waterMl = ep.water().observeDay(today).first().filter { it.kind == BeverageKind.WATER }.sumOf { it.amountMl }
            val glass = ep.appSettings().defaultGlassMl.first()
            val streak = ep.streaks().current()

            ids.forEach { id ->
            val views = RemoteViews(context.packageName, R.layout.widget_today)
            val hero = com.artishorizon.tidefit.core.designsystem.Palettes.resolve(context, ep.appSettings().palette.first()).fit
            val ink = hero.onHero.toArgb()
            val soft = hero.onHero.copy(alpha = 0.72f).toArgb()
            val options = manager.getAppWidgetOptions(id)
            val wDp = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, 250)
            val hDp = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT, 70)
            views.setImageViewBitmap(R.id.widget_bg, WidgetArt.render(context, wDp, hDp, hero.hero.toArgb()))
            views.setInt(R.id.widget_flame_icon, "setColorFilter", ink)
            views.setInt(R.id.widget_drop_icon, "setColorFilter", soft)
            listOf(R.id.widget_kcal, R.id.widget_streak, R.id.widget_add_water).forEach { views.setTextColor(it, ink) }
            listOf(R.id.widget_kcal_label, R.id.widget_water).forEach { views.setTextColor(it, soft) }
            if (targets != null) {
                val left = targets.caloriesKcal - eaten
                views.setTextViewText(R.id.widget_kcal, "%,d".format(kotlin.math.abs(left)))
                views.setTextViewText(R.id.widget_kcal_label, if (left >= 0) "kcal left" else "kcal over")
                views.setTextViewText(R.id.widget_water, "%,d / %,d ml".format(waterMl, targets.waterMl))
            } else {
                views.setTextViewText(R.id.widget_kcal, "%,d".format(eaten))
                views.setTextViewText(R.id.widget_kcal_label, "kcal eaten")
                views.setTextViewText(R.id.widget_water, "%,d ml".format(waterMl))
            }
            views.setTextViewText(R.id.widget_streak, streak.current.toString())
            views.setTextViewText(R.id.widget_add_water, "+$glass")
            views.setContentDescription(R.id.widget_add_water, "Add $glass millilitres of water")

            val flags = PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            views.setOnClickPendingIntent(R.id.widget_root, PendingIntent.getActivity(context, 0, Intent(context, MainActivity::class.java), flags))
            views.setOnClickPendingIntent(
                R.id.widget_add_water,
                PendingIntent.getBroadcast(context, 1, Intent(context, TodayWidget::class.java).setAction(ACTION_ADD_WATER), flags)
            )
            manager.updateAppWidget(id, views)
            }
        }
    }
}
