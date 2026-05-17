package com.workcount.app.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.view.View
import android.widget.RemoteViews
import com.workcount.app.MainActivity
import com.workcount.app.R
import com.workcount.app.appContainer
import com.workcount.app.service.ShiftForegroundService
import com.workcount.core.domain.Shift
import com.workcount.core.domain.ShiftCalculators
import com.workcount.core.domain.ShiftStatus
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking

object WorkCountWidget {
    fun updateAll(context: Context) {
        val manager = AppWidgetManager.getInstance(context)
        val component = ComponentName(context, WorkCountWidgetReceiver::class.java)
        val ids = manager.getAppWidgetIds(component)
        if (ids.isEmpty()) return

        val activeShift = runBlocking(Dispatchers.IO) {
            context.appContainer.shiftRepository.observeActiveShift().first()
        }
        ids.forEach { appWidgetId ->
            manager.updateAppWidget(appWidgetId, buildRemoteViews(context, activeShift))
        }
    }

    private fun buildRemoteViews(context: Context, activeShift: Shift?): RemoteViews {
        val views = RemoteViews(context.packageName, R.layout.widget_workcount)

        views.setTextViewText(R.id.widget_title, "WorkCount")
        views.setOnClickPendingIntent(R.id.widget_open_button, openAppIntent(context))

        if (activeShift == null) {
            views.setTextViewText(R.id.widget_status, "No active shift")
            views.setTextViewText(R.id.widget_duration, "Ready")
            views.setTextViewText(R.id.widget_location, "Open the app to start")
            views.setViewVisibility(R.id.widget_pause_button, View.GONE)
            views.setViewVisibility(R.id.widget_stop_button, View.GONE)
        } else {
            val isPaused = activeShift.status == ShiftStatus.PAUSED
            val seconds = ShiftCalculators.payableDurationSeconds(activeShift, System.currentTimeMillis())
            views.setTextViewText(
                R.id.widget_status,
                if (isPaused) "Shift paused" else "Shift running",
            )
            views.setTextViewText(R.id.widget_duration, formatWidgetDuration(seconds))
            views.setTextViewText(
                R.id.widget_location,
                activeShift.locationText.ifBlank { "No place set" },
            )
            views.setTextViewText(
                R.id.widget_pause_button,
                if (isPaused) "Resume" else "Pause",
            )
            views.setViewVisibility(R.id.widget_pause_button, View.VISIBLE)
            views.setViewVisibility(R.id.widget_stop_button, View.VISIBLE)
            views.setOnClickPendingIntent(
                R.id.widget_pause_button,
                widgetActionIntent(context, ACTION_TOGGLE_PAUSE),
            )
            views.setOnClickPendingIntent(
                R.id.widget_stop_button,
                widgetActionIntent(context, ACTION_STOP_SHIFT),
            )
        }

        return views
    }

    private fun openAppIntent(context: Context): PendingIntent {
        return PendingIntent.getActivity(
            context,
            201,
            Intent(context, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    private fun widgetActionIntent(context: Context, action: String): PendingIntent {
        return PendingIntent.getBroadcast(
            context,
            action.hashCode(),
            Intent(context, WorkCountWidgetReceiver::class.java).setAction(action),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    private fun formatWidgetDuration(seconds: Long): String {
        val hours = seconds / 3600L
        val minutes = (seconds % 3600L) / 60L
        return "%02d:%02d".format(hours, minutes)
    }

    const val ACTION_TOGGLE_PAUSE: String = "com.workcount.app.widget.TOGGLE_PAUSE"
    const val ACTION_STOP_SHIFT: String = "com.workcount.app.widget.STOP_SHIFT"
}

class WorkCountWidgetReceiver : AppWidgetProvider() {
    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray,
    ) {
        super.onUpdate(context, appWidgetManager, appWidgetIds)
        WorkCountWidget.updateAll(context)
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        when (intent.action) {
            WorkCountWidget.ACTION_TOGGLE_PAUSE -> {
                val pendingResult = goAsync()
                CoroutineScope(Dispatchers.IO).launch {
                    try {
                        val repository = context.appContainer.shiftRepository
                        when (repository.observeActiveShift().first()?.status) {
                            ShiftStatus.ACTIVE -> repository.pauseActiveShift(System.currentTimeMillis())
                            ShiftStatus.PAUSED -> repository.resumeActiveShift(System.currentTimeMillis())
                            else -> Unit
                        }
                        WorkCountWidget.updateAll(context)
                        ShiftForegroundService.sync(context)
                    } finally {
                        pendingResult.finish()
                    }
                }
            }

            WorkCountWidget.ACTION_STOP_SHIFT -> {
                val pendingResult = goAsync()
                CoroutineScope(Dispatchers.IO).launch {
                    try {
                        context.appContainer.shiftRepository.stopActiveShift(System.currentTimeMillis())
                        WorkCountWidget.updateAll(context)
                        ShiftForegroundService.stop(context)
                    } finally {
                        pendingResult.finish()
                    }
                }
            }
        }
    }
}
