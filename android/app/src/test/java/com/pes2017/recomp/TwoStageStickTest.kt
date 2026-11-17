package com.pes2017.recomp

import kotlin.math.hypot
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The two-stage stick's numbers, as the spec the pad is built against
 * (docs/touch-controls.md, "Two-stage stick").
 *
 * One socket, two zones: the drawn circle is the movement zone, and the narrower
 * ring outside it is the sprint zone -- entered by swiping outward past the rim,
 * left by pulling back in. The first circle is the larger of the two and RB lives
 * in the ring, never inside it.
 *
 * These assert every threshold, so a retune of any number fails here rather than
 * quietly changing the feel on a phone.
 */
class TwoStageStickTest {

    private val delta = 1e-4f

    // ---- the published thresholds -------------------------------------------

    @Test
    fun theZoneAndSprintThresholdsAreTheOnesWeAgreed() {
        assertEquals(1.0f, StickStage.INNER_RADIUS, delta)
        assertEquals(1.35f, StickStage.OUTER_RADIUS, delta)
        assertEquals(1.06f, StickStage.SPRINT_IN, delta)
        assertEquals(0.94f, StickStage.SPRINT_OUT, delta)
        assertEquals(70L, StickStage.DWELL_MS)
        assertEquals(0.90f, StickStage.INNER_GAIN, delta)
    }

    @Test
    fun theFirstCircleIsTheLargerOfTheTwoZones() {
        val inner = StickStage.INNER_RADIUS
        val ring = StickStage.OUTER_RADIUS - StickStage.INNER_RADIUS
        // Wider than the ring ...
        assertTrue(inner > ring)
        // ... and larger in area, which is the reading that matters on a screen:
        // the sprint zone must not be the bigger target of the two.
        val innerArea = inner * inner
        val ringArea = StickStage.OUTER_RADIUS * StickStage.OUTER_RADIUS - inner * inner
        assertTrue(innerArea > ringArea)
    }

    // ---- the movement zone --------------------------------------------------

    @Test
    fun theMovementZoneScalesTheThrowAndKeepsItsDirection() {
        assertEquals(0.27f, StickStage.magnitude(0.30f), delta)
        assertEquals(0.558f, StickStage.magnitude(0.62f), delta)   // the knob's own rim
        assertEquals(0.891f, StickStage.magnitude(0.99f), delta)
    }

    @Test
    fun aCentredStickSendsNothingAtAll() {
        assertEquals(0f, StickStage.magnitude(0f), delta)
        val sample = TwoStageStick().sample(1, 0.001f, 0f, 0L)
        assertEquals(0f, sample.x, delta)
        assertEquals(0f, sample.y, delta)
    }

    @Test
    fun theInsideTopsOutJustUnderFullPace() {
        // The tenth the ring buys back; without it "sprint" would only be a bit.
        assertEquals(StickStage.INNER_GAIN, StickStage.magnitude(StickStage.INNER_RADIUS), delta)
        assertEquals(0.90f, StickStage.magnitude(1.0f), delta)
    }

    @Test
    fun aDiagonalStaysADiagonalRightThroughTheBoundary() {
        val stick = TwoStageStick()
        for (distance in listOf(0.5f, 0.99f, 1.0f, 1.2f, 1.35f)) {
            val component = distance / hypot(1f, 1f)
            val sample = stick.sample(1, component, component, 0L)
            assertEquals("x and y stay equal at $distance", sample.x, sample.y, delta)
            assertEquals(
                "the throw becomes the mapped magnitude at $distance",
                StickStage.magnitude(distance), hypot(sample.x, sample.y), delta,
            )
        }
    }

    // ---- the sprint ring ----------------------------------------------------

    @Test
    fun theRingRampsToFullPaceAndThenHolds() {
        // 0.90 + 0.10 * (0.10 / 0.35)
        assertEquals(0.9285714f, StickStage.magnitude(1.10f), delta)
        assertEquals(1.0f, StickStage.magnitude(StickStage.OUTER_RADIUS), delta)
        // Past the ring the stick is still one full deflection, not an
        // out-of-range value the engine would have to repair.
        assertEquals(1.0f, StickStage.magnitude(1.8f), delta)
        assertEquals(1.0f, StickStage.magnitude(4.0f), delta)
    }

    @Test
    fun theBoundaryIsContinuous() {
        val inside = StickStage.magnitude(0.9999f)
        val outside = StickStage.magnitude(1.0001f)
        assertTrue("no step at the rim: $inside vs $outside", kotlin.math.abs(outside - inside) < 1e-3f)
    }

    @Test
    fun pushingFurtherNeverSendsLess() {
        var previous = -1f
        var distance = 0f
        while (distance <= 2f) {
            val magnitude = StickStage.magnitude(distance)
            assertTrue("monotonic at $distance", magnitude >= previous - 1e-6f)
            previous = magnitude
            distance += 0.001f
        }
        assertEquals(1.0f, previous, delta)
    }

    // ---- when RB goes down --------------------------------------------------

    @Test
    fun theRingDoesNotSprintUntilTheDwellIsUp() {
        val stick = TwoStageStick()
        assertFalse(stick.sample(1, 1.20f, 0f, 1000L).sprint)
        assertFalse("69 ms of ring is not a sprint", stick.sample(1, 1.20f, 0f, 1069L).sprint)
        assertEquals("and RB is untouched", false, stick.isSprinting(1))
        assertTrue("70 ms is", stick.sample(1, 1.20f, 0f, 1070L).sprint)
        assertTrue(stick.isSprinting(1))
    }

    @Test
    fun theInsideNeverSprintsHoweverLongItIsHeld() {
        val stick = TwoStageStick()
        assertFalse(stick.sample(1, 0.99f, 0f, 0L).sprint)
        assertFalse(stick.sample(1, 0.99f, 0f, 10_000L).sprint)
        assertFalse(stick.isSprinting(1))
    }

    @Test
    fun aSprintIsHeldAcrossTheHysteresisBand() {
        val stick = TwoStageStick()
        assertFalse(stick.sample(1, 1.20f, 0f, 0L).sprint)
        assertTrue(stick.sample(1, 1.20f, 0f, 70L).sprint)
        // Circling the rim: still inside the ring by the exit point.
        assertTrue(stick.sample(1, 1.00f, 0f, 90L).sprint)
        assertTrue(stick.sample(1, 0.95f, 0f, 110L).sprint)
        // Pulled back in properly: RB comes up at once, with no dwell on the way out.
        assertFalse(stick.sample(1, 0.94f, 0f, 130L).sprint)
        assertFalse(stick.isSprinting(1))
    }

    @Test
    fun aThumbBalancedOnTheBoundaryNeverChatters() {
        val stick = TwoStageStick()
        var now = 0L
        // Jitter either side of the rim, 10 ms a sample, for a second: the entry
        // clock restarts on every dip, so the dwell is never reached.
        repeat(100) {
            val distance = if (it % 2 == 0) 1.05f else 1.08f
            assertFalse("flicker at sample $it", stick.sample(1, distance, 0f, now).sprint)
            now += 10L
        }
        assertFalse(stick.isSprinting(1))
    }

    @Test
    fun leavingTheRingNeedsTheDwellAgain() {
        val stick = TwoStageStick()
        stick.sample(1, 1.20f, 0f, 0L)
        assertTrue(stick.sample(1, 1.20f, 0f, 70L).sprint)
        assertFalse(stick.sample(1, 0.90f, 0f, 100L).sprint)
        // Straight back out: the old sprint does not come back by itself.
        assertFalse(stick.sample(1, 1.20f, 0f, 120L).sprint)
        assertTrue(stick.sample(1, 1.20f, 0f, 190L).sprint)
    }

    // ---- two thumbs, and every way a push ends ------------------------------

    @Test
    fun oneThumbsRingIsNotAnotherThumbsRing() {
        val stick = TwoStageStick()
        stick.sample(1, 1.20f, 0f, 0L)
        assertTrue(stick.sample(1, 1.20f, 0f, 70L).sprint)
        // The second thumb, inside the circle, is not sprinting and does not
        // disturb the first one's stage.
        assertFalse(stick.sample(2, 0.50f, 0f, 80L).sprint)
        assertTrue(stick.isSprinting(1))
        assertFalse(stick.isSprinting(2))
        // Lifting the share thumb leaves the owner's sprint alone.
        stick.release(2)
        assertTrue(stick.isSprinting(1))
        // And the owner's lift ends its own stage.
        stick.release(1)
        assertFalse(stick.isSprinting(1))
    }

    @Test
    fun aCancelledPushLeavesNothingBehind() {
        val stick = TwoStageStick()
        stick.sample(1, 1.20f, 0f, 0L)
        assertTrue(stick.sample(1, 1.20f, 0f, 70L).sprint)
        stick.reset()   // pause, focus loss, ACTION_CANCEL, the editor
        assertFalse(stick.isSprinting(1))
        // A fresh push starts from nothing, dwell included.
        assertFalse(stick.sample(1, 1.20f, 0f, 200L).sprint)
        assertTrue(stick.sample(1, 1.20f, 0f, 270L).sprint)
    }

    @Test
    fun theRingGivesTheSameDirectionBackAtTheSameMagnitude() {
        // The pointer's sample is the only place the mapping happens, so a
        // direction cannot drift between one frame and the next.
        val stick = TwoStageStick()
        val inside = stick.sample(1, 0.42f, -0.42f, 0L)
        val outside = stick.sample(1, 0.95f, -0.95f, 500L)
        assertEquals(inside.x / inside.y, outside.x / outside.y, 1e-4f)
        assertEquals(-inside.x, inside.y, delta)
    }
}
