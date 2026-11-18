package com.pes2017.recomp

/**
 * Everything one swipe direction can be bound to.
 *
 * The pad can send far more than a face button: the menu and d-pad bits, a
 * double tap, a held shoulder or trigger with a face button, and a full throw
 * of either stick with or without a modifier. The editor offers them in
 * groups, and this object is the one list it reads, so a command the pad can
 * perform is always one the player can bind and the guide can explain.
 *
 * It is deliberately data, not a dialog: [SwipeSequence] is what turns each
 * entry into held states, and a test can walk the whole catalogue without a
 * phone.
 */
object SwipeCatalogue {

    private val FACE_BUTTONS = listOf(
        NativeBridge.A, NativeBridge.B, NativeBridge.X, NativeBridge.Y,
    )

    /** The order PES's own combinations are written in: L1, L2, R1, R2. */
    private val MODIFIER_ORDER = listOf(
        SwipeModifier.LB, SwipeModifier.LT, SwipeModifier.RB, SwipeModifier.RT,
    )

    private val STICK_FLICKS = listOf(
        SwipeFlick.LS_UP, SwipeFlick.LS_DOWN, SwipeFlick.LS_LEFT, SwipeFlick.LS_RIGHT,
        SwipeFlick.RS_UP, SwipeFlick.RS_DOWN, SwipeFlick.RS_LEFT, SwipeFlick.RS_RIGHT,
    )

    /** The plain pad bits: everything a single press can send. */
    val buttons: List<SwipeAction> = listOf(
        NativeBridge.A, NativeBridge.B, NativeBridge.X, NativeBridge.Y,
        NativeBridge.LB, NativeBridge.RB,
        NativeBridge.START, NativeBridge.BACK,
        NativeBridge.L3, NativeBridge.R3,
        NativeBridge.UP, NativeBridge.DOWN, NativeBridge.LEFT, NativeBridge.RIGHT,
    ).map { SwipeAction.of(it) }

    /**
     * Two quick presses. The four face buttons and the d-pad: the title reads a
     * repeated tap on either as a command of its own (the low cross, a d-pad
     * step that repeats), and a shoulder or a menu button has no double tap.
     */
    val doubles: List<SwipeAction> = listOf(
        NativeBridge.A, NativeBridge.B, NativeBridge.X, NativeBridge.Y,
        NativeBridge.UP, NativeBridge.DOWN, NativeBridge.LEFT, NativeBridge.RIGHT,
    ).map { SwipeAction.double(it) }

    /** A held modifier with a face button: PES's LB + pass, RT + shoot, and so on. */
    val modifiers: List<SwipeAction> = MODIFIER_ORDER.flatMap { modifier ->
        FACE_BUTTONS.map { SwipeAction.held(modifier, it) }
    }

    /** A full throw of either stick, on its own. */
    val flicks: List<SwipeAction> = STICK_FLICKS.map { SwipeAction.flick(it) }

    /** A full throw of a stick while a modifier is held. */
    val modifierFlicks: List<SwipeAction> = MODIFIER_ORDER.flatMap { modifier ->
        STICK_FLICKS.map { SwipeAction.heldFlick(modifier, it) }
    }

    /** One heading in the picker, and what it holds. */
    class Group(val titleRes: Int, val entries: List<SwipeAction>)

    /** The order the groups are offered in, with their title resources. */
    val groups: List<Group> = listOf(
        Group(R.string.swipe_group_none, listOf(SwipeAction.NONE)),
        Group(R.string.swipe_group_buttons, buttons),
        Group(R.string.swipe_group_doubles, doubles),
        Group(R.string.swipe_group_modifiers, modifiers),
        Group(R.string.swipe_group_sticks, flicks),
        Group(R.string.swipe_group_modifier_sticks, modifierFlicks),
    )

    /** Every bindable action, in the order the groups list them. */
    val all: List<SwipeAction> = groups.flatMap { it.entries }
}
