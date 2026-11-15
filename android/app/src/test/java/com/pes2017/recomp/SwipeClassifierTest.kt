package com.pes2017.recomp

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The four-way swipe: which direction a flick was, and which ones are refused
 * rather than guessed.
 */
class SwipeClassifierTest {

    private fun classifier(minTravelPx: Float = 54f) = SwipeClassifier(minTravelPx)

    /** A straight flick from the middle, in one move, lifted at [atMs]. */
    private fun flick(
        classifier: SwipeClassifier,
        dx: Float,
        dy: Float,
        atMs: Long = 120L,
    ): Swipe {
        classifier.begin(1, 100f, 100f, 0L)
        classifier.move(1, 100f + dx, 100f + dy)
        return classifier.end(1, atMs)
    }

    @Test
    fun theFourDirectionsAreRead() {
        assertEquals(Swipe.UP, flick(classifier(), 0f, -80f))
        assertEquals(Swipe.DOWN, flick(classifier(), 0f, 80f))
        assertEquals(Swipe.LEFT, flick(classifier(), -80f, 0f))
        assertEquals(Swipe.RIGHT, flick(classifier(), 80f, 0f))
    }

    @Test
    fun aDiagonalIsNotGuessed() {
        // Evenly up and right: neither axis is the answer.
        assertEquals(Swipe.NONE, flick(classifier(), 70f, -70f))
        // A 51/49 split is a coin toss, not a direction.
        assertEquals(Swipe.NONE, flick(classifier(), 60f, -59f))
    }

    @Test
    fun aSlightLeanIsEnough() {
        // Four to one is a thumb that meant it.
        assertEquals(Swipe.RIGHT, flick(classifier(), 80f, -20f))
    }

    @Test
    fun aTapIsNotASwipe() {
        assertEquals(Swipe.NONE, flick(classifier(), 6f, 0f))
        assertEquals(Swipe.NONE, flick(classifier(), 0f, 0f))
    }

    @Test
    fun aHeldDragIsStillASwipe() {
        // The pad sets no time limit: charging a shot and then choosing a
        // variant is one deliberate gesture, not a slow flick to throw away.
        assertEquals(Swipe.UP, flick(classifier(), 0f, -80f, atMs = 900L))
    }

    @Test
    fun aTimeLimitStillRejectsASlideWhenTheCallerAsksForOne() {
        val timed = SwipeClassifier(minTravelPx = 54f, maxDurationMs = 300L)
        assertEquals(Swipe.NONE, flick(timed, 0f, -80f, atMs = 400L))
        assertEquals(Swipe.UP, flick(timed, 0f, -80f, atMs = 120L))
    }

    @Test
    fun thePadsShortThresholdReadsAnOrdinaryDrag() {
        // 10 dp on a 64 dp button is a thumb moving off the spot, not a flick
        // across the screen; the pad uses this threshold for every button.
        val pad = SwipeClassifier(minTravelPx = 10f)
        assertEquals(Swipe.RIGHT, flick(pad, 14f, 2f))
        assertEquals(Swipe.NONE, flick(pad, 6f, 2f))
    }

    @Test
    fun nearestAxisReadingHasNoDiagonalDeadBand() {
        // Action buttons ask for dominance 1: a 45-degree drag is still a
        // direction, because the thumb leaned somewhere.
        val pad = SwipeClassifier(minTravelPx = 10f)
        pad.begin(1, 100f, 100f, 0L, SwipeClassifier.CARDINAL_DOMINANCE)
        pad.move(1, 171f, 29f)
        assertEquals(Swipe.RIGHT, pad.end(1, 120L))
        pad.begin(2, 100f, 100f, 0L, SwipeClassifier.CARDINAL_DOMINANCE)
        pad.move(2, 171f, 172f)
        assertEquals(Swipe.DOWN, pad.end(2, 120L))
    }

    @Test
    fun theDirectionIsWhereItEndedUpNotWhereItWandered() {
        val classifier = classifier()
        classifier.begin(1, 0f, 0f, 0L)
        classifier.move(1, 0f, -120f)     // a long way up...
        classifier.move(1, 0f, 4f)        // ...and back down again
        assertEquals(Swipe.NONE, classifier.end(1, 150L))
    }

    @Test
    fun aCancelledGestureLeavesNothingBehind() {
        val classifier = classifier()
        classifier.begin(1, 0f, 0f, 0L)
        assertTrue(classifier.busy(1))
        classifier.cancel(1)

        assertFalse(classifier.busy(1))
        assertEquals(Swipe.NONE, classifier.end(1, 100L))
    }

    @Test
    fun anUnknownPointerIsNotASwipe() {
        assertEquals(Swipe.NONE, classifier().end(7, 0L))
    }

    @Test
    fun twoFingersAreClassifiedOnTheirOwn() {
        val classifier = classifier()
        classifier.begin(1, 0f, 0f, 0L)
        classifier.begin(2, 200f, 200f, 0L)

        classifier.move(1, 0f, -90f)
        classifier.move(2, 290f, 200f)

        assertEquals(Swipe.UP, classifier.end(1, 100L))
        assertEquals(Swipe.RIGHT, classifier.end(2, 100L))
    }

    @Test
    fun clearDropsEveryGesture() {
        val classifier = classifier()
        classifier.begin(1, 0f, 0f, 0L)
        classifier.begin(2, 0f, 0f, 0L)

        classifier.clear()

        assertFalse(classifier.busy(1))
        assertFalse(classifier.busy(2))
    }
}
