package com.activekids.game.motion

import com.activekids.pose.FrameTransform
import com.activekids.pose.PoseLandmarks
import com.activekids.pose.TrackedPlayer
import kotlin.math.hypot

internal data class Point(val x: Float, val y: Float) {
    fun distanceTo(other: Point): Float = hypot(x - other.x, y - other.y)
}

internal data class Arm(val shoulder: Point, val hand: Point?)

internal data class MotionPose(val left: Arm, val right: Arm, val hipCenter: Point, val torsoHeight: Float)

internal val MotionPose.shoulderCenter: Point
    get() = Point((left.shoulder.x + right.shoulder.x) / 2f, (left.shoulder.y + right.shoulder.y) / 2f)

internal val MotionPose.lean: Float get() = (shoulderCenter.x - hipCenter.x) / torsoHeight

internal data class ArmPattern(val left: Point, val right: Point, val lean: Float) {
    fun matches(other: ArmPattern, tolerance: Float): Boolean =
        left.distanceTo(other.left) <= tolerance && right.distanceTo(other.right) <= tolerance &&
            kotlin.math.abs(lean - other.lean) <= tolerance
}

internal fun MotionPose.armPattern(): ArmPattern? {
    val leftHand = left.hand ?: return null
    val rightHand = right.hand ?: return null
    val center = shoulderCenter
    fun normalize(point: Point) = Point((point.x - center.x) / torsoHeight, (point.y - center.y) / torsoHeight)
    return ArmPattern(normalize(leftHand), normalize(rightHand), lean)
}

internal fun MotionPose.handsNear(point: Point, radius: Float): Boolean =
    left.hand?.distanceTo(point)?.let { it <= radius } == true ||
        right.hand?.distanceTo(point)?.let { it <= radius } == true

internal fun MotionPose.reachPoint(side: Int): Point {
    val shoulder = if (side < 0) left.shoulder else right.shoulder
    return Point(
        (shoulder.x + side * torsoHeight * 0.65f).coerceIn(135f, 1785f),
        (shoulder.y + torsoHeight * 0.25f).coerceIn(300f, 935f),
    )
}

internal fun TrackedPlayer.motionPose(transform: FrameTransform, visibility: Float): MotionPose? {
    fun point(index: Int): Point? = landmarks.getOrNull(index)
        ?.takeIf { it.visibility >= visibility && it.x.isFinite() && it.y.isFinite() }
        ?.let { Point(transform.mapX(it.x), transform.mapY(it.y)) }

    val leftShoulder = point(PoseLandmarks.LEFT_SHOULDER) ?: return null
    val rightShoulder = point(PoseLandmarks.RIGHT_SHOULDER) ?: return null
    val leftHip = point(PoseLandmarks.LEFT_HIP) ?: return null
    val rightHip = point(PoseLandmarks.RIGHT_HIP) ?: return null
    val hipCenter = Point((leftHip.x + rightHip.x) / 2f, (leftHip.y + rightHip.y) / 2f)
    val torsoHeight = hipCenter.y - (leftShoulder.y + rightShoulder.y) / 2f
    if (torsoHeight <= 0f) return null

    val left = Arm(leftShoulder, point(PoseLandmarks.LEFT_WRIST))
    val right = Arm(rightShoulder, point(PoseLandmarks.RIGHT_WRIST))
    // Sort by screen position so gestures work with either camera mirror setting.
    return if (leftShoulder.x <= rightShoulder.x) MotionPose(left, right, hipCenter, torsoHeight)
    else MotionPose(right, left, hipCenter, torsoHeight)
}
