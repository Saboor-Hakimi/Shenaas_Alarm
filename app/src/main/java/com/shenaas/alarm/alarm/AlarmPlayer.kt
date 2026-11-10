package com.shenaas.alarm.alarm

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioDeviceInfo
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.media.AudioTrack
import android.media.MediaPlayer
import android.net.Uri
import android.os.Build
import android.os.SystemClock
import android.os.VibrationAttributes
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.util.Log
import com.shenaas.alarm.model.Alarm
import com.shenaas.alarm.model.BuiltinSound
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

/**
 * Plays the alarm: sound on the alarm stream, vibration, optional crescendo, and in extreme mode
 * keeps forcing the alarm volume back up if anything turns it down.
 */
class AlarmPlayer(private val context: Context) {
    private val audio = context.getSystemService(AudioManager::class.java)
    private val vibrator: Vibrator = context.getSystemService(VibratorManager::class.java).defaultVibrator

    private var media: MediaPlayer? = null
    private var track: AudioTrack? = null
    private var focusRequest: AudioFocusRequest? = null
    private var loop: Job? = null

    private var attributes: AudioAttributes = ALARM_ATTRIBUTES
    private var route: AudioDeviceInfo? = null
    private var savedVolume = -1
    private var targetVolume = -1
    private var gain = 1f
    private var duck = 1f
    private var wantsVibration = false
    private var vibrating = false

    fun start(alarm: Alarm, scope: CoroutineScope) {
        if (alarm.silent) return
        if (alarm.headphonesOnly) {
            // Test Lab: headphones or nothing. Never fall back to the speaker.
            route = LabAudio.device(context) ?: return
            attributes = LabAudio.MEDIA_ATTRIBUTES
        }
        requestFocus()
        if (route == null) {
            val max = audio.getStreamMaxVolume(AudioManager.STREAM_ALARM)
            targetVolume = (max * alarm.volume / 100f).roundToInt().coerceIn(1, max)
            savedVolume = audio.getStreamVolume(AudioManager.STREAM_ALARM)
            setAlarmVolume(targetVolume)
        }

        startSound(alarm.soundId)
        gain = if (alarm.crescendo) CRESCENDO_FLOOR else 1f
        applyVolume()
        wantsVibration = alarm.vibrate
        updateVibration()

        loop = scope.launch {
            val startedAt = SystemClock.elapsedRealtime()
            while (isActive) {
                if (alarm.crescendo && gain < 1f) {
                    val progress = (SystemClock.elapsedRealtime() - startedAt) / CRESCENDO_MS.toFloat()
                    gain = (CRESCENDO_FLOOR + (1 - CRESCENDO_FLOOR) * progress).coerceAtMost(1f)
                    applyVolume()
                }
                if (alarm.extremeMode && route == null && audio.getStreamVolume(AudioManager.STREAM_ALARM) != targetVolume) {
                    setAlarmVolume(targetVolume)
                }
                delay(250)
            }
        }
    }

    /** 1 = full blast, 0 = silent (used while the microphone needs to hear the sleeper). */
    fun setDuck(level: Float) {
        duck = level.coerceIn(0f, 1f)
        applyVolume()
        updateVibration()
    }

    fun stop() {
        loop?.cancel()
        loop = null
        media?.runCatching { stop(); release() }
        media = null
        track?.runCatching { stop(); release() }
        track = null
        if (vibrating) vibrator.cancel()
        vibrating = false
        focusRequest?.let { audio.abandonAudioFocusRequest(it) }
        focusRequest = null
        if (savedVolume >= 0) setAlarmVolume(savedVolume)
        savedVolume = -1
    }

    private fun startSound(soundId: String) {
        BuiltinSound.byId(soundId)?.let { return startSynth(it) }
        val player = MediaPlayer()
        val started = runCatching {
            player.setAudioAttributes(attributes)
            player.setDataSource(context, Uri.parse(soundId))
            route?.let { player.setPreferredDevice(it) }
            player.isLooping = true
            player.setOnErrorListener { _, what, extra ->
                Log.w(TAG, "Ringtone failed ($what/$extra), falling back to the siren")
                media?.release()
                media = null
                startSynth(BuiltinSound.HELLFIRE)
                true
            }
            player.prepare()
            player.start()
        }.isSuccess
        if (started) {
            media = player
        } else {
            // Ringtones are unreadable before the first unlock, or the file may be gone. Never ring silently.
            player.release()
            startSynth(BuiltinSound.HELLFIRE)
        }
    }

    private fun startSynth(sound: BuiltinSound) {
        track = SoundSynth.loopTrack(SoundSynth.render(sound), attributes, route).also {
            it.setVolume(gain * duck)
            it.play()
        }
    }

    private fun applyVolume() {
        val v = (gain * duck).coerceIn(0f, 1f)
        media?.setVolume(v, v)
        track?.setVolume(v)
    }

    private fun updateVibration() {
        val should = wantsVibration && duck > 0.05f
        if (should && !vibrating) {
            val effect = VibrationEffect.createWaveform(longArrayOf(0, 900, 450), 0)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                vibrator.vibrate(effect, VibrationAttributes.createForUsage(VibrationAttributes.USAGE_ALARM))
            } else {
                @Suppress("DEPRECATION")
                vibrator.vibrate(effect, ALARM_ATTRIBUTES)
            }
        } else if (!should && vibrating) {
            vibrator.cancel()
        }
        vibrating = should
    }

    private fun requestFocus() {
        val request = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT)
            .setAudioAttributes(attributes)
            .setOnAudioFocusChangeListener { /* An alarm keeps ringing no matter who wants focus. */ }
            .build()
        audio.requestAudioFocus(request)
        focusRequest = request
    }

    private fun setAlarmVolume(index: Int) {
        try {
            audio.setStreamVolume(AudioManager.STREAM_ALARM, index, 0)
        } catch (e: SecurityException) {
            Log.w(TAG, "Can't change alarm volume", e)
        }
    }

    companion object {
        private const val TAG = "AlarmPlayer"
        private const val CRESCENDO_MS = 45_000L
        private const val CRESCENDO_FLOOR = 0.12f

        val ALARM_ATTRIBUTES: AudioAttributes = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_ALARM)
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
            .build()
    }
}
