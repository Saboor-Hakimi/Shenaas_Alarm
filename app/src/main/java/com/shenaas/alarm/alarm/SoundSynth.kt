package com.shenaas.alarm.alarm

import android.media.AudioAttributes
import android.media.AudioDeviceInfo
import android.media.AudioFormat
import android.media.AudioTrack
import com.shenaas.alarm.model.BuiltinSound
import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.tanh
import kotlin.random.Random

/**
 * Generates the built-in alarm sounds and the challenge beat as looping PCM, so the app ships
 * no audio assets and every sound works even before the first unlock after a reboot.
 */
object SoundSynth {
    const val RATE = 44_100
    private const val TWO_PI = 2 * PI

    private val cache = HashMap<BuiltinSound, ShortArray>()

    @Synchronized
    fun render(sound: BuiltinSound): ShortArray = cache.getOrPut(sound) {
        when (sound) {
            BuiltinSound.HELLFIRE -> siren()
            BuiltinSound.KLAXON -> klaxon()
            BuiltinSound.BEEPER -> beeper()
            BuiltinSound.AIRHORN -> airHorn()
            BuiltinSound.DEMON_BELL -> demonBell()
            BuiltinSound.SUNRISE -> sunrise()
        }
    }

    private val ALARM: AudioAttributes = AudioAttributes.Builder()
        .setUsage(AudioAttributes.USAGE_ALARM)
        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
        .build()

    /**
     * A static, endlessly looping track. Call [AudioTrack.play] to start it. [device] pins playback
     * to one output (used by the Test Lab to keep sound in headphones).
     */
    fun loopTrack(pcm: ShortArray, attributes: AudioAttributes = ALARM, device: AudioDeviceInfo? = null): AudioTrack {
        val track = AudioTrack.Builder()
            .setAudioAttributes(attributes)
            .setAudioFormat(
                AudioFormat.Builder()
                    .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                    .setSampleRate(RATE)
                    .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                    .build(),
            )
            .setTransferMode(AudioTrack.MODE_STATIC)
            .setBufferSizeInBytes(pcm.size * 2)
            .build()
        track.write(pcm, 0, pcm.size)
        track.setLoopPoints(0, pcm.size, -1)
        if (device != null) track.setPreferredDevice(device)
        return track
    }

    /** One bar of kick / snare / hi-hat at [bpm]. Four beats per loop. */
    fun beatLoop(bpm: Float): ShortArray {
        val beat = 60.0 / bpm
        val n = (RATE * beat * 4).toInt()
        val out = FloatArray(n)
        val noise = Random(7)
        for (k in 0 until 4) {
            val beatStart = (k * beat * RATE).toInt()
            // Kick: sine with a fast pitch drop.
            var phase = 0.0
            for (i in 0 until (0.35 * RATE).toInt()) {
                val idx = beatStart + i
                if (idx >= n) break
                val t = i.toDouble() / RATE
                phase += TWO_PI * (45 + 110 * exp(-t * 28)) / RATE
                out[idx] += (sin(phase) * exp(-t * 9) * 0.95).toFloat()
            }
            // Snare on 2 and 4.
            if (k % 2 == 1) {
                for (i in 0 until (0.22 * RATE).toInt()) {
                    val idx = beatStart + i
                    if (idx >= n) break
                    val t = i.toDouble() / RATE
                    val body = sin(TWO_PI * 190 * t) * exp(-t * 30) * 0.35
                    val rattle = (noise.nextDouble() * 2 - 1) * exp(-t * 20) * 0.5
                    out[idx] += (body + rattle).toFloat()
                }
            }
            // Off-beat hi-hat: differentiated noise is a cheap high-pass.
            val hatStart = beatStart + (beat * RATE / 2).toInt()
            var last = 0.0
            for (i in 0 until (0.05 * RATE).toInt()) {
                val idx = hatStart + i
                if (idx >= n) break
                val t = i.toDouble() / RATE
                val white = noise.nextDouble() * 2 - 1
                out[idx] += ((white - last) * exp(-t * 90) * 0.22).toFloat()
                last = white
            }
        }
        return normalize(out, 0.8f)
    }

    /** A short bright click used as the metronome tick for count-ins. */
    fun tick(): ShortArray {
        val n = (0.06 * RATE).toInt()
        val out = FloatArray(n) { i ->
            val t = i.toDouble() / RATE
            (sin(TWO_PI * 1800 * t) * exp(-t * 70)).toFloat()
        }
        return normalize(out, 0.7f)
    }

    private fun siren(): ShortArray {
        val rise = 1.7
        val fall = 0.7
        val n = ((rise + fall) * RATE).toInt()
        var phase = 0.0
        val out = FloatArray(n) { i ->
            val t = i.toDouble() / RATE
            val f = if (t < rise) 480 + 1020 * (t / rise) else 1500 - 1020 * ((t - rise) / fall)
            phase += TWO_PI * f / RATE
            (tanh(3.0 * sin(phase)) * 0.7 + 0.3 * sin(phase * 2 + 0.5)).toFloat()
        }
        return normalize(out)
    }

    private fun klaxon(): ShortArray {
        val on = 0.75
        val n = (1.05 * RATE).toInt()
        var phase = 0.0
        val out = FloatArray(n) { i ->
            val t = i.toDouble() / RATE
            if (t >= on) return@FloatArray 0f
            val f = 220 + 330 * (t / on).pow(0.6)
            phase += TWO_PI * f / RATE
            val env = minOf(1.0, t / 0.01, (on - t) / 0.04)
            ((tanh(6 * sin(phase)) + 0.4 * tanh(6 * sin(phase * 2.01))) * env).toFloat()
        }
        return normalize(out)
    }

    private fun beeper(): ShortArray {
        val beep = 0.07
        val gap = 0.06
        val n = ((4 * (beep + gap) + 0.45) * RATE).toInt()
        val out = FloatArray(n) { i ->
            val t = i.toDouble() / RATE
            val slot = (t / (beep + gap)).toInt()
            val local = t - slot * (beep + gap)
            if (slot >= 4 || local > beep) return@FloatArray 0f
            val env = minOf(1.0, local / 0.003, (beep - local) / 0.003)
            (tanh(4 * sin(TWO_PI * 3150 * t)) * env).toFloat()
        }
        return normalize(out)
    }

    private fun airHorn(): ShortArray {
        val on = 1.1
        val n = ((on + 0.35) * RATE).toInt()
        val freqs = doubleArrayOf(233.08, 349.23, 466.16, 233.08 * 1.004)
        val phases = DoubleArray(freqs.size)
        val out = FloatArray(n) { i ->
            val t = i.toDouble() / RATE
            if (t >= on) return@FloatArray 0f
            var s = 0.0
            for (k in freqs.indices) {
                val vibrato = 1 + 0.004 * sin(TWO_PI * 5.5 * t)
                phases[k] = (phases[k] + freqs[k] * vibrato / RATE) % 1.0
                s += 2 * phases[k] - 1
            }
            val env = minOf(1.0, t / 0.015, (on - t) / 0.06)
            (tanh(2.5 * s / freqs.size) * env).toFloat()
        }
        return normalize(out)
    }

    private fun demonBell(): ShortArray {
        val strike = 1.1
        val n = (2 * strike * RATE).toInt()
        val carrier = 196.0
        val out = FloatArray(n) { i ->
            val t = (i.toDouble() / RATE) % strike
            val index = 6 * exp(-3 * t)
            val fm = sin(TWO_PI * carrier * t + index * sin(TWO_PI * carrier * 1.41 * t)) * exp(-1.6 * t)
            val tritone = 0.3 * sin(TWO_PI * carrier * 1.414 * t) * exp(-2.2 * t)
            val thud = 0.4 * sin(TWO_PI * 70 * t) * exp(-12 * t)
            (fm + tritone + thud).toFloat()
        }
        return normalize(out)
    }

    private fun sunrise(): ShortArray {
        val notes = doubleArrayOf(523.25, 659.25, 783.99, 1046.5)
        val step = 0.35
        val n = ((notes.size * step + 0.6) * RATE).toInt()
        val out = FloatArray(n)
        notes.forEachIndexed { k, f ->
            val start = (k * step * RATE).toInt()
            for (i in 0 until (1.2 * RATE).toInt()) {
                val idx = start + i
                if (idx >= n) break
                val t = i.toDouble() / RATE
                val attack = minOf(1.0, t / 0.01)
                out[idx] += ((sin(TWO_PI * f * t) + 0.2 * sin(TWO_PI * 2 * f * t)) * exp(-4 * t) * attack).toFloat()
            }
        }
        return normalize(out, 0.8f)
    }

    private fun normalize(samples: FloatArray, peak: Float = 0.92f): ShortArray {
        val max = samples.maxOfOrNull { kotlin.math.abs(it) }?.takeIf { it > 0f } ?: 1f
        val scale = peak * Short.MAX_VALUE / max
        return ShortArray(samples.size) { (samples[it] * scale).toInt().toShort() }
    }
}
