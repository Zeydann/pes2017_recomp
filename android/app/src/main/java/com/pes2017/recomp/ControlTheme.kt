package com.pes2017.recomp

/**
 * The pad's visual language, in one place: how much light and motion it uses,
 * how long a press lasts, and the glass colour under every control.
 *
 * The pad is drawn over a match, so everything here is deliberately restrained.
 * A glow is feedback, not decoration: it starts on the press and is gone
 * [RELEASE_MS] after the lift, and there is no idle animation at all -- a pad
 * that breathes on the ball is a pad that tires the eye and burns frames.
 */
object ControlTheme {

    /** Flat controls, exactly as before the theme: no glow, no scale, no frames. */
    const val FX_OFF = "off"

    /** Default: clean rims, a short press scale and a soft aura while held. */
    const val FX_SUBTLE = "subtle"

    /** Also draws the expanding press pulse and the stance cross-fade. */
    const val FX_FULL = "full"

    /** How long the press takes to settle, in milliseconds. */
    const val PRESS_MS = 90L

    /** How long the light takes to fade after the lift. */
    const val RELEASE_MS = 140L

    /** The expanding ring drawn on the press in [FX_FULL]. */
    const val PULSE_MS = 180L

    /** A stance change fades from one accent to the other over this. */
    const val STATE_MS = 180L

    /**
     * The overlay's share of a 16.6 ms frame. Above this, on average, the pad's
     * light is costing more than it is worth and is turned down for the session.
     */
    const val FRAME_BUDGET_NS = 6_000_000L

    /** The glass under a control: near-black, so the pitch stays visible through it. */
    const val GLASS = 0xFF0B0F17.toInt()

    fun animated(level: String): Boolean = level != FX_OFF

    fun pulsed(level: String): Boolean = level == FX_FULL

    /** Ease-out, so the press moves quickly and settles rather than stopping dead. */
    fun easeOut(t: Float): Float {
        val clamped = t.coerceIn(0f, 1f)
        return 1f - (1f - clamped) * (1f - clamped)
    }

    /**
     * The press's progress, [0] just landed and [1] fully settled. [pressed] is
     * the pad's own state, so a toggle that is latched reads as held.
     */
    fun pressAmount(pressed: Boolean, downAt: Long, releaseAt: Long, now: Long): Float {
        if (pressed || releaseAt < downAt) {
            if (downAt == 0L) return if (pressed) 1f else 0f
            return easeOut(((now - downAt).toFloat() / PRESS_MS).coerceIn(0f, 1f))
        }
        val fade = 1f - ((now - releaseAt).toFloat() / RELEASE_MS).coerceIn(0f, 1f)
        return easeOut(fade)
    }

    /**
     * How far a control shrinks while held: small enough that the glyph does
     * not move under the thumb, large enough to feel like a real button.
     */
    fun scale(amount: Float): Float = 1f - 0.04f * amount.coerceIn(0f, 1f)

    /** Alpha multiplier for the aura while held. */
    fun aura(amount: Float): Float = amount.coerceIn(0f, 1f)
}
