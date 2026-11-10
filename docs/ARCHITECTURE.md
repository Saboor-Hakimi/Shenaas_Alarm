# Architecture

Single-module Android app, Kotlin + Jetpack Compose (Material 3), no DI framework, no database.
Package root: `com.shenaas.alarm`.

| Item | Value |
|---|---|
| minSdk / targetSdk / compileSdk | 31 / 37 / 37 |
| Toolchain | AGP 9.4.1 (built-in Kotlin), Kotlin 2.2.10, Gradle 9.6.0, JDK from Android Studio |
| UI | Compose BOM 2026.09.00 (Compose 1.12, Material3 1.4), material-icons-extended |
| Camera / vision | CameraX 1.6.2, MediaPipe Tasks Vision 1.2.2 + EfficientDet-Lite2 (float16, `assets/`), ML Kit image labeling 17.0.9 (bundled) |
| Speech | Android `SpeechRecognizer` (on-device preferred), biased toward expected words on Android 13+ |
| ABIs | `arm64-v8a` only |

## Packages

```
model/      Alarm, Difficulty, ChallengeType, BuiltinSound, ChallengeSpec, RingState (+ JSON)
data/       Storage (device-protected), AlarmRepository, Prefs, StatsRepository
alarm/      AlarmScheduler, AlarmReceiver, BootReceiver, AlarmService, AlarmPlayer, SoundSynth,
            RingSession, RingFlow, Notifications, TestAlarm, LabAudio (debug headphone routing)
challenge/  ChallengePlanner, Tuning, SpeechMatch, ChallengeContent (words, phrases, objects, math, sarcasm)
ui/theme    Shenaas palette, Plus Jakarta Sans typography, shapes
ui/components  Shared composables (buttons, hold button, cards, chips, progress, banner)
ui/home, ui/edit, ui/settings, ui/about, ui/stats, ui/lab (debug only)
ui/ring     RingActivity / PracticeActivity, RingController, RingScreens, challenges/*
util/       TimeText, Permissions
```

## Storage

Everything is in **device-protected storage** (`createDeviceProtectedStorageContext()`), so the app
can read its alarms and resume a ring after a reboot *before* the user unlocks the phone (direct boot).
All alarm components are `directBootAware`.

- `files/alarms.json`: the alarm list (`AlarmRepository`, written with `AtomicFile`, exposed as a `StateFlow`)
- `files/stats.json`: wake records (last 500), per-challenge stats and adaptive intensity
- `shared_prefs/shenaas_alarm.xml`: settings and `active_ring`, the persisted state of the ring in progress

## Alarm lifecycle

```
Save in editor
  └─ AlarmRepository.upsert ─ AlarmScheduler.schedule
        setAlarmClock(trigger, broadcast PendingIntent ACTION_FIRE)       ← exact, Doze-exempt, status-bar icon

ACTION_FIRE ─ AlarmReceiver.fire
  ├─ one-time → enabled=false;  repeating → re-armed for the next day immediately
  ├─ more than 60 min late (phone was off) → "missed" notification + stats, stop
  └─ AlarmService.start(reason = SCHEDULED | SNOOZE | RESUMED)

AlarmService (foreground, type systemExempted)
  ├─ startForeground(full-screen ringing notification)
  ├─ ChallengePlanner.plan(...)  → RingSession.begin(state)   (state persisted to Prefs.activeRing)
  ├─ AlarmPlayer.start           (alarm stream volume, sound loop, vibration, crescendo, volume lock)
  ├─ partial wake lock, "give up" timer
  ├─ nag loop: every 20 s, if the ring screen isn't visible → re-post notification + relaunch screen
  └─ collects RingSession: DONE or cleared → stop everything

RingActivity (full-screen intent or direct launch; showWhenLocked, turnScreenOn, back blocked,
              volume keys swallowed in extreme mode)
  └─ LiveRingController ↔ RingSession
        RINGING ─ "I'm up" → CHALLENGE ─ challenge callbacks → … → RingFlow.complete → DONE
           └─ hold to snooze → AlarmService.snooze → snoozedUntil, snoozesUsed+1, reschedule, notify, clear
```

**Snooze** reuses the alarm's single fire PendingIntent: `AlarmScheduler.nextTrigger` returns
`snoozedUntil` when one is pending, otherwise the next scheduled occurrence. `snoozesUsed` is added to
the next plan's challenge count (the "snooze tax") and reset when the alarm is beaten.

**Wake-up check** (`RingFlow.complete`): `setExactAndAllowWhileIdle` posts an "Are you awake?"
notification after N minutes and arms `ACTION_WAKE_FAIL` 100 s later with `setAlarmClock`. Tapping the
notification (`ACTION_WAKE_OK`) cancels it; otherwise `AlarmService.start(WAKE_CHECK_FAILED)` rings again.

**One alarm at a time**: if a second alarm fires mid-ring, the current ring continues; the second
alarm's next occurrence is still scheduled.

## Ring state and controllers

`RingState` (immutable) holds the alarm snapshot, reason, plan (`List<ChallengeSpec>`), completed count,
fails, phase, an `attempt` counter (bumped to remount the current challenge) and a sarcasm message.
`RingSession` is the process-wide `StateFlow` shared by the service and the screen, plus a `duck` flow
(challenges set the alarm loudness; Word on Beat sets 0 so the microphone can hear).

`RingController` is the UI's only entry point:
- `LiveRingController` drives `RingSession`, records stats and adaptive levels, calls the service.
- `PracticeRingController` keeps a local state, makes no sound and records nothing (editor and Test Lab).

`ChallengeHost` gives every challenge a `ChallengeCallbacks` object (`onActivity`, `onWin(score)`,
`onFail(message)`, `onUnavailable`, `setDuck`) and runs the idle watchdog.

## Resilience

| Threat | Handling |
|---|---|
| Reboot / app update / clock or time-zone change | `BootReceiver` (incl. `LOCKED_BOOT_COMPLETED`, `MY_PACKAGE_REPLACED`) reschedules everything. A ring that started under 3 h ago is resumed 3 s later. |
| Process killed mid-ring | `START_STICKY`; the service restores the ring from `Prefs.activeRing`. |
| A challenge crashes the app | Uncaught-exception hook (`RingFlow.recoverFromCrash`) persists the ring with that challenge swapped out before the process dies. |
| Sensor refuses to register | `SensorEffect` catches it and the challenge is swapped. |
| Challenge can't run (no permission, engine, camera) | `ChallengePlanner` excludes it at plan time; at run time `onUnavailable` swaps it via `fallbackFor` (voice → typing, camera → steps, body → math). |
| Foreground service start blocked | A full-screen fallback notification opens the ring screen, which starts the service from the foreground. |
| Ringtone unreadable (direct boot, deleted file) | `AlarmPlayer` falls back to the synthesized siren. |
| Notification swiped away | Delete intent re-posts it. |
| User leaves the ring screen | Nag loop re-posts the heads-up and relaunches the screen. |

## Sound

`SoundSynth` renders all built-in sounds and the challenge drum loop as 16-bit mono PCM at 44.1 kHz
and plays them as looping static `AudioTrack`s with `USAGE_ALARM`. There are no audio assets, every
sound works in direct boot, and alarms play on the alarm stream (allowed through Do Not Disturb by default).

## Notifications

| Channel | Importance | Use |
|---|---|---|
| `ringing` | High, silent (the player makes the noise), bypass DND | Foreground ringing notification with full-screen intent |
| `snoozed` | Low | "Snoozed until 7:05 · +1 challenge" (ongoing) |
| `wake_check` | High, sound | "Are you awake?" |
| `missed` | Default | Missed / gave-up alarms |

## Permissions

| Permission | Purpose |
|---|---|
| `USE_EXACT_ALARM` (+ `SCHEDULE_EXACT_ALARM` maxSdk 32) | `setAlarmClock` / exact wake-check timers |
| `POST_NOTIFICATIONS`, `USE_FULL_SCREEN_INTENT` | Show the alarm and take over the lock screen |
| `FOREGROUND_SERVICE`, `FOREGROUND_SERVICE_SYSTEM_EXEMPTED` | Alarm service (`systemExempted` is the documented type for alarm apps with exact-alarm access) |
| `WAKE_LOCK`, `VIBRATE`, `MODIFY_AUDIO_SETTINGS`, `RECEIVE_BOOT_COMPLETED` | Ringing and restoring |
| `CAMERA`, `RECORD_AUDIO`, `ACTIVITY_RECOGNITION` | Find the Object, Word on Beat, Steps (requested only when those challenges are enabled) |

`<queries>` declares `android.speech.RecognitionService` so `SpeechRecognizer` is visible under package
visibility rules.

## Design system

Taken from shenaas.com: primary orange `#F34500` (hover `#F26A35`), electric blue `#1F51FF`, night
`#0D1014`, plus hell red `#FF1F3D` for the aggressive accents. Dark only. Plus Jakarta Sans (bundled, OFL).
Corner radii 10/16/24/32 dp, pill buttons. All tokens are in `ui/theme/Theme.kt` (`Brand`).

## Build configuration notes

- Release builds use R8 (`isMinifyEnabled`, `isShrinkResources`). MediaPipe ships no consumer rules,
  so `app/proguard-rules.pro` keeps `com.google.mediapipe.**` and `com.google.protobuf.**` (ML Kit ships its own rules).
- The `.tflite` model is stored uncompressed (`androidResources.noCompress`) so MediaPipe can memory-map it.
- Release is signed with the debug key for side-loading; configure a real upload key before publishing.
- `BuildConfig.DEBUG` gates the Test Lab.
- Unit tests: `./gradlew :app:testDebugUnitTest` (`SpeechMatchTest`).
- **Test Lab headphone routing**: Android always plays alarm-usage audio on the speaker as well, even
  with headphones connected. In headphone mode, lab alarms (`Alarm.headphonesOnly`) and practice beats
  play as media pinned to the headphone device with `setPreferredDevice`, and stay silent if no
  headphones are connected. Real alarms are unaffected.
