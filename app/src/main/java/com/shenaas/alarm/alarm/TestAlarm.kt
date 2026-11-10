package com.shenaas.alarm.alarm

import android.content.Context
import com.shenaas.alarm.data.AlarmRepository
import com.shenaas.alarm.model.Alarm
import com.shenaas.alarm.model.ChallengeType
import com.shenaas.alarm.model.Difficulty
import java.time.LocalTime

/**
 * A real alarm a few seconds from now, so users can check it actually takes over the lock screen.
 * Hidden from the alarm list, kept out of stats, and deleted once it's over. The debug Test Lab can
 * make it [silent] and pick its challenges.
 */
object TestAlarm {
    const val ID = 999_999
    private const val DELAY_MS = 10_000L

    fun schedule(
        context: Context,
        challenges: Set<ChallengeType> = setOf(ChallengeType.MATH, ChallengeType.SHAKE),
        difficulty: Difficulty = Difficulty.EASY,
        count: Int = 1,
        silent: Boolean = false,
        headphonesOnly: Boolean = false,
    ) {
        val now = LocalTime.now()
        val alarm = Alarm(
            id = ID,
            hour = now.hour,
            minute = now.minute,
            enabled = false,
            label = when {
                silent -> "Silent test alarm"
                headphonesOnly -> "Headphone test alarm"
                else -> "Test alarm"
            },
            challenges = challenges,
            difficulty = difficulty,
            challengeCount = count.coerceIn(1, Alarm.MAX_CHALLENGES),
            maxSnoozes = 1,
            snoozeMinutes = 1,
            wakeUpCheck = false,
            extremeMode = !silent && !headphonesOnly,
            silent = silent,
            headphonesOnly = headphonesOnly,
            // A pending snooze is how a one-off alarm rings at a precise second.
            snoozedUntil = System.currentTimeMillis() + DELAY_MS,
        )
        AlarmRepository.upsert(alarm)
        AlarmScheduler.schedule(context, alarm)
    }

    fun isTest(alarm: Alarm) = alarm.id == ID
}
