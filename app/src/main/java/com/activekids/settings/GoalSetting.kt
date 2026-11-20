package com.activekids.settings

import kotlin.math.ceil
import kotlin.math.roundToInt

enum class GoalSetting(val title: String, private val defaultRate: Int) {
    BUBBLE_POP("Bubble Pop", 10),
    STAR_REACH("Star Reach", 8),
    POSE_MATCH("Pose Match", 5),
    TRAIL_DASH("Trail Dash", 11),
    FREEZE_DANCE("Freeze Dance", 4),
    PENGUIN_PARADE("Penguin Parade", 10),
    METEOR_SHIELD("Meteor Shield", 10),
    ROBOT_ORCHESTRA("Robot Orchestra", 16),
    SHAPE_GATE("Shape Gate", 5),
    TREASURE_TEAM("Treasure Team", 4),
    MIRROR_LEADER("Mirror Leader", 6);

    fun target(settings: AppSettings): Int = ceil(rate(settings) * settings.roundSeconds / 60.0).toInt()

    internal val spec get() = NumberSpec(
        key = "goal_$name",
        label = title,
        help = "Goal points per minute. Shorter rounds need fewer points.",
        min = 1f, max = 60f, step = 1f,
        read = { rate(it).toFloat() },
        write = { settings, value -> settings.copy(goalRates = settings.goalRates + (name to value.roundToInt())) },
    )

    private fun rate(settings: AppSettings) = settings.goalRates[name] ?: defaultRate
}
