package com.pes2017.recomp

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * The Graphics & performance page's cycles, checked as arithmetic: Build 24's
 * option lists, one option a press, and the last press wrapping to the first.
 */
class GraphicsOptionsTest {

    @Test
    fun internalResolutionKeepsItsThreeOptions() {
        assertArrayEquals(intArrayOf(1, 2, 3), GraphicsOptions.INTERNAL_SCALE)
    }

    @Test
    fun internalResolutionCyclesBackTo1x() {
        assertEquals(2, GraphicsOptions.step(GraphicsOptions.INTERNAL_SCALE, 1, 1))
        assertEquals(3, GraphicsOptions.step(GraphicsOptions.INTERNAL_SCALE, 2, 1))
        // The press after 3x is 1x: the one the page used to swallow.
        assertEquals(1, GraphicsOptions.step(GraphicsOptions.INTERNAL_SCALE, 3, 1))
    }

    @Test
    fun sceneResolutionKeepsBuild24sFourOptions() {
        assertArrayEquals(intArrayOf(50, 60, 75, 100), GraphicsOptions.SCENE_PERCENT)
    }

    @Test
    fun sceneResolutionCycles50To100AndBack() {
        var scene = 50
        val seen = ArrayList<Int>()
        repeat(4) {
            seen += scene
            scene = GraphicsOptions.step(GraphicsOptions.SCENE_PERCENT, scene, 1)
        }
        assertEquals(listOf(50, 60, 75, 100), seen)
        // 100% is reachable, and the press after it is 50%.
        assertEquals(50, scene)
        // Backwards wraps the other way.
        assertEquals(100, GraphicsOptions.step(GraphicsOptions.SCENE_PERCENT, 50, -1))
    }

    @Test
    fun frameRateLimitKeepsEveryFiveFpsStep() {
        assertArrayEquals(
            intArrayOf(30, 35, 40, 45, 50, 55, 60),
            GraphicsOptions.FPS_LIMIT,
        )
    }

    @Test
    fun frameRateLimitWalksEveryValueThen60PressesStraightTo30() {
        var fps = 30
        val seen = ArrayList<Int>()
        repeat(GraphicsOptions.FPS_LIMIT.size) {
            seen += fps
            fps = GraphicsOptions.step(GraphicsOptions.FPS_LIMIT, fps, 1)
        }
        // 30 -> every intermediate value, in order, -> 60.
        assertEquals(listOf(30, 35, 40, 45, 50, 55, 60), seen)
        // The very next press, with 60 on screen, is 30 -- not 60 again.
        assertEquals(30, fps)
        // And one press is exactly one option, never a jump above 60.
        assertEquals(30, GraphicsOptions.step(GraphicsOptions.FPS_LIMIT, 60, 1))
    }

    @Test
    fun frameRateLimitWrapsBackwardsToo() {
        assertEquals(60, GraphicsOptions.step(GraphicsOptions.FPS_LIMIT, 30, -1))
        assertEquals(55, GraphicsOptions.step(GraphicsOptions.FPS_LIMIT, 60, -1))
    }

    @Test
    fun aSavedValueNotOnTheListBecomesTheNextOptionAndStillMovesOneStep() {
        // An older build could save 55% scene and 5-step render multipliers; the
        // page must still land on one of the four options and move by one.
        assertEquals(60, GraphicsOptions.normalize(GraphicsOptions.SCENE_PERCENT, 55))
        assertEquals(100, GraphicsOptions.normalize(GraphicsOptions.SCENE_PERCENT, 100))
        assertEquals(50, GraphicsOptions.normalize(GraphicsOptions.SCENE_PERCENT, 49))
        assertEquals(75, GraphicsOptions.step(GraphicsOptions.SCENE_PERCENT, 55, 1))
        assertEquals(1, GraphicsOptions.normalize(GraphicsOptions.INTERNAL_SCALE, 0))
        assertEquals(3, GraphicsOptions.normalize(GraphicsOptions.INTERNAL_SCALE, 4))
        assertEquals(60, GraphicsOptions.normalize(GraphicsOptions.FPS_LIMIT, 58))
        assertEquals(30, GraphicsOptions.normalize(GraphicsOptions.FPS_LIMIT, 1))
    }

    @Test
    fun everyPressAdvancesExactlyOneOptionAndAFullTurnReturns() {
        for (options in listOf(
            GraphicsOptions.INTERNAL_SCALE,
            GraphicsOptions.SCENE_PERCENT,
            GraphicsOptions.FPS_LIMIT,
        )) {
            for (start in options) {
                var value = start
                for (step in 1..options.size) {
                    val next = GraphicsOptions.step(options, value, 1)
                    assertEquals(options[(options.indexOf(value) + 1) % options.size], next)
                    value = next
                }
                // One full turn of the list is back where it started.
                assertEquals(start, value)
            }
        }
    }
}
