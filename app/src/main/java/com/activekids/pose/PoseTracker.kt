package com.activekids.pose

import android.content.Context
import android.graphics.Bitmap
import android.os.SystemClock
import com.activekids.core.NoticeCenter
import com.activekids.settings.AppSettings
import com.activekids.settings.PoseModel
import com.activekids.settings.Processor
import com.google.mediapipe.framework.image.BitmapExtractor
import com.google.mediapipe.framework.image.BitmapImageBuilder
import com.google.mediapipe.framework.image.MPImage
import com.google.mediapipe.tasks.core.BaseOptions
import com.google.mediapipe.tasks.core.Delegate
import com.google.mediapipe.tasks.vision.core.RunningMode
import com.google.mediapipe.tasks.vision.poselandmarker.PoseLandmarker
import com.google.mediapipe.tasks.vision.poselandmarker.PoseLandmarkerResult
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * Wraps MediaPipe's Pose Landmarker in live-stream mode. All methods except [status] must be
 * called on the single camera-analysis thread, so the landmarker is never used concurrently.
 */
class PoseTracker(
    private val context: Context,
    private val notices: NoticeCenter,
    private val onFrame: (PoseFrame) -> Unit,
) {
    data class Config(
        val model: PoseModel,
        val processor: Processor,
        val maxPlayers: Int,
        val detectionConfidence: Float,
        val trackingConfidence: Float,
    ) {
        companion object {
            fun from(s: AppSettings) = Config(s.model, s.processor, s.maxPlayers, s.detectionConfidence, s.trackingConfidence)
        }
    }

    data class Status(
        val ready: Boolean = false,
        val model: PoseModel? = null,
        val processorInUse: Processor? = null,
        val failed: Boolean = false,
    )

    private val _status = MutableStateFlow(Status())
    val status: StateFlow<Status> = _status

    private var landmarker: PoseLandmarker? = null
    private var activeConfig: Config? = null
    private var lastTimestampMs = 0L
    private var detectErrorReported = false

    /** Rebuilds the landmarker only when the config changed. A failed config is not retried until it changes. */
    fun ensureConfigured(config: Config) {
        if (config == activeConfig) return
        close()
        activeConfig = config
        landmarker = buildWithFallback(config)
    }

    fun process(bitmap: Bitmap) {
        val lm = landmarker ?: return
        var ts = SystemClock.uptimeMillis()
        if (ts <= lastTimestampMs) ts = lastTimestampMs + 1 // live-stream mode requires strictly increasing timestamps
        lastTimestampMs = ts
        try {
            lm.detectAsync(BitmapImageBuilder(bitmap).build(), ts)
        } catch (e: RuntimeException) {
            reportDetectError("detectAsync threw", e)
        }
    }

    fun close() {
        try {
            landmarker?.close()
        } catch (e: RuntimeException) {
            notices.error("tracker-close", "Body tracking did not shut down cleanly.", "pose", "PoseLandmarker.close failed", e)
        }
        landmarker = null
        activeConfig = null
        _status.value = Status()
    }

    private fun buildWithFallback(config: Config): PoseLandmarker? {
        val context = mapOf("model" to config.model.name, "processor" to config.processor.name, "players" to config.maxPlayers)
        try {
            return build(config, config.processor).also {
                notices.clear(KEY_PROCESSOR)
                notices.clear(KEY_START)
                _status.value = Status(ready = true, model = config.model, processorInUse = config.processor)
            }
        } catch (e: Exception) {
            if (config.processor != Processor.GPU) {
                failStart(e, context)
                return null
            }
            notices.fallback(
                key = KEY_PROCESSOR,
                userMessage = "GPU body tracking could not start, so the CPU is used (slower).",
                area = "pose",
                attempted = "PoseLandmarker on GPU",
                reason = e.javaClass.simpleName + ": " + e.message,
                fallback = "CPU delegate",
                context = context,
            )
        }
        return try {
            build(config, Processor.CPU).also {
                notices.clear(KEY_START)
                _status.value = Status(ready = true, model = config.model, processorInUse = Processor.CPU)
            }
        } catch (e: Exception) {
            failStart(e, context)
            null
        }
    }

    private fun failStart(e: Exception, context: Map<String, Any?>) {
        notices.error(
            KEY_START,
            "Body tracking could not start. Try the Lite model or CPU in Settings.",
            "pose",
            "PoseLandmarker creation failed",
            e,
            context,
        )
        _status.value = Status(failed = true)
    }

    private fun build(config: Config, processor: Processor): PoseLandmarker {
        val base = BaseOptions.builder()
            .setModelAssetPath(config.model.assetName)
            .setDelegate(if (processor == Processor.GPU) Delegate.GPU else Delegate.CPU)
            .build()
        val options = PoseLandmarker.PoseLandmarkerOptions.builder()
            .setBaseOptions(base)
            .setRunningMode(RunningMode.LIVE_STREAM)
            .setNumPoses(config.maxPlayers)
            .setMinPoseDetectionConfidence(config.detectionConfidence)
            .setMinPosePresenceConfidence(config.detectionConfidence)
            .setMinTrackingConfidence(config.trackingConfidence)
            .setResultListener(::onResult)
            .setErrorListener { e -> reportDetectError("result listener error", e) }
            .build()
        return PoseLandmarker.createFromOptions(context, options)
    }

    private fun onResult(result: PoseLandmarkerResult, input: MPImage) {
        val bitmap = try {
            BitmapExtractor.extract(input)
        } catch (e: IllegalArgumentException) {
            reportDetectError("could not read frame bitmap", e)
            return
        }
        val poses = result.landmarks().map { pose ->
            pose.map { Landmark(it.x(), it.y(), it.visibility().orElse(1f)) }
        }
        if (detectErrorReported) {
            detectErrorReported = false
            notices.clear(KEY_DETECT)
        }
        onFrame(PoseFrame(bitmap, poses, result.timestampMs(), SystemClock.uptimeMillis() - result.timestampMs()))
    }

    /** Reported once per failure streak so a broken frame loop doesn't flood the log. */
    private fun reportDetectError(detail: String, e: Throwable) {
        if (detectErrorReported) return
        detectErrorReported = true
        notices.error(KEY_DETECT, "Body tracking hit an error on some frames.", "pose", detail, e)
    }

    private companion object {
        const val KEY_PROCESSOR = "tracker-processor"
        const val KEY_START = "tracker-start"
        const val KEY_DETECT = "tracker-detect"
    }
}
