package com.pes2017.recomp

import kotlin.math.max

/**
 * How a control the player has switched off looks while it is being edited.
 *
 * In play an off control is simply not there -- it is not drawn and it cannot be
 * hit. In the editor it has to be the opposite: visible enough to find, dim
 * enough to read at a glance as "off", and never lit up as if a thumb were
 * pressing something that sends nothing. Hence three rules, in one place so the
 * overlay only has to render them:
 *
 *  - its opacity is [OFF_ALPHA], not the 45% a faded control gets;
 *  - it has no press glow at all, even if its id is mid-animation;
 *  - the editor draws the OFF badge under it.
 *
 * Free of Android types: [ControlOffStateTest] checks the rules without a
 * canvas, which is the only way to test a draw decision.
 */
object ControlOffState {

    /** The opacity of an off control in the editor. Dim, not invisible. */
    const val OFF_ALPHA = 0.30f

    /** The opacity the editor gives a control that is on but belongs to another phase. */
    const val FADED_ALPHA = 0.28f

    /** Whether [visible] on this stance means the control is off right now. */
    fun isOff(visible: Boolean, editing: Boolean): Boolean = editing && !visible

    /**
     * What a control is drawn at. An off control gets [OFF_ALPHA] exactly, so a
     * player can see that it is different from the ordinary faded controls
     * around it; everything else keeps the editor's faded floor, and in play
     * the layout's own opacity.
     */
    fun alpha(stanceAlpha: Float, off: Boolean, editing: Boolean): Float = when {
        off && editing -> OFF_ALPHA
        editing -> max(FADED_ALPHA, stanceAlpha)
        else -> stanceAlpha
    }

    /** The press light, with an off control forced flat: 0 for any animation. */
    fun glow(amount: Float, off: Boolean, editing: Boolean): Float =
        if (off && editing) 0f else amount

    /** Whether the OFF badge belongs under this control right now. */
    fun showsBadge(off: Boolean, editing: Boolean): Boolean = off && editing
}
