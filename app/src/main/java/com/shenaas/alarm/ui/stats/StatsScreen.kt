package com.shenaas.alarm.ui.stats

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
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
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.shenaas.alarm.data.StatsRepository
import com.shenaas.alarm.model.ChallengeType
import com.shenaas.alarm.ui.components.ButtonKind
import com.shenaas.alarm.ui.components.GlowBackground
import com.shenaas.alarm.ui.components.HellButton
import com.shenaas.alarm.ui.components.SectionCard
import com.shenaas.alarm.ui.components.SelectChip
import com.shenaas.alarm.ui.components.StatTile
import com.shenaas.alarm.ui.components.icon
import com.shenaas.alarm.ui.theme.Brand
import com.shenaas.alarm.ui.theme.Jakarta
import com.shenaas.alarm.util.TimeText
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.ceil

private data class DayStat(val date: LocalDate, val averageMs: Long?, val wakeUps: Int, val fails: Int, val missed: Int)

/** Rounds a minute value up to a clean axis maximum. */
private fun niceMax(minutes: Float): Int {
    val steps = intArrayOf(2, 4, 6, 10, 16, 20, 30, 40, 60, 90, 120)
    return steps.firstOrNull { it >= minutes } ?: (ceil(minutes / 60f) * 60).toInt()
}

@Composable
fun StatsScreen(onBack: () -> Unit) {
    BackHandler(onBack = onBack)
    val stats by StatsRepository.stats.collectAsStateWithLifecycle()
    var showTable by remember { mutableStateOf(false) }
    var selected by remember { mutableStateOf<Int?>(null) }
    var confirmReset by remember { mutableStateOf(false) }

    val today = LocalDate.now()
    val byDay = stats.records.groupBy { it.date() }
    val week = (6 downTo 0).map { offset ->
        val date = today.minusDays(offset.toLong())
        val records = byDay[date].orEmpty()
        val wins = records.filterNot { it.missed }
        DayStat(
            date = date,
            averageMs = wins.takeIf { it.isNotEmpty() }?.map { it.durationMs }?.average()?.toLong(),
            wakeUps = wins.size,
            fails = records.sumOf { it.fails },
            missed = records.count { it.missed },
        )
    }
    val recentWins = stats.wakeUps.takeLast(7)
    val average = recentWins.takeIf { it.isNotEmpty() }?.map { it.durationMs }?.average()?.toLong()

    GlowBackground {
        Column(
            Modifier
                .fillMaxSize()
                .statusBarsPadding(),
        ) {
            Row(Modifier.padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Back", tint = Brand.Text) }
                Text("Your mornings", style = MaterialTheme.typography.titleLarge, color = Brand.Text)
            }
            Column(
                Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 20.dp)
                    .navigationBarsPadding(),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    StatTile("🔥 ${stats.streakDays(today)}", "Day streak", Brand.Text)
                    StatTile(average?.let { TimeText.duration(it) } ?: "–", "Avg time to get up", Brand.Text)
                }
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    StatTile("${stats.wakeUps.size}", "Wake-ups", Brand.Text)
                    StatTile("${stats.totalFails}", "Times called lazy", Brand.Text)
                }

                SectionCard(title = "Time to get up", subtitle = "From the first ring to beating every challenge, last 7 days") {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        SelectChip("Chart", !showTable, { showTable = false })
                        SelectChip("Table", showTable, { showTable = true })
                    }
                    Spacer(Modifier.height(16.dp))
                    if (showTable) {
                        WeekTable(week)
                    } else {
                        WakeChart(week, selected) { selected = if (selected == it) null else it }
                        Spacer(Modifier.height(12.dp))
                        val day = selected?.let { week[it] }
                        Text(
                            when {
                                day == null -> "Tap a day for details"
                                day.averageMs == null && day.missed > 0 -> "${day.date.label()} · slept through it"
                                day.averageMs == null -> "${day.date.label()} · no alarm"
                                else -> "${day.date.label()} · ${TimeText.duration(day.averageMs)} · ${day.fails} ${if (day.fails == 1) "fail" else "fails"}"
                            },
                            style = MaterialTheme.typography.bodyMedium,
                            color = if (day == null) Brand.TextMuted else Brand.Text,
                        )
                    }
                }

                val played = ChallengeType.entries.mapNotNull { type -> stats.challenges[type]?.takeIf { it.attempts > 0 }?.let { type to it } }
                SectionCard(title = "Challenges", subtitle = "Your level climbs when you crush them, drops when you struggle") {
                    if (played.isEmpty()) {
                        Text("No challenges beaten yet. Tomorrow, then.", style = MaterialTheme.typography.bodyMedium, color = Brand.TextSecondary)
                    }
                    played.forEach { (type, stat) ->
                        Row(Modifier.padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(type.icon(), contentDescription = null, tint = Brand.Orange, modifier = Modifier.size(22.dp))
                            Spacer(Modifier.width(12.dp))
                            Column(Modifier.weight(1f)) {
                                Text(type.title, style = MaterialTheme.typography.titleSmall, color = Brand.Text)
                                Text(
                                    "${stat.wins} won · ${stat.fails} failed" + if (stat.wins > 0) " · avg ${TimeText.duration(stat.averageWinMs)}" else "",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = Brand.TextSecondary,
                                )
                            }
                            LevelMeter(stat.intensity)
                        }
                    }
                }

                SectionCard(title = "Wake-up checks") {
                    Text(
                        "${stats.wakeChecksPassed} answered · ${stats.wakeChecksFailed} slept through",
                        style = MaterialTheme.typography.titleSmall,
                        color = Brand.Text,
                    )
                }

                HellButton("Reset stats", { confirmReset = true }, Modifier.fillMaxWidth(), kind = ButtonKind.Outline)
                Spacer(Modifier.height(24.dp))
            }
        }
    }

    if (confirmReset) {
        AlertDialog(
            onDismissRequest = { confirmReset = false },
            containerColor = Brand.SurfaceHigh,
            title = { Text("Reset all stats?") },
            text = { Text("Streak, history and challenge levels go back to zero.") },
            confirmButton = {
                TextButton(onClick = {
                    StatsRepository.reset()
                    confirmReset = false
                }) { Text("Reset", color = Brand.HellRed) }
            },
            dismissButton = { TextButton(onClick = { confirmReset = false }) { Text("Cancel", color = Brand.Text) } },
        )
    }
}

private fun LocalDate.label(): String =
    if (this == LocalDate.now()) "Today" else format(DateTimeFormatter.ofPattern("EEE d MMM", Locale.getDefault()))

/**
 * Single-series column chart: thin columns with a rounded cap and a square base, hairline grid,
 * only the slowest day labeled. Tapping a day's slot selects it; the readout below shows the values.
 */
@Composable
private fun WakeChart(week: List<DayStat>, selected: Int?, onSelect: (Int) -> Unit) {
    val measurer = rememberTextMeasurer()
    val maxMinutes = niceMax((week.mapNotNull { it.averageMs }.maxOrNull() ?: 0L) / 60_000f)
    val slowest = week.indices.filter { week[it].averageMs != null }.maxByOrNull { week[it].averageMs ?: 0 }
    val tickStyle = TextStyle(fontFamily = Jakarta, fontSize = 11.sp, color = Brand.TextMuted, fontFeatureSettings = "tnum")
    val labelStyle = TextStyle(fontFamily = Jakarta, fontSize = 12.sp, color = Brand.TextSecondary)
    val valueStyle = TextStyle(fontFamily = Jakarta, fontSize = 12.sp, color = Brand.Text)
    val description = week.joinToString { d -> "${d.date.label()}: ${d.averageMs?.let { TimeText.duration(it) } ?: "none"}" }

    Canvas(
        Modifier
            .fillMaxWidth()
            .height(220.dp)
            .semantics { contentDescription = "Time to get up per day. $description" }
            .pointerInput(week.size) {
                detectTapGestures { offset ->
                    val axis = 36.dp.toPx()
                    val slot = (size.width - axis) / week.size
                    val index = ((offset.x - axis) / slot).toInt()
                    if (index in week.indices) onSelect(index)
                }
            },
    ) {
        val axisWidth = 36.dp.toPx()
        val xBand = 24.dp.toPx()
        val top = 18.dp.toPx()
        val plotHeight = size.height - xBand - top
        val baseline = top + plotHeight
        val slot = (size.width - axisWidth) / week.size
        val barWidth = minOf(24.dp.toPx(), slot * 0.5f)
        val cap = CornerRadius(4.dp.toPx())

        listOf(0, maxMinutes / 2, maxMinutes).forEach { tick ->
            val y = baseline - plotHeight * tick / maxMinutes
            drawLine(Brand.SurfaceHighest, Offset(axisWidth, y), Offset(size.width, y), strokeWidth = 1f)
            val text = measurer.measure("${tick}m", tickStyle)
            drawText(text, topLeft = Offset(axisWidth - text.size.width - 8.dp.toPx(), y - text.size.height / 2))
        }

        week.forEachIndexed { i, day ->
            val centerX = axisWidth + slot * i + slot / 2
            val name = measurer.measure(
                if (day.date == LocalDate.now()) "Today" else day.date.format(DateTimeFormatter.ofPattern("EEE", Locale.getDefault())),
                labelStyle,
            )
            drawText(name, topLeft = Offset(centerX - name.size.width / 2, baseline + 6.dp.toPx()))
            val ms = day.averageMs ?: return@forEachIndexed
            val height = (plotHeight * (ms / 60_000f) / maxMinutes).coerceIn(4.dp.toPx(), plotHeight)
            val rect = Rect(centerX - barWidth / 2, baseline - height, centerX + barWidth / 2, baseline)
            val path = Path().apply { addRoundRect(RoundRect(rect, topLeft = cap, topRight = cap, bottomRight = CornerRadius.Zero, bottomLeft = CornerRadius.Zero)) }
            drawPath(path, if (selected == i) Brand.OrangeSoft else Brand.Orange)
            if (i == slowest || i == selected) {
                val value = measurer.measure(TimeText.duration(ms), valueStyle)
                drawText(value, topLeft = Offset(centerX - value.size.width / 2, rect.top - value.size.height - 2.dp.toPx()))
            }
        }
    }
}

@Composable
private fun WeekTable(week: List<DayStat>) {
    Column {
        TableRow("Day", "Time", "Fails", "Result", header = true)
        week.reversed().forEach { d ->
            TableRow(
                d.date.label(),
                d.averageMs?.let { TimeText.duration(it) } ?: "–",
                if (d.wakeUps + d.missed == 0) "–" else "${d.fails}",
                when {
                    d.wakeUps > 0 && d.missed > 0 -> "Won ${d.wakeUps}, missed ${d.missed}"
                    d.wakeUps > 0 -> "Won"
                    d.missed > 0 -> "Missed"
                    else -> "No alarm"
                },
            )
        }
    }
}

@Composable
private fun TableRow(day: String, time: String, fails: String, result: String, header: Boolean = false) {
    val style = if (header) MaterialTheme.typography.labelMedium else MaterialTheme.typography.bodyMedium.copy(fontFeatureSettings = "tnum")
    val color = if (header) Brand.TextMuted else Brand.Text
    Row(Modifier.padding(vertical = 6.dp)) {
        Text(day, style = style, color = color, modifier = Modifier.weight(1.3f))
        Text(time, style = style, color = color, modifier = Modifier.weight(1f))
        Text(fails, style = style, color = color, modifier = Modifier.weight(0.7f))
        Text(result, style = style, color = if (header) color else Brand.TextSecondary, modifier = Modifier.weight(1.4f))
    }
}

/** Adaptive level as a small meter: the fill shows how far above the 0.8× floor the level sits. */
@Composable
private fun LevelMeter(intensity: Float) {
    val fraction = ((intensity - StatsRepository.MIN_INTENSITY) / (StatsRepository.MAX_INTENSITY - StatsRepository.MIN_INTENSITY)).coerceIn(0f, 1f)
    Column(horizontalAlignment = Alignment.End) {
        Text("Level ${"%.1f".format(intensity)}×", style = MaterialTheme.typography.labelMedium, color = Brand.Text)
        Spacer(Modifier.height(4.dp))
        Box(
            Modifier
                .width(72.dp)
                .height(6.dp)
                .clip(CircleShape)
                .background(Brand.Orange.copy(alpha = 0.22f)),
        ) {
            Box(
                Modifier
                    .fillMaxHeight()
                    .fillMaxWidth(fraction)
                    .background(Brand.Orange),
            )
        }
    }
}
