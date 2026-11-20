package com.activekids.game

import android.graphics.Color

object Palette {
    /** One colour per player slot, chosen to stay distinct on a camera background. */
    private val players = intArrayOf(
        Color.rgb(56, 189, 248), // sky
        Color.rgb(250, 204, 21), // yellow
        Color.rgb(244, 114, 182), // pink
        Color.rgb(74, 222, 128), // green
    )
    private val bubbles = intArrayOf(
        Color.rgb(96, 165, 250),
        Color.rgb(167, 139, 250),
        Color.rgb(244, 114, 182),
        Color.rgb(52, 211, 153),
        Color.rgb(251, 191, 36),
        Color.rgb(248, 113, 113),
    )

    fun player(slot: Int): Int = players[slot.mod(players.size)]
    fun bubble(index: Int): Int = bubbles[index.mod(bubbles.size)]
}
