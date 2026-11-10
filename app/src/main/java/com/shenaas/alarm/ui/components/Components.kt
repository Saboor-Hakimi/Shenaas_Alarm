package com.shenaas.alarm.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.DirectionsWalk
import androidx.compose.material.icons.rounded.Calculate
import androidx.compose.material.icons.rounded.CenterFocusStrong
import androidx.compose.material.icons.rounded.Keyboard
import androidx.compose.material.icons.rounded.Nightlife
import androidx.compose.material.icons.rounded.RecordVoiceOver
import androidx.compose.material.icons.rounded.SportsGymnastics
import androidx.compose.material.icons.rounded.Vibration
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.shenaas.alarm.R
import com.shenaas.alarm.model.ChallengeType
import com.shenaas.alarm.ui.theme.Brand
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

fun ChallengeType.icon(): ImageVector = when (this) {
    ChallengeType.WORD_BEAT -> Icons.Rounded.RecordVoiceOver
    ChallengeType.FIND_OBJECT -> Icons.Rounded.CenterFocusStrong
    ChallengeType.TYPING -> Icons.Rounded.Keyboard
    ChallengeType.MATH -> Icons.Rounded.Calculate
    ChallengeType.SHAKE -> Icons.Rounded.Vibration
    ChallengeType.STEPS -> Icons.AutoMirrored.Rounded.DirectionsWalk
    ChallengeType.JUMP -> Icons.Rounded.SportsGymnastics
    ChallengeType.DANCE -> Icons.Rounded.Nightlife
}

@Composable
fun ShenaasLogo(modifier: Modifier = Modifier, height: Dp = 22.dp) {
    Image(
        painter = painterResource(R.drawable.shenaas_logo),
        contentDescription = "Shenaas",
        contentScale = ContentScale.Fit,
        modifier = modifier.height(height),
    )
}

/** Night background with an ember glow bleeding in from the top. */
@Composable
fun GlowBackground(
    modifier: Modifier = Modifier,
    glow: Color = Brand.Orange,
    intensity: Float = 0.22f,
    content: @Composable BoxScope.() -> Unit,
) {
    Box(
        modifier
            .fillMaxSize()
            .background(Brand.Night)
            .drawBehind {
                drawRect(
                    Brush.radialGradient(
                        colors = listOf(glow.copy(alpha = intensity), Color.Transparent),
                        center = Offset(size.width / 2f, 0f),
                        radius = size.maxDimension * 0.7f,
                    ),
                )
            },
        content = content,
    )
}

enum class ButtonKind { Hellfire, Electric, White, Outline, Ghost }

@Composable
fun HellButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    kind: ButtonKind = ButtonKind.Hellfire,
    icon: ImageVector? = null,
    enabled: Boolean = true,
    height: Dp = 56.dp,
) {
    val background: Brush = when (kind) {
        ButtonKind.Hellfire -> Brand.Hellfire
        ButtonKind.Electric -> Brand.Electric
        ButtonKind.White -> SolidColor(Color.White)
        ButtonKind.Outline, ButtonKind.Ghost -> SolidColor(Color.Transparent)
    }
    val content = if (kind == ButtonKind.White) Brand.Night else Color.White
    Row(
        modifier
            .height(height)
            .alpha(if (enabled) 1f else 0.4f)
            .clip(CircleShape)
            .background(background)
            .then(if (kind == ButtonKind.Outline) Modifier.border(1.5.dp, Brand.Outline, CircleShape) else Modifier)
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 24.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        if (icon != null) {
            Icon(icon, contentDescription = null, tint = content, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(10.dp))
        }
        Text(text, style = MaterialTheme.typography.labelLarge, color = content, textAlign = TextAlign.Center)
    }
}

/** Must be held for [holdMillis] to fire, so a sleepy thumb can't trigger it by accident. */
@Composable
fun HoldButton(
    text: String,
    onHeld: () -> Unit,
    modifier: Modifier = Modifier,
    holdMillis: Int = 2000,
    enabled: Boolean = true,
    color: Color = Brand.ElectricBlue,
) {
    val progress = remember { Animatable(0f) }
    val scope = rememberCoroutineScope()
    val haptics = LocalHapticFeedback.current
    Box(
        modifier
            .height(56.dp)
            .alpha(if (enabled) 1f else 0.4f)
            .clip(CircleShape)
            .border(1.5.dp, color.copy(alpha = 0.7f), CircleShape)
            .pointerInput(enabled) {
                if (!enabled) return@pointerInput
                awaitEachGesture {
                    awaitFirstDown()
                    haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    val hold = scope.launch {
                        progress.animateTo(1f, tween((holdMillis * (1 - progress.value)).toInt(), easing = LinearEasing))
                        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                        onHeld()
                    }
                    waitForUpOrCancellation()
                    if (progress.value < 1f) {
                        hold.cancel()
                        scope.launch { progress.animateTo(0f, tween(250)) }
                    }
                }
            },
        contentAlignment = Alignment.Center,
    ) {
        Box(
            Modifier
                .align(Alignment.CenterStart)
                .fillMaxHeight()
                .fillMaxWidth(progress.value)
                .background(color.copy(alpha = 0.45f)),
        )
        Text(text, style = MaterialTheme.typography.labelLarge, color = Color.White, modifier = Modifier.padding(horizontal = 20.dp))
    }
}

@Composable
fun SectionCard(
    modifier: Modifier = Modifier,
    title: String? = null,
    subtitle: String? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.large)
            .background(Brand.Surface)
            .border(1.dp, Brand.Outline, MaterialTheme.shapes.large)
            .padding(20.dp),
    ) {
        if (title != null) {
            Text(title.uppercase(), style = MaterialTheme.typography.labelMedium, color = Brand.TextSecondary)
            if (subtitle != null) {
                Spacer(Modifier.height(2.dp))
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = Brand.TextMuted)
            }
            Spacer(Modifier.height(14.dp))
        }
        content()
    }
}

@Composable
fun SelectChip(
    text: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    color: Color = Brand.Orange,
) {
    Box(
        modifier
            .height(40.dp)
            .clip(CircleShape)
            .background(if (selected) color else Brand.SurfaceHigh)
            .border(1.dp, if (selected) color else Brand.Outline, CircleShape)
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, style = MaterialTheme.typography.labelLarge, color = if (selected) Color.White else Brand.TextSecondary, maxLines = 1, softWrap = false)
    }
}

@Composable
fun Badge(text: String, color: Color, modifier: Modifier = Modifier) {
    Box(
        modifier
            .clip(CircleShape)
            .background(color.copy(alpha = 0.16f))
            .border(1.dp, color.copy(alpha = 0.5f), CircleShape)
            .padding(horizontal = 10.dp, vertical = 4.dp),
    ) {
        Text(text.uppercase(), style = MaterialTheme.typography.labelSmall, color = color)
    }
}

@Composable
fun ShenaasSwitch(checked: Boolean, onCheckedChange: (Boolean) -> Unit, enabled: Boolean = true) {
    Switch(
        checked = checked,
        onCheckedChange = onCheckedChange,
        enabled = enabled,
        colors = SwitchDefaults.colors(
            checkedThumbColor = Color.White,
            checkedTrackColor = Brand.Orange,
            checkedBorderColor = Brand.Orange,
            uncheckedThumbColor = Brand.TextMuted,
            uncheckedTrackColor = Brand.SurfaceHighest,
            uncheckedBorderColor = Brand.Outline,
        ),
    )
}

@Composable
fun ToggleRow(
    title: String,
    subtitle: String?,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
) {
    Row(
        modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.small)
            .clickable { onCheckedChange(!checked) }
            .padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) {
            Icon(icon, contentDescription = null, tint = if (checked) Brand.Orange else Brand.TextMuted, modifier = Modifier.size(22.dp))
            Spacer(Modifier.width(14.dp))
        }
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleSmall, color = Brand.Text)
            if (subtitle != null) Text(subtitle, style = MaterialTheme.typography.bodySmall, color = Brand.TextSecondary)
        }
        Spacer(Modifier.width(12.dp))
        ShenaasSwitch(checked, onCheckedChange)
    }
}

/** One bar per challenge, lit up as they're beaten. */
@Composable
fun SegmentedProgress(total: Int, done: Int, modifier: Modifier = Modifier, active: Color = Brand.Orange) {
    Row(modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        repeat(total) { i ->
            val fill by animateFloatAsState(if (i < done) 1f else 0f, spring(stiffness = Spring.StiffnessLow), label = "segment")
            Box(
                Modifier
                    .weight(1f)
                    .height(6.dp)
                    .clip(CircleShape)
                    .background(Brand.SurfaceHighest),
            ) {
                Box(
                    Modifier
                        .fillMaxHeight()
                        .fillMaxWidth(fill)
                        .background(if (i == done) active.copy(alpha = 0.5f) else active),
                )
            }
        }
    }
}

@Composable
fun RingProgress(
    progress: Float,
    modifier: Modifier = Modifier,
    stroke: Dp = 14.dp,
    brush: Brush = Brand.Hellfire,
    track: Color = Brand.SurfaceHighest,
    content: @Composable BoxScope.() -> Unit = {},
) {
    val animated by animateFloatAsState(progress.coerceIn(0f, 1f), spring(stiffness = Spring.StiffnessMediumLow), label = "ring")
    Box(modifier, contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val width = stroke.toPx()
            drawArc(track, 0f, 360f, useCenter = false, style = Stroke(width))
            drawArc(brush, -90f, 360f * animated, useCenter = false, style = Stroke(width, cap = StrokeCap.Round))
        }
        content()
    }
}

/** A sarcastic banner that drops in for a few seconds whenever [messageId] changes. */
@Composable
fun SarcasmBanner(message: String?, messageId: Int, modifier: Modifier = Modifier, color: Color = Brand.HellRed) {
    var visible by remember { mutableStateOf(false) }
    LaunchedEffect(messageId) {
        if (message.isNullOrBlank()) return@LaunchedEffect
        visible = true
        delay(3200)
        visible = false
    }
    AnimatedVisibility(
        visible = visible && !message.isNullOrBlank(),
        enter = slideInVertically { -it } + fadeIn() + scaleIn(initialScale = 0.9f),
        exit = slideOutVertically { -it } + fadeOut(),
        modifier = modifier,
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                .clip(MaterialTheme.shapes.medium)
                .background(color)
                .padding(horizontal = 18.dp, vertical = 14.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(message.orEmpty(), style = MaterialTheme.typography.titleMedium, color = Color.White, textAlign = TextAlign.Center)
        }
    }
}

@Composable
fun rememberPulse(target: Float = 1.06f, periodMillis: Int = 550): Float {
    val transition = rememberInfiniteTransition(label = "pulse")
    val scale by transition.animateFloat(
        initialValue = 1f,
        targetValue = target,
        animationSpec = infiniteRepeatable(tween(periodMillis), RepeatMode.Reverse),
        label = "pulseScale",
    )
    return scale
}

@Composable
fun RowScope.StatTile(value: String, label: String, accent: Color = Brand.Orange) {
    Column(
        Modifier
            .weight(1f)
            .clip(MaterialTheme.shapes.medium)
            .background(Brand.Surface)
            .border(1.dp, Brand.Outline, MaterialTheme.shapes.medium)
            .padding(14.dp),
    ) {
        Text(value, style = MaterialTheme.typography.headlineSmall, color = accent)
        Text(label, style = MaterialTheme.typography.bodySmall, color = Brand.TextSecondary)
    }
}

@Composable
fun IconBubble(icon: ImageVector, tint: Color, modifier: Modifier = Modifier, size: Dp = 44.dp) {
    Box(
        modifier
            .size(size)
            .clip(CircleShape)
            .background(tint.copy(alpha = 0.15f)),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(size * 0.5f))
    }
}
