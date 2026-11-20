package com.activekids.pose

import android.graphics.Bitmap
import android.graphics.Matrix
import android.hardware.camera2.CameraCharacteristics
import android.util.Size
import android.view.Surface
import androidx.activity.ComponentActivity
import androidx.camera.camera2.interop.Camera2CameraInfo
import androidx.camera.camera2.interop.ExperimentalCamera2Interop
import androidx.camera.core.Camera
import androidx.camera.core.CameraInfo
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.core.resolutionselector.ResolutionSelector
import androidx.camera.core.resolutionselector.ResolutionStrategy
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.content.ContextCompat
import com.activekids.core.NoticeCenter
import com.activekids.settings.AnalysisResolution
import com.activekids.settings.CameraChoice
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.util.concurrent.Executor

/** Opens the chosen camera with CameraX and hands upright RGBA bitmaps to [onBitmap] on [analysisExecutor]. */
class CameraController(
    private val activity: ComponentActivity,
    private val notices: NoticeCenter,
    private val analysisExecutor: Executor,
    private val onBitmap: (Bitmap) -> Unit,
) {
    data class Status(
        val active: Boolean = false,
        val label: String = "Camera not started",
        val zoomRatio: Float? = null,
        val resolution: String? = null,
    )

    private val _status = MutableStateFlow(Status())
    val status: StateFlow<Status> = _status

    private var bound: Pair<CameraChoice, AnalysisResolution>? = null
    private var frameErrorReported = false

    fun bind(choice: CameraChoice, resolution: AnalysisResolution) {
        if (bound == choice to resolution) return
        bound = choice to resolution
        _status.value = Status(label = "Starting camera…")
        val future = ProcessCameraProvider.getInstance(activity)
        future.addListener({
            try {
                bindWith(future.get(), choice, resolution)
                notices.clear(KEY_START)
            } catch (e: Exception) {
                bound = null
                _status.value = Status(label = "Camera failed to start")
                notices.error(
                    KEY_START,
                    "The camera could not start. Close other camera apps and reopen Active Kids.",
                    "camera",
                    "bindToLifecycle failed",
                    e,
                    mapOf("choice" to choice.name, "resolution" to resolution.name),
                )
            }
        }, ContextCompat.getMainExecutor(activity))
    }

    private fun bindWith(provider: ProcessCameraProvider, choice: CameraChoice, resolution: AnalysisResolution) {
        val (selector, label) = chooseCamera(provider, choice)

        val analysis = ImageAnalysis.Builder()
            .setResolutionSelector(
                ResolutionSelector.Builder()
                    .setResolutionStrategy(
                        ResolutionStrategy(
                            Size(resolution.width, resolution.height),
                            ResolutionStrategy.FALLBACK_RULE_CLOSEST_HIGHER_THEN_LOWER,
                        ),
                    )
                    .build(),
            )
            .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
            .setOutputImageFormat(ImageAnalysis.OUTPUT_IMAGE_FORMAT_RGBA_8888)
            .setTargetRotation(activity.display?.rotation ?: Surface.ROTATION_90)
            .build()
        analysis.setAnalyzer(analysisExecutor, ::analyze)

        provider.unbindAll()
        val camera = provider.bindToLifecycle(activity, selector, analysis)
        val actual = analysis.resolutionInfo?.resolution?.let { "${it.width}×${it.height}" }
        _status.value = Status(active = true, label = label, resolution = actual)
        if (choice == CameraChoice.REAR_WIDEST) zoomOutFully(camera, label, actual)
    }

    /** Picks the camera; for "widest", the rear camera with the shortest focal length. */
    @androidx.annotation.OptIn(ExperimentalCamera2Interop::class)
    private fun chooseCamera(provider: ProcessCameraProvider, choice: CameraChoice): Pair<CameraSelector, String> {
        val wantFront = choice == CameraChoice.FRONT
        val primary = if (wantFront) CameraSelector.DEFAULT_FRONT_CAMERA else CameraSelector.DEFAULT_BACK_CAMERA
        if (!provider.hasCamera(primary)) {
            val other = if (wantFront) CameraSelector.DEFAULT_BACK_CAMERA else CameraSelector.DEFAULT_FRONT_CAMERA
            notices.fallback(
                KEY_CHOICE,
                "The ${if (wantFront) "front" else "rear"} camera isn't available, so the other camera is used.",
                "camera", "open ${choice.name}", "no such camera", "opposite camera",
            )
            return other to if (wantFront) "Rear camera" else "Front camera"
        }
        notices.clear(KEY_CHOICE)
        if (choice != CameraChoice.REAR_WIDEST) return primary to choice.label

        val backCameras = provider.availableCameraInfos.filter { it.lensFacing == CameraSelector.LENS_FACING_BACK }
        val widest: CameraInfo? = backCameras.minByOrNull { info ->
            Camera2CameraInfo.from(info)
                .getCameraCharacteristic(CameraCharacteristics.LENS_INFO_AVAILABLE_FOCAL_LENGTHS)
                ?.minOrNull() ?: Float.MAX_VALUE
        }
        if (widest == null) return primary to "Rear camera"
        val selector = CameraSelector.Builder().addCameraFilter { infos -> infos.filter { it == widest } }.build()
        return selector to "Rear camera (widest lens)"
    }

    /** On Pixels the ultrawide is reached through zoom below 1×. If that's not offered, say so. */
    private fun zoomOutFully(camera: Camera, label: String, resolution: String?) {
        val minZoom = camera.cameraInfo.zoomState.value?.minZoomRatio ?: 1f
        if (minZoom >= 0.99f) {
            notices.fallback(
                KEY_ZOOM,
                "This camera doesn't offer an ultrawide view to apps, so the normal rear view is used. Stand further back.",
                "camera", "zoom below 1x", "minZoomRatio=$minZoom", "zoom 1x",
            )
            _status.value = Status(active = true, label = label, zoomRatio = 1f, resolution = resolution)
            return
        }
        val zoomFuture = camera.cameraControl.setZoomRatio(minZoom)
        zoomFuture.addListener({
            try {
                zoomFuture.get()
                notices.clear(KEY_ZOOM)
                _status.value = Status(active = true, label = label, zoomRatio = minZoom, resolution = resolution)
            } catch (e: Exception) {
                notices.fallback(
                    KEY_ZOOM,
                    "Couldn't switch to the ultrawide view, so the normal rear view is used.",
                    "camera", "setZoomRatio($minZoom)", e.javaClass.simpleName + ": " + e.message, "zoom 1x",
                )
                _status.value = Status(active = true, label = label, zoomRatio = 1f, resolution = resolution)
            }
        }, ContextCompat.getMainExecutor(activity))
    }

    private fun analyze(proxy: ImageProxy) {
        try {
            val raw = proxy.toBitmap()
            val degrees = proxy.imageInfo.rotationDegrees
            val upright = if (degrees == 0) {
                raw
            } else {
                Bitmap.createBitmap(raw, 0, 0, raw.width, raw.height, Matrix().apply { postRotate(degrees.toFloat()) }, true)
            }
            frameErrorReported = false
            onBitmap(upright)
        } catch (e: Exception) {
            if (!frameErrorReported) {
                frameErrorReported = true
                notices.error(KEY_FRAME, "Some camera frames could not be read.", "camera", "analyze frame failed", e)
            }
        } finally {
            proxy.close()
        }
    }

    private companion object {
        const val KEY_START = "camera-start"
        const val KEY_CHOICE = "camera-choice"
        const val KEY_ZOOM = "camera-zoom"
        const val KEY_FRAME = "camera-frame"
    }
}
