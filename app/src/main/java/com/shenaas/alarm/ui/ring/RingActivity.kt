package com.shenaas.alarm.ui.ring

import android.graphics.Color
import android.os.Bundle
import android.view.KeyEvent
import android.view.WindowManager
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.shenaas.alarm.alarm.AlarmReceiver
import com.shenaas.alarm.alarm.AlarmService
import com.shenaas.alarm.alarm.RingSession
import com.shenaas.alarm.challenge.Sarcasm
import com.shenaas.alarm.model.Alarm
import com.shenaas.alarm.model.RingPhase
import com.shenaas.alarm.model.RingReason
import com.shenaas.alarm.ui.ring.challenges.LocalBeatsEnabled
import com.shenaas.alarm.ui.ring.challenges.LocalBeatsToHeadphones
import com.shenaas.alarm.ui.theme.ShenaasTheme
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.json.JSONObject

/**
 * The screen of a ringing alarm. Shows over the lock screen, can't be backed out of, swallows the
 * volume keys in extreme mode, and only closes once the alarm is beaten or snoozed.
 */
open class RingActivity : ComponentActivity() {
    protected lateinit var controller: RingController

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
        )
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        controller = createController() ?: run {
            finish()
            return
        }
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (controller.state.value?.phase == RingPhase.DONE) {
                    controller.close()
                    finish()
                } else if (!controller.practice) {
                    Toast.makeText(this@RingActivity, Sarcasm.pick(Sarcasm.idle), Toast.LENGTH_SHORT).show()
                } else {
                    finish()
                }
            }
        })
        setContent {
            val state by controller.state.collectAsStateWithLifecycle()
            ShenaasTheme {
                CompositionLocalProvider(
                    LocalBeatsEnabled provides (beatsEnabled && state?.alarm?.silent != true),
                    LocalBeatsToHeadphones provides (beatsToHeadphones || state?.alarm?.headphonesOnly == true),
                ) {
                    RingRoot(controller, onClose = ::finish)
                }
            }
        }
        watchForEnd()
    }

    protected open val beatsEnabled: Boolean get() = true
    protected open val beatsToHeadphones: Boolean get() = false

    protected open fun createController(): RingController? {
        setShowWhenLocked(true)
        setTurnScreenOn(true)
        val session = RingSession.state.value
        when {
            session == null -> {
                // Opened from the fallback notification: the service couldn't start in the background,
                // but it can now that this screen is in the foreground.
                val alarmId = intent.getIntExtra(AlarmReceiver.EXTRA_ALARM_ID, -1)
                if (alarmId < 0) return null
                AlarmService.start(this, alarmId, RingReason.SCHEDULED)
            }
            session.phase != RingPhase.DONE && !AlarmService.running ->
                AlarmService.start(this, session.alarm.id, RingReason.RESUMED)
        }
        return LiveRingController(applicationContext)
    }

    /** Closes the screen when the ring ends elsewhere (snooze, give-up, or never started). */
    private fun watchForEnd() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                var seen = controller.state.value != null
                launch {
                    delay(5_000)
                    if (!seen) finish()
                }
                controller.state.collect { state ->
                    if (state != null) seen = true else if (seen) finish()
                }
            }
        }
    }

    override fun onStart() {
        super.onStart()
        if (::controller.isInitialized && !controller.practice) RingSession.screenVisible = true
    }

    override fun onStop() {
        if (::controller.isInitialized && !controller.practice) RingSession.screenVisible = false
        super.onStop()
    }

    override fun onDestroy() {
        if (isFinishing && ::controller.isInitialized) controller.close()
        super.onDestroy()
    }

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        val state = if (::controller.isInitialized) controller.state.value else null
        val locked = state != null && !controller.practice && state.phase != RingPhase.DONE && state.alarm.extremeMode
        if (locked && event.keyCode in VOLUME_KEYS) return true
        return super.dispatchKeyEvent(event)
    }

    private companion object {
        val VOLUME_KEYS = setOf(KeyEvent.KEYCODE_VOLUME_UP, KeyEvent.KEYCODE_VOLUME_DOWN, KeyEvent.KEYCODE_VOLUME_MUTE)
    }
}

/** Runs an alarm's challenges with no alarm sound, from the editor or the Test Lab. */
class PracticeActivity : RingActivity() {
    override val beatsEnabled: Boolean get() = !intent.getBooleanExtra(EXTRA_MUTE_BEATS, false)
    override val beatsToHeadphones: Boolean get() = intent.getBooleanExtra(EXTRA_HEADPHONES, false)

    override fun createController(): RingController? {
        val alarm = intent.getStringExtra(EXTRA_ALARM_JSON)?.let { runCatching { Alarm.fromJson(JSONObject(it)) }.getOrNull() } ?: return null
        return PracticeRingController(applicationContext, alarm)
    }

    companion object {
        const val EXTRA_ALARM_JSON = "alarm_json"
        const val EXTRA_MUTE_BEATS = "mute_beats"
        const val EXTRA_HEADPHONES = "headphones"
    }
}
