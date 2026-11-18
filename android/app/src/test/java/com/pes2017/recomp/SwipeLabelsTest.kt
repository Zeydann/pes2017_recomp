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

    private fun spare() = ControlElement(
        "c1", ControlKind.CUSTOM, 0, "C1", 0.5f, 0.5f, 48f, role = ControlRole.NONE,
    )

    @Test
    fun aSpareNamesTheActionItPlays() {
        // C1..C10 hold nothing of their own, so the pill has to read the command
        // the direction sends -- the same words the action buttons show.
        assertEquals(
            "Chipped Through Ball",
            SwipeLabels.describe(spare(), SwipeAction.held(SwipeModifier.LB, NativeBridge.Y)),
        )
        assertEquals(
            "One-Two",
            SwipeLabels.describe(spare(), SwipeAction.held(SwipeModifier.LB, NativeBridge.A)),
        )
        assertEquals("Low Cross", SwipeLabels.describe(spare(), SwipeAction.double(NativeBridge.B)))
        assertEquals(
            "Chip Shot",
            SwipeLabels.describe(spare(), SwipeAction.held(SwipeModifier.LB, NativeBridge.X)),
        )
    }

    @Test
    fun aSpareBoundToAShoulderStillReadsItsBinding() {
        // Nothing in the game's vocabulary for this one: the binding is the name.
        assertEquals("LB", SwipeLabels.describe(spare(), SwipeAction.of(NativeBridge.LB)))
    }

    @Test
    fun aSpareAccentFollowsTheCommandItPlays() {
        assertEquals(0, SwipeLabels.accentBit(spare()))
        assertEquals(NativeBridge.X, SwipeLabels.accentBit(spare().copy(swipeUp = NativeBridge.X)))
        assertEquals(NativeBridge.Y, SwipeLabels.accentBit(spare().copy(value = NativeBridge.Y)))
        // The centre tap counts too: an unbound swipe list but a bound tap is
        // still a spare that plays something.
        assertEquals(
            NativeBridge.B,
            SwipeLabels.accentBit(spare().copy(swipeTap = NativeBridge.B)),
        )
    }

    @Test
    fun anUnboundDirectionHasNoName() {
        assertEquals("", SwipeLabels.describe(button("x", NativeBridge.X), SwipeAction.NONE))
    }

    @Test
    fun theAdvancedInstructionButtonNamesItsFourArms() {
        // The floating pill says the instruction, not the arm it sends; the
        // editor's own direction list is untouched.
        val adv = ControlElement(
            "sw2", ControlKind.TACTICAL, 0, "ADV", 0.5f, 0.5f, 88f,
        )
        assertEquals(
            "Attack 1",
            SwipeLabels.describe(adv, SwipeAction.held(SwipeModifier.LT, NativeBridge.UP)),
        )
        assertEquals(
            "Attack 2",
            SwipeLabels.describe(adv, SwipeAction.held(SwipeModifier.LT, NativeBridge.DOWN)),
        )
        assertEquals(
            "Defense 2",
            SwipeLabels.describe(adv, SwipeAction.held(SwipeModifier.LT, NativeBridge.RIGHT)),
        )
        assertEquals(
            "Defense 1",
            SwipeLabels.describe(adv, SwipeAction.held(SwipeModifier.LT, NativeBridge.LEFT)),
        )
    }

    @Test
    fun aReboundAdvancedArmReadsTheBindingItSendsNow() {
        val adv = ControlElement(
            "sw2", ControlKind.TACTICAL, 0, "ADV", 0.5f, 0.5f, 88f,
        )
        assertEquals(
            "LB + D-Pad Up",
            SwipeLabels.describe(adv, SwipeAction.held(SwipeModifier.LB, NativeBridge.UP)),
        )
    }
}
