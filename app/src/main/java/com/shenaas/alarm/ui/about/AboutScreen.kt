package com.shenaas.alarm.ui.about

import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Code
import androidx.compose.material.icons.rounded.Language
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.VerifiedUser
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.shenaas.alarm.BuildConfig
import com.shenaas.alarm.R
import com.shenaas.alarm.ui.components.ButtonKind
import com.shenaas.alarm.ui.components.GlowBackground
import com.shenaas.alarm.ui.components.HellButton
import com.shenaas.alarm.ui.components.IconBubble
import com.shenaas.alarm.ui.components.SectionCard
import com.shenaas.alarm.ui.components.ShenaasLogo
import com.shenaas.alarm.ui.theme.Brand

private const val SHENAAS_URL = "https://shenaas.com"
private const val GITHUB_URL = "https://github.com/saboor-hakimi"

@Composable
fun AboutScreen(onBack: () -> Unit) {
    BackHandler(onBack = onBack)
    val context = LocalContext.current
    fun open(url: String) {
        try {
            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
        } catch (_: ActivityNotFoundException) {
            // No browser on the device; nothing sensible to fall back to.
        }
    }

    GlowBackground {
        Column(
            Modifier
                .fillMaxSize()
                .statusBarsPadding(),
        ) {
            Row(Modifier.padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Back", tint = Brand.Text) }
                Text("About", style = MaterialTheme.typography.titleLarge, color = Brand.Text)
            }
            Column(
                Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 20.dp)
                    .navigationBarsPadding(),
                verticalArrangement = Arrangement.spacedBy(14.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Spacer(Modifier.height(8.dp))
                Box(
                    Modifier
                        .size(112.dp)
                        .clip(MaterialTheme.shapes.extraLarge)
                        .background(Brand.Surface),
                    contentAlignment = Alignment.Center,
                ) {
                    Image(painterResource(R.drawable.ic_launcher_foreground), contentDescription = null, modifier = Modifier.size(112.dp))
                }
                Text("ALARM FROM HELL", style = MaterialTheme.typography.headlineLarge.merge(TextStyle(brush = Brand.Hellfire)), textAlign = TextAlign.Center)
                Text("Version ${BuildConfig.VERSION_NAME}", style = MaterialTheme.typography.bodyMedium, color = Brand.TextSecondary)

                SectionCard(title = "Developed by") {
                    ShenaasLogo(height = 30.dp)
                    Spacer(Modifier.height(12.dp))
                    Text(
                        "Alarm From Hell is developed by Shenaas, a digital marketing agency that never hits snooze.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = Brand.TextSecondary,
                    )
                }

                SectionCard(title = "Created by") {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconBubble(Icons.Rounded.Person, Brand.Orange)
                        Spacer(Modifier.width(14.dp))
                        Column {
                            Text("Saboor Hakimi", style = MaterialTheme.typography.titleLarge, color = Brand.Text)
                            Text("Developer · Shenaas", style = MaterialTheme.typography.bodyMedium, color = Brand.TextSecondary)
                        }
                    }
                    Spacer(Modifier.height(12.dp))
                    Text(
                        "Saboor brought Alarm From Hell to life at Shenaas, from the first idea to the app on your phone.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = Brand.TextSecondary,
                    )
                }

                SectionCard(title = "Have a similar project or idea?") {
                    Text(
                        "Reach out to Shenaas. We'd love to hear about it.",
                        style = MaterialTheme.typography.titleSmall,
                        color = Brand.Text,
                    )
                    Spacer(Modifier.height(14.dp))
                    HellButton(
                        "VISIT SHENAAS.COM",
                        onClick = { open(SHENAAS_URL) },
                        icon = Icons.Rounded.Language,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Spacer(Modifier.height(10.dp))
                    HellButton(
                        "GITHUB · SABOOR-HAKIMI",
                        onClick = { open(GITHUB_URL) },
                        icon = Icons.Rounded.Code,
                        kind = ButtonKind.Outline,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }

                SectionCard(title = "Privacy & credits") {
                    Row(verticalAlignment = Alignment.Top) {
                        Icon(Icons.Rounded.VerifiedUser, contentDescription = null, tint = Brand.Green, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(
                            "Camera and voice challenges run entirely on your phone. Nothing is uploaded, and there are no accounts.",
                            style = MaterialTheme.typography.bodySmall,
                            color = Brand.TextSecondary,
                        )
                    }
                    Spacer(Modifier.height(10.dp))
                    Text(
                        "Plus Jakarta Sans (SIL Open Font License). Object detection: MediaPipe EfficientDet-Lite2 (Apache 2.0) " +
                            "and Google ML Kit image labeling. Speech: your phone's on-device speech recognition.",
                        style = MaterialTheme.typography.bodySmall,
                        color = Brand.TextMuted,
                    )
                }
                Spacer(Modifier.height(24.dp))
            }
        }
    }
}
