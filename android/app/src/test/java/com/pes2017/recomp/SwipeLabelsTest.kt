package com.pes2017.recomp

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * The words the floating swipe label and the editor use: a direction should
 * read as the action it plays, not as the bits it happens to send.
 */
class SwipeLabelsTest {

    private fun button(
        id: String,
        bit: Int,
        role: ControlRole = ControlRole.of(bit),
    ) = ControlElement(
        id, ControlKind.BUTTON, bit, id.uppercase(), 0.5f, 0.5f, 64f, role = role,
    )

    @Test
    fun theFourActionButtonsUseTheGamesOwnNames() {
        val shoot = button("x", NativeBridge.X)
        assertEquals("Chip Shot", SwipeLabels.describe(shoot, SwipeAction.held(SwipeModifier.LB, NativeBridge.X)))
        assertEquals("Finesse Shot", SwipeLabels.describe(shoot, SwipeAction.held(SwipeModifier.RT, NativeBridge.X)))
        assertEquals("Driven Shot", SwipeLabels.describe(shoot, SwipeAction.held(SwipeModifier.RB, NativeBridge.X)))

        val cross = button("b", NativeBridge.B)
        assertEquals("Low Cross", SwipeLabels.describe(cross, SwipeAction.double(NativeBridge.B)))
        assertEquals("Early Cross", SwipeLabels.describe(cross, SwipeAction.held(SwipeModifier.LB, NativeBridge.B)))

        val pass = button("a", NativeBridge.A)
        assertEquals("One-Two", SwipeLabels.describe(pass, SwipeAction.held(SwipeModifier.LB, NativeBridge.A)))

        val through = button("y", NativeBridge.Y)
        assertEquals(
            "Chipped Through Ball",
            SwipeLabels.describe(through, SwipeAction.held(SwipeModifier.LB, NativeBridge.Y)),
        )
    }

    @Test
    fun aSpareFallsBackToItsBinding() {
        val spare = ControlElement(
            "c1", ControlKind.CUSTOM, 0, "C1", 0.5f, 0.5f, 48f, role = ControlRole.NONE,
        )
        assertEquals(
            "LB + A",
            SwipeLabels.describe(spare, SwipeAction.held(SwipeModifier.LB, NativeBridge.A)),
        )
        assertEquals("B ×2", SwipeLabels.describe(spare, SwipeAction.double(NativeBridge.B)))
    }

    @Test
    fun anUnboundDirectionHasNoName() {
        assertEquals("", SwipeLabels.describe(button("x", NativeBridge.X), SwipeAction.NONE))
    }
}
