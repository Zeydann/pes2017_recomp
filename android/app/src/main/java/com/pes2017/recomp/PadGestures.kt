package com.pes2017.recomp

import kotlin.math.hypot

/**
 * The gestures the pad has to remember between touch samples: the sprint
 * latch, the fake shot, and what a bound swipe does when it is more than one
 * bit. All of it is arithmetic on numbers the overlay already has, so it lives
 * here rather than in it, and a test can check every edge without a phone.
 */

/**
 * A button a swipe can hold beside its primary bit. Bumpers are pad bits;
 * triggers are the two analogue axes, which is why they are their own names
 * rather than bits.
 */
enum class SwipeModifier {
    NONE,
    LB,
    RB,
    LT,
    RT,
    ;

    /** The pad bit to hold, or 0 for none and for the triggers. */
    val bit: Int
        get() = when (this) {
            LB -> NativeBridge.LB
            RB -> NativeBridge.RB
            else -> 0
        }

    val isTrigger: Boolean get() = this == LT || this == RT
}

/**
 * What one swipe direction sends.
 *
 * A plain bit is the original binding. A modifier is held slightly before the
 * primary bit and released with it, which is how PES 2017's combinations are
 * performed (LB + pass, R2 during the shot gauge, and so on). A double tap is
 * two quick presses of the primary bit, for the commands the title reads as a
 * repeated tap (the low cross, the knock-on).
 */
data class SwipeAction(
    val bit: Int = 0,
    val modifier: SwipeModifier = SwipeModifier.NONE,
    val doubleTap: Boolean = false,
) {
    val bound: Boolean get() = bit != 0

    companion object {
        val NONE = SwipeAction()

        fun of(bit: Int): SwipeAction = SwipeAction(bit = bit)

        fun held(modifier: SwipeModifier, bit: Int): SwipeAction =
            SwipeAction(bit = bit, modifier = modifier)

        fun double(bit: Int): SwipeAction = SwipeAction(bit = bit, doubleTap = true)
    }
}

/** One timed press of the pad that a compound swipe performs. */
data class SwipeStep(
    val delayMs: Long,
    val buttons: Int,
    val leftTrigger: Boolean = false,
    val rightTrigger: Boolean = false,
)

/**
 * A compound swipe as the steps the overlay schedules.
 *
 * A modifier is pressed a frame before the button (the title reads the modifier
 * first, as a hand does), both are held for a beat -- long enough for the
 * title's next poll -- and the last step clears everything. The double tap is
 * two presses with a gap and a clearing step at the end. Every sequence ends
 * with an all-clear step, which is what makes a cancelled gesture safe: the
 * overlay can also call [ComboHold.clear] at any point and nothing is left
 * down.
 */
object SwipeSequence {

    /** The modifier goes down this long before the primary bit. */
    const val MODIFIER_LEAD_MS = 30L

    /** How long the pair is held: several polls at the title's frame rate. */
    const val HOLD_MS = 120L

    /** The gap between a double tap's two presses. */
    const val DOUBLE_TAP_GAP_MS = 90L

    fun steps(action: SwipeAction): List<SwipeStep> {
        if (!action.bound) return emptyList()

        if (action.doubleTap) {
            return listOf(
                SwipeStep(0L, action.bit),
                SwipeStep(DOUBLE_TAP_GAP_MS, 0),
                SwipeStep(DOUBLE_TAP_GAP_MS * 2, action.bit),
                SwipeStep(DOUBLE_TAP_GAP_MS * 3, 0),
            )
        }

        return when (action.modifier) {
            SwipeModifier.NONE -> listOf(
                SwipeStep(0L, action.bit),
                SwipeStep(HOLD_MS, 0),
            )
            SwipeModifier.LB, SwipeModifier.RB -> listOf(
                SwipeStep(0L, action.modifier.bit),
                SwipeStep(MODIFIER_LEAD_MS, action.modifier.bit or action.bit),
                SwipeStep(MODIFIER_LEAD_MS + HOLD_MS, 0),
            )
            SwipeModifier.LT -> listOf(
                SwipeStep(0L, 0, leftTrigger = true),
                SwipeStep(MODIFIER_LEAD_MS, action.bit, leftTrigger = true),
                SwipeStep(MODIFIER_LEAD_MS + HOLD_MS, 0),
            )
            SwipeModifier.RT -> listOf(
                SwipeStep(0L, 0, rightTrigger = true),
                SwipeStep(MODIFIER_LEAD_MS, action.bit, rightTrigger = true),
                SwipeStep(MODIFIER_LEAD_MS + HOLD_MS, 0),
            )
        }
    }
}

/**
 * What a compound swipe is holding right now. The overlay owns one, applies
 * each step to it and adds it to the state it pushes; [clear] is the one call
 * every cancellation makes, so a bumper or trigger cannot be left down.
 */
class ComboHold {
    var buttons = 0
        private set
    var leftTrigger = false
        private set
    var rightTrigger = false
        private set

    val any: Boolean get() = buttons != 0 || leftTrigger || rightTrigger

    fun apply(step: SwipeStep) {
        buttons = step.buttons
        leftTrigger = step.leftTrigger
        rightTrigger = step.rightTrigger
    }

    fun clear() {
        buttons = 0
        leftTrigger = false
        rightTrigger = false
    }
}

/** Which of the two things a lifted gesture should do when both apply. */
object SwipePriority {
    /**
     * A fake shot owns the gesture it is part of: its swipe direction must not
     * also fire, or one drag would cancel a shot and send another command.
     */
    fun sendsSwipe(faked: Boolean, action: SwipeAction): Boolean = !faked && action.bound
}

/**
 * Sprint from the movement stick's rim.
 *
 * One threshold would chatter: a thumb held exactly on it would flicker between
 * running and jogging every sample. The press point (0.92 of the raw throw) and
 * the release point (0.82) are therefore separate, and the state is latched
 * between them. The raw throw decides -- not the shaped one -- so the curve
 * cannot make a jog a sprint by squeezing the top of its range.
 *
 * [reset] is what every cancellation path calls: pause, focus loss, the pad
 * being hidden or edited. A sprint cannot outlive the thumb that started it.
 */
class SprintLatch(
    private val pressAt: Float = 0.92f,
    private val releaseAt: Float = 0.82f,
) {
    /** Whether the movement stick is running right now. */
    var active = false
        private set

    private val press = pressAt.coerceIn(0.1f, 1f)
    private val release = releaseAt.coerceIn(0f, press - 0.01f)

    /** One sample of the stick, from -1..1 on each axis. Returns [active]. */
    fun update(x: Float, y: Float): Boolean {
        val magnitude = hypot(x, y)
        active = if (active) magnitude >= release else magnitude >= press
        return active
    }

    fun reset() {
        active = false
    }
}

/**
 * The fake shot, as PES 2017 actually performs it.
 *
 * The title's own manual lists "cancel pass / shot" as B, X or Y followed by A:
 * while the shot's power gauge is showing, pressing pass cancels the shot and
 * the player feints. It is not a shot and then a pass -- that would be a real
 * shot followed by a real pass, which is exactly what the gesture must avoid.
 *
 * So the gesture is read while the thumb is still down: the press starts on
 * shoot, a drag towards pass arms it, and the caller presses pass at that
 * moment, *before* the lift, while shoot is still held. The lift then releases
 * both together and sends nothing extra.
 *
 * The drag has to end up within about 45 degrees of the line from shoot to pass
 * and to have travelled [minTravelPx]: a thumb that wandered sideways is not a
 * fake shot, and a tap never arms one.
 *
 * One instance per pointer, because two thumbs can hold two buttons.
 */
class FakeShotGesture(
    /** How far the thumb has to travel before the gesture arms, in pixels. */
    private val minTravelPx: Float,
    /** How close to the shoot -> pass line the drag has to be (cosine). */
    private val cosine: Float = 0.70f,
) {
    /** Whether this gesture has already pressed pass. */
    var armed = false
        private set

    /**
     * One move sample. Returns true on the sample that arms the gesture, which
     * is when the caller must press pass; every later sample returns false, so
     * one drag presses pass once.
     */
    fun moved(
        startX: Float, startY: Float, x: Float, y: Float,
        shootX: Float, shootY: Float, passX: Float, passY: Float,
    ): Boolean {
        if (armed) return false
        val towardX = passX - shootX
        val towardY = passY - shootY
        val toward = hypot(towardX, towardY)
        if (toward < 1f) return false
        val dx = x - startX
        val dy = y - startY
        val travel = hypot(dx, dy)
        if (travel < minTravelPx) return false
        val alignment = (dx * towardX + dy * towardY) / (travel * toward)
        if (alignment < cosine) return false
        armed = true
        return true
    }

    fun reset() {
        armed = false
    }
}

/**
 * Which sector of the skill wheel a thumb is over, and when the wheel is open
 * at all.
 *
 * Eight sectors, 45 degrees each, starting at the right and running the way a
 * clock face's numbers do the other way: sector 0 right, 1 up-right, 2 up, 3
 * up-left, 4 left, 5 down-left, 6 down, 7 down-right. The screen's y grows
 * downward, so the angle is taken from -dy. The deadzone is what makes a
 * quick tap nothing at all: inside a quarter of the radius the thumb has not
 * said which move it wants, and the hold threshold is what keeps an ordinary
 * press from opening the wheel in the first place.
 */
object TrickWheel {
    /** How long the thumb has to stay down before the wheel opens. */
    const val HOLD_MS = 120L

    /** The fraction of the radius that is "no move chosen". */
    const val DEADZONE = 0.25f

    const val SECTORS = 8

    /** Degrees per sector. */
    private const val STEP_DEGREES = 360.0 / SECTORS

    /**
     * The sector under a thumb that is [dx], [dy] from where it landed, or -1
     * for the deadzone and for a radius too small to mean anything.
     */
    fun sector(dx: Float, dy: Float, radius: Float): Int {
        if (radius <= 0f) return -1
        if (dx * dx + dy * dy < (radius * DEADZONE) * (radius * DEADZONE)) return -1
        val degrees = Math.toDegrees(kotlin.math.atan2(-dy.toDouble(), dx.toDouble()))
        val fromRight = ((degrees % 360.0) + 360.0) % 360.0
        return (((fromRight + STEP_DEGREES / 2.0) / STEP_DEGREES).toInt()) % SECTORS
    }

    /** Whether a thumb held this long has become a wheel rather than a tap. */
    fun isHold(heldMs: Long): Boolean = heldMs >= HOLD_MS
}

/**
 * The shortest gap between two presses of the same button for the second to be
 * a press of its own rather than the bounce of the first. Two taps of sprint --
 * the speed burst -- are a fifth of a second apart and never touch this; a
 * chattering switch, or one press delivered twice by a driver, does.
 *
 * Only the press *pulse* is gated, never the hold: a suppressed pulse still
 * holds its bit, so a bounce can only cost a tap that was one edge anyway.
 */
class TapCadence(private val minGapMs: Long = 40L) {
    private var lastAt = Long.MIN_VALUE

    /** Whether a press at [nowMs] should also leave a pulse for the guest. */
    fun accept(nowMs: Long): Boolean {
        if (lastAt != Long.MIN_VALUE && nowMs - lastAt < minGapMs) return false
        lastAt = nowMs
        return true
    }

    fun reset() {
        lastAt = Long.MIN_VALUE
    }
}
