package com.shenaas.alarm.alarm

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.RingtoneManager
import androidx.core.app.NotificationCompat
import com.shenaas.alarm.MainActivity
import com.shenaas.alarm.R
import com.shenaas.alarm.model.Alarm
import com.shenaas.alarm.model.RingReason
import com.shenaas.alarm.model.RingState
import com.shenaas.alarm.ui.ring.RingActivity
import com.shenaas.alarm.util.TimeText

object Notifications {
    const val CHANNEL_RINGING = "ringing"
    private const val CHANNEL_SNOOZED = "snoozed"
    private const val CHANNEL_WAKE_CHECK = "wake_check"
    private const val CHANNEL_MISSED = "missed"

    const val ID_RINGING = 666
    private fun idSnoozed(alarmId: Int) = 10_000 + alarmId
    private fun idWakeCheck(alarmId: Int) = 20_000 + alarmId
    private fun idMissed(alarmId: Int) = 30_000 + alarmId

    private const val BRAND_COLOR = 0xFFF34500.toInt()

    fun createChannels(context: Context) {
        val manager = context.getSystemService(NotificationManager::class.java)
        manager.createNotificationChannels(
            listOf(
                NotificationChannel(CHANNEL_RINGING, context.getString(R.string.channel_ringing), NotificationManager.IMPORTANCE_HIGH).apply {
                    description = context.getString(R.string.channel_ringing_desc)
                    // AlarmPlayer makes the noise; the notification itself stays quiet.
                    setSound(null, null)
                    enableVibration(false)
                    setBypassDnd(true)
                    lockscreenVisibility = Notification.VISIBILITY_PUBLIC
                },
                NotificationChannel(CHANNEL_SNOOZED, context.getString(R.string.channel_snoozed), NotificationManager.IMPORTANCE_LOW),
                NotificationChannel(CHANNEL_WAKE_CHECK, context.getString(R.string.channel_wake_check), NotificationManager.IMPORTANCE_HIGH).apply {
                    description = context.getString(R.string.channel_wake_check_desc)
                    setSound(
                        RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION),
                        AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_ALARM).build(),
                    )
                    enableVibration(true)
                    setBypassDnd(true)
                },
                NotificationChannel(CHANNEL_MISSED, context.getString(R.string.channel_missed), NotificationManager.IMPORTANCE_DEFAULT),
            ),
        )
    }

    fun ringing(context: Context, state: RingState?, alert: Boolean = false): Notification {
        val remaining = state?.let { it.plan.size - it.completed } ?: 1
        val time = state?.let { TimeText.clock(context, it.alarm.hour, it.alarm.minute) }
        val title = when {
            state == null -> "Wake up!"
            state.reason == RingReason.WAKE_CHECK_FAILED -> "You fell back asleep. Wrong move."
            state.alarm.label.isNotBlank() -> "${state.alarm.label} · $time"
            else -> "Wake up! It's $time"
        }
        val text = "$remaining ${if (remaining == 1) "challenge stands" else "challenges stand"} between you and silence. No shortcuts."
        val screen = ringScreen(context)
        return NotificationCompat.Builder(context, CHANNEL_RINGING)
            .setSmallIcon(R.drawable.ic_stat_alarm)
            .setColor(BRAND_COLOR)
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setOngoing(true)
            .setAutoCancel(false)
            .setOnlyAlertOnce(!alert)
            .setFullScreenIntent(screen, true)
            .setContentIntent(screen)
            .setDeleteIntent(
                AlarmScheduler.receiverIntent(context, AlarmReceiver.ACTION_RING_NOTIFICATION_DISMISSED, 0, AlarmScheduler.REQUEST_RING_DISMISSED),
            )
            .addAction(0, "Face the challenges", screen)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .build()
    }

    /** Re-posts the ringing notification; used when the sleeper swipes it away or wanders off. */
    fun repostRinging(context: Context, alert: Boolean) {
        val state = RingSession.state.value ?: return
        if (!RingSession.isRinging) return
        manager(context).notify(ID_RINGING, ringing(context, state, alert))
    }

    fun showSnoozed(context: Context, alarm: Alarm, until: Long) {
        val notification = NotificationCompat.Builder(context, CHANNEL_SNOOZED)
            .setSmallIcon(R.drawable.ic_stat_alarm)
            .setColor(BRAND_COLOR)
            .setContentTitle("Snoozed until ${TimeText.clock(context, until)}")
            .setContentText("+1 challenge added to the next round. Hope it was worth it.")
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setOngoing(true)
            .setShowWhen(false)
            .setContentIntent(mainScreen(context))
            .build()
        manager(context).notify(idSnoozed(alarm.id), notification)
    }

    fun cancelSnoozed(context: Context, alarmId: Int) = manager(context).cancel(idSnoozed(alarmId))

    fun showWakeCheck(context: Context, alarmId: Int) {
        val ok = AlarmScheduler.receiverIntent(context, AlarmReceiver.ACTION_WAKE_OK, alarmId, AlarmScheduler.REQUEST_WAKE_OK)
        val notification = NotificationCompat.Builder(context, CHANNEL_WAKE_CHECK)
            .setSmallIcon(R.drawable.ic_stat_alarm)
            .setColor(BRAND_COLOR)
            .setContentTitle("Are you awake? 👀")
            .setContentText("Tap within 100 seconds or the alarm comes back. Angrier.")
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setAutoCancel(true)
            .setContentIntent(ok)
            .addAction(0, "I'M AWAKE", ok)
            .setTimeoutAfter(AlarmScheduler.WAKE_CHECK_WINDOW_MS)
            .build()
        manager(context).notify(idWakeCheck(alarmId), notification)
    }

    fun cancelWakeCheck(context: Context, alarmId: Int) = manager(context).cancel(idWakeCheck(alarmId))

    fun showMissed(context: Context, alarm: Alarm) {
        val notification = NotificationCompat.Builder(context, CHANNEL_MISSED)
            .setSmallIcon(R.drawable.ic_stat_alarm)
            .setColor(BRAND_COLOR)
            .setContentTitle("You slept through your ${TimeText.clock(context, alarm.hour, alarm.minute)} alarm")
            .setContentText("Impressive. Concerning, but impressive.")
            .setAutoCancel(true)
            .setContentIntent(mainScreen(context))
            .build()
        manager(context).notify(idMissed(alarm.id), notification)
    }

    fun ringScreen(context: Context, alarmId: Int? = null): PendingIntent {
        val intent = Intent(context, RingActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_NO_USER_ACTION)
        if (alarmId != null) intent.putExtra(AlarmReceiver.EXTRA_ALARM_ID, alarmId)
        return PendingIntent.getActivity(context, alarmId ?: 0, intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
    }

    /**
     * Last resort when the alarm service isn't allowed to start: a full-screen notification that
     * opens the ring screen, which then starts the service from the foreground.
     */
    fun showFallbackRing(context: Context, alarm: Alarm) {
        val screen = ringScreen(context, alarm.id)
        val notification = NotificationCompat.Builder(context, CHANNEL_RINGING)
            .setSmallIcon(R.drawable.ic_stat_alarm)
            .setColor(BRAND_COLOR)
            .setContentTitle("Wake up! It's ${TimeText.clock(context, alarm.hour, alarm.minute)}")
            .setContentText("Tap to face your challenges.")
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setFullScreenIntent(screen, true)
            .setContentIntent(screen)
            .setOngoing(true)
            .build()
        manager(context).notify(ID_RINGING, notification)
    }

    fun cancelRinging(context: Context) = manager(context).cancel(ID_RINGING)

    private fun mainScreen(context: Context): PendingIntent = PendingIntent.getActivity(
        context,
        0,
        Intent(context, MainActivity::class.java),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

    private fun manager(context: Context) = context.getSystemService(NotificationManager::class.java)
}
