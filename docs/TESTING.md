# Testing

## Ways to test without waiting for a real alarm

| Tool | Where | Sound | Stats |
|---|---|---|---|
| **Test Lab: Practice now** | Home → blue flask icon (debug builds only) | none (rhythm beats optional) | not recorded |
| **Test Lab: Silent alarm in 10 seconds** | Test Lab | none, no vibration | not recorded |
| **Try these challenges** | Alarm editor | none | not recorded |
| **Ring a test alarm in 10 seconds** | Settings | full volume | not recorded |

### Test Lab (debug builds)

Available in debug builds (`./gradlew :app:assembleDebug`). In release builds the entry point and route
are disabled by `BuildConfig.DEBUG`, so users never see it.

- **Challenges / Difficulty**: choose which challenges and level to try. *Tuning at this level*
  shows the exact parameters each challenge will use, including your current adaptive level.
- **Audio**: *Silent*, or *Headphones*. In headphone mode, lab runs play their sound only in connected
  wired/USB/Bluetooth headphones (pick which one; *Play a test sound* checks it). With nothing
  connected they stay silent; they never fall back to the speaker. The media volume applies.
- **Practice now**: runs each selected challenge once in the real challenge UI, with no alarm. The
  rhythm beats follow the audio mode.
- **Lab alarm in 10 seconds**: schedules a real alarm through `AlarmManager` that goes through the
  whole flow (foreground service, notification, full-screen takeover over the lock screen, snooze,
  idle watchdog, challenges, success screen). Silent mode: no sound or vibration. Headphone mode:
  the alarm sound, including gradual volume, plays only in the headphones. Lock the phone first.
- **Find the Object readout** (debug builds): under the viewfinder, live evidence score plus the top
  detector and image-labeler results, for checking recognition in your own rooms.
- **Diagnostics**: speech recognition (and on-device speech), microphone, camera, each motion sensor,
  notification, full-screen and exact-alarm access, next system alarm, current ring. Tap *Object
  detector* to load the model and time it.
- **Live sensors**: gravity, acceleration, linear-acceleration and gyroscope peaks, plus shake,
  jump and step counters using the challenges' own detectors at Normal thresholds. Use it to check
  thresholds on a given phone.

## Automated tests

`./gradlew :app:testDebugUnitTest` runs `SpeechMatchTest`: accented and sleepy pronunciations match
("vindow", "kofi", "locket", "pan cake"), different words don't, and Nightmare is stricter than Easy.

## Manual checklist

Alarm delivery
- [ ] Alarm set for 1–2 minutes ahead rings on time with the screen off and locked
- [ ] Ring screen appears over the lock screen; notification shows "Face the challenges"
- [ ] Back does nothing; leaving the app brings the ring screen back within ~20 s
- [ ] Swiping the notification away re-posts it
- [ ] Extreme mode: volume buttons do nothing; the volume can't be lowered
- [ ] Hold to snooze: alarm stops, "Snoozed until…" notification, rings again with +1 challenge
- [ ] Out of snoozes: snooze button replaced by text
- [ ] Beating all challenges: success screen; alarm stops; one-time alarm turns itself off
- [ ] Wake-up check: notification after the delay; ignoring it for 100 s re-rings the alarm
- [ ] Reboot during a ring: alarm resumes after boot (before unlock too)
- [ ] Reboot with alarms set: they still ring
- [ ] Give up after N minutes: stops, "missed" notification, streak broken

Challenges: each at Easy and Nightmare, plus the swap path (revoke mic/camera permission, then
ring an alarm that uses Word on Beat / Find the Object).

## Verified during development

On a **Pixel 10 Pro, Android 17 (API 37, Oct 2026 patch)**:
- an alarm scheduled from the app was registered as an exact alarm clock (`USE_EXACT_ALARM`) and fired at the exact second
- the foreground service started from the alarm broadcast and requested audio focus on the alarm stream
- the ring screen launched automatically
- this test exposed a crash in the Jumps challenge (sampling rate above the 200 Hz limit), now fixed
  and guarded (sensor failures swap the challenge; challenge crashes can't cause a crash loop)
- home, settings and Test Lab screens render correctly; fresh installs show the missing-permission warning

On the **Android 17 emulator (API 37)**:
- the test alarm fired on time and the service ran as a `systemExempted` foreground service
- leaving the ring screen brought it back automatically
- editor layout, including the repeat-chip wrapping fix

Detector comparison on frames from the reference reel (a sink filmed on another phone's screen): Lite0
never found the sink in the center crop; Lite2 did (0.51), and both often saw "bowl". That led to
multi-signal matching, look-alikes in context, the crop/full alternation and the draining hold meter.

Not verified on a device during development: speech-recognition accuracy, object-detection accuracy
in real rooms, reboot resume, direct-boot ringing, and the wake-up check end-to-end. Run those from
the checklist above.

## Known limitations

- Word on Beat needs a speech recognition service (Google's on Pixels). Without one it's swapped for Typing.
- Find the Object relies on COCO classes plus ML Kit's labels. Things neither recognizes (sandali,
  jai-namaz, aftaba) aren't used as targets, and unusual-looking objects may need a reroll on
  Hard/Nightmare, where look-alikes aren't accepted.
- Motion challenges can't be performed on an emulator without its virtual sensor controls.
- UI text is English only.
