package com.pes2017.recomp

/**
 * What a bound swipe is called in words a player recognises.
 *
 * The pad already knows the bits a direction sends, but "LB + X" is a binding,
 * not an action: the floating label over a charging shot should say "Chip Shot",
 * because that is the thing the player is trying to do. The names follow the
 * four action buttons and the four modifiers (docs/touch-controls.md), and fall
 * back to the plain binding for a spare button the player bound themselves.
 *
 * Free of Android types, so the labels can be checked without a device.
 */
object SwipeLabels {

    /**
     * The name of [action] on [element], in title case: "Chip Shot", "Low
     * Cross", "One-Two". Never empty for a bound action.
     */
    fun describe(element: ControlElement, action: SwipeAction): String {
        if (!action.bound) return ""
        val role = if (element.role != ControlRole.NONE) element.role else ControlRole.of(element.value)
        val named = namedFor(role, action)
        if (named != null) return named
        return plain(element, action)
    }

    /** The four action buttons' own vocabulary, or null for anything else. */
    private fun namedFor(role: ControlRole, action: SwipeAction): String? {
        if (action.doubleTap) {
            return when (role) {
                ControlRole.SHOOT -> "Double Shot"
                ControlRole.CROSS -> "Low Cross"
                ControlRole.PASS -> "Double Pass"
                ControlRole.THROUGH -> "Double Through Ball"
                else -> null
            }
        }
        return when (role) {
            ControlRole.SHOOT -> when (action.modifier) {
                SwipeModifier.LB -> "Chip Shot"
                SwipeModifier.RT -> "Finesse Shot"
                SwipeModifier.LT -> "Manual Shot"
                SwipeModifier.RB -> "Driven Shot"
                SwipeModifier.NONE -> "Shot"
            }
            ControlRole.CROSS -> when (action.modifier) {
                SwipeModifier.LB -> "Early Cross"
                SwipeModifier.LT -> "Manual Cross"
                SwipeModifier.RT -> "Controlled Cross"
                SwipeModifier.RB -> "Driven Cross"
                SwipeModifier.NONE -> "Cross"
            }
            ControlRole.PASS -> when (action.modifier) {
                SwipeModifier.LB -> "One-Two"
                SwipeModifier.LT -> "Manual Pass"
                SwipeModifier.RB -> "Driven Pass"
                SwipeModifier.RT -> "Controlled Pass"
                SwipeModifier.NONE -> "Pass"
            }
            ControlRole.THROUGH -> when (action.modifier) {
                SwipeModifier.LB -> "Chipped Through Ball"
                SwipeModifier.LT -> "Manual Through Ball"
                SwipeModifier.RB -> "Lofted Through Ball"
                SwipeModifier.RT -> "Controlled Through Ball"
                SwipeModifier.NONE -> "Through Ball"
            }
            else -> null
        }
    }

    /** A spare or a rebound control: "LB + A", "B ×2", "A". */
    private fun plain(element: ControlElement, action: SwipeAction): String {
        val bit = bitName(action.bit, element)
        return when {
            action.doubleTap -> "$bit ×2"
            action.modifier != SwipeModifier.NONE ->
                modifierName(action.modifier) + " + " + bit
            else -> bit
        }
    }

    private fun modifierName(modifier: SwipeModifier): String = when (modifier) {
        SwipeModifier.LB -> "LB"
        SwipeModifier.RB -> "RB"
        SwipeModifier.LT -> "LT"
        SwipeModifier.RT -> "RT"
        SwipeModifier.NONE -> ""
    }

    /** The pad's own name for a bit, so a spare reads as the button it holds. */
    private fun bitName(bit: Int, element: ControlElement): String = when (bit) {
        NativeBridge.A -> "A"
        NativeBridge.B -> "B"
        NativeBridge.X -> "X"
        NativeBridge.Y -> "Y"
        NativeBridge.LB -> "LB"
        NativeBridge.RB -> "RB"
        NativeBridge.START -> "Start"
        NativeBridge.BACK -> "Select"
        NativeBridge.L3 -> "L3"
        NativeBridge.R3 -> "R3"
        NativeBridge.UP -> "D-Pad Up"
        NativeBridge.DOWN -> "D-Pad Down"
        NativeBridge.LEFT -> "D-Pad Left"
        NativeBridge.RIGHT -> "D-Pad Right"
        0 -> element.label.ifEmpty { "Action" }
        else -> element.label.ifEmpty { "Action" }
    }
}
