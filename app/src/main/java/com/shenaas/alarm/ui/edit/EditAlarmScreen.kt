package com.shenaas.alarm.ui.edit

import android.Manifest
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.AudioTrack
import android.media.Ringtone
import android.media.RingtoneManager
import android.net.Uri
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.rounded.VolumeUp
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.LibraryMusic
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Remove
import androidx.compose.material.icons.rounded.Snooze
import androidx.compose.material.icons.rounded.Speed
import androidx.compose.material.icons.rounded.Stop
import androidx.compose.material.icons.rounded.Vibration
import androidx.compose.material.icons.rounded.VerifiedUser
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.IntentCompat
import com.shenaas.alarm.alarm.AlarmScheduler
import com.shenaas.alarm.alarm.SoundSynth
import com.shenaas.alarm.challenge.ChallengePlanner
import com.shenaas.alarm.data.AlarmRepository
import com.shenaas.alarm.model.Alarm
import com.shenaas.alarm.model.BuiltinSound
import com.shenaas.alarm.model.ChallengeKind
import com.shenaas.alarm.model.ChallengeType
import com.shenaas.alarm.model.Difficulty
import com.shenaas.alarm.ui.components.ButtonKind
import com.shenaas.alarm.ui.components.GlowBackground
import com.shenaas.alarm.ui.components.HellButton
import com.shenaas.alarm.ui.components.SectionCard
import com.shenaas.alarm.ui.components.SelectChip
import com.shenaas.alarm.ui.components.ToggleRow
import com.shenaas.alarm.ui.components.icon
import com.shenaas.alarm.ui.home.orderedDays
import com.shenaas.alarm.ui.ring.PracticeActivity
import com.shenaas.alarm.ui.theme.Brand
import com.shenaas.alarm.util.Permissions
import com.shenaas.alarm.util.TimeText
import kotlinx.coroutines.launch
import org.json.JSONObject
import java.time.format.TextStyle
import java.util.Locale
import kotlin.math.roundToInt

@Composable
fun EditAlarmScreen(alarmId: Int?, onDone: () -> Unit) {
    val context = LocalContext.current
    val existing = remember(alarmId) { alarmId?.let { AlarmRepository.get(it) } }
    val initialJson = remember(alarmId) { (existing ?: Alarm(id = 0, hour = 7, minute = 0)).toJson().toString() }
    var draftJson by rememberSaveable(alarmId) { mutableStateOf(initialJson) }
    val draft = remember(draftJson) { Alarm.fromJson(JSONObject(draftJson)) }
    fun update(transform: Alarm.() -> Alarm) {
        draftJson = draft.transform().toJson().toString()
    }

    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    var showSounds by remember { mutableStateOf(false) }
    var confirmDiscard by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    var pendingChallenge by remember { mutableStateOf<ChallengeType?>(null) }

    val challengePermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        val type = pendingChallenge ?: return@rememberLauncherForActivityResult
        pendingChallenge = null
        when {
            granted -> update { copy(challenges = challenges + type) }
            // Steps still works off the accelerometer without activity recognition.
            type == ChallengeType.STEPS -> update { copy(challenges = challenges + type) }
            else -> scope.launch {
                snackbar.showSnackbar("${type.title} needs ${if (type == ChallengeType.WORD_BEAT) "the microphone" else "the camera"}. Enable it in Settings.")
            }
        }
    }

    fun save() {
        val id = existing?.id ?: AlarmRepository.nextId()
        val alarm = draft.copy(id = id, enabled = true, snoozedUntil = 0L, snoozesUsed = existing?.snoozesUsed ?: 0)
        AlarmRepository.upsert(alarm)
        AlarmScheduler.schedule(context, alarm)
        AlarmScheduler.nextTrigger(alarm)?.let {
            Toast.makeText(context, "Alarm set for ${TimeText.countdown(it - System.currentTimeMillis())} from now", Toast.LENGTH_SHORT).show()
        }
        onDone()
    }

    val notificationPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { save() }
    fun requestSave() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && !Permissions.granted(context, Manifest.permission.POST_NOTIFICATIONS)) {
            notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            save()
        }
    }

    fun toggleChallenge(type: ChallengeType) {
        if (type in draft.challenges) {
            if (draft.challenges.size == 1) {
                scope.launch { snackbar.showSnackbar("Pick at least one challenge. Nice try.") }
            } else {
                update { copy(challenges = challenges - type) }
            }
            return
        }
        val permission = ChallengePlanner.requiredPermission(type)
        if (permission != null && !Permissions.granted(context, permission)) {
            pendingChallenge = type
            challengePermission.launch(permission)
        } else {
            update { copy(challenges = challenges + type) }
        }
    }

    BackHandler {
        if (draftJson != initialJson) confirmDiscard = true else onDone()
    }

    val rings = AlarmScheduler.nextTrigger(draft.copy(enabled = true, snoozedUntil = 0L))

    GlowBackground {
        Column(
            Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .imePadding(),
        ) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = { if (draftJson != initialJson) confirmDiscard = true else onDone() }) {
                    Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Back", tint = Brand.Text)
                }
                Text(
                    if (existing == null) "New alarm" else "Edit alarm",
                    style = MaterialTheme.typography.titleLarge,
                    color = Brand.Text,
                    modifier = Modifier.weight(1f),
                )
                if (existing != null) {
                    IconButton(onClick = { confirmDelete = true }) { Icon(Icons.Rounded.Delete, "Delete", tint = Brand.HellRed) }
                }
            }

            Column(
                Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 20.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                SectionCard {
                    WheelTimePicker(
                        hour = draft.hour,
                        minute = draft.minute,
                        is24h = TimeText.is24h(context),
                        onChange = { h, m -> if (h != draft.hour || m != draft.minute) update { copy(hour = h, minute = m) } },
                    )
                    Spacer(Modifier.height(10.dp))
                    Text(
                        rings?.let { "Rings in ${TimeText.countdown(it - System.currentTimeMillis())}" }.orEmpty(),
                        style = MaterialTheme.typography.titleSmall,
                        color = Brand.Orange,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }

                SectionCard(title = "Repeat") {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        orderedDays().forEach { day ->
                            val on = draft.repeatsOn(day)
                            Box(
                                Modifier
                                    .size(40.dp)
                                    .clip(CircleShape)
                                    .background(if (on) Brand.Orange else Brand.SurfaceHigh)
                                    .border(1.dp, if (on) Brand.Orange else Brand.Outline, CircleShape)
                                    .clickable { update { copy(days = days xor Alarm.dayBit(day)) } },
                                contentAlignment = Alignment.Center,
                            ) {
                                Text(
                                    day.getDisplayName(TextStyle.NARROW, Locale.getDefault()),
                                    style = MaterialTheme.typography.labelLarge,
                                    color = if (on) Color.White else Brand.TextSecondary,
                                )
                            }
                        }
                    }
                    Spacer(Modifier.height(12.dp))
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf("Once" to 0, "Weekdays" to Alarm.WEEKDAYS, "Weekends" to Alarm.WEEKEND, "Daily" to Alarm.EVERY_DAY).forEach { (name, mask) ->
                            SelectChip(name, draft.days == mask, { update { copy(days = mask) } })
                        }
                    }
                }

                SectionCard(title = "Label") {
                    OutlinedTextField(
                        value = draft.label,
                        onValueChange = { text -> update { copy(label = text.take(40)) } },
                        placeholder = { Text("Gym. Work. Flight. Destiny.") },
                        singleLine = true,
                        shape = MaterialTheme.shapes.medium,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = Brand.Orange,
                            unfocusedBorderColor = Brand.Outline,
                            cursorColor = Brand.Orange,
                            focusedContainerColor = Brand.SurfaceHigh,
                            unfocusedContainerColor = Brand.SurfaceHigh,
                            focusedTextColor = Brand.Text,
                            unfocusedTextColor = Brand.Text,
                        ),
                        modifier = Modifier.fillMaxWidth(),
                    )
                }

                SectionCard(title = "Difficulty", subtitle = "How much suffering before silence") {
                    Difficulty.entries.chunked(2).forEach { row ->
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            row.forEach { level ->
                                DifficultyCard(level, draft.difficulty == level, Modifier.weight(1f)) {
                                    update { copy(difficulty = level, challengeCount = level.defaultCount) }
                                }
                            }
                        }
                        Spacer(Modifier.height(10.dp))
                    }
                }

                SectionCard(title = "Challenges", subtitle = "Picked at random from these when the alarm rings") {
                    ChallengeType.entries.chunked(2).forEach { row ->
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            row.forEach { type ->
                                ChallengeCard(type, type in draft.challenges, Modifier.weight(1f)) { toggleChallenge(type) }
                            }
                        }
                        Spacer(Modifier.height(10.dp))
                    }
                    Spacer(Modifier.height(4.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text("Challenges to beat", style = MaterialTheme.typography.titleSmall, color = Brand.Text)
                            Text("Each snooze adds one more", style = MaterialTheme.typography.bodySmall, color = Brand.TextSecondary)
                        }
                        Stepper(draft.challengeCount, 1..Alarm.MAX_CHALLENGES) { update { copy(challengeCount = it) } }
                    }
                    Spacer(Modifier.height(14.dp))
                    HellButton(
                        "TRY THESE CHALLENGES",
                        onClick = {
                            context.startActivity(Intent(context, PracticeActivity::class.java).putExtra(PracticeActivity.EXTRA_ALARM_JSON, draftJson))
                        },
                        kind = ButtonKind.Outline,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }

                SectionCard(title = "Sound") {
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clip(MaterialTheme.shapes.medium)
                            .clickable { showSounds = true }
                            .padding(vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(Icons.Rounded.MusicNote, contentDescription = null, tint = Brand.Orange)
                        Spacer(Modifier.width(14.dp))
                        Column(Modifier.weight(1f)) {
                            Text(draft.soundTitle, style = MaterialTheme.typography.titleSmall, color = Brand.Text)
                            Text(
                                BuiltinSound.byId(draft.soundId)?.vibe ?: "Phone ringtone",
                                style = MaterialTheme.typography.bodySmall,
                                color = Brand.TextSecondary,
                            )
                        }
                        Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, contentDescription = null, tint = Brand.TextSecondary)
                    }
                    Spacer(Modifier.height(12.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.AutoMirrored.Rounded.VolumeUp, contentDescription = null, tint = Brand.TextSecondary)
                        Spacer(Modifier.width(12.dp))
                        Slider(
                            value = draft.volume / 100f,
                            onValueChange = { v -> update { copy(volume = (v * 100).roundToInt().coerceIn(10, 100)) } },
                            valueRange = 0.1f..1f,
                            colors = SliderDefaults.colors(
                                thumbColor = Brand.Orange,
                                activeTrackColor = Brand.Orange,
                                inactiveTrackColor = Brand.SurfaceHighest,
                            ),
                            modifier = Modifier.weight(1f),
                        )
                        Spacer(Modifier.width(12.dp))
                        Text("${draft.volume}%", style = MaterialTheme.typography.labelLarge, color = Brand.Text)
                    }
                    ToggleRow(
                        "Extreme mode",
                        "Volume locked while ringing. Volume buttons do nothing.",
                        draft.extremeMode,
                        { update { copy(extremeMode = it) } },
                        icon = Icons.Rounded.Bolt,
                    )
                    ToggleRow("Gradual volume", "Starts quiet, ends at full blast", draft.crescendo, { update { copy(crescendo = it) } }, icon = Icons.Rounded.Speed)
                    ToggleRow("Vibrate", null, draft.vibrate, { update { copy(vibrate = it) } }, icon = Icons.Rounded.Vibration)
                }

                SectionCard(title = "Snooze") {
                    ToggleRow(
                        "Allow snooze",
                        "Hold to snooze. Every snooze adds a challenge.",
                        draft.snoozeEnabled,
                        { update { copy(snoozeEnabled = it) } },
                        icon = Icons.Rounded.Snooze,
                    )
                    if (draft.snoozeEnabled) {
                        Spacer(Modifier.height(8.dp))
                        Text("Length", style = MaterialTheme.typography.labelMedium, color = Brand.TextSecondary)
                        Spacer(Modifier.height(8.dp))
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            listOf(3, 5, 10, 15).forEach { m -> SelectChip("$m min", draft.snoozeMinutes == m, { update { copy(snoozeMinutes = m) } }) }
                        }
                        Spacer(Modifier.height(12.dp))
                        Text("Max snoozes", style = MaterialTheme.typography.labelMedium, color = Brand.TextSecondary)
                        Spacer(Modifier.height(8.dp))
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            listOf(1, 2, 3, 5).forEach { n -> SelectChip("$n", draft.maxSnoozes == n, { update { copy(maxSnoozes = n) } }) }
                        }
                    }
                }

                SectionCard(title = "Wake-up check") {
                    ToggleRow(
                        "Are you actually awake?",
                        "A few minutes after you win, we check on you. No answer in 100 seconds and the alarm comes back.",
                        draft.wakeUpCheck,
                        { update { copy(wakeUpCheck = it) } },
                        icon = Icons.Rounded.VerifiedUser,
                    )
                }
                Spacer(Modifier.height(110.dp))
            }
        }

        Box(
            Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 20.dp, vertical = 16.dp),
        ) {
            HellButton("SAVE ALARM", onClick = ::requestSave, modifier = Modifier.fillMaxWidth(), height = 60.dp)
        }
        SnackbarHost(
            snackbar,
            Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(bottom = 90.dp),
        )
    }

    if (showSounds) {
        SoundSheet(
            selectedId = draft.soundId,
            onPick = { id, title -> update { copy(soundId = id, soundTitle = title) } },
            onDismiss = { showSounds = false },
        )
    }
    if (confirmDiscard) {
        AlertDialog(
            onDismissRequest = { confirmDiscard = false },
            containerColor = Brand.SurfaceHigh,
            title = { Text("Discard changes?") },
            confirmButton = { TextButton(onClick = onDone) { Text("Discard", color = Brand.HellRed) } },
            dismissButton = { TextButton(onClick = { confirmDiscard = false }) { Text("Keep editing", color = Brand.Text) } },
        )
    }
    if (confirmDelete && existing != null) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            containerColor = Brand.SurfaceHigh,
            title = { Text("Delete this alarm?") },
            text = { Text("Your bed will be thrilled.") },
            confirmButton = {
                TextButton(onClick = {
                    AlarmScheduler.cancel(context, existing.id)
                    AlarmRepository.delete(existing.id)
                    onDone()
                }) { Text("Delete", color = Brand.HellRed) }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Keep", color = Brand.Text) } },
        )
    }
}

@Composable
private fun DifficultyCard(level: Difficulty, selected: Boolean, modifier: Modifier, onClick: () -> Unit) {
    val color = Brand.difficulty(level)
    Column(
        modifier
            .clip(MaterialTheme.shapes.medium)
            .background(if (selected) color.copy(alpha = 0.18f) else Brand.SurfaceHigh)
            .border(if (selected) 2.dp else 1.dp, if (selected) color else Brand.Outline, MaterialTheme.shapes.medium)
            .clickable(onClick = onClick)
            .padding(14.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier
                    .size(10.dp)
                    .clip(CircleShape)
                    .background(color),
            )
            Spacer(Modifier.width(8.dp))
            Text(level.label, style = MaterialTheme.typography.titleMedium, color = Brand.Text)
        }
        Spacer(Modifier.height(4.dp))
        Text(level.tagline, style = MaterialTheme.typography.bodySmall, color = Brand.TextSecondary, minLines = 2)
    }
}

private fun ChallengeType.needs(): String = when {
    this == ChallengeType.WORD_BEAT -> "MIC"
    this == ChallengeType.FIND_OBJECT -> "CAMERA"
    kind == ChallengeKind.BODY -> "MOVE"
    else -> "BRAIN"
}

@Composable
private fun ChallengeCard(type: ChallengeType, selected: Boolean, modifier: Modifier, onClick: () -> Unit) {
    Column(
        modifier
            .clip(MaterialTheme.shapes.medium)
            .background(if (selected) Brand.Orange.copy(alpha = 0.14f) else Brand.SurfaceHigh)
            .border(if (selected) 2.dp else 1.dp, if (selected) Brand.Orange else Brand.Outline, MaterialTheme.shapes.medium)
            .clickable(onClick = onClick)
            .padding(14.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(type.icon(), contentDescription = null, tint = if (selected) Brand.Orange else Brand.TextSecondary, modifier = Modifier.size(24.dp))
            Spacer(Modifier.weight(1f))
            if (selected) {
                Icon(Icons.Rounded.CheckCircle, contentDescription = "Selected", tint = Brand.Orange, modifier = Modifier.size(20.dp))
            } else {
                Text(type.needs(), style = MaterialTheme.typography.labelSmall, color = Brand.TextMuted)
            }
        }
        Spacer(Modifier.height(10.dp))
        Text(type.title, style = MaterialTheme.typography.titleSmall, color = Brand.Text)
        Text(type.blurb, style = MaterialTheme.typography.bodySmall, color = Brand.TextSecondary, minLines = 2, maxLines = 2)
    }
}

@Composable
private fun Stepper(value: Int, range: IntRange, onChange: (Int) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        StepButton(Icons.Rounded.Remove, value > range.first) { onChange(value - 1) }
        Text(
            "$value",
            style = MaterialTheme.typography.headlineSmall,
            color = Brand.Text,
            textAlign = TextAlign.Center,
            modifier = Modifier.width(44.dp),
        )
        StepButton(Icons.Rounded.Add, value < range.last) { onChange(value + 1) }
    }
}

@Composable
private fun StepButton(icon: ImageVector, enabled: Boolean, onClick: () -> Unit) {
    Box(
        Modifier
            .size(40.dp)
            .clip(CircleShape)
            .background(if (enabled) Brand.SurfaceHighest else Brand.SurfaceHigh)
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = null, tint = if (enabled) Brand.Text else Brand.TextMuted)
    }
}

/** Plays a short preview of a sound; only one at a time. */
private class SoundPreview(private val context: Context) {
    private var track: AudioTrack? = null
    private var ringtone: Ringtone? = null
    var playingId by mutableStateOf<String?>(null)
        private set

    fun toggle(id: String) {
        val wasPlaying = playingId == id
        stop()
        if (wasPlaying) return
        val builtin = BuiltinSound.byId(id)
        if (builtin != null) {
            track = SoundSynth.loopTrack(SoundSynth.render(builtin)).apply {
                setVolume(0.5f)
                play()
            }
        } else {
            ringtone = RingtoneManager.getRingtone(context, Uri.parse(id))?.apply {
                audioAttributes = AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_ALARM).build()
                play()
            }
        }
        playingId = id
    }

    fun stop() {
        track?.runCatching { stop(); release() }
        track = null
        ringtone?.stop()
        ringtone = null
        playingId = null
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SoundSheet(selectedId: String, onPick: (id: String, title: String) -> Unit, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val preview = remember { SoundPreview(context) }
    DisposableEffect(Unit) { onDispose { preview.stop() } }
    val ringtonePicker = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        val uri = result.data?.let { IntentCompat.getParcelableExtra(it, RingtoneManager.EXTRA_RINGTONE_PICKED_URI, Uri::class.java) }
            ?: return@rememberLauncherForActivityResult
        val title = RingtoneManager.getRingtone(context, uri)?.getTitle(context) ?: "Ringtone"
        onPick(uri.toString(), title)
        onDismiss()
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = Brand.Surface,
    ) {
        Column(
            Modifier
                .padding(horizontal = 20.dp)
                .navigationBarsPadding()
                .padding(bottom = 16.dp),
        ) {
            Text("Pick your poison", style = MaterialTheme.typography.headlineSmall, color = Brand.Text)
            Spacer(Modifier.height(4.dp))
            Text("Built-in sounds are tuned to be as loud and annoying as legally possible.", style = MaterialTheme.typography.bodySmall, color = Brand.TextSecondary)
            Spacer(Modifier.height(12.dp))
            BuiltinSound.entries.forEach { sound ->
                SoundRow(
                    title = sound.title,
                    subtitle = sound.vibe,
                    selected = sound.id == selectedId,
                    playing = preview.playingId == sound.id,
                    onPreview = { preview.toggle(sound.id) },
                    onSelect = {
                        onPick(sound.id, sound.title)
                        onDismiss()
                    },
                )
            }
            Spacer(Modifier.height(8.dp))
            Row(
                Modifier
                    .fillMaxWidth()
                    .clip(MaterialTheme.shapes.medium)
                    .background(Brand.SurfaceHigh)
                    .clickable {
                        preview.stop()
                        val current = selectedId.takeIf { BuiltinSound.byId(it) == null }?.let(Uri::parse)
                        ringtonePicker.launch(
                            Intent(RingtoneManager.ACTION_RINGTONE_PICKER)
                                .putExtra(RingtoneManager.EXTRA_RINGTONE_TYPE, RingtoneManager.TYPE_ALARM)
                                .putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_SILENT, false)
                                .putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_DEFAULT, true)
                                .putExtra(RingtoneManager.EXTRA_RINGTONE_EXISTING_URI, current),
                        )
                    }
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Rounded.LibraryMusic, contentDescription = null, tint = Brand.ElectricBlue)
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f)) {
                    Text("Phone ringtones…", style = MaterialTheme.typography.titleSmall, color = Brand.Text)
                    Text("Use one of your own alarm sounds", style = MaterialTheme.typography.bodySmall, color = Brand.TextSecondary)
                }
                Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, contentDescription = null, tint = Brand.TextSecondary)
            }
        }
    }
}

@Composable
private fun SoundRow(title: String, subtitle: String, selected: Boolean, playing: Boolean, onPreview: () -> Unit, onSelect: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.medium)
            .background(if (selected) Brand.Orange.copy(alpha = 0.14f) else Color.Transparent)
            .clickable(onClick = onSelect)
            .padding(vertical = 10.dp, horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .size(40.dp)
                .clip(CircleShape)
                .background(if (playing) Brand.Orange else Brand.SurfaceHighest)
                .clickable(onClick = onPreview),
            contentAlignment = Alignment.Center,
        ) {
            Icon(if (playing) Icons.Rounded.Stop else Icons.Rounded.PlayArrow, contentDescription = "Preview", tint = Color.White)
        }
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleSmall, color = Brand.Text)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = Brand.TextSecondary)
        }
        if (selected) Icon(Icons.Rounded.CheckCircle, contentDescription = "Selected", tint = Brand.Orange)
    }
}
