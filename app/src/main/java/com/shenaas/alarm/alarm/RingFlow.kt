package com.shenaas.alarm.alarm

import android.content.Context
import com.shenaas.alarm.challenge.ChallengePlanner
import com.shenaas.alarm.data.AlarmRepository
import com.shenaas.alarm.data.Prefs
import com.shenaas.alarm.data.StatsRepository
import com.shenaas.alarm.data.WakeRecord
import com.shenaas.alarm.model.DoneInfo
import com.shenaas.alarm.model.RingPhase
import com.shenaas.alarm.model.RingState

/** How a ring ends. [AlarmService] watches [RingSession] and shuts itself down when it does. */
object RingFlow {
    /** An interrupted ring (reboot, app update, process death) comes back if it started this recently. */
    private const val RESUME_LIMIT_MS = 3 * 60 * 60 * 1000L

    fun isResumable(state: RingState, now: Long = System.currentTimeMillis()) = now - state.startedAt < RESUME_LIMIT_MS

    /** Every challenge is beaten: log the win, arm the wake-up check, stop the noise. */
    fun complete(context: Context) {
        val state = RingSession.state.value ?: return
        if (state.phase == RingPhase.DONE) return
        val now = System.currentTimeMillis()
        if (TestAlarm.isTest(state.alarm)) {
            AlarmRepository.delete(state.alarm.id)
            val done = DoneInfo(now - state.startedAt, state.fails, state.plan.size, 0, StatsRepository.stats.value.streakDays(), wakeUpCheck = false)
            RingSession.update { it.copy(phase = RingPhase.DONE, done = done) }
            return
        }
        val alarm = AlarmRepository.update(state.alarm.id) { it.copy(snoozedUntil = 0L, snoozesUsed = 0) }
        alarm?.let { AlarmScheduler.schedule(context, it) }
        StatsRepository.recordWake(
            WakeRecord(
                at = now,
                alarmId = state.alarm.id,
                durationMs = now - state.startedAt,
                challenges = state.plan.size,
                fails = state.fails,
                snoozes = state.alarm.snoozesUsed,
                missed = false,
            ),
        )
        val wakeCheck = state.alarm.wakeUpCheck
        if (wakeCheck) AlarmScheduler.scheduleWakeCheck(context, state.alarm.id)
        val done = DoneInfo(
            durationMs = now - state.startedAt,
            fails = state.fails,
            challenges = state.plan.size,
            snoozes = state.alarm.snoozesUsed,
            streakDays = StatsRepository.stats.value.streakDays(),
            wakeUpCheck = wakeCheck,
        )
        RingSession.update { it.copy(phase = RingPhase.DONE, done = done) }
    }

    /**
     * Called from the uncaught-exception handler. If a challenge just crashed the app, persist the
     * ring with that challenge swapped out, so the resumed alarm can't crash-loop on it.
     */
    fun recoverFromCrash(context: Context) {
        val state = RingSession.state.value ?: return
        if (state.phase != RingPhase.CHALLENGE) return
        val spec = state.current ?: return
        val plan = state.plan.toMutableList().also { it[state.completed] = ChallengePlanner.fallbackFor(context, spec) }
        Prefs.activeRing = state.copy(plan = plan, phase = RingPhase.RINGING)
    }

    /** The alarm rang for [com.shenaas.alarm.data.Prefs.giveUpMinutes] without being beaten. */
    fun missed(context: Context) {
        val state = RingSession.state.value ?: return
        val now = System.currentTimeMillis()
        if (TestAlarm.isTest(state.alarm)) {
            AlarmRepository.delete(state.alarm.id)
            RingSession.clear()
            return
        }
        val alarm = AlarmRepository.update(state.alarm.id) { it.copy(snoozedUntil = 0L, snoozesUsed = 0) }
        alarm?.let { AlarmScheduler.schedule(context, it) }
        StatsRepository.recordWake(
            WakeRecord(now, state.alarm.id, now - state.startedAt, state.plan.size, state.fails, state.alarm.snoozesUsed, missed = true),
        )
        Notifications.showMissed(context, state.alarm)
        RingSession.clear()
    }
}
