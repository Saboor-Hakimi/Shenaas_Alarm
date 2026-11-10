package com.shenaas.alarm.ui.ring.challenges

import android.Manifest
import android.content.pm.PackageManager
import android.hardware.Sensor
import android.os.SystemClock
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.core.content.ContextCompat
import com.shenaas.alarm.challenge.Tuning
import com.shenaas.alarm.model.ChallengeSpec
import com.shenaas.alarm.ui.theme.Brand
import kotlinx.coroutines.delay

/** Counts motion events up to a target and reports a win once, with a speed-based score. */
private class MotionCounter(val target: Int, private val expectedMsPerRep: Long, private val cb: ChallengeCallbacks) {
    private val startedAt = SystemClock.elapsedRealtime()
    private var won = false

    fun check(count: Int) {
        if (won || count < target) return
        won = true
        cb.onWin(speedScore(target * expectedMsPerRep, SystemClock.elapsedRealtime() - startedAt))
    }
}

@Composable
fun ShakeChallenge(spec: ChallengeSpec, cb: ChallengeCallbacks) {
    val manager = rememberSensorManager()
    val haptics = LocalHapticFeedback.current
    val target = remember { Tuning.shakeTarget(spec) }
    val counter = remember { MotionCounter(target, 450, cb) }
    val detector = remember { ShakeDetector(Tuning.shakeThreshold(spec)) }
    val linear = remember { manager.has(Sensor.TYPE_LINEAR_ACCELERATION) }
    var count by remember { mutableIntStateOf(0) }
    var lastShake by remember { mutableLongStateOf(SystemClock.elapsedRealtime()) }

    SensorEffect(if (linear) Sensor.TYPE_LINEAR_ACCELERATION else Sensor.TYPE_ACCELEROMETER, onFailure = cb.onUnavailable) { e ->
        val now = SystemClock.elapsedRealtime()
        val (x, y, z) = Triple(e.values[0], e.values[1], e.values[2])
        val shook = if (linear) detector.onLinear(x, y, z, now) else detector.onRaw(x, y, z, now)
        if (shook && count < target) {
            count++
            lastShake = now
            cb.onActivity()
            haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
            counter.check(count)
        }
    }
    if (spec.strict) {
        // Nightmare: stop shaking and your progress melts away.
        LaunchedEffect(Unit) {
            while (true) {
                delay(1000)
                if (count in 1 until target && SystemClock.elapsedRealtime() - lastShake > 3000) count--
            }
        }
    }
    CounterChallengeBody(
        emoji = "🫨",
        count = count,
        target = target,
        instruction = "Shake it like your bed is on fire",
        footnote = if (spec.strict) "Nightmare: stop for 3 seconds and progress melts." else "Big, hard shakes. Wimpy wiggles don't count.",
    )
}

@Composable
fun StepsChallenge(spec: ChallengeSpec, cb: ChallengeCallbacks) {
    val context = LocalContext.current
    val manager = rememberSensorManager()
    val target = remember { Tuning.stepTarget(spec) }
    val counter = remember { MotionCounter(target, 650, cb) }
    val useStepSensor = remember {
        manager.has(Sensor.TYPE_STEP_DETECTOR) &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.ACTIVITY_RECOGNITION) == PackageManager.PERMISSION_GRANTED
    }
    val fallback = remember { AccelStepDetector() }
    var count by remember { mutableIntStateOf(0) }

    fun step() {
        if (count >= target) return
        count++
        cb.onActivity()
        counter.check(count)
    }

    SensorEffect(if (useStepSensor) Sensor.TYPE_STEP_DETECTOR else Sensor.TYPE_ACCELEROMETER, onFailure = cb.onUnavailable) { e ->
        if (useStepSensor) {
            step()
        } else if (fallback.onRaw(e.values[0], e.values[1], e.values[2], SystemClock.elapsedRealtime())) {
            step()
        }
    }
    CounterChallengeBody(
        emoji = "🚶",
        count = count,
        target = target,
        instruction = "Walk. Out of the bedroom, ideally.",
        brush = Brand.Electric,
        footnote = "Hold the phone and keep moving. Steps can take a second to register.",
    )
}

@Composable
fun JumpChallenge(spec: ChallengeSpec, cb: ChallengeCallbacks) {
    val haptics = LocalHapticFeedback.current
    val target = remember { Tuning.jumpTarget(spec) }
    val counter = remember { MotionCounter(target, 1200, cb) }
    val detector = remember { JumpDetector() }
    var count by remember { mutableIntStateOf(0) }

    SensorEffect(Sensor.TYPE_ACCELEROMETER, rate = FAST_SENSOR_PERIOD_US, onFailure = cb.onUnavailable) { e ->
        if (detector.onRaw(e.values[0], e.values[1], e.values[2], SystemClock.elapsedRealtime()) && count < target) {
            count++
            cb.onActivity()
            haptics.performHapticFeedback(HapticFeedbackType.LongPress)
            counter.check(count)
        }
    }
    CounterChallengeBody(
        emoji = "🦘",
        count = count,
        target = target,
        instruction = "Jump! Phone in hand, feet off the floor.",
        footnote = "Real jumps only. Hold on tight, don't throw your phone.",
    )
}
