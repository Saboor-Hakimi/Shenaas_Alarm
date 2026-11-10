package com.shenaas.alarm.ui.ring.challenges

import android.hardware.Sensor
import android.media.AudioManager
import android.os.SystemClock
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.Nightlife
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.shenaas.alarm.challenge.BeatWords
import com.shenaas.alarm.challenge.ChallengePlanner
import com.shenaas.alarm.challenge.DanceMove
import com.shenaas.alarm.challenge.Sarcasm
import com.shenaas.alarm.challenge.SpeechMatch
import com.shenaas.alarm.challenge.Tuning
import com.shenaas.alarm.model.ChallengeSpec
import com.shenaas.alarm.model.ChallengeType
import com.shenaas.alarm.ui.components.ButtonKind
import com.shenaas.alarm.ui.components.HellButton
import com.shenaas.alarm.ui.components.IconBubble
import com.shenaas.alarm.ui.components.RingProgress
import com.shenaas.alarm.ui.theme.Brand
import com.shenaas.alarm.ui.theme.ClockStyle
import kotlinx.coroutines.delay
import kotlin.math.abs
import kotlin.math.sqrt
import kotlin.random.Random

private enum class BeatPhase { READY, COUNT_IN, RUN, RESULT }

/**
 * Recognition lags speech, and freshly woken people speak late and slowly, so a word still counts
 * this long after its slot ends. Easier levels are more forgiving.
 */
private fun speechGraceMs(level: Int) = longArrayOf(1400, 1200, 1000, 850)[level.coerceIn(0, 3)]
private const val EARLY_MS = 200L

@Composable
fun WordBeatChallenge(spec: ChallengeSpec, cb: ChallengeCallbacks) {
    val context = LocalContext.current
    val available = remember { ChallengePlanner.isAvailable(context, ChallengeType.WORD_BEAT) }
    if (!available) {
        LaunchedEffect(Unit) { cb.onUnavailable() }
        return
    }
    val haptics = LocalHapticFeedback.current
    val audio = remember { context.getSystemService(AudioManager::class.java) }
    val interval = remember { Tuning.beatIntervalMs(spec) }
    val beatMs = interval / 2
    val passRatio = remember { Tuning.beatPassRatio(spec) }
    val level = spec.difficulty.level
    val grace = remember { speechGraceMs(level) }
    var attempt by remember { mutableIntStateOf(0) }
    var failedAttempts by remember { mutableIntStateOf(0) }
    val words = remember(attempt) { BeatWords.sequence(Tuning.beatWords(spec), twoWords = spec.strict, Random(spec.seed + attempt)) }
    val hits: SnapshotStateList<Boolean?> = remember(attempt) { mutableStateListOf<Boolean?>().apply { repeat(words.size) { add(null) } } }
    val slots = remember(attempt) { LongArray(words.size) }
    val firstHeard = remember(attempt) { HashMap<String, Long>() }
    var phase by remember { mutableStateOf(BeatPhase.READY) }
    var index by remember { mutableIntStateOf(-1) }
    var countIn by remember { mutableIntStateOf(3) }
    var transcript by remember { mutableStateOf("") }
    var micLevel by remember { mutableFloatStateOf(0f) }
    var mutedMusic by remember { mutableStateOf(false) }
    // In headphone lab runs the beat itself is on the media stream, so it mustn't be muted.
    val beatOnMedia = LocalBeatsToHeadphones.current

    fun judge(now: Long) {
        words.forEachIndexed { i, word ->
            val start = slots[i]
            if (start == 0L || hits[i] == true) return@forEachIndexed
            val at = firstHeard[word] ?: return@forEachIndexed
            if (at in (start - EARLY_MS)..(start + interval + grace)) {
                hits[i] = true
                haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
            } else if (now > start + interval + grace) {
                hits[i] = false
            }
        }
    }

    // The listener outlives attempts, so it reads the current attempt's words through this.
    val onHeard by rememberUpdatedState { hypotheses: List<String> ->
        val now = SystemClock.elapsedRealtime()
        transcript = hypotheses.first()
        cb.onActivity()
        for (word in words) {
            if (word !in firstHeard && hypotheses.any { SpeechMatch.matches(it, word, level) }) firstHeard[word] = now
        }
        judge(now)
    }
    val listener = remember {
        SpeechListener(
            context = context,
            onHeard = { onHeard(it) },
            onLevel = { micLevel = it },
            onFatal = { cb.onUnavailable() },
        )
    }

    // Steer the recognizer toward this attempt's words; helps a lot with sleepy or accented speech.
    LaunchedEffect(words) { listener.bias = words }

    fun quiet(on: Boolean) {
        if (on) {
            cb.setDuck(0f)
            // The recognizer chimes on the media stream every time it restarts; silence it.
            if (!beatOnMedia && !audio.isStreamMute(AudioManager.STREAM_MUSIC)) {
                audio.adjustStreamVolume(AudioManager.STREAM_MUSIC, AudioManager.ADJUST_MUTE, 0)
                mutedMusic = true
            }
        } else {
            cb.setDuck(1f)
            if (mutedMusic) {
                audio.adjustStreamVolume(AudioManager.STREAM_MUSIC, AudioManager.ADJUST_UNMUTE, 0)
                mutedMusic = false
            }
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            listener.stop()
            quiet(false)
        }
    }

    BeatEffect(bpm = 60_000f / beatMs, playing = phase == BeatPhase.COUNT_IN || phase == BeatPhase.RUN, volume = 0.3f)

    LaunchedEffect(phase, attempt) {
        when (phase) {
            BeatPhase.READY -> Unit
            BeatPhase.COUNT_IN -> {
                quiet(true)
                listener.start()
                for (c in 3 downTo 1) {
                    countIn = c
                    delay(beatMs)
                }
                countIn = 0
                delay(beatMs)
                phase = BeatPhase.RUN
            }
            BeatPhase.RUN -> {
                for (i in words.indices) {
                    index = i
                    slots[i] = SystemClock.elapsedRealtime()
                    judge(slots[i])
                    delay(interval)
                    judge(SystemClock.elapsedRealtime())
                }
                delay(grace)
                judge(SystemClock.elapsedRealtime() + interval)
                phase = BeatPhase.RESULT
            }
            BeatPhase.RESULT -> {
                listener.stop()
                quiet(false)
                val score = hits.count { it == true } / words.size.toFloat()
                if (score >= passRatio) {
                    delay(1200)
                    cb.onWin(score)
                } else {
                    failedAttempts++
                    cb.onFail("Only ${hits.count { it == true }} of ${words.size}. ${Sarcasm.pick(Sarcasm.fails)}")
                    delay(2200)
                    index = -1
                    attempt++
                    phase = BeatPhase.READY
                }
            }
        }
    }

    Column(
        Modifier
            .fillMaxSize()
            .padding(horizontal = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        when (phase) {
            BeatPhase.READY -> RhythmIntro(
                icon = { IconBubble(Icons.Rounded.Mic, Brand.Orange, size = 88.dp) },
                title = "Say each word ON the beat",
                lines = listOf(
                    "The alarm goes quiet so I can hear you.",
                    "Say every word out loud the moment it lights up.",
                    "Hit ${(passRatio * 100).toInt()}% of ${words.size} words to pass. Accents are fine; mumbling isn't.",
                ),
                startLabel = if (attempt == 0) "I'M READY" else "AGAIN",
                onStart = {
                    cb.onActivity()
                    phase = BeatPhase.COUNT_IN
                },
                onSwap = if (failedAttempts >= 3) cb.onUnavailable else null,
            )
            BeatPhase.COUNT_IN -> CountIn(countIn)
            BeatPhase.RUN, BeatPhase.RESULT -> {
                val word = words.getOrNull(index).orEmpty()
                val pop = remember { Animatable(1f) }
                LaunchedEffect(index) {
                    pop.snapTo(1.25f)
                    pop.animateTo(1f, spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium))
                }
                RingProgress(progress = micLevel, stroke = 6.dp, brush = Brand.Electric, modifier = Modifier.size(280.dp)) {
                    Text(
                        word.uppercase(),
                        style = ClockStyle.copy(fontSize = if (word.length > 9) 34.sp else 46.sp),
                        color = if (hits.getOrNull(index) == true) Brand.Green else Brand.Text,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.scale(pop.value),
                    )
                }
                Spacer(Modifier.height(10.dp))
                if (phase == BeatPhase.RUN) {
                    Text("Your turn! Say the word on beat", style = MaterialTheme.typography.titleMedium, color = Brand.BlueSoft)
                }
                Spacer(Modifier.height(16.dp))
                WordChips(words, hits, index)
                Spacer(Modifier.height(16.dp))
                Text(
                    if (phase == BeatPhase.RESULT) "${hits.count { it == true }} / ${words.size}" else "heard: ${transcript.ifBlank { "…" }}",
                    style = if (phase == BeatPhase.RESULT) MaterialTheme.typography.headlineLarge else MaterialTheme.typography.bodyMedium,
                    color = Brand.TextSecondary,
                    textAlign = TextAlign.Center,
                    maxLines = 2,
                )
            }
        }
    }
}

/** Live readings needed to judge dance moves; plain fields because they change at sensor rate. */
private class DanceSensors {
    val gravity = floatArrayOf(0f, 9.8f, 0f)
    var shakes = 0
    var lastShakeAt = 0L
    var spin = 0f
    var lastGyroNs = 0L
    var armed = false

    fun holds(move: DanceMove, strict: Boolean): Boolean {
        val tilt = if (strict) 7f else 6f
        return when (move) {
            DanceMove.TILT_LEFT -> gravity[0] > tilt
            DanceMove.TILT_RIGHT -> gravity[0] < -tilt
            DanceMove.FACE_UP -> gravity[2] > if (strict) 8.8f else 8f
            DanceMove.SHAKE -> shakes >= 3
            DanceMove.SPIN -> spin >= if (strict) 5.5f else 4.7f
        }
    }

    fun resetFor(move: DanceMove, strict: Boolean) {
        shakes = 0
        spin = 0f
        // A pose already held when the move appears doesn't count; you have to strike it on the beat.
        armed = !holds(move, strict)
    }
}

@Composable
fun DanceChallenge(spec: ChallengeSpec, cb: ChallengeCallbacks) {
    val manager = rememberSensorManager()
    val haptics = LocalHapticFeedback.current
    val hasGyro = remember { manager.has(Sensor.TYPE_GYROSCOPE) }
    val hasGravity = remember { manager.has(Sensor.TYPE_GRAVITY) }
    val window = remember { Tuning.danceWindowMs(spec) }
    val passRatio = remember { Tuning.dancePassRatio(spec) }
    var attempt by remember { mutableIntStateOf(0) }
    var failedAttempts by remember { mutableIntStateOf(0) }
    val moves = remember(attempt) {
        val random = Random(spec.seed + attempt)
        val pool = DanceMove.entries.filter { hasGyro || it != DanceMove.SPIN }
        buildList {
            repeat(Tuning.danceMoves(spec)) {
                var next = pool.random(random)
                while (next == lastOrNull()) next = pool.random(random)
                add(next)
            }
        }
    }
    val hits: SnapshotStateList<Boolean?> = remember(attempt) { mutableStateListOf<Boolean?>().apply { repeat(moves.size) { add(null) } } }
    val sensors = remember { DanceSensors() }
    var phase by remember { mutableStateOf(BeatPhase.READY) }
    var index by remember { mutableIntStateOf(-1) }
    var countIn by remember { mutableIntStateOf(3) }
    var windowProgress by remember { mutableFloatStateOf(0f) }

    fun evaluate() {
        if (phase != BeatPhase.RUN) return
        val move = moves.getOrNull(index) ?: return
        if (hits[index] != null) return
        val holds = sensors.holds(move, spec.strict)
        if (!sensors.armed) {
            if (!holds) sensors.armed = true
            return
        }
        if (holds) {
            hits[index] = true
            haptics.performHapticFeedback(HapticFeedbackType.LongPress)
        }
    }

    SensorEffect(if (hasGravity) Sensor.TYPE_GRAVITY else Sensor.TYPE_ACCELEROMETER, onFailure = cb.onUnavailable) { e ->
        val alpha = if (hasGravity) 0f else 0.85f
        for (i in 0..2) sensors.gravity[i] = alpha * sensors.gravity[i] + (1 - alpha) * e.values[i]
        evaluate()
    }
    SensorEffect(Sensor.TYPE_LINEAR_ACCELERATION) { e ->
        val now = SystemClock.elapsedRealtime()
        val g = sqrt(e.values[0] * e.values[0] + e.values[1] * e.values[1] + e.values[2] * e.values[2])
        if (g > 2f) cb.onActivity()
        if (g > 12f && now - sensors.lastShakeAt > 150) {
            sensors.lastShakeAt = now
            sensors.shakes++
            evaluate()
        }
    }
    SensorEffect(Sensor.TYPE_GYROSCOPE) { e ->
        if (sensors.lastGyroNs != 0L) {
            val dt = (e.timestamp - sensors.lastGyroNs) / 1e9f
            sensors.spin += (abs(e.values[0]) + abs(e.values[1]) + abs(e.values[2])) * dt
        }
        sensors.lastGyroNs = e.timestamp
        evaluate()
    }

    DisposableEffect(Unit) { onDispose { cb.setDuck(1f) } }
    BeatEffect(bpm = 240_000f / window, playing = phase == BeatPhase.COUNT_IN || phase == BeatPhase.RUN, volume = 0.6f)

    LaunchedEffect(phase, attempt) {
        when (phase) {
            BeatPhase.READY -> Unit
            BeatPhase.COUNT_IN -> {
                cb.setDuck(0.15f)
                for (c in 3 downTo 1) {
                    countIn = c
                    delay(window / 4)
                }
                countIn = 0
                delay(window / 4)
                phase = BeatPhase.RUN
            }
            BeatPhase.RUN -> {
                for (i in moves.indices) {
                    sensors.resetFor(moves[i], spec.strict)
                    index = i
                    val start = SystemClock.elapsedRealtime()
                    while (true) {
                        val elapsed = SystemClock.elapsedRealtime() - start
                        windowProgress = 1f - elapsed / window.toFloat()
                        if (elapsed >= window) break
                        delay(16)
                    }
                    if (hits[i] == null) hits[i] = false
                }
                phase = BeatPhase.RESULT
            }
            BeatPhase.RESULT -> {
                cb.setDuck(1f)
                val score = hits.count { it == true } / moves.size.toFloat()
                if (score >= passRatio) {
                    delay(1200)
                    cb.onWin(score)
                } else {
                    failedAttempts++
                    cb.onFail("${hits.count { it == true }} of ${moves.size} moves. ${Sarcasm.pick(Sarcasm.fails)}")
                    delay(2200)
                    index = -1
                    attempt++
                    phase = BeatPhase.READY
                }
            }
        }
    }

    Column(
        Modifier
            .fillMaxSize()
            .padding(horizontal = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        when (phase) {
            BeatPhase.READY -> RhythmIntro(
                icon = { IconBubble(Icons.Rounded.Nightlife, Brand.ElectricBlue, size = 88.dp) },
                title = "Dance. On the beat.",
                lines = listOf(
                    "Hold the phone in front of you, screen facing you.",
                    "Strike each move before the ring runs out.",
                    "Nail ${(passRatio * 100).toInt()}% of ${moves.size} moves to pass.",
                ),
                startLabel = if (attempt == 0) "LET'S DANCE" else "AGAIN",
                onStart = {
                    cb.onActivity()
                    phase = BeatPhase.COUNT_IN
                },
                onSwap = if (failedAttempts >= 3) cb.onUnavailable else null,
            )
            BeatPhase.COUNT_IN -> CountIn(countIn)
            BeatPhase.RUN, BeatPhase.RESULT -> {
                val move = moves.getOrNull(index)
                val hit = hits.getOrNull(index) == true
                val glow by animateColorAsState(if (hit) Brand.Green else Brand.Orange, label = "danceGlow")
                RingProgress(
                    progress = if (phase == BeatPhase.RUN) windowProgress else 0f,
                    stroke = 14.dp,
                    brush = SolidColor(glow),
                    modifier = Modifier.size(280.dp),
                ) {
                    AnimatedContent(
                        targetState = move,
                        transitionSpec = { (scaleIn(initialScale = 0.6f) + fadeIn()) togetherWith fadeOut() },
                        label = "move",
                    ) { m ->
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(m?.emoji.orEmpty(), fontSize = 72.sp)
                            Text(m?.label.orEmpty(), style = MaterialTheme.typography.headlineMedium, color = if (hit) Brand.Green else Brand.Text)
                        }
                    }
                }
                Spacer(Modifier.height(16.dp))
                Text(
                    if (phase == BeatPhase.RESULT) "${hits.count { it == true }} / ${moves.size}" else move?.hint.orEmpty(),
                    style = if (phase == BeatPhase.RESULT) MaterialTheme.typography.headlineLarge else MaterialTheme.typography.bodyLarge,
                    color = Brand.TextSecondary,
                    textAlign = TextAlign.Center,
                )
                Spacer(Modifier.height(20.dp))
                HitDots(hits, index)
            }
        }
    }
}

@Composable
private fun RhythmIntro(
    icon: @Composable () -> Unit,
    title: String,
    lines: List<String>,
    startLabel: String,
    onStart: () -> Unit,
    onSwap: (() -> Unit)?,
) {
    icon()
    Spacer(Modifier.height(20.dp))
    Text(title, style = MaterialTheme.typography.headlineMedium, color = Brand.Text, textAlign = TextAlign.Center)
    Spacer(Modifier.height(16.dp))
    lines.forEach {
        Text("• $it", style = MaterialTheme.typography.bodyLarge, color = Brand.TextSecondary, modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp))
    }
    Spacer(Modifier.height(28.dp))
    HellButton(startLabel, onStart, Modifier.fillMaxWidth(), kind = ButtonKind.White)
    if (onSwap != null) {
        Spacer(Modifier.height(10.dp))
        HellButton("Swap for another challenge", onSwap, Modifier.fillMaxWidth(), kind = ButtonKind.Ghost)
    }
}

@Composable
private fun CountIn(value: Int) {
    AnimatedContent(
        targetState = value,
        transitionSpec = { (scaleIn(initialScale = 1.8f) + fadeIn()) togetherWith fadeOut() },
        label = "countIn",
    ) { v ->
        Text(if (v == 0) "GO!" else "$v", style = ClockStyle.copy(fontSize = 120.sp), color = if (v == 0) Brand.Orange else Brand.Text)
    }
}

/** Every word of the run as karaoke chips: the current one lit, hits green, misses red. */
@Composable
private fun WordChips(words: List<String>, hits: List<Boolean?>, current: Int) {
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        words.forEachIndexed { i, word ->
            val hit = hits.getOrNull(i)
            val background = when {
                hit == true -> Brand.Green
                hit == false -> Brand.HellRed.copy(alpha = 0.7f)
                i == current -> Color.White
                else -> Brand.ElectricBlue.copy(alpha = 0.35f)
            }
            Text(
                word,
                style = MaterialTheme.typography.titleMedium,
                color = if (i == current && hit == null) Brand.ElectricBlue else Color.White,
                modifier = Modifier
                    .clip(MaterialTheme.shapes.medium)
                    .background(background)
                    .padding(horizontal = 14.dp, vertical = 10.dp),
            )
        }
    }
}

@Composable
private fun HitDots(hits: List<Boolean?>, current: Int) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
        hits.forEachIndexed { i, hit ->
            val color = when (hit) {
                true -> Brand.Green
                false -> Brand.HellRed
                null -> Brand.SurfaceHighest
            }
            Box(
                Modifier
                    .size(if (i == current) 16.dp else 12.dp)
                    .clip(CircleShape)
                    .background(color)
                    .then(if (i == current) Modifier.border(2.dp, Color.White, CircleShape) else Modifier),
            )
        }
    }
}
