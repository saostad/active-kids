package com.activekids.pose

import android.graphics.Bitmap

/** One body point in normalized image coordinates (0..1, origin top-left of the upright camera image). */
data class Landmark(val x: Float, val y: Float, val visibility: Float)

/** A camera image together with the poses detected in it. The bitmap is never mutated after publishing. */
class PoseFrame(
    val bitmap: Bitmap,
    val poses: List<List<Landmark>>,
    val timestampMs: Long,
    val latencyMs: Long,
) {
    val width: Int get() = bitmap.width
    val height: Int get() = bitmap.height
}

/** MediaPipe pose landmark indices (33-point BlazePose topology). */
object PoseLandmarks {
    const val NOSE = 0
    const val LEFT_SHOULDER = 11
    const val RIGHT_SHOULDER = 12
    const val LEFT_ELBOW = 13
    const val RIGHT_ELBOW = 14
    const val LEFT_WRIST = 15
    const val RIGHT_WRIST = 16
    const val LEFT_INDEX = 19
    const val RIGHT_INDEX = 20
    const val LEFT_HIP = 23
    const val RIGHT_HIP = 24
    const val LEFT_KNEE = 25
    const val RIGHT_KNEE = 26
    const val LEFT_ANKLE = 27
    const val RIGHT_ANKLE = 28

    val TORSO = listOf(LEFT_SHOULDER, RIGHT_SHOULDER, LEFT_HIP, RIGHT_HIP)

    val CONNECTIONS = listOf(
        LEFT_SHOULDER to RIGHT_SHOULDER,
        LEFT_SHOULDER to LEFT_ELBOW, LEFT_ELBOW to LEFT_WRIST, LEFT_WRIST to LEFT_INDEX,
        RIGHT_SHOULDER to RIGHT_ELBOW, RIGHT_ELBOW to RIGHT_WRIST, RIGHT_WRIST to RIGHT_INDEX,
        LEFT_SHOULDER to LEFT_HIP, RIGHT_SHOULDER to RIGHT_HIP, LEFT_HIP to RIGHT_HIP,
        LEFT_HIP to LEFT_KNEE, LEFT_KNEE to LEFT_ANKLE,
        RIGHT_HIP to RIGHT_KNEE, RIGHT_KNEE to RIGHT_ANKLE,
    )
}
