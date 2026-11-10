package com.shenaas.alarm.data

import android.content.Context
import android.util.AtomicFile
import com.shenaas.alarm.model.ChallengeType
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

data class WakeRecord(
    val at: Long,
    val alarmId: Int,
    val durationMs: Long,
    val challenges: Int,
    val fails: Int,
    val snoozes: Int,
    val missed: Boolean,
) {
    fun date(zone: ZoneId = ZoneId.systemDefault()): LocalDate = Instant.ofEpochMilli(at).atZone(zone).toLocalDate()
}

data class ChallengeStat(
    val attempts: Int = 0,
    val wins: Int = 0,
    val fails: Int = 0,
    val totalWinMs: Long = 0,
    /** Adaptive difficulty multiplier, see [StatsRepository.recordChallenge]. */
    val intensity: Float = 1f,
) {
    val averageWinMs: Long get() = if (wins == 0) 0 else totalWinMs / wins
}

data class Stats(
    val records: List<WakeRecord> = emptyList(),
    val challenges: Map<ChallengeType, ChallengeStat> = emptyMap(),
    val wakeChecksPassed: Int = 0,
    val wakeChecksFailed: Int = 0,
) {
    val wakeUps: List<WakeRecord> get() = records.filterNot { it.missed }
    val totalFails: Int get() = records.sumOf { it.fails }

    /** Consecutive days, ending today or yesterday, with at least one completed wake-up. */
    fun streakDays(today: LocalDate = LocalDate.now()): Int {
        val days = wakeUps.map { it.date() }.toSet()
        var day = if (today in days) today else today.minusDays(1)
        var streak = 0
        while (day in days) {
            streak++
            day = day.minusDays(1)
        }
        return streak
    }
}

object StatsRepository {
    private const val MAX_RECORDS = 500
    const val MIN_INTENSITY = 0.8f
    const val MAX_INTENSITY = 1.6f

    private lateinit var file: AtomicFile
    private val _stats = MutableStateFlow(Stats())
    val stats: StateFlow<Stats> = _stats.asStateFlow()

    fun init(context: Context) {
        if (::file.isInitialized) return
        file = Storage.file(context, "stats.json")
        _stats.value = load()
    }

    fun intensity(type: ChallengeType): Float =
        if (Prefs.adaptiveDifficulty) _stats.value.challenges[type]?.intensity ?: 1f else 1f

    @Synchronized
    fun recordWake(record: WakeRecord) = mutate { it.copy(records = (it.records + record).takeLast(MAX_RECORDS)) }

    /**
     * Adaptive difficulty: crushing a challenge ([score] near 1) makes the next one tougher,
     * struggling ([score] near 0, or failing) eases off a little.
     */
    @Synchronized
    fun recordChallenge(type: ChallengeType, success: Boolean, durationMs: Long = 0, score: Float = 0f) = mutate { stats ->
        val old = stats.challenges[type] ?: ChallengeStat()
        val delta = when {
            !success -> -0.03f
            score >= 0.75f -> 0.08f
            score < 0.35f -> -0.06f
            else -> 0.02f
        }
        val updated = old.copy(
            attempts = old.attempts + 1,
            wins = old.wins + if (success) 1 else 0,
            fails = old.fails + if (success) 0 else 1,
            totalWinMs = old.totalWinMs + if (success) durationMs else 0,
            intensity = (old.intensity + delta).coerceIn(MIN_INTENSITY, MAX_INTENSITY),
        )
        stats.copy(challenges = stats.challenges + (type to updated))
    }

    @Synchronized
    fun recordWakeCheck(passed: Boolean) = mutate {
        if (passed) it.copy(wakeChecksPassed = it.wakeChecksPassed + 1) else it.copy(wakeChecksFailed = it.wakeChecksFailed + 1)
    }

    @Synchronized
    fun reset() = mutate { Stats() }

    private fun mutate(transform: (Stats) -> Stats) {
        val next = transform(_stats.value)
        _stats.value = next
        file.writeText(toJson(next).toString())
    }

    private fun toJson(stats: Stats): JSONObject {
        val records = JSONArray(stats.records.map {
            JSONObject()
                .put("at", it.at)
                .put("alarmId", it.alarmId)
                .put("durationMs", it.durationMs)
                .put("challenges", it.challenges)
                .put("fails", it.fails)
                .put("snoozes", it.snoozes)
                .put("missed", it.missed)
        })
        val challenges = JSONObject()
        stats.challenges.forEach { (type, s) ->
            challenges.put(
                type.name,
                JSONObject()
                    .put("attempts", s.attempts)
                    .put("wins", s.wins)
                    .put("fails", s.fails)
                    .put("totalWinMs", s.totalWinMs)
                    .put("intensity", s.intensity.toDouble()),
            )
        }
        return JSONObject()
            .put("records", records)
            .put("challenges", challenges)
            .put("wakeChecksPassed", stats.wakeChecksPassed)
            .put("wakeChecksFailed", stats.wakeChecksFailed)
    }

    private fun load(): Stats {
        val text = file.readText() ?: return Stats()
        return runCatching {
            val o = JSONObject(text)
            val recordsJson = o.optJSONArray("records") ?: JSONArray()
            val records = (0 until recordsJson.length()).map {
                val r = recordsJson.getJSONObject(it)
                WakeRecord(
                    at = r.getLong("at"),
                    alarmId = r.optInt("alarmId"),
                    durationMs = r.optLong("durationMs"),
                    challenges = r.optInt("challenges"),
                    fails = r.optInt("fails"),
                    snoozes = r.optInt("snoozes"),
                    missed = r.optBoolean("missed"),
                )
            }
            val challengesJson = o.optJSONObject("challenges") ?: JSONObject()
            val challenges = challengesJson.keys().asSequence().mapNotNull { key ->
                val type = ChallengeType.parse(key) ?: return@mapNotNull null
                val c = challengesJson.getJSONObject(key)
                type to ChallengeStat(
                    attempts = c.optInt("attempts"),
                    wins = c.optInt("wins"),
                    fails = c.optInt("fails"),
                    totalWinMs = c.optLong("totalWinMs"),
                    intensity = c.optDouble("intensity", 1.0).toFloat(),
                )
            }.toMap()
            Stats(records, challenges, o.optInt("wakeChecksPassed"), o.optInt("wakeChecksFailed"))
        }.getOrDefault(Stats())
    }
}
