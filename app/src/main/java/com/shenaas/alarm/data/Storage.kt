package com.shenaas.alarm.data

import android.content.Context
import android.content.SharedPreferences
import android.util.AtomicFile
import java.io.File
import java.io.IOException

/**
 * All app data lives in device-protected storage so alarms can be restored and can ring
 * after a reboot, before the user unlocks the phone for the first time.
 */
internal object Storage {
    fun deviceContext(context: Context): Context =
        context.applicationContext.createDeviceProtectedStorageContext()

    fun file(context: Context, name: String): AtomicFile =
        AtomicFile(File(deviceContext(context).filesDir, name))

    fun prefs(context: Context): SharedPreferences =
        deviceContext(context).getSharedPreferences("shenaas_alarm", Context.MODE_PRIVATE)
}

internal fun AtomicFile.readText(): String? = try {
    String(readFully(), Charsets.UTF_8)
} catch (_: IOException) {
    null
}

internal fun AtomicFile.writeText(text: String) {
    val out = try {
        startWrite()
    } catch (_: IOException) {
        return
    }
    try {
        out.write(text.toByteArray(Charsets.UTF_8))
        finishWrite(out)
    } catch (_: IOException) {
        failWrite(out)
    }
}
