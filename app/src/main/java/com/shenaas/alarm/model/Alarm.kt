package com.shenaas.alarm.model

import org.json.JSONArray
import org.json.JSONObject
import java.time.DayOfWeek

enum class Difficulty(val label: String, val tagline: String, val defaultCount: Int) {
    EASY("Easy", "One simple challenge", 1),
    NORMAL("Normal", "Two random challenges", 2),
    HARD("Hard", "Three different challenges", 3),
    NIGHTMARE("Nightmare", "Randomized chaos, strict checks", 5);

    /** 0..3, used to index per-difficulty tuning tables. */
    val level: Int get() = ordinal
    val strict: Boolean get() = this == NIGHTMARE
}

enum class ChallengeKind { VOICE, CAMERA, BRAIN, BODY }

enum class ChallengeType(val title: String, val blurb: String, val kind: ChallengeKind) {
    WORD_BEAT("Word on Beat", "Shout the words right on the beat", ChallengeKind.VOICE),
    FIND_OBJECT("Find the Object", "Hunt down a real object with your camera", ChallengeKind.CAMERA),
    TYPING("Typing", "Type it exactly. Pasting is for cowards.", ChallengeKind.BRAIN),
    MATH("Math", "Solve problems with a half-asleep brain", ChallengeKind.BRAIN),
    SHAKE("Shake", "Shake your phone like you mean it", ChallengeKind.BODY),
    STEPS("Steps", "Walk it off. Literally.", ChallengeKind.BODY),
    JUMP("Jumps", "Jump with the phone in your hand", ChallengeKind.BODY),
    DANCE("Dance", "Hit every move on the beat", ChallengeKind.BODY);

    companion object {
        fun parse(name: String): ChallengeType? = entries.firstOrNull { it.name == name }
    }
}

data class Alarm(
    val id: Int,
    val hour: Int,
    val minute: Int,
    val enabled: Boolean = true,
    val label: String = "",
    /** Bit (dayOfWeek.value - 1) is set for each repeat day. 0 means a one-time alarm. */
    val days: Int = 0,
    val challenges: Set<ChallengeType> = DEFAULT_CHALLENGES,
    val difficulty: Difficulty = Difficulty.NORMAL,
    val challengeCount: Int = Difficulty.NORMAL.defaultCount,
    /** Either a [BuiltinSound.id] or a content:// ringtone URI. */
    val soundId: String = BuiltinSound.HELLFIRE.id,
    val soundTitle: String = BuiltinSound.HELLFIRE.title,
    /** Percent of the alarm stream's maximum volume. */
    val volume: Int = 100,
    /** Locks the volume while ringing and swallows the volume keys. */
    val extremeMode: Boolean = true,
    val crescendo: Boolean = false,
    val vibrate: Boolean = true,
    val snoozeEnabled: Boolean = true,
    val snoozeMinutes: Int = 5,
    val maxSnoozes: Int = 2,
    val wakeUpCheck: Boolean = true,
    /** Wall-clock time a pending snooze fires at, or 0. */
    val snoozedUntil: Long = 0L,
    /** Snoozes used since the alarm last went off for real. Each one adds a challenge. */
    val snoozesUsed: Int = 0,
    /** Rings with no sound or vibration. Only set by the debug Test Lab. */
    val silent: Boolean = false,
    /** Sound goes only to connected headphones, never the speaker. Only set by the debug Test Lab. */
    val headphonesOnly: Boolean = false,
) {
    val isRepeating: Boolean get() = days != 0

    fun repeatsOn(day: DayOfWeek): Boolean = days and dayBit(day) != 0

    fun toJson(): JSONObject = JSONObject()
        .put("id", id)
        .put("hour", hour)
        .put("minute", minute)
        .put("enabled", enabled)
        .put("label", label)
        .put("days", days)
        .put("challenges", JSONArray(challenges.map { it.name }))
        .put("difficulty", difficulty.name)
        .put("challengeCount", challengeCount)
        .put("soundId", soundId)
        .put("soundTitle", soundTitle)
        .put("volume", volume)
        .put("extremeMode", extremeMode)
        .put("crescendo", crescendo)
        .put("vibrate", vibrate)
        .put("snoozeEnabled", snoozeEnabled)
        .put("snoozeMinutes", snoozeMinutes)
        .put("maxSnoozes", maxSnoozes)
        .put("wakeUpCheck", wakeUpCheck)
        .put("snoozedUntil", snoozedUntil)
        .put("snoozesUsed", snoozesUsed)
        .put("silent", silent)
        .put("headphonesOnly", headphonesOnly)

    companion object {
        val DEFAULT_CHALLENGES = setOf(ChallengeType.MATH, ChallengeType.SHAKE, ChallengeType.TYPING)
        const val MAX_CHALLENGES = 8

        fun dayBit(day: DayOfWeek): Int = 1 shl (day.value - 1)

        val WEEKDAYS: Int = listOf(
            DayOfWeek.MONDAY, DayOfWeek.TUESDAY, DayOfWeek.WEDNESDAY, DayOfWeek.THURSDAY, DayOfWeek.FRIDAY,
        ).fold(0) { acc, d -> acc or dayBit(d) }
        val WEEKEND: Int = dayBit(DayOfWeek.SATURDAY) or dayBit(DayOfWeek.SUNDAY)
        val EVERY_DAY: Int = WEEKDAYS or WEEKEND

        fun fromJson(o: JSONObject): Alarm {
            val challenges = o.optJSONArray("challenges")?.let { arr ->
                (0 until arr.length()).mapNotNull { ChallengeType.parse(arr.getString(it)) }.toSet()
            }.orEmpty().ifEmpty { DEFAULT_CHALLENGES }
            val difficulty = Difficulty.entries.firstOrNull { it.name == o.optString("difficulty") } ?: Difficulty.NORMAL
            return Alarm(
                id = o.getInt("id"),
                hour = o.getInt("hour"),
                minute = o.getInt("minute"),
                enabled = o.optBoolean("enabled", true),
                label = o.optString("label"),
                days = o.optInt("days"),
                challenges = challenges,
                difficulty = difficulty,
                challengeCount = o.optInt("challengeCount", difficulty.defaultCount).coerceIn(1, MAX_CHALLENGES),
                soundId = o.optString("soundId", BuiltinSound.HELLFIRE.id),
                soundTitle = o.optString("soundTitle", BuiltinSound.HELLFIRE.title),
                volume = o.optInt("volume", 100).coerceIn(10, 100),
                extremeMode = o.optBoolean("extremeMode", true),
                crescendo = o.optBoolean("crescendo", false),
                vibrate = o.optBoolean("vibrate", true),
                snoozeEnabled = o.optBoolean("snoozeEnabled", true),
                snoozeMinutes = o.optInt("snoozeMinutes", 5),
                maxSnoozes = o.optInt("maxSnoozes", 2),
                wakeUpCheck = o.optBoolean("wakeUpCheck", true),
                snoozedUntil = o.optLong("snoozedUntil"),
                snoozesUsed = o.optInt("snoozesUsed"),
                silent = o.optBoolean("silent"),
                headphonesOnly = o.optBoolean("headphonesOnly"),
            )
        }
    }
}

enum class BuiltinSound(val id: String, val title: String, val vibe: String) {
    HELLFIRE("builtin:hellfire", "Hellfire Siren", "Air-raid siren that never stops climbing"),
    KLAXON("builtin:klaxon", "Nuclear Klaxon", "Submarine dive alarm. Battle stations."),
    BEEPER("builtin:beeper", "Panic Beeper", "Piercing rapid-fire beeps"),
    AIRHORN("builtin:airhorn", "Stadium Air Horn", "Pure violence"),
    DEMON_BELL("builtin:bell", "Demon Bell", "A haunted church bell, just for you"),
    SUNRISE("builtin:sunrise", "Gentle Sunrise", "Soft chimes. For the weak.");

    companion object {
        fun byId(id: String): BuiltinSound? = entries.firstOrNull { it.id == id }
    }
}
