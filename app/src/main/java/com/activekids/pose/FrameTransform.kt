package com.activekids.pose

import kotlin.math.max

/**
 * Maps normalized camera coordinates into a target area using "center crop" (fill, crop the
 * overflow), optionally mirrored so players see themselves like in a mirror. The camera picture
 * and the body points must both go through the same transform so they line up.
 */
class FrameTransform(
    val viewWidth: Float,
    val viewHeight: Float,
    val sourceWidth: Float,
    val sourceHeight: Float,
    val mirror: Boolean,
) {
    val scale: Float = max(viewWidth / sourceWidth, viewHeight / sourceHeight)
    val offsetX: Float = (viewWidth - sourceWidth * scale) / 2f
    val offsetY: Float = (viewHeight - sourceHeight * scale) / 2f

    fun mapX(nx: Float): Float {
        val px = nx * sourceWidth * scale + offsetX
        return if (mirror) viewWidth - px else px
    }

    fun mapY(ny: Float): Float = ny * sourceHeight * scale + offsetY

    /** Where the (unmirrored) source image lands; mirror the canvas around the view centre to match [mapX]. */
    val destLeft get() = offsetX
    val destTop get() = offsetY
    val destRight get() = offsetX + sourceWidth * scale
    val destBottom get() = offsetY + sourceHeight * scale
}
