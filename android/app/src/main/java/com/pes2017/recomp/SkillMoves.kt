package com.pes2017.recomp

/**
 * The native skill table (runtime/skill_moves.h) as the app names it.
 *
 * A move index is stored in a layout file and handed to
 * [NativeBridge.nativeSkillPlay], so the order here is the order there: a move
 * may be appended, never inserted or shuffled. The C++ side refuses an index
 * it does not know, and logs it, rather than reading past the table.
 *
 * The names are shown on the wheel and in the editor. A move that ends by
 * waiting for a left-stick direction says so, because the player supplies that
 * part: the wheel only plays the right-stick (or button) half.
 */
object SkillMoves {

    /** The wheel's sectors, and the number of moves the table carries. */
    const val COUNT = 8

    private val NAMES = arrayOf(
        "DOUBLE TOUCH",
        "ROULETTE",
        "RAINBOW FLICK",
        "DRAG BACK",
        "BODY FEINT",
        "STEP OVER",
        "RONALDO CHOP",
        "ELASTICO",
    )

    /** Two or three letters, for the ring where a full name will not fit. */
    private val SHORT = arrayOf("DBL", "ROUL", "RBNB", "DRAG", "FEINT", "STEP", "CHOP", "ELAS")

    /**
     * Whether the gesture continues into a direction the player picks. The
     * wheel plays the first half; the left thumb supplies the rest.
     */
    private val LEAVES_EXIT = booleanArrayOf(
        true,   // double touch: then push the stick to the exit
        true,   // roulette
        false,  // rainbow: two R3 clicks, nothing after it
        false,  // drag back
        true,   // body feint
        true,   // step over
        true,   // ronaldo chop
        true,   // elastico
    )

    fun valid(index: Int): Boolean = index in NAMES.indices

    fun name(index: Int): String = NAMES.getOrNull(index) ?: "—"

    fun short(index: Int): String = SHORT.getOrNull(index) ?: ""

    fun leavesExit(index: Int): Boolean = LEAVES_EXIT.getOrNull(index) == true
}
