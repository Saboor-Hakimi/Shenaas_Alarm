package com.shenaas.alarm.util

import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.shenaas.alarm.alarm.AlarmScheduler

/** Everything an alarm needs to be able to wake someone, and where to fix it. */
object Permissions {
    fun granted(context: Context, permission: String) =
        ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED

    fun notifications(context: Context) = NotificationManagerCompat.from(context).areNotificationsEnabled()

    /** Android 14+ lets users revoke full-screen alerts, which is what puts the alarm over the lock screen. */
    fun fullScreen(context: Context) =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.UPSIDE_DOWN_CAKE ||
            context.getSystemService(NotificationManager::class.java).canUseFullScreenIntent()

    fun exactAlarms(context: Context) = AlarmScheduler.canScheduleExact(context)

    fun unrestrictedBattery(context: Context) =
        context.getSystemService(PowerManager::class.java).isIgnoringBatteryOptimizations(context.packageName)

    /** True when an alarm will reliably ring and show its screen. */
    fun alarmReady(context: Context) = notifications(context) && fullScreen(context) && exactAlarms(context)

    fun notificationSettings(context: Context) = Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
        .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)

    fun fullScreenSettings(context: Context): Intent =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            Intent(Settings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT, packageUri(context))
        } else {
            notificationSettings(context)
        }

    fun exactAlarmSettings(context: Context) = Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, packageUri(context))

    fun batterySettings(context: Context) = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, packageUri(context))

    fun appSettings(context: Context) = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, packageUri(context))

    private fun packageUri(context: Context) = Uri.parse("package:${context.packageName}")
}
