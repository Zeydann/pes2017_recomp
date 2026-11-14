package com.pes2017.recomp

import kotlin.math.hypot

/**
 * The two gestures the pad has to remember between touch samples: the sprint
 * latch, and the fake shot. Both are arithmetic on numbers the overlay already
 * has, so they live here rather than in it, and a test can check every edge
 * without a phone.
 */

/**
 * Sprint from the movement stick's rim.
 *
 * One threshold would chatter: a thumb held exactly on it would flicker between
 * running and jogging every sample. The press point (0.92 of the raw throw) and
 * the release point (0.82) are therefore separate, and the state is latched
 * between them. The raw throw decides -- not the shaped one -- so the curve
 * cannot make a jog a sprint by squeezing the top of its range.
 *
 * [reset] is what every cancellation path calls: pause, focus loss, the pad
 * being hidden or edited. A sprint cannot outlive the thumb that started it.
 */
class SprintLatch(
    private val pressAt: Float = 0.92f,
    private val releaseAt: Float = 0.82f,
) {
    /** Whether the movement stick is running right now. */
    var active = false
        private set

    private val press = pressAt.coerceIn(0.1f, 1f)
    private val release = releaseAt.coerceIn(0f, press - 0.01f)

    /** One sample of the stick, from -1..1 on each axis. Returns [active]. */
    fun update(x: Float, y: Float): Boolean {
        val magnitude = hypot(x, y)
        active = if (active) magnitude >= release else magnitude >= press
        return active
    }

    fun reset() {
        active = false
    }
}

/**
 * The fake shot, as PES 2017 actually performs it.
 *
 * The title's own manual lists "cancel pass / shot" as B, X or Y followed by A:
 * while the shot's power gauge is showing, pressing pass cancels the shot and
 * the player feints. It is not a shot and then a pass -- that would be a real
 * shot followed by a real pass, which is exactly what the gesture must avoid.
 *
 * So the gesture is read while the thumb is still down: the press starts on
 * shoot, a drag towards pass arms it, and the caller presses pass at that
 * moment, *before* the lift, while shoot is still held. The lift then releases
 * both together and sends nothing extra.
 *
 * The drag has to end up within about 45 degrees of the line from shoot to pass
 * and to have travelled [minTravelPx]: a thumb that wandered sideways is not a
 * fake shot, and a tap never arms one.
 *
 * One instance per pointer, because two thumbs can hold two buttons.
 */
class FakeShotGesture(
    /** How far the thumb has to travel before the gesture arms, in pixels. */
    private val minTravelPx: Float,
    /** How close to the shoot -> pass line the drag has to be (cosine). */
    private val cosine: Float = 0.70f,
) {
    /** Whether this gesture has already pressed pass. */
    var armed = false
        private set

    /**
     * One move sample. Returns true on the sample that arms the gesture, which
     * is when the caller must press pass; every later sample returns false, so
     * one drag presses pass once.
     */
    fun moved(
        startX: Float, startY: Float, x: Float, y: Float,
        shootX: Float, shootY: Float, passX: Float, passY: Float,
    ): Boolean {
        if (armed) return false
        val towardX = passX - shootX
        val towardY = passY - shootY
        val toward = hypot(towardX, towardY)
        if (toward < 1f) return false
        val dx = x - startX
        val dy = y - startY
        val travel = hypot(dx, dy)
        if (travel < minTravelPx) return false
        val alignment = (dx * towardX + dy * towardY) / (travel * toward)
        if (alignment < cosine) return false
        armed = true
        return true
    }

    fun reset() {
        armed = false
    }
}
