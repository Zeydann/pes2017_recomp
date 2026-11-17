package com.pes2017.recomp

import kotlin.math.hypot
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * What a thumb on a stick sends, through the same arithmetic the overlay uses.
 *
 * The overlay itself needs a phone: it is a View, it owns a Canvas and it reads
 * touches from the system. The three pieces it hands a throw to are pure, though,
 * and they are the whole path from glass to `nativeTouchState`: [StickVector]
 * turns the thumb's offset into the pad's -1..1 vector, [TwoStageStick] maps it
 * for stage 2, and [StickShape] applies the player's deadzone and curve. If any
 * of them can come out as zero for a real throw, the player stands still while
 * the thumb is out -- so these tests are the ones that would have caught it.
 */
class StickAxesTest {

    private val radius = 150f      // the drawn socket's radius, in the pad's units
    private val baseX = 190f
    private val baseY = 530f

    /** The guest's Y points up: a push up the screen is a positive Y. */
    private fun pointUp(fraction: Float): Pair<Float, Float> =
        baseX to (baseY - fraction * radius)

    /** Everything the overlay does to one sample, in the order it does it. */
    private fun modeOne(x: Float, y: Float): FloatArray {
        val raw = StickVector.of(baseX, baseY, x, y, radius)
        val axes = StickShape.apply(
            raw[0], raw[1], Prefs.DEFAULT_STICK_DEADZONE, Prefs.DEFAULT_STICK_CURVE,
            StickShape.GUEST_FLOOR,
        )
        return floatArrayOf(axes[0], axes[1])
    }

    private fun modeTwo(x: Float, y: Float, nowMs: Long = 1_000L, stick: TwoStageStick = TwoStageStick()):
        TwoStageStick.Sample {
        val raw = StickVector.of(baseX, baseY, x, y, radius, StickStage.OUTER_RADIUS)
        return stick.sample(1, raw[0], raw[1], nowMs)
    }

    // ---- stage 1: the plain stick -------------------------------------------

    @Test
    fun aThumbOffTheCentreMovesThePlayerInStageOne() {
        // Distances a player actually holds, not just the rim.
        for (fraction in listOf(0.10f, 0.25f, 0.50f, 0.75f, 1.00f)) {
            val (x, y) = pointUp(fraction)
            val axes = modeOne(x, y)
            val length = hypot(axes[0], axes[1])
            assertTrue("a throw of $fraction R sends $length", length > 0.05f)
            assertTrue("and it is not over full deflection: $length", length <= 1.001f)
            // Up the screen is still up: the shaping must not bend the direction.
            assertTrue("up stays up at $fraction R", axes[1] > 0f)
            assertEquals("no sideways leak at $fraction R", 0f, axes[0], 0.0001f)
        }
    }

    @Test
    fun theThrowsGrowWithTheThumb() {
        var last = -1f
        for (fraction in listOf(0.20f, 0.40f, 0.60f, 0.80f, 1.00f)) {
            val (x, y) = pointUp(fraction)
            val axes = modeOne(x, y)
            val length = hypot(axes[0], axes[1])
            assertTrue("$fraction R ($length) should out-reach the last one ($last)", length > last)
            last = length
        }
    }

    @Test
    fun bothSticksUseTheSameArithmetic() {
        // The right stick differs only in its throw and its defaults; a thumb at
        // the same share of its rim sends the same share of deflection.
        val (x, y) = pointUp(0.60f)
        val left = modeOne(x, y)
        val rightRadius = 50f
        val rightRaw = StickVector.of(baseX, baseY, baseX + 0.60f * rightRadius, baseY, rightRadius)
        val right = StickShape.apply(
            rightRaw[0], rightRaw[1],
            Prefs.DEFAULT_RIGHT_STICK_DEADZONE, Prefs.DEFAULT_RIGHT_STICK_CURVE,
        )
        assertTrue("left ${left.toList()}", hypot(left[0], left[1]) > 0.05f)
        assertTrue("right ${right.toList()}", hypot(right[0], right[1]) > 0.05f)
        assertEquals(left[0], 0f, 0.0001f)
        assertEquals(right[1], 0f, 0.0001f)
    }

    @Test
    fun aRestingThumbSendsNothing() {
        // Only the centre is silent: the guest's own 14% floor is the deadzone,
        // and stacking more on top of it is dead travel the thumb can feel.
        val axes = modeOne(baseX, baseY)
        assertEquals(0f, axes[0], 0f)
        assertEquals(0f, axes[1], 0f)
    }

    // ---- stage 2: the movement circle and the ring --------------------------

    @Test
    fun theRimOfStageTwoSendsNineTenthsAndStillMovesThePlayer() {
        val (x, y) = pointUp(1.00f)
        val sample = modeTwo(x, y)
        val length = hypot(sample.x, sample.y)
        assertEquals(StickStage.INNER_GAIN, length, 0.0005f)
        assertTrue("the rim is movement, not idle", sample.x > -0.001f && length > 0.5f)
        assertTrue("and it is not a sprint on its own", !sample.sprint)
    }

    @Test
    fun aThumbPastTheRimIsStillRead() {
        // The ring is real travel: past 1.0 R the stick keeps answering rather
        // than clamping dead at the socket's edge.
        val (x, y) = pointUp(1.35f)
        val stick = TwoStageStick()
        val first = modeTwo(x, y, nowMs = 5_000L, stick = stick)
        assertEquals(1f, hypot(first.x, first.y), 0.0005f)
        // The ring has to be held for its dwell before it is a sprint.
        assertTrue("held for the dwell, the ring runs",
            modeTwo(x, y, nowMs = 5_000L + StickStage.DWELL_MS, stick = stick).sprint)
    }

    @Test
    fun anInsideThrowKeepsItsDirectionInStageTwo() {
        for (fraction in listOf(0.20f, 0.50f, 0.90f)) {
            val sample = modeTwo(baseX + fraction * radius, baseY + fraction * radius)
            assertTrue("right and down at $fraction R", sample.x > 0f && sample.y < 0f)
            assertEquals("a diagonal is not bent", sample.x, -sample.y, 0.0005f)
        }
    }

    @Test
    fun theStageOneThrowAndTheStageTwoThrowAgreeAtTheRim() {
        // The switch changes the top tenth of the throw, not whether the stick
        // works: inside the socket both send the same direction.
        val (x, y) = pointUp(0.80f)
        val one = modeOne(x, y)
        val two = modeTwo(x, y)
        assertEquals(one[1] / hypot(one[0], one[1]), two.y / hypot(two.x, two.y), 0.0005f)
        assertTrue("both are real throws", hypot(one[0], one[1]) > 0.05f &&
            hypot(two.x, two.y) > 0.05f)
    }
}
