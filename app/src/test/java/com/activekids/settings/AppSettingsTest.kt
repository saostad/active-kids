package com.activekids.settings

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AppSettingsTest {
    @Test
    fun defaultsAreValid() {
        assertEquals(emptyList<String>(), AppSettings().validationErrors())
    }

    @Test
    fun outOfRangeValuesAreRejected() {
        assertTrue(AppSettings(maxPlayers = 9).validationErrors().single().contains("Players at once"))
        assertNotNull(SettingSpecs.roundSeconds.validate(5f))
        assertNotNull(SettingSpecs.bubblesPerSecond.validate(Float.NaN))
        assertNull(SettingSpecs.bubblesPerSecond.validate(5f))
    }

    @Test
    fun defaultsSitInsideTheirRanges() {
        val d = AppSettings()
        SettingSpecs.all.forEach { spec ->
            val v = spec.read(d)
            assertTrue("${spec.key} default $v outside ${spec.min}..${spec.max}", v in spec.min..spec.max)
        }
    }

    @Test
    fun snapRoundsToStepAndClamps() {
        assertEquals(1.2f, SettingSpecs.bubblesPerSecond.snap(1.23f), 1e-4f)
        assertEquals(300f, SettingSpecs.roundSeconds.snap(999f), 1e-4f)
        assertEquals(45f, SettingSpecs.roundSeconds.snap(50f), 1e-4f)
    }

    @Test
    fun specWriteRoundTrips() {
        SettingSpecs.all.forEach { spec ->
            val written = spec.write(AppSettings(), spec.max)
            assertEquals(spec.key, spec.max, spec.read(written), 1e-4f)
        }
    }
}
