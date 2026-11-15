package com.pes2017.recomp

import kotlin.math.hypot
import kotlin.math.max

/**
 * Where a swipe has to travel, and what it runs into.
 *
 * A direction bound to a button is not just "a flick anywhere": the thumb starts
 * on that button and leaves it along one of four lines. If the line ends under a
 * neighbour -- or off the edge of a phone held in landscape -- the gesture either
 * cannot complete or finishes on another control. The editor reports both, and
 * the default pad is checked against this before it ships.
 *
 * Pure geometry: no Android types, so the unit tests can walk every default
 * binding without a device.
 */
object GestureCorridor {

    /**
     * The run a flick is read over: the classifier's own minimum (18 dp) plus a
     * margin, so a direction is not called blocked just because it is short.
     */
    const val MIN_LENGTH_DP = 32f

    /** How far beside the line the thumb can stray and still be on it. */
    const val WIDTH_DP = 18f

    /** Nothing may finish closer than this to the screen's edge. */
    const val EDGE_MARGIN_DP = 8f

    /** Landscape gesture bars live at the bottom; corridors keep clear of them. */
    const val BOTTOM_INSET_DP = 16f

    /** The four directions, in the order a corridor is described. */
    val DIRECTIONS = arrayOf(Swipe.UP, Swipe.DOWN, Swipe.LEFT, Swipe.RIGHT)

    /** A unit vector pointing the way the swipe travels. */
    fun vector(direction: Swipe): FloatArray = when (direction) {
        Swipe.UP -> floatArrayOf(0f, -1f)
        Swipe.DOWN -> floatArrayOf(0f, 1f)
        Swipe.LEFT -> floatArrayOf(-1f, 0f)
        Swipe.RIGHT -> floatArrayOf(1f, 0f)
        Swipe.NONE -> floatArrayOf(0f, 0f)
    }

    /** How far the corridor reaches past the control's centre. */
    fun length(element: ControlElement): Float = MIN_LENGTH_DP

    /** The point the thumb has to reach, in the layout's dp space. */
    fun end(element: ControlElement, direction: Swipe, layout: ControlLayout): FloatArray {
        val start = centre(element, layout)
        val unit = vector(direction)
        val reach = length(element)
        return floatArrayOf(start[0] + unit[0] * reach, start[1] + unit[1] * reach)
    }

    private fun centre(element: ControlElement, layout: ControlLayout) =
        floatArrayOf(element.xFrac * layout.widthDp, element.yFrac * layout.heightDp)

    /**
     * What is wrong with this direction, as ids of controls the corridor crosses
     * and the code `"edge"` when its end is off the usable screen. An empty list
     * means the gesture has room; [Swipe.NONE] is always clear.
     */
    fun issues(
        element: ControlElement,
        direction: Swipe,
        layout: ControlLayout,
        slackDp: Float = 6f,
    ): List<String> {
        if (direction == Swipe.NONE) return emptyList()
        val start = centre(element, layout)
        val end = end(element, direction, layout)
        val problems = ArrayList<String>(3)

        if (end[0] < EDGE_MARGIN_DP || end[0] > layout.widthDp - EDGE_MARGIN_DP ||
            end[1] < EDGE_MARGIN_DP || end[1] > layout.heightDp - BOTTOM_INSET_DP
        ) {
            problems.add("edge")
        }

        for (other in layout.elements) {
            if (other === element) continue
            if (!other.visible) continue
            if (other.kind == ControlKind.LOOK) continue
            val otherCentre = centre(other, layout)
            // The thumb may stray [WIDTH_DP] off the line, but the control it
            // runs past still owns its own slack; the corridor does not add a
            // second half-width on top of that, or every diagonal in a diamond
            // would read as blocked.
            val clearance = other.sizeDp * 0.5f + slackDp
            if (distanceToSegment(otherCentre[0], otherCentre[1], start[0], start[1], end[0], end[1]) < clearance) {
                problems.add(other.id)
            }
        }
        return problems
    }

    /** Every bound direction that has a problem: direction to the ids it crosses. */
    fun problems(element: ControlElement, layout: ControlLayout): Map<Swipe, List<String>> {
        val result = LinkedHashMap<Swipe, List<String>>()
        for (direction in DIRECTIONS) {
            if (element.swipeBit(direction) == 0) continue
            val issues = issues(element, direction, layout)
            if (issues.isNotEmpty()) result[direction] = issues
        }
        return result
    }

    private fun distanceToSegment(px: Float, py: Float, ax: Float, ay: Float, bx: Float, by: Float): Float {
        val dx = bx - ax
        val dy = by - ay
        val lengthSquared = dx * dx + dy * dy
        if (lengthSquared <= 0.0001f) return hypot(px - ax, py - ay)
        val t = (((px - ax) * dx + (py - ay) * dy) / lengthSquared).coerceIn(0f, 1f)
        return hypot(px - (ax + t * dx), py - (ay + t * dy))
    }
}
