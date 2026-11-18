package com.pes2017.recomp

/**
 * Which row of a menu is lit.
 *
 * The arithmetic only, so the view can be dumb and [MenuSelectionTest] can walk
 * every wrap and every skipped row without a phone: a D-pad that stops on a
 * greyed-out line, or that walks off the end instead of wrapping, is the sort
 * of thing that only shows up in a hand if nothing checks it first.
 */
class MenuSelection(private val enabled: List<Boolean>, start: Int = 0) {

    /** The row that is lit. Always an enabled row when there is one. */
    var index: Int = 0
        private set

    init {
        index = if (start in enabled.indices && enabled[start]) start else firstEnabled() ?: 0
    }

    /** Whether any row can be chosen at all. */
    val any: Boolean get() = enabled.any { it }

    /** Lights [row] if it exists and is enabled. Returns whether the answer changed. */
    fun focus(row: Int): Boolean {
        if (row !in enabled.indices || !enabled[row]) return false
        if (index == row) return false
        index = row
        return true
    }

    /**
     * Moves [delta] rows, wrapping, and stopping on the first enabled one.
     * Returns whether the lit row changed: a menu of one row, or of none, is a
     * key that did nothing rather than a key that moved.
     */
    fun move(delta: Int): Boolean {
        val count = enabled.size
        if (count == 0 || !any) return false
        var next = index
        repeat(count) {
            next = ((next + delta) % count + count) % count
            if (enabled[next]) {
                if (next == index) return false
                index = next
                return true
            }
        }
        return false
    }

    private fun firstEnabled(): Int? = enabled.indexOfFirst { it }.takeIf { it >= 0 }
}
