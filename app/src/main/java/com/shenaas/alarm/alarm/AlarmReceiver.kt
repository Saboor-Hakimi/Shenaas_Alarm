package com.shenaas.alarm.alarm

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.shenaas.alarm.data.AlarmRepository
import com.shenaas.alarm.data.Prefs
import com.shenaas.alarm.data.StatsRepository
import com.shenaas.alarm.data.WakeRecord
import com.shenaas.alarm.model.RingReason

class AlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val alarmId = intent.getIntExtra(EXTRA_ALARM_ID, -1)
        when (intent.action) {
            ACTION_FIRE -> fire(context, alarmId, intent.getLongExtra(EXTRA_TRIGGER_AT, 0L))
            ACTION_WAKE_CHECK -> {
                Notifications.showWakeCheck(context, alarmId)
                AlarmScheduler.scheduleWakeFail(context, alarmId)
            }
            ACTION_WAKE_OK -> {
                AlarmScheduler.cancelWakeTimers(context, alarmId)
                Notifications.cancelWakeCheck(context, alarmId)
                StatsRepository.recordWakeCheck(passed = true)
            }
            ACTION_WAKE_FAIL -> {
                Notifications.cancelWakeCheck(context, alarmId)
                StatsRepository.recordWakeCheck(passed = false)
                AlarmService.start(context, alarmId, RingReason.WAKE_CHECK_FAILED)
            }
            ACTION_RING_NOTIFICATION_DISMISSED -> Notifications.repostRinging(context, alert = false)
        }
    }

    private fun fire(context: Context, alarmId: Int, triggerAt: Long) {
        val alarm = AlarmRepository.get(alarmId) ?: return
        val now = System.currentTimeMillis()
        val resuming = Prefs.activeRing?.alarm?.id == alarmId
        val snoozed = alarm.snoozedUntil != 0L
        if (!alarm.enabled && !snoozed && !resuming) return

        // One-time alarms switch themselves off; repeating ones are re-armed for the next day right away.
        val updated = alarm.copy(snoozedUntil = 0L, enabled = alarm.enabled && alarm.isRepeating)
        AlarmRepository.upsert(updated)
        AlarmScheduler.schedule(context, updated)

        if (!resuming && triggerAt > 0 && now - triggerAt > LATE_LIMIT_MS) {
            // The phone was off when it should have rung. Ringing an hour late helps nobody.
            Notifications.showMissed(context, alarm)
            StatsRepository.recordWake(WakeRecord(now, alarmId, 0, 0, 0, alarm.snoozesUsed, missed = true))
            return
        }
        val reason = when {
            resuming -> RingReason.RESUMED
            snoozed -> RingReason.SNOOZE
            else -> RingReason.SCHEDULED
        }
        AlarmService.start(context, alarmId, reason)
    }

    companion object {
        const val ACTION_FIRE = "com.shenaas.alarm.action.FIRE"
        const val ACTION_WAKE_CHECK = "com.shenaas.alarm.action.WAKE_CHECK"
        const val ACTION_WAKE_OK = "com.shenaas.alarm.action.WAKE_OK"
        const val ACTION_WAKE_FAIL = "com.shenaas.alarm.action.WAKE_FAIL"
        const val ACTION_RING_NOTIFICATION_DISMISSED = "com.shenaas.alarm.action.RING_DISMISSED"
        const val EXTRA_ALARM_ID = "alarm_id"
        const val EXTRA_TRIGGER_AT = "trigger_at"
        private const val LATE_LIMIT_MS = 60 * 60 * 1000L
    }
}
