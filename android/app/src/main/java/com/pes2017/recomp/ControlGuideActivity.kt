package com.pes2017.recomp

import android.content.pm.ActivityInfo
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity

/**
 * The control guide: the whole pad on one screen, read from the layout the
 * player is actually using rather than from a picture that goes stale.
 *
 * Reached from the launcher and from the pause menu, so it is available both
 * before a match and in the middle of one. Every card is a plain View built
 * here: the styles are the touch pad's own glass and accent colours, and there
 * are no images to keep in step with a binding.
 */
class ControlGuideActivity : AppCompatActivity() {

    private val accent = 0xFF38BDF8.toInt()
    private val ink = 0xFFE2E8F0.toInt()
    private val dim = 0xFF94A3B8.toInt()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
        val density = resources.displayMetrics.density
        fun dp(value: Int) = (value * density).toInt()

        val layout = ControlLayout.load(this)

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.rgb(8, 11, 16))
            setPadding(dp(18), dp(12), dp(18), dp(12))
        }

        val header = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        header.addView(TextView(this).apply {
            setText(R.string.controls_guide)
            setTextColor(Color.WHITE)
            textSize = 20f
            paint.isFakeBoldText = true
            letterSpacing = 0.06f
        }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        header.addView(Button(this).apply {
            setText(R.string.guide_close)
            textSize = 12f
            minWidth = 0
            minimumWidth = 0
            setOnClickListener { finish() }
        })
        root.addView(header)
        root.addView(TextView(this).apply {
            setText(R.string.guide_intro)
            setTextColor(dim)
            textSize = 12f
        })

        val column = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, dp(8), 0, 0)
        }
        val scroll = ScrollView(this).apply { isFillViewport = true }
        scroll.addView(column)
        root.addView(scroll, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f,
        ))

        addCard(column, R.string.guide_essentials, getString(R.string.guide_essentials_text))
        addCard(column, R.string.guide_dribbling, getString(R.string.guide_dribbling_text))
        addCard(column, R.string.guide_wheel,
            getString(R.string.guide_wheel_text) + "\n\n" + wheelSummary(layout))
        addCard(column, R.string.guide_defending,
            getString(R.string.guide_defending_text) + "\n\n" + stanceSummary(layout))
        addCard(column, R.string.guide_swipes,
            getString(R.string.guide_swipes_text) + "\n\n" + swipeSummary(layout))
        addCard(column, R.string.guide_customization, getString(R.string.guide_customization_text))
        addCard(column, R.string.guide_security, getString(R.string.guide_security_text))

        setContentView(root)
    }

    private fun addCard(column: LinearLayout, title: Int, body: String) {
        val density = resources.displayMetrics.density
        fun dp(value: Int) = (value * density).toInt()
        val card = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(14), dp(10), dp(14), dp(12))
            background = GradientDrawable().apply {
                cornerRadius = 14f * density
                setColor(0xCC0B0F17.toInt())
                setStroke(maxOf(1, dp(1)), 0x5538BDF8)
            }
        }
        card.addView(TextView(this).apply {
            setText(title)
            setTextColor(accent)
            textSize = 14f
            paint.isFakeBoldText = true
            letterSpacing = 0.08f
        })
        card.addView(TextView(this).apply {
            text = body
            setTextColor(ink)
            textSize = 12.5f
            setLineSpacing(3f * density, 1f)
        })
        column.addView(card, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT,
        ).apply { bottomMargin = dp(8) })
    }

    /** The wheel as the player has it now: each sector and the move in it. */
    private fun wheelSummary(layout: ControlLayout): String {
        val wheel = layout.find("trick") ?: return getString(R.string.skill_wheel)
        val sectors = arrayOf(
            R.string.swipe_right, R.string.skill_sector_up_right, R.string.swipe_up,
            R.string.skill_sector_up_left, R.string.swipe_left, R.string.skill_sector_down_left,
            R.string.swipe_down, R.string.skill_sector_down_right,
        )
        val lines = ArrayList<String>(sectors.size)
        for (sector in sectors.indices) {
            if (sector >= wheel.trickMoves.size) break
            val move = wheel.trickMove(sector)
            val name = if (move < 0) getString(R.string.button_none) else SkillMoves.name(move)
            lines.add("· " + getString(sectors[sector]) + " — " + name)
        }
        return lines.joinToString("\n")
    }

    /** The four action buttons and the second line each carries while defending. */
    private fun stanceSummary(layout: ControlLayout): String {
        val lines = ArrayList<String>(5)
        for (id in listOf("a", "b", "x", "y")) {
            val element = layout.find(id) ?: continue
            val label = ControlLayout.labelFor(element, Prefs(this).playStationButtons)
            val defending = element.sub2.ifEmpty { "—" }
            lines.add("· $label — $defending")
        }
        return lines.joinToString("\n")
    }

    /** Every bound swipe on the action buttons, from the current layout. */
    private fun swipeSummary(layout: ControlLayout): String {
        val names = intArrayOf(R.string.swipe_up, R.string.swipe_down, R.string.swipe_left, R.string.swipe_right)
        val lines = ArrayList<String>(8)
        for (id in listOf("a", "b", "x", "y", "swipe")) {
            val element = layout.find(id) ?: continue
            if (!element.visible) continue
            val parts = ArrayList<String>(4)
            for ((index, direction) in GestureCorridor.DIRECTIONS.withIndex()) {
                val action = element.swipeAction(direction)
                if (!action.bound) continue
                parts.add(getString(names[index]) + " " + actionName(action))
            }
            if (parts.isEmpty()) continue
            val label = ControlLayout.labelFor(element, Prefs(this).playStationButtons)
            lines.add("· $label: " + parts.joinToString(" · "))
        }
        return if (lines.isEmpty()) getString(R.string.button_none) else lines.joinToString("\n")
    }

    private fun actionName(action: SwipeAction): String = when {
        !action.bound -> getString(R.string.button_none)
        action.doubleTap -> bitName(action.bit) + " ×2"
        action.modifier != SwipeModifier.NONE -> action.modifier.name + " + " + bitName(action.bit)
        else -> bitName(action.bit)
    }

    private fun bitName(bit: Int): String = when (bit) {
        0 -> getString(R.string.button_none)
        NativeBridge.A -> "A"
        NativeBridge.B -> "B"
        NativeBridge.X -> "X"
        NativeBridge.Y -> "Y"
        NativeBridge.LB -> "LB"
        NativeBridge.RB -> "RB"
        NativeBridge.START -> "START"
        NativeBridge.BACK -> "BACK"
        NativeBridge.L3 -> "L3"
        NativeBridge.R3 -> "R3"
        NativeBridge.UP -> "D-PAD UP"
        NativeBridge.DOWN -> "D-PAD DOWN"
        NativeBridge.LEFT -> "D-PAD LEFT"
        else -> "D-PAD RIGHT"
    }
}
