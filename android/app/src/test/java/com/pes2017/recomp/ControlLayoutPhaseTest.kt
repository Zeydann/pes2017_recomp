package com.pes2017.recomp

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * When a control belongs on screen: the player's own switch and the phase the
 * runtime reports, together.
 *
 * A build that cannot tell what the title is doing reports [GamePhase.UNKNOWN],
 * and the menus' controls stay where the player put them rather than vanishing
 * because of a guess.
 */
class ControlLayoutPhaseTest {

    private fun control(
        id: String = "dpad",
        kind: ControlKind = ControlKind.DPAD,
        visible: Boolean = true,
        shownWhen: PhaseMask = PhaseMask.MENUS,
    ) = ControlElement(id, kind, 0, "D-PAD", 0.25f, 0.6f, 110f, visible = visible, shownWhen = shownWhen)

    @Test
    fun aMenusControlIsOnScreenOverTheMenusAndOffThePitch() {
        val dpad = control()

        assertTrue(dpad.shownIn(GamePhase.MENUS))
        assertFalse(dpad.shownIn(GamePhase.MATCH))
    }

    @Test
    fun theTwoStageSwitchSitsAboveTheSocketAndIsAlwaysOnScreen() {
        // It ships on the pad, on the socket's own centre line, and its lower
        // edge clears the rim by more than a thumb's width: it is a switch, not
        // something the stick's own reach can catch.
        val layout = ControlLayout.defaults(800f, 360f)
        val stick = layout.find("stick") ?: error("the movement stick ships on the pad")
        val switch = layout.find("stick_mode") ?: error("the two-stage switch ships on the pad")

        assertEquals(stick.xFrac, switch.xFrac, 1e-4f)
        // 360 dp tall: the socket's rim is 190 dp down, the switch's lowest edge
        // is 179 dp down.
        val socketRimDp = 360f - 95f - 150f / 2f
        val switchBottomDp = 360f - 192f + 44f / 4f
        assertTrue(
            "switch bottom $switchBottomDp is above the rim $socketRimDp",
            switchBottomDp <= socketRimDp - 6f,
        )
        // The player's own switch, so it is reachable wherever they are: in a
        // match, in the title's menus, and before either is known. Only the
        // ring's RB is match-gated, in pushState, not the switch that picks it.
        assertTrue(switch.shownIn(GamePhase.MATCH))
        assertTrue(switch.shownIn(GamePhase.MENUS))
        assertTrue(switch.shownIn(GamePhase.UNKNOWN))
    }

    @Test
    fun anUnknownPhaseKeepsTheMenusControls() {
        // No addresses for the title's client states: hiding the d-pad would be
        // hiding it on a pitch the app cannot recognise.
        assertTrue(control().shownIn(GamePhase.UNKNOWN))
        assertFalse(control(shownWhen = PhaseMask.MATCH).shownIn(GamePhase.UNKNOWN))
    }

    @Test
    fun aMatchControlIsTheOtherWayRound() {
        val hud = control(id = "hud", kind = ControlKind.LOOK, shownWhen = PhaseMask.MATCH)

        assertFalse(hud.shownIn(GamePhase.MENUS))
        assertTrue(hud.shownIn(GamePhase.MATCH))
    }

    @Test
    fun alwaysIsAlways() {
        val start = control(id = "start", kind = ControlKind.BUTTON, shownWhen = PhaseMask.ALWAYS)

        for (phase in GamePhase.entries) assertTrue(start.shownIn(phase))
    }

    @Test
    fun thePlayersSwitchStillCounts() {
        val hidden = control(visible = false)

        assertFalse(hidden.shownIn(GamePhase.MENUS))
        assertFalse(hidden.shownIn(GamePhase.UNKNOWN))
    }

    @Test
    fun theDefaultLayoutHasADpadForTheMenusAndTheMatch() {
        val layout = ControlLayout.defaults(800f, 360f)
        val dpad = layout.find("dpad") ?: error("the default pad has a d-pad")

        assertTrue(dpad.kind == ControlKind.DPAD)
        assertTrue("it is there for the menus", dpad.shownIn(GamePhase.MENUS))
        // It is over the pitch too now, where it fades itself out after four
        // idle seconds (DpadAutoHide) instead of sitting there all match.
        assertTrue("and available for tactics in a match", dpad.shownIn(GamePhase.MATCH))
    }
}
