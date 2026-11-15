package com.pes2017.recomp

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The d-pad's idle clock, without a device: visible for four quiet seconds,
 * then a short fade, then gone until a touch wakes it. The visible window is
 * the requested three-to-five seconds, and the fade is deliberately shorter
 * than a blink-and-miss.
 */
class DpadAutoHideTest {

    private val idle = DpadAutoHide.IDLE_MS
    private val fade = DpadAutoHide.FADE_MS

    @Test
    fun itStaysFullyVisibleInsideTheIdleWindow() {
        val clock = DpadAutoHide()
        clock.reset(1_000L)
        assertEquals(1f, clock.alpha(1_000L), 0f)
        assertEquals(1f, clock.alpha(1_000L + idle - 1), 0f)
        assertFalse(clock.hidden)
    }

    @Test
    fun itStartsFadingAtFourSeconds() {
        val clock = DpadAutoHide()
        clock.reset(0L)
        assertFalse("nothing happens before the idle window", clock.tick(idle - 1))
        assertTrue("the fade starts exactly at the idle window", clock.tick(idle))
        assertTrue(clock.fading(idle))
    }

    @Test
    fun theFadeIsGradualAndEndsHidden() {
        val clock = DpadAutoHide()
        clock.reset(0L)
        clock.tick(idle)

        val halfway = idle + fade / 2
        assertEquals(0.5f, clock.alpha(halfway), 0.02f)
        clock.tick(idle + fade - 1)
        assertFalse("not gone a millisecond early", clock.hidden)
        clock.tick(idle + fade)
        assertTrue(clock.hidden)
        assertEquals(0f, clock.alpha(idle + fade), 0f)
    }

    @Test
    fun aTouchBringsItStraightBack() {
        val clock = DpadAutoHide()
        clock.reset(0L)
        clock.tick(idle)
        clock.tick(idle + fade)
        assertTrue(clock.hidden)

        clock.reset(idle + fade + 10_000L)
        assertFalse(clock.hidden)
        assertEquals(1f, clock.alpha(idle + fade + 10_000L), 0f)
    }

    @Test
    fun theRemainingIdleTimeIsCountedFromTheLastTouch() {
        val clock = DpadAutoHide()
        clock.reset(5_000L)
        assertEquals(idle, clock.idleRemaining(5_000L))
        assertEquals(idle / 2, clock.idleRemaining(5_000L + idle / 2))
        assertEquals(0L, clock.idleRemaining(5_000L + idle + 5_000L))
    }

    @Test
    fun aTouchDuringTheFadeStopsIt() {
        val clock = DpadAutoHide()
        clock.reset(0L)
        clock.tick(idle)
        assertTrue(clock.fading(idle + 10L))
        clock.reset(idle + 20L)
        assertFalse(clock.fading(idle + 20L))
        assertEquals(1f, clock.alpha(idle + 20L), 0f)
    }
}
