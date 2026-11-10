package com.shenaas.alarm.ui.ring

import android.content.Context
import android.os.SystemClock
import com.shenaas.alarm.alarm.AlarmService
import com.shenaas.alarm.alarm.RingFlow
import com.shenaas.alarm.alarm.RingSession
import com.shenaas.alarm.alarm.TestAlarm
import com.shenaas.alarm.challenge.ChallengePlanner
import com.shenaas.alarm.challenge.Sarcasm
import com.shenaas.alarm.data.StatsRepository
import com.shenaas.alarm.model.Alarm
import com.shenaas.alarm.model.DoneInfo
import com.shenaas.alarm.model.RingPhase
import com.shenaas.alarm.model.RingReason
import com.shenaas.alarm.model.RingState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/** What the ring screen can do. Live rings drive [RingSession]; practice runs are local and silent. */
interface RingController {
    val state: StateFlow<RingState?>
    val practice: Boolean

    fun startChallenges()
    fun challengeWon(score: Float)
    fun challengeFailed(message: String?)
    /** Nothing happened for too long: back to the full-volume ringing screen. */
    fun idleKickback()
    /** The current challenge can't run on this phone right now; replace it. */
    fun swapCurrent()
    fun snooze()
    fun setDuck(level: Float)
    fun close()
}

private val progressLines = listOf("Fine. That one counts.", "Look who's waking up.", "Okay, okay. Next!", "Not bad for a zombie.")

private fun RingState.afterWin(now: Long): RingState =
    copy(completed = completed + 1, attempt = 0, currentStartedAt = now).withMessage(Sarcasm.pick(progressLines))

class LiveRingController(private val context: Context) : RingController {
    override val state: StateFlow<RingState?> = RingSession.state
    override val practice = false

    override fun startChallenges() {
        RingSession.update {
            if (it.phase == RingPhase.RINGING) it.copy(phase = RingPhase.CHALLENGE, currentStartedAt = SystemClock.elapsedRealtime()) else it
        }
    }

    override fun challengeWon(score: Float) {
        val current = state.value ?: return
        val spec = current.current ?: return
        val now = SystemClock.elapsedRealtime()
        if (!TestAlarm.isTest(current.alarm)) {
            StatsRepository.recordChallenge(spec.type, success = true, durationMs = now - current.currentStartedAt, score = score)
        }
        if (current.completed + 1 >= current.plan.size) {
            RingSession.update { it.copy(completed = it.plan.size) }
            RingFlow.complete(context)
        } else {
            RingSession.update { it.afterWin(now) }
        }
    }

    override fun challengeFailed(message: String?) {
        val current = state.value ?: return
        val spec = current.current ?: return
        if (!TestAlarm.isTest(current.alarm)) StatsRepository.recordChallenge(spec.type, success = false)
        RingSession.update { it.copy(fails = it.fails + 1).withMessage(message ?: Sarcasm.pick(Sarcasm.fails)) }
    }

    override fun idleKickback() {
        RingSession.duck.value = 1f
        RingSession.update { it.copy(phase = RingPhase.RINGING, attempt = it.attempt + 1).withMessage(Sarcasm.pick(Sarcasm.idle)) }
    }

    override fun swapCurrent() {
        RingSession.update { s ->
            val spec = s.current ?: return@update s
            val plan = s.plan.toMutableList().also { it[s.completed] = ChallengePlanner.fallbackFor(context, spec) }
            s.copy(plan = plan, attempt = s.attempt + 1, currentStartedAt = SystemClock.elapsedRealtime()).withMessage(Sarcasm.SWAPPED)
        }
    }

    override fun snooze() = AlarmService.snooze(context)

    override fun setDuck(level: Float) {
        RingSession.duck.value = level
    }

    override fun close() {
        if (state.value?.phase == RingPhase.DONE) RingSession.clear()
    }
}

/** Runs an alarm's challenges with no sound and no stats, so they can be tried out from the editor. */
class PracticeRingController(private val context: Context, alarm: Alarm) : RingController {
    private val startedAt = System.currentTimeMillis()
    private val _state = MutableStateFlow<RingState?>(
        RingState(
            alarm = alarm.copy(snoozeEnabled = false),
            reason = RingReason.SCHEDULED,
            startedAt = startedAt,
            plan = ChallengePlanner.practicePlan(alarm),
            phase = RingPhase.CHALLENGE,
            currentStartedAt = SystemClock.elapsedRealtime(),
        ),
    )
    override val state: StateFlow<RingState?> = _state.asStateFlow()
    override val practice = true

    override fun startChallenges() = _state.update { it?.copy(phase = RingPhase.CHALLENGE, currentStartedAt = SystemClock.elapsedRealtime()) }

    override fun challengeWon(score: Float) = _state.update { s ->
        s ?: return@update null
        if (s.completed + 1 >= s.plan.size) {
            s.copy(
                completed = s.plan.size,
                phase = RingPhase.DONE,
                done = DoneInfo(System.currentTimeMillis() - startedAt, s.fails, s.plan.size, 0, 0, wakeUpCheck = false),
            )
        } else {
            s.afterWin(SystemClock.elapsedRealtime())
        }
    }

    override fun challengeFailed(message: String?) =
        _state.update { it?.copy(fails = it.fails + 1)?.withMessage(message ?: Sarcasm.pick(Sarcasm.fails)) }

    override fun idleKickback() =
        _state.update { it?.copy(attempt = it.attempt + 1)?.withMessage(Sarcasm.pick(Sarcasm.idle)) }

    override fun swapCurrent() = _state.update { s ->
        val spec = s?.current ?: return@update s
        val plan = s.plan.toMutableList().also { it[s.completed] = ChallengePlanner.fallbackFor(context, spec) }
        s.copy(plan = plan, attempt = s.attempt + 1).withMessage(Sarcasm.SWAPPED)
    }

    override fun snooze() = Unit
    override fun setDuck(level: Float) = Unit
    override fun close() = Unit
}
