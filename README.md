# Shenaas Alarm: Alarm From Hell

An aggressive Android alarm clock by [Shenaas](https://shenaas.com). You can't dismiss it with one tap:
you have to beat physical, mental, voice and camera challenges before it goes quiet.

Built natively in Kotlin + Jetpack Compose. Targets Android 12+ (minSdk 31), tuned for Pixel 7,
Pixel 8 and Pixel 10 Pro on Android 17 (targetSdk 37).

## Features

**Alarms**
- Multiple alarms, one-time or repeating on chosen weekdays (presets: once, weekdays, weekends, daily)
- Exact delivery with `AlarmManager.setAlarmClock` (shows in the status bar, exempt from Doze)
- Full-screen ringing screen over the lock screen; works before the first unlock after a reboot
- Survives reboots, app updates, time and time-zone changes, and process death: an interrupted ring
  comes straight back ("Rebooting won't save you")
- Six synthesized built-in sounds (siren, klaxon, beeper, air horn, demon bell, gentle chimes) or any
  phone ringtone, with automatic fallback to the siren if a ringtone can't be played
- Per-alarm volume, gradual volume, vibration and **Extreme mode** (volume locked while ringing,
  volume buttons ignored)
- Hold-to-snooze with a length and limit. Every snooze adds one more challenge.
- **Wake-up check**: a few minutes after you win, a notification asks if you're awake. No answer
  in 100 seconds and the alarm rings again.
- Back is blocked, the ring screen comes back if you leave it, and the notification re-posts if swiped away
- "Give up" timeout (15/30/60 min or never), after which it counts as missed

**Challenges**
| Challenge | How it's verified |
|---|---|
| Word on Beat | Karaoke-style words on a drum beat; on-device speech recognition, biased toward the expected words, with accent-tolerant sound-alike matching for sleepy and non-native speakers. |
| Find the Object | Camera + two on-device recognizers (MediaPipe EfficientDet-Lite2 + ML Kit image labeling) with room context and a forgiving hold meter. Default items fit Afghan homes (piyala, naan, deg, dastarkhan, toshak, chaplak...), with a General set too. Hard+ rejects photos shown on a screen. |
| Typing | Type the phrase exactly within a time limit. Paste and multi-character insertions are rejected. |
| Math | Problems scale from `23 + 41` to `(27 × 14) + 312 − 58`. |
| Shake / Steps / Jumps | Accelerometer, step detector and free-fall + landing detection |
| Dance | Tilt, flip, shake and spin moves on the beat, using gravity, linear acceleration and gyroscope |

**Difficulty**: Easy (one challenge), Normal (two random), Hard (three different), Nightmare
(five randomized, strict verification, progress resets on mistakes). The number of challenges is
adjustable. **Adaptive difficulty** makes a challenge harder when you crush it and eases off when you struggle.

If a challenge can't run on the phone at ring time (permission revoked, no speech engine,
camera busy), it's swapped for another, so you can never be locked into an impossible alarm.

**Stats**: streak, average time to get up, a 7-day chart (with table view), per-challenge results and
adaptive level, wake-up check results.

## Build and run

Requirements: Android Studio (bundled JDK) and Android SDK platform 37.

```bash
export JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home"
./gradlew :app:assembleDebug        # app/build/outputs/apk/debug/app-debug.apk
./gradlew :app:assembleRelease      # minified, signed with the debug key for testing
./gradlew :app:testDebugUnitTest    # unit tests
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

Before publishing, replace the debug signing config in `app/build.gradle.kts` with a real upload key.
Only `arm64-v8a` native code is packaged (all target Pixels are 64-bit only).

## Testing tips

- **Test Lab** (debug builds, blue flask icon on the home screen): practice any challenge with no alarm,
  fire a lab alarm through the real ring flow either **silent** or with sound **only in your
  headphones**, see the exact tuning per level, and check diagnostics and live sensor readings.
- **Settings → Ring a test alarm in 10 seconds** fires a real, full-volume alarm (Easy, one challenge)
  that's hidden from the list and kept out of stats. Lock the phone first to see the lock-screen takeover.
- **Edit alarm → Try these challenges** runs the alarm's challenges silently, without stats.

See [docs/TESTING.md](docs/TESTING.md) for the full checklist and what was verified on a Pixel 10 Pro.

## Documentation

| Doc | For |
|---|---|
| [docs/USER_GUIDE.md](docs/USER_GUIDE.md) | Using the app: setup, alarms, challenges, snooze, wake-up check, stats |
| [docs/CHALLENGES.md](docs/CHALLENGES.md) | How every challenge verifies you, with the tuning tables per difficulty |
| [docs/ARCHITECTURE.md](docs/ARCHITECTURE.md) | Alarm lifecycle, storage, resilience, permissions, build notes |
| [docs/TESTING.md](docs/TESTING.md) | Test Lab, manual checklist, verification status, known limitations |

## Project layout

```
app/src/main/java/com/shenaas/alarm/
  model/        Alarm, difficulty, challenge types, ring state
  data/         JSON storage in device-protected storage (alarms, settings, stats)
  alarm/        Scheduler, receivers, foreground AlarmService, player, sound synthesis, notifications
  challenge/    Planner, per-difficulty tuning, words / phrases / objects / sarcasm
  ui/home       Alarm list
  ui/edit       Editor with wheel time picker
  ui/ring       Ring screen, challenge host, success screen, and every challenge
  ui/settings   Reliability checklist and behavior settings
  ui/stats      Stats and chart
  ui/lab        Test Lab (debug builds only)
```

## Permissions

Notifications, full-screen intents and exact alarms (`USE_EXACT_ALARM`) make the alarm ring reliably.
A `systemExempted` foreground service keeps it ringing. Camera, microphone and activity recognition are
requested only when you enable a challenge that needs them. All recognition runs on the device.

## About

Developed by [Shenaas](https://shenaas.com). Created by Saboor Hakimi
([github.com/saboor-hakimi](https://github.com/saboor-hakimi)). For similar projects and ideas, reach out to Shenaas.

## Credits

Plus Jakarta Sans (SIL Open Font License, see `app/src/main/assets/OFL-PlusJakartaSans.txt`).
EfficientDet-Lite2 model from MediaPipe (Apache 2.0). Google ML Kit image labeling.
