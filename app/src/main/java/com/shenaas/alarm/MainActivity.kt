package com.shenaas.alarm

import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import com.shenaas.alarm.alarm.RingSession
import com.shenaas.alarm.ui.about.AboutScreen
import com.shenaas.alarm.ui.edit.EditAlarmScreen
import com.shenaas.alarm.ui.home.HomeScreen
import com.shenaas.alarm.ui.lab.TestLabScreen
import com.shenaas.alarm.ui.ring.RingActivity
import com.shenaas.alarm.ui.settings.SettingsScreen
import com.shenaas.alarm.ui.stats.StatsScreen
import com.shenaas.alarm.ui.theme.ShenaasTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
        )
        setContent {
            ShenaasTheme {
                App()
            }
        }
    }

    override fun onResume() {
        super.onResume()
        // An alarm is going off: there's nothing to do here until it's beaten.
        if (RingSession.isRinging) startActivity(Intent(this, RingActivity::class.java))
    }
}

/** Routes: "home", "new", "edit/<id>", "settings", "about", "stats", and "lab" in debug builds. */
@Composable
private fun App() {
    var route by rememberSaveable { mutableStateOf("home") }
    BackHandler(enabled = route != "home") { route = "home" }

    AnimatedContent(
        targetState = route,
        transitionSpec = {
            if (targetState == "home") {
                (slideInHorizontally(tween(260)) { -it / 4 } + fadeIn()) togetherWith (slideOutHorizontally(tween(260)) { it / 3 } + fadeOut())
            } else {
                (slideInHorizontally(tween(260)) { it / 3 } + fadeIn()) togetherWith (slideOutHorizontally(tween(260)) { -it / 4 } + fadeOut())
            }
        },
        label = "route",
    ) { current ->
        when {
            current == "home" -> HomeScreen(
                onAdd = { route = "new" },
                onEdit = { route = "edit/$it" },
                onSettings = { route = "settings" },
                onStats = { route = "stats" },
                onLab = { route = "lab" },
            )
            current == "new" -> EditAlarmScreen(alarmId = null, onDone = { route = "home" })
            current.startsWith("edit/") -> EditAlarmScreen(alarmId = current.removePrefix("edit/").toIntOrNull(), onDone = { route = "home" })
            current == "settings" -> SettingsScreen(onBack = { route = "home" }, onAbout = { route = "about" })
            current == "about" -> AboutScreen(onBack = { route = "settings" })
            current == "stats" -> StatsScreen(onBack = { route = "home" })
            current == "lab" && BuildConfig.DEBUG -> TestLabScreen(onBack = { route = "home" })
        }
    }
}
