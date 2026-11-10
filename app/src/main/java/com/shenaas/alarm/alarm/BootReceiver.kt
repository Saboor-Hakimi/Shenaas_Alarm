package com.shenaas.alarm.alarm

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.shenaas.alarm.data.AlarmRepository
import com.shenaas.alarm.data.Prefs

/** Re-arms alarms after a reboot, app update or clock change, and resumes a ring a reboot interrupted. */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val now = System.currentTimeMillis()
        val interrupted = Prefs.activeRing
        if (interrupted != null && !RingSession.isRinging) {
            if (RingFlow.isResumable(interrupted, now)) {
                // Rebooting won't save you: the interrupted alarm comes right back.
                val alarm = AlarmRepository.get(interrupted.alarm.id) ?: interrupted.alarm
                AlarmRepository.upsert(alarm.copy(snoozedUntil = now + RESUME_DELAY_MS))
            } else {
                Prefs.activeRing = null
            }
        }
        AlarmScheduler.rescheduleAll(context)
    }

    private companion object {
        const val RESUME_DELAY_MS = 3_000L
    }
}
