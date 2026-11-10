package com.shenaas.alarm.ui.ring.challenges

import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.media.AudioTrack
import android.util.Log
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.shenaas.alarm.alarm.LabAudio
import com.shenaas.alarm.alarm.SoundSynth
import com.shenaas.alarm.ui.components.RingProgress
import com.shenaas.alarm.ui.theme.Brand
import com.shenaas.alarm.ui.theme.ClockStyle
import kotlin.math.sqrt

/** How a challenge talks back to the ring screen. */
class ChallengeCallbacks(
    /** Any sign of life; resets the idle watchdog. Cheap enough to call on every sensor event. */
    val onActivity: () -> Unit,
    /** Beaten. [score] in 0..1 rates how convincingly, and feeds adaptive difficulty. */
    val onWin: (score: Float) -> Unit,
    /** Failed an attempt; null picks a random sarcastic line. */
    val onFail: (message: String?) -> Unit,
    /** Can't run on this phone right now (missing permission, no speech engine...). */
    val onUnavailable: () -> Unit,
    /** Alarm loudness while this challenge runs: 1 = full, 0 = silent. */
    val setDuck: (Float) -> Unit,
)

fun SensorManager.has(type: Int) = getDefaultSensor(type) != null

@Composable
fun rememberSensorManager(): SensorManager {
    val context = LocalContext.current
    return remember { context.getSystemService(SensorManager::class.java) }
}

/** 100 Hz: fast enough for jump detection, under the 200 Hz cap that needs HIGH_SAMPLING_RATE_SENSORS. */
const val FAST_SENSOR_PERIOD_US = 10_000

/**
 * Listens to [type] while composed. Returns false when the phone has no such sensor; calls
 * [onFailure] if the sensor exists but refuses to start, so a challenge can never crash an alarm.
 */
@Composable
fun SensorEffect(
    type: Int,
    rate: Int = SensorManager.SENSOR_DELAY_GAME,
    onFailure: () -> Unit = {},
    onEvent: (SensorEvent) -> Unit,
): Boolean {
    val manager = rememberSensorManager()
    val sensor = remember(type) { manager.getDefaultSensor(type) }
    val callback by rememberUpdatedState(onEvent)
    val failure by rememberUpdatedState(onFailure)
    DisposableEffect(sensor) {
        if (sensor == null) return@DisposableEffect onDispose { }
        val listener = object : SensorEventListener {
            override fun onSensorChanged(event: SensorEvent) = callback(event)
            override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit
        }
        val registered = try {
            manager.registerListener(listener, sensor, rate)
        } catch (e: Exception) {
            Log.w("SensorEffect", "Couldn't listen to sensor $type", e)
            false
        }
        if (!registered) failure()
        onDispose { manager.unregisterListener(listener) }
    }
    return sensor != null
}

private fun magnitude(x: Float, y: Float, z: Float) = sqrt(x * x + y * y + z * z)

/** Counts hard shakes from linear acceleration, or from raw acceleration with gravity filtered out. */
class ShakeDetector(private val threshold: Float, private val minGapMs: Long = 220) {
    private val gravity = FloatArray(3)
    private var primed = false
    private var last = 0L

    fun onLinear(x: Float, y: Float, z: Float, nowMs: Long): Boolean {
        if (magnitude(x, y, z) > threshold && nowMs - last > minGapMs) {
            last = nowMs
            return true
        }
        return false
    }

    fun onRaw(x: Float, y: Float, z: Float, nowMs: Long): Boolean {
        if (!primed) {
            gravity[0] = x; gravity[1] = y; gravity[2] = z
            primed = true
        }
        gravity[0] = 0.8f * gravity[0] + 0.2f * x
        gravity[1] = 0.8f * gravity[1] + 0.2f * y
        gravity[2] = 0.8f * gravity[2] + 0.2f * z
        return onLinear(x - gravity[0], y - gravity[1], z - gravity[2], nowMs)
    }
}

/** Step counting from the accelerometer, for phones without (or without permission for) a step sensor. */
class AccelStepDetector {
    private var smoothed = SensorManager.GRAVITY_EARTH
    private var above = false
    private var last = 0L

    fun onRaw(x: Float, y: Float, z: Float, nowMs: Long): Boolean {
        smoothed = smoothed * 0.75f + magnitude(x, y, z) * 0.25f
        if (!above && smoothed > 11.2f && nowMs - last > 280) {
            above = true
            last = nowMs
            return true
        }
        if (above && smoothed < 9.6f) above = false
        return false
    }
}

/**
 * A jump with the phone in hand reads as near-zero g while airborne followed by a hard landing
 * spike. Tilting or shaking doesn't produce that sequence.
 */
class JumpDetector {
    private var weightlessSince = -1L
    private var airborneAt = -1L
    private var lastJump = 0L

    fun onRaw(x: Float, y: Float, z: Float, nowMs: Long): Boolean {
        val g = magnitude(x, y, z)
        if (g < 4.5f) {
            if (weightlessSince < 0) weightlessSince = nowMs
            if (nowMs - weightlessSince >= 70 && airborneAt < 0) airborneAt = nowMs
        } else {
            weightlessSince = -1
        }
        if (airborneAt > 0) {
            if (g > 15f) {
                airborneAt = -1
                if (nowMs - lastJump > 350) {
                    lastJump = nowMs
                    return true
                }
            } else if (nowMs - airborneAt > 1200) {
                airborneAt = -1
            }
        }
        return false
    }
}

/** False in silent test runs from the Test Lab: rhythm challenges keep their visual beat only. */
val LocalBeatsEnabled = staticCompositionLocalOf { true }

/** True in Test Lab runs set to headphones: beats play only there, never on the speaker. */
val LocalBeatsToHeadphones = staticCompositionLocalOf { false }

/** Plays a drum loop on the alarm stream while composed, so it's audible even with media muted. */
@Composable
fun BeatEffect(bpm: Float, playing: Boolean, volume: Float = 0.35f) {
    if (!LocalBeatsEnabled.current) return
    val context = LocalContext.current
    val headphones = LocalBeatsToHeadphones.current
    val device = remember(headphones) { if (headphones) LabAudio.device(context) else null }
    if (headphones && device == null) return
    val track = remember(bpm, device) {
        val pcm = SoundSynth.beatLoop(bpm)
        if (device != null) SoundSynth.loopTrack(pcm, LabAudio.MEDIA_ATTRIBUTES, device) else SoundSynth.loopTrack(pcm)
    }
    DisposableEffect(track) {
        onDispose { runCatching { track.stop(); track.release() } }
    }
    LaunchedEffect(track, playing) {
        if (playing) {
            track.setVolume(volume)
            track.play()
        } else if (track.playState == AudioTrack.PLAYSTATE_PLAYING) {
            track.pause()
            track.setPlaybackHeadPosition(0)
        }
    }
}

/** Big ring counter shared by the shake / steps / jump challenges. */
@Composable
fun CounterChallengeBody(
    emoji: String,
    count: Int,
    target: Int,
    instruction: String,
    brush: Brush = Brand.Hellfire,
    footnote: String? = null,
) {
    val bump = remember { Animatable(1f) }
    LaunchedEffect(count) {
        if (count == 0) return@LaunchedEffect
        bump.snapTo(1.12f)
        bump.animateTo(1f, spring(dampingRatio = Spring.DampingRatioMediumBouncy))
    }
    Column(
        Modifier
            .fillMaxSize()
            .padding(horizontal = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        RingProgress(
            progress = count / target.toFloat(),
            brush = brush,
            stroke = 18.dp,
            modifier = Modifier
                .size(260.dp)
                .scale(bump.value),
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(emoji, fontSize = 52.sp)
                Text("$count", style = ClockStyle.copy(fontSize = 72.sp), color = Brand.Text)
                Text("of $target", style = MaterialTheme.typography.titleMedium, color = Brand.TextSecondary)
            }
        }
        Spacer(Modifier.height(28.dp))
        Text(
            instruction,
            style = MaterialTheme.typography.headlineSmall,
            color = Brand.Text,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )
        if (footnote != null) {
            Spacer(Modifier.height(8.dp))
            Text(footnote, style = MaterialTheme.typography.bodyMedium, color = Brand.TextSecondary, textAlign = TextAlign.Center)
        }
    }
}

/** Ratio of how long a challenge should take to how long it did, clamped to 0..1. */
fun speedScore(expectedMs: Long, actualMs: Long): Float =
    if (actualMs <= 0) 1f else (expectedMs.toFloat() / actualMs).coerceIn(0f, 1f)
