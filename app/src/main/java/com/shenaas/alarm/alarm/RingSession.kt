package com.shenaas.alarm.alarm

import com.shenaas.alarm.data.Prefs
import com.shenaas.alarm.model.RingPhase
import com.shenaas.alarm.model.RingState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.updateAndGet

/** Process-wide state of the alarm that is ringing, shared by [AlarmService] and the ring screen. */
object RingSession {
    private val _state = MutableStateFlow<RingState?>(null)
    val state: StateFlow<RingState?> = _state.asStateFlow()

    /** Alarm loudness requested by the active challenge: 1 = full, 0 = silent. */
    val duck = MutableStateFlow(1f)

    /** True while the ring screen is in the foreground; the service nags with notifications otherwise. */
    @Volatile
    var screenVisible = false

    val isRinging: Boolean get() = _state.value?.let { it.phase != RingPhase.DONE } == true

    fun begin(state: RingState) {
        duck.value = 1f
        _state.value = state
        persist(state)
    }

    fun update(transform: (RingState) -> RingState) {
        persist(_state.updateAndGet { it?.let(transform) })
    }

    fun clear() {
        duck.value = 1f
        _state.value = null
        Prefs.activeRing = null
    }

    private fun persist(state: RingState?) {
        Prefs.activeRing = state?.takeIf { it.phase != RingPhase.DONE }
    }
}
