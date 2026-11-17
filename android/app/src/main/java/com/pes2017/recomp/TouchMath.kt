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
     * The title's own thumbstick deadzone, as a share of full deflection
     * (runtime/android/input.cpp, ToAxis). Nothing below it moves the player, so
     * a shaped throw starts here rather than at zero: a host deadzone on top of
     * it is dead travel the thumb can feel.
     */
    const val GUEST_FLOOR = 0.14f

    /**
     * [x] and [y] shaped together, as a vector: shaping each axis separately
     * bends diagonals and turns the deadzone into a square.
     *
     * Returns (0, 0) inside the deadzone, and (0, 0) again for a throw that
     * comes out as sub-pixel wobble -- a residual of 0.01 is still a nudge the
     * title acts on.
     */
    fun apply(
        x: Float, y: Float, deadzone: Float, exponent: Float, floor: Float = 0f,
    ): FloatArray {
        val dz = deadzone.coerceIn(0f, 0.9f)
        val curve = exponent.coerceIn(0.25f, 4f)
        val lift = floor.coerceIn(0f, 0.5f)
        val magnitude = hypot(x, y)
        if (!(magnitude > dz)) return floatArrayOf(0f, 0f)

        // Out to the rim at one, then what the curve does with the rest of the
        // throw: the deadzone is not left behind at the top, so full speed is
        // still reachable by a thumb that reaches the rim. [lift] starts what is
        // left at the first value the title acts on, so the two deadzones are
        // never added together.
        val reach = min(magnitude, 1f)
        val shaped = if (dz > 0f || curve != 1f) {
            val t = ((reach - dz) / (1f - dz)).coerceAtLeast(0f)
            lift + t.pow(curve) * (1f - lift)
        } else {
            // No deadzone and no curve is the console's own mapping: the thumb's
            // throw is the deflection, and the title's floor is the only
            // deadzone in the path.
            reach
        }
        val scale = shaped / magnitude
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
 * the socket's visible travel, so a thumb on the rim is full deflection and a
 * thumb past it is still full deflection rather than an out-of-range value the
 * shaping would have to fix. A second thumb uses the same base; nothing here
 * moves the base, which is the point -- a base that follows the thumb turns a
 * push into a drag and makes the deflection depend on how fast the thumb moved.
 */
object StickVector {

    /**
     * The guest's Y points up; the screen's points down.
     *
     * [limit] is how many radii the throw is measured to. It is 1 for every
     * stick drawn as one circle; the two-stage stick passes its ring's edge,
     * because the sprint zone lives past the socket and clamping at the rim
     * there would throw the ring away before anything could read it.
     */
    fun of(
        baseX: Float, baseY: Float, x: Float, y: Float, radius: Float, limit: Float = 1f,
    ): FloatArray {
        if (radius <= 0f) return floatArrayOf(0f, 0f)
        var dx = (x - baseX) / radius
        var dy = -(y - baseY) / radius
        val length = hypot(dx, dy)
        val cap = limit.coerceAtLeast(1f)
        if (length > cap) {
            dx = dx / length * cap
            dy = dy / length * cap
        }
        // Below this the thumb is resting, not pushing: a residual of 0.002 is
        // not a direction the title should act on.
        if (hypot(dx, dy) < 0.002f) return floatArrayOf(0f, 0f)
        return floatArrayOf(dx, dy)
    }
}

/**
 * The two-stage stick's numbers.
 *
 * One socket holds two zones. Everything inside the drawn rim is the movement
 * zone: 360 degrees of normal pace, no sprint. The ring outside it, out to
 * [OUTER_RADIUS], is the sprint zone, reached by swiping outward past the rim;
 * it is narrower than the circle it surrounds, and it is where RB comes from.
 *
 * The mapping is one scalar on a vector, so a direction never changes as the
 * thumb crosses the boundary. The inside tops out at [INNER_GAIN], a tenth under
 * full pace, and the ring buys that tenth back on its way to 1.0 -- which is the
 * only thing "higher sensitivity" can mean on an axis that already saturates.
 */
object StickStage {

    /** The drawn socket: the rim of the movement zone, in socket radii. */
    const val INNER_RADIUS = 1.0f

    /** The far edge of the sprint ring, in socket radii. */
    const val OUTER_RADIUS = 1.35f

    /**
     * Where the ring is entered and left, in socket radii. The band between them
     * is hysteresis: a thumb circling the rim crosses 1.0 constantly, and RB
     * flickering on every crossing would be a sprint the player did not ask for.
     */
    const val SPRINT_IN = 1.06f
    const val SPRINT_OUT = 0.94f

    /** How long the ring has to be held before RB goes down. */
    const val DWELL_MS = 70L

    /** What a thumb on the rim sends: a tenth under full pace, so the ring adds something. */
    const val INNER_GAIN = 0.90f

    /** The deflection for a thumb [distance] socket radii from the centre. */
    fun magnitude(distance: Float): Float {
        if (!(distance > 0f)) return 0f
        if (distance <= INNER_RADIUS) return distance * INNER_GAIN
        val t = ((distance - INNER_RADIUS) / (OUTER_RADIUS - INNER_RADIUS)).coerceAtMost(1f)
        return INNER_GAIN + (1f - INNER_GAIN) * t
    }
}

/**
 * Which zone one finger's movement stick is in, and what that sends.
 *
 * The stage belongs to the pointer, not to the element: a second thumb sharing
 * the socket cannot flip the first one's sprint, and a lift only clears its own
 * finger. Entering the ring needs [StickStage.DWELL_MS] of it; leaving it does
 * not wait for anything, because a sprint that outlives the thumb is worse than
 * one that starts late. Every cancellation path calls [reset].
 */
class TwoStageStick(private val dwellMs: Long = StickStage.DWELL_MS) {

    /** One sample of a finger's offset: the vector to send, and whether the ring is sprinting. */
    class Sample(val x: Float, val y: Float, val sprint: Boolean)

    private val sprinting = HashMap<Int, Boolean>()
    private val inRingSince = HashMap<Int, Long>()

    fun isSprinting(pointerId: Int): Boolean = sprinting[pointerId] == true

    /**
     * [dx] and [dy] are the thumb's offset from the socket centre in socket
     * radii, unclamped: the ring lives past 1.0, so a clamp before this point
     * would throw the sprint away.
     */
    fun sample(pointerId: Int, dx: Float, dy: Float, nowMs: Long): Sample {
        val distance = hypot(dx, dy)
        val sprint = stage(pointerId, distance, nowMs)
        // Below this the thumb is resting, not pushing -- the same residual
        // rule the rest of the stick path uses.
        if (distance < 0.002f) return Sample(0f, 0f, sprint)
        val scale = StickStage.magnitude(distance) / distance
        return Sample(dx * scale, dy * scale, sprint)
    }

    private fun stage(pointerId: Int, distance: Float, nowMs: Long): Boolean {
        if (sprinting[pointerId] == true) {
            if (distance <= StickStage.SPRINT_OUT) {
                sprinting.remove(pointerId)
                inRingSince.remove(pointerId)
            }
            return sprinting[pointerId] == true
        }
        // Short of the entry point the dwell is thrown away: a thumb that dips
        // back out of the band has not held the ring, and its clock starts again.
        if (distance < StickStage.SPRINT_IN) {
            inRingSince.remove(pointerId)
            return false
        }
        val since = inRingSince[pointerId]
        if (since == null) {
            inRingSince[pointerId] = nowMs
            return false
        }
        if (nowMs - since >= dwellMs) {
            sprinting[pointerId] = true
            return true
        }
        return false
    }

    /** One finger lifted: its stage goes with it, and nobody else's does. */
    fun release(pointerId: Int) {
        sprinting.remove(pointerId)
        inRingSince.remove(pointerId)
    }

    /** Every finger up: pause, focus loss, the editor, a cancel. */
    fun reset() {
        sprinting.clear()
        inRingSince.clear()
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
