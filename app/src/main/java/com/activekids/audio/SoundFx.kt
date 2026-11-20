package com.activekids.audio

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import com.activekids.core.NoticeCenter
import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.sin

/** Small synthesized sound effects (no audio files needed). Audio follows HDMI to the TV automatically. */
class SoundFx(private val notices: NoticeCenter) {
    enum class Sfx { POP, TICK, GO, FINISH, LOSS, DRUM, NOTE_LOW, NOTE_HIGH, FREEZE }

    private val tracks = HashMap<Sfx, AudioTrack>()
    private var broken = false

    fun play(sfx: Sfx) {
        if (broken) return
        try {
            val track = tracks.getOrPut(sfx) { build(synth(sfx)) }
            track.stop()
            track.reloadStaticData()
            track.play()
        } catch (e: Exception) {
            broken = true
            notices.error("sound", "Game sounds stopped working. Games keep running without sound.", "audio", "play $sfx failed", e)
        }
    }

    fun release() {
        tracks.values.forEach { it.release() }
        tracks.clear()
    }

    private fun build(samples: ShortArray): AudioTrack {
        val track = AudioTrack.Builder()
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_GAME)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build(),
            )
            .setAudioFormat(
                AudioFormat.Builder()
                    .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                    .setSampleRate(SAMPLE_RATE)
                    .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                    .build(),
            )
            .setTransferMode(AudioTrack.MODE_STATIC)
            .setBufferSizeInBytes(samples.size * 2)
            .build()
        val written = track.write(samples, 0, samples.size)
        if (written != samples.size) {
            track.release()
            throw IllegalStateException("AudioTrack.write wrote $written of ${samples.size} samples")
        }
        return track
    }

    private fun synth(sfx: Sfx): ShortArray = when (sfx) {
        Sfx.POP -> chirp(900.0, 280.0, 90)
        Sfx.TICK -> tones(listOf(660.0 to 120))
        Sfx.GO -> tones(listOf(990.0 to 320))
        Sfx.FINISH -> tones(listOf(523.0 to 140, 659.0 to 140, 784.0 to 140, 1047.0 to 360))
        Sfx.LOSS -> tones(listOf(440.0 to 160, 330.0 to 220))
        Sfx.DRUM -> chirp(170.0, 48.0, 135)
        Sfx.NOTE_LOW -> tones(listOf(523.25 to 150))
        Sfx.NOTE_HIGH -> tones(listOf(783.99 to 150))
        Sfx.FREEZE -> chirp(830.0, 430.0, 190)
    }

    private fun chirp(from: Double, to: Double, ms: Int): ShortArray {
        val n = SAMPLE_RATE * ms / 1000
        var phase = 0.0
        return ShortArray(n) { i ->
            val t = i / n.toDouble()
            phase += 2 * PI * (from + (to - from) * t) / SAMPLE_RATE
            (sin(phase) * exp(-4 * t) * AMPLITUDE).toInt().toShort()
        }
    }

    private fun tones(notes: List<Pair<Double, Int>>): ShortArray {
        val out = ArrayList<Short>()
        for ((freq, ms) in notes) {
            val n = SAMPLE_RATE * ms / 1000
            for (i in 0 until n) {
                val t = i / n.toDouble()
                val envelope = minOf(1.0, t * 20) * (1 - t)
                out += (sin(2 * PI * freq * i / SAMPLE_RATE) * envelope * AMPLITUDE).toInt().toShort()
            }
        }
        return out.toShortArray()
    }

    private companion object {
        const val SAMPLE_RATE = 22050
        const val AMPLITUDE = 12000.0
    }
}
