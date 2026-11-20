package com.activekids.pose

import org.junit.Assert.assertEquals
import org.junit.Test

class FrameTransformTest {
    @Test
    fun centerCropsA4by3CameraInto16by9() {
        // 640x480 into 1920x1080: scale 3 (fill width), 1440 tall, so 180 cropped top and bottom.
        val t = FrameTransform(1920f, 1080f, 640f, 480f, mirror = false)
        assertEquals(3f, t.scale, 1e-4f)
        assertEquals(0f, t.mapX(0f), 1e-3f)
        assertEquals(1920f, t.mapX(1f), 1e-3f)
        assertEquals(-180f, t.mapY(0f), 1e-3f)
        assertEquals(540f, t.mapY(0.5f), 1e-3f)
    }

    @Test
    fun mirrorFlipsHorizontallyOnly() {
        val t = FrameTransform(1920f, 1080f, 1280f, 720f, mirror = true)
        assertEquals(1920f - 0.25f * 1920f, t.mapX(0.25f), 1e-3f)
        assertEquals(0.25f * 1080f, t.mapY(0.25f), 1e-3f)
    }
}
