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

    // ---- the pass double tap (one-two) ---------------------------------------

    @Test
    fun twoQuickTapsOfPassMakeAPair() {
        val tap = DoubleTap()
        tap.release(1_000)
        assertTrue("a second press inside 220 ms", tap.press(1_180))
    }

    @Test
    fun aSlowSecondTapIsJustAnotherPass() {
        // Missing the window degrades into two ordinary passes, never nothing.
        val tap = DoubleTap()
        tap.release(1_000)
        assertFalse(tap.press(1_260))
    }

    @Test
    fun aFirstPressWithNothingBeforeItIsNotAPair() {
        assertFalse(DoubleTap().press(5_000))
    }

    @Test
    fun aCompletedPairIsConsumedSoThreeTapsAreNotTwoPairs() {
        val tap = DoubleTap()
        tap.release(0)
        assertTrue(tap.press(100))
        assertFalse("the pair is spent", tap.press(180))
        tap.release(200)
        assertTrue("a fresh pair", tap.press(300))
    }

    @Test
    fun resetForgetsAHalfPair() {
        val tap = DoubleTap()
        tap.release(0)
        tap.reset()
        assertFalse(tap.press(50))
    }

    @Test
    fun theWindowIsTheDocumentedFifthOfASecond() {
        assertEquals(220L, DoubleTap.WINDOW_MS)
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
    fun aSwipeFiresExactlyWhenItsDirectionIsBound() {
        // Button swipes are direct commands now: there is no second gesture
        // competing for the drag, so a lift only asks whether it was bound.
        assertTrue(SwipePriority.sendsSwipe(SwipeAction.held(SwipeModifier.RB, NativeBridge.X)))
        assertTrue(SwipePriority.sendsSwipe(SwipeAction.of(NativeBridge.A)))
        assertFalse(SwipePriority.sendsSwipe(SwipeAction.NONE))
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
