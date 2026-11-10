package com.shenaas.alarm.challenge

import com.shenaas.alarm.model.ChallengeSpec
import com.shenaas.alarm.model.ChallengeType
import kotlin.math.roundToInt

/**
 * Per-difficulty knobs, indexed Easy / Normal / Hard / Nightmare. Counts scale up with the
 * adaptive intensity, time windows shrink with it.
 */
object Tuning {
    private fun ChallengeSpec.count(vararg base: Int): Int = (base[difficulty.level] * intensity).roundToInt().coerceAtLeast(1)
    private fun ChallengeSpec.window(vararg base: Int): Long = (base[difficulty.level] / intensity).roundToInt().toLong()
    private fun ChallengeSpec.pick(vararg values: Float): Float = values[difficulty.level]

    fun shakeTarget(s: ChallengeSpec) = s.count(20, 35, 50, 80)
    /** Linear acceleration in m/s² that counts as one shake. */
    fun shakeThreshold(s: ChallengeSpec) = s.pick(11f, 13f, 14.5f, 16f)

    fun stepTarget(s: ChallengeSpec) = s.count(15, 30, 50, 80)
    fun jumpTarget(s: ChallengeSpec) = s.count(4, 8, 12, 20)

    fun mathProblems(s: ChallengeSpec) = s.count(2, 3, 4, 5).coerceAtMost(8)

    fun typingRounds(s: ChallengeSpec) = s.count(1, 2, 3, 3).coerceAtMost(5)
    fun typingMsPerChar(s: ChallengeSpec) = s.window(1100, 800, 600, 480)
    fun typingCaseSensitive(s: ChallengeSpec) = s.difficulty.level >= 2

    fun beatWords(s: ChallengeSpec) = s.count(4, 6, 8, 10).coerceAtMost(14)
    fun beatIntervalMs(s: ChallengeSpec) = s.window(2200, 1800, 1500, 1250).coerceAtLeast(900)
    fun beatPassRatio(s: ChallengeSpec) = s.pick(0.5f, 0.65f, 0.75f, 0.85f)

    fun danceMoves(s: ChallengeSpec) = s.count(4, 6, 8, 10).coerceAtMost(14)
    fun danceWindowMs(s: ChallengeSpec) = s.window(2800, 2400, 2000, 1700).coerceAtLeast(1200)
    fun dancePassRatio(s: ChallengeSpec) = s.pick(0.5f, 0.65f, 0.75f, 0.85f)

    /** Per-frame evidence (see ObjectMatcher) needed for the hold meter to fill. */
    fun objectConfidence(s: ChallengeSpec) = s.pick(0.40f, 0.45f, 0.50f, 0.55f)
    /** Total time with good evidence needed; the meter drains, rather than resets, on a bad frame. */
    fun objectHoldMs(s: ChallengeSpec) = longArrayOf(700, 1000, 1300, 1600)[s.difficulty.level]
    /** Minimum share of the viewfinder the object's box must fill, so a speck in the distance doesn't count. */
    fun objectMinArea(s: ChallengeSpec) = s.pick(0.02f, 0.03f, 0.05f, 0.07f)
    fun objectRerolls(s: ChallengeSpec) = intArrayOf(3, 2, 1, 1)[s.difficulty.level]
    /** Easy and Normal accept common look-alikes (a vessel sink seen as a bowl) when the room fits. */
    fun objectLookAlikes(s: ChallengeSpec) = s.difficulty.level <= 1
    /** Hard and up reject pictures of the object shown on a screen. */
    fun objectRejectScreens(s: ChallengeSpec) = s.difficulty.level >= 2

    /** Doing nothing for this long throws the sleeper back to the full-volume ringing screen. */
    fun idleTimeoutMs(s: ChallengeSpec): Long = when (s.type) {
        ChallengeType.FIND_OBJECT -> 120_000
        ChallengeType.STEPS -> 60_000
        else -> if (s.strict) 25_000 else 40_000
    }
}
