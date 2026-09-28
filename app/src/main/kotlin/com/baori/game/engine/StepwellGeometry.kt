package com.baori.game.engine

import com.baori.game.data.model.PathSegment
import com.baori.game.data.model.Vec3

/**
 * Small geometry helpers shared by the renderer and the game logic.
 *
 * Levels define geometry purely as segments between named nodes; nodes never
 * appear explicitly in the JSON. [nodePositions] derives their coordinates
 * from the first segment that mentions them.
 */
object StepwellGeometry {

    /**
     * The platform height of a segment: its shallower (higher) endpoint.
     * A segment counts as drowned only when the water surface stands above
     * this — i.e. when the whole run of the stair is under water. Deeper
     * stairs drown first, which keeps the reveal spatially readable.
     */
    fun platformYOf(segment: PathSegment): Float =
        maxOf(segment.start.y, segment.end.y)

    /**
     * Applies the water rule to a set of aligned segments: a segment is
     * walkable only when it is aligned AND not submerged (levels 4/5).
     * With no water plane this is the identity — levels 1–3 are unaffected.
     */
    fun walkableSegmentIds(
        segments: List<PathSegment>,
        alignedIds: Set<String>,
        water: WaterPlane?,
        angleDeg: Float,
    ): Set<String> {
        if (water == null || alignedIds.isEmpty()) return alignedIds
        val byId = segments.associateBy { it.id }
        return alignedIds.filterTo(LinkedHashSet()) { id ->
            val segment = byId[id] ?: return@filterTo false
            !water.isSubmerged(platformYOf(segment), angleDeg)
        }
    }

    /** Node id -> 3D position in well units, derived from the segments. */
    fun nodePositions(segments: List<PathSegment>): Map<String, Vec3> {
        val map = LinkedHashMap<String, Vec3>()
        for (segment in segments) {
            if (!map.containsKey(segment.a)) map[segment.a] = segment.start
            if (!map.containsKey(segment.b)) map[segment.b] = segment.end
        }
        return map
    }

    /**
     * Finds the walkable segment connecting two nodes, or null when the two
     * nodes are not currently connected by an aligned segment.
     */
    fun connectingSegment(
        segments: List<PathSegment>,
        walkableIds: Set<String>,
        fromNode: String,
        toNode: String,
    ): PathSegment? =
        segments.firstOrNull { segment ->
            segment.id in walkableIds &&
                ((segment.a == fromNode && segment.b == toNode) ||
                    (segment.b == fromNode && segment.a == toNode))
        }

    /**
     * Breadth-first path from [from] to [to] over the currently walkable
     * segments, or null when the goal is not reachable right now. The
     * GameViewModel uses this to decide when the girl may walk.
     */
    fun findPath(
        segments: List<PathSegment>,
        walkableIds: Set<String>,
        from: String,
        to: String,
    ): List<String>? {
        if (from == to) return listOf(from)
        val adjacency = HashMap<String, MutableList<String>>()
        for (segment in segments) {
            if (segment.id !in walkableIds) continue
            adjacency.getOrPut(segment.a) { mutableListOf() }.add(segment.b)
            adjacency.getOrPut(segment.b) { mutableListOf() }.add(segment.a)
        }
        val visited = mutableSetOf(from)
        val queue = ArrayDeque<List<String>>()
        queue.addLast(listOf(from))
        while (queue.isNotEmpty()) {
            val path = queue.removeFirst()
            val last = path.last()
            for (neighbor in adjacency[last].orEmpty()) {
                if (neighbor in visited) continue
                val nextPath = path + neighbor
                if (neighbor == to) return nextPath
                visited.add(neighbor)
                queue.addLast(nextPath)
            }
        }
        return null
    }

    /**
     * Edge-count distance from every node to [target] over ALL segments,
     * ignoring alignment — the well's real geography. Nodes that cannot
     * reach [target] at all (decoy dead ends) are absent from the map.
     */
    fun distancesTo(
        segments: List<PathSegment>,
        target: String,
    ): Map<String, Int> {
        val adjacency = HashMap<String, MutableList<String>>()
        for (segment in segments) {
            adjacency.getOrPut(segment.a) { mutableListOf() }.add(segment.b)
            adjacency.getOrPut(segment.b) { mutableListOf() }.add(segment.a)
        }
        val distances = HashMap<String, Int>()
        distances[target] = 0
        val queue = ArrayDeque<String>()
        queue.addLast(target)
        while (queue.isNotEmpty()) {
            val node = queue.removeFirst()
            for (neighbor in adjacency[node].orEmpty()) {
                if (neighbor in distances) continue
                distances[neighbor] = distances.getValue(node) + 1
                queue.addLast(neighbor)
            }
        }
        return distances
    }

    /**
     * Chooses the walk the girl should take RIGHT NOW, given that the player
     * can only walk on stone that is real at the current angle.
     *
     * Levels 2–5 need several rotations to finish — no single angle aligns
     * the whole route — so the walk contract is a chunk walk: advance along
     * the currently aligned graph as far as it makes progress toward the
     * goal, let the player rotate, then walk again. The target is the
     * reachable node that sits closest to the goal in the well's real
     * geography ([distancesTo]); decoy dead ends can never win because they
     * are disconnected from the goal and therefore have no distance.
     *
     * @return the node path to walk (including the start), or null when no
     *   walk from [from] makes progress right now.
     */
    fun chunkWalkTarget(
        segments: List<PathSegment>,
        walkableIds: Set<String>,
        from: String,
        to: String,
    ): List<String>? {
        if (from == to) return null
        val distances = distancesTo(segments, to)
        val fromDistance = distances[from] ?: return null // dead level: goal unreachable in well-space

        // Breadth-first over the currently walkable graph; BFS visits nodes
        // in edge order, so the first path found to a node is a shortest one.
        val paths = HashMap<String, List<String>>()
        paths[from] = listOf(from)
        val queue = ArrayDeque<String>()
        queue.addLast(from)
        while (queue.isNotEmpty()) {
            val node = queue.removeFirst()
            for (segment in segments) {
                if (segment.id !in walkableIds) continue
                val neighbor = when (node) {
                    segment.a -> segment.b
                    segment.b -> segment.a
                    else -> null
                } ?: continue
                if (neighbor in paths) continue
                paths[neighbor] = paths.getValue(node) + neighbor
                queue.addLast(neighbor)
            }
        }

        // Strictly closer to the goal wins; sorted iteration keeps the
        // choice deterministic when two nodes sit at the same distance.
        var best: List<String>? = null
        var bestDistance = fromDistance
        for ((node, path) in paths.entries.sortedBy { it.key }) {
            val distance = distances[node] ?: continue // dead end: never makes progress
            if (distance < bestDistance) {
                bestDistance = distance
                best = path
            }
        }
        return best?.takeIf { it.last() != from }
    }
}
