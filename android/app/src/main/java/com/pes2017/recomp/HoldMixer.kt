package com.pes2017.recomp

/**
 * Where a virtual button's down state comes from.
 *
 * The pad used to merge everything into one number as it went, which worked
 * until two sources wanted the same bit and only one of them stopped: a macro
 * that let go of LT also let go of the jockey's LT, and a second toggle on the
 * same button could not be told from the first. Naming the sources makes the
 * rule explicit -- a bit is down while *any* source wants it down -- and gives a
 * test something to assert against without a phone.
 */
enum class HoldSource {
    /** A thumb on the glass: a held button, a stick's arm, the d-pad. */
    PHYSICAL_TOUCH,

    /**
     * The two-stage stick's sprint ring (TouchMath.TwoStageStick). Its own source
     * because RB is the title's menu key as well as sprint: a ring that ended
     * must let go of RB without disturbing a thumb that is holding it, and one
     * that is held must not be masked by anything else in the merge.
     */
    STICK_SPRINT,

    /** The jockey button's latch. */
    JOCKEY_TOGGLE,

    /** The first custom toggle's latch. */
    TG1_TOGGLE,

    /** The second custom toggle's latch. */
    TG2_TOGGLE,

    /** The third custom toggle's latch. */
    TG3_TOGGLE,

    /** The fourth custom toggle's latch. */
    TG4_TOGGLE,

    /** The fifth custom toggle's latch. */
    TG5_TOGGLE,

    /** The sixth custom toggle's latch. */
    TG6_TOGGLE,

    /** A compound swipe's timed steps. */
    MACRO_SEQUENCE,

    /**
     * The modifier of a swipe on a button that is being held to charge. A
     * charged chip shot is the modifier arriving while the shoot bit is already
     * down, so this source rides alongside the thumb instead of being a timed
     * step after the lift.
     */
    SWIPE_HOLD,
}

/**
 * Every source that wants a virtual button down, merged by reference instead of
 * overwritten.
 *
 * Each source states the whole mask it is holding (not a delta), and the result
 * is the union of them all. Releasing one therefore cannot release a bit another
 * still wants: SW2 switching the left trigger off at the end of its sequence
 * leaves the jockey's latched LT exactly where it was, and two latches on the
 * same bit both have to be let go before it comes up.
 *
 * No allocation once built: the arrays are fixed at five sources, so this can be
 * read on every frame without feeding the collector.
 */
class HoldMixer {

    private val buttonMasks = IntArray(HoldSource.entries.size)
    private val lefts = BooleanArray(HoldSource.entries.size)
    private val rights = BooleanArray(HoldSource.entries.size)

    /** The mask [source] is holding, as it last stated it. */
    fun maskFor(source: HoldSource): Int = buttonMasks[source.ordinal]

    /** States everything one source wants down; a source says the whole truth each time. */
    fun set(
        source: HoldSource,
        buttons: Int,
        leftTrigger: Boolean = false,
        rightTrigger: Boolean = false,
    ) {
        buttonMasks[source.ordinal] = buttons
        lefts[source.ordinal] = leftTrigger
        rights[source.ordinal] = rightTrigger
    }

    /** Lets go of everything one source was holding. */
    fun release(source: HoldSource) = set(source, 0)

    /** Whether [source] is holding anything at all. */
    fun holds(source: HoldSource): Boolean =
        buttonMasks[source.ordinal] != 0 || lefts[source.ordinal] || rights[source.ordinal]

    /** Lets go of every source: pause, reset, the editor. */
    fun clear() {
        for (i in buttonMasks.indices) {
            buttonMasks[i] = 0
            lefts[i] = false
            rights[i] = false
        }
    }

    /** The union of every source's mask: what the title is told. */
    val buttons: Int
        get() {
            var merged = 0
            for (mask in buttonMasks) merged = merged or mask
            return merged
        }

    val leftTrigger: Boolean
        get() {
            for (held in lefts) if (held) return true
            return false
        }

    val rightTrigger: Boolean
        get() {
            for (held in rights) if (held) return true
            return false
        }

    companion object {
        /**
         * The source a latched control holds through. A spare or a plain button
         * that was switched to latching has no name of its own, so it counts as
         * the player's own hold: it ends with the pad, like a thumb does.
         */
        fun sourceFor(id: String): HoldSource = when (id) {
            "jockey" -> HoldSource.JOCKEY_TOGGLE
            "tg1" -> HoldSource.TG1_TOGGLE
            "tg2" -> HoldSource.TG2_TOGGLE
            "tg3" -> HoldSource.TG3_TOGGLE
            "tg4" -> HoldSource.TG4_TOGGLE
            "tg5" -> HoldSource.TG5_TOGGLE
            "tg6" -> HoldSource.TG6_TOGGLE
            else -> HoldSource.PHYSICAL_TOUCH
        }
    }
}
