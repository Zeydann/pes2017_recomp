package com.pes2017.recomp

import kotlin.math.abs
import kotlin.math.max

/** A direction a swipe can be read as. */
internal enum class Swipe { NONE, UP, DOWN, LEFT, RIGHT }

/**
 * Reads a finger's travel as one of four directions.
 *
 * This is the gesture for controls that have no bit of their own: a flick up for
 * a through ball, and nothing at all while the finger is travelling. Putting it
 * on an action button instead would mean the button's own bit had already
 * reached the guest by the time the direction was known, which is the duplicate
 * command this exists to avoid.
 *
 * The direction is decided when the finger lifts, from where it ended up rather
 * than where it wandered: jitter cannot change the answer mid-gesture, and the
 * same flick reads the same at any sampling rate. A thumb that went evenly up
 * and right gets neither, because guessing between them is worse than doing
 * nothing.
 *
 * Free of Android types, so [SwipeClassifierTest] checks it without a device.
 */
internal class SwipeClassifier(
    /** How far the finger has to end up from where it started, in pixels. */
    private val minTravelPx: Float,
    /** A drag longer than this is a slide across the pad, not a flick. */
    private val maxDurationMs: Long = 300L,
    /** How far the winning axis has to beat the other one. */
    private val dominance: Float = DEFAULT_DOMINANCE,
) {
    private class Gesture(
        val startX: Float,
        val startY: Float,
        val startMs: Long,
        /** How far the winning axis must beat the other for this gesture. */
        val dominance: Float,
    ) {
        var lastX = startX
        var lastY = startY
    }

    private val gestures = HashMap<Int, Gesture>()

    /**
     * Starts reading a gesture. [cardinalBias] is per gesture rather than per pad:
     * a five-way tactical button wants each arm to own a full quarter turn, while
     * an action button's swipes are content to say "that was diagonal, nothing".
     */
    fun begin(
        pointerId: Int,
        x: Float,
        y: Float,
        nowMs: Long,
        cardinalBias: Float = dominance,
    ) {
        gestures[pointerId] = Gesture(x, y, nowMs, cardinalBias)
    }

    fun move(pointerId: Int, x: Float, y: Float) {
        val gesture = gestures[pointerId] ?: return
        gesture.lastX = x
        gesture.lastY = y
    }

    /** The direction this pointer's gesture made, decided once, as it lifts. */
    fun end(pointerId: Int, nowMs: Long): Swipe {
        val gesture = gestures.remove(pointerId) ?: return Swipe.NONE
        if (nowMs - gesture.startMs > maxDurationMs) return Swipe.NONE

        val dx = gesture.lastX - gesture.startX
        val dy = gesture.lastY - gesture.startY
        val ax = abs(dx)
        val ay = abs(dy)
        if (max(ax, ay) < minTravelPx) return Swipe.NONE

        return when {
            ax >= ay * gesture.dominance -> if (dx > 0f) Swipe.RIGHT else Swipe.LEFT
            ay >= ax * gesture.dominance -> if (dy > 0f) Swipe.DOWN else Swipe.UP
            else -> Swipe.NONE
        }
    }

    /**
     * What the gesture would read as right now, without ending it. This is the
     * drawing's feedback -- the arrow a thumb is heading for lights up under
     * it -- and it is deliberately the same rule as [end]: a gesture that
     * would not fire must not look as if it will.
     */
    fun peek(pointerId: Int): Swipe {
        val gesture = gestures[pointerId] ?: return Swipe.NONE
        val dx = gesture.lastX - gesture.startX
        val dy = gesture.lastY - gesture.startY
        val ax = abs(dx)
        val ay = abs(dy)
        if (max(ax, ay) < minTravelPx) return Swipe.NONE
        return when {
            ax >= ay * gesture.dominance -> if (dx > 0f) Swipe.RIGHT else Swipe.LEFT
            ay >= ax * gesture.dominance -> if (dy > 0f) Swipe.DOWN else Swipe.UP
            else -> Swipe.NONE
        }
    }

    companion object {
        /**
         * The pad's usual rule: an axis has to win by nearly a third, so a flick
         * that went evenly up and right sends nothing rather than a guess.
         */
        const val DEFAULT_DOMINANCE = 1.30f

        /**
         * The five-way buttons' rule: every arm owns a full quarter turn.
         *
         * A tactical flick is a thumb moving off a 88 dp circle in a hurry, and
         * the arms are commands that cost the player a tactic if the wrong one
         * fires. At 1.30 a flick 42 degrees off the vertical was nothing at all,
         * and the thumb's next attempt was a harder diagonal, which resolved as
         * its *neighbour*. Letting each arm take everything inside 45 degrees of
         * it means the nearest arm always wins, and there is no band between the
         * arms where a gesture is read as something other than what the thumb
         * leaned towards.
         */
        const val CARDINAL_DOMINANCE = 1.0f
    }

    /** The gesture was taken away (another finger claimed it, or the pad hid). */
    fun cancel(pointerId: Int) {
        gestures.remove(pointerId)
    }

    fun busy(pointerId: Int): Boolean = gestures.containsKey(pointerId)

    fun clear() {
        gestures.clear()
    }
}
