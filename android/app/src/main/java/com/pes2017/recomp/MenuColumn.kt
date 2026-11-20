package com.pes2017.recomp

import android.content.Context
import android.util.AttributeSet
import android.view.View
import android.widget.LinearLayout

/**
 * The launcher's list of entries: equal shares of the screen, but never less
 * than a row needs.
 *
 * The column used to be a plain LinearLayout with every entry on `weight=1`
 * and `height=0dp`, which means "split the height equally" and nothing else.
 * On a phone whose list is tall enough that is the PES menu's look, and it is
 * the look this keeps. On a shorter one -- landscape, a small screen, or the
 * portrait panel taking more room than the list -- the share drops below the
 * height of the text, and a Button clips its own text at its bounds. Latin
 * fits; Arabic, whose Naskh face sets taller than the Latin line and whose dots
 * and descenders sit outside it, got its letters cut at the top and bottom of
 * every row. That is the clipping, not the translation.
 *
 * So each weighted child is measured at the height its own text needs first,
 * and only the *leftover* is handed out by weight. If there is no leftover the
 * rows keep their natural height and the ScrollView around this column scrolls,
 * so a label is never squeezed below what it needs and never cut.
 */
class MenuColumn @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
) : LinearLayout(context, attrs) {

    init {
        orientation = VERTICAL
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val bounded = MeasureSpec.getMode(heightMeasureSpec) != MeasureSpec.UNSPECIFIED
        val available = if (bounded) MeasureSpec.getSize(heightMeasureSpec) else 0

        var natural = paddingTop + paddingBottom
        var weights = 0f
        for (index in 0 until childCount) {
            val child = getChildAt(index)
            if (child.visibility == View.GONE) continue
            val params = child.layoutParams as LayoutParams
            val width = getChildMeasureSpec(
                widthMeasureSpec, paddingLeft + paddingRight, params.width,
            )
            // A weighted entry arrives as height=0: measured with no bound it
            // reports the height its text actually needs, which is the floor.
            val height = if (params.height == 0 && params.weight > 0f) {
                MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED)
            } else {
                getChildMeasureSpec(
                    heightMeasureSpec, paddingTop + paddingBottom, params.height,
                )
            }
            child.measure(width, height)
            natural += child.measuredHeight + params.topMargin + params.bottomMargin
            if (params.weight > 0f) weights += params.weight
        }

        var total = natural
        if (bounded && weights > 0f && available > natural) {
            val spare = available - natural
            for (index in 0 until childCount) {
                val child = getChildAt(index)
                if (child.visibility == View.GONE) continue
                val params = child.layoutParams as LayoutParams
                if (params.weight <= 0f) continue
                val share = (spare * params.weight / weights).toInt()
                val width = getChildMeasureSpec(
                    widthMeasureSpec, paddingLeft + paddingRight, params.width,
                )
                child.measure(
                    width,
                    MeasureSpec.makeMeasureSpec(
                        child.measuredHeight + share, MeasureSpec.EXACTLY,
                    ),
                )
            }
            total = available
        }

        setMeasuredDimension(
            resolveSize(suggestedMinimumWidth + paddingLeft + paddingRight, widthMeasureSpec),
            resolveSize(total, heightMeasureSpec),
        )
    }
}
