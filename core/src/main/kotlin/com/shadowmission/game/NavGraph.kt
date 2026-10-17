package com.shadowmission.game

import com.shadowmission.level.Stairs
import java.util.PriorityQueue
import kotlin.math.abs
import kotlin.math.hypot

sealed interface PathStep {
    /** Walk along [floor] to [toX]. */
    data class Walk(val floor: Int, val toX: Float) : PathStep

    /** Take [stairs] up or down. */
    data class Climb(val stairs: Stairs, val up: Boolean) : PathStep
}

/**
 * Finds routes through the building. A side-view building is small, so this is a tiny graph:
 * the start, the goal and both ends of every staircase, joined wherever two of them share
 * a stretch of floor with no wall or locked door in between.
 */
class NavGraph(private val building: Building) {

    private val level = building.level

    /** Index of the walkable stretch of [floor] that [x] belongs to. */
    fun areaOf(floor: Int, x: Float): Int =
        building.walls(floor).drop(1).dropLast(1).count { wall -> wall <= x && !building.canWalkThrough(floor, wall) }

    fun findPath(from: Position, to: Position): List<PathStep>? {
        if (level.roomAt(to.floor, to.x) == null) return null

        data class Node(val pos: Position, val stairs: Stairs? = null)
        val nodes = mutableListOf(Node(from), Node(to))
        level.stairs.forEach { s ->
            nodes += Node(Position(s.bottomFloor, s.x), s)
            nodes += Node(Position(s.topFloor, s.right), s)
        }

        fun cost(a: Node, b: Node): Float? = when {
            a.pos.floor == b.pos.floor ->
                if (areaOf(a.pos.floor, a.pos.x) == areaOf(b.pos.floor, b.pos.x)) abs(a.pos.x - b.pos.x) else null
            a.stairs != null && a.stairs === b.stairs -> hypot(a.stairs.width, level.floorHeight)
            else -> null
        }

        val dist = FloatArray(nodes.size) { Float.MAX_VALUE }
        val prev = IntArray(nodes.size) { -1 }
        dist[0] = 0f
        val queue = PriorityQueue<Int>(compareBy { dist[it] })
        queue += 0
        while (queue.isNotEmpty()) {
            val i = queue.poll()
            if (i == 1) break
            for (j in nodes.indices) {
                if (j == i) continue
                val c = cost(nodes[i], nodes[j]) ?: continue
                if (dist[i] + c < dist[j]) {
                    dist[j] = dist[i] + c
                    prev[j] = i
                    queue.remove(j)
                    queue += j
                }
            }
        }
        if (dist[1] == Float.MAX_VALUE) return null

        val order = generateSequence(1) { prev[it].takeIf { p -> p >= 0 } }.toList().reversed()
        val steps = mutableListOf<PathStep>()
        order.zipWithNext().forEach { (a, b) ->
            val na = nodes[a]
            val nb = nodes[b]
            if (na.pos.floor == nb.pos.floor) {
                if (na.pos.x != nb.pos.x) steps += PathStep.Walk(nb.pos.floor, nb.pos.x)
            } else {
                steps += PathStep.Climb(nb.stairs!!, up = nb.pos.floor > na.pos.floor)
            }
        }
        return steps
    }
}
