package com.shenaas.alarm.alarm

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.util.Log
import com.shenaas.alarm.MainActivity
import com.shenaas.alarm.data.AlarmRepository
import com.shenaas.alarm.data.Prefs
import com.shenaas.alarm.model.Alarm
import java.time.Instant
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime

object AlarmScheduler {
    private const val TAG = "AlarmScheduler"

    /** How long the sleeper has to answer the wake-up check. */
    const val WAKE_CHECK_WINDOW_MS = 100_000L

    /** Next time [alarm] should ring, or null if it shouldn't. A pending snooze wins over the schedule. */
    fun nextTrigger(alarm: Alarm, now: Long = System.currentTimeMillis()): Long? {
        if (alarm.snoozedUntil > now) return alarm.snoozedUntil
        if (!alarm.enabled) return null
        val zone = ZoneId.systemDefault()
        var date = Instant.ofEpochMilli(now).atZone(zone).toLocalDate()
        repeat(8) {
            val candidate = ZonedDateTime.of(date, LocalTime.of(alarm.hour, alarm.minute), zone).toInstant().toEpochMilli()
            if (candidate > now && (!alarm.isRepeating || alarm.repeatsOn(date.dayOfWeek))) return candidate
            date = date.plusDays(1)
        }
        return null
    }

    fun schedule(context: Context, alarm: Alarm) {
        val manager = context.getSystemService(AlarmManager::class.java)
        val at = nextTrigger(alarm)
        if (at == null) {
            manager.cancel(fireIntent(context, alarm.id, 0))
            return
        }
        val operation = fireIntent(context, alarm.id, at)
        setAlarmClock(context, manager, at, operation)
    }

    fun cancel(context: Context, alarmId: Int) {
        context.getSystemService(AlarmManager::class.java).cancel(fireIntent(context, alarmId, 0))
    }

    fun rescheduleAll(context: Context) {
        AlarmRepository.all().forEach { schedule(context, it) }
    }

    fun canScheduleExact(context: Context): Boolean =
        context.getSystemService(AlarmManager::class.java).canScheduleExactAlarms()

    /** Schedules the "are you awake?" notification after the alarm was beaten. */
    fun scheduleWakeCheck(context: Context, alarmId: Int) {
        val manager = context.getSystemService(AlarmManager::class.java)
        val at = System.currentTimeMillis() + Prefs.wakeCheckDelayMinutes * 60_000L
        val operation = receiverIntent(context, AlarmReceiver.ACTION_WAKE_CHECK, alarmId, REQUEST_WAKE_CHECK)
        try {
            manager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, operation)
        } catch (e: SecurityException) {
            Log.w(TAG, "Exact alarms not allowed, wake check will be inexact", e)
            manager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, operation)
        }
    }

    /** Re-rings the alarm if the wake-up check isn't answered in time. */
    fun scheduleWakeFail(context: Context, alarmId: Int) {
        val manager = context.getSystemService(AlarmManager::class.java)
        val at = System.currentTimeMillis() + WAKE_CHECK_WINDOW_MS
        setAlarmClock(context, manager, at, receiverIntent(context, AlarmReceiver.ACTION_WAKE_FAIL, alarmId, REQUEST_WAKE_FAIL))
    }

    fun cancelWakeTimers(context: Context, alarmId: Int) {
        val manager = context.getSystemService(AlarmManager::class.java)
        manager.cancel(receiverIntent(context, AlarmReceiver.ACTION_WAKE_CHECK, alarmId, REQUEST_WAKE_CHECK))
        manager.cancel(receiverIntent(context, AlarmReceiver.ACTION_WAKE_FAIL, alarmId, REQUEST_WAKE_FAIL))
    }

    private fun setAlarmClock(context: Context, manager: AlarmManager, at: Long, operation: PendingIntent) {
        val show = PendingIntent.getActivity(
            context,
            0,
            Intent(context, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        try {
            manager.setAlarmClock(AlarmManager.AlarmClockInfo(at, show), operation)
        } catch (e: SecurityException) {
            // Only possible on Android 12 if the user revoked "Alarms & reminders".
            Log.w(TAG, "Exact alarms not allowed, falling back to an inexact alarm", e)
            manager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, operation)
        }
    }

    private fun fireIntent(context: Context, alarmId: Int, triggerAt: Long): PendingIntent {
        val intent = Intent(context, AlarmReceiver::class.java)
            .setAction(AlarmReceiver.ACTION_FIRE)
            .putExtra(AlarmReceiver.EXTRA_ALARM_ID, alarmId)
            .putExtra(AlarmReceiver.EXTRA_TRIGGER_AT, triggerAt)
        return PendingIntent.getBroadcast(
            context,
            alarmId * REQUEST_STRIDE + REQUEST_FIRE,
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
    }

    internal fun receiverIntent(context: Context, action: String, alarmId: Int, slot: Int): PendingIntent =
        PendingIntent.getBroadcast(
            context,
            alarmId * REQUEST_STRIDE + slot,
            Intent(context, AlarmReceiver::class.java).setAction(action).putExtra(AlarmReceiver.EXTRA_ALARM_ID, alarmId),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )

    private const val REQUEST_STRIDE = 8
    private const val REQUEST_FIRE = 0
    private const val REQUEST_WAKE_CHECK = 1
    private const val REQUEST_WAKE_FAIL = 2
    internal const val REQUEST_WAKE_OK = 3
    internal const val REQUEST_RING_DISMISSED = 4
}
