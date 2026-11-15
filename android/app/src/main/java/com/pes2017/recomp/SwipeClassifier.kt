package com.pes2017.recomp

import kotlin.math.abs
import kotlin.math.max

/** A direction a swipe can be read as. */
internal enum class Swipe { NONE, UP, DOWN, LEFT, RIGHT }

/**
 * Reads a finger's travel as one of four directions.
 *
 * This is the gesture a tap and a swipe share. A finger that lifts where it
 * landed is a tap and sends the control's normal action; a finger that ends up
 * far enough in one direction is a swipe and sends that direction's binding.
 * The two are one rule, not two gestures stacked on the same button: the
 * direction is measured from the thumb's whole travel, so a short, ordinary
 * drag counts and a long, charging hold can still turn into a swipe.
 *
 * The direction is decided when the finger lifts, from where it ended up rather
 * than where it wandered: jitter cannot change the answer mid-gesture, and the
 * same drag reads the same at any sampling rate. [peek] answers the same
 * question mid-gesture because the arrows and the floating action name are
 * drawn from it; a gesture that would not fire must not look as if it will.
 *
 * Free of Android types, so [SwipeClassifierTest] checks it without a device.
 */
internal class SwipeClassifier(
    /** How far the finger has to end up from where it started, in pixels. */
    private val minTravelPx: Float,
    /**
     * A drag longer than this is not a swipe. Zero (the pad's setting) means no
     * time limit: a player may hold a button to charge a shot and then drag to
     * choose the variant, and a clock running from the press would throw that
     * gesture away for being deliberate.
     */
    private val maxDurationMs: Long = 0L,
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
        // A time limit is opt-in. When one is set it only rejects drags that
        // were really slides; the pad leaves it at zero so charging a shot and
        // then picking a direction is one gesture.
        if (maxDurationMs > 0L && nowMs - gesture.startMs > maxDurationMs) return Swipe.NONE
        return read(gesture.lastX - gesture.startX, gesture.lastY - gesture.startY, gesture.dominance)
    }

    /**
     * What the gesture would read as right now, without ending it. This is the
     * drawing's feedback -- the arrow a thumb is heading for lights up under
     * it -- and it is deliberately the same rule as [end]: a gesture that
     * would not fire must not look as if it will.
     */
    fun peek(pointerId: Int): Swipe {
        val gesture = gestures[pointerId] ?: return Swipe.NONE
        return read(gesture.lastX - gesture.startX, gesture.lastY - gesture.startY, gesture.dominance)
    }

    /**
     * The one rule both [end] and [peek] use: below the travel threshold this is
     * a tap, and above it the axis that wins by the gesture's dominance is the
     * direction. Equal diagonals are left alone unless the caller asked for
     * nearest-axis reading (dominance 1), which the pad's buttons do.
     */
    private fun read(dx: Float, dy: Float, dominance: Float): Swipe {
        val ax = abs(dx)
        val ay = abs(dy)
        if (max(ax, ay) < minTravelPx) return Swipe.NONE
        return when {
            ax >= ay * dominance -> if (dx > 0f) Swipe.RIGHT else Swipe.LEFT
            ay >= ax * dominance -> if (dy > 0f) Swipe.DOWN else Swipe.UP
            else -> Swipe.NONE
        }
    }

    companion object {
        /**
         * The pad's usual rule: an axis has to win by nearly a third, so a flick
         * that went evenly up and right sends nothing rather than a guess. The
         * action buttons do not use it -- see [CARDINAL_DOMINANCE] -- because a
         * thumb does not drag in a perfect axis, and a dead diagonal band made
         * an ordinary swipe feel like it needed an extreme flick.
         */
        const val DEFAULT_DOMINANCE = 1.30f

        /**
         * The action buttons' and five-way buttons' rule: every arm owns a full
         * quarter turn.
         *
         * A tactical flick is a thumb moving off a 88 dp circle in a hurry, and
         * the arms are commands that cost the player a tactic if the wrong one
         * fires. At 1.30 a flick 42 degrees off the vertical was nothing at all,
         * and the thumb's next attempt was a harder diagonal, which resolved as
         * its *neighbour*. Letting each arm take everything inside 45 degrees of
         * it means the nearest arm always wins, and there is no band between the
         * arms where a gesture is read as something other than what the thumb
         * leaned towards. Pass, shoot, cross, through and the ten spares all use
         * it, so one drag rule covers every button the player can bind.
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
