package com.shenaas.alarm.ui.settings

import android.Manifest
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.DirectionsWalk
import androidx.compose.material.icons.automirrored.rounded.OpenInNew
import androidx.compose.material.icons.rounded.AccessAlarm
import androidx.compose.material.icons.rounded.BatteryAlert
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Fullscreen
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.NotificationsActive
import androidx.compose.material.icons.rounded.PhotoCamera
import androidx.compose.material.icons.rounded.Psychology
import androidx.compose.material.icons.rounded.Timer
import androidx.compose.material.icons.rounded.VerifiedUser
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import com.shenaas.alarm.alarm.TestAlarm
import com.shenaas.alarm.challenge.HuntPack
import com.shenaas.alarm.data.Prefs
import com.shenaas.alarm.ui.components.ButtonKind
import com.shenaas.alarm.ui.components.GlowBackground
import com.shenaas.alarm.ui.components.HellButton
import com.shenaas.alarm.ui.components.SectionCard
import com.shenaas.alarm.ui.components.SelectChip
import com.shenaas.alarm.ui.components.ShenaasLogo
import com.shenaas.alarm.ui.components.ToggleRow
import com.shenaas.alarm.ui.theme.Brand
import com.shenaas.alarm.util.Permissions

private data class Check(
    val title: String,
    val why: String,
    val icon: ImageVector,
    val ok: Boolean,
    val critical: Boolean,
    val fix: () -> Unit,
)

@Composable
fun SettingsScreen(onBack: () -> Unit, onAbout: () -> Unit) {
    BackHandler(onBack = onBack)
    val context = LocalContext.current
    var refresh by remember { mutableIntStateOf(0) }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { refresh++ }
    val permissionRequest = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        refresh++
        if (!granted) context.open(Permissions.appSettings(context))
    }
    fun request(permission: String) = permissionRequest.launch(permission)

    val checks = remember(refresh) {
        listOf(
            Check(
                "Notifications", "The alarm shows itself through a notification.", Icons.Rounded.NotificationsActive,
                Permissions.notifications(context), critical = true,
            ) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && !Permissions.granted(context, Manifest.permission.POST_NOTIFICATIONS)) {
                    request(Manifest.permission.POST_NOTIFICATIONS)
                } else {
                    context.open(Permissions.notificationSettings(context))
                }
            },
            Check(
                "Full-screen alarm", "Takes over the lock screen when it rings.", Icons.Rounded.Fullscreen,
                Permissions.fullScreen(context), critical = true,
            ) { context.open(Permissions.fullScreenSettings(context)) },
            Check(
                "Exact alarms", "Rings at 6:30, not \"around 6:40\".", Icons.Rounded.AccessAlarm,
                Permissions.exactAlarms(context), critical = true,
            ) { context.open(Permissions.exactAlarmSettings(context)) },
            Check(
                "Microphone", "For the Word on Beat challenge.", Icons.Rounded.Mic,
                Permissions.granted(context, Manifest.permission.RECORD_AUDIO), critical = false,
            ) { request(Manifest.permission.RECORD_AUDIO) },
            Check(
                "Camera", "For the Find the Object challenge.", Icons.Rounded.PhotoCamera,
                Permissions.granted(context, Manifest.permission.CAMERA), critical = false,
            ) { request(Manifest.permission.CAMERA) },
            Check(
                "Physical activity", "Accurate step counting for the Steps challenge.", Icons.AutoMirrored.Rounded.DirectionsWalk,
                Permissions.granted(context, Manifest.permission.ACTIVITY_RECOGNITION), critical = false,
            ) { request(Manifest.permission.ACTIVITY_RECOGNITION) },
            Check(
                "Unrestricted battery", "Optional. Stops aggressive battery saving from getting in the way.", Icons.Rounded.BatteryAlert,
                Permissions.unrestrictedBattery(context), critical = false,
            ) { context.open(Permissions.batterySettings(context)) },
        )
    }

    var giveUp by remember { mutableIntStateOf(Prefs.giveUpMinutes) }
    var wakeDelay by remember { mutableIntStateOf(Prefs.wakeCheckDelayMinutes) }
    var adaptive by remember { mutableStateOf(Prefs.adaptiveDifficulty) }
    var huntPack by remember { mutableStateOf(Prefs.huntPack) }

    GlowBackground {
        Column(
            Modifier
                .fillMaxSize()
                .statusBarsPadding(),
        ) {
            Row(Modifier.padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Back", tint = Brand.Text) }
                Text("Settings", style = MaterialTheme.typography.titleLarge, color = Brand.Text)
            }
            Column(
                Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 20.dp)
                    .navigationBarsPadding(),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                SectionCard(title = "Will it actually ring?", subtitle = "Green across the board means no excuses tomorrow") {
                    checks.forEach { check -> CheckRow(check) }
                    Spacer(Modifier.height(12.dp))
                    HellButton(
                        "RING A TEST ALARM IN 10 SECONDS",
                        onClick = {
                            TestAlarm.schedule(context)
                            Toast.makeText(context, "Lock your phone. It rings in 10 seconds.", Toast.LENGTH_LONG).show()
                        },
                        kind = ButtonKind.Outline,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }

                SectionCard(title = "Alarm behavior") {
                    Text("Give up after", style = MaterialTheme.typography.titleSmall, color = Brand.Text)
                    Text("Then it counts as a missed alarm and breaks your streak.", style = MaterialTheme.typography.bodySmall, color = Brand.TextSecondary)
                    Spacer(Modifier.height(10.dp))
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf(15 to "15 min", 30 to "30 min", 60 to "1 hour", 0 to "Never").forEach { (minutes, label) ->
                            SelectChip(label, giveUp == minutes, {
                                giveUp = minutes
                                Prefs.giveUpMinutes = minutes
                            })
                        }
                    }
                    Spacer(Modifier.height(18.dp))
                    Text("Wake-up check after", style = MaterialTheme.typography.titleSmall, color = Brand.Text)
                    Spacer(Modifier.height(10.dp))
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf(2, 3, 5, 10).forEach { minutes ->
                            SelectChip("$minutes min", wakeDelay == minutes, {
                                wakeDelay = minutes
                                Prefs.wakeCheckDelayMinutes = minutes
                            })
                        }
                    }
                    Spacer(Modifier.height(18.dp))
                    Text("Find the Object items", style = MaterialTheme.typography.titleSmall, color = Brand.Text)
                    Text(
                        "Afghan home asks for things like a piyala, naan, the dastarkhan or the toshak.",
                        style = MaterialTheme.typography.bodySmall,
                        color = Brand.TextSecondary,
                    )
                    Spacer(Modifier.height(10.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        HuntPack.entries.forEach { pack ->
                            SelectChip(pack.label, huntPack == pack, {
                                huntPack = pack
                                Prefs.huntPack = pack
                            })
                        }
                    }
                    Spacer(Modifier.height(10.dp))
                    ToggleRow(
                        "Adaptive difficulty",
                        "Crush a challenge and it gets harder next time. Struggle and it eases off.",
                        adaptive,
                        {
                            adaptive = it
                            Prefs.adaptiveDifficulty = it
                        },
                        icon = Icons.Rounded.Psychology,
                    )
                }

                SectionCard(title = "About") {
                    HellButton("ABOUT THIS APP", onClick = onAbout, kind = ButtonKind.Outline, modifier = Modifier.fillMaxWidth())
                    Spacer(Modifier.height(16.dp))
                    ShenaasLogo(height = 28.dp)
                    Spacer(Modifier.height(12.dp))
                    Text(
                        "Alarm From Hell is made by Shenaas, the digital marketing agency that never hits snooze.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = Brand.TextSecondary,
                    )
                    Spacer(Modifier.height(12.dp))
                    Row(
                        Modifier
                            .clip(CircleShape)
                            .clickable { context.open(Intent(Intent.ACTION_VIEW, Uri.parse("https://shenaas.com"))) }
                            .padding(vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text("shenaas.com", style = MaterialTheme.typography.titleSmall, color = Brand.Orange)
                        Spacer(Modifier.width(6.dp))
                        Icon(Icons.AutoMirrored.Rounded.OpenInNew, contentDescription = null, tint = Brand.Orange, modifier = Modifier.size(16.dp))
                    }
                    Spacer(Modifier.height(8.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Rounded.VerifiedUser, contentDescription = null, tint = Brand.TextMuted, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(
                            "Camera and voice challenges run entirely on your phone. Nothing is uploaded.",
                            style = MaterialTheme.typography.bodySmall,
                            color = Brand.TextMuted,
                        )
                    }
                    Spacer(Modifier.height(4.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Rounded.Timer, contentDescription = null, tint = Brand.TextMuted, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Version 1.0.0", style = MaterialTheme.typography.bodySmall, color = Brand.TextMuted)
                    }
                }
                Spacer(Modifier.height(24.dp))
            }
        }
    }
}

@Composable
private fun CheckRow(check: Check) {
    val color = when {
        check.ok -> Brand.Green
        check.critical -> Brand.HellRed
        else -> Brand.Orange
    }
    Row(
        Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.small)
            .clickable(enabled = !check.ok, onClick = check.fix)
            .padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(check.icon, contentDescription = null, tint = color, modifier = Modifier.size(22.dp))
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(check.title, style = MaterialTheme.typography.titleSmall, color = Brand.Text)
            Text(check.why, style = MaterialTheme.typography.bodySmall, color = Brand.TextSecondary)
        }
        Spacer(Modifier.width(10.dp))
        if (check.ok) {
            Icon(Icons.Rounded.CheckCircle, contentDescription = "Granted", tint = Brand.Green)
        } else {
            Text(
                "FIX",
                style = MaterialTheme.typography.labelLarge,
                color = Brand.Night,
                modifier = Modifier
                    .clip(CircleShape)
                    .background(color)
                    .padding(horizontal = 14.dp, vertical = 6.dp),
            )
        }
    }
}

private fun Context.open(intent: Intent) {
    try {
        startActivity(intent)
    } catch (_: ActivityNotFoundException) {
        startActivity(Permissions.appSettings(this))
    }
}
