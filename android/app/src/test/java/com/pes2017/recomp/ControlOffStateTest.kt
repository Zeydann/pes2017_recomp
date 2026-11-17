package com.pes2017.recomp

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * A control the player switched off, as the editor draws it: dimmed to 30%,
 * never lit, with the OFF badge under it -- and, in play, simply absent.
 */
class ControlOffStateTest {

    @Test
    fun anOffControlIsDimmedToExactlyThirtyPercent() {
        val alpha = ControlOffState.alpha(stanceAlpha = 0.9f, off = true, editing = true)
        assertEquals(0.30f, alpha, 0.0001f)
        assertEquals(ControlOffState.OFF_ALPHA, alpha, 0.0001f)
    }

    @Test
    fun anOnControlKeepsTheEditorsFadedFloor() {
        assertEquals(0.28f, ControlOffState.alpha(0.05f, off = false, editing = true), 0.0001f)
        assertEquals(0.8f, ControlOffState.alpha(0.8f, off = false, editing = true), 0.0001f)
    }

    @Test
    fun inPlayTheLayoutOpacityIsUsedUnchanged() {
        assertEquals(0.45f, ControlOffState.alpha(0.45f, off = false, editing = false), 0.0001f)
        // In play an off control is not drawn at all, so its alpha is moot; the
        // policy must not quietly dim something that is visible.
        assertEquals(0.45f, ControlOffState.alpha(0.45f, off = true, editing = false), 0.0001f)
    }

    @Test
    fun anOffControlNeverGlows() {
        assertEquals(0f, ControlOffState.glow(1f, off = true, editing = true), 0.0001f)
        assertEquals(1f, ControlOffState.glow(1f, off = false, editing = true), 0.0001f)
    }

    @Test
    fun theBadgeBelongsToTheEditorOnly() {
        assertTrue(ControlOffState.showsBadge(off = true, editing = true))
        assertFalse(ControlOffState.showsBadge(off = true, editing = false))
        assertFalse(ControlOffState.showsBadge(off = false, editing = true))
    }

    @Test
    fun offMeansHiddenAndOnlyTheEditorSaysSo() {
        assertTrue(ControlOffState.isOff(visible = false, editing = true))
        assertFalse(ControlOffState.isOff(visible = false, editing = false))
        assertFalse(ControlOffState.isOff(visible = true, editing = true))
    }

    @Test
    fun theOffOpacityIsDistinguishableFromTheOrdinaryFade() {
        // 0.30 against 0.28 is a small gap by design: the badge carries the
        // message, and the dimming says which controls are in the same state.
        assertTrue(ControlOffState.OFF_ALPHA > ControlOffState.FADED_ALPHA)
    }
}

/** One switch for every buzz in the app. */
class HapticsPolicyTest {

    @Test
    fun switchedOffSilencesEverything() {
        for (event in HapticEvent.values()) {
            assertFalse("$event", HapticsPolicy.allows(enabled = false, hasVibrator = true, event = event))
        }
    }

    @Test
    fun switchedOnWithAMotorAllowsEverything() {
        for (event in HapticEvent.values()) {
            assertTrue("$event", HapticsPolicy.allows(enabled = true, hasVibrator = true, event = event))
        }
    }

    // ---- the resting light ---------------------------------------------------

    @Test
    fun onlyTheControlsWithASwitchOfTheirOwnCarryTheRestingGlow() {
        for (kind in listOf(ControlKind.LATCH, ControlKind.TACTICAL, ControlKind.STICK_MODE)) {
            assertTrue("$kind", ControlOffState.showsRestGlow(kind, engaged = false, editing = false))
        }
    }

    @Test
    fun aMomentaryActionButtonNeverGlows() {
        // The whole point of the change: cross, through, pass, shoot, start,
        // select, the skill palette, the spares, the trick wheel and the stance
        // chip keep their own resting look. A light on every one of them was the
        // pad turned red.
        for (kind in listOf(
            ControlKind.BUTTON, ControlKind.TRIGGER, ControlKind.SKILL, ControlKind.CUSTOM,
            ControlKind.TRICK, ControlKind.STANCE, ControlKind.MENTALITY,
        )) {
            assertFalse("$kind", ControlOffState.showsRestGlow(kind, engaged = false, editing = false))
        }
    }

    @Test
    fun aToggleThatIsOnLosesItsGlow() {
        assertFalse(ControlOffState.showsRestGlow(ControlKind.LATCH, engaged = true, editing = false))
        assertFalse(ControlOffState.showsRestGlow(ControlKind.TACTICAL, engaged = true, editing = false))
        // Mode 2 is the switch being on, so the switch is not resting.
        assertFalse(ControlOffState.showsRestGlow(ControlKind.STICK_MODE, engaged = true, editing = false))
    }

    @Test
    fun theEditorAndTheThingsThatAreNotSwitchesAreLeftOut() {
        assertFalse(ControlOffState.showsRestGlow(ControlKind.LATCH, engaged = false, editing = true))
        assertFalse(ControlOffState.showsRestGlow(ControlKind.STICK_MODE, engaged = false, editing = true))
        for (kind in listOf(ControlKind.STICK, ControlKind.DPAD, ControlKind.SWIPE, ControlKind.LOOK)) {
            assertFalse("$kind", ControlOffState.showsRestGlow(kind, engaged = false, editing = false))
        }
    }

    @Test
    fun aPhoneWithoutAMotorStillTicksControlsButCannotRumble() {
        assertTrue(HapticsPolicy.allows(true, hasVibrator = false, event = HapticEvent.TAP))
        assertTrue(HapticsPolicy.allows(true, hasVibrator = false, event = HapticEvent.DIRECTION))
        assertFalse(HapticsPolicy.allows(true, hasVibrator = false, event = HapticEvent.MATCH))
    }
}
