package com.shenaas.alarm.ui.ring

import android.os.SystemClock
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AccessAlarm
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.shenaas.alarm.challenge.Sarcasm
import com.shenaas.alarm.challenge.Tuning
import com.shenaas.alarm.model.ChallengeSpec
import com.shenaas.alarm.model.ChallengeType
import com.shenaas.alarm.model.DoneInfo
import com.shenaas.alarm.model.RingPhase
import com.shenaas.alarm.model.RingState
import com.shenaas.alarm.ui.components.Badge
import com.shenaas.alarm.ui.components.ButtonKind
import com.shenaas.alarm.ui.components.HellButton
import com.shenaas.alarm.ui.components.HoldButton
import com.shenaas.alarm.ui.components.SarcasmBanner
import com.shenaas.alarm.ui.components.SegmentedProgress
import com.shenaas.alarm.ui.components.ShenaasLogo
import com.shenaas.alarm.ui.components.StatTile
import com.shenaas.alarm.ui.components.icon
import com.shenaas.alarm.ui.ring.challenges.ChallengeCallbacks
import com.shenaas.alarm.ui.ring.challenges.DanceChallenge
import com.shenaas.alarm.ui.ring.challenges.FindObjectChallenge
import com.shenaas.alarm.ui.ring.challenges.JumpChallenge
import com.shenaas.alarm.ui.ring.challenges.MathChallenge
import com.shenaas.alarm.ui.ring.challenges.ShakeChallenge
import com.shenaas.alarm.ui.ring.challenges.StepsChallenge
import com.shenaas.alarm.ui.ring.challenges.TypingChallenge
import com.shenaas.alarm.ui.ring.challenges.WordBeatChallenge
import com.shenaas.alarm.ui.theme.Brand
import com.shenaas.alarm.ui.theme.ClockStyle
import com.shenaas.alarm.util.TimeText
import kotlinx.coroutines.delay
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.sin
import kotlin.random.Random

@Composable
fun RingRoot(controller: RingController, onClose: () -> Unit) {
    val state by controller.state.collectAsStateWithLifecycle()
    val s = state
    if (s == null) {
        Box(Modifier.fillMaxSize().background(Brand.Night), contentAlignment = Alignment.Center) {
            CircularProgressIndicator(color = Brand.Orange)
        }
        return
    }
    AnimatedContent(
        targetState = s.phase,
        transitionSpec = { (fadeIn(tween(300)) + scaleIn(initialScale = 0.96f)) togetherWith fadeOut(tween(200)) },
        label = "ringPhase",
    ) { phase ->
        when (phase) {
            RingPhase.RINGING -> RingingScreen(s, controller)
            RingPhase.CHALLENGE -> ChallengeHost(s, controller)
            RingPhase.DONE -> SuccessScreen(s.done, controller.practice) {
                controller.close()
                onClose()
            }
        }
    }
}

@Composable
private fun rememberNow(): LocalDateTime {
    var now by remember { mutableStateOf(LocalDateTime.now()) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(1000)
            now = LocalDateTime.now()
        }
    }
    return now
}

@Composable
private fun RingingScreen(state: RingState, controller: RingController) {
    val context = LocalContext.current
    val now = rememberNow()
    val headline = remember(state.startedAt) { Sarcasm.pick(Sarcasm.headlines) }
    val taunt = remember(state.startedAt, state.attempt) { Sarcasm.pick(Sarcasm.taunts) }
    val flash = rememberInfiniteTransition(label = "flash")
    val heat by flash.animateFloat(0.25f, 0.6f, infiniteRepeatable(tween(520, easing = LinearEasing), RepeatMode.Reverse), label = "heat")
    val wobble by flash.animateFloat(-14f, 14f, infiniteRepeatable(tween(90, easing = LinearEasing), RepeatMode.Reverse), label = "wobble")
    val remaining = state.plan.size - state.completed

    Box(
        Modifier
            .fillMaxSize()
            .background(Brand.Night)
            .drawBehind {
                drawRect(
                    Brush.verticalGradient(
                        0f to Brand.HellRed.copy(alpha = heat),
                        0.45f to Brand.Orange.copy(alpha = heat * 0.35f),
                        1f to Color.Transparent,
                    ),
                )
            },
    ) {
        Column(
            Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .padding(horizontal = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Spacer(Modifier.height(16.dp))
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                ShenaasLogo(height = 20.dp)
                Spacer(Modifier.weight(1f))
                Badge(state.alarm.difficulty.label, Brand.difficulty(state.alarm.difficulty))
            }
            SarcasmBanner(state.message, state.messageId, Modifier.padding(top = 16.dp))
            Spacer(Modifier.weight(1f))
            Icon(
                Icons.Rounded.AccessAlarm,
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier
                    .size(64.dp)
                    .rotate(wobble),
            )
            Spacer(Modifier.height(12.dp))
            Text(headline, style = MaterialTheme.typography.headlineLarge, color = Color.White)
            Text(
                TimeText.bigClock(context, now.hour, now.minute),
                style = ClockStyle.copy(fontSize = 104.sp),
                color = Color.White,
            )
            Text(
                now.format(DateTimeFormatter.ofPattern("EEEE, d MMMM", Locale.getDefault())),
                style = MaterialTheme.typography.titleMedium,
                color = Brand.TextSecondary,
            )
            if (state.alarm.label.isNotBlank()) {
                Spacer(Modifier.height(8.dp))
                Text(state.alarm.label, style = MaterialTheme.typography.titleLarge, color = Brand.Orange, textAlign = TextAlign.Center)
            }
            Spacer(Modifier.height(20.dp))
            Text(taunt, style = MaterialTheme.typography.bodyLarge, color = Brand.Text, textAlign = TextAlign.Center)
            Spacer(Modifier.weight(1f))

            Text(
                "$remaining ${if (remaining == 1) "CHALLENGE" else "CHALLENGES"} BETWEEN YOU AND SILENCE",
                style = MaterialTheme.typography.labelMedium,
                color = Brand.TextSecondary,
            )
            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                state.plan.drop(state.completed).take(8).forEach { spec ->
                    Box(
                        Modifier
                            .size(40.dp)
                            .clip(CircleShape)
                            .background(Color.White.copy(alpha = 0.1f)),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(spec.type.icon(), contentDescription = spec.type.title, tint = Color.White, modifier = Modifier.size(20.dp))
                    }
                }
            }
            Spacer(Modifier.height(20.dp))
            HellButton(
                "I'M UP. PROVE IT →",
                onClick = controller::startChallenges,
                kind = ButtonKind.White,
                height = 64.dp,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(12.dp))
            if (state.canSnooze) {
                HoldButton(
                    "Hold to snooze ${state.alarm.snoozeMinutes} min · ${state.snoozesLeft} left · +1 challenge",
                    onHeld = controller::snooze,
                    modifier = Modifier.fillMaxWidth(),
                )
            } else {
                Text(
                    if (state.alarm.snoozeEnabled) "Out of snoozes. You did this to yourself." else "No snoozing. Not today.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Brand.TextMuted,
                    modifier = Modifier.padding(vertical = 16.dp),
                )
            }
            Spacer(Modifier.height(16.dp))
        }
    }
}

@Composable
private fun ChallengeHost(state: RingState, controller: RingController) {
    val spec = state.current ?: return
    val challengeKey = Triple(state.completed, state.attempt, spec.type)
    val lastActivity = remember(challengeKey) { longArrayOf(SystemClock.elapsedRealtime()) }
    val callbacks = remember(challengeKey, controller) {
        ChallengeCallbacks(
            onActivity = { lastActivity[0] = SystemClock.elapsedRealtime() },
            onWin = controller::challengeWon,
            onFail = controller::challengeFailed,
            onUnavailable = controller::swapCurrent,
            setDuck = controller::setDuck,
        )
    }
    // Idle watchdog: lie still for too long and it's back to the full-volume ringing screen.
    LaunchedEffect(challengeKey) {
        val timeout = Tuning.idleTimeoutMs(spec)
        while (true) {
            delay(1000)
            if (SystemClock.elapsedRealtime() - lastActivity[0] > timeout) {
                controller.idleKickback()
                break
            }
        }
    }

    Column(
        Modifier
            .fillMaxSize()
            .background(Brand.Night)
            .statusBarsPadding()
            .navigationBarsPadding(),
    ) {
        Column(Modifier.padding(horizontal = 20.dp, vertical = 12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "CHALLENGE ${state.completed + 1} / ${state.plan.size}",
                    style = MaterialTheme.typography.labelMedium,
                    color = Brand.Orange,
                    modifier = Modifier.weight(1f),
                )
                if (controller.practice) Badge("Practice", Brand.ElectricBlue)
                Spacer(Modifier.width(8.dp))
                Badge(spec.difficulty.label, Brand.difficulty(spec.difficulty))
            }
            Spacer(Modifier.height(10.dp))
            SegmentedProgress(state.plan.size, state.completed)
            Spacer(Modifier.height(14.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(spec.type.icon(), contentDescription = null, tint = Brand.Orange, modifier = Modifier.size(28.dp))
                Spacer(Modifier.width(10.dp))
                Column {
                    Text(spec.type.title, style = MaterialTheme.typography.headlineSmall, color = Brand.Text)
                    Text(spec.type.blurb, style = MaterialTheme.typography.bodyMedium, color = Brand.TextSecondary)
                }
            }
        }
        Box(Modifier.weight(1f)) {
            key(challengeKey) {
                Challenge(spec.type, spec, callbacks)
            }
            SarcasmBanner(
                state.message,
                state.messageId,
                Modifier
                    .align(Alignment.TopCenter)
                    .padding(horizontal = 16.dp),
            )
        }
    }
}

@Composable
private fun Challenge(type: ChallengeType, spec: ChallengeSpec, cb: ChallengeCallbacks) {
    when (type) {
        ChallengeType.WORD_BEAT -> WordBeatChallenge(spec, cb)
        ChallengeType.FIND_OBJECT -> FindObjectChallenge(spec, cb)
        ChallengeType.TYPING -> TypingChallenge(spec, cb)
        ChallengeType.MATH -> MathChallenge(spec, cb)
        ChallengeType.SHAKE -> ShakeChallenge(spec, cb)
        ChallengeType.STEPS -> StepsChallenge(spec, cb)
        ChallengeType.JUMP -> JumpChallenge(spec, cb)
        ChallengeType.DANCE -> DanceChallenge(spec, cb)
    }
}

@Composable
private fun SuccessScreen(done: DoneInfo?, practice: Boolean, onClose: () -> Unit) {
    val check = remember { Animatable(0f) }
    LaunchedEffect(Unit) { check.animateTo(1f, spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessLow)) }
    Box(Modifier.fillMaxSize().background(Brand.Night)) {
        Confetti(Modifier.fillMaxSize())
        Column(
            Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Spacer(Modifier.weight(1f))
            Box(
                Modifier
                    .size(132.dp)
                    .scale(check.value)
                    .clip(CircleShape)
                    .background(Brand.Hellfire),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Rounded.Check, contentDescription = null, tint = Color.White, modifier = Modifier.size(84.dp))
            }
            Spacer(Modifier.height(28.dp))
            Text(
                if (practice) "Practice complete." else Sarcasm.SUCCESS,
                style = MaterialTheme.typography.displaySmall,
                color = Brand.Text,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(12.dp))
            Text(
                if (practice) "The real thing is a lot louder." else "Took you ${TimeText.duration(done?.durationMs ?: 0)} to get here.",
                style = MaterialTheme.typography.titleMedium,
                color = Brand.TextSecondary,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(28.dp))
            if (done != null) {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    StatTile("${done.challenges}", "challenges")
                    StatTile("${done.fails}", if (done.fails == 1) "fail" else "fails", Brand.HellRed)
                    if (!practice) StatTile("🔥 ${done.streakDays}", "day streak", Brand.ElectricBlue)
                }
            }
            if (done?.wakeUpCheck == true) {
                Spacer(Modifier.height(16.dp))
                Text(
                    "Wake-up check incoming. Go back to bed and the alarm comes back.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Brand.Orange,
                    textAlign = TextAlign.Center,
                )
            }
            Spacer(Modifier.weight(1f))
            HellButton(if (practice) "BACK TO EDITING" else "START MY DAY", onClose, Modifier.fillMaxWidth(), height = 60.dp)
        }
    }
}

private class Particle(
    val x: Float,
    val delay: Float,
    val speed: Float,
    val drift: Float,
    val size: Float,
    val spin: Float,
    val color: Color,
)

@Composable
private fun Confetti(modifier: Modifier = Modifier) {
    val colors = listOf(Brand.Orange, Brand.HellRed, Brand.ElectricBlue, Color.White, Brand.OrangeSoft)
    val particles = remember {
        val random = Random(42)
        List(140) {
            Particle(
                x = random.nextFloat(),
                delay = random.nextFloat() * 0.8f,
                speed = 0.25f + random.nextFloat() * 0.35f,
                drift = random.nextFloat() * 2 - 1,
                size = 6f + random.nextFloat() * 10f,
                spin = random.nextFloat() * 720 - 360,
                color = colors[random.nextInt(colors.size)],
            )
        }
    }
    var seconds by remember { mutableLongStateOf(0L) }
    LaunchedEffect(Unit) {
        val start = withFrameNanos { it }
        while (true) {
            withFrameNanos { seconds = it - start }
            if (seconds > 6_000_000_000L) break
        }
    }
    Canvas(modifier) {
        val t = seconds / 1e9f
        particles.forEach { p ->
            val life = t - p.delay
            if (life < 0) return@forEach
            val y = -0.05f + life * p.speed
            if (y > 1.1f) return@forEach
            val x = p.x + sin(life * 3 + p.drift * 6) * 0.03f + p.drift * life * 0.05f
            val center = Offset(x * size.width, y * size.height)
            rotate(p.spin * life, center) {
                drawRect(p.color, topLeft = center - Offset(p.size / 2, p.size / 4), size = Size(p.size, p.size / 2))
            }
        }
    }
}
