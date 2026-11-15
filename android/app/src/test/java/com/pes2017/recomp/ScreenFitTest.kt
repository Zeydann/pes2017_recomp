package com.pes2017.recomp

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Fitting a pad to the screen it is actually on.
 *
 * The cases are the shapes phones really have: a punch-hole on the long edge in
 * landscape, a notch on the short edge, the gesture bar along the bottom, and a
 * small window where the pad has to give something up. The rule under test is
 * always the same one -- the whole control, not its centre, stays clear of the
 * insets -- and that fitting never moves a control on a screen with none.
 */
class ScreenFitTest {

    private fun fit(
        widthDp: Float = 800f,
        heightDp: Float = 360f,
        left: Float = 0f,
        top: Float = 0f,
        right: Float = 0f,
        bottom: Float = 0f,
    ) = ScreenFit(widthDp, heightDp, SafeAreaDp(left, top, right, bottom))

    @Test
    fun aScreenWithoutInsetsLeavesEveryControlWhereItWas() {
        val screen = fit()
        val fitted = screen.fit(0.5f, 0.5f, 64f)

        assertEquals(400f, fitted.xDp, 0.001f)
        assertEquals(180f, fitted.yDp, 0.001f)
        assertEquals(64f, fitted.sizeDp, 0.001f)
        assertFalse(screen.hasInsets)
    }

    @Test
    fun aLeftCutoutPushesTheWholeCircleClearOfIt() {
        // 0.02 of 800 is 16 dp, which is inside a 60 dp cutout; the centre has to
        // move to 60 + half the button, not just past 60.
        val fitted = fit(left = 60f).fit(0.02f, 0.5f, 64f)

        assertEquals(60f + 32f, fitted.xDp, 0.001f)
    }

    @Test
    fun aTopCutoutDoesTheSameVertically() {
        val fitted = fit(top = 30f).fit(0.5f, 0.02f, 48f)

        assertEquals(30f + 24f, fitted.yDp, 0.001f)
    }

    @Test
    fun theGestureBarKeepsTheBottomRowAboveIt() {
        // 0.98 of 360 is 352.8, past the top of a 32 dp bar.
        val fitted = fit(bottom = 32f).fit(0.5f, 0.98f, 64f)

        assertEquals(360f - 32f - 32f, fitted.yDp, 0.001f)
    }

    @Test
    fun aRightCutoutIsHonouredToo() {
        val fitted = fit(right = 48f).fit(0.98f, 0.5f, 64f)

        assertEquals(800f - 48f - 32f, fitted.xDp, 0.001f)
    }

    @Test
    fun aControlIsOnlyEverShrunkByAFullSafeArea() {
        // A tablet has more room than the layout asked for: no upscaling, or the
        // same file would mean different things on different screens.
        assertEquals(64f, fit(widthDp = 1600f, heightDp = 900f).sizeDp(64f), 0.001f)
        // A window barely holds the pad: the smaller side is the limit.
        assertEquals(180f, fit(heightDp = 180f).sizeDp(900f), 0.001f)
    }

    @Test
    fun aTinySafeAreaCannotShrinkAControlIntoNothing() {
        val tiny = fit(widthDp = 60f, heightDp = 60f, left = 10f, right = 10f, top = 10f, bottom = 10f)
        assertEquals(ScreenFit.MINIMUM_DP, tiny.sizeDp(200f), 0.001f)
    }

    @Test
    fun fittingUsesTheSafeSizeForThePlaceItFitsInto() {
        // The fitted size is what the edges are measured with, so a control
        // clamped on one axis is not then pushed out on the other.
        val screen = fit(left = 40f, bottom = 40f)
        val fitted = screen.fit(0.01f, 0.99f, 900f)
        assertTrue(fitted.sizeDp <= screen.safeHeightDp)
        assertTrue("left edge clear", fitted.xDp - fitted.sizeDp / 2f >= 40f - 0.001f)
        assertTrue("bottom edge clear", fitted.yDp + fitted.sizeDp / 2f <= 360f - 40f + 0.001f)
    }

    @Test
    fun insetsAreReportedOnlyWhenThereAreSome() {
        assertTrue(fit(left = 1f).hasInsets)
        assertFalse(fit().hasInsets)
    }

    @Test
    fun aSafeAreaWiderThanTheScreenStillAnswersWithTheScreen() {
        // Insets that cannot both be honoured (a keyboard reported as a side
        // inset, a broken window metric) must not invert the safe area, produce
        // NaN, or throw a control off the edge it does know about.
        val broken = fit(widthDp = 100f, heightDp = 100f, left = 80f, right = 80f)
        assertTrue(broken.safeWidthDp > 0f)
        val x = broken.xDp(0.5f, 20f)
        assertFalse(x.isNaN())
        assertTrue("kept past the cutout it can honour", x >= 80f - 0.001f)
    }
}
