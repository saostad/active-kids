package com.activekids.pose

import kotlin.math.hypot

data class TrackedPlayer(val slot: Int, val landmarks: List<Landmark>)

/**
 * MediaPipe does not give people stable IDs, and its result order changes from frame to frame.
 * This keeps each person on the same "player slot" (colour and score) by matching body centres
 * to where each slot was last seen. Slots are remembered while a person steps out of view, so a
 * child who walks back in near the same spot gets their score back.
 */
class PlayerTracker {
    private val lastSeen = HashMap<Int, Pair<Float, Float>>()

    fun reset() = lastSeen.clear()

    fun assign(poses: List<List<Landmark>>, maxSlots: Int): List<TrackedPlayer> {
        lastSeen.keys.removeAll { it >= maxSlots }
        val centres = poses.map(::centre)

        val poseToSlot = HashMap<Int, Int>()
        val usedSlots = HashSet<Int>()
        val candidates = buildList {
            centres.forEachIndexed { pose, c ->
                lastSeen.forEach { (slot, seen) -> add(Triple(pose, slot, hypot(c.first - seen.first, c.second - seen.second))) }
            }
        }.sortedBy { it.third }
        for ((pose, slot, _) in candidates) {
            if (pose !in poseToSlot && slot !in usedSlots) {
                poseToSlot[pose] = slot
                usedSlots += slot
            }
        }

        // Anyone left over takes a never-used slot, left to right.
        val newPoses = poses.indices.filter { it !in poseToSlot }.sortedBy { centres[it].first }
        val freshSlots = (0 until maxSlots).filter { it !in usedSlots && it !in lastSeen }
        newPoses.zip(freshSlots).forEach { (pose, slot) -> poseToSlot[pose] = slot }

        poseToSlot.forEach { (pose, slot) -> lastSeen[slot] = centres[pose] }
        return poseToSlot.entries.sortedBy { it.value }.map { TrackedPlayer(it.value, poses[it.key]) }
    }

    private fun centre(pose: List<Landmark>): Pair<Float, Float> {
        val torso = PoseLandmarks.TORSO.mapNotNull { pose.getOrNull(it) }
        val points = torso.ifEmpty { pose }
        if (points.isEmpty()) return 0.5f to 0.5f
        return points.map { it.x }.average().toFloat() to points.map { it.y }.average().toFloat()
    }
}
