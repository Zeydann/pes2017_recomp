package com.pes2017.recomp

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The thumb-to-stick arithmetic both stick modes share (TouchMath.kt).
 *
 * The point of these checks is that a stick is normalised against the throw the
 * player can see: rim is full, beyond the rim is still full, the guest's Y
 * points up, and a resting thumb is an exact zero rather than sensor noise.
 */
class StickVectorTest {

    private fun vector(baseX: Float = 100f, baseY: Float = 100f, x: Float, y: Float, radius: Float = 50f) =
        StickVector.of(baseX, baseY, x, y, radius)

    @Test
    fun theRimIsFullDeflection() {
        val right = vector(x = 150f, y = 100f)
        assertEquals(1f, right[0], 1e-5f)
        assertEquals(0f, right[1], 1e-5f)
    }

    @Test
    fun beyondTheRimIsClampedNotAmplified() {
        val far = vector(x = 400f, y = 100f)
        assertEquals(1f, far[0], 1e-5f)
        assertEquals(0f, far[1], 1e-5f)
    }

    @Test
    fun screenUpIsGuestPlusY() {
        // Finger up from the base: the screen's Y falls, the guest's rises.
        val up = vector(x = 100f, y = 50f)
        assertEquals(0f, up[0], 1e-5f)
        assertEquals(1f, up[1], 1e-5f)
        val down = vector(x = 100f, y = 150f)
        assertEquals(-1f, down[1], 1e-5f)
    }

    @Test
    fun aRestingThumbIsExactlyZero() {
        val still = vector(x = 100.02f, y = 100f)
        assertEquals(0f, still[0], 0f)
        assertEquals(0f, still[1], 0f)
    }

    @Test
    fun aDiagonalKeepsItsDirection() {
        // Equal pushes on both axes: the magnitude is clamped, the angle is not.
        val diagonal = vector(x = 140f, y = 60f)
        assertEquals(kotlin.math.sqrt(0.5f), diagonal[0], 1e-4f)
        assertEquals(kotlin.math.sqrt(0.5f), diagonal[1], 1e-4f)
    }

    @Test
    fun aZeroRadiusIsNotADirection() {
        val none = vector(x = 140f, y = 140f, radius = 0f)
        assertTrue(none[0] == 0f && none[1] == 0f)
    }

    @Test
    fun aMovingBaseChangesTheSameThumb() {
        // The same finger is right of one base and left of another: this is
        // why a floating socket has to be fixed where the thumb landed.
        val fromLeft = StickVector.of(50f, 100f, 100f, 100f, 50f)
        val fromRight = StickVector.of(150f, 100f, 100f, 100f, 50f)
        assertEquals(1f, fromLeft[0], 1e-5f)
        assertEquals(-1f, fromRight[0], 1e-5f)
    }
}
