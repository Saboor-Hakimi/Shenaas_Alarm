package com.shenaas.alarm.data

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit
import com.shenaas.alarm.challenge.HuntPack
import com.shenaas.alarm.model.RingState
import org.json.JSONObject

object Prefs {
    private lateinit var prefs: SharedPreferences

    fun init(context: Context) {
        if (!::prefs.isInitialized) prefs = Storage.prefs(context)
    }

    /** Minutes before a ringing alarm gives up and counts as missed. 0 means never. */
    var giveUpMinutes: Int
        get() = prefs.getInt("give_up_minutes", 60)
        set(value) = prefs.edit { putInt("give_up_minutes", value) }

    var adaptiveDifficulty: Boolean
        get() = prefs.getBoolean("adaptive", true)
        set(value) = prefs.edit { putBoolean("adaptive", value) }

    /** Minutes after dismissal before the "are you awake?" check. */
    var wakeCheckDelayMinutes: Int
        get() = prefs.getInt("wake_check_delay", 3)
        set(value) = prefs.edit { putInt("wake_check_delay", value) }

    /** Which objects Find the Object asks for; Afghan homes by default. */
    var huntPack: HuntPack
        get() = HuntPack.entries.firstOrNull { it.name == prefs.getString("hunt_pack", null) } ?: HuntPack.AFGHAN_HOME
        set(value) = prefs.edit { putString("hunt_pack", value.name) }

    /** Test Lab audio: an ordinal of [com.shenaas.alarm.alarm.LabAudio.Mode]. */
    var labAudioMode: Int
        get() = prefs.getInt("lab_audio_mode", 0)
        set(value) = prefs.edit { putInt("lab_audio_mode", value) }

    var labAudioDeviceId: Int
        get() = prefs.getInt("lab_audio_device", 0)
        set(value) = prefs.edit { putInt("lab_audio_device", value) }

    var setupSeen: Boolean
        get() = prefs.getBoolean("setup_seen", false)
        set(value) = prefs.edit { putBoolean("setup_seen", value) }

    /** The ring in progress, persisted so a reboot or process death can't get the sleeper out of it. */
    var activeRing: RingState?
        get() = prefs.getString("active_ring", null)?.let { runCatching { RingState.fromJson(JSONObject(it)) }.getOrNull() }
        set(value) = prefs.edit(commit = true) {
            if (value == null) remove("active_ring") else putString("active_ring", value.toJson().toString())
        }
}
