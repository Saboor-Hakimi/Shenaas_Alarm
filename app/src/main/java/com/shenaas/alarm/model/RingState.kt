package com.shenaas.alarm.model

import org.json.JSONArray
import org.json.JSONObject

/** One challenge the sleeper has to beat. Tuning is derived from [difficulty] and [intensity]. */
data class ChallengeSpec(
    val type: ChallengeType,
    val difficulty: Difficulty,
    /** Adaptive multiplier from past performance. Above 1 means harder. */
    val intensity: Float,
    val seed: Long,
) {
    val strict: Boolean get() = difficulty.strict

    fun toJson(): JSONObject = JSONObject()
        .put("type", type.name)
        .put("difficulty", difficulty.name)
        .put("intensity", intensity.toDouble())
        .put("seed", seed)

    companion object {
        fun fromJson(o: JSONObject): ChallengeSpec? {
            val type = ChallengeType.parse(o.optString("type")) ?: return null
            return ChallengeSpec(
                type = type,
                difficulty = Difficulty.entries.firstOrNull { it.name == o.optString("difficulty") } ?: Difficulty.NORMAL,
                intensity = o.optDouble("intensity", 1.0).toFloat(),
                seed = o.optLong("seed"),
            )
        }
    }
}

enum class RingReason { SCHEDULED, SNOOZE, WAKE_CHECK_FAILED, RESUMED }

enum class RingPhase { RINGING, CHALLENGE, DONE }

data class DoneInfo(
    val durationMs: Long,
    val fails: Int,
    val challenges: Int,
    val snoozes: Int,
    val streakDays: Int,
    val wakeUpCheck: Boolean,
)

/** Everything about the alarm that is going off right now. */
data class RingState(
    val alarm: Alarm,
    val reason: RingReason,
    val startedAt: Long,
    val plan: List<ChallengeSpec>,
    val completed: Int = 0,
    val fails: Int = 0,
    val phase: RingPhase = RingPhase.RINGING,
    /** Bumped to restart the current challenge from scratch. */
    val attempt: Int = 0,
    val currentStartedAt: Long = 0L,
    /** Sarcastic banner text, shown once per [messageId]. */
    val message: String? = null,
    val messageId: Int = 0,
    val done: DoneInfo? = null,
) {
    val current: ChallengeSpec? get() = plan.getOrNull(completed)
    val snoozesLeft: Int get() = (alarm.maxSnoozes - alarm.snoozesUsed).coerceAtLeast(0)
    val canSnooze: Boolean get() = alarm.snoozeEnabled && snoozesLeft > 0 && phase != RingPhase.DONE

    fun withMessage(text: String?): RingState = copy(message = text, messageId = messageId + 1)

    fun toJson(): JSONObject = JSONObject()
        .put("alarm", alarm.toJson())
        .put("reason", reason.name)
        .put("startedAt", startedAt)
        .put("plan", JSONArray(plan.map { it.toJson() }))
        .put("completed", completed)
        .put("fails", fails)

    companion object {
        fun fromJson(o: JSONObject): RingState? = runCatching {
            val planJson = o.getJSONArray("plan")
            RingState(
                alarm = Alarm.fromJson(o.getJSONObject("alarm")),
                reason = RingReason.entries.firstOrNull { it.name == o.optString("reason") } ?: RingReason.SCHEDULED,
                startedAt = o.getLong("startedAt"),
                plan = (0 until planJson.length()).mapNotNull { ChallengeSpec.fromJson(planJson.getJSONObject(it)) },
                completed = o.optInt("completed"),
                fails = o.optInt("fails"),
            )
        }.getOrNull()
    }
}
