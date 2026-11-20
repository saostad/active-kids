package com.activekids.settings

import kotlin.math.roundToInt

enum class CameraChoice(val label: String) {
    REAR_WIDEST("Rear, widest (ultrawide)"),
    REAR("Rear, main"),
    FRONT("Front"),
}

enum class AnalysisResolution(val label: String, val width: Int, val height: Int) {
    SD("640×480 (faster)", 640, 480),
    HD("1280×720 (sharper on TV)", 1280, 720),
}

enum class PoseModel(val label: String, val assetName: String) {
    LITE("Lite (fastest)", "pose_landmarker_lite.task"),
    FULL("Full (more accurate)", "pose_landmarker_full.task"),
}

enum class Processor(val label: String) {
    GPU("GPU"),
    CPU("CPU"),
}

/** Every tunable that changes how the app behaves. Defaults live here; the Settings screen edits them. */
data class AppSettings(
    val camera: CameraChoice = CameraChoice.REAR_WIDEST,
    val resolution: AnalysisResolution = AnalysisResolution.HD,
    val model: PoseModel = PoseModel.FULL,
    val processor: Processor = Processor.GPU,
    val maxPlayers: Int = 3,
    val detectionConfidence: Float = 0.5f,
    val trackingConfidence: Float = 0.5f,
    val landmarkVisibility: Float = 0.5f,
    val roundSeconds: Int = 60,
    val countdownSeconds: Int = 3,
    val bubblesPerSecond: Float = 1.2f,
    val bubbleSizePercent: Float = 9f,
    val handRadiusPercent: Float = 5f,
    val bubbleRiseSeconds: Float = 6f,
    val starSizePercent: Float = 30f,
    val posePromptSeconds: Int = 5,
    val poseHoldSeconds: Float = 0.8f,
    val poseTolerancePercent: Float = 30f,
    val runnerApproachSeconds: Float = 3f,
    val runnerSpacingSeconds: Float = 1.8f,
    val runnerStepPercent: Float = 55f,
    val runnerJumpPercent: Float = 25f,
    val runnerJumpSeconds: Float = 0.6f,
    val danceSeconds: Float = 4f,
    val freezeSeconds: Float = 2f,
    val freezeTolerancePercent: Float = 22f,
    val freezeTrackingGraceSeconds: Float = 0.15f,
    val penguinLeanPercent: Float = 30f,
    val penguinTravelSeconds: Float = 2.8f,
    val penguinSteerSpeed: Float = 1.5f,
    val meteorTravelSeconds: Float = 3f,
    val meteorSpacingSeconds: Float = 1.5f,
    val meteorRadiusPercent: Float = 8f,
    val orchestraBeatSeconds: Float = 0.8f,
    val orchestraWindowSeconds: Float = 0.35f,
    val orchestraPadPercent: Float = 38f,
    val gateTravelSeconds: Float = 5f,
    val gateTolerancePercent: Float = 43f,
    val treasureHoldSeconds: Float = 0.6f,
    val treasureReachPercent: Float = 38f,
    val mirrorBeatSeconds: Float = 1.5f,
    val mirrorMatchPercent: Float = 45f,
    val goalRates: Map<String, Int> = emptyMap(),
    val mistakeLimit: Int = 4,
    val playerReturnSeconds: Int = 8,
    val cameraBrightnessPercent: Int = 45,
    val showSkeleton: Boolean = true,
    val mirrorView: Boolean = true,
    val soundOn: Boolean = true,
)

/** Describes one numeric setting: its label, allowed range and how to read/write it. */
class NumberSpec(
    val key: String,
    val label: String,
    val help: String,
    val min: Float,
    val max: Float,
    val step: Float,
    val unit: String = "",
    val decimals: Int = 0,
    val read: (AppSettings) -> Float,
    val write: (AppSettings, Float) -> AppSettings,
) {
    /** Number of in-between stops for a Material slider. */
    val sliderSteps: Int get() = ((max - min) / step).roundToInt() - 1

    fun validate(value: Float): String? = when {
        value.isNaN() || value.isInfinite() -> "$label must be a number"
        value < min - EPSILON || value > max + EPSILON -> "$label must be between ${format(min)} and ${format(max)}$unit"
        else -> null
    }

    fun snap(value: Float): Float = (min + ((value - min) / step).roundToInt() * step).coerceIn(min, max)

    fun format(value: Float): String = if (decimals == 0) value.roundToInt().toString() else "%.${decimals}f".format(value)

    private companion object {
        const val EPSILON = 1e-4f
    }
}

object SettingSpecs {
    val maxPlayers = NumberSpec(
        "maxPlayers", "Players at once", "How many people the camera tracks.",
        1f, 4f, 1f, read = { it.maxPlayers.toFloat() }, write = { s, v -> s.copy(maxPlayers = v.roundToInt()) },
    )
    val detectionConfidence = NumberSpec(
        "detectionConfidence", "Detection confidence", "Higher means fewer false people, but harder to detect.",
        0.1f, 0.9f, 0.05f, decimals = 2,
        read = { it.detectionConfidence }, write = { s, v -> s.copy(detectionConfidence = v) },
    )
    val trackingConfidence = NumberSpec(
        "trackingConfidence", "Tracking confidence", "How sure tracking must be before re-detecting a person.",
        0.1f, 0.9f, 0.05f, decimals = 2,
        read = { it.trackingConfidence }, write = { s, v -> s.copy(trackingConfidence = v) },
    )
    val landmarkVisibility = NumberSpec(
        "landmarkVisibility", "Body-point visibility", "Hide hands/joints the model is less sure about than this.",
        0.1f, 0.9f, 0.05f, decimals = 2,
        read = { it.landmarkVisibility }, write = { s, v -> s.copy(landmarkVisibility = v) },
    )
    val roundSeconds = NumberSpec(
        "roundSeconds", "Round length", "How long one game lasts.",
        15f, 300f, 15f, unit = " s", read = { it.roundSeconds.toFloat() }, write = { s, v -> s.copy(roundSeconds = v.roundToInt()) },
    )
    val countdownSeconds = NumberSpec(
        "countdownSeconds", "Countdown", "3-2-1 before the game starts.",
        0f, 10f, 1f, unit = " s", read = { it.countdownSeconds.toFloat() }, write = { s, v -> s.copy(countdownSeconds = v.roundToInt()) },
    )
    val bubblesPerSecond = NumberSpec(
        "bubblesPerSecond", "Bubbles per second", "How quickly new bubbles appear.",
        0.2f, 5f, 0.1f, decimals = 1, read = { it.bubblesPerSecond }, write = { s, v -> s.copy(bubblesPerSecond = v) },
    )
    val bubbleSizePercent = NumberSpec(
        "bubbleSizePercent", "Bubble size", "Bubble radius as a share of the screen height.",
        4f, 20f, 1f, unit = " %", read = { it.bubbleSizePercent }, write = { s, v -> s.copy(bubbleSizePercent = v) },
    )
    val handRadiusPercent = NumberSpec(
        "handRadiusPercent", "Hand reach", "How close a hand must be to pop (share of screen height). Bigger is easier.",
        2f, 15f, 1f, unit = " %", read = { it.handRadiusPercent }, write = { s, v -> s.copy(handRadiusPercent = v) },
    )
    val bubbleRiseSeconds = NumberSpec(
        "bubbleRiseSeconds", "Bubble float time", "Seconds for a bubble to float from bottom to top. Bigger is slower.",
        2f, 15f, 0.5f, unit = " s", decimals = 1, read = { it.bubbleRiseSeconds }, write = { s, v -> s.copy(bubbleRiseSeconds = v) },
    )
    val starSizePercent = NumberSpec(
        "starSizePercent", "Star size", "Star radius relative to your torso height. Bigger is easier.",
        15f, 45f, 5f, unit = " %", read = { it.starSizePercent }, write = { s, v -> s.copy(starSizePercent = v) },
    )
    val posePromptSeconds = NumberSpec(
        "posePromptSeconds", "Time per pose", "How often Pose Match shows a new pose.",
        3f, 12f, 1f, unit = " s",
        read = { it.posePromptSeconds.toFloat() }, write = { s, v -> s.copy(posePromptSeconds = v.roundToInt()) },
    )
    val poseHoldSeconds = NumberSpec(
        "poseHoldSeconds", "Hold time", "How long you must hold the pose to score a point.",
        0.2f, 2f, 0.1f, unit = " s", decimals = 1,
        read = { it.poseHoldSeconds }, write = { s, v -> s.copy(poseHoldSeconds = v) },
    )
    val poseTolerancePercent = NumberSpec(
        "poseTolerancePercent", "Pose tolerance", "How much your hand position can vary. Bigger is more forgiving.",
        15f, 45f, 5f, unit = " %",
        read = { it.poseTolerancePercent }, write = { s, v -> s.copy(poseTolerancePercent = v) },
    )
    val runnerApproachSeconds = NumberSpec(
        "runnerApproachSeconds", "Obstacle travel time", "How long obstacles take to reach you. Bigger is slower.",
        2f, 6f, 0.5f, unit = " s", decimals = 1,
        read = { it.runnerApproachSeconds }, write = { s, v -> s.copy(runnerApproachSeconds = v) },
    )
    val runnerSpacingSeconds = NumberSpec(
        "runnerSpacingSeconds", "Obstacle spacing", "Time between obstacles.",
        1f, 4f, 0.2f, unit = " s", decimals = 1,
        read = { it.runnerSpacingSeconds }, write = { s, v -> s.copy(runnerSpacingSeconds = v) },
    )
    val runnerStepPercent = NumberSpec(
        "runnerStepPercent", "Step distance", "How far you move sideways to change lanes, relative to torso height.",
        25f, 100f, 5f, unit = " %",
        read = { it.runnerStepPercent }, write = { s, v -> s.copy(runnerStepPercent = v) },
    )
    val runnerJumpPercent = NumberSpec(
        "runnerJumpPercent", "Jump height", "How much your hips must rise to register a jump, relative to torso height.",
        10f, 50f, 5f, unit = " %",
        read = { it.runnerJumpPercent }, write = { s, v -> s.copy(runnerJumpPercent = v) },
    )
    val runnerJumpSeconds = NumberSpec(
        "runnerJumpSeconds", "In-game jump time", "How long the runner stays in the air after a hop.",
        0.3f, 1.2f, 0.1f, unit = " s", decimals = 1,
        read = { it.runnerJumpSeconds }, write = { s, v -> s.copy(runnerJumpSeconds = v) },
    )
    val cameraBrightnessPercent = NumberSpec(
        "cameraBrightnessPercent", "Camera brightness", "How visible the camera picture is behind the game.",
        0f, 100f, 5f, unit = " %", read = { it.cameraBrightnessPercent.toFloat() },
        write = { s, v -> s.copy(cameraBrightnessPercent = v.roundToInt()) },
    )

    val tracking = listOf(maxPlayers, detectionConfidence, trackingConfidence, landmarkVisibility)
    val round = listOf(roundSeconds, countdownSeconds)
    val bubblePop = listOf(bubblesPerSecond, bubbleSizePercent, handRadiusPercent, bubbleRiseSeconds)
    val starReach = listOf(starSizePercent)
    val poseMatch = listOf(posePromptSeconds, poseHoldSeconds, poseTolerancePercent)
    val trailDash = listOf(runnerApproachSeconds, runnerSpacingSeconds, runnerStepPercent, runnerJumpPercent, runnerJumpSeconds)
    private fun gameNumber(
        key: String, label: String, help: String, range: ClosedFloatingPointRange<Float>, step: Float, unit: String,
        read: (AppSettings) -> Float, write: (AppSettings, Float) -> AppSettings,
    ) = NumberSpec(key, label, help, range.start, range.endInclusive, step, unit,
        decimals = if (step < 1f) 2 else 0, read = read, write = write)

    val partyGames = listOf(
        "Freeze Dance" to listOf(
            gameNumber("danceSeconds", "Music time", "How long players dance between freezes.", 2f..8f, 0.5f, " s",
                { it.danceSeconds }, { s, v -> s.copy(danceSeconds = v) }),
            gameNumber("freezeSeconds", "Freeze time", "How long players must hold still.", 1f..5f, 0.5f, " s",
                { it.freezeSeconds }, { s, v -> s.copy(freezeSeconds = v) }),
            gameNumber("freezeTolerancePercent", "Movement tolerance", "Allowed movement as a share of torso height.", 10f..50f, 1f, " %",
                { it.freezeTolerancePercent }, { s, v -> s.copy(freezeTolerancePercent = v) }),
            gameNumber("freezeTrackingGraceSeconds", "Tracking grace", "Brief gaps in hand tracking that still count as holding still.", 0f..0.4f, 0.05f, " s",
                { it.freezeTrackingGraceSeconds }, { s, v -> s.copy(freezeTrackingGraceSeconds = v) }),
        ),
        "Penguin Parade" to listOf(
            gameNumber("penguinLeanPercent", "Full steering lean", "Amount of lean needed for maximum steering.", 15f..60f, 5f, " %",
                { it.penguinLeanPercent }, { s, v -> s.copy(penguinLeanPercent = v) }),
            gameNumber("penguinTravelSeconds", "Slide travel time", "Higher values give more time to avoid snowbanks.", 2f..5f, 0.2f, " s",
                { it.penguinTravelSeconds }, { s, v -> s.copy(penguinTravelSeconds = v) }),
            gameNumber("penguinSteerSpeed", "Steering speed", "How fast the penguin moves sideways.", 0.5f..2.5f, 0.1f, " lanes/s",
                { it.penguinSteerSpeed }, { s, v -> s.copy(penguinSteerSpeed = v) }),
        ),
        "Meteor Shield" to listOf(
            gameNumber("meteorTravelSeconds", "Meteor travel time", "How long a meteor takes to reach the base.", 2f..6f, 0.5f, " s",
                { it.meteorTravelSeconds }, { s, v -> s.copy(meteorTravelSeconds = v) }),
            gameNumber("meteorSpacingSeconds", "Meteor spacing", "Time between incoming meteors.", 1f..4f, 0.25f, " s",
                { it.meteorSpacingSeconds }, { s, v -> s.copy(meteorSpacingSeconds = v) }),
            gameNumber("meteorRadiusPercent", "Meteor size", "Meteor radius as a share of the screen height.", 5f..12f, 1f, " %",
                { it.meteorRadiusPercent }, { s, v -> s.copy(meteorRadiusPercent = v) }),
        ),
        "Robot Orchestra" to listOf(
            gameNumber("orchestraBeatSeconds", "Time between beats", "How quickly new notes arrive.", 0.6f..1.6f, 0.1f, " s",
                { it.orchestraBeatSeconds }, { s, v -> s.copy(orchestraBeatSeconds = v) }),
            gameNumber("orchestraWindowSeconds", "Timing tolerance", "How early or late a tap can be and still score.", 0.15f..0.5f, 0.05f, " s",
                { it.orchestraWindowSeconds }, { s, v -> s.copy(orchestraWindowSeconds = v) }),
            gameNumber("orchestraPadPercent", "Pad size", "Pad radius relative to the player's torso height.", 20f..60f, 5f, " %",
                { it.orchestraPadPercent }, { s, v -> s.copy(orchestraPadPercent = v) }),
        ),
        "Shape Gate" to listOf(
            gameNumber("gateTravelSeconds", "Gate travel time", "How much time you have to make each shape.", 3f..9f, 0.5f, " s",
                { it.gateTravelSeconds }, { s, v -> s.copy(gateTravelSeconds = v) }),
            gameNumber("gateTolerancePercent", "Shape tolerance", "How closely hands need to fit the outline.", 20f..65f, 1f, " %",
                { it.gateTolerancePercent }, { s, v -> s.copy(gateTolerancePercent = v) }),
        ),
        "Treasure Team" to listOf(
            gameNumber("treasureHoldSeconds", "Lever hold time", "How long to hold the lever to unlock the chest.", 0.3f..1.5f, 0.1f, " s",
                { it.treasureHoldSeconds }, { s, v -> s.copy(treasureHoldSeconds = v) }),
            gameNumber("treasureReachPercent", "Target size", "Lever and chest radius relative to torso height.", 20f..60f, 5f, " %",
                { it.treasureReachPercent }, { s, v -> s.copy(treasureReachPercent = v) }),
        ),
        "Mirror Leader" to listOf(
            gameNumber("mirrorBeatSeconds", "Time per move", "Time the leader and followers get for each move.", 1f..4f, 0.25f, " s",
                { it.mirrorBeatSeconds }, { s, v -> s.copy(mirrorBeatSeconds = v) }),
            gameNumber("mirrorMatchPercent", "Match tolerance", "How closely hands need to match the leader's pose.", 20f..70f, 5f, " %",
                { it.mirrorMatchPercent }, { s, v -> s.copy(mirrorMatchPercent = v) }),
        ),
    )
    val display = listOf(cameraBrightnessPercent)
    val goals = listOf(
        NumberSpec(
            "mistakeLimit", "Mistakes allowed", "Collisions in Trail Dash, snowbanks in Penguin Parade, or hits to the moon base.",
            1f, 10f, 1f, read = { it.mistakeLimit.toFloat() }, write = { s, v -> s.copy(mistakeLimit = v.roundToInt()) },
        ),
        NumberSpec(
            "playerReturnSeconds", "Time to return", "How long a paused round waits for a required player to return to the camera.",
            3f, 30f, 1f, unit = " s", read = { it.playerReturnSeconds.toFloat() },
            write = { s, v -> s.copy(playerReturnSeconds = v.roundToInt()) },
        ),
    ) + GoalSetting.entries.map { it.spec }
    val all = tracking + round + bubblePop + starReach + poseMatch + trailDash + partyGames.flatMap { it.second } + goals + display
}

/** All validation problems, or an empty list when the settings are usable. */
fun AppSettings.validationErrors(): List<String> = SettingSpecs.all.mapNotNull { it.validate(it.read(this)) }
