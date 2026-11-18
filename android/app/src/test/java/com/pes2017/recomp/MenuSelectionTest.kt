package com.pes2017.recomp

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The menu's selection arithmetic: what the D-pad and the stick move.
 *
 * The band on screen is only as good as this: one threshold wrong and a menu
 * either stops on a greyed-out line or jumps over two entries at once.
 */
class MenuSelectionTest {

    @Test
    fun theFirstEnabledRowIsLitWhenTheScreenOpens() {
        val selection = MenuSelection(listOf(false, true, true))
        assertEquals(1, selection.index)
    }

    @Test
    fun aMenuWithNothingEnabledLightsNothingAndMovesNowhere() {
        val selection = MenuSelection(listOf(false, false))
        assertFalse(selection.any)
        assertFalse(selection.move(1))
        assertFalse(selection.move(-1))
        assertEquals(0, selection.index)
    }

    @Test
    fun movingWrapsAtBothEnds() {
        val selection = MenuSelection(listOf(true, true, true), start = 0)
        assertTrue(selection.move(-1))
        assertEquals(2, selection.index)
        assertTrue(selection.move(1))
        assertEquals(0, selection.index)
    }

    @Test
    fun disabledRowsAreSkippedRatherThanStoppedOn() {
        val selection = MenuSelection(listOf(true, false, false, true), start = 0)
        assertTrue(selection.move(1))
        assertEquals(3, selection.index)
        assertTrue(selection.move(1))
        assertEquals(0, selection.index)
    }

    @Test
    fun aSingleRowIsAKeyThatDoesNothing() {
        val selection = MenuSelection(listOf(true))
        assertFalse(selection.move(1))
        assertEquals(0, selection.index)
    }

    @Test
    fun focusRefusesDisabledAndMissingRows() {
        val selection = MenuSelection(listOf(true, false, true))
        assertFalse(selection.focus(1))
        assertEquals(0, selection.index)
        assertTrue(selection.focus(2))
        assertEquals(2, selection.index)
        assertFalse(selection.focus(9))
        assertEquals(2, selection.index)
    }

    @Test
    fun aScreenThatOpensOnADisabledRowLandsOnTheFirstOne() {
        val selection = MenuSelection(listOf(false, false, true), start = 0)
        assertEquals(2, selection.index)
    }
}
