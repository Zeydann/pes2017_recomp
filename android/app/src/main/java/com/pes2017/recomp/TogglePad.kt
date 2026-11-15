package com.pes2017.recomp

/**
 * What a latched control keeps down while it is engaged.
 *
 * A latch is not a hold: there is no finger on it, so the bits it sends are not
 * counted per pointer and cannot be released by lifting a thumb. What it sends
 * is therefore spelled out once, here, and the pad merges it into the state it
 * pushes on every frame.
 *
 * Kept free of Android types, like [TouchClaims], so the arithmetic can be
 * tested without a phone (TogglePadTest).
 */
data class LatchHold(
    val buttons: Int = 0,
    val leftTrigger: Boolean = false,
    val rightTrigger: Boolean = false,
) {
    val any: Boolean get() = buttons != 0 || leftTrigger || rightTrigger

    companion object {
        val NONE = LatchHold()

        /**
         * The hold a bound command performs: the modifier, then the bit, both
         * kept down. This is the same pairing a compound swipe sends for a
         * single beat -- a latch simply never sends the step that lets go.
         */
        fun of(action: SwipeAction): LatchHold {
            if (!action.bound) return NONE
            val modifierBit = when (action.modifier) {
                SwipeModifier.LB -> NativeBridge.LB
                SwipeModifier.RB -> NativeBridge.RB
                else -> 0
            }
            return LatchHold(
                buttons = modifierBit or action.bit,
                leftTrigger = action.modifier == SwipeModifier.LT,
                rightTrigger = action.modifier == SwipeModifier.RT,
            )
        }
    }
}

/**
 * The game actions a custom toggle (TG1..TG6) can be assigned, and the input
 * each one keeps down while the toggle is on.
 *
 * This is the whole list the editor offers, in the order it shows them: the
 * title's movement and pressure commands, spelled as the game spells them, so a
 * player picks "Sprint: RT" rather than composing a bit mask. Two entries can
 * share an input -- Jockeying and Shielding are both LT -- because they are two
 * different things a player may want a toggle for.
 *
 * [PLAYER_MOVEMENT] is the odd one: LS is the left stick, and a latch cannot
 * steer an analogue stick (a deflection needs a direction and a thumb), so it
 * holds the stick's click (L3), which is the only left-stick input a toggle can
 * keep down. Everything else is a bit or a trigger, or both.
 */
enum class ToggleAction(val label: String) {
    NOTHING("Nothing (Unassigned)"),
    SPRINT("Sprint: RT"),
    JOCKEY("Jockeying: LT"),
    RUNNING_JOCKEY("Running Jockey: LT + RT"),
    TEAMMATE_PRESSURE("Teammate Pressure: RB"),
    PRESSURE("Pressure: A"),
    MANUAL_GK("Manual Goalkeeper Movement: R3"),
    SUPER_CANCEL("Super Cancel: RB + RT"),
    SHIELDING("Shielding: LT"),
    PLAYER_MOVEMENT("Player Movement: LS"),
    ;

    /** What the pad holds while a toggle assigned this action is switched on. */
    val hold: LatchHold
        get() = when (this) {
            NOTHING -> LatchHold.NONE
            SPRINT -> LatchHold(rightTrigger = true)
            JOCKEY -> LatchHold(leftTrigger = true)
            RUNNING_JOCKEY -> LatchHold(leftTrigger = true, rightTrigger = true)
            TEAMMATE_PRESSURE -> LatchHold(buttons = NativeBridge.RB)
            PRESSURE -> LatchHold(buttons = NativeBridge.A)
            MANUAL_GK -> LatchHold(buttons = NativeBridge.R3)
            SUPER_CANCEL -> LatchHold(buttons = NativeBridge.RB, rightTrigger = true)
            SHIELDING -> LatchHold(leftTrigger = true)
            // LS is the left stick: a latch can only hold its click (see above).
            PLAYER_MOVEMENT -> LatchHold(buttons = NativeBridge.L3)
        }

    companion object {
        /** The action a saved layout names, or [NOTHING] for one it does not. */
        fun of(name: String?): ToggleAction =
            entries.firstOrNull { it.name == name } ?: NOTHING
    }
}

/**
 * What this control holds while its latch is engaged.
 *
 * Four things can latch, and each gets its hold from the field that already
 * says what it does: a trigger latches the trigger it names, a plain button its
 * own bit, a spare its bound centre bit, and a [ControlKind.LATCH] the
 * [ToggleAction] the editor assigned to it. A control with nothing bound latches
 * nothing, which is why an unbound toggle is a button that does not hold the
 * game hostage.
 */
internal fun ControlElement.latchHold(): LatchHold = when (kind) {
    ControlKind.TRIGGER -> LatchHold(leftTrigger = value == 0, rightTrigger = value != 0)
    // TG1..TG6: the assigned game action, held whole -- trigger, bit or both --
    // for as long as the toggle is on.
    ControlKind.LATCH -> toggleAction.hold
    ControlKind.BUTTON, ControlKind.CUSTOM -> LatchHold(value)
    else -> LatchHold.NONE
}

/** Whether a tap on this control latches it rather than holding it. */
internal fun ControlElement.latches(): Boolean = when (kind) {
    // The two custom toggles are latches by definition, so they do not need
    // the per-control switch a button has.
    ControlKind.LATCH -> true
    ControlKind.BUTTON, ControlKind.CUSTOM, ControlKind.TRIGGER -> toggle
    else -> false
}
