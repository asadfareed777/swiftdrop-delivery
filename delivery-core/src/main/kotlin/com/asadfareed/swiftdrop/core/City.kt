package com.asadfareed.swiftdrop.core

import java.util.PriorityQueue
import kotlin.math.atan2
import kotlin.math.hypot

/** A point on the city plane, in metres. y grows southwards (screen convention). */
data class Point(val x: Double, val y: Double) {
    fun distanceTo(o: Point): Double = hypot(o.x - x, o.y - y)
}

data class Node(val id: String, val position: Point)

/** Axis-aligned rectangle used for parks and blocks on the map. */
data class Area(val name: String, val left: Double, val top: Double, val right: Double, val bottom: Double)

/**
 * Street network as an undirected weighted graph, plus purely visual features
 * (parks, river) that the app draws underneath the streets.
 */
class CityMap(
    val nodes: Map<String, Node>,
    private val adjacency: Map<String, Set<String>>,
    val parks: List<Area>,
    val river: List<Point>,
    val width: Double,
    val height: Double,
) {
    val edges: List<Pair<Node, Node>> = adjacency.flatMap { (a, ns) ->
        ns.filter { a < it }.map { nodes.getValue(a) to nodes.getValue(it) }
    }

    fun neighbours(id: String): Set<String> = adjacency[id].orEmpty()

    fun node(id: String): Node = nodes.getValue(id)

    /** A* shortest path by street distance. Returns null when unreachable. */
    fun shortestPath(fromId: String, toId: String): Route? {
        val goal = node(toId).position
        val g = HashMap<String, Double>().apply { put(fromId, 0.0) }
        val prev = HashMap<String, String>()
        val open = PriorityQueue<Pair<String, Double>>(compareBy { it.second })
        open += fromId to node(fromId).position.distanceTo(goal)
        val closed = HashSet<String>()

        while (open.isNotEmpty()) {
            val (current, _) = open.poll()
            if (current == toId) break
            if (!closed.add(current)) continue
            val here = node(current).position
            for (next in neighbours(current)) {
                if (next in closed) continue
                val tentative = g.getValue(current) + here.distanceTo(node(next).position)
                if (tentative < (g[next] ?: Double.MAX_VALUE)) {
                    g[next] = tentative
                    prev[next] = current
                    open += next to tentative + node(next).position.distanceTo(goal)
                }
            }
        }
        if (fromId != toId && toId !in prev) return null
        val ids = generateSequence(toId) { prev[it] }.toList().asReversed()
        return Route(ids.map { node(it).position })
    }
}

data class CourierPosition(val point: Point, val bearing: Double)

/** A polyline with cumulative distances, so positions can be interpolated by distance travelled. */
class Route(val points: List<Point>) {
    private val cumulative: DoubleArray = DoubleArray(points.size).also { acc ->
        for (i in 1 until points.size) acc[i] = acc[i - 1] + points[i - 1].distanceTo(points[i])
    }

    val length: Double get() = cumulative.lastOrNull() ?: 0.0

    /** Position and heading after travelling [distance] metres (clamped to the route). */
    fun positionAt(distance: Double): CourierPosition {
        if (points.size == 1) return CourierPosition(points[0], 0.0)
        val d = distance.coerceIn(0.0, length)
        var i = 1
        while (i < points.lastIndex && cumulative[i] < d) i++
        val a = points[i - 1]
        val b = points[i]
        val segment = cumulative[i] - cumulative[i - 1]
        val t = if (segment == 0.0) 0.0 else (d - cumulative[i - 1]) / segment
        val p = Point(a.x + (b.x - a.x) * t, a.y + (b.y - a.y) * t)
        return CourierPosition(p, bearing(a, b))
    }

    /** Remaining distance from the point on the route closest to [p]. */
    fun remainingFrom(p: Point): Double {
        var best = Double.MAX_VALUE
        var bestAlong = 0.0
        for (i in 1 until points.size) {
            val a = points[i - 1]
            val b = points[i]
            val dx = b.x - a.x
            val dy = b.y - a.y
            val len2 = dx * dx + dy * dy
            val t = if (len2 == 0.0) 0.0 else (((p.x - a.x) * dx + (p.y - a.y) * dy) / len2).coerceIn(0.0, 1.0)
            val proj = Point(a.x + dx * t, a.y + dy * t)
            val dist = proj.distanceTo(p)
            if (dist < best) {
                best = dist
                bestAlong = cumulative[i - 1] + a.distanceTo(proj)
            }
        }
        return (length - bestAlong).coerceAtLeast(0.0)
    }

    companion object {
        /** Compass bearing in degrees: 0 = north (up), 90 = east. */
        fun bearing(a: Point, b: Point): Double {
            val deg = Math.toDegrees(atan2(b.x - a.x, -(b.y - a.y)))
            return (deg + 360) % 360
        }
    }
}

/**
 * Fictional demo city: a 9 × 13 street grid with 150 m blocks, a river crossed by two
 * bridges, a central park and a diagonal boulevard.
 */
object DemoCity {
    const val BLOCK = 150.0
    const val COLS = 9
    const val ROWS = 13

    fun id(row: Int, col: Int) = "r${row}c$col"

    val map: CityMap by lazy { build() }

    private fun build(): CityMap {
        val nodes = LinkedHashMap<String, Node>()
        for (r in 0 until ROWS) for (c in 0 until COLS) {
            nodes[id(r, c)] = Node(id(r, c), Point(c * BLOCK, r * BLOCK))
        }
        val adj = HashMap<String, MutableSet<String>>()
        fun link(a: String, b: String) {
            adj.getOrPut(a) { mutableSetOf() } += b
            adj.getOrPut(b) { mutableSetOf() } += a
        }

        val parkInterior = setOf(id(3, 4))
        val bridges = setOf(2, 6)
        for (r in 0 until ROWS) for (c in 0 until COLS) {
            val here = id(r, c)
            if (here in parkInterior) continue
            if (c + 1 < COLS && id(r, c + 1) !in parkInterior) link(here, id(r, c + 1))
            if (r + 1 < ROWS && id(r + 1, c) !in parkInterior) {
                val crossesRiver = r == 6
                if (!crossesRiver || c in bridges) link(here, id(r + 1, c))
            }
        }
        // Diagonal boulevard from the south-west corner towards the centre.
        listOf(12 to 0, 11 to 1, 10 to 2, 9 to 3, 8 to 4).zipWithNext().forEach { (a, b) ->
            link(id(a.first, a.second), id(b.first, b.second))
        }
        nodes.remove(id(3, 4))

        val riverY = 6.5 * BLOCK
        val river = (0..16).map { i ->
            val x = -60 + i * (COLS * BLOCK) / 14.0
            Point(x, riverY + 22 * kotlin.math.sin(i / 2.2))
        }
        val parks = listOf(
            Area("Central Park", 3 * BLOCK, 2 * BLOCK, 5 * BLOCK, 4 * BLOCK),
            Area("Riverside Green", 7 * BLOCK, 9 * BLOCK, 8 * BLOCK, 10 * BLOCK),
        )
        return CityMap(nodes, adj, parks, river, (COLS - 1) * BLOCK, (ROWS - 1) * BLOCK)
    }
}
