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
    fun describe(
        element: ControlElement,
        action: SwipeAction,
        /** Turns a name into the current language; bindings are left alone. */
        translate: (String) -> String = { it },
    ): String {
        if (!action.bound) return ""
        // A stick throw is its own vocabulary: "LS Up", "LB + RS Left". It has
        // no button to name and no PES command to resolve to, so it is read the
        // same way on every control.
        if (action.flick != SwipeFlick.NONE && action.bit == 0) {
            val flick = translate(flickName(action.flick))
            return when (action.modifier) {
                SwipeModifier.NONE -> flick
                else -> modifierName(action.modifier) + " + " + flick
            }
        }
        // The role follows the command the gesture actually sends, not only the
        // control's own bit: a spare (C1..C10) holds nothing itself, and its
        // directions are bound to face buttons. A spare bound to LB + Y is
        // playing the chipped through ball, so the pill says exactly that -- as
        // it does over the through-ball button -- instead of "LB + Y".
        // The advanced-instruction button's four arms are the instructions
        // themselves, and the floating pill over it should say so. This is the
        // gameplay label only: the editor still lists the LT + d-pad arm each
        // direction sends, because that is what a player edits there.
        advancedInstruction(element, action)?.let { return translate(it) }
        val role = when {
            element.role != ControlRole.NONE -> element.role
            element.kind == ControlKind.CUSTOM -> ControlRole.of(action.bit)
            else -> ControlRole.of(element.value)
        }
        val named = namedFor(role, action)
        if (named != null) return translate(named)
        return plain(element, action, translate)
    }

    /**
     * The pad bit a spare's accent should follow: its own binding when the
     * editor gave it one, otherwise the first direction that sends a face
     * button, otherwise 0. Only the drawing uses it, to tint C1..C10 the colour
     * of the command they play; an unbound spare is nobody's colour.
     */
    fun accentBit(element: ControlElement): Int {
        if (element.value != 0) return element.value
        for (direction in DIRECTIONS) {
            val bit = element.swipeBit(direction)
            if (bit != 0) return bit
        }
        return element.swipeTap
    }

    /** The four directions, in the order the file and the editor list them. */
    private val DIRECTIONS = arrayOf(Swipe.UP, Swipe.DOWN, Swipe.LEFT, Swipe.RIGHT)

    /**
     * The four advanced instructions, as the floating HUD names them. Only the
     * factory binding is named this way: once a direction is rebound to
     * something else, the label follows the command it now sends.
     */
    private fun advancedInstruction(element: ControlElement, action: SwipeAction): String? {
        if (element.id != ADVANCE_BUTTON) return null
        if (action.doubleTap || action.flick != SwipeFlick.NONE) return null
        if (action.modifier != SwipeModifier.LT) return null
        return when (action.bit) {
            NativeBridge.UP -> "Attack 1"
            NativeBridge.DOWN -> "Attack 2"
            NativeBridge.RIGHT -> "Defense 2"
            NativeBridge.LEFT -> "Defense 1"
            else -> null
        }
    }

    /** The id of the advanced-instruction button (sw2, on-screen "ADV"). */
    private const val ADVANCE_BUTTON = "sw2"

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
    private fun plain(
        element: ControlElement,
        action: SwipeAction,
        translate: (String) -> String,
    ): String {
        val bit = translate(bitName(action.bit, element))
        return when {
            action.doubleTap -> "$bit ×2"
            action.modifier != SwipeModifier.NONE ->
                modifierName(action.modifier) + " + " + bit
            else -> bit
        }
    }

    /** How a stick throw reads in the editor and in a floating label. */
    fun flickName(flick: SwipeFlick): String = when (flick) {
        SwipeFlick.NONE -> ""
        SwipeFlick.LS_UP -> "LS Up"
        SwipeFlick.LS_DOWN -> "LS Down"
        SwipeFlick.LS_LEFT -> "LS Left"
        SwipeFlick.LS_RIGHT -> "LS Right"
        SwipeFlick.RS_UP -> "RS Up"
        SwipeFlick.RS_DOWN -> "RS Down"
        SwipeFlick.RS_LEFT -> "RS Left"
        SwipeFlick.RS_RIGHT -> "RS Right"
    }

    /** How a held modifier reads: "LB", "RT". */
    fun modifierName(modifier: SwipeModifier): String = when (modifier) {
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
