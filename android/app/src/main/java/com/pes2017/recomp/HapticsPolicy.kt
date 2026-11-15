package com.pes2017.recomp

/**
 * One thing the pad can buzz about.
 *
 * The set is deliberately small: a control that feels different for every
 * event stops telling the thumb anything. [TAP] is a press, [DIRECTION] is the
 * moment a flick is recognised, [SKILL] and [ONE_TWO] are the two compound
 * commands, and [MATCH] is the title's own feedback -- the only one that needs
 * a vibration motor rather than the view's own haptic tick.
 */
enum class HapticEvent {
    /** A control went down. */
    TAP,

    /** A thumb turned a press into a direction. */
    DIRECTION,

    /** A compound sequence finished and everything is up again. */
    RELEASE,

    /** A skill move was played from a button or the wheel. */
    SKILL,

    /** The pass double tap was read as the one-two. */
    ONE_TWO,

    /** The title's own feedback (goals, full time), from the match. */
    MATCH,
}

/**
 * Whether a haptic event may happen at all.
 *
 * One switch governs every buzz in the app, and this is the only place that
 * decides -- the overlay, the editor and the match all ask here. Kept free of
 * Android types so the rule can be tested without a phone, which matters
 * because "the toggle turned vibration off but the pad still ticks" is exactly
 * the kind of bug that is invisible in code review.
 */
object HapticsPolicy {

    /**
     * [enabled] is the player's switch, [hasVibrator] is whether the device can
     * actually buzz. A phone with no motor still answers the view's haptic
     * feedback (some emulate it with sound), so only [HapticEvent.MATCH] is
     * refused for want of a motor.
     */
    fun allows(enabled: Boolean, hasVibrator: Boolean, event: HapticEvent): Boolean {
        if (!enabled) return false
        return when (event) {
            HapticEvent.MATCH -> hasVibrator
            else -> true
        }
    }
}
