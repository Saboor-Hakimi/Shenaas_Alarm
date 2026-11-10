package com.shenaas.alarm.alarm

import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import android.util.Log
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import com.shenaas.alarm.challenge.ChallengePlanner
import com.shenaas.alarm.challenge.Sarcasm
import com.shenaas.alarm.data.AlarmRepository
import com.shenaas.alarm.data.Prefs
import com.shenaas.alarm.model.RingPhase
import com.shenaas.alarm.model.RingReason
import com.shenaas.alarm.model.RingState
import com.shenaas.alarm.ui.ring.RingActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.distinctUntilChangedBy
import kotlinx.coroutines.launch

/**
 * Foreground service that owns a ringing alarm: it plays the sound, keeps the CPU awake, keeps the
 * full-screen notification alive, and only stops once [RingSession] says the alarm is beaten,
 * snoozed or given up on.
 */
class AlarmService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val jobs = mutableListOf<Job>()
    private var player: AlarmPlayer? = null
    private var wakeLock: PowerManager.WakeLock? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> start(
                alarmId = intent.getIntExtra(AlarmReceiver.EXTRA_ALARM_ID, -1),
                reason = intent.getStringExtra(EXTRA_REASON)?.let { runCatching { RingReason.valueOf(it) }.getOrNull() } ?: RingReason.SCHEDULED,
            )
            ACTION_SNOOZE -> snooze()
            else -> {
                // Restarted by the system after the process died mid-ring.
                val saved = Prefs.activeRing
                if (saved != null) {
                    start(saved.alarm.id, RingReason.RESUMED)
                } else {
                    if (goForeground(null)) shutdown()
                }
            }
        }
        return START_STICKY
    }

    private fun start(alarmId: Int, reason: RingReason) {
        val current = RingSession.state.value?.takeIf { it.phase != RingPhase.DONE }
        if (current != null && player != null) {
            // One alarm at a time. A second alarm going off mid-ring just keeps this one going.
            goForeground(current)
            return
        }
        val state = current ?: newState(alarmId, reason)
        if (state == null) {
            if (goForeground(null)) shutdown()
            return
        }
        RingSession.begin(state)
        if (!goForeground(state)) return
        running = true

        val alarm = state.alarm
        Notifications.cancelSnoozed(this, alarm.id)
        acquireWakeLock()
        player = AlarmPlayer(this).also { it.start(alarm, scope) }

        jobs += scope.launch { RingSession.duck.collect { player?.setDuck(it) } }
        jobs += scope.launch {
            RingSession.state.distinctUntilChangedBy { it?.phase to it?.completed }.collect { s ->
                if (s == null || s.phase == RingPhase.DONE) {
                    shutdown()
                } else {
                    Notifications.repostRinging(this@AlarmService, alert = false)
                }
            }
        }
        val giveUpMinutes = Prefs.giveUpMinutes
        if (giveUpMinutes > 0) {
            jobs += scope.launch {
                delay(giveUpMinutes * 60_000L)
                RingFlow.missed(this@AlarmService)
            }
        }
        jobs += scope.launch {
            // Wandered off to another app? The alarm follows you.
            while (true) {
                delay(NAG_INTERVAL_MS)
                if (!RingSession.screenVisible) {
                    Notifications.repostRinging(this@AlarmService, alert = true)
                    launchRingScreen()
                }
            }
        }
        launchRingScreen()
    }

    /** A fresh ring, or the one a reboot or process death interrupted. */
    private fun newState(alarmId: Int, reason: RingReason): RingState? {
        val now = System.currentTimeMillis()
        val alarm = AlarmRepository.get(alarmId) ?: Prefs.activeRing?.alarm?.takeIf { it.id == alarmId } ?: return null
        val saved = Prefs.activeRing?.takeIf { it.alarm.id == alarmId && RingFlow.isResumable(it, now) }
        return when {
            saved != null -> saved.copy(alarm = alarm, reason = RingReason.RESUMED, phase = RingPhase.RINGING).withMessage(Sarcasm.RESUMED)
            reason == RingReason.WAKE_CHECK_FAILED ->
                RingState(alarm, reason, now, ChallengePlanner.plan(this, alarm, reason)).withMessage(Sarcasm.WAKE_CHECK_FAILED)
            else -> RingState(alarm, reason, now, ChallengePlanner.plan(this, alarm, reason))
        }
    }

    private fun snooze() {
        val state = RingSession.state.value
        if (state == null) {
            shutdown()
            return
        }
        if (!state.canSnooze) return
        val until = System.currentTimeMillis() + state.alarm.snoozeMinutes * 60_000L
        val alarm = AlarmRepository.update(state.alarm.id) { it.copy(snoozedUntil = until, snoozesUsed = it.snoozesUsed + 1) }
        if (alarm != null) {
            AlarmScheduler.schedule(this, alarm)
            Notifications.showSnoozed(this, alarm, until)
        }
        RingSession.clear()
    }

    private fun goForeground(state: RingState?): Boolean = try {
        ServiceCompat.startForeground(
            this,
            Notifications.ID_RINGING,
            Notifications.ringing(this, state),
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) ServiceInfo.FOREGROUND_SERVICE_TYPE_SYSTEM_EXEMPTED else 0,
        )
        true
    } catch (e: Exception) {
        // ForegroundServiceStartNotAllowedException or a type check failure. Hand over to the ring screen.
        Log.e(TAG, "Couldn't enter the foreground", e)
        state?.let { Notifications.showFallbackRing(this, it.alarm) }
        stopSelf()
        false
    }

    private fun launchRingScreen() {
        try {
            startActivity(Intent(this, RingActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        } catch (e: Exception) {
            Log.d(TAG, "Ring screen launch deferred to the full-screen notification", e)
        }
    }

    private fun acquireWakeLock() {
        if (wakeLock?.isHeld == true) return
        val timeoutMinutes = Prefs.giveUpMinutes.takeIf { it > 0 } ?: 180
        wakeLock = getSystemService(PowerManager::class.java)
            .newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "ShenaasAlarm:ring")
            .apply { acquire((timeoutMinutes + 5) * 60_000L) }
    }

    private fun shutdown() {
        running = false
        jobs.forEach { it.cancel() }
        jobs.clear()
        player?.stop()
        player = null
        wakeLock?.takeIf { it.isHeld }?.release()
        wakeLock = null
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    override fun onDestroy() {
        running = false
        player?.stop()
        player = null
        wakeLock?.takeIf { it.isHeld }?.release()
        scope.cancel()
        super.onDestroy()
    }

    companion object {
        private const val TAG = "AlarmService"
        private const val ACTION_START = "com.shenaas.alarm.action.START"
        private const val ACTION_SNOOZE = "com.shenaas.alarm.action.SNOOZE"
        private const val EXTRA_REASON = "reason"
        private const val NAG_INTERVAL_MS = 20_000L

        /** Whether a ring is actually being played right now. */
        @Volatile
        var running = false
            private set

        fun start(context: Context, alarmId: Int, reason: RingReason) {
            val intent = Intent(context, AlarmService::class.java)
                .setAction(ACTION_START)
                .putExtra(AlarmReceiver.EXTRA_ALARM_ID, alarmId)
                .putExtra(EXTRA_REASON, reason.name)
            try {
                ContextCompat.startForegroundService(context, intent)
            } catch (e: IllegalStateException) {
                Log.e(TAG, "Alarm service start blocked", e)
                AlarmRepository.get(alarmId)?.let { Notifications.showFallbackRing(context, it) }
            }
        }

        /** Called from the ring screen, which is in the foreground. */
        fun snooze(context: Context) {
            context.startService(Intent(context, AlarmService::class.java).setAction(ACTION_SNOOZE))
        }
    }
}
