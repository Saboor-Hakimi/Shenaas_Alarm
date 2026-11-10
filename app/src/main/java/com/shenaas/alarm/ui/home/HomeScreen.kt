package com.shenaas.alarm.ui.home

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.BarChart
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Science
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.shenaas.alarm.BuildConfig
import com.shenaas.alarm.alarm.AlarmScheduler
import com.shenaas.alarm.alarm.RingSession
import com.shenaas.alarm.alarm.TestAlarm
import com.shenaas.alarm.data.AlarmRepository
import com.shenaas.alarm.model.Alarm
import com.shenaas.alarm.model.RingPhase
import com.shenaas.alarm.ui.components.Badge
import com.shenaas.alarm.ui.components.GlowBackground
import com.shenaas.alarm.ui.components.ShenaasLogo
import com.shenaas.alarm.ui.components.ShenaasSwitch
import com.shenaas.alarm.ui.components.icon
import com.shenaas.alarm.ui.theme.Brand
import com.shenaas.alarm.ui.theme.ClockStyle
import com.shenaas.alarm.util.Permissions
import com.shenaas.alarm.util.TimeText
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.time.DayOfWeek
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle as DayTextStyle
import java.time.temporal.WeekFields
import java.util.Locale

@Composable
fun HomeScreen(
    onAdd: () -> Unit,
    onEdit: (Int) -> Unit,
    onSettings: () -> Unit,
    onStats: () -> Unit,
    onLab: () -> Unit,
) {
    val context = LocalContext.current
    val allAlarms by AlarmRepository.alarms.collectAsStateWithLifecycle()
    val alarms = allAlarms.filterNot(TestAlarm::isTest)
    val ring by RingSession.state.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    var ready by remember { mutableStateOf(Permissions.alarmReady(context)) }
    var confirmDelete by remember { mutableStateOf<Alarm?>(null) }

    LaunchedEffect(Unit) {
        while (true) {
            delay(15_000)
            now = System.currentTimeMillis()
        }
    }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        now = System.currentTimeMillis()
        ready = Permissions.alarmReady(context)
    }

    val ringingId = ring?.takeIf { it.phase != RingPhase.DONE }?.alarm?.id
    fun locked(alarm: Alarm) = alarm.id == ringingId || alarm.snoozedUntil > now
    fun showLocked() = scope.launch { snackbar.showSnackbar("Locked until you beat your challenges. 😈") }

    val next = alarms.mapNotNull { a -> AlarmScheduler.nextTrigger(a, now)?.let { a to it } }.minByOrNull { it.second }

    GlowBackground {
        LazyColumn(
            contentPadding = PaddingValues(bottom = 120.dp),
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding(),
        ) {
            item {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(start = 24.dp, end = 12.dp, top = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    ShenaasLogo(height = 24.dp)
                    Spacer(Modifier.weight(1f))
                    if (BuildConfig.DEBUG) {
                        IconButton(onClick = onLab) { Icon(Icons.Rounded.Science, "Test Lab", tint = Brand.ElectricBlue) }
                    }
                    IconButton(onClick = onStats) { Icon(Icons.Rounded.BarChart, "Stats", tint = Brand.Text) }
                    IconButton(onClick = onSettings) { Icon(Icons.Rounded.Settings, "Settings", tint = Brand.Text) }
                }
            }
            item {
                Column(Modifier.padding(horizontal = 24.dp, vertical = 20.dp)) {
                    Text("ALARM", style = MaterialTheme.typography.displayMedium, color = Brand.Text)
                    Text(
                        "FROM HELL",
                        style = MaterialTheme.typography.displayMedium.merge(TextStyle(brush = Brand.Hellfire)),
                    )
                    Spacer(Modifier.height(6.dp))
                    Text(
                        "No one-tap dismiss. No mercy. Beat the challenges or keep listening.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = Brand.TextSecondary,
                    )
                }
            }
            item { NextAlarmCard(next?.first, next?.second, now) }
            if (!ready) {
                item {
                    Row(
                        Modifier
                            .padding(horizontal = 20.dp, vertical = 8.dp)
                            .fillMaxWidth()
                            .clip(MaterialTheme.shapes.large)
                            .background(Brand.HellRed.copy(alpha = 0.14f))
                            .border(1.dp, Brand.HellRed.copy(alpha = 0.6f), MaterialTheme.shapes.large)
                            .clickable(onClick = onSettings)
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(Icons.Rounded.Warning, contentDescription = null, tint = Brand.HellRed)
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text("Your alarms might not ring", style = MaterialTheme.typography.titleSmall, color = Brand.Text)
                            Text("Tap to grant notification, full-screen and exact alarm access.", style = MaterialTheme.typography.bodySmall, color = Brand.TextSecondary)
                        }
                    }
                }
            }
            if (alarms.isEmpty()) {
                item { EmptyState(onAdd) }
            } else {
                items(alarms, key = { it.id }) { alarm ->
                    AlarmCard(
                        alarm = alarm,
                        now = now,
                        locked = locked(alarm),
                        ringing = alarm.id == ringingId,
                        onClick = { if (locked(alarm)) showLocked() else onEdit(alarm.id) },
                        onLongClick = { if (locked(alarm)) showLocked() else confirmDelete = alarm },
                        onToggle = { on ->
                            if (locked(alarm)) {
                                showLocked()
                            } else {
                                val updated = alarm.copy(enabled = on, snoozedUntil = 0L, snoozesUsed = 0)
                                AlarmRepository.upsert(updated)
                                AlarmScheduler.schedule(context, updated)
                                if (on) {
                                    AlarmScheduler.nextTrigger(updated)?.let {
                                        scope.launch { snackbar.showSnackbar("Rings in ${TimeText.countdown(it - System.currentTimeMillis())}. Sleep well. Or don't.") }
                                    }
                                }
                            }
                        },
                    )
                }
            }
        }

        Box(
            Modifier
                .align(Alignment.BottomEnd)
                .navigationBarsPadding()
                .padding(24.dp)
                .size(68.dp)
                .clip(CircleShape)
                .background(Brand.Hellfire)
                .clickable(onClick = onAdd),
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.Rounded.Add, contentDescription = "New alarm", tint = Color.White, modifier = Modifier.size(32.dp))
        }
        SnackbarHost(
            snackbar,
            Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(bottom = 100.dp),
        )
    }

    confirmDelete?.let { alarm ->
        AlertDialog(
            onDismissRequest = { confirmDelete = null },
            containerColor = Brand.SurfaceHigh,
            title = { Text("Delete this alarm?") },
            text = { Text("${TimeText.clock(context, alarm.hour, alarm.minute)}${if (alarm.label.isNotBlank()) " · ${alarm.label}" else ""}. Your bed will be thrilled.") },
            confirmButton = {
                TextButton(onClick = {
                    AlarmScheduler.cancel(context, alarm.id)
                    AlarmRepository.delete(alarm.id)
                    confirmDelete = null
                }) { Text("Delete", color = Brand.HellRed) }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = null }) { Text("Keep", color = Brand.Text) } },
        )
    }
}

@Composable
private fun NextAlarmCard(alarm: Alarm?, at: Long?, now: Long) {
    val context = LocalContext.current
    Column(
        Modifier
            .padding(horizontal = 20.dp, vertical = 8.dp)
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.extraLarge)
            .background(if (alarm != null) Brand.Hellfire else SolidColor(Brand.Surface))
            .padding(24.dp),
    ) {
        if (alarm == null || at == null) {
            Text("NO ALARMS SET", style = MaterialTheme.typography.labelMedium, color = Brand.TextSecondary)
            Spacer(Modifier.height(4.dp))
            Text("Living dangerously, huh?", style = MaterialTheme.typography.headlineSmall, color = Brand.Text)
            return
        }
        val day = Instant.ofEpochMilli(at).atZone(ZoneId.systemDefault())
        Text("NEXT ALARM", style = MaterialTheme.typography.labelMedium, color = Color.White.copy(alpha = 0.8f))
        Spacer(Modifier.height(4.dp))
        Text("in ${TimeText.countdown(at - now)}", style = MaterialTheme.typography.displaySmall, color = Color.White)
        Spacer(Modifier.height(6.dp))
        Text(
            "${day.format(DateTimeFormatter.ofPattern("EEE", Locale.getDefault()))} ${TimeText.clock(context, at)} · " +
                "${(alarm.challengeCount + alarm.snoozesUsed).let { if (it == 1) "1 challenge" else "$it challenges" }} · ${alarm.difficulty.label}",
            style = MaterialTheme.typography.titleSmall,
            color = Color.White.copy(alpha = 0.9f),
        )
    }
}

@Composable
private fun EmptyState(onAdd: () -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp, vertical = 48.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text("😴", fontSize = 64.sp)
        Spacer(Modifier.height(12.dp))
        Text("Nothing to wake you up. Yet.", style = MaterialTheme.typography.titleLarge, color = Brand.Text)
        Spacer(Modifier.height(6.dp))
        Text("Tap + and pick your poison.", style = MaterialTheme.typography.bodyMedium, color = Brand.TextSecondary)
        Spacer(Modifier.height(20.dp))
        Box(
            Modifier
                .clip(CircleShape)
                .background(Brand.SurfaceHigh)
                .clickable(onClick = onAdd)
                .padding(horizontal = 20.dp, vertical = 12.dp),
        ) {
            Text("Create an alarm", style = MaterialTheme.typography.labelLarge, color = Brand.Orange)
        }
    }
}

/** Mon..Sun, starting on the locale's first day of the week. */
fun orderedDays(locale: Locale = Locale.getDefault()): List<DayOfWeek> {
    val first = WeekFields.of(locale).firstDayOfWeek
    return List(7) { first.plus(it.toLong()) }
}

fun repeatSummary(alarm: Alarm): String = when (alarm.days) {
    0 -> "Once"
    Alarm.EVERY_DAY -> "Every day"
    Alarm.WEEKDAYS -> "Weekdays"
    Alarm.WEEKEND -> "Weekends"
    else -> orderedDays().filter { alarm.repeatsOn(it) }.joinToString(" ") { it.getDisplayName(DayTextStyle.SHORT, Locale.getDefault()) }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun AlarmCard(
    alarm: Alarm,
    now: Long,
    locked: Boolean,
    ringing: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    onToggle: (Boolean) -> Unit,
) {
    val context = LocalContext.current
    val active = alarm.enabled || alarm.snoozedUntil > now
    val textColor = if (active) Brand.Text else Brand.TextMuted
    Column(
        Modifier
            .padding(horizontal = 20.dp, vertical = 6.dp)
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.large)
            .background(Brand.Surface)
            .border(1.dp, if (ringing) Brand.HellRed else Brand.Outline, MaterialTheme.shapes.large)
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
            .padding(20.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.Bottom) {
                    Text(TimeText.bigClock(context, alarm.hour, alarm.minute), style = ClockStyle.copy(fontSize = 48.sp), color = textColor)
                    TimeText.amPm(context, alarm.hour)?.let {
                        Spacer(Modifier.width(6.dp))
                        Text(it, style = MaterialTheme.typography.titleMedium, color = textColor, modifier = Modifier.padding(bottom = 9.dp))
                    }
                }
                Text(
                    listOfNotNull(alarm.label.takeIf { it.isNotBlank() }, repeatSummary(alarm)).joinToString(" · "),
                    style = MaterialTheme.typography.bodyMedium,
                    color = Brand.TextSecondary,
                )
            }
            if (locked) {
                Icon(Icons.Rounded.Lock, contentDescription = "Locked", tint = Brand.HellRed, modifier = Modifier.padding(end = 12.dp))
            }
            ShenaasSwitch(checked = active, onCheckedChange = onToggle, enabled = !locked)
        }
        Spacer(Modifier.height(14.dp))
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Badge(alarm.difficulty.label, Brand.difficulty(alarm.difficulty))
            Text("×${alarm.challengeCount + alarm.snoozesUsed}", style = MaterialTheme.typography.labelLarge, color = Brand.TextSecondary)
            Spacer(Modifier.width(2.dp))
            alarm.challenges.sortedBy { it.ordinal }.forEach {
                Icon(it.icon(), contentDescription = it.title, tint = if (active) Brand.Orange else Brand.TextMuted, modifier = Modifier.size(18.dp))
            }
        }
        when {
            ringing -> StatusLine("Ringing right now. Go beat your challenges.", Brand.HellRed)
            alarm.snoozedUntil > now -> StatusLine("Snoozed until ${TimeText.clock(context, alarm.snoozedUntil)} · +${alarm.snoozesUsed} challenge", Brand.Orange)
        }
    }
}

@Composable
private fun StatusLine(text: String, color: Color) {
    Spacer(Modifier.height(10.dp))
    Text(text, style = MaterialTheme.typography.labelLarge, color = color)
}
