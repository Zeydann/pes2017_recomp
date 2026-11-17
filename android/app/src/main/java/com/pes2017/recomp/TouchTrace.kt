package com.pes2017.recomp

import java.util.Locale

/**
 * What the pad is telling the title, in lines short enough to read in logcat.
 *
 * Two things are worth printing and nothing else is: the state the overlay
 * hands the runtime (both stick throws and every button bit) and the swipe that
 * ended a gesture, with what it sent. A thumb that is moving is thinned to a
 * couple of lines a second, because a sixty-hertz stream is unreadable; a bit
 * that changes is printed at once, because a press that comes and goes between
 * two samples is exactly the frame a dropped shot hides in.
 *
 * Free of Android types -- the Log call is the overlay's -- so a test can pin
 * the thinning down without a device.
 */
internal class TouchTraceGate(private val axisGapMs: Long = AXIS_GAP_MS) {
    private var lastAt = Long.MIN_VALUE
    private var lastBits = ""
    private var lastAxes = ""

    /**
     * The line for one pushed state, or null when this frame says nothing the
     * last one did not. [nowMs] is uptime, so a line's spacing is real time.
     */
    fun state(
        nowMs: Long,
        buttons: Int,
        leftTrigger: Int,
        rightTrigger: Int,
        lx: Float,
        ly: Float,
        rx: Float,
        ry: Float,
    ): String? {
        val axes = "l=(${axis(lx)},${axis(ly)}) r=(${axis(rx)},${axis(ry)})"
        val bits = "$buttons/$leftTrigger/$rightTrigger"
        if (bits == lastBits && axes == lastAxes) return null
        val bitChanged = bits != lastBits
        // A bit that changed is never thinned out: it may be down for one push.
        if (!bitChanged && lastAt != Long.MIN_VALUE && nowMs - lastAt < axisGapMs) return null
        lastAt = nowMs
        lastBits = bits
        lastAxes = axes
        val hex = String.format(Locale.US, "%04X", buttons)
        return "axes $axes buttons=0x$hex LT=$leftTrigger RT=$rightTrigger"
    }

    /** Forgets the last line, so the next one prints even if it is all centred. */
    fun reset() {
        lastAt = Long.MIN_VALUE
        lastBits = ""
        lastAxes = ""
    }

    companion object {
        /** The logcat tag, beside the runtime's own ("PES17") and the macro's. */
        const val TAG = "PES17.Touch"

        /** Two lines a second while a thumb moves; a bit change always prints. */
        const val AXIS_GAP_MS = 500L

        /**
         * One finished swipe: the thumb's direction and the bits it sent, or
         * `nothing` when the direction was unbound -- which is the line a dead
         * finesse flick needs, because silence looks the same as a tap.
         */
        fun swipe(control: String, direction: Swipe, action: SwipeAction): String {
            val what = when {
                !action.bound -> "nothing"
                action.doubleTap -> "double 0x${hex(action.bit)}"
                action.modifier == SwipeModifier.LT -> "LT+0x${hex(action.bit)}"
                action.modifier == SwipeModifier.RT -> "RT+0x${hex(action.bit)}"
                action.modifier != SwipeModifier.NONE -> "${action.modifier.name}+0x${hex(action.bit)}"
                else -> "0x${hex(action.bit)}"
            }
            return "swipe $control ${direction.name.lowercase(Locale.US)} -> $what"
        }

        private fun hex(bits: Int): String = String.format(Locale.US, "%04X", bits)

        private fun axis(value: Float): String = String.format(Locale.US, "%+.2f", value)
    }
}
