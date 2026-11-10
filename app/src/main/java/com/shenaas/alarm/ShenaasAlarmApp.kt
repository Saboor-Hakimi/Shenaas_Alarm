package com.shenaas.alarm

import android.app.Application
import com.shenaas.alarm.alarm.Notifications
import com.shenaas.alarm.alarm.RingFlow
import com.shenaas.alarm.data.AlarmRepository
import com.shenaas.alarm.data.Prefs
import com.shenaas.alarm.data.StatsRepository

class ShenaasAlarmApp : Application() {
    override fun onCreate() {
        super.onCreate()
        Prefs.init(this)
        AlarmRepository.init(this)
        StatsRepository.init(this)
        Notifications.createChannels(this)

        val crashHandler = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, error ->
            runCatching { RingFlow.recoverFromCrash(this) }
            crashHandler?.uncaughtException(thread, error)
        }
    }
}
