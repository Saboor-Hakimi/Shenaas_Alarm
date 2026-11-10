package com.shenaas.alarm.challenge

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.hardware.Sensor
import android.hardware.SensorManager
import android.speech.SpeechRecognizer
import androidx.core.content.ContextCompat
import com.shenaas.alarm.data.StatsRepository
import com.shenaas.alarm.model.Alarm
import com.shenaas.alarm.model.ChallengeSpec
import com.shenaas.alarm.model.ChallengeType
import com.shenaas.alarm.model.Difficulty
import com.shenaas.alarm.model.RingReason
import kotlin.random.Random

object ChallengePlanner {
    private const val MAX_PLAN = 10

    /**
     * Picks the challenges for one ring. Easy/Normal/Hard draw different challenges from the
     * sleeper's pool before repeating any; Nightmare is fully random (never the same one twice in
     * a row). Every snooze adds one more.
     */
    fun plan(context: Context, alarm: Alarm, reason: RingReason): List<ChallengeSpec> {
        val pool = alarm.challenges.filter { isAvailable(context, it) }.ifEmpty { listOf(ChallengeType.MATH) }
        val snoozeTax = if (reason == RingReason.WAKE_CHECK_FAILED) 0 else alarm.snoozesUsed
        val count = (alarm.challengeCount + snoozeTax).coerceIn(1, MAX_PLAN)
        val random = Random(System.nanoTime())
        val types = mutableListOf<ChallengeType>()
        if (alarm.difficulty == Difficulty.NIGHTMARE) {
            repeat(count) {
                var next = pool.random(random)
                while (pool.size > 1 && next == types.lastOrNull()) next = pool.random(random)
                types += next
            }
        } else {
            var bag = ArrayDeque<ChallengeType>()
            while (types.size < count) {
                if (bag.isEmpty()) {
                    bag = ArrayDeque(pool.shuffled(random))
                    if (bag.size > 1 && bag.first() == types.lastOrNull()) bag.addLast(bag.removeFirst())
                }
                types += bag.removeFirst()
            }
        }
        return types.map { spec(it, alarm.difficulty, random) }
    }

    /** Replacement when a challenge can't run on this phone right now (no permission, no engine). */
    fun fallbackFor(context: Context, failed: ChallengeSpec): ChallengeSpec {
        val type = when (failed.type) {
            ChallengeType.WORD_BEAT -> ChallengeType.TYPING
            ChallengeType.FIND_OBJECT -> if (isAvailable(context, ChallengeType.STEPS)) ChallengeType.STEPS else ChallengeType.MATH
            ChallengeType.MATH -> ChallengeType.TYPING
            else -> ChallengeType.MATH
        }
        return spec(type, failed.difficulty, Random(System.nanoTime()))
    }

    fun practicePlan(alarm: Alarm): List<ChallengeSpec> {
        val random = Random(System.nanoTime())
        return alarm.challenges.sortedBy { it.ordinal }.map { spec(it, alarm.difficulty, random) }
    }

    fun isAvailable(context: Context, type: ChallengeType): Boolean = when (type) {
        ChallengeType.WORD_BEAT ->
            granted(context, Manifest.permission.RECORD_AUDIO) && SpeechRecognizer.isRecognitionAvailable(context)
        ChallengeType.FIND_OBJECT ->
            granted(context, Manifest.permission.CAMERA) &&
                context.packageManager.hasSystemFeature(PackageManager.FEATURE_CAMERA_ANY)
        ChallengeType.SHAKE, ChallengeType.STEPS, ChallengeType.JUMP, ChallengeType.DANCE ->
            context.getSystemService(SensorManager::class.java)?.getDefaultSensor(Sensor.TYPE_ACCELEROMETER) != null
        ChallengeType.TYPING, ChallengeType.MATH -> true
    }

    /** Runtime permission [type] needs, or null. Steps works without it via the accelerometer. */
    fun requiredPermission(type: ChallengeType): String? = when (type) {
        ChallengeType.WORD_BEAT -> Manifest.permission.RECORD_AUDIO
        ChallengeType.FIND_OBJECT -> Manifest.permission.CAMERA
        ChallengeType.STEPS -> Manifest.permission.ACTIVITY_RECOGNITION
        else -> null
    }

    private fun spec(type: ChallengeType, difficulty: Difficulty, random: Random) =
        ChallengeSpec(type, difficulty, StatsRepository.intensity(type), random.nextLong())

    private fun granted(context: Context, permission: String) =
        ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED
}
