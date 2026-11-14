package com.pes2017.recomp

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * The sixteen swipe slots: four directions on each of the four action buttons.
 *
 * Each slot is its own field, and `swipeBit` is the only thing the overlay asks
 * when a finger lifts, so this checks the mapping the game actually sends --
 * not merely that four fields exist.
 */
class SwipeBindingsTest {

    private fun button(id: String, up: Int, down: Int, left: Int, right: Int) = ControlElement(
        id, ControlKind.BUTTON, 0, id.uppercase(), 0.5f, 0.5f, 68f,
        swipeUp = up, swipeDown = down, swipeLeft = left, swipeRight = right,
    )

    @Test
    fun everyDirectionOfEveryActionButtonHasItsOwnBit() {
        // Each button gets four different bits, and no button shares a slot
        // with another: a swipe on pass cannot fire what a swipe on shoot does.
        val pass = button("a", NativeBridge.Y, NativeBridge.LB, NativeBridge.RB, NativeBridge.B)
        val through = button("y", NativeBridge.LB, NativeBridge.RB, NativeBridge.B, NativeBridge.Y)
        val shoot = button("x", NativeBridge.RB, NativeBridge.B, NativeBridge.Y, NativeBridge.LB)
        val cross = button("b", NativeBridge.B, NativeBridge.Y, NativeBridge.LB, NativeBridge.RB)

        assertEquals(NativeBridge.Y, pass.swipeBit(Swipe.UP))
        assertEquals(NativeBridge.LB, pass.swipeBit(Swipe.DOWN))
        assertEquals(NativeBridge.RB, pass.swipeBit(Swipe.LEFT))
        assertEquals(NativeBridge.B, pass.swipeBit(Swipe.RIGHT))

        assertEquals(NativeBridge.LB, through.swipeBit(Swipe.UP))
        assertEquals(NativeBridge.RB, through.swipeBit(Swipe.DOWN))
        assertEquals(NativeBridge.B, through.swipeBit(Swipe.LEFT))
        assertEquals(NativeBridge.Y, through.swipeBit(Swipe.RIGHT))

        assertEquals(NativeBridge.RB, shoot.swipeBit(Swipe.UP))
        assertEquals(NativeBridge.B, shoot.swipeBit(Swipe.DOWN))
        assertEquals(NativeBridge.Y, shoot.swipeBit(Swipe.LEFT))
        assertEquals(NativeBridge.LB, shoot.swipeBit(Swipe.RIGHT))

        assertEquals(NativeBridge.B, cross.swipeBit(Swipe.UP))
        assertEquals(NativeBridge.Y, cross.swipeBit(Swipe.DOWN))
        assertEquals(NativeBridge.LB, cross.swipeBit(Swipe.LEFT))
        assertEquals(NativeBridge.RB, cross.swipeBit(Swipe.RIGHT))
    }

    @Test
    fun anUnboundDirectionSendsNothing() {
        val fresh = button("a", 0, 0, 0, 0)

        assertEquals(0, fresh.swipeBit(Swipe.UP))
        assertEquals(0, fresh.swipeBit(Swipe.DOWN))
        assertEquals(0, fresh.swipeBit(Swipe.LEFT))
        assertEquals(0, fresh.swipeBit(Swipe.RIGHT))
        // A tap is separate from the four directions and is bound separately.
        assertEquals(0, fresh.swipeBit(Swipe.NONE))
    }

    @Test
    fun bindingOneDirectionLeavesTheOthersAlone() {
        val one = button("a", 0, 0, 0, NativeBridge.Y)

        assertEquals(NativeBridge.Y, one.swipeBit(Swipe.RIGHT))
        assertEquals(0, one.swipeBit(Swipe.UP))
        assertEquals(0, one.swipeBit(Swipe.DOWN))
        assertEquals(0, one.swipeBit(Swipe.LEFT))
    }
}
