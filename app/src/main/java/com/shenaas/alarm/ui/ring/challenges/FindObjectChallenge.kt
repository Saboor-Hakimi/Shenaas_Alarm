package com.shenaas.alarm.ui.ring.challenges

import android.hardware.Sensor
import android.os.SystemClock
import android.util.Size
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.core.resolutionselector.AspectRatioStrategy
import androidx.camera.core.resolutionselector.ResolutionSelector
import androidx.camera.core.resolutionselector.ResolutionStrategy
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.FlashlightOff
import androidx.compose.material.icons.rounded.FlashlightOn
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size as GeoSize
import androidx.compose.ui.graphics.ClipOp
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.shenaas.alarm.BuildConfig
import com.shenaas.alarm.challenge.ChallengePlanner
import com.shenaas.alarm.challenge.HuntTargets
import com.shenaas.alarm.challenge.Tuning
import com.shenaas.alarm.data.Prefs
import com.shenaas.alarm.model.ChallengeSpec
import com.shenaas.alarm.model.ChallengeType
import com.shenaas.alarm.ui.theme.Brand
import kotlinx.coroutines.delay
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt
import kotlin.random.Random

@Composable
fun FindObjectChallenge(spec: ChallengeSpec, cb: ChallengeCallbacks) {
    val context = LocalContext.current
    val available = remember { ChallengePlanner.isAvailable(context, ChallengeType.FIND_OBJECT) }
    if (!available) {
        LaunchedEffect(Unit) { cb.onUnavailable() }
        return
    }
    val lifecycleOwner = LocalLifecycleOwner.current
    val haptics = LocalHapticFeedback.current
    val density = LocalDensity.current
    val callbacks by rememberUpdatedState(cb)

    val random = remember { Random(spec.seed) }
    val shuffleRandom = remember { Random(spec.seed xor 0x5F3759DF) }
    val pool = remember { HuntTargets.poolFor(spec.difficulty.level, Prefs.huntPack) }
    val threshold = remember { Tuning.objectConfidence(spec) }
    val minArea = remember { Tuning.objectMinArea(spec) }
    val holdMs = remember { Tuning.objectHoldMs(spec).toFloat() }
    val lookAlikes = remember { Tuning.objectLookAlikes(spec) }
    val rejectScreens = remember { Tuning.objectRejectScreens(spec) }
    val startedAt = remember { SystemClock.elapsedRealtime() }

    var target by remember { mutableStateOf(pool.random(random)) }
    var shown by remember { mutableStateOf(target) }
    var shuffling by remember { mutableStateOf(true) }
    var rerolls by remember { mutableIntStateOf(Tuning.objectRerolls(spec)) }
    var frame by remember { mutableStateOf<SpottedFrame?>(null) }
    var evidence by remember { mutableStateOf(Evidence.NONE) }
    var progress by remember { mutableFloatStateOf(0f) }
    var lastFrameAt by remember { mutableLongStateOf(0L) }
    var lastCheatWarning by remember { mutableLongStateOf(0L) }
    var won by remember { mutableStateOf(false) }
    var showSwap by remember { mutableStateOf(false) }
    var camera by remember { mutableStateOf<Camera?>(null) }
    var torch by remember { mutableStateOf(false) }
    val viewfinder = remember { AtomicReference<ViewfinderSpec?>(null) }
    val pop = remember { Animatable(1f) }

    // Slot-machine shuffle before landing on the target, on start and after each reroll.
    LaunchedEffect(target) {
        shuffling = true
        progress = 0f
        repeat(10) { i ->
            shown = pool.random(shuffleRandom)
            delay(55L + i * 16)
        }
        shown = target
        pop.snapTo(1.3f)
        pop.animateTo(1f, spring(dampingRatio = Spring.DampingRatioMediumBouncy))
        haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
        shuffling = false
    }

    val previewView = remember { PreviewView(context).apply { scaleType = PreviewView.ScaleType.FILL_CENTER } }

    DisposableEffect(lifecycleOwner) {
        val executor = Executors.newSingleThreadExecutor()
        val spotter = ObjectSpotter(context)
        val main = ContextCompat.getMainExecutor(context)
        val gaveUp = AtomicBoolean(false)
        fun unavailable() {
            if (gaveUp.compareAndSet(false, true)) main.execute { callbacks.onUnavailable() }
        }
        val future = ProcessCameraProvider.getInstance(context)
        var provider: ProcessCameraProvider? = null
        var disposed = false
        future.addListener({
            if (disposed) return@addListener
            val cameraProvider = runCatching { future.get() }.getOrNull() ?: return@addListener unavailable()
            provider = cameraProvider
            val preview = Preview.Builder().build().also { it.setSurfaceProvider(previewView.surfaceProvider) }
            val analysis = ImageAnalysis.Builder()
                .setResolutionSelector(
                    ResolutionSelector.Builder()
                        .setAspectRatioStrategy(AspectRatioStrategy.RATIO_4_3_FALLBACK_AUTO_STRATEGY)
                        // High enough that the viewfinder crop still has detail after cropping.
                        .setResolutionStrategy(ResolutionStrategy(Size(1280, 960), ResolutionStrategy.FALLBACK_RULE_CLOSEST_HIGHER_THEN_LOWER))
                        .build(),
                )
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .setOutputImageFormat(ImageAnalysis.OUTPUT_IMAGE_FORMAT_RGBA_8888)
                .build()
            analysis.setAnalyzer(executor) { image ->
                if (!spotter.ensureReady()) {
                    image.close()
                    unavailable()
                    return@setAnalyzer
                }
                spotter.analyze(image, viewfinder.get())?.let { result -> main.execute { if (!disposed) frame = result } }
            }
            try {
                cameraProvider.unbindAll()
                camera = cameraProvider.bindToLifecycle(lifecycleOwner, CameraSelector.DEFAULT_BACK_CAMERA, preview, analysis)
            } catch (e: Exception) {
                unavailable()
            }
        }, main)
        onDispose {
            disposed = true
            provider?.unbindAll()
            executor.execute { spotter.close() }
            executor.shutdown()
        }
    }

    // Walking around looking for the thing counts as being awake.
    SensorEffect(Sensor.TYPE_LINEAR_ACCELERATION, rate = android.hardware.SensorManager.SENSOR_DELAY_NORMAL) { e ->
        if (sqrt(e.values[0] * e.values[0] + e.values[1] * e.values[1] + e.values[2] * e.values[2]) > 1.2f) cb.onActivity()
    }

    // Evidence accumulates: good frames fill the meter, bad frames drain it slowly instead of
    // resetting it, so one flickering detection doesn't throw away a solid lock.
    LaunchedEffect(frame) {
        val f = frame ?: return@LaunchedEffect
        val now = SystemClock.elapsedRealtime()
        val dt = if (lastFrameAt == 0L) 100L else (now - lastFrameAt).coerceIn(0L, 300L)
        lastFrameAt = now
        if (won || shuffling) return@LaunchedEffect
        val ev = ObjectMatcher.evidence(target, f, minArea, lookAlikes, rejectScreens)
        evidence = ev
        val good = ev.score >= threshold
        val before = progress
        progress = (progress + if (good) dt / holdMs else -dt / (holdMs * 1.5f)).coerceIn(0f, 1f)
        if (good) cb.onActivity()
        if (before == 0f && progress > 0f) haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
        if (progress >= 1f) {
            won = true
            haptics.performHapticFeedback(HapticFeedbackType.LongPress)
            cb.onWin(speedScore(45_000, now - startedAt))
            return@LaunchedEffect
        }
        if (rejectScreens && !good && now - lastCheatWarning > 6_000) {
            val ignoringScreens = ObjectMatcher.evidence(target, f, minArea, lookAlikes, rejectScreens = false)
            if (ignoringScreens.score >= threshold) {
                lastCheatWarning = now
                cb.onFail("That's a picture on a screen. Nice try. Find the real one.")
            }
        }
    }

    LaunchedEffect(rerolls) {
        if (rerolls == 0) {
            delay(60_000)
            showSwap = true
        }
    }
    LaunchedEffect(torch, camera) { camera?.cameraControl?.enableTorch(torch) }

    BoxWithConstraints(
        Modifier
            .fillMaxSize()
            .background(Color.Black),
    ) {
        val side = min(maxWidth.value * 0.8f, maxHeight.value * 0.46f).dp
        val sidePx = with(density) { side.roundToPx() }
        AndroidView(
            factory = { previewView },
            modifier = Modifier
                .fillMaxSize()
                .onSizeChanged { viewfinder.set(ViewfinderSpec(it.width, it.height, sidePx)) },
        )
        Viewfinder(frame, evidence, progress, threshold, side, Modifier.fillMaxSize())

        // Target, above the viewfinder.
        Column(
            Modifier
                .align(Alignment.TopCenter)
                .fillMaxWidth()
                .padding(top = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(shown.emoji, fontSize = 52.sp, modifier = Modifier.scale(pop.value))
            Text(shown.name, style = MaterialTheme.typography.headlineLarge, color = Color.White, textAlign = TextAlign.Center)
            Text(if (shuffling) "Picking your target…" else "Find this", style = MaterialTheme.typography.titleSmall, color = Brand.Green)
        }

        // Status and controls, below the viewfinder.
        Column(
            Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                when {
                    shuffling -> " "
                    progress > 0f -> "Gotcha. Hold steady… ${(progress * 100).toInt()}%"
                    else -> shown.hint
                },
                style = MaterialTheme.typography.titleSmall,
                color = Color.White,
                textAlign = TextAlign.Center,
            )
            if (BuildConfig.DEBUG) {
                frame?.let { DebugReadout(it, evidence) }
            }
            Spacer(Modifier.height(14.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(18.dp), verticalAlignment = Alignment.CenterVertically) {
                if (camera?.cameraInfo?.hasFlashUnit() == true) {
                    RoundButton(if (torch) Icons.Rounded.FlashlightOn else Icons.Rounded.FlashlightOff, if (torch) "Light off" else "Light on") {
                        torch = !torch
                        cb.onActivity()
                    }
                }
                if (rerolls > 0) {
                    RoundButton(Icons.Rounded.Refresh, "Something else ($rerolls left)", badge = rerolls) {
                        rerolls--
                        val others = pool.filter { it != target }
                        if (others.isNotEmpty()) target = others.random(random)
                        cb.onActivity()
                    }
                }
                if (showSwap) {
                    RoundButton(Icons.Rounded.Shuffle, "Swap challenge") { cb.onUnavailable() }
                }
            }
        }
    }
}

/** Dimmed surround, corner brackets that turn green as evidence builds, and a fill for the hold meter. */
@Composable
private fun Viewfinder(frame: SpottedFrame?, evidence: Evidence, progress: Float, threshold: Float, side: androidx.compose.ui.unit.Dp, modifier: Modifier) {
    Canvas(modifier) {
        val sidePx = side.toPx()
        val hole = Rect(Offset((size.width - sidePx) / 2, (size.height - sidePx) / 2), GeoSize(sidePx, sidePx))
        val corner = CornerRadius(24.dp.toPx())
        clipPath(Path().apply { addRoundRect(RoundRect(hole, corner)) }, clipOp = ClipOp.Difference) {
            drawRect(Color.Black.copy(alpha = 0.55f))
        }
        drawRoundRect(Brand.Green.copy(alpha = 0.35f * progress), hole.topLeft, hole.size, corner)

        // Where the detector thinks the object is, when a box backs up the evidence.
        val box = evidence.box
        if (frame != null && box != null && evidence.score >= threshold) {
            val scale = max(size.width / frame.width, size.height / frame.height)
            val dx = (size.width - frame.width * scale) / 2
            val dy = (size.height - frame.height * scale) / 2
            drawRoundRect(
                Brand.Green,
                Offset(box.left * scale + dx, box.top * scale + dy),
                GeoSize(box.width() * scale, box.height() * scale),
                CornerRadius(14.dp.toPx()),
                style = Stroke(3.dp.toPx()),
            )
        }

        val color = lerp(Color.White, Brand.Green, (evidence.score / threshold).coerceIn(0f, 1f))
        val len = 34.dp.toPx()
        val stroke = 5.dp.toPx()
        fun bracket(x: Float, y: Float, sx: Float, sy: Float) {
            drawLine(color, Offset(x, y), Offset(x + sx * len, y), stroke, StrokeCap.Round)
            drawLine(color, Offset(x, y), Offset(x, y + sy * len), stroke, StrokeCap.Round)
        }
        bracket(hole.left, hole.top, 1f, 1f)
        bracket(hole.right, hole.top, -1f, 1f)
        bracket(hole.left, hole.bottom, 1f, -1f)
        bracket(hole.right, hole.bottom, -1f, -1f)

        val c = hole.center
        drawCircle(color.copy(alpha = 0.9f), 18.dp.toPx(), c, style = Stroke(2.5.dp.toPx()))
        drawLine(color, c - Offset(8.dp.toPx(), 0f), c + Offset(8.dp.toPx(), 0f), 2.5.dp.toPx(), StrokeCap.Round)
        drawLine(color, c - Offset(0f, 8.dp.toPx()), c + Offset(0f, 8.dp.toPx()), 2.5.dp.toPx(), StrokeCap.Round)
    }
}

/** Debug builds only: what both recognizers see, for tuning on a real phone. */
@Composable
private fun DebugReadout(frame: SpottedFrame, evidence: Evidence) {
    val detections = frame.items.sortedByDescending { it.score }.take(3).joinToString { "${it.label} ${"%.2f".format(it.score)}" }
    val scene = frame.scene.entries.sortedByDescending { it.value }.take(3).joinToString { "${it.key} ${"%.2f".format(it.value)}" }
    Text(
        "ev ${"%.2f".format(evidence.score)} ${evidence.via}\ndet: $detections\nscene: $scene",
        style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace, fontSize = 11.sp),
        color = Color.White.copy(alpha = 0.8f),
        textAlign = TextAlign.Center,
        modifier = Modifier
            .padding(top = 6.dp)
            .clip(MaterialTheme.shapes.small)
            .background(Color.Black.copy(alpha = 0.5f))
            .padding(horizontal = 8.dp, vertical = 4.dp),
    )
}

@Composable
private fun RoundButton(icon: ImageVector, description: String, badge: Int? = null, onClick: () -> Unit) {
    Box {
        Box(
            Modifier
                .size(56.dp)
                .clip(CircleShape)
                .background(Color.White.copy(alpha = 0.16f))
                .clickable(onClickLabel = description, onClick = onClick),
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, contentDescription = description, tint = Color.White, modifier = Modifier.size(26.dp))
        }
        if (badge != null) {
            Box(
                Modifier
                    .align(Alignment.TopEnd)
                    .size(20.dp)
                    .clip(CircleShape)
                    .background(Brand.Orange),
                contentAlignment = Alignment.Center,
            ) {
                Text("$badge", style = MaterialTheme.typography.labelSmall, color = Color.White)
            }
        }
    }
}
