package com.pes2017.recomp

import android.app.Activity
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.GradientDrawable
import android.os.SystemClock
import android.text.InputType
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.style.ForegroundColorSpan
import android.view.Gravity
import android.view.InputDevice
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.view.inputmethod.EditorInfo
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.appcompat.app.AppCompatDialog
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * The in-game menu, drawn the way PES 2017 draws one on the 360: a panel down
 * the left of the pitch, a dark blue gradient under condensed italic capitals,
 * and one bright band with a cyan edge on the row the player is on.
 *
 * One dialog holds a stack of [Screen]s, so a submenu is the same window with
 * new rows -- there is never a second dialog to flicker over the first, and the
 * back key walks the stack the way the original game does. Rows are built by a
 * lambda rather than kept: a toggle can be re-rendered with its new word without
 * rebuilding the screen by hand.
 *
 * Controller-first, touch-friendly: the D-pad and stick move the band, A
 * (Cross) chooses, B (Circle) goes back, Start closes; a tap chooses the row it
 * lands on, which is the same one-key grammar on glass.
 */
class PesMenu(
    private val activity: Activity,
    private val onClosed: () -> Unit = {},
) : AppCompatDialog(activity, R.style.Theme_PES17_Menu) {

    /** One line of a screen. [checked] turns the row into a choice with a dot. */
    class Row(
        val label: String,
        val detail: String = "",
        val enabled: Boolean = true,
        val checked: Boolean? = null,
        val onAdjust: ((PesMenu, Int) -> Unit)? = null,
        val activate: ((PesMenu) -> Unit)? = null,
    )

    /** One text box on a form screen (rename, a macro's name, the console). */
    class Field(
        val label: String,
        val initial: String = "",
        val hint: String = "",
        val multiline: Boolean = false,
    )

    /** A whole screen: a heading, a sentence, its rows, and its form boxes. */
    class Screen(
        val title: String,
        val message: String = "",
        val rows: List<Row> = emptyList(),
        val fields: List<Field> = emptyList(),
        val submit: String = "",
        val onSubmit: (List<String>) -> Unit = {},
        val hint: String = "",
        /**
         * Whether the page wants the menu's own Back row. A page that already
         * prints a way back of its own -- a prompt's Cancel, an empty list's
         * Close -- sets this to false, so the way up is never offered twice.
         */
        val backRow: Boolean = true,
    )

    private val density = activity.resources.displayMetrics.density
    private fun dp(value: Int): Int = (value * density).roundToInt()

    private val headingType = Typeface.create("sans-serif-condensed", Typeface.BOLD_ITALIC)
    private val bodyType = Typeface.create("sans-serif-condensed", Typeface.BOLD)

    private val root = FrameLayout(activity)
    private val panel = LinearLayout(activity)
    private val brand = TextView(activity)
    private val heading = TextView(activity)
    private val message = TextView(activity)
    private val scroll = ScrollView(activity)
    private val body = LinearLayout(activity)
    private val promptStrip = TextView(activity)
    private val hintLine = TextView(activity)

    private val stack = ArrayList<(PesMenu) -> Screen>()

    /**
     * The heading of every page on [stack], kept as the pages are rendered so a
     * submenu's Back row can name the page it returns to without building that
     * page a second time.
     */
    private val titles = ArrayList<String>()
    private var screen: Screen? = null
    private var index = 0
    private val rows = ArrayList<Row>()
    private val rowViews = ArrayList<RowView>()
    private val fieldViews = ArrayList<EditText>()

    /** How many pages deep the menu is; a row that pushed one leaves it open. */
    val depth: Int get() = stack.size

    private var closing = false
    private var closedOnce = false
    private var lastStickStep = 0L

    init {
        setCanceledOnTouchOutside(false)
        setOnDismissListener {
            if (!closedOnce) {
                closedOnce = true
                onClosed()
            }
        }

        window?.apply {
            setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
            setDimAmount(DIM)
            setLayout(WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.MATCH_PARENT)
            addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND)
            setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)
        }

        val metrics = activity.resources.displayMetrics
        val widthDp = metrics.widthPixels / metrics.density
        val panelWidth = (min(widthDp * PANEL_FRACTION, PANEL_MAX_DP) * density).roundToInt()

        panel.orientation = LinearLayout.VERTICAL
        // The panel takes its own taps: only the pitch beside it goes back, so
        // tapping a heading or the space between rows cannot close the menu.
        panel.setOnClickListener { }
        panel.background = GradientDrawable(
            GradientDrawable.Orientation.TOP_BOTTOM,
            intArrayOf(PANEL_TOP, PANEL_BOTTOM),
        )
        panel.layoutParams = FrameLayout.LayoutParams(panelWidth, ViewGroup.LayoutParams.MATCH_PARENT)
        panel.setPadding(dp(16), dp(18), dp(14), dp(12))

        // "PES 2017 | Typhon Lhoucine": the title keeps the size, typeface,
        // letter spacing and colour it always had, the name sits beside it on
        // the same condensed line, and the bar between them is the dimmer cyan,
        // so the pair reads as one header rather than two labels. The line wraps
        // rather than ellipsizing: the whole name has to be readable even on the
        // narrowest panel.
        brand.text = SpannableStringBuilder("PES 2017").apply {
            val barAt = length
            append(" | ")
            setSpan(
                ForegroundColorSpan(COLOR_HEADER_BAR), barAt, length,
                Spanned.SPAN_EXCLUSIVE_EXCLUSIVE,
            )
            val nameAt = length
            append(OWNER)
            setSpan(
                ForegroundColorSpan(COLOR_HEADER_NAME), nameAt, length,
                Spanned.SPAN_EXCLUSIVE_EXCLUSIVE,
            )
        }
        brand.setTextColor(0xFF57C7FF.toInt())
        brand.textSize = 10f
        brand.letterSpacing = 0.32f
        brand.typeface = bodyType
        brand.setPadding(0, 0, 0, dp(2))

        heading.setTextColor(Color.WHITE)
        heading.textSize = 21f
        heading.typeface = headingType
        heading.letterSpacing = 0.05f
        heading.isAllCaps = true
        heading.maxLines = 2
        heading.ellipsize = android.text.TextUtils.TruncateAt.END

        message.setTextColor(0xFFA9C4E8.toInt())
        message.textSize = 12f
        message.typeface = bodyType
        message.setPadding(0, dp(6), 0, 0)
        message.visibility = View.GONE

        val rule = View(activity)
        rule.setBackgroundColor(0x6635D3FF)
        val header = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            addView(brand, LinearLayout.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
            addView(heading, LinearLayout.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
            addView(message, LinearLayout.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
            addView(rule, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(1)).apply {
                topMargin = dp(10)
            })
        }
        panel.addView(header, LinearLayout.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)

        body.orientation = LinearLayout.VERTICAL
        scroll.isFillViewport = true
        scroll.isVerticalScrollBarEnabled = false
        scroll.addView(body, FrameLayout.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        panel.addView(
            scroll,
            LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f),
        )

        promptStrip.setTextColor(0xFF7FD2F5.toInt())
        promptStrip.textSize = 10f
        promptStrip.typeface = bodyType
        promptStrip.letterSpacing = 0.08f
        promptStrip.setPadding(0, dp(10), 0, dp(2))

        hintLine.setTextColor(0xFF8FA9CC.toInt())
        hintLine.textSize = 10f
        hintLine.typeface = bodyType
        hintLine.setPadding(0, dp(2), 0, 0)
        hintLine.visibility = View.GONE
        panel.addView(promptStrip, LinearLayout.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        panel.addView(hintLine, LinearLayout.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)

        root.setBackgroundColor(Color.TRANSPARENT)
        root.addView(panel)
        // A tap on the pitch beside the panel is swallowed rather than closing
        // the menu: a thumb resting on the glass must not resume the match.
        // Carry on, or B, is the way out.
        root.setOnClickListener { }
        setContentView(root)
    }

    // ---- opening and closing -------------------------------------------------

    /** Opens the dialog on [build], replacing whatever stack was there. */
    fun open(build: (PesMenu) -> Screen) {
        stack.clear()
        titles.clear()
        stack.add(build)
        // A fresh visit: whatever ended the previous one (dismissNow, a close)
        // must not leave this page unable to push or to go back. An instance is
        // normally used once, but a menu that silently refused to navigate
        // because of a flag from an earlier life is exactly the stuck submenu
        // this class is supposed to make impossible.
        closing = false
        closedOnce = false
        if (!isShowing) {
            panel.alpha = 0f
            panel.translationX = -dp(28).toFloat()
            show()
            panel.animate().alpha(1f).translationX(0f).setDuration(OPEN_MS).start()
        }
        showScreen(build, keepSelection = false)
    }

    /** Opens [build] on top of the current screen, as a submenu. */
    fun push(build: (PesMenu) -> Screen) {
        if (closing) return
        // A push on a menu that is not on screen would be a page nobody can
        // see or leave: open it as the root instead.
        if (!isShowing) {
            open(build)
            return
        }
        stack.add(build)
        showScreen(build, keepSelection = false)
        rebrand()
    }

    /** Back one level, closing the dialog when this is the root. */
    fun back() {
        if (closing) return
        if (stack.size > 1) {
            stack.removeAt(stack.size - 1)
            showScreen(stack.last(), keepSelection = false)
            rebrand()
        } else {
            closeMenu()
        }
    }

    /** Re-runs the current screen's builder, keeping the lit row where it was. */
    fun refresh() {
        val builder = stack.lastOrNull() ?: return
        showScreen(builder, keepSelection = true)
    }

    /** Dismisses at once, for a caller that is opening another screen behind it. */
    fun dismissNow() {
        if (!isShowing) return
        closing = true
        dismiss()
    }

    /** Animates out and dismisses. Safe to call twice. */
    fun closeMenu() {
        if (closing || !isShowing) {
            if (isShowing) dismiss()
            return
        }
        closing = true
        panel.animate()
            .alpha(0f)
            .translationX(-dp(24).toFloat())
            .setDuration(CLOSE_MS)
            .withEndAction { dismiss() }
            .start()
    }

    override fun onBackPressed() {
        back()
    }

    // ---- rendering -----------------------------------------------------------

    private fun showScreen(build: (PesMenu) -> Screen, keepSelection: Boolean) {
        val next = build(this)
        val previous = index
        screen = next
        while (titles.size > stack.size) titles.removeAt(titles.size - 1)
        if (titles.size == stack.size) titles[stack.size - 1] = next.title
        else titles.add(next.title)
        rows.clear()
        rows.addAll(next.rows)
        val selection = MenuSelection(rows.map { it.enabled }, if (keepSelection) previous else 0)
        index = selection.index
        render(next)
        if (!keepSelection) {
            scroll.post { scroll.scrollTo(0, 0) }
        }
    }

    /** The row that leaves this page for the one under it, named for its parent. */
    private fun backRow(): Row = Row(
        activity.getString(R.string.back),
        detail = titles.getOrNull(stack.size - 2).orEmpty(),
    ) { it.back() }

    private fun rebrand() {
        // A submenu is a new page: the panel eases in from the left, the same
        // way the root did, so the eye reads it as a page turn rather than a
        // list that changed under the thumb.
        panel.translationX = -dp(14).toFloat()
        panel.alpha = 0.35f
        panel.animate().translationX(0f).alpha(1f).setDuration(PUSH_MS).start()
    }

    private fun render(current: Screen) {
        heading.text = current.title
        message.text = current.message
        message.visibility = if (current.message.isEmpty()) View.GONE else View.VISIBLE

        body.removeAllViews()
        rowViews.clear()
        fieldViews.clear()

        // Every page above the root prints its way back to the page under it,
        // first in the panel, where a long list cannot push it out of sight.
        // B and the system's back gesture do the same thing, so a player on a
        // pad loses nothing and a player on glass never has to know a key to
        // leave a submenu. It names the parent, so it can only lead one level
        // up, and it is not one of the page's rows: the band and the pickers'
        // indices are exactly what they were.
        if (stack.size > 1 && current.backRow) {
            body.addView(
                RowView(backRow(), BACK_ROW),
                LinearLayout.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT,
            )
        }

        for (field in current.fields) {
            body.addView(fieldLabel(field.label))
            val editor = EditText(activity).apply {
                setText(field.initial)
                if (field.hint.isNotEmpty()) hint = field.hint
                setSelection(text.length)
                setSingleLine(!field.multiline)
                minLines = if (field.multiline) 2 else 1
                setTextColor(0xFFEAF4FF.toInt())
                setHintTextColor(0xFF7F97B8.toInt())
                textSize = 14f
                typeface = bodyType
                background = GradientDrawable().apply {
                    setColor(0x66102038)
                    cornerRadius = dp(2).toFloat()
                    setStroke(maxOf(1, dp(1)), 0x6635D3FF)
                }
                setPadding(dp(8), dp(6), dp(8), dp(6))
                inputType = InputType.TYPE_CLASS_TEXT or
                    if (field.multiline) InputType.TYPE_TEXT_FLAG_MULTI_LINE else 0
                imeOptions = EditorInfo.IME_ACTION_DONE
            }
            fieldViews.add(editor)
            body.addView(editor, LinearLayout.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        }

        for ((i, row) in rows.withIndex()) {
            val view = RowView(row, i)
            rowViews.add(view)
            body.addView(view, LinearLayout.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        }
        paintSelection(scrollTo = false)

        val hasAdjust = rows.any { it.onAdjust != null }
        promptStrip.text = when {
            current.fields.isNotEmpty() -> "A  ${current.submit.ifEmpty { "CONFIRM" }}     B  CANCEL"
            hasAdjust -> "A  CHANGE     B  BACK     \u25C0 \u25B6  ADJUST"
            else -> "A  SELECT     B  BACK     START  CLOSE"
        }
        hintLine.text = current.hint
        hintLine.visibility = if (current.hint.isEmpty()) View.GONE else View.VISIBLE
    }

    private fun fieldLabel(text: String): TextView = TextView(activity).apply {
        this.text = text
        setTextColor(0xFF9FC4EA.toInt())
        textSize = 11f
        letterSpacing = 0.08f
        typeface = bodyType
        isAllCaps = true
        setPadding(0, dp(8), 0, dp(3))
    }

    private fun paintSelection(scrollTo: Boolean) {
        for (view in rowViews) view.chosen = view.rowIndex == index
        if (scrollTo && index in rowViews.indices) {
            rowViews[index].requestRectangleOnScreen(
                android.graphics.Rect(0, 0, rowViews[index].width, rowViews[index].height), false,
            )
        }
    }

    // ---- moving and choosing -------------------------------------------------

    private fun move(delta: Int) {
        val selection = MenuSelection(rows.map { it.enabled }, index)
        if (selection.move(delta)) {
            index = selection.index
            paintSelection(scrollTo = true)
        }
    }

    private fun choose() {
        val row = rows.getOrNull(index) ?: return
        if (!row.enabled) return
        if (row.onAdjust != null) {
            row.onAdjust.invoke(this, 1)
        } else {
            row.activate?.invoke(this) ?: closeMenu()
        }
    }

    private fun adjust(row: Row, delta: Int) {
        val handler = row.onAdjust ?: return
        handler.invoke(this, delta)
    }

    private fun submit() {
        val current = screen ?: return
        if (current.fields.isEmpty()) return
        current.onSubmit.invoke(fieldViews.map { it.text.toString() })
    }

    // ---- controller ----------------------------------------------------------

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        val down = event.action == KeyEvent.ACTION_DOWN
        val repeat = event.repeatCount > 0
        // Typing in a form box keeps the arrow keys: they move the caret there,
        // not the band. A, B and Start still work, so the pad still leaves.
        val typing = currentFocus is EditText && screen?.fields?.isNotEmpty() == true
        if (typing && event.keyCode !in FRAME_KEYS) return super.dispatchKeyEvent(event)
        when (event.keyCode) {
            KeyEvent.KEYCODE_DPAD_UP -> if (down) move(-1)
            KeyEvent.KEYCODE_DPAD_DOWN -> if (down) move(1)
            KeyEvent.KEYCODE_DPAD_LEFT ->
                if (down) adjust(rows.getOrNull(index) ?: return true, -1)
            KeyEvent.KEYCODE_DPAD_RIGHT ->
                if (down) adjust(rows.getOrNull(index) ?: return true, 1)
            KeyEvent.KEYCODE_BUTTON_A, KeyEvent.KEYCODE_ENTER, KeyEvent.KEYCODE_DPAD_CENTER,
            KeyEvent.KEYCODE_NUMPAD_ENTER, KeyEvent.KEYCODE_SPACE ->
                if (down && !repeat) {
                    if (screen?.fields?.isNotEmpty() == true) submit() else choose()
                }
            KeyEvent.KEYCODE_BUTTON_B, KeyEvent.KEYCODE_BACK ->
                if (down) back()
            KeyEvent.KEYCODE_BUTTON_START, KeyEvent.KEYCODE_MENU ->
                if (down) closeMenu()
            else -> return super.dispatchKeyEvent(event)
        }
        return true
    }

    /**
     * The analogue stick, for a pad that only reports it as an axis. It moves
     * the band at a walking pace rather than once per frame, so holding the
     * stick over does not run to the end of the list.
     */
    override fun onGenericMotionEvent(event: MotionEvent): Boolean {
        if (event.source and InputDevice.SOURCE_JOYSTICK != InputDevice.SOURCE_JOYSTICK) {
            return super.onGenericMotionEvent(event)
        }
        val x = event.getAxisValue(MotionEvent.AXIS_X)
        val y = event.getAxisValue(MotionEvent.AXIS_Y)
        if (kotlin.math.abs(x) < STICK_DEADZONE && kotlin.math.abs(y) < STICK_DEADZONE) return false
        val now = SystemClock.uptimeMillis()
        if (now - lastStickStep < STICK_REPEAT_MS) return true
        lastStickStep = now
        when {
            y < -STICK_DEADZONE -> move(-1)
            y > STICK_DEADZONE -> move(1)
            x < -STICK_DEADZONE -> adjust(rows.getOrNull(index) ?: return true, -1)
            x > STICK_DEADZONE -> adjust(rows.getOrNull(index) ?: return true, 1)
        }
        return true
    }

    // ---- one row -------------------------------------------------------------

    /**
     * A row: the band and its cyan edge are drawn, not composed from images, so
     * the panel stays one cheap gradient per selection change over a running
     * match. The label and the value are ordinary text views on top.
     */
    private inner class RowView(val row: Row, val rowIndex: Int) : LinearLayout(activity) {

        private val bandPaint = Paint(Paint.ANTI_ALIAS_FLAG)
        private val edgePaint = Paint(Paint.ANTI_ALIAS_FLAG)
        private val linePaint = Paint(Paint.ANTI_ALIAS_FLAG)
        // Both scroll when they do not fit, and sit still when they do: a row
        // that cuts a saved pad's name or a bound command into "LB + LS U..."
        // is the menu failing to say what it means.
        private val label = MarqueeText(activity)
        private val detail = MarqueeText(activity)
        private val tick = TextView(activity)
        private val chevron = TextView(activity)
        private val chosenBackground = GradientDrawable(
            GradientDrawable.Orientation.LEFT_RIGHT,
            intArrayOf(0xF22E86D8.toInt(), 0x00204C86),
        )

        var chosen: Boolean
            get() = isSelected
            set(value) {
                if (isSelected == value) return
                isSelected = value
                restyle()
                invalidate()
            }

        init {
            orientation = HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            minimumHeight = dp(48)
            isClickable = true
            isFocusable = true
            setWillNotDraw(false)
            setPadding(dp(14), dp(8), dp(8), dp(9))
            background = chosenBackground.constantState?.newDrawable()?.mutate()
            background.alpha = 0

            label.text = row.label
            label.typeface = headingType
            label.textSize = 14.5f
            label.letterSpacing = 0.05f
            label.isAllCaps = true
            label.layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)

            detail.typeface = bodyType
            detail.textSize = 11.5f
            detail.gravity = Gravity.END
            detail.setPadding(dp(8), 0, dp(4), 0)

            tick.typeface = bodyType
            tick.textSize = 12f
            tick.setPadding(0, 0, dp(8), 0)
            tick.text = "\u25CF"

            chevron.typeface = headingType
            chevron.textSize = 17f
            chevron.text = "\u203A"

            addView(tick)
            addView(label)
            addView(detail)
            addView(chevron)

            edgePaint.color = EDGE
            linePaint.color = LINE

            tick.visibility = if (row.checked == null) View.GONE else View.VISIBLE
            detail.visibility = if (row.detail.isEmpty()) View.GONE else View.VISIBLE
            chevron.visibility = if (row.onAdjust == null && row.enabled) View.VISIBLE else View.GONE

            restyle()

            setOnClickListener {
                if (!row.enabled) return@setOnClickListener
                // The Back row is not a page row: a tap leaves the page rather
                // than moving the band onto something that is not a choice.
                if (rowIndex == BACK_ROW) {
                    row.activate?.invoke(this@PesMenu)
                    return@setOnClickListener
                }
                if (index != rowIndex) {
                    index = rowIndex
                    paintSelection(scrollTo = true)
                }
                if (row.onAdjust != null) adjust(row, 1) else row.activate?.invoke(this@PesMenu) ?: closeMenu()
            }
            setOnLongClickListener {
                if (!row.enabled || row.onAdjust == null) return@setOnLongClickListener false
                adjust(row, -1)
                true
            }
        }

        override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
            super.onSizeChanged(w, h, oldw, oldh)
            // The value column is capped, never the name: a long value would
            // otherwise claim the row's whole width and squeeze the name out of
            // sight, which is the same cut-off in another form. It is set from
            // the measured row, so it follows a rotation or a different screen.
            detail.maxWidth = (w * DETAIL_SHARE).toInt()
        }

        private fun restyle() {
            label.setTextColor(
                when {
                    !row.enabled -> 0xFF6C7E9C.toInt()
                    isSelected -> Color.WHITE
                    else -> 0xFFD6E6FA.toInt()
                },
            )
            detail.setTextColor(if (isSelected) 0xFFB9F1FF.toInt() else 0xFF8FB3D9.toInt())
            detail.text = row.detail
            tick.setTextColor(if (row.checked == true) 0xFF57D8FF.toInt() else 0x557F97B8)
            chevron.setTextColor(if (isSelected) 0xFFB9F1FF.toInt() else 0x66D6E6FA)
            alpha = if (row.enabled) 1f else 0.55f
            background?.alpha = if (isSelected) 255 else 0
        }

        override fun onDraw(canvas: Canvas) {
            super.onDraw(canvas)
            if (isSelected) {
                canvas.drawRect(0f, 0f, dp(3).toFloat(), height.toFloat(), edgePaint)
                bandPaint.color = 0x33B9F1FF
                canvas.drawRect(0f, 0f, width.toFloat(), dp(1).toFloat(), bandPaint)
            }
            canvas.drawRect(dp(14).toFloat(), height - 1f, width.toFloat(), height.toFloat(), linePaint)
        }
    }

    companion object {
        private val FRAME_KEYS = setOf(
            KeyEvent.KEYCODE_BUTTON_A, KeyEvent.KEYCODE_BUTTON_B, KeyEvent.KEYCODE_BUTTON_START,
            KeyEvent.KEYCODE_ENTER, KeyEvent.KEYCODE_NUMPAD_ENTER, KeyEvent.KEYCODE_BACK,
            KeyEvent.KEYCODE_MENU,
        )

        /** How much of the window the panel takes, and its ceiling on a tablet. */
        private const val PANEL_FRACTION = 0.46f
        private const val PANEL_MAX_DP = 380f

        /** The Back row's place in a page: not one of its own rows, so never lit. */
        private const val BACK_ROW = -1

        /** The most of a row's width its value may take; the name keeps the rest. */
        private const val DETAIL_SHARE = 0.45f

        private const val STICK_DEADZONE = 0.6f
        private const val STICK_REPEAT_MS = 170L

        private const val OPEN_MS = 200L
        private const val PUSH_MS = 150L
        private const val CLOSE_MS = 160L

        private const val DIM = 0.45f

        /** Whose build this is, printed beside the title in the panel's header. */
        private const val OWNER = "Typhon Lhoucine"
        private const val COLOR_HEADER_NAME = 0xFFEAF4FF.toInt()
        private const val COLOR_HEADER_BAR = 0x9957C7FF.toInt()

        private const val PANEL_TOP = 0xF2071B3D.toInt()
        private const val PANEL_BOTTOM = 0xF20A2F66.toInt()
        private const val EDGE = 0xFF35D3FF.toInt()
        private const val LINE = 0x33B9D9FF
    }
}
