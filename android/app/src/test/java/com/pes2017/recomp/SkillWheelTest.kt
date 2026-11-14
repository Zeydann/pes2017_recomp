package com.pes2017.recomp

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The skill wheel's data (SkillMoves.kt, ControlLayout's `trick` element).
 *
 * The wheel is only a picker: it plays a move index through the runtime. What is
 * checked here is that the eight sectors ship bound, that every bound index is
 * one the native table has, that no two sectors share a move, and that an index
 * the app should not have accepted is refused rather than stored.
 */
class SkillWheelTest {

    // ---- the table's names ----------------------------------------------------

    @Test
    fun theTableHasTheEightMovesTheWheelBinds() {
        assertEquals(8, SkillMoves.COUNT)
        for (index in 0 until SkillMoves.COUNT) {
            assertTrue("move $index has a name", SkillMoves.name(index).isNotEmpty())
            assertTrue("move $index has a short label", SkillMoves.short(index).isNotEmpty())
            assertTrue("move $index is valid", SkillMoves.valid(index))
        }
        assertFalse(SkillMoves.valid(-1))
        assertFalse(SkillMoves.valid(SkillMoves.COUNT))
    }

    @Test
    fun theMovesThatWaitForThePlayerSaySo() {
        // These end by asking for a left-stick direction; the rainbow does not,
        // and neither does the drag back.
        assertTrue(SkillMoves.leavesExit(1))   // roulette
        assertTrue(SkillMoves.leavesExit(4))   // body feint
        assertFalse(SkillMoves.leavesExit(2))  // rainbow: 2x R3
        assertFalse(SkillMoves.leavesExit(3))  // drag back
    }

    // ---- the default wheel ----------------------------------------------------

    @Test
    fun everySectorShipsBoundAndNoTwoShareAMove() {
        val wheel = ControlLayout.defaults(800f, 360f).find("trick")
        assertNotNull("the wheel is part of the default pad", wheel)
        wheel!!
        assertEquals(ControlKind.TRICK, wheel.kind)
        assertEquals(PhaseMask.MATCH, wheel.shownWhen)
        val bound = mutableSetOf<Int>()
        for (sector in 0 until TrickWheel.SECTORS) {
            val move = wheel.trickMove(sector)
            assertTrue("sector $sector plays a move the table has", SkillMoves.valid(move))
            assertTrue("sector $sector is not a duplicate", bound.add(move))
        }
        assertEquals("the eight default sectors cover the whole table", 8, bound.size)
    }

    @Test
    fun theWheelMatchesTheSectorsTheSheetNames() {
        // 0 right, then anticlockwise. Right is the chop, up is the feint, down
        // is the double touch: the two leaves that used to share a script.
        val wheel = ControlLayout.defaults(800f, 360f).find("trick")!!
        assertEquals(6, wheel.trickMove(0))   // ronaldo chop
        assertEquals(7, wheel.trickMove(1))   // elastico
        assertEquals(4, wheel.trickMove(2))   // body feint
        assertEquals(2, wheel.trickMove(3))   // rainbow
        assertEquals(3, wheel.trickMove(4))   // drag back
        assertEquals(5, wheel.trickMove(5))   // step over
        assertEquals(0, wheel.trickMove(6))   // double touch
        assertEquals(1, wheel.trickMove(7))   // roulette
    }

    @Test
    fun anUnboundSectorPlaysNothingAndABadIndexIsRefused() {
        val wheel = ControlLayout.defaults(800f, 360f).find("trick")!!
        wheel.setTrickMove(0, -1)
        assertEquals(-1, wheel.trickMove(0))
        wheel.setTrickMove(0, SkillMoves.COUNT)
        assertEquals("an index past the table is not stored", -1, wheel.trickMove(0))
        wheel.setTrickMove(-1, 2)
        wheel.setTrickMove(TrickWheel.SECTORS, 2)
        assertEquals("off the wheel stays off the wheel", -1, wheel.trickMove(-1))
        assertEquals(-1, wheel.trickMove(TrickWheel.SECTORS))
    }

    @Test
    fun thePaletteIsStillThereAndStillHidden() {
        // The wheel is the default; the five buttons are not removed, only off
        // the pad until a player turns them on.
        val layout = ControlLayout.defaults(800f, 360f)
        val palette = layout.elements.filter { it.kind == ControlKind.SKILL }
        assertEquals(5, palette.size)
        assertTrue(palette.none { it.visible })
        assertEquals(1, layout.elements.count { it.kind == ControlKind.TRICK })
        assertTrue(layout.find("trick")!!.visible)
    }
}
