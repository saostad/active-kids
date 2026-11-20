package com.activekids.ui

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.activekids.app
import com.activekids.display.TvDisplayController
import com.activekids.pose.CameraController
import com.activekids.pose.PoseTracker
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

enum class CameraPermission { UNKNOWN, GRANTED, DENIED }

class MainActivity : ComponentActivity() {
    private lateinit var analysisExecutor: ExecutorService
    private lateinit var camera: CameraController
    private lateinit var tracker: PoseTracker
    private lateinit var tv: TvDisplayController
    private val permission = MutableStateFlow(CameraPermission.UNKNOWN)

    private val requestCamera = registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        permission.value = if (granted) CameraPermission.GRANTED else CameraPermission.DENIED
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        WindowInsetsControllerCompat(window, window.decorView).apply {
            hide(WindowInsetsCompat.Type.systemBars())
            systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }

        val app = app
        analysisExecutor = Executors.newSingleThreadExecutor { r -> Thread(r, "camera-analysis") }
        tracker = PoseTracker(this, app.notices, app.engine::submitFrame)
        // Runs on the analysis thread: (re)configure the tracker from current settings, then track.
        camera = CameraController(this, app.notices, analysisExecutor) { bitmap ->
            tracker.ensureConfigured(PoseTracker.Config.from(app.settings.settings.value))
            tracker.process(bitmap)
        }
        tv = TvDisplayController(this, app.engine, app.notices)

        setContent {
            ActiveKidsTheme {
                AppRoot(
                    app = app,
                    permission = permission,
                    cameraStatus = camera.status,
                    trackerStatus = tracker.status,
                    tvState = tv.state,
                    onRequestPermission = ::requestOrOpenSettings,
                )
            }
        }

        // The camera (re)binds whenever permission is granted or camera settings change.
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                combine(permission, app.settings.settings) { p, s -> p to s }.collect { (p, s) ->
                    if (p == CameraPermission.GRANTED) camera.bind(s.camera, s.resolution)
                }
            }
        }

        if (hasCameraPermission()) {
            permission.value = CameraPermission.GRANTED
        } else {
            requestCamera.launch(Manifest.permission.CAMERA)
        }
    }

    private fun hasCameraPermission() =
        ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED

    private fun requestOrOpenSettings() {
        if (shouldShowRequestPermissionRationale(Manifest.permission.CAMERA) || permission.value == CameraPermission.UNKNOWN) {
            requestCamera.launch(Manifest.permission.CAMERA)
        } else {
            // "Don't ask again" was chosen: only the system settings page can grant it now.
            startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", packageName, null)))
        }
    }

    override fun onStart() {
        super.onStart()
        tv.start()
    }

    override fun onResume() {
        super.onResume()
        // Returning from the system settings page after granting the camera there.
        if (permission.value == CameraPermission.DENIED && hasCameraPermission()) permission.value = CameraPermission.GRANTED
    }

    override fun onStop() {
        tv.stop()
        super.onStop()
    }

    override fun onDestroy() {
        analysisExecutor.execute { tracker.close() }
        analysisExecutor.shutdown()
        super.onDestroy()
    }
}
