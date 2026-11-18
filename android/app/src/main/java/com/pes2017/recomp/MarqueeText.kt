package com.pes2017.recomp

import android.content.Context
import android.text.TextUtils
import android.util.AttributeSet
import android.view.Gravity
import androidx.appcompat.widget.AppCompatTextView

/**
 * One line of menu text that scrolls itself when — and only when — it does not
 * fit.
 *
 * A menu row is one line tall, so a long name or a long value used to end in an
 * ellipsis: the player could not read what they had bound, which saved pad they
 * were looking at, or which page they came from. When the text is wider than the
 * space the row gives it, it now marquees at the platform's own speed and keeps
 * going for as long as the row is on screen; a tap, the D-pad or the lift does
 * not have to start it.
 *
 * Text that fits is left exactly as it was — same alignment, no movement, no
 * animation and no extra frames — so the menu only moves where it would
 * otherwise be cut. The test is the measured width of the text against the
 * view's own width, so it holds at every resolution and aspect ratio, and it
 * flips back to still text if a resize or a rotation makes the same text fit.
 */
class MarqueeText(context: Context, attrs: AttributeSet? = null) :
    AppCompatTextView(context, attrs) {

    /** Where the text sits while it is not scrolling, restored when it fits. */
    private var restingGravity: Int? = null
    private var scrolling = false

    init {
        // Marquee is a single-line behaviour: the line is laid out once and
        // slid, rather than wrapped the way a paragraph would be.
        isSingleLine = true
        maxLines = 1
        ellipsize = TextUtils.TruncateAt.END
    }

    override fun onLayout(changed: Boolean, left: Int, top: Int, right: Int, bottom: Int) {
        super.onLayout(changed, left, top, right, bottom)
        // Every pass that can change the answer — a new label, a wider row, a
        // smaller font — arrives here, so the decision is never stale.
        updateScrolling()
    }

    private fun updateScrolling() {
        if (width <= 0) return
        val available = width - compoundPaddingLeft - compoundPaddingRight
        if (available <= 0) return
        // Measured as it is actually drawn: the row's names are all-caps and
        // letter-spaced, and the plain string underneath is narrower than the
        // glyphs on screen, which would let a clipped label pass as fitting.
        val shown = transformationMethod?.getTransformation(text, this) ?: text
        val tracking = letterSpacing * textSize * shown.length
        val shouldScroll = paint.measureText(shown, 0, shown.length) + tracking > available
        if (shouldScroll == scrolling) return
        scrolling = shouldScroll
        if (shouldScroll) {
            // A slid line starts at the left edge: a right-aligned over-long
            // value would begin its scroll somewhere inside the text.
            if (restingGravity == null) restingGravity = gravity
            gravity = (gravity and Gravity.VERTICAL_GRAVITY_MASK) or Gravity.START
            ellipsize = TextUtils.TruncateAt.MARQUEE
            marqueeRepeatLimit = -1
            isSelected = true
        } else {
            isSelected = false
            ellipsize = TextUtils.TruncateAt.END
            restingGravity?.let { gravity = it }
            restingGravity = null
        }
    }
}
