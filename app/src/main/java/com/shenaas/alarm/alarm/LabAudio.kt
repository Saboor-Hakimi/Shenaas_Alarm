package com.shenaas.alarm.alarm

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioDeviceInfo
import android.media.AudioManager
import com.shenaas.alarm.data.Prefs

/**
 * Debug-only audio routing for the Test Lab, so alarm sounds and beats can be heard in headphones
 * without the phone speaker. Android always duplicates alarm-type audio to the speaker for
 * safety, so lab sound plays as media pinned to a headphone device, and stays silent if none is connected.
 */
object LabAudio {
    enum class Mode { SILENT, HEADPHONES }

    private val headphoneTypes = setOf(
        AudioDeviceInfo.TYPE_WIRED_HEADSET,
        AudioDeviceInfo.TYPE_WIRED_HEADPHONES,
        AudioDeviceInfo.TYPE_BLUETOOTH_A2DP,
        AudioDeviceInfo.TYPE_BLE_HEADSET,
        AudioDeviceInfo.TYPE_USB_HEADSET,
        AudioDeviceInfo.TYPE_HEARING_AID,
    )

    val MEDIA_ATTRIBUTES: AudioAttributes = AudioAttributes.Builder()
        .setUsage(AudioAttributes.USAGE_MEDIA)
        .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
        .build()

    var mode: Mode
        get() = Mode.entries.getOrElse(Prefs.labAudioMode) { Mode.SILENT }
        set(value) {
            Prefs.labAudioMode = value.ordinal
        }

    fun headphones(context: Context): List<AudioDeviceInfo> =
        context.getSystemService(AudioManager::class.java)
            .getDevices(AudioManager.GET_DEVICES_OUTPUTS)
            .filter { it.type in headphoneTypes }

    /** The headphones to play through: the one picked in the lab if still connected, else any. */
    fun device(context: Context): AudioDeviceInfo? {
        val connected = headphones(context)
        return connected.firstOrNull { it.id == Prefs.labAudioDeviceId } ?: connected.firstOrNull()
    }

    fun describe(device: AudioDeviceInfo): String {
        val kind = when (device.type) {
            AudioDeviceInfo.TYPE_BLUETOOTH_A2DP, AudioDeviceInfo.TYPE_BLE_HEADSET -> "Bluetooth"
            AudioDeviceInfo.TYPE_USB_HEADSET -> "USB"
            AudioDeviceInfo.TYPE_HEARING_AID -> "Hearing aid"
            else -> "Wired"
        }
        return "${device.productName} · $kind"
    }
}
