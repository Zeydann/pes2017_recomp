package com.pes2017.recomp

import kotlin.math.max
import kotlin.math.min

/**
 * The part of the window a control may safely sit in, in dp.
 *
 * The match draws into the cutout, so the window is the whole screen; what lies
 * under a punch-hole camera, a rounded corner or the gesture bar is still part
 * of it, and a control there is a control the player cannot see or reach. These
 * are the insets the system reports for `systemBars() | displayCutout()`,
 * converted to dp because the pad is laid out in dp.
 */
data class SafeAreaDp(
    val left: Float = 0f,
    val top: Float = 0f,
    val right: Float = 0f,
    val bottom: Float = 0f,
) {
    val any: Boolean get() = left > 0f || top > 0f || right > 0f || bottom > 0f

    companion object {
        val NONE = SafeAreaDp()
    }
}

/** A control's place and size after fitting, in dp. */
data class FittedControl(val xDp: Float, val yDp: Float, val sizeDp: Float)

/**
 * Fits controls into the safe area of a screen.
 *
 * Positions in a layout are fractions of the window and sizes are dp, which is
 * what lets one layout work on every phone -- but neither number knows where
 * the cutout is. Fitting is therefore a *view* of the layout, not a rewrite of
 * it: [xDp] and [yDp] clamp a control's centre so the whole circle stays inside
 * the safe rectangle, and [sizeDp] shrinks one only when the safe area is
 * genuinely too small to hold it. The layout file is never written back with
 * fitted values, so a pad edited on a phone with a wide notch is the same pad
 * when the same file is opened on one without it.
 *
 * Free of Android types on purpose: [ScreenFitTest] checks every edge case
 * without a device.
 */
class ScreenFit(
    val widthDp: Float,
    val heightDp: Float,
    val insets: SafeAreaDp = SafeAreaDp.NONE,
) {
    /** The width left for controls once the insets are taken out. */
    val safeWidthDp: Float get() = max(1f, widthDp - insets.left - insets.right)

    /** The height left for controls once the insets are taken out. */
    val safeHeightDp: Float get() = max(1f, heightDp - insets.top - insets.bottom)

    val hasInsets: Boolean get() = insets.any

    /**
     * The size a control may actually have: what the layout asked for, or the
     * safe area's smaller side when that is all there is. It never grows a
     * control -- a wider phone must not turn a 64 dp button into a 90 dp one,
     * or a layout stops meaning the same thing on every screen.
     */
    fun sizeDp(requestedDp: Float): Float =
        requestedDp.coerceAtMost(max(MINIMUM_DP, min(safeWidthDp, safeHeightDp)))

    /**
     * The centre's x, in dp, for a control of [sizeDp] whose layout fraction is
     * [fracDp]. Half the control is kept clear of each inset, so the circle --
     * not just its centre -- stays in the safe area.
     */
    fun xDp(fracDp: Float, sizeDp: Float): Float {
        val half = sizeDp.coerceAtLeast(0f) / 2f
        val low = insets.left + half
        val high = max(low, widthDp - insets.right - half)
        return (fracDp * widthDp).coerceIn(low, high)
    }

    /** The centre's y, in dp: the same rule as [xDp]. */
    fun yDp(fracDp: Float, sizeDp: Float): Float {
        val half = sizeDp.coerceAtLeast(0f) / 2f
        val low = insets.top + half
        val high = max(low, heightDp - insets.bottom - half)
        return (fracDp * heightDp).coerceIn(low, high)
    }

    /** A control fitted whole: its safe size first, then its place for that size. */
    fun fit(xFrac: Float, yFrac: Float, requestedSizeDp: Float): FittedControl {
        val size = sizeDp(requestedSizeDp)
        return FittedControl(xDp(xFrac, size), yDp(yFrac, size), size)
    }

    companion object {
        /**
         * The floor a control is never shrunk below, in dp. Below this a button
         * is no longer a thumb target, and a safe area that would demand it is
         * not a screen this pad is meant for.
         */
        const val MINIMUM_DP = 44f
    }
}
