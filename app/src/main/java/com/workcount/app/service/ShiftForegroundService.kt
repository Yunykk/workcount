package com.workcount.app.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.workcount.app.MainActivity
import com.workcount.app.R
import com.workcount.app.appContainer
import com.workcount.app.widget.WorkCountWidget
import com.workcount.core.domain.ForgottenTimerPolicy
import com.workcount.core.domain.Shift
import com.workcount.core.domain.ShiftCalculators
import com.workcount.core.domain.ShiftStatus
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class ShiftForegroundService : Service() {
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private lateinit var notificationManager: NotificationManager
    private var currentShift: Shift? = null
    private var tickerJob: Job? = null
    private var lastReminderAt: Long = 0L

    override fun onCreate() {
        super.onCreate()
        notificationManager = getSystemService(NotificationManager::class.java)
        ensureChannels()
        observeActiveShift()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_PAUSE -> serviceScope.launch { appContainer.shiftRepository.pauseActiveShift(System.currentTimeMillis()) }
            ACTION_RESUME -> serviceScope.launch { appContainer.shiftRepository.resumeActiveShift(System.currentTimeMillis()) }
            ACTION_STOP -> serviceScope.launch { appContainer.shiftRepository.stopActiveShift(System.currentTimeMillis()) }
            ACTION_SYNC, null -> Unit
        }
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        tickerJob?.cancel()
        serviceScope.coroutineContext[Job]?.cancel()
        super.onDestroy()
    }

    private fun observeActiveShift() {
        serviceScope.launch {
            appContainer.shiftRepository.observeActiveShift().collect { shift ->
                currentShift = shift
                if (shift == null) {
                    tickerJob?.cancel()
                    stopForeground(STOP_FOREGROUND_REMOVE)
                    WorkCountWidget.updateAll(applicationContext)
                    stopSelf()
                } else {
                    startForeground(NOTIFICATION_ID, buildNotification(shift))
                    WorkCountWidget.updateAll(applicationContext)
                    startTicker()
                }
            }
        }
    }

    private fun startTicker() {
        if (tickerJob?.isActive == true) return
        tickerJob = serviceScope.launch {
            while (true) {
                currentShift?.let { shift ->
                    notificationManager.notify(NOTIFICATION_ID, buildNotification(shift))
                    maybeSendReminder(shift)
                }
                delay(5_000)
            }
        }
    }

    private suspend fun maybeSendReminder(shift: Shift) {
        val settings = appContainer.settingsRepository.observeSettings().first()
        if (settings.forgottenTimerPolicy == ForgottenTimerPolicy.NONE) return
        if (shift.status == ShiftStatus.COMPLETED) return

        val elapsedMillis = System.currentTimeMillis() - shift.startedAtEpochMillis
        val thresholdMillis = settings.reminderThresholdHours * 60L * 60L * 1000L
        val repeatMillis = settings.reminderRepeatMinutes * 60L * 1000L
        if (elapsedMillis < thresholdMillis) return

        if (settings.forgottenTimerPolicy == ForgottenTimerPolicy.AUTO_STOP) {
            appContainer.shiftRepository.stopActiveShift(System.currentTimeMillis())
            notificationManager.notify(
                REMINDER_ID,
                NotificationCompat.Builder(this, REMINDER_CHANNEL_ID)
                    .setSmallIcon(R.drawable.ic_launcher_foreground)
                    .setContentTitle("Shift stopped")
                    .setContentText("WorkCount stopped a forgotten timer.")
                    .setPriority(NotificationCompat.PRIORITY_HIGH)
                    .setAutoCancel(true)
                    .build(),
            )
            return
        }

        if (System.currentTimeMillis() - lastReminderAt < repeatMillis) return

        lastReminderAt = System.currentTimeMillis()
        notificationManager.notify(
            REMINDER_ID,
            NotificationCompat.Builder(this, REMINDER_CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_launcher_foreground)
                .setContentTitle("Timer is still running")
                .setContentText("Check whether this shift should be stopped.")
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setAutoCancel(true)
                .build(),
        )
    }

    private fun buildNotification(shift: Shift): Notification {
        val duration = formatDuration(ShiftCalculators.payableDurationSeconds(shift, System.currentTimeMillis()))
        val title = if (shift.status == ShiftStatus.PAUSED) "WorkCount: paused" else "WorkCount: shift running"
        val place = shift.locationText.ifBlank { "No place set" }
        val contentText = "$duration - $place"

        val openAppIntent = PendingIntent.getActivity(
            this,
            10,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        val actionIntent = PendingIntent.getService(
            this,
            if (shift.status == ShiftStatus.PAUSED) 11 else 12,
            Intent(this, ShiftForegroundService::class.java).setAction(
                if (shift.status == ShiftStatus.PAUSED) ACTION_RESUME else ACTION_PAUSE,
            ),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val stopIntent = PendingIntent.getService(
            this,
            13,
            Intent(this, ShiftForegroundService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle(title)
            .setContentText(contentText)
            .setContentIntent(openAppIntent)
            .setOngoing(true)
            .addAction(
                0,
                if (shift.status == ShiftStatus.PAUSED) "Resume" else "Pause",
                actionIntent,
            )
            .addAction(0, "Stop", stopIntent)
            .build()
    }

    private fun ensureChannels() {
        notificationManager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_ID,
                "WorkCount timer",
                NotificationManager.IMPORTANCE_LOW,
            ),
        )
        notificationManager.createNotificationChannel(
            NotificationChannel(
                REMINDER_CHANNEL_ID,
                "WorkCount reminders",
                NotificationManager.IMPORTANCE_HIGH,
            ),
        )
    }

    companion object {
        private const val CHANNEL_ID = "workcount_timer_channel"
        private const val REMINDER_CHANNEL_ID = "workcount_reminder_channel"
        private const val NOTIFICATION_ID = 101
        private const val REMINDER_ID = 102
        private const val ACTION_SYNC = "com.workcount.app.action.SYNC"
        private const val ACTION_PAUSE = "com.workcount.app.action.PAUSE"
        private const val ACTION_RESUME = "com.workcount.app.action.RESUME"
        private const val ACTION_STOP = "com.workcount.app.action.STOP"

        fun sync(context: Context) {
            ContextCompat.startForegroundService(
                context,
                Intent(context, ShiftForegroundService::class.java).setAction(ACTION_SYNC),
            )
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, ShiftForegroundService::class.java))
        }
    }
}

private fun formatDuration(seconds: Long): String {
    val hours = seconds / 3600L
    val minutes = (seconds % 3600L) / 60L
    return "%02d:%02d".format(hours, minutes)
}
