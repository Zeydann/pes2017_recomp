package com.pes2017.recomp

import android.content.pm.ActivityInfo
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat

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

    // The menu's own palette, so the guide reads as a page of the title and not
    // as a separate help app: cyan accent, cool ink, navy behind.
    private val accent = 0xFF35D3FF.toInt()
    private val ink = 0xFFE2ECFA.toInt()
    private val dim = 0xFF9FB8D6.toInt()
    private val heading = android.graphics.Typeface.create("sans-serif-condensed", android.graphics.Typeface.BOLD_ITALIC)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
        // Edge to edge, with the content padded inside the cutout and the
        // gesture bar. Top-anchored content otherwise sits under the status
        // bar or a punch-hole, where a thumb cannot reliably reach it.
        goEdgeToEdge()
        val density = resources.displayMetrics.density
        fun dp(value: Int) = (value * density).toInt()

        val layout = ControlLayout.load(this)

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = GradientDrawable(
                GradientDrawable.Orientation.TOP_BOTTOM,
                intArrayOf(0xFF04122E.toInt(), 0xFF0A2A5E.toInt()),
            )
        }
        ViewCompat.setOnApplyWindowInsetsListener(root) { view, insets ->
            val safe = insets.getInsets(
                WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout(),
            )
            view.setPadding(safe.left + dp(18), safe.top + dp(12), safe.right + dp(18), safe.bottom + dp(12))
            insets
        }

        val header = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        header.addView(TextView(this).apply {
            setText(R.string.controls_guide)
            setTextColor(Color.WHITE)
            textSize = 20f
            typeface = heading
            isAllCaps = true
            letterSpacing = 0.05f
        }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        header.addView(Button(this).apply {
            setText(R.string.guide_close)
            textSize = 14f
            isAllCaps = false
            // A real target rather than a few letters at the screen's edge: the
            // guide is opened with a thumb, in landscape, and Close is the only
            // way back.
            minWidth = dp(88)
            minimumWidth = dp(88)
            minHeight = dp(44)
            minimumHeight = dp(44)
            setPadding(dp(20), dp(8), dp(20), dp(8))
            setOnClickListener { finish() }
        }, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT,
        ))
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
        addCard(column, R.string.guide_custom_buttons,
            getString(R.string.guide_custom_buttons_text) + "\n\n" + spareSummary(layout))
        addCard(column, R.string.guide_customization, getString(R.string.guide_customization_text))
        addCard(column, R.string.guide_menus, getString(R.string.guide_menus_text))
        addCard(column, R.string.guide_security, getString(R.string.guide_security_text))

        setContentView(root)
        ViewCompat.requestApplyInsets(root)
    }

    /** Full screen into the cutout, as the match is, then padded by the insets. */
    private fun goEdgeToEdge() {
        WindowCompat.setDecorFitsSystemWindows(window, false)
        WindowInsetsControllerCompat(window, window.decorView).apply {
            hide(WindowInsetsCompat.Type.systemBars())
            systemBarsBehavior =
                WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            val attributes = window.attributes
            attributes.layoutInDisplayCutoutMode =
                WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
            window.attributes = attributes
        }
    }

    private fun addCard(column: LinearLayout, title: Int, body: String) {
        val density = resources.displayMetrics.density
        fun dp(value: Int) = (value * density).toInt()
        val card = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(14), dp(10), dp(14), dp(12))
            background = GradientDrawable().apply {
                cornerRadius = 2f * density
                setColor(0x99081830.toInt())
                setStroke(maxOf(1, dp(1)), 0x3335D3FF)
            }
        }
        // A cyan edge on the card, as the menu's selected band wears.
        card.addView(View(this).apply { setBackgroundColor(accent) }, LinearLayout.LayoutParams(
            dp(34), maxOf(1, dp(2)),
        ))
        card.addView(TextView(this).apply {
            setText(title)
            setTextColor(accent)
            textSize = 14f
            typeface = heading
            isAllCaps = true
            letterSpacing = 0.06f
            setPadding(0, dp(6), 0, 0)
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

    /**
     * How many of the ten spares are in use. The card above explains what they
     * are; this line says whether the player has already given one a job, which
     * is the question a guide is asked after the pad has been edited.
     */
    private fun spareSummary(layout: ControlLayout): String {
        val spares = layout.elements.filter { it.kind == ControlKind.CUSTOM }
        fun bound(element: ControlElement) = element.swipeTap != 0 || element.swipeUp != 0 ||
            element.swipeDown != 0 || element.swipeLeft != 0 || element.swipeRight != 0
        val used = spares.count { bound(it) }
        return getString(R.string.guide_custom_buttons_summary, used, spares.size)
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
        action.flick != SwipeFlick.NONE && action.bit == 0 ->
            ControlVocabulary.of(this, SwipeLabels.flickName(action.flick)).let { flick ->
                when (action.modifier) {
                    SwipeModifier.NONE -> flick
                    else -> SwipeLabels.modifierName(action.modifier) + " + " + flick
                }
            }
        action.doubleTap -> bitName(action.bit) + " ×2"
        action.modifier != SwipeModifier.NONE -> SwipeLabels.modifierName(action.modifier) + " + " + bitName(action.bit)
        else -> bitName(action.bit)
    }

    private fun bitName(bit: Int): String = ControlVocabulary.of(this, rawBitName(bit))

    private fun rawBitName(bit: Int): String = when (bit) {
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
