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
 * What this control holds while its latch is engaged.
 *
 * Four things can latch, and each gets its hold from the field that already
 * says what it does: a trigger latches the trigger it names, a plain button its
 * own bit, a spare its bound centre bit, and a [ControlKind.LATCH] the command
 * the editor bound to it. A control with nothing bound latches nothing, which
 * is why an unbound toggle is a button that does not hold the game hostage.
 */
internal fun ControlElement.latchHold(): LatchHold = when (kind) {
    ControlKind.TRIGGER -> LatchHold(leftTrigger = value == 0, rightTrigger = value != 0)
    ControlKind.LATCH -> LatchHold(if (value != 0) value else swipeTap)
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
