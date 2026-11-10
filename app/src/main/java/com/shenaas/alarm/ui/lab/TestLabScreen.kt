package com.shenaas.alarm.ui.lab

import android.Manifest
import android.app.AlarmManager
import android.content.Intent
import android.content.pm.PackageManager
import android.hardware.Sensor
import android.media.AudioDeviceCallback
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.speech.SpeechRecognizer
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Headphones
import androidx.compose.material.icons.rounded.Science
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.shenaas.alarm.alarm.LabAudio
import com.shenaas.alarm.alarm.RingSession
import com.shenaas.alarm.alarm.SoundSynth
import com.shenaas.alarm.alarm.TestAlarm
import com.shenaas.alarm.challenge.ChallengePlanner
import com.shenaas.alarm.challenge.Tuning
import com.shenaas.alarm.data.Prefs
import com.shenaas.alarm.data.StatsRepository
import com.shenaas.alarm.model.BuiltinSound
import com.shenaas.alarm.model.Alarm
import com.shenaas.alarm.model.ChallengeSpec
import com.shenaas.alarm.model.ChallengeType
import com.shenaas.alarm.model.Difficulty
import com.shenaas.alarm.ui.components.Badge
import com.shenaas.alarm.ui.components.ButtonKind
import com.shenaas.alarm.ui.components.GlowBackground
import com.shenaas.alarm.ui.components.HellButton
import com.shenaas.alarm.ui.components.SectionCard
import com.shenaas.alarm.ui.components.SelectChip
import com.shenaas.alarm.ui.components.icon
import com.shenaas.alarm.ui.ring.PracticeActivity
import com.shenaas.alarm.ui.ring.challenges.AccelStepDetector
import com.shenaas.alarm.ui.ring.challenges.FAST_SENSOR_PERIOD_US
import com.shenaas.alarm.ui.ring.challenges.JumpDetector
import com.shenaas.alarm.ui.ring.challenges.ObjectSpotter
import com.shenaas.alarm.ui.ring.challenges.SensorEffect
import com.shenaas.alarm.ui.ring.challenges.ShakeDetector
import com.shenaas.alarm.ui.ring.challenges.rememberSensorManager
import com.shenaas.alarm.ui.theme.Brand
import com.shenaas.alarm.util.Permissions
import com.shenaas.alarm.util.TimeText
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.abs
import kotlin.math.sqrt

/**
 * Debug-build-only workbench: practice any challenge with no alarm noise, fire a silent alarm
 * through the real ring flow, and check what this phone supports.
 */
@Composable
fun TestLabScreen(onBack: () -> Unit) {
    BackHandler(onBack = onBack)
    val context = LocalContext.current
    var selected by remember { mutableStateOf(ChallengeType.entries.toSet()) }
    var difficulty by remember { mutableStateOf(Difficulty.NORMAL) }
    var count by remember { mutableIntStateOf(Difficulty.NORMAL.defaultCount) }
    var audioMode by remember { mutableStateOf(LabAudio.mode) }
    var refresh by remember { mutableIntStateOf(0) }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { refresh++ }

    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { refresh++ }
    fun toggle(type: ChallengeType) {
        if (type in selected) {
            if (selected.size > 1) selected = selected - type
            return
        }
        selected = selected + type
        ChallengePlanner.requiredPermission(type)?.takeUnless { Permissions.granted(context, it) }?.let(permission::launch)
    }

    fun practiceAlarm() = Alarm(id = 0, hour = 7, minute = 0, challenges = selected, difficulty = difficulty, challengeCount = count)

    GlowBackground(glow = Brand.ElectricBlue) {
        Column(
            Modifier
                .fillMaxSize()
                .statusBarsPadding(),
        ) {
            Row(Modifier.padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Back", tint = Brand.Text) }
                Icon(Icons.Rounded.Science, contentDescription = null, tint = Brand.ElectricBlue)
                Spacer(Modifier.width(8.dp))
                Text("Test Lab", style = MaterialTheme.typography.titleLarge, color = Brand.Text, modifier = Modifier.weight(1f))
                Badge("Debug build", Brand.ElectricBlue, Modifier.padding(end = 12.dp))
            }
            Column(
                Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 20.dp)
                    .navigationBarsPadding(),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                Text(
                    "Nothing here makes alarm noise. Try challenges and the full ring flow quietly. Lab runs don't count toward stats.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Brand.TextSecondary,
                )

                SectionCard(title = "Challenges") {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        ChallengeType.entries.forEach { type ->
                            LabChip(type, type in selected) { toggle(type) }
                        }
                    }
                    Spacer(Modifier.height(10.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        SelectChip("All", selected.size == ChallengeType.entries.size, { selected = ChallengeType.entries.toSet() }, color = Brand.ElectricBlue)
                        SelectChip("No sensors", false, {
                            selected = setOf(ChallengeType.MATH, ChallengeType.TYPING)
                        }, color = Brand.ElectricBlue)
                    }
                }

                SectionCard(title = "Difficulty") {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Difficulty.entries.forEach { level ->
                            SelectChip(level.label, difficulty == level, {
                                difficulty = level
                                count = level.defaultCount
                            }, color = Brand.difficulty(level))
                        }
                    }
                    Spacer(Modifier.height(14.dp))
                    Text("Tuning at this level", style = MaterialTheme.typography.labelMedium, color = Brand.TextSecondary)
                    Spacer(Modifier.height(6.dp))
                    selected.sortedBy { it.ordinal }.forEach { type ->
                        val spec = ChallengeSpec(type, difficulty, StatsRepository.intensity(type), seed = 0)
                        Row(Modifier.padding(vertical = 3.dp)) {
                            Text(type.title, style = MaterialTheme.typography.bodySmall, color = Brand.Text, modifier = Modifier.width(118.dp))
                            Text(tuningSummary(spec), style = MaterialTheme.typography.bodySmall, color = Brand.TextSecondary)
                        }
                    }
                }

                LabAudioCard(audioMode, onModeChange = {
                    audioMode = it
                    LabAudio.mode = it
                })

                SectionCard(title = "Practice", subtitle = "Each selected challenge once, no alarm, no stats") {
                    HellButton(
                        "PRACTICE NOW",
                        onClick = {
                            context.startActivity(
                                Intent(context, PracticeActivity::class.java)
                                    .putExtra(PracticeActivity.EXTRA_ALARM_JSON, practiceAlarm().toJson().toString())
                                    .putExtra(PracticeActivity.EXTRA_MUTE_BEATS, audioMode == LabAudio.Mode.SILENT)
                                    .putExtra(PracticeActivity.EXTRA_HEADPHONES, audioMode == LabAudio.Mode.HEADPHONES),
                            )
                        },
                        kind = ButtonKind.Electric,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }

                SectionCard(
                    title = "Lab alarm",
                    subtitle = if (audioMode == LabAudio.Mode.SILENT) {
                        "The real ring flow: lock screen, notification, snooze, challenges. No sound, no vibration."
                    } else {
                        "The real ring flow, with the alarm sound only in your headphones."
                    },
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Challenges per ring", style = MaterialTheme.typography.titleSmall, color = Brand.Text, modifier = Modifier.weight(1f))
                        SelectChip("−", false, { count = (count - 1).coerceAtLeast(1) })
                        Text("$count", style = MaterialTheme.typography.titleLarge, color = Brand.Text, modifier = Modifier.padding(horizontal = 14.dp))
                        SelectChip("+", false, { count = (count + 1).coerceAtMost(Alarm.MAX_CHALLENGES) })
                    }
                    Spacer(Modifier.height(12.dp))
                    HellButton(
                        "LAB ALARM IN 10 SECONDS",
                        onClick = {
                            val headphones = audioMode == LabAudio.Mode.HEADPHONES
                            TestAlarm.schedule(context, selected, difficulty, count, silent = !headphones, headphonesOnly = headphones)
                            val where = if (headphones) "Sound in your headphones only." else "No sound."
                            Toast.makeText(context, "Lock the phone. Lab alarm in 10 seconds. $where", Toast.LENGTH_LONG).show()
                        },
                        kind = ButtonKind.Outline,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }

                Diagnostics(refresh)
                LiveSensors()
                Spacer(Modifier.height(24.dp))
            }
        }
    }
}

/** Silent, or sound routed to connected headphones only. Lists headphones live as they come and go. */
@Composable
private fun LabAudioCard(mode: LabAudio.Mode, onModeChange: (LabAudio.Mode) -> Unit) {
    val context = LocalContext.current
    val audio = remember { context.getSystemService(AudioManager::class.java) }
    var devices by remember { mutableStateOf(LabAudio.headphones(context)) }
    var picked by remember { mutableIntStateOf(Prefs.labAudioDeviceId) }
    DisposableEffect(Unit) {
        val callback = object : AudioDeviceCallback() {
            override fun onAudioDevicesAdded(addedDevices: Array<out AudioDeviceInfo>) {
                devices = LabAudio.headphones(context)
            }
            override fun onAudioDevicesRemoved(removedDevices: Array<out AudioDeviceInfo>) {
                devices = LabAudio.headphones(context)
            }
        }
        audio.registerAudioDeviceCallback(callback, null)
        onDispose { audio.unregisterAudioDeviceCallback(callback) }
    }
    val active = devices.firstOrNull { it.id == picked } ?: devices.firstOrNull()

    SectionCard(title = "Audio", subtitle = "Alarm sounds and rhythm beats in lab runs") {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            SelectChip("Silent", mode == LabAudio.Mode.SILENT, { onModeChange(LabAudio.Mode.SILENT) }, color = Brand.ElectricBlue)
            SelectChip("Headphones", mode == LabAudio.Mode.HEADPHONES, { onModeChange(LabAudio.Mode.HEADPHONES) }, color = Brand.ElectricBlue)
        }
        if (mode == LabAudio.Mode.HEADPHONES) {
            Spacer(Modifier.height(12.dp))
            if (devices.isEmpty()) {
                Text(
                    "No headphones connected. Plug in or pair some; until then lab runs stay silent, never on the speaker.",
                    style = MaterialTheme.typography.bodySmall,
                    color = Brand.Orange,
                )
            } else {
                devices.forEach { device ->
                    val selected = device.id == active?.id
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clip(MaterialTheme.shapes.small)
                            .clickable {
                                picked = device.id
                                Prefs.labAudioDeviceId = device.id
                            }
                            .padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(Icons.Rounded.Headphones, contentDescription = null, tint = if (selected) Brand.BlueSoft else Brand.TextMuted, modifier = Modifier.size(20.dp))
                        Spacer(Modifier.width(10.dp))
                        Text(LabAudio.describe(device), style = MaterialTheme.typography.bodyMedium, color = Brand.Text, modifier = Modifier.weight(1f))
                        if (selected) Icon(Icons.Rounded.CheckCircle, contentDescription = "Selected", tint = Brand.BlueSoft, modifier = Modifier.size(18.dp))
                    }
                }
                Spacer(Modifier.height(8.dp))
                HellButton(
                    "PLAY A TEST SOUND",
                    onClick = { active?.let { playTestSound(it) } },
                    kind = ButtonKind.Outline,
                    modifier = Modifier.fillMaxWidth(),
                )
                Text(
                    "Uses the media volume, so the volume buttons work in lab runs.",
                    style = MaterialTheme.typography.bodySmall,
                    color = Brand.TextMuted,
                    modifier = Modifier.padding(top = 6.dp),
                )
            }
        }
    }
}

/** Two seconds of the siren, only on [device]. */
private fun playTestSound(device: AudioDeviceInfo) {
    val track = SoundSynth.loopTrack(SoundSynth.render(BuiltinSound.HELLFIRE), LabAudio.MEDIA_ATTRIBUTES, device)
    track.setVolume(0.6f)
    track.play()
    Handler(Looper.getMainLooper()).postDelayed({ runCatching { track.stop(); track.release() } }, 2_000)
}

private fun tuningSummary(s: ChallengeSpec): String {
    val level = if (s.intensity != 1f) " · ${"%.1f".format(s.intensity)}×" else ""
    return when (s.type) {
        ChallengeType.WORD_BEAT -> "${Tuning.beatWords(s)} words, ${Tuning.beatIntervalMs(s)} ms, pass ${(Tuning.beatPassRatio(s) * 100).toInt()}%"
        ChallengeType.FIND_OBJECT -> "conf ${Tuning.objectConfidence(s)}, hold ${Tuning.objectHoldMs(s)} ms, ${Tuning.objectRerolls(s)} rerolls"
        ChallengeType.TYPING -> "${Tuning.typingRounds(s)} phrases, ${Tuning.typingMsPerChar(s)} ms/char"
        ChallengeType.MATH -> "${Tuning.mathProblems(s)} problems"
        ChallengeType.SHAKE -> "${Tuning.shakeTarget(s)} shakes > ${Tuning.shakeThreshold(s)} m/s²"
        ChallengeType.STEPS -> "${Tuning.stepTarget(s)} steps"
        ChallengeType.JUMP -> "${Tuning.jumpTarget(s)} jumps"
        ChallengeType.DANCE -> "${Tuning.danceMoves(s)} moves, ${Tuning.danceWindowMs(s)} ms, pass ${(Tuning.dancePassRatio(s) * 100).toInt()}%"
    } + level
}

@Composable
private fun LabChip(type: ChallengeType, selected: Boolean, onClick: () -> Unit) {
    Row(
        Modifier
            .clip(MaterialTheme.shapes.medium)
            .background(if (selected) Brand.ElectricBlue.copy(alpha = 0.18f) else Brand.SurfaceHigh)
            .border(1.dp, if (selected) Brand.ElectricBlue else Brand.Outline, MaterialTheme.shapes.medium)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(type.icon(), contentDescription = null, tint = if (selected) Brand.BlueSoft else Brand.TextMuted, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(8.dp))
        Text(type.title, style = MaterialTheme.typography.labelLarge, color = if (selected) Brand.Text else Brand.TextSecondary)
    }
}

@Composable
private fun Diagnostics(refresh: Int) {
    val context = LocalContext.current
    val sensors = rememberSensorManager()
    val scope = rememberCoroutineScope()
    val ring by RingSession.state.collectAsStateWithLifecycle()
    var detector by remember { mutableStateOf<String?>(null) }

    val rows = remember(refresh) {
        fun sensor(type: Int) = sensors.getDefaultSensor(type)?.let { "${it.name} (${it.vendor})" }
        listOf(
            Triple("Speech recognition", SpeechRecognizer.isRecognitionAvailable(context), null),
            Triple("On-device speech", SpeechRecognizer.isOnDeviceRecognitionAvailable(context), "faster, works offline"),
            Triple("Microphone permission", Permissions.granted(context, Manifest.permission.RECORD_AUDIO), null),
            Triple("Camera", context.packageManager.hasSystemFeature(PackageManager.FEATURE_CAMERA_ANY), null),
            Triple("Camera permission", Permissions.granted(context, Manifest.permission.CAMERA), null),
            Triple("Accelerometer", sensor(Sensor.TYPE_ACCELEROMETER) != null, sensor(Sensor.TYPE_ACCELEROMETER)),
            Triple("Linear acceleration", sensor(Sensor.TYPE_LINEAR_ACCELERATION) != null, null),
            Triple("Gravity", sensor(Sensor.TYPE_GRAVITY) != null, null),
            Triple("Gyroscope", sensor(Sensor.TYPE_GYROSCOPE) != null, null),
            Triple("Step detector", sensor(Sensor.TYPE_STEP_DETECTOR) != null, null),
            Triple("Activity permission", Permissions.granted(context, Manifest.permission.ACTIVITY_RECOGNITION), "accurate steps"),
            Triple("Notifications", Permissions.notifications(context), null),
            Triple("Full-screen alarms", Permissions.fullScreen(context), null),
            Triple("Exact alarms", Permissions.exactAlarms(context), null),
        )
    }
    val nextAlarm = remember(refresh) { context.getSystemService(AlarmManager::class.java).nextAlarmClock?.triggerTime }

    SectionCard(title = "Diagnostics") {
        rows.forEach { (label, ok, detail) -> DiagRow(label, if (ok) "OK" else "Missing", ok, detail) }
        DiagRow(
            "Object detector",
            detector ?: "Tap to load",
            detector?.startsWith("Loaded") == true,
            "EfficientDet-Lite2 + ML Kit labeler, on device",
            onClick = {
                detector = "Loading…"
                scope.launch {
                    val start = SystemClock.elapsedRealtime()
                    val ok = withContext(Dispatchers.Default) { ObjectSpotter(context).use { it.ensureReady() } }
                    detector = if (ok) "Loaded in ${SystemClock.elapsedRealtime() - start} ms" else "Failed to load"
                }
            },
        )
        DiagRow("Next system alarm", nextAlarm?.let { TimeText.clock(context, it) } ?: "None", nextAlarm != null, null)
        DiagRow("Ringing now", ring?.let { "${it.alarm.label.ifBlank { "Alarm" }} · ${it.phase}" } ?: "No", ring == null, null)
    }
}

@Composable
private fun DiagRow(label: String, value: String, ok: Boolean, detail: String?, onClick: (() -> Unit)? = null) {
    Row(
        Modifier
            .fillMaxWidth()
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(label, style = MaterialTheme.typography.bodyMedium, color = Brand.Text)
            if (detail != null) Text(detail, style = MaterialTheme.typography.bodySmall, color = Brand.TextMuted, maxLines = 1)
        }
        if (ok) Icon(Icons.Rounded.CheckCircle, contentDescription = null, tint = Brand.Green, modifier = Modifier.size(16.dp).padding(end = 4.dp))
        Text(value, style = MaterialTheme.typography.labelLarge, color = if (ok) Brand.Green else Brand.Orange)
    }
}

/** Raw readings plus the same detectors the challenges use, for tuning thresholds on a real phone. */
@Composable
private fun LiveSensors() {
    val raw = remember { FloatArray(6) } // gravity x/y/z, linear |a| peak, gyro |ω| peak, accel |g|
    var shown by remember { mutableStateOf(FloatArray(6)) }
    var shakes by remember { mutableIntStateOf(0) }
    var jumps by remember { mutableIntStateOf(0) }
    var steps by remember { mutableIntStateOf(0) }
    val shake = remember { ShakeDetector(threshold = 13f) }
    val jump = remember { JumpDetector() }
    val fallbackSteps = remember { AccelStepDetector() }
    val sensors = rememberSensorManager()
    val stepSensor = remember { sensors.getDefaultSensor(Sensor.TYPE_STEP_DETECTOR) != null }

    SensorEffect(Sensor.TYPE_GRAVITY) { e -> for (i in 0..2) raw[i] = e.values[i] }
    SensorEffect(Sensor.TYPE_LINEAR_ACCELERATION) { e ->
        val a = sqrt(e.values[0] * e.values[0] + e.values[1] * e.values[1] + e.values[2] * e.values[2])
        raw[3] = maxOf(raw[3], a)
        if (shake.onLinear(e.values[0], e.values[1], e.values[2], SystemClock.elapsedRealtime())) shakes++
    }
    SensorEffect(Sensor.TYPE_GYROSCOPE) { e -> raw[4] = maxOf(raw[4], abs(e.values[0]) + abs(e.values[1]) + abs(e.values[2])) }
    SensorEffect(Sensor.TYPE_ACCELEROMETER, rate = FAST_SENSOR_PERIOD_US) { e ->
        val now = SystemClock.elapsedRealtime()
        raw[5] = sqrt(e.values[0] * e.values[0] + e.values[1] * e.values[1] + e.values[2] * e.values[2])
        if (jump.onRaw(e.values[0], e.values[1], e.values[2], now)) jumps++
        if (!stepSensor && fallbackSteps.onRaw(e.values[0], e.values[1], e.values[2], now)) steps++
    }
    SensorEffect(Sensor.TYPE_STEP_DETECTOR) { steps++ }

    LaunchedEffect(Unit) {
        var lastDecay = SystemClock.elapsedRealtime()
        while (true) {
            delay(120)
            shown = raw.copyOf()
            // Peaks hold for a second so short spikes are readable.
            if (SystemClock.elapsedRealtime() - lastDecay > 1000) {
                raw[3] = 0f
                raw[4] = 0f
                lastDecay = SystemClock.elapsedRealtime()
            }
        }
    }

    SectionCard(title = "Live sensors", subtitle = "Same detectors as the challenges, Normal thresholds") {
        val mono = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace, fontSize = 13.sp)
        Text("gravity  x %6.2f  y %6.2f  z %6.2f".format(shown[0], shown[1], shown[2]), style = mono, color = Brand.Text)
        Text("accel |g| %5.2f   linear peak %5.2f".format(shown[5], shown[3]), style = mono, color = Brand.Text)
        Text("gyro peak %5.2f rad/s".format(shown[4]), style = mono, color = Brand.Text)
        Spacer(Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("shakes $shakes · jumps $jumps · steps $steps", style = MaterialTheme.typography.titleSmall, color = Brand.Text, modifier = Modifier.weight(1f))
            SelectChip("Reset", false, {
                shakes = 0
                jumps = 0
                steps = 0
            }, color = Color.Transparent)
        }
        if (!stepSensor) Text("No step detector: counting steps from the accelerometer.", style = MaterialTheme.typography.bodySmall, color = Brand.TextMuted)
    }
}
