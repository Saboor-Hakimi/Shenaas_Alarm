# Challenges: how they work and how they're tuned

All knobs live in `challenge/Tuning.kt`, indexed **Easy / Normal / Hard / Nightmare**. Counts are
multiplied by the challenge's adaptive **intensity**; time windows are divided by it.

## Planning a ring (`ChallengePlanner`)

1. The pool is the alarm's selected challenges that can run right now (permissions granted, speech
   engine present, camera present, accelerometer present). If none can, Math.
2. Count = the alarm's *challenges to beat* + snoozes used (not for wake-check re-rings), capped at 10.
3. Easy/Normal/Hard draw from a shuffled bag, so every challenge is used before any repeats.
   Nightmare is fully random, never the same challenge twice in a row.
4. Each `ChallengeSpec` gets the current adaptive intensity and a random seed.

## Adaptive difficulty (`StatsRepository.recordChallenge`)

Each win reports a score from 0 to 1:

| Result | Intensity change |
|---|---|
| Win, score ≥ 0.75 | +0.08 |
| Win, 0.35 ≤ score < 0.75 | +0.02 |
| Win, score < 0.35 | −0.06 |
| Failed attempt | −0.03 |

Clamped to 0.8×–1.6×. Scores: counters and Find the Object compare expected time with actual time;
Math and Typing do the same with a penalty for mistakes; Word on Beat and Dance use the hit ratio.

## Idle watchdog

No sign of life (keystroke, sensor movement, recognized speech) for 40 s (Nightmare 25 s, Steps 60 s,
Find the Object 120 s) sends the sleeper back to the full-volume ringing screen. The current challenge
restarts; completed ones stay done.

## Word on Beat (`RhythmChallenges.kt`, `SpeechListener.kt`, `challenge/SpeechMatch.kt`)

- Alarm ducked to silence and the media stream muted (to hide the recognizer's restart chime). A
  drum loop plays at 2 beats per word. All words show as karaoke chips: the current one lit, hits
  green, misses red. The flow is ready → 3-2-1-Go! → "Your turn!".
- `SpeechRecognizer` prefers the on-device recognizer (Pixels ship one), falls back to the default
  one if English isn't installed, restarts after every utterance, and returns partial results and up
  to 5 alternatives. On Android 13+ it is **biased toward the attempt's words**
  (`EXTRA_BIASING_STRINGS`), which helps a lot with sleepy or accented speech.
- **Accent-tolerant matching** (`SpeechMatch`): each heard word is compared by spelling and by a
  sound-alike key that merges sounds people blur when half asleep or speaking a second language
  (b/p, d/t, g/k, v/f/w, s/z, th→t, sh/ch/j, r/l, any vowel, silent h, doubled letters). Plurals,
  "-ing" endings and words split in two ("pan cake") also count. Covered by `SpeechMatchTest`.
- The first time each target word is recognized is timestamped. A word **hits** if that falls inside
  its slot, from 200 ms early up to the slot length plus a grace period for late, slow speech.
- The word list avoids tongue-twisters, "th" and near-homophones. Nightmare uses two-word phrases,
  which must both be said, in order.
- A fail shows the score and offers a retry with new words. After three fails a swap is offered.

| | Easy | Normal | Hard | Nightmare |
|---|---|---|---|---|
| Words | 4 | 6 | 8 | 10 |
| Slot per word | 2200 ms | 1800 ms | 1500 ms | 1250 ms |
| Late-speech grace | 1400 ms | 1200 ms | 1000 ms | 850 ms |
| Similarity needed | 0.60 | 0.68 | 0.78 | 0.86 |
| Needed to pass | 50% | 65% | 75% | 85% |

Similarity is the better of spelling similarity and sound-key similarity (× 0.95), so Nightmare still
accepts any accent but not a different word ("copy" passes for "coffee" on Easy, not on Nightmare).

## Find the Object (`FindObjectChallenge.kt`, `ObjectSpotter.kt`)

Two on-device recognizers vote:
- **MediaPipe object detector, EfficientDet-Lite2** (float16, COCO's 80 classes, with boxes). It's a
  larger, more accurate model than Lite0, and it alternates each frame between the viewfinder
  crop and the full frame because they catch different things.
- **Google ML Kit image labeling** (bundled, 400+ labels) on the viewfinder crop. It recognizes whole
  scenes and objects the detector lacks: "Sink", "Shoe", "Kitchen", "Bathroom".

Pipeline: CameraX analysis at 1280×960, keeping only the latest frame, rotated upright. The square the
user sees in the viewfinder is cropped out at full resolution, so the object fills more of the
model's input. `ObjectMatcher` turns each frame into one evidence score, the best of:
- a detector box with a target label that fills a minimum share of the viewfinder (+0.1 when an
  image label shows the right room, e.g. "Kitchen" for a toaster)
- an image label that names the target (−0.1, since there's no box)
- on Easy/Normal only, and only in the right room, a known look-alike (a vessel sink detected as
  "bowl") × 0.85

A **hold meter** fills while evidence is above the threshold and drains 1.5× slower when it isn't,
so one flickering frame no longer throws away a solid lock. Hard and up reject a match that sits
inside a detected TV, laptop or phone screen.

UI, following Alarmy: a slot-machine shuffle that lands on the target, a square viewfinder with corner
brackets that turn green as evidence builds, a dimmed surround, a green fill as the meter rises,
round flashlight and reroll buttons, and a swap button 60 s after rerolls run out. Debug builds show a
live readout of what both recognizers see.

| | Easy | Normal | Hard | Nightmare |
|---|---|---|---|---|
| Evidence needed | 0.40 | 0.45 | 0.50 | 0.55 |
| Hold time | 700 ms | 1000 ms | 1300 ms | 1600 ms |
| Min box size (of viewfinder) | 2% | 3% | 5% | 7% |
| Look-alikes in context | yes | yes | no | no |
| Screen check | no | no | yes | yes |
| Rerolls | 3 | 2 | 1 | 1 |

Targets come from the item set chosen in Settings, each split into near (0), mid (1) and far-from-bed
(2) tiers. Easy draws from tiers 0–1, Normal from 1–2, Hard and Nightmare from tier 2 only.
- **Afghan home** (default): piyala or tea glass, pardah, kitab · toshak, kawch, dastarkhan, chai
  thermos, fruit, chaplak or shoes, TV, wall clock, gul · naan, deg, sink, refrigerator, toothbrush, toilet.
- **General**: cup, bottle, chair, book, laptop · shoes, couch, TV, plant, bowl, clock, dining table ·
  sink, refrigerator, toothbrush, toilet, microwave, oven, toaster.

## Typing (`BrainChallenges.kt`)

- The target is plain, unselectable text, colored per character as you type.
- The input uses a password-type keyboard (no suggestions, autocorrect or swipe typing) and a
  disabled text toolbar. Any change that inserts more than one character at once (paste, autofill,
  clipboard chip) is rejected; Nightmare also clears the field.
- Time limit per phrase = 6 s + length × ms per char. Running out gives a new phrase and counts as a fail.

| | Easy | Normal | Hard | Nightmare |
|---|---|---|---|---|
| Phrases | 1 | 2 | 3 | 3 |
| ms per character | 1100 | 800 | 600 | 480 |
| Case/punctuation exact | no | no | yes | yes |
| Content | short lowercase | sentences | punctuation | punctuation or random `xK7p-…` |

## Math

Custom keypad, no system keyboard. A wrong answer shakes the problem and gives a new one. In
Nightmare it also resets progress to zero.

| | Easy | Normal | Hard | Nightmare |
|---|---|---|---|---|
| Problems | 2 | 3 | 4 | 5 |
| Form | `a + b` | `a + b + c` or `a × b` | `a × b + c` | `(a × b) + c − d` |

## Shake / Steps / Jumps (`MotionChallenges.kt`, `ChallengeKit.kt`)

- **Shake**: linear-acceleration magnitude above the threshold, at least 220 ms apart (raw
  accelerometer with gravity high-pass if there's no linear sensor). In Nightmare, 3 s without
  shaking drains one shake per second.
- **Steps**: `TYPE_STEP_DETECTOR` when available and activity recognition is granted; otherwise
  peak detection on smoothed acceleration (above 11.2, re-armed below 9.6 m/s², at least 280 ms apart).
- **Jumps**: a jump is ≥ 70 ms of near weightlessness (|a| < 4.5 m/s²) followed within 1.2 s by a
  landing spike above 15 m/s². Tilting or shaking doesn't produce that sequence. Sampled at 100 Hz,
  which stays under the 200 Hz limit that would need `HIGH_SAMPLING_RATE_SENSORS`.

| | Easy | Normal | Hard | Nightmare |
|---|---|---|---|---|
| Shakes | 20 | 35 | 50 | 80 |
| Shake threshold | 11 m/s² | 13 m/s² | 14.5 m/s² | 16 m/s² |
| Steps | 15 | 30 | 50 | 80 |
| Jumps | 4 | 8 | 12 | 20 |

## Dance

Moves come on a 4-beat bar with no immediate repeats: tilt left/right (gravity x beyond ±6, strict
±7 m/s²), palm up (gravity z above 8, strict 8.8), shake it (3 hard shakes), spin (gyroscope rotation
adds up past 4.7 rad, strict 5.5; only if the phone has a gyroscope). A pose already held when a move
appears must be released and struck again.

| | Easy | Normal | Hard | Nightmare |
|---|---|---|---|---|
| Moves | 4 | 6 | 8 | 10 |
| Window per move | 2800 ms | 2400 ms | 2000 ms | 1700 ms |
| Needed to pass | 50% | 65% | 75% | 85% |
