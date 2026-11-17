package com.pes2017.recomp

import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.min
import kotlin.math.pow

/**
 * The two pieces of stick behaviour a matching runtime owns, kept here as well
 * so the controls do not depend on which engine the apk was packaged with.
 *
 * runtime/stick_curve.h and runtime/input_stick_arrows.h are compiled into
 * libpes17.so, and the Android build packages a libpes17.so built elsewhere.
 * An engine from before those files has neither behaviour in it: the stick
 * jumps from standing to a jog on the first pixel, and a menu that only takes
 * the d-pad cannot be reached with a thumb at all. Both are arithmetic on a
 * vector the finger has already made, so the app can do them before the state
 * is sent, and the engine's own copies are used instead whenever the packaged
 * runtime answers for them (NativeBridge.stickSettings,
 * NativeBridge.dpadSynthesis). Exactly one side does it, so nothing is shaped
 * or doubled twice.
 */

/**
 * How a touch stick's throw becomes a deflection (runtime/stick_curve.h).
 *
 * A touchscreen stick has no spring and no travel to feel, so linear-from-zero
 * reads as a jump the moment the thumb moves. A radial deadzone stops a resting
 * thumb nudging the player off the spot, and a curve on what is left above 1 is
 * finer around the middle, where a walking pace lives.
 */
object StickShape {

    /**
     * [x] and [y] shaped together, as a vector: shaping each axis separately
     * bends diagonals and turns the deadzone into a square.
     *
     * Returns (0, 0) inside the deadzone, and (0, 0) again for a throw that
     * comes out as sub-pixel wobble -- a residual of 0.01 is still a nudge the
     * title acts on.
     */
    fun apply(x: Float, y: Float, deadzone: Float, exponent: Float): FloatArray {
        val dz = deadzone.coerceIn(0f, 0.9f)
        val curve = exponent.coerceIn(0.25f, 4f)
        val magnitude = hypot(x, y)
        if (!(magnitude > dz)) return floatArrayOf(0f, 0f)

        // Out to the rim at one, then what the curve does with the rest of the
        // throw: the deadzone is not left behind at the top, so full speed is
        // still reachable by a thumb that reaches the rim.
        val reach = min(magnitude, 1f)
        val t = ((reach - dz) / (1f - dz)).coerceAtLeast(0f)
        val scale = t.pow(curve) / magnitude
        val sx = x * scale
        val sy = y * scale

        if (hypot(sx, sy) < 0.002f) return floatArrayOf(0f, 0f)
        return floatArrayOf(sx, sy)
    }
}

/**
 * A thumb's offset from a stick's socket, as the pad's -1..1 vector.
 *
 * Both stick modes are this one piece of arithmetic: Fixed measures from the
 * socket the layout drew, Move from the point the thumb landed on. The throw is
 * whatever the pad measures against -- the whole socket, or the knob's own
 * travel when [Prefs.travelMatchesKnob] is on -- so a thumb at the end of that
 * travel is full deflection and a thumb past it is still full deflection rather
 * than an out-of-range value the shaping would have to fix. A second thumb uses
 * the same base; nothing here moves the base, which is the point -- a base that
 * follows the thumb turns a push into a drag and makes the deflection depend on
 * how fast the thumb moved.
 */
object StickVector {

    /** The guest's Y points up; the screen's points down. */
    fun of(baseX: Float, baseY: Float, x: Float, y: Float, radius: Float): FloatArray {
        if (radius <= 0f) return floatArrayOf(0f, 0f)
        var dx = (x - baseX) / radius
        var dy = -(y - baseY) / radius
        val length = hypot(dx, dy)
        if (length > 1f) {
            dx /= length
            dy /= length
        }
        // Below this the thumb is resting, not pushing: a residual of 0.002 is
        // not a direction the title should act on.
        if (hypot(dx, dy) < 0.002f) return floatArrayOf(0f, 0f)
        return floatArrayOf(dx, dy)
    }
}

/**
 * The left stick standing in for the d-pad (runtime/input_stick_arrows.h),
 * which is how a screen that only accepts the d-pad is reached with a thumb.
 *
 * Press at 0.60 of full deflection, release at 0.42. A thumb resting exactly at
 * a threshold would chatter the bit every push, and a worn stick dithers around
 * its own flat zone; the gap between the two points is what stops both. One
 * latch per direction, carried across pushes by the caller.
 */
class StickArrows {

    private var up = false
    private var down = false
    private var left = false
    private var right = false

    fun any(): Boolean = up || down || left || right

    fun clear() {
        up = false
        down = false
        left = false
        right = false
    }

    /** The d-pad bits this throw is standing in for, from -1..1 stick values. */
    fun bits(x: Float, y: Float): Int {
        right = edge(x, +1, right)
        left = edge(x, -1, left)
        up = edge(y, +1, up)
        down = edge(y, -1, down)

        // A pad has no diagonal. A thumb in the corner of a socket is aiming at
        // one direction, not two, and two bits is how pressing down in a menu
        // also sent right and moved the tab bar beside the list.
        if ((up || down) && (left || right)) {
            if (abs(x) <= abs(y)) {
                left = false
                right = false
            } else {
                up = false
                down = false
            }
        }

        var bits = 0
        if (up) bits = bits or NativeBridge.UP
        if (down) bits = bits or NativeBridge.DOWN
        if (left) bits = bits or NativeBridge.LEFT
        if (right) bits = bits or NativeBridge.RIGHT
        return bits
    }

    /**
     * One direction of one axis: set at the press point, kept until the stick
     * comes back inside the release ring or swings the other way.
     */
    private fun edge(value: Float, direction: Int, held: Boolean): Boolean {
        val magnitude = abs(value)
        if (held) return !(magnitude <= RELEASE || sign(value) != direction)
        return magnitude >= PRESS && sign(value) == direction
    }

    private fun sign(value: Float): Int = if (value > 0f) 1 else if (value < 0f) -1 else 0

    private companion object {
        /** 0.60 and 0.42 of a full throw, as the engine's 16-bit axes have them. */
        const val PRESS = 0.60f
        const val RELEASE = 0.42f
    }
}
