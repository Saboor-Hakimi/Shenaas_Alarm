package com.shenaas.alarm.ui.ring.challenges

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Matrix
import android.graphics.RectF
import android.util.Log
import androidx.camera.core.ImageProxy
import com.google.android.gms.tasks.Tasks
import com.google.mediapipe.framework.image.BitmapImageBuilder
import com.google.mediapipe.tasks.core.BaseOptions
import com.google.mediapipe.tasks.vision.core.RunningMode
import com.google.mediapipe.tasks.vision.objectdetector.ObjectDetector
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.label.ImageLabeler
import com.google.mlkit.vision.label.ImageLabeling
import com.google.mlkit.vision.label.defaults.ImageLabelerOptions
import com.shenaas.alarm.challenge.HuntTarget
import com.shenaas.alarm.challenge.HuntTargets
import java.io.Closeable
import java.util.concurrent.TimeUnit
import kotlin.math.max
import kotlin.math.min

data class Spotted(val label: String, val score: Float, val box: RectF)

/**
 * What the recognizers saw in one upright camera frame of [width] x [height] pixels. [items] are
 * detector boxes in frame pixels; [scene] holds image-labeler confidences for the [roi] square
 * (the on-screen viewfinder).
 */
data class SpottedFrame(
    val width: Int,
    val height: Int,
    val roi: RectF,
    val items: List<Spotted>,
    val scene: Map<String, Float>,
)

/** Where the viewfinder square sits on screen, so the analyzer can crop the same region. */
data class ViewfinderSpec(val viewWidth: Int, val viewHeight: Int, val sidePx: Int)

/**
 * On-device recognition for Find the Object: MediaPipe object detection (EfficientDet-Lite2, COCO
 * labels) plus ML Kit image labeling. Nothing leaves the phone. Created and used on the camera
 * analysis thread.
 *
 * Each frame is cropped to the viewfinder so the object fills more of the model's input. The
 * detector alternates between that crop and the whole frame, which catch different things; the
 * labeler always looks at the crop.
 */
class ObjectSpotter(private val context: Context) : Closeable {
    private var detector: ObjectDetector? = null
    private var labeler: ImageLabeler? = null
    private var failed = false
    private var frameIndex = 0L

    /** Lazily loads both models; returns false if the detector can't be loaded on this device. */
    fun ensureReady(): Boolean {
        if (detector != null) return true
        if (failed) return false
        detector = try {
            val options = ObjectDetector.ObjectDetectorOptions.builder()
                .setBaseOptions(BaseOptions.builder().setModelAssetPath(MODEL).build())
                .setRunningMode(RunningMode.IMAGE)
                .setMaxResults(10)
                .setScoreThreshold(0.2f)
                .build()
            ObjectDetector.createFromOptions(context, options)
        } catch (e: Exception) {
            Log.e(TAG, "Object detector unavailable", e)
            failed = true
            null
        }
        // The labeler is a second opinion; the challenge still works without it.
        labeler = runCatching { ImageLabeling.getClient(ImageLabelerOptions.Builder().setConfidenceThreshold(0.3f).build()) }
            .onFailure { Log.w(TAG, "Image labeler unavailable", it) }
            .getOrNull()
        return detector != null
    }

    fun analyze(image: ImageProxy, viewfinder: ViewfinderSpec?): SpottedFrame? {
        try {
            val detector = detector ?: return null
            val raw = image.toBitmap()
            val rotation = image.imageInfo.rotationDegrees
            val upright = if (rotation == 0) {
                raw
            } else {
                Bitmap.createBitmap(raw, 0, 0, raw.width, raw.height, Matrix().apply { postRotate(rotation.toFloat()) }, true)
            }
            val roi = roiFor(upright.width, upright.height, viewfinder)
            val crop = Bitmap.createBitmap(upright, roi.left.toInt(), roi.top.toInt(), roi.width().toInt(), roi.height().toInt())

            val onCrop = frameIndex++ % 2 == 0L
            val result = detector.detect(BitmapImageBuilder(if (onCrop) crop else upright).build())
            val items = result.detections().mapNotNull { detection ->
                val best = detection.categories().maxByOrNull { it.score() } ?: return@mapNotNull null
                val box = RectF(detection.boundingBox())
                if (onCrop) box.offset(roi.left, roi.top)
                Spotted(best.categoryName(), best.score(), box)
            }
            val scene = labeler?.let { l ->
                runCatching { Tasks.await(l.process(InputImage.fromBitmap(crop, 0)), 2, TimeUnit.SECONDS) }
                    .getOrNull()
                    ?.associate { it.text to it.confidence }
            }.orEmpty()
            return SpottedFrame(upright.width, upright.height, roi, items, scene)
        } catch (e: Exception) {
            Log.w(TAG, "Frame analysis failed", e)
            return null
        } finally {
            image.close()
        }
    }

    /**
     * The frame region shown inside the viewfinder. The preview uses FILL_CENTER, so the frame is
     * scaled to cover the view and centered; the viewfinder is centered in the view.
     */
    private fun roiFor(width: Int, height: Int, spec: ViewfinderSpec?): RectF {
        val side = if (spec == null || spec.viewWidth == 0 || spec.viewHeight == 0) {
            min(width, height) * 0.6f
        } else {
            val scale = max(spec.viewWidth / width.toFloat(), spec.viewHeight / height.toFloat())
            (spec.sidePx / scale).coerceIn(64f, min(width, height).toFloat())
        }
        val left = (width - side) / 2f
        val top = (height - side) / 2f
        return RectF(left, top, left + side, top + side)
    }

    override fun close() {
        detector?.close()
        detector = null
        labeler?.close()
        labeler = null
    }

    private companion object {
        const val TAG = "ObjectSpotter"
        const val MODEL = "efficientdet_lite2.tflite"
    }
}

/** How strongly one frame shows the target, and where (when a detector box backs it up). */
data class Evidence(val score: Float, val box: RectF?, val via: String) {
    companion object {
        val NONE = Evidence(0f, null, "")
    }
}

/** Fuses detector and labeler output into one score per frame. Pure, so it's easy to reason about and tune. */
object ObjectMatcher {
    private const val CONTEXT_MIN = 0.5f
    private const val CONTEXT_BONUS = 0.1f
    private const val SCENE_PENALTY = 0.1f
    private const val LOOK_ALIKE_WEIGHT = 0.85f

    fun evidence(
        target: HuntTarget,
        frame: SpottedFrame,
        minArea: Float,
        allowLookAlikes: Boolean,
        rejectScreens: Boolean,
    ): Evidence {
        val roiArea = frame.roi.width() * frame.roi.height()
        fun bigEnough(s: Spotted) = s.box.width() * s.box.height() / roiArea >= minArea
        fun scene(labels: Set<String>) = labels.maxOfOrNull { frame.scene[it] ?: 0f } ?: 0f

        val inContext = scene(target.contextLabels) >= CONTEXT_MIN
        val candidates = buildList {
            frame.items.filter { it.label in target.detectorLabels && bigEnough(it) }.maxByOrNull { it.score }?.let {
                add(Evidence((it.score + if (inContext) CONTEXT_BONUS else 0f).coerceAtMost(1f), it.box, it.label))
            }
            val sceneScore = scene(target.sceneLabels)
            if (sceneScore > 0f) add(Evidence(sceneScore - SCENE_PENALTY, null, "scene"))
            if (allowLookAlikes && inContext) {
                frame.items.filter { it.label in target.lookAlikes && bigEnough(it) }.maxByOrNull { it.score }?.let {
                    add(Evidence(it.score * LOOK_ALIKE_WEIGHT, it.box, "${it.label}~"))
                }
            }
        }
        val best = candidates.maxByOrNull { it.score } ?: return Evidence.NONE
        if (rejectScreens && onScreen(best, frame, target)) return Evidence.NONE
        return best
    }

    /** True when the "object" is really a picture on a TV, laptop or phone held up to the camera. */
    fun onScreen(evidence: Evidence, frame: SpottedFrame, target: HuntTarget): Boolean {
        val screens = frame.items.filter { it.label in HuntTargets.screens && it.label !in target.detectorLabels }
        val box = evidence.box
        if (box != null) return screens.any { it.box.contains(box.centerX(), box.centerY()) }
        // A scene-only match: reject if a screen fills most of the viewfinder.
        val roiArea = frame.roi.width() * frame.roi.height()
        return screens.any { s ->
            val overlap = RectF(s.box).apply { if (!intersect(frame.roi)) setEmpty() }
            overlap.width() * overlap.height() / roiArea > 0.5f
        }
    }
}
