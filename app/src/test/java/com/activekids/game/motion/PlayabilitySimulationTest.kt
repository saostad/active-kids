package com.activekids.game.motion

import com.activekids.game.GameKind
import com.activekids.game.GameSpace
import com.activekids.game.GameSummary
import com.activekids.game.ObjectiveLogic
import com.activekids.game.RoundResult
import com.activekids.game.bubblepop.BubblePopConfig
import com.activekids.game.bubblepop.BubblePopLogic
import com.activekids.game.bubblepop.HandPoint
import com.activekids.game.roundGoal
import com.activekids.settings.AppSettings
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs
import kotlin.math.sign
import kotlin.random.Random

class PlayabilitySimulationTest {
    private val settings = AppSettings(roundSeconds = 30)
    private val frames = longArrayOf(16, 33, 50, 24)
    private val body = MotionPose(
        Arm(Point(847f, 450f), Point(816f, 670f)),
        Arm(Point(1073f, 450f), Point(1104f, 670f)),
        Point(960f, 690f), 240f,
    )

    @Test
    fun bubblePopCanBeWonByReachingOnlyForBubblesWithinArmRange() {
        val cfg = BubblePopConfig(
            30_000, 0, settings.bubblesPerSecond, GameSpace.HEIGHT * settings.bubbleSizePercent / 100,
            GameSpace.HEIGHT * settings.handRadiusPercent / 100, ms(settings.bubbleRiseSeconds), 1,
        )
        val bubbles = BubblePopLogic(cfg, GameSpace.WIDTH, GameSpace.HEIGHT, Random(11))
        var hand = Point(860f, 670f)
        var choice = 0L
        var target = hand
        runToGoal(GameKind.BUBBLE_POP) { time, dt ->
            if (time >= choice) {
                target = bubbles.bubbles.filter { it.x in 700f..1220f && it.y in 355f..840f }
                    .minByOrNull { hand.distanceTo(Point(it.x, it.y)) }?.let { Point(it.x, it.y) } ?: body.hipCenter
                choice = time + 350
            }
            hand = Point(
                hand.x + (target.x - hand.x).coerceIn(-dt * 0.9f, dt * 0.9f),
                hand.y + (target.y - hand.y).coerceIn(-dt * 0.9f, dt * 0.9f),
            )
            bubbles.update(dt, listOf(HandPoint(0, hand.x, hand.y)))
            bubbles.scores.toList() to 0
        }
    }

    @Test
    fun starsFitWithinShoulderReachForPlayersOfDifferentSizes() {
        for (scale in listOf(0.55f, 1f, 1.35f)) {
            fun scaled(p: Point) = Point(960f + (p.x - 960f) * scale, 600f + (p.y - 600f) * scale)
            val person = body.copy(
                left = Arm(scaled(body.left.shoulder), null),
                right = Arm(scaled(body.right.shoulder), null),
                hipCenter = scaled(body.hipCenter),
                torsoHeight = body.torsoHeight * scale,
            )
            var points = 0
            var nextReach = 1200L
            runToGoal(GameKind.STAR_REACH) { time, _ ->
                if (time >= nextReach) {
                    val star = starTarget(person, points, settings.starSizePercent / 100f)
                    val left = star.center.x < 960f
                    val arm = if (left) person.left else person.right
                    assertTrue(arm.shoulder.distanceTo(star.center) <= person.torsoHeight * 1.12f)
                    val reach = arm.copy(hand = star.center.copy(x = star.center.x + 7f * scale))
                    val reached = if (left) person.copy(left = reach) else person.copy(right = reach)
                    if (star.reachedBy(reached)) points++
                    nextReach = time + 1200
                }
                listOf(points) to 0
            }
        }
    }

    @Test
    fun poseMatchCanBeWonEvenWithOneMissedPromptAndDelayedResponses() {
        val scores = IntArray(1)
        val calls = PoseMatchLogic(
            settings.posePromptSeconds * 1000L, ms(settings.poseHoldSeconds), settings.poseTolerancePercent / 100f, scores,
        )
        runToGoal(GameKind.POSE_MATCH) { time, dt ->
            val promptMs = settings.posePromptSeconds * 1000L
            val pose = when (calls.prompt) {
                PosePrompt.HANDS_UP -> poseWith(GateShape.ARCH.pose)
                PosePrompt.ARMS_OUT -> poseWith(GateShape.WINGS.pose)
                PosePrompt.HANDS_LOW -> body
            }
            val reacting = time % promptMs < 420 || time / promptMs == 1L
            calls.update(dt, if (reacting) emptyMap() else mapOf(0 to pose))
            scores.toList() to 0
        }
    }

    @Test
    fun trailDashCanRecoverFromACollisionAndAccumulatesMistakes() {
        val trail = TrailDashLogic(
            ms(settings.runnerApproachSeconds), ms(settings.runnerSpacingSeconds), settings.runnerStepPercent / 100f,
            settings.runnerJumpPercent / 100f, ms(settings.runnerJumpSeconds), Random(18),
        )
        val center = Point(950f, 690f)
        trail.calibrate(center, 240f)
        var score = 0
        runToGoal(GameKind.TRAIL_DASH) { _, dt ->
            val next = trail.obstacles.firstOrNull()
            val dodge = next != null && trail.collisions > 0 && trail.progress(next) > 0.77f
            val position = if (dodge && next.lane == TrailLane.CENTER) center.copy(x = 1120f) else center
            score += trail.advance(dt, position)
            listOf(score) to trail.collisions
        }
        assertEquals(1, trail.collisions)
    }

    @Test
    fun freezeDanceAcceptsSmallJitterAndBriefTrackingGapsButRejectsMoves() {
        val dance = FreezeDanceLogic(
            ms(settings.danceSeconds), ms(settings.freezeSeconds), settings.freezeTolerancePercent / 100f,
            ms(settings.freezeTrackingGraceSeconds),
        )
        var score = 0
        var previousWasMissing = false
        runToGoal(GameKind.FREEZE_DANCE) { time, dt ->
            val missing = !previousWasMissing && time % 991 < 42
            previousWasMissing = missing
            val hand = checkNotNull(body.left.hand)
            val smallJitter = hand.copy(x = hand.x + ((time % 9) - 4))
            val honest = body.copy(left = body.left.copy(hand = smallJitter))
            val moving = body.copy(left = body.left.copy(hand = hand.copy(x = hand.x - if (time % 500 < 220) 110 else 0)))
            val poses = if (missing) mapOf(1 to moving) else mapOf(0 to honest, 1 to moving)
            val winners = dance.advance(dt, poses)
            assertFalse(1 in winners)
            if (0 in winners) score++
            listOf(score, 0) to 0
        }
    }

    @Test
    fun penguinHasTimeForAFullWidthTurnAfterReactingToTheNextRow() {
        val play = settings.copy(roundSeconds = 60)
        for ((speed, travel) in listOf(1.5f to 2.8f, 0.5f to 2f, 2.5f to 5f)) {
            for (seed in 0..15) {
                val custom = play.copy(penguinSteerSpeed = speed, penguinTravelSeconds = travel)
                val ice = PenguinParadeLogic(ms(travel), custom.penguinLeanPercent / 100f, speed, Random(seed))
                var score = 0
                var lastArrival = -1L
                var reactAt = 0L
                runToGoal(GameKind.PENGUIN_PARADE, custom) { time, dt ->
                    val next = ice.rows.firstOrNull()
                    if (next != null && next.arrives != lastArrival) {
                        lastArrival = next.arrives
                        reactAt = time + 450
                    }
                    val error = (next?.fish ?: 0) - ice.position
                    val lean = if (time < reactAt || abs(error) < 0.12f) 0f else sign(error) * custom.penguinLeanPercent / 100f
                    score += ice.advance(dt, lean)
                    listOf(score) to ice.snowbanks
                }
            }
        }
    }

    @Test
    fun meteorShieldCanBeWonAsATeamUsingReachableInterceptZones() {
        val field = MeteorShieldLogic(ms(settings.meteorTravelSeconds), ms(settings.meteorSpacingSeconds),
            GameSpace.HEIGHT * settings.meteorRadiusPercent / 100f, Random(5))
        val zones = linkedMapOf(0 to (435f..655f), 1 to (1265f..1485f))
        val scores = IntArray(2)
        runToGoal(GameKind.METEOR_SHIELD) { time, dt ->
            val hands = field.meteors.filter { field.center(it).y in 370f..760f && time % 2000 > 330 }
                .groupBy { it.slot }.mapValues { (_, meteors) -> listOf(field.center(meteors.first()).copy(x = meteors.first().x + 15f)) }
            field.advance(dt, zones, hands).forEach { scores[it]++ }
            scores.toList() to field.baseHits
        }
        assertTrue(scores.all { it > 0 })
    }

    @Test
    fun orchestraCanBeWonWithReactionDelayAndMissedNotes() {
        val music = RobotOrchestraLogic(ms(settings.orchestraBeatSeconds), ms(settings.orchestraWindowSeconds))
        val scores = IntArray(2)
        runToGoal(GameKind.ROBOT_ORCHESTRA) { time, dt ->
            val press = List(2) { pad ->
                music.notes.any { it.pad == pad && it.beat % 3 != 1 && time - it.arrives in 215..310 }
            }
            music.advance(dt, press).forEach { scores[it.pad]++ }
            scores.toList() to 0
        }
        assertTrue(scores.all { it > 0 })
    }

    @Test
    fun shapeGateAndTreasureTeamCanRecoverAfterAPartnerMisses() {
        var gatesCleared = 0
        val gateMs = ms(settings.gateTravelSeconds)
        val gate = ShapeGateLogic(gateMs, settings.gateTolerancePercent / 100f)
        runToGoal(GameKind.SHAPE_GATE) { time, dt ->
            val first = gate.shape(0).pose
            val second = gate.shape(1).pose
            val ready = time % gateMs >= 550 || gate.progress > 0.9f
            val both = ready && gatesCleared > 0 || time > gateMs + 600
            if (gate.advance(dt, listOf(first.takeIf { ready }, second.takeIf { both }))) gatesCleared++
            listOf(gatesCleared, gatesCleared) to 0
        }
        val hunt = TreasureTeamLogic(ms(settings.treasureHoldSeconds))
        runToGoal(GameKind.TREASURE_TEAM) { time, dt ->
            val cycle = time % 2300
            val held = cycle in 450..1900 && (time > 2300 || cycle < 800)
            val grab = cycle in 1550..1750
            hunt.advance(dt, held, grab)
            listOf(hunt.finds, hunt.finds) to 0
        }
        assertEquals(hunt.finds % 2, hunt.leverPlayer)
    }

    @Test
    fun mirrorLeaderCanWinAShortRoundWithTheLongestBeatSetting() {
        val custom = settings.copy(roundSeconds = 15, mirrorBeatSeconds = 4f)
        val mirror = MirrorLeaderLogic(MirrorLeaderLogic.playableBeatMs(custom), custom.mirrorMatchPercent / 100f)
        val scores = IntArray(2)
        runToGoal(GameKind.MIRROR_LEADER, custom) { time, dt ->
            val reference = mirror.reference?.let { it.copy(left = it.left.copy(y = it.left.y + 0.07f)) }
            val gesture = GateShape.entries[mirror.step].pose
            val posing = time % MirrorLeaderLogic.playableBeatMs(custom) > 400 || mirror.remaining < 0.1f
            val observed = if (mirror.copying && posing) reference else null
            mirror.advance(dt, mapOf(0 to gesture.takeUnless { mirror.copying }, 1 to observed)).forEach { scores[it]++ }
            scores.toList() to 0
        }
    }

    @Test
    fun everyGameHasATimerLossAndHazardGamesCanLoseEarly() {
        GameKind.entries.forEach { kind ->
            val objective = ObjectiveLogic(kind.roundGoal(settings))
            objective.advance(GameSummary("Playing", 1, listOf(0)), 33)
            assertNull(kind.name, objective.result)
            objective.advance(GameSummary("Finished", 0, listOf(0)), 33)
            assertEquals(kind.name, RoundResult.TIME_OUT, objective.result)
        }
        for (kind in listOf(GameKind.TRAIL_DASH, GameKind.PENGUIN_PARADE, GameKind.METEOR_SHIELD)) {
            val objective = ObjectiveLogic(kind.roundGoal(settings))
            objective.advance(GameSummary("Playing", 20, listOf(0), settings.mistakeLimit - 1), 33)
            assertNull(kind.name, objective.result)
            objective.advance(GameSummary("Playing", 19, listOf(0), settings.mistakeLimit), 33)
            assertEquals(kind.name, RoundResult.TOO_MANY_MISTAKES, objective.result)
        }
    }

    @Test
    fun setupWaitDoesNotLoseButMidGameWaitHasAReturnDeadline() {
        val objective = ObjectiveLogic(GameKind.TREASURE_TEAM.roundGoal(settings))
        val waiting = GameSummary("Waiting for player", 24, listOf(0, 0))
        objective.advance(waiting, 30_000)
        assertNull(objective.result)
        objective.advance(waiting.copy(phase = "Playing"), 33)
        objective.advance(waiting, 7000)
        assertEquals(1L, objective.returnSecondsLeft)
        objective.advance(waiting.copy(phase = "Playing"), 33)
        assertNull(objective.returnSecondsLeft)
        objective.advance(waiting, 8000)
        assertEquals(RoundResult.PLAYER_LEFT, objective.result)
    }

    private fun runToGoal(
        kind: GameKind,
        config: AppSettings = settings,
        step: (time: Long, dt: Long) -> Pair<List<Int>, Int>,
    ) {
        val rules = kind.roundGoal(config)
        val objective = ObjectiveLogic(rules)
        val total = config.roundSeconds * 1000L
        var time = 0L
        var index = 0
        while (time < total && objective.result == null) {
            val dt = frames[index++ % frames.size].coerceAtMost(total - time)
            time += dt
            val (scores, mistakes) = step(time, dt)
            val seconds = ((total - time + 999) / 1000).toInt()
            objective.advance(GameSummary(if (seconds == 0) "Finished" else "Playing", seconds, scores, mistakes), dt)
        }
        assertEquals("${kind.title} at ${time}ms, score ${objective.points}/${rules.target}",
            RoundResult.WON, objective.result)
    }

    private fun poseWith(pattern: ArmPattern): MotionPose {
        fun hand(offset: Point) = Point(body.shoulderCenter.x + offset.x * body.torsoHeight,
            body.shoulderCenter.y + offset.y * body.torsoHeight)
        return body.copy(left = body.left.copy(hand = hand(pattern.left)), right = body.right.copy(hand = hand(pattern.right)))
    }

    private fun ms(seconds: Float) = (seconds * 1000).toLong()
}
