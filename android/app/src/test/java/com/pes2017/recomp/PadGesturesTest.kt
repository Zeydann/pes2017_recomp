package com.pes2017.recomp

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The sprint latch and the fake shot's arming rule (PadGestures.kt).
 *
 * Both are pure arithmetic on the values the overlay already holds, so the
 * edges -- the press point, the release point, a drag that is too short, a
 * drag that goes the wrong way, and a gesture that must press pass only once --
 * are checked here rather than by playing with two thumbs.
 */
class PadGesturesTest {

    // ---- sprint --------------------------------------------------------------

    @Test
    fun aThumbAllTheWayOutSprints() {
        val latch = SprintLatch()
        assertTrue(latch.update(0.93f, 0f))
        assertTrue(latch.active)
        assertTrue(latch.update(0f, 0.95f))
    }

    @Test
    fun justInsideThePressPointDoesNotSprint() {
        val latch = SprintLatch()
        assertFalse(latch.update(0.90f, 0f))
        assertFalse(latch.active)
    }

    @Test
    fun theReleasePointKeepsARestingThumbFromChattering() {
        val latch = SprintLatch()
        assertTrue(latch.update(0.92f, 0f))
        // Between press and release: still running, because it is latched.
        assertTrue(latch.update(0.85f, 0f))
        assertTrue(latch.active)
        // Below release: stopped.
        assertFalse(latch.update(0.80f, 0f))
        // And back between the two points it stays stopped until the press point.
        assertFalse(latch.update(0.85f, 0f))
        assertTrue(latch.update(0.92f, 0f))
    }

    @Test
    fun releasingTheStickStopsTheSprint() {
        val latch = SprintLatch()
        latch.update(1f, 0f)
        assertFalse(latch.update(0f, 0f))
    }

    @Test
    fun resetStopsAStuckSprint() {
        val latch = SprintLatch()
        latch.update(1f, 0f)
        latch.reset()
        assertFalse(latch.active)
    }

    // ---- the fake shot -------------------------------------------------------

    private fun fake(minTravelPx: Float = 40f) = FakeShotGesture(minTravelPx)

    /** Shoot at (100, 100), pass at (200, 100): the drag has to go right. */
    private fun moved(
        gesture: FakeShotGesture,
        dx: Float,
        dy: Float,
        fromX: Float = 100f,
        fromY: Float = 100f,
    ): Boolean = gesture.moved(
        fromX, fromY, fromX + dx, fromY + dy,
        100f, 100f, 200f, 100f,
    )

    @Test
    fun aDragTowardsPassArmsTheGesture() {
        val gesture = fake()
        assertTrue(moved(gesture, 60f, 0f))
        assertTrue(gesture.armed)
    }

    @Test
    fun aShortDragIsJustAThumbResting() {
        val gesture = fake()
        assertFalse(moved(gesture, 20f, 0f))
        assertFalse(gesture.armed)
    }

    @Test
    fun aDragAwayFromPassDoesNotArm() {
        assertFalse(moved(fake(), -60f, 0f))
    }

    @Test
    fun aDragAcrossTheButtonDoesNotArm() {
        // Straight up: 90 degrees from the line to pass, so 45 is too far.
        assertFalse(moved(fake(), 0f, -60f))
        // Evenly right and down is 0.71 of the way, inside the cut-off; make it
        // clearly diagonal so the test is about the rule, not rounding.
        assertFalse(moved(fake(), 40f, -60f))
    }

    @Test
    fun theGesturePressesPassOnlyOnce() {
        val gesture = fake()
        assertTrue(moved(gesture, 60f, 0f))
        assertFalse(moved(gesture, 80f, 0f))
        assertFalse(moved(gesture, 100f, 0f))
        assertTrue(gesture.armed)
    }

    @Test
    fun resetLetsTheNextDragArmAgain() {
        val gesture = fake()
        assertTrue(moved(gesture, 60f, 0f))
        gesture.reset()
        assertFalse(gesture.armed)
        assertTrue(moved(gesture, 60f, 0f))
    }

    @Test
    fun aPassControlOnTopOfShootCannotArmAnything() {
        // The direction from shoot to pass is degenerate; nothing to aim at.
        val gesture = fake()
        assertFalse(gesture.moved(100f, 100f, 200f, 100f, 100f, 100f, 100f, 100f))
        assertFalse(gesture.armed)
    }

    // ---- compound swipe sequences --------------------------------------------

    @Test
    fun aModifierGoesDownBeforeItsButtonAndBothAreLetGo() {
        val steps = SwipeSequence.steps(SwipeAction.held(SwipeModifier.LB, NativeBridge.A))

        // The modifier alone first: a hand holds the shoulder, then presses.
        assertTrue(steps[0].buttons == NativeBridge.LB)
        assertTrue(steps[1].buttons == (NativeBridge.LB or NativeBridge.A))
        assertEquals(0, steps.last().buttons)
        assertTrue(steps[0].delayMs < steps[1].delayMs)
        assertTrue(steps[1].delayMs < steps.last().delayMs)
    }

    @Test
    fun aTriggerModifierHoldsTheTriggerAxisWithTheButton() {
        val steps = SwipeSequence.steps(SwipeAction.held(SwipeModifier.RT, NativeBridge.X))

        assertTrue("the trigger leads", steps[0].rightTrigger)
        assertEquals(0, steps[0].buttons)
        assertTrue("the button is pressed while it is held", steps[1].rightTrigger)
        assertEquals(NativeBridge.X, steps[1].buttons)
        val end = steps.last()
        assertFalse(end.rightTrigger)
        assertFalse(end.leftTrigger)
        assertEquals(0, end.buttons)
    }

    @Test
    fun theTwoTriggersAreNotInterchangeable() {
        val left = SwipeSequence.steps(SwipeAction.held(SwipeModifier.LT, NativeBridge.A))
        assertTrue(left[1].leftTrigger)
        assertFalse(left[1].rightTrigger)
    }

    @Test
    fun aDoubleTapPressesTheButtonTwiceWithAClearingGap() {
        val steps = SwipeSequence.steps(SwipeAction.double(NativeBridge.B))

        assertEquals(4, steps.size)
        assertEquals(NativeBridge.B, steps[0].buttons)
        assertEquals(0, steps[1].buttons)
        assertEquals(NativeBridge.B, steps[2].buttons)
        assertEquals(0, steps.last().buttons)
        assertTrue(steps[1].delayMs > 0L)
        assertTrue(steps[3].delayMs > steps[2].delayMs)
    }

    @Test
    fun aPlainBindingIsStillOnePressAndOneRelease() {
        val steps = SwipeSequence.steps(SwipeAction.of(NativeBridge.Y))

        assertEquals(2, steps.size)
        assertEquals(NativeBridge.Y, steps[0].buttons)
        assertEquals(0, steps.last().buttons)
    }

    @Test
    fun everySequenceEndsWithNothingHeld() {
        val actions = listOf(
            SwipeAction.of(NativeBridge.A),
            SwipeAction.double(NativeBridge.B),
            SwipeAction.held(SwipeModifier.LB, NativeBridge.A),
            SwipeAction.held(SwipeModifier.LT, NativeBridge.Y),
            SwipeAction.held(SwipeModifier.RB, NativeBridge.X),
            SwipeAction.held(SwipeModifier.RT, NativeBridge.B),
        )
        for (action in actions) {
            val end = SwipeSequence.steps(action).last()
            assertEquals("buttons after $action", 0, end.buttons)
            assertFalse(end.leftTrigger)
            assertFalse(end.rightTrigger)
        }
    }

    @Test
    fun anUnboundActionHasNoSequenceAtAll() {
        assertTrue(SwipeSequence.steps(SwipeAction.NONE).isEmpty())
    }

    @Test
    fun clearingMidSequenceLetsGoOfTheModifierAndTrigger() {
        val hold = ComboHold()
        hold.apply(SwipeSequence.steps(SwipeAction.held(SwipeModifier.LT, NativeBridge.A))[1])
        assertTrue(hold.any)

        hold.clear()

        assertEquals(0, hold.buttons)
        assertFalse(hold.leftTrigger)
        assertFalse(hold.rightTrigger)
        assertFalse(hold.any)
    }

    @Test
    fun aFakeShotOwnsItsGesture() {
        // Even a bound direction must not also fire when the drag was a fake
        // shot: one gesture, one action.
        val bound = SwipeAction.held(SwipeModifier.RB, NativeBridge.X)
        assertTrue(SwipePriority.sendsSwipe(faked = false, bound))
        assertFalse(SwipePriority.sendsSwipe(faked = true, bound))
        assertFalse(SwipePriority.sendsSwipe(faked = false, SwipeAction.NONE))
    }

    // ---- the speed burst's two taps ------------------------------------------

    @Test
    fun twoTapsOfSprintAreTwoPresses() {
        // The speed burst is two taps of RB. Both are presses of their own, and
        // the gap between a bounce and a tap is what the cadence is for.
        val cadence = TapCadence()
        assertTrue(cadence.accept(1_000))
        assertFalse("a bounce inside 40 ms is the same press", cadence.accept(1_010))
        assertTrue("a second tap of a double tap", cadence.accept(1_160))
    }

    @Test
    fun aStuckFirstPressDoesNotSwallowTheNextTap() {
        val cadence = TapCadence()
        assertTrue(cadence.accept(0))
        assertTrue(cadence.accept(40))
        cadence.reset()
        assertTrue("a reset pad lets the first press of a new gesture through", cadence.accept(60))
    }

    // ---- the skill wheel -----------------------------------------------------

    @Test
    fun theWheelOpensOnAHoldAndNotOnATap() {
        assertFalse(TrickWheel.isHold(0))
        assertFalse(TrickWheel.isHold(TrickWheel.HOLD_MS - 1))
        assertTrue(TrickWheel.isHold(TrickWheel.HOLD_MS))
        assertTrue(TrickWheel.isHold(TrickWheel.HOLD_MS + 500))
    }

    @Test
    fun everySectorIsFortyFiveDegrees() {
        val radius = 100f
        val r = 50f
        val d = r * 0.70710678f
        // 0 right, then anticlockwise: up is screen-negative y.
        assertEquals(0, TrickWheel.sector(r, 0f, radius))
        assertEquals(1, TrickWheel.sector(d, -d, radius))
        assertEquals(2, TrickWheel.sector(0f, -r, radius))
        assertEquals(3, TrickWheel.sector(-d, -d, radius))
        assertEquals(4, TrickWheel.sector(-r, 0f, radius))
        assertEquals(5, TrickWheel.sector(-d, d, radius))
        assertEquals(6, TrickWheel.sector(0f, r, radius))
        assertEquals(7, TrickWheel.sector(d, d, radius))
    }

    @Test
    fun theDeadzoneChosesNothing() {
        assertEquals(-1, TrickWheel.sector(0f, 0f, 100f))
        assertEquals(-1, TrickWheel.sector(20f, 0f, 100f))
        assertEquals(-1, TrickWheel.sector(0f, 0f, 0f))
        assertEquals("just outside the deadzone is a choice", 0, TrickWheel.sector(30f, 0f, 100f))
    }

    @Test
    fun aSectorEdgeFallsOnTheNearerSide() {
        val radius = 100f
        val r = 50.0
        val justBelow = Math.toRadians(22.4)
        val justAbove = Math.toRadians(22.6)
        assertEquals(0, TrickWheel.sector((r * Math.cos(justBelow)).toFloat(),
            (-r * Math.sin(justBelow)).toFloat(), radius))
        assertEquals(1, TrickWheel.sector((r * Math.cos(justAbove)).toFloat(),
            (-r * Math.sin(justAbove)).toFloat(), radius))
    }
}
