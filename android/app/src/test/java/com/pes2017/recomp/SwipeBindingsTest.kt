package com.pes2017.recomp

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
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
    fun theDefaultPadCarriesTheSixteenStartingCombinations() {
        val pad = ControlLayout.defaults(800f, 360f)
        fun action(id: String, direction: Swipe) =
            pad.find(id)?.swipeAction(direction) ?: error("the default pad has $id")

        // Pass: one-two, manual, driven ground, controlled.
        assertEquals(SwipeAction.held(SwipeModifier.LB, NativeBridge.A), action("a", Swipe.UP))
        assertEquals(SwipeAction.held(SwipeModifier.LT, NativeBridge.A), action("a", Swipe.DOWN))
        assertEquals(SwipeAction.held(SwipeModifier.RB, NativeBridge.A), action("a", Swipe.LEFT))
        assertEquals(SwipeAction.held(SwipeModifier.RT, NativeBridge.A), action("a", Swipe.RIGHT))

        // Through pass: chipped, manual, lofted, controlled.
        assertEquals(SwipeAction.held(SwipeModifier.LB, NativeBridge.Y), action("y", Swipe.UP))
        assertEquals(SwipeAction.held(SwipeModifier.LT, NativeBridge.Y), action("y", Swipe.DOWN))
        assertEquals(SwipeAction.held(SwipeModifier.RB, NativeBridge.Y), action("y", Swipe.LEFT))
        assertEquals(SwipeAction.held(SwipeModifier.RT, NativeBridge.Y), action("y", Swipe.RIGHT))

        // Cross: early/deep, low (double tap), manual, controlled curl.
        assertEquals(SwipeAction.held(SwipeModifier.LB, NativeBridge.B), action("b", Swipe.UP))
        assertEquals(SwipeAction.double(NativeBridge.B), action("b", Swipe.DOWN))
        assertEquals(SwipeAction.held(SwipeModifier.LT, NativeBridge.B), action("b", Swipe.LEFT))
        assertEquals(SwipeAction.held(SwipeModifier.RT, NativeBridge.B), action("b", Swipe.RIGHT))

        // Shoot: chip, finesse, manual, driven.
        assertEquals(SwipeAction.held(SwipeModifier.LB, NativeBridge.X), action("x", Swipe.UP))
        assertEquals(SwipeAction.held(SwipeModifier.RT, NativeBridge.X), action("x", Swipe.DOWN))
        assertEquals(SwipeAction.held(SwipeModifier.LT, NativeBridge.X), action("x", Swipe.LEFT))
        assertEquals(SwipeAction.held(SwipeModifier.RB, NativeBridge.X), action("x", Swipe.RIGHT))
    }

    @Test
    fun theDefaultCombinationsStillCarryTheirPrimaryBit() {
        // The old plain-bit reader must keep working for a layout file or a
        // caller written before modifiers existed.
        val element = ControlLayout.defaults(800f, 360f).find("a")!!

        assertEquals(NativeBridge.A, element.swipeBit(Swipe.UP))
        assertEquals(NativeBridge.A, element.swipeBit(Swipe.DOWN))
        assertEquals(NativeBridge.A, element.swipeBit(Swipe.LEFT))
        assertEquals(NativeBridge.A, element.swipeBit(Swipe.RIGHT))
    }

    @Test
    fun aModifierAndADoubleTapAreDifferentBindings() {
        val element = ControlElement(
            "b", ControlKind.BUTTON, NativeBridge.B, "B", 0.5f, 0.5f, 68f,
            swipeDown = NativeBridge.B, swipeDownMod = SwipeModifier.LT, swipeDownDouble = true,
        )
        // Both can be recorded; the sequence builder gives the double tap
        // precedence, which is why the editor sets one or the other.
        val action = element.swipeAction(Swipe.DOWN)
        assertEquals(NativeBridge.B, action.bit)
        assertEquals(SwipeModifier.LT, action.modifier)
        assertTrue(action.doubleTap)
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
