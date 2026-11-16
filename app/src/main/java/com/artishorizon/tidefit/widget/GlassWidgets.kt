package com.artishorizon.tidefit.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.os.Bundle
import android.widget.RemoteViews
import com.artishorizon.tidefit.MainActivity
import com.artishorizon.tidefit.R
import dagger.hilt.android.EntryPointAccessors
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlin.math.abs

/** Base for the dark glass widgets. Each subclass says how to paint itself and how to describe itself to a screen reader. */
abstract class GlassWidget : AppWidgetProvider() {
    protected abstract fun paintBitmap(art: GlassArt, snap: WidgetSnapshot): Bitmap
    protected abstract fun describe(snap: WidgetSnapshot): String

    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) {
        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try { render(context, manager, ids) } finally { pending.finish() }
        }
    }

    override fun onAppWidgetOptionsChanged(context: Context, manager: AppWidgetManager, id: Int, newOptions: Bundle) {
        onUpdate(context, manager, intArrayOf(id))
    }

    suspend fun render(context: Context, manager: AppWidgetManager, ids: IntArray) {
        val ep = EntryPointAccessors.fromApplication(context.applicationContext, WidgetEntryPoint::class.java)
        val snap = WidgetSnapshot.load(ep)
        val open = PendingIntent.getActivity(context, 0, Intent(context, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        ids.forEach { id ->
            val o = manager.getAppWidgetOptions(id)
            val wDp = o.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, 160)
            val hDp = o.getInt(AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT, 160)
            val views = RemoteViews(context.packageName, R.layout.widget_glass)
            views.setImageViewBitmap(R.id.widget_img, paintBitmap(GlassArt(context, wDp, hDp), snap))
            views.setContentDescription(R.id.widget_img, describe(snap))
            views.setOnClickPendingIntent(R.id.widget_root, open)
            manager.updateAppWidget(id, views)
        }
    }

    companion object {
        suspend fun updateAll(context: Context) {
            val manager = AppWidgetManager.getInstance(context)
            listOf(CaloriesWidget(), WaterWidget(), WorkoutWidget(), StreakWidget()).forEach { widget ->
                val ids = manager.getAppWidgetIds(ComponentName(context, widget::class.java))
                if (ids.isNotEmpty()) widget.render(context, manager, ids)
            }
        }
    }
}

class CaloriesWidget : GlassWidget() {
    override fun paintBitmap(art: GlassArt, snap: WidgetSnapshot) = art.calories(snap)
    override fun describe(snap: WidgetSnapshot) =
        "Calories: ${snap.eatenKcal} eaten" + (snap.leftKcal?.let { ", ${abs(it)} ${if (it >= 0) "left" else "over"}" } ?: "")
}

class WaterWidget : GlassWidget() {
    override fun paintBitmap(art: GlassArt, snap: WidgetSnapshot) = art.water(snap)
    override fun describe(snap: WidgetSnapshot) = "Water today: ${snap.waterMl} millilitres" + (snap.waterTargetMl?.let { " of $it" } ?: "")
}

class WorkoutWidget : GlassWidget() {
    override fun paintBitmap(art: GlassArt, snap: WidgetSnapshot) = art.workout(snap)
    override fun describe(snap: WidgetSnapshot) = "Daily workout: ${snap.workoutMinutes} minutes" + (snap.workoutName?.let { ", $it" } ?: "")
}

class StreakWidget : GlassWidget() {
    override fun paintBitmap(art: GlassArt, snap: WidgetSnapshot) = art.streak(snap)
    override fun describe(snap: WidgetSnapshot) = "Streak: ${snap.streak} days" + if (snap.streakToday) ", done for today" else ""
}
