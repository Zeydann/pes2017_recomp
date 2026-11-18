package com.pes2017.recomp

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.hypot

/**
 * The defensive half of the default pad (ControlLayout.defaults): the near-thumb
 * player switch, and the second line each action button carries when the
 * defending pad is out.
 *
 * The geometry checks are deliberate: the switch sits beside a cluster that is
 * already tight, so a future change that moves it onto a neighbour fails here
 * rather than being discovered by a thumb.
 */
class DefensiveLayoutTest {

    private fun layout() = ControlLayout.defaults(800f, 360f)

    private fun dpX(element: ControlElement) = element.xFrac * 800f
    private fun dpY(element: ControlElement) = element.yFrac * 360f
    private fun radiusOf(element: ControlElement) = element.sizeDp / 2f

    /** Centre distance minus both hit radii (each carries 6 dp of slack). */
    private fun clearance(a: ControlElement, b: ControlElement) =
        hypot(dpX(a) - dpX(b), dpY(a) - dpY(b)) - radiusOf(a) - radiusOf(b) - 12f

    // ---- what ships ---------------------------------------------------------

    @Test
    fun thePadHasNoOnScreenStanceChip() {
        // The stance is chosen in Settings now: a pad that still drew a chip
        // would be a control nothing handles.
        assertEquals(null, layout().find("stance"))
    }

    @Test
    fun theNearThumbSwitchIsTheDefendingPadsAndShipsVisible() {
        val layout = layout()
        val near = layout.find("switch")
        assertNotNull(near)
        near!!
        assertEquals(ControlKind.BUTTON, near.kind)
        assertEquals("same LB bit as the pad's LB", NativeBridge.LB, near.value)
        assertEquals(PhaseMask.DEFEND, near.shownWhen)
        assertTrue(near.visible)
    }

    @Test
    fun theFarLbBelongsToTheAttackingPadOnly() {
        // Without the ball LB is the player switch; the one across the screen is
        // the attacking copy, and the switch above is the reachable one.
        val lb = layout().find("lb")!!
        assertEquals(PhaseMask.ATTACK, lb.shownWhen)
    }

    @Test
    fun theActionButtonsCarryTheirDefensiveNames() {
        val layout = layout()
        assertEquals("PRESS", layout.find("a")!!.sub2)
        assertEquals("TEAM", layout.find("b")!!.sub2)
        assertEquals("SLIDE", layout.find("x")!!.sub2)
        assertEquals("GK", layout.find("y")!!.sub2)
        assertEquals("SWITCH", layout.find("switch")!!.sub2)
        // The attacking names are untouched: the fake shot still looks for SHOOT.
        assertEquals("SHOOT", layout.find("x")!!.sub)
    }

    // ---- nothing new lands on anything old ----------------------------------

    @Test
    fun theStanceChipClearsEverythingElseOnTheAttackingPad() {
        val layout = layout()
        val chip = layout.find("stance")!!
        for (other in layout.elements) {
            if (other === chip || other.kind == ControlKind.LOOK) continue
            if (!other.shownIn(GamePhase.MATCH, PadStance.ATTACK)) continue
            if (!other.visible) continue
            assertTrue(
                "stance chip overlaps ${other.id}",
                clearance(chip, other) > 0f,
            )
        }
    }

    @Test
    fun theSwitchClearsEverythingElseOnTheDefendingPad() {
        val layout = layout()
        val near = layout.find("switch")!!
        for (other in layout.elements) {
            if (other === near || other.kind == ControlKind.LOOK) continue
            if (!other.shownIn(GamePhase.MATCH, PadStance.DEFEND)) continue
            if (!other.visible) continue
            // The attacking LB is not on the defending pad: compare only what
            // actually coexists.
            if (other.id == "lb") continue
            assertTrue(
                "defending switch overlaps ${other.id}",
                clearance(near, other) > 0f,
            )
        }
    }

}
