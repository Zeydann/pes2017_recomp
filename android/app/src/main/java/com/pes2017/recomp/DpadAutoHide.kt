package com.pes2017.recomp

/**
 * The d-pad's idle clock: how long it has been since the player touched it,
 * when the fade began, and how visible it is now.
 *
 * Kept out of the view so the timing can be tested without a device. The view
 * only asks [alpha] while drawing and calls [reset] on a touch; a hidden d-pad
 * is refused by the hit test, so it cannot swallow a touch meant for the pitch.
 *
 * Only a match uses this: in the title's menus the d-pad is navigation and must
 * not disappear under the thumb.
 */
class DpadAutoHide(
    private val idleMs: Long = IDLE_MS,
    private val fadeMs: Long = FADE_MS,
) {
    private var lastTouchAt: Long = 0L
    private var fadingSince: Long = -1L

    /** True once the fade has finished: the control is gone until [reset]. */
    var hidden: Boolean = false
        private set

    /** Called on a touch on or beside the d-pad, and whenever it comes back on screen. */
    fun reset(now: Long) {
        lastTouchAt = now
        fadingSince = -1L
        hidden = false
    }

    /**
     * Advances the clock. Returns true when this call started the fade, so the
     * view knows to begin asking for frames.
     */
    fun tick(now: Long): Boolean {
        if (hidden) return false
        if (fadingSince < 0) {
            if (now - lastTouchAt < idleMs) return false
            fadingSince = now
            return true
        }
        if (now - fadingSince >= fadeMs) hidden = true
        return false
    }

    /** Visibility right now: 1 before the fade, 0 when hidden, linear between. */
    fun alpha(now: Long): Float {
        if (hidden) return 0f
        if (fadingSince < 0) return 1f
        val progress = ((now - fadingSince).toFloat() / fadeMs).coerceIn(0f, 1f)
        return 1f - progress
    }

    /** Whether a fade is in progress and another frame is worth drawing. */
    fun fading(now: Long): Boolean = !hidden && fadingSince >= 0 && alpha(now) > 0f

    /** Milliseconds until the idle fade should start, for the view's timer. */
    fun idleRemaining(now: Long): Long =
        if (fadingSince >= 0 || hidden) 0L else (idleMs - (now - lastTouchAt)).coerceAtLeast(0L)

    companion object {
        /** Four seconds: inside the requested three-to-five second window. */
        const val IDLE_MS = 4_000L

        /** Short enough to feel like a fade, long enough not to blink. */
        const val FADE_MS = 280L
    }
}
