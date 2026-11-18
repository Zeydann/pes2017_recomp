package com.pes2017.recomp

import android.app.Activity
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.SeekBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.widget.SwitchCompat
import kotlin.math.roundToInt

/**
 * The pad editor's own strip. Everything on it acts on whichever control is
 * selected; dragging and pinching happen on the control itself. Shared by the
 * match (pause > Edit controls) and Settings > Controls > Arrange, so both edit
 * the same controls.json the same way.
 *
 * The strip sits at the bottom, and moves to the top while a control in the
 * lower half is selected, so it never covers the control being edited.
 */
class ControlEditPanel(
    private val activity: Activity,
    private val root: FrameLayout,
    private val overlay: TouchOverlayView,
    private val onDone: () -> Unit,
) {
    private val view: View = activity.layoutInflater.inflate(R.layout.edit_panel, root, false)
    private val title: TextView = view.findViewById(R.id.edit_title)
    private val clearance: TextView = view.findViewById(R.id.edit_clearance)
    private val size: SeekBar = view.findViewById(R.id.edit_size)
    private val opacity: SeekBar = view.findViewById(R.id.edit_opacity)
    private val visible: SwitchCompat = view.findViewById(R.id.edit_visible)
    private val toggle: SwitchCompat = view.findViewById(R.id.edit_toggle)
    private val snap: SwitchCompat = view.findViewById(R.id.edit_snap)
    /** What is left of the editor while nothing is selected: a hint and the way out. */
    private val idle: View = LinearLayout(activity).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        setBackgroundColor(0xB3101418.toInt())
        val dp = activity.resources.displayMetrics.density
        setPadding((12 * dp).roundToInt(), (4 * dp).roundToInt(), (4 * dp).roundToInt(), (4 * dp).roundToInt())
        addView(TextView(activity).apply {
            setText(R.string.edit_pick_one)
            setTextColor(0xFFFFFFFF.toInt())
            textSize = 12f
        })
    }
    private val resetOne: Button = view.findViewById(R.id.edit_reset_one)

    /**
     * The editor's own strip: two rows, not one.
     *
     * A single row grew wider than the screen as the editor gained buttons, and
     * the first thing pushed off the end was "Arrangement" -- the one control
     * whose absence cannot be explained to a player. The upper row holds what
     * acts on the selected control; the lower row holds reset and done, which
     * never change width.
     */
    private val actions: LinearLayout = LinearLayout(activity).apply {
        orientation = LinearLayout.VERTICAL
        val dp = activity.resources.displayMetrics.density
        setPadding((4 * dp).roundToInt(), (4 * dp).roundToInt(), (4 * dp).roundToInt(), (4 * dp).roundToInt())
        // A dark backing: the row floats over a bright pitch, and a button whose
        // edges disappear is a button a player aims at and misses.
        setBackgroundColor(0x99000000.toInt())
        val tools = LinearLayout(activity).apply { orientation = LinearLayout.HORIZONTAL }
        val finishes = LinearLayout(activity).apply { orientation = LinearLayout.HORIZONTAL }
        // The tools are wider than a phone held sideways once Swipe directions
        // and the wheel join Arrangement and More. A scroller keeps every one of
        // them reachable: with a plain row and END gravity the leftmost button
        // -- Arrangement -- was measured off screen and could not be tapped.
        val scroller = HorizontalScrollView(activity).apply {
            isHorizontalScrollBarEnabled = false
            isFillViewport = false
            addView(tools, ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT,
            ))
        }
        addView(scroller, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT,
        ))
        addView(finishes, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT,
        ).apply { topMargin = (4 * dp).roundToInt() })
        for (id in intArrayOf(R.id.edit_reset_one, R.id.edit_reset_all, R.id.edit_done)) {
            val button = view.findViewById<Button>(id)
            (button.parent as ViewGroup).removeView(button)
            button.textSize = 12f
            button.minWidth = 0
            button.minimumWidth = 0
            finishes.addView(button, LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT,
            ).apply { marginStart = (6 * dp).roundToInt() })
        }
    }

    /** The upper row of [actions]: the buttons that act on the selection. */
    private fun toolRow(): LinearLayout =
        ((actions.getChildAt(0) as HorizontalScrollView).getChildAt(0) as LinearLayout)
    /** Shown while a swipe control or an action button is selected: what each direction sends. */
    private val swipes: Button = Button(activity).apply {
        setText(R.string.swipe_bindings)
        textSize = 12f
        minWidth = 0
        minimumWidth = 0
        visibility = View.GONE
        setOnClickListener { showSwipeBindings() }
    }

    /** Shown while the skill wheel is selected: what each of its eight sectors plays. */
    private val wheel: Button = Button(activity).apply {
        setText(R.string.skill_wheel)
        textSize = 12f
        minWidth = 0
        minimumWidth = 0
        visibility = View.GONE
        setOnClickListener { showSkillWheel() }
    }

    /**
     * "Save Layout": the five numbered places, one tap from the editor. Choosing
     * a slot saves into it, and a slot that is already taken asks before it is
     * replaced, so a tap can never quietly lose a pad.
     */
    private val saveLayout: Button = Button(activity).apply {
        setText(R.string.layout_save)
        textSize = 12f
        minWidth = 0
        minimumWidth = 0
        setOnClickListener { pickSaveSlot() }
    }

    /**
     * Saving, loading and exporting layouts, one tap from the editor. The same
     * dialog is under More, but a player who has just arranged a pad looks for
     * Save first; burying it two menus deep is the reason it read as missing.
     */
    private val layouts: Button = Button(activity).apply {
        setText(R.string.layout_menu)
        textSize = 12f
        minWidth = 0
        minimumWidth = 0
        setOnClickListener { showProfiles() }
    }

    /** TG1/TG2: which saved layout the toggle puts on screen. */
    private val mapping: Button = Button(activity).apply {
        setText(R.string.toggle_mapping)
        textSize = 12f
        minWidth = 0
        minimumWidth = 0
        visibility = View.GONE
        setOnClickListener { pickToggleMapping() }
    }

    private var current: ControlElement? = null
    private var attached = false
    private var atTop = false

    /**
     * Set while [show] writes the switches from the selected control. Without
     * it, setting `isChecked` fires the change listener, which reads a
     * half-updated control back into the layout -- so selecting a hidden
     * control could silently re-show (or re-hide) its neighbour.
     */
    private var updating = false

    init {
        val dp = activity.resources.displayMetrics.density
        toolRow().addView(saveLayout, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT,
        ).apply { marginStart = (6 * dp).roundToInt() })
        toolRow().addView(layouts, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT,
        ).apply { marginStart = (6 * dp).roundToInt() })
        toolRow().addView(swipes, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT,
        ).apply { marginStart = (6 * dp).roundToInt() })
        toolRow().addView(wheel, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT,
        ).apply { marginStart = (6 * dp).roundToInt() })
        toolRow().addView(mapping, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT,
        ).apply { marginStart = (6 * dp).roundToInt() })
        val more = Button(activity).apply {
            setText(R.string.edit_more)
            textSize = 12f
            minWidth = 0
            minimumWidth = 0
            setOnClickListener { showMore() }
        }
        toolRow().addView(more, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT,
        ).apply { marginStart = (6 * dp).roundToInt() })
        size.max = 100
        opacity.max = 100
        size.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(bar: SeekBar, value: Int, fromUser: Boolean) {
                if (!fromUser) return
                val element = current ?: return
                val smallest = if (element.kind == ControlKind.LOOK) 120f else 36f
                val largest = if (element.kind == ControlKind.LOOK) 900f else 200f
                element.setSize(
                    smallest + (largest - smallest) * (value / 100f), overlay.stance,
                )
                overlay.refresh()
            }

            override fun onStartTrackingTouch(bar: SeekBar) {
                remember()
            }

            override fun onStopTrackingTouch(bar: SeekBar) {
                // Grown into a neighbour: step it aside.
                current?.let { overlay.keepApart(it) }
                save()
                current?.let { show(it) }
            }
        })
        opacity.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(bar: SeekBar, value: Int, fromUser: Boolean) {
                if (!fromUser) return
                val element = current ?: return
                element.setAlpha((value / 100f).coerceIn(0.05f, 1f), overlay.stance)
                overlay.refresh()
            }

            override fun onStartTrackingTouch(bar: SeekBar) {
                remember()
            }

            override fun onStopTrackingTouch(bar: SeekBar) = save()
        })
        visible.setOnCheckedChangeListener { _, checked ->
            if (updating) return@setOnCheckedChangeListener
            val element = current ?: return@setOnCheckedChangeListener
            if (element.visibleIn(overlay.stance) == checked) return@setOnCheckedChangeListener
            remember()
            element.setVisible(checked, overlay.stance)
            if (checked) overlay.keepApart(element)
            overlay.refresh()
            save()
        }
        overlay.onEditStarted = { remember() }
        toggle.setOnCheckedChangeListener { _, checked ->
            if (updating) return@setOnCheckedChangeListener
            val element = current ?: return@setOnCheckedChangeListener
            element.toggle = checked
            save()
        }
        snap.setOnCheckedChangeListener { _, checked -> overlay.snapToGrid = checked }
        actions.findViewById<Button>(R.id.edit_reset_one).setOnClickListener {
            val element = current ?: return@setOnClickListener
            remember()
            // A reset is a new control: a latch left on the old one must not
            // keep holding its bit over the top of the fresh one.
            overlay.releaseEverything()
            overlay.layout.reset(element.id)
            val fresh = overlay.layout.find(element.id)
            if (fresh != null) overlay.keepApart(fresh)
            overlay.select(fresh)
            show(fresh)
            overlay.refresh()
            save()
        }
        actions.findViewById<Button>(R.id.edit_reset_all).setOnClickListener {
            AlertDialog.Builder(activity)
                .setTitle(R.string.reset_all)
                .setMessage(R.string.reset_all_explained)
                .setNegativeButton(R.string.cancel, null)
                .setPositiveButton(R.string.reset) { _, _ ->
                    remember()
                    overlay.releaseEverything()
                    overlay.layout.resetAll()
                    overlay.select(null)
                    overlay.refresh()
                    save()
                }
                .show()
        }
        actions.findViewById<Button>(R.id.edit_done).setOnClickListener { onDone() }
    }

    private fun save() = overlay.layout.save(activity)

    /**
     * Whether this control has per-direction swipes worth binding. The two
     * shoulders and the two menu buttons do not: LB and RB are held as the
     * modifiers of other actions, and Start/Select belong to the title's menus,
     * so a direction on them was a setting that did nothing useful.
     */
    private fun offersSwipes(element: ControlElement): Boolean = when (element.kind) {
        ControlKind.SWIPE, ControlKind.CUSTOM, ControlKind.TACTICAL -> true
        ControlKind.BUTTON -> element.value != NativeBridge.LB &&
            element.value != NativeBridge.RB &&
            element.value != NativeBridge.START &&
            element.value != NativeBridge.BACK
        else -> false
    }

    // ---- what a swipe sends -------------------------------------------------

    private fun showSwipeBindings() {
        val element = current ?: return
        val directions = intArrayOf(R.string.swipe_up, R.string.swipe_down,
            R.string.swipe_left, R.string.swipe_right, R.string.swipe_center)
        val labels = Array(directions.size) { index ->
            activity.getString(directions[index]) + ": " + actionName(swipeBinding(element, index))
        }
        AlertDialog.Builder(activity)
            .setTitle(R.string.swipe_bindings)
            .setItems(labels) { _, which -> pickSwipeAction(element, which) }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    /**
     * The whole vocabulary a direction can send, in two levels.
     *
     * One flat list of every combination was longer than a phone's screen and
     * made the face buttons hard to find among the stick throws. The first
     * dialog picks a group -- nothing, the pad's buttons, double taps, a
     * modifier with a button, a stick throw, or a modifier with a stick throw --
     * and the second lists that group's entries. The catalogue itself lives in
     * [SwipeCatalogue], so the editor, the labels and the guide all name the
     * same set.
     */
    private fun pickSwipeAction(element: ControlElement, direction: Int) {
        val groups = SwipeCatalogue.groups
        val titles = Array(groups.size) { activity.getString(groups[it].titleRes) }
        AlertDialog.Builder(activity)
            .setTitle(R.string.swipe_direction)
            .setItems(titles) { _, which -> pickSwipeEntry(element, direction, groups[which]) }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    /** One group of [SwipeCatalogue], as the second half of the picker. */
    private fun pickSwipeEntry(
        element: ControlElement,
        direction: Int,
        group: SwipeCatalogue.Group,
    ) {
        val labels = Array(group.entries.size) { actionName(group.entries[it]) }
        AlertDialog.Builder(activity)
            .setTitle(group.titleRes)
            .setItems(labels) { _, which ->
                val action = group.entries[which]
                remember()
                setSwipeBinding(element, direction, action)
                // Shown straight away: an unbound swipe stays hidden, and a
                // player who has just bound one expects to see it.
                if (action.bound && !element.visibleIn(overlay.stance)) {
                    element.setVisible(true, overlay.stance)
                }
                overlay.refresh()
                show(element)
                save()
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    /**
     * 0 up, 1 down, 2 left, 3 right, 4 the centre tap: the order the dialog
     * lists them in. The centre is the fifth action -- a spare's or a tactical
     * button's custom slot -- and is read on the lift when the thumb did not
     * travel far enough to be a direction.
     */
    private fun swipeBinding(element: ControlElement, direction: Int): SwipeAction = when (direction) {
        0 -> element.swipeAction(Swipe.UP)
        1 -> element.swipeAction(Swipe.DOWN)
        2 -> element.swipeAction(Swipe.LEFT)
        3 -> element.swipeAction(Swipe.RIGHT)
        else -> element.swipeAction(Swipe.NONE)
    }

    private fun setSwipeBinding(element: ControlElement, direction: Int, action: SwipeAction) {
        val swipe = when (direction) {
            0 -> Swipe.UP
            1 -> Swipe.DOWN
            2 -> Swipe.LEFT
            3 -> Swipe.RIGHT
            else -> Swipe.NONE
        }
        element.setSwipeAction(swipe, action)
    }

    // ---- what the skill wheel plays ------------------------------------------

    /** The eight sectors, in the wheel's own order, so the list reads like the ring. */
    private fun showSkillWheel() {
        val element = current ?: return
        val labels = Array(TrickWheel.SECTORS) { sector ->
            val move = element.trickMove(sector)
            activity.getString(
                R.string.skill_wheel_sector,
                sectorName(sector),
                if (move < 0) activity.getString(R.string.button_none) else SkillMoves.name(move),
            )
        }
        AlertDialog.Builder(activity)
            .setTitle(R.string.skill_wheel)
            .setItems(labels) { _, which -> pickSkillMove(element, which) }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    /**
     * One sector's move: none, or any move the runtime's table carries. The
     * hint is part of the name because the wheel deliberately stops where the
     * player's left stick takes over.
     */
    private fun pickSkillMove(element: ControlElement, sector: Int) {
        val labels = Array(SkillMoves.COUNT + 1) { index ->
            if (index == 0) activity.getString(R.string.button_none)
            else {
                val name = SkillMoves.name(index - 1)
                if (SkillMoves.leavesExit(index - 1)) {
                    name + " (" + activity.getString(R.string.skill_wheel_exit) + ")"
                } else {
                    name
                }
            }
        }
        AlertDialog.Builder(activity)
            .setTitle(R.string.skill_wheel_sector_title)
            .setItems(labels) { _, which ->
                remember()
                element.setTrickMove(sector, if (which == 0) -1 else which - 1)
                overlay.refresh()
                show(element)
                save()
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    /** 0 right, then anticlockwise, the way [TrickWheel] numbers them. */
    private fun sectorName(sector: Int): String = activity.getString(
        when (sector) {
            0 -> R.string.swipe_right
            1 -> R.string.skill_sector_up_right
            2 -> R.string.swipe_up
            3 -> R.string.skill_sector_up_left
            4 -> R.string.swipe_left
            5 -> R.string.skill_sector_down_left
            6 -> R.string.swipe_down
            else -> R.string.skill_sector_down_right
        },
    )

    /** How one swipe binding reads in the editor: "LB + A", "B ×2", "A", "LB + LS Up". */
    private fun actionName(action: SwipeAction): String = when {
        !action.bound -> activity.getString(R.string.button_none)
        action.flick != SwipeFlick.NONE && action.bit == 0 -> when (action.modifier) {
            SwipeModifier.NONE -> SwipeLabels.flickName(action.flick)
            else -> SwipeLabels.modifierName(action.modifier) + " + " +
                SwipeLabels.flickName(action.flick)
        }
        action.doubleTap -> bitName(action.bit) + " ×2"
        action.modifier != SwipeModifier.NONE ->
            SwipeLabels.modifierName(action.modifier) + " + " + bitName(action.bit)
        else -> bitName(action.bit)
    }

    /** The name printed for a pad bit, as the console's own face names mark them. */
    private fun bitName(bit: Int): String = when (bit) {
        0 -> activity.getString(R.string.button_none)
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

    // ---- the deeper editor --------------------------------------------------

    /** Twenty steps of undo: a pad is small, and a snapshot is its json. */
    private val history = ArrayDeque<String>()
    private val future = ArrayDeque<String>()

    private fun remember() {
        history.addLast(overlay.layout.toJson())
        while (history.size > 20) history.removeFirst()
        future.clear()
    }

    private fun undo() {
        if (history.isEmpty()) {
            Toast.makeText(activity, R.string.nothing_to_undo, Toast.LENGTH_SHORT).show()
            return
        }
        val snapshot = history.removeLast()
        future.addLast(overlay.layout.toJson())
        restore(snapshot)
    }

    private fun redo() {
        if (future.isEmpty()) {
            Toast.makeText(activity, R.string.nothing_to_redo, Toast.LENGTH_SHORT).show()
            return
        }
        val snapshot = future.removeLast()
        history.addLast(overlay.layout.toJson())
        restore(snapshot)
    }

    private fun restore(snapshot: String) {
        if (!overlay.layout.restoreFrom(snapshot)) return
        overlay.select(null)
        show(null)
        overlay.refresh()
        save()
    }

    /**
     * The corridor each swipe has to travel, or what stands in it. This is the
     * warning the plan promised: a direction bound towards the edge or through
     * a neighbour is still allowed, but the player hears about it here rather
     * than from a flick that did nothing.
     */
    private fun showClearance(element: ControlElement) {
        if (element.kind != ControlKind.BUTTON && element.kind != ControlKind.SWIPE &&
            element.kind != ControlKind.TACTICAL
        ) {
            clearance.visibility = View.GONE
            return
        }
        val problems = GestureCorridor.problems(element, overlay.layout)
        clearance.visibility = View.VISIBLE
        clearance.text = if (problems.isEmpty()) {
            activity.getString(R.string.clearance_clear)
        } else {
            problems.entries.joinToString("   ") { (direction, ids) ->
                activity.getString(R.string.clearance_blocked, direction.name, ids.joinToString(", "))
            }
        }
    }

    /**
     * More has two lists. With nothing selected it is the pad's own menu -- the
     * one place Add a Control can be found without first picking a control, as
     * a player who has removed something looks for it before choosing anything
     * -- and with a control selected it offers only what acts on that control.
     * Saving, loading, importing and exporting the whole pad live behind the
     * strip's own Layouts button, never on one control or in this list.
     */
    private fun showMore() {
        val element = current ?: overlay.selected
        val labels = ArrayList<String>(8)
        val actions = ArrayList<() -> Unit>(8)
        if (element == null) {
            labels.add(activity.getString(R.string.edit_add))
            actions.add { addControl() }
            labels.add(activity.getString(R.string.edit_undo))
            actions.add { undo() }
            labels.add(activity.getString(R.string.edit_redo))
            actions.add { redo() }
        } else {
            // A custom toggle keeps the action it was assigned: its binding is
            // that action, not a pad bit, and the strip's own TG mapping button
            // is still where the mapping is changed.
            if (element.kind == ControlKind.BUTTON) {
                labels.add(activity.getString(R.string.edit_bind))
                actions.add { pickBinding(element) }
            }
            labels.add(activity.getString(R.string.edit_rename))
            actions.add { rename(element) }
            labels.add(activity.getString(R.string.edit_remove))
            actions.add { removeControl(element) }
            labels.add(activity.getString(R.string.edit_undo))
            actions.add { undo() }
            labels.add(activity.getString(R.string.edit_redo))
            actions.add { redo() }
        }
        AlertDialog.Builder(activity)
            .setTitle(R.string.edit_more)
            .setItems(labels.toTypedArray()) { _, which -> actions[which].invoke() }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun pickBinding(element: ControlElement) {
        val bits = intArrayOf(
            0, NativeBridge.A, NativeBridge.B, NativeBridge.X, NativeBridge.Y,
            NativeBridge.LB, NativeBridge.RB, NativeBridge.START, NativeBridge.BACK,
            NativeBridge.L3, NativeBridge.R3, NativeBridge.UP, NativeBridge.DOWN,
            NativeBridge.LEFT, NativeBridge.RIGHT,
        )
        val labels = Array(bits.size) { bitName(bits[it]) }
        AlertDialog.Builder(activity)
            .setTitle(R.string.edit_bind)
            .setItems(labels) { _, which ->
                remember()
                element.value = bits[which]
                element.role = ControlRole.of(element.value)
                overlay.refresh()
                show(element)
                save()
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun rename(element: ControlElement) {
        val dp = activity.resources.displayMetrics.density
        val box = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            setPadding((20 * dp).roundToInt(), (8 * dp).roundToInt(), (20 * dp).roundToInt(), 0)
        }
        val name = EditText(activity).apply {
            setText(element.label)
            hint = activity.getString(R.string.edit_label)
        }
        val caption = EditText(activity).apply {
            setText(element.sub)
            hint = activity.getString(R.string.edit_caption)
        }
        box.addView(name)
        box.addView(caption)
        AlertDialog.Builder(activity)
            .setTitle(R.string.edit_rename)
            .setView(box)
            .setNegativeButton(R.string.cancel, null)
            .setPositiveButton(android.R.string.ok) { _, _ ->
                remember()
                element.label = name.text.toString().trim().take(8).ifEmpty { element.label }
                element.sub = caption.text.toString().trim().take(8)
                overlay.refresh()
                show(element)
                save()
            }
            .show()
    }

    private fun toggleDefendCopy(element: ControlElement) {
        remember()
        if (element.hasDefendOverride()) element.mergeStance() else element.splitStance()
        overlay.refresh()
        show(element)
        save()
    }

    private fun addControl() {
        val missing = overlay.layout.missingControls()
        if (missing.isEmpty()) {
            Toast.makeText(activity, R.string.edit_add_none, Toast.LENGTH_SHORT).show()
            return
        }
        val labels = Array(missing.size) { index ->
            ControlLayout.labelFor(missing[index], overlay.playStation) + "  (" + missing[index].id + ")"
        }
        AlertDialog.Builder(activity)
            .setTitle(R.string.edit_add)
            .setItems(labels) { _, which ->
                remember()
                val added = missing[which]
                // A palette control that ships hidden (the d-pad, stick clicks)
                // is being added to be used, not to stay out of sight.
                added.visible = true
                if (overlay.layout.add(added)) {
                    overlay.keepApart(added)
                    overlay.select(added)
                    show(added)
                    overlay.refresh()
                    save()
                }
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun removeControl(element: ControlElement) {
        if (element.id == "stick") {
            Toast.makeText(activity, R.string.edit_remove_required, Toast.LENGTH_SHORT).show()
            return
        }
        AlertDialog.Builder(activity)
            .setTitle(R.string.edit_remove)
            .setMessage(activity.getString(R.string.edit_remove_confirm,
                ControlLayout.labelFor(element, overlay.playStation)))
            .setNegativeButton(R.string.cancel, null)
            .setPositiveButton(R.string.edit_remove) { _, _ ->
                remember()
                overlay.layout.remove(element.id)
                overlay.select(null)
                show(null)
                overlay.refresh()
                save()
            }
            .show()
    }

    // ---- saved layouts (slots) ----------------------------------------------

    /**
     * The saved pads: five numbered places and as many named ones as the player
     * makes. A slot's name is its own -- renaming never renames a file -- so the
     * list reads back what was typed rather than a sanitised file name.
     *
     * Save, load and export are the first things in the list, and all five
     * numbered places appear even before they hold a pad. They were once only
     * reachable through a named "Save as" and an existing file, which is why a
     * player could not find them at all.
     */
    private fun showProfiles() {
        val slots = ControlLayout.listSlots(activity)
        val labels = ArrayList<String>(slots.size + 12)
        val actions = ArrayList<() -> Unit>(slots.size + 12)
        labels.add(activity.getString(R.string.layout_save))
        actions.add { pickSaveSlot() }
        labels.add(activity.getString(R.string.profile_save_as))
        actions.add { saveCurrentAs() }
        labels.add(activity.getString(R.string.layout_export_current))
        actions.add { shareLayout(overlay.layout, activity.getString(R.string.layout_current)) }
        for (id in (1..LayoutStore.MAX_NUMBERED).map { it.toString() }) {
            val slot = slots.firstOrNull { it.id == id }
            if (slot == null) {
                labels.add(activity.getString(R.string.profile_slot_empty, id))
                actions.add { saveToSlot(id) }
            } else {
                labels.add(activity.getString(R.string.profile_slot, id, slot.displayName))
                actions.add { slotActions(slot) }
            }
        }
        for (slot in slots.filter { !it.numbered }) {
            labels.add(slot.displayName)
            actions.add { slotActions(slot) }
        }
        labels.add(activity.getString(R.string.layout_import))
        actions.add { importLayout() }
        AlertDialog.Builder(activity)
            .setTitle(R.string.layout_title)
            .setItems(labels.toTypedArray()) { _, which -> actions[which].invoke() }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    /** Load, overwrite, rename, duplicate, export or delete one saved pad. */
    private fun slotActions(slot: LayoutSlot) {
        val labels = arrayOf(
            activity.getString(R.string.profile_load),
            activity.getString(R.string.profile_overwrite_current),
            activity.getString(R.string.profile_rename),
            activity.getString(R.string.profile_duplicate),
            activity.getString(R.string.layout_export),
            activity.getString(R.string.profile_delete),
        )
        AlertDialog.Builder(activity)
            .setTitle(slot.displayName)
            .setItems(labels) { _, which ->
                when (which) {
                    0 -> loadSlot(slot)
                    1 -> writeSlot(slot.id, slot.displayName)
                    2 -> renameSlot(slot)
                    3 -> duplicateSlot(slot)
                    4 -> exportSlot(slot)
                    else -> confirmDeleteSlot(slot)
                }
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    /**
     * The five numbered places as a picker, each taken one shown with the name
     * of the layout already in it. Every save path starts here: a layout only
     * ever lives in a numbered slot, never as a file of its own below them.
     */
    private fun showSlotPicker(titleRes: Int, onPick: (SaveSlotChoice) -> Unit) {
        val slots = ControlLayout.saveSlots(activity)
        val labels = Array(slots.size) { index ->
            val slot = slots[index]
            if (slot.occupied) {
                activity.getString(R.string.profile_slot, slot.id, slot.displayName)
            } else {
                activity.getString(R.string.profile_slot_empty, slot.id)
            }
        }
        AlertDialog.Builder(activity)
            .setTitle(titleRes)
            .setItems(labels) { _, which -> onPick(slots[which]) }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    /**
     * "Save Layout": the five numbered places, with the taken ones named, so the
     * player picks where the pad goes instead of typing a name first.
     */
    private fun pickSaveSlot() = showSlotPicker(R.string.layout_save) { saveToChosenSlot(it) }

    /**
     * Saves the pad into the chosen numbered slot. A slot that already holds a
     * layout is only replaced after the player says yes; "No", the back button
     * or a tap outside the prompt leaves that saved pad exactly as it was. An
     * overwrite keeps the slot's name and changes only the controls in it.
     */
    private fun saveToChosenSlot(slot: SaveSlotChoice) {
        val name = slot.displayName.ifBlank {
            activity.getString(R.string.profile_default_name, slot.id)
        }
        if (!slot.occupied) {
            writeSlot(slot.id, name, reopen = false)
            return
        }
        AlertDialog.Builder(activity)
            .setTitle(R.string.layout_save)
            .setMessage(R.string.slot_taken_confirm)
            .setNegativeButton(R.string.no, null)
            .setPositiveButton(R.string.yes) { _, _ -> writeSlot(slot.id, name, reopen = false) }
            .show()
    }

    /** Saves the current pad into an empty numbered place under a typed name. */
    private fun saveToSlot(id: String) {
        val suggested = activity.getString(R.string.profile_default_name, id)
        nameDialog(activity.getString(R.string.profile_save_as), suggested) { name ->
            writeSlot(id, name)
        }
    }

    /**
     * TG1..TG6's assignment: the game actions a toggle can keep held, in the
     * order the title's own control table lists them. One toggle holds one
     * action, and the action is what the pad holds every frame while the toggle
     * is on -- sprint holds RT, running jockey holds LT + RT, super cancel holds
     * RB + RT -- until the toggle is tapped off again.
     *
     * The list is [ToggleAction] itself, so the editor cannot offer an action
     * the pad does not know how to hold.
     */
    private fun pickToggleMapping() {
        val element = current ?: overlay.selected ?: return
        if (element.kind != ControlKind.LATCH) return
        val actions = ToggleAction.entries
        val labels = Array(actions.size) { actions[it].label }
        val selected = actions.indexOf(element.toggleAction).coerceAtLeast(0)
        AlertDialog.Builder(activity)
            .setTitle(
                activity.getString(
                    R.string.toggle_mapping_title,
                    ControlLayout.labelFor(element, overlay.playStation),
                ),
            )
            .setSingleChoiceItems(labels, selected) { dialog, which ->
                remember()
                element.toggleAction = actions[which]
                // A toggle that is already engaged keeps the hold it latched
                // with until it is tapped off and on again: the hold is taken
                // at the tap, not guessed from an action that changed under it.
                show(element)
                overlay.refresh()
                save()
                dialog.dismiss()
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    /** Puts a factory pad back, without touching any saved slot. */
    private fun loadBuiltIn(profile: String) {
        remember()
        overlay.layout.replaceWith(
            ControlLayout.defaults(overlay.layout.widthDp, overlay.layout.heightDp, profile),
        )
        overlay.select(null)
        show(null)
        overlay.refresh()
        save()
    }

    private fun loadSlot(slot: LayoutSlot) {
        val loaded = ControlLayout.loadSlot(activity, slot.id)
        if (loaded == null) {
            Toast.makeText(activity, R.string.profile_failed, Toast.LENGTH_SHORT).show()
            return
        }
        remember()
        overlay.layout.replaceWith(loaded)
        overlay.select(null)
        show(null)
        overlay.refresh()
        save()
    }

    /**
     * "Save Current Layout As…": a numbered place first, then a name for the
     * inside of it. The typed name is the slot's label ("1 · test"), never a
     * file of its own, so a save can no longer land below the five slots as a
     * loose profile while the slot the player meant stays empty. A slot that
     * already holds a layout is only replaced after the player says so.
     */
    private fun saveCurrentAs() {
        showSlotPicker(R.string.profile_save_as) { slot ->
            if (!slot.occupied) {
                askSlotName(slot) { name -> writeSlot(slot.id, name, reopen = false) }
                return@showSlotPicker
            }
            AlertDialog.Builder(activity)
                .setTitle(R.string.profile_save_as)
                .setMessage(R.string.slot_taken_confirm)
                .setNegativeButton(R.string.no, null)
                .setPositiveButton(R.string.yes) { _, _ ->
                    askSlotName(slot) { name -> writeSlot(slot.id, name, reopen = false) }
                }
                .show()
        }
    }

    /** The name for the inside of [slot]: what it is called now, or "Slot N". */
    private fun askSlotName(slot: SaveSlotChoice, onOk: (String) -> Unit) {
        val suggested = slot.displayName.ifBlank {
            activity.getString(R.string.profile_default_name, slot.id)
        }
        nameDialog(activity.getString(R.string.profile_save_as), suggested, onOk)
    }

    private fun writeSlot(id: String, name: String, reopen: Boolean = true) {
        val ok = ControlLayout.saveSlot(activity, overlay.layout, id, name)
        Toast.makeText(
            activity, if (ok) R.string.profile_saved else R.string.profile_failed, Toast.LENGTH_SHORT,
        ).show()
        // The slot picker closes on its own answer; only the layouts list wants
        // to come back so the player sees the slot it now holds.
        if (ok && reopen) showProfiles()
    }

    private fun renameSlot(slot: LayoutSlot) {
        nameDialog(activity.getString(R.string.profile_rename), slot.displayName) { name ->
            val ok = ControlLayout.renameSlot(activity, slot.id, name)
            Toast.makeText(
                activity, if (ok) R.string.profile_renamed else R.string.profile_failed,
                Toast.LENGTH_SHORT,
            ).show()
            if (ok) showProfiles()
        }
    }

    /**
     * Copies a saved pad into the first free numbered slot. With all five used,
     * the copy goes beside it under its own id -- never on top of another pad,
     * which is the silent overwrite this replaces.
     */
    private fun duplicateSlot(slot: LayoutSlot) {
        val free = ControlLayout.freeSlot(activity)
        val id = free ?: LayoutStore.sanitize(slot.displayName + " copy")
        if (free == null && ControlLayout.listSlots(activity).any { it.id == id }) {
            Toast.makeText(activity, R.string.profile_full, Toast.LENGTH_SHORT).show()
            return
        }
        val copyName = activity.getString(R.string.profile_copy_name, slot.displayName)
        val ok = ControlLayout.duplicateSlot(activity, slot.id, id, copyName)
        Toast.makeText(
            activity, if (ok) R.string.profile_duplicated else R.string.profile_failed,
            Toast.LENGTH_SHORT,
        ).show()
        if (ok) showProfiles()
    }

    private fun confirmDeleteSlot(slot: LayoutSlot) {
        AlertDialog.Builder(activity)
            .setTitle(R.string.profile_delete)
            .setMessage(activity.getString(R.string.profile_delete_confirm, slot.displayName))
            .setNegativeButton(R.string.cancel, null)
            .setPositiveButton(R.string.profile_delete) { _, _ ->
                ControlLayout.deleteSlot(activity, slot.id)
                showProfiles()
            }
            .show()
    }

    private fun exportSlot(slot: LayoutSlot) {
        val layout = ControlLayout.loadSlot(activity, slot.id) ?: return
        shareLayout(layout, slot.displayName)
    }

    private fun shareLayout(layout: ControlLayout, name: String) {
        val json = LayoutEnvelope.export(layout, name, appVersion())
        val ok = LayoutTransfer.share(activity, json, name)
        Toast.makeText(
            activity, if (ok) R.string.layout_exported else R.string.layout_export_failed,
            Toast.LENGTH_SHORT,
        ).show()
    }

    private fun appVersion(): String = try {
        activity.packageManager.getPackageInfo(activity.packageName, 0).versionName ?: ""
    } catch (_: Throwable) {
        ""
    }

    private fun nameDialog(title: String, initial: String, onOk: (String) -> Unit) {
        val dp = activity.resources.displayMetrics.density
        val input = EditText(activity).apply {
            setText(initial)
            hint = activity.getString(R.string.profile_name)
            setPadding((20 * dp).roundToInt(), (8 * dp).roundToInt(), (20 * dp).roundToInt(), 0)
        }
        AlertDialog.Builder(activity)
            .setTitle(title)
            .setView(input)
            .setNegativeButton(R.string.cancel, null)
            .setPositiveButton(android.R.string.ok) { _, _ ->
                val name = input.text.toString().trim()
                if (name.isEmpty()) {
                    Toast.makeText(activity, R.string.profile_failed, Toast.LENGTH_SHORT).show()
                } else {
                    onOk(name)
                }
            }
            .show()
    }

    // ---- import / export -----------------------------------------------------

    private fun importLayout() {
        LayoutTransfer.openDocument(activity)
    }

    /**
     * The document picker's answer, forwarded by whichever activity hosts this
     * panel. A file that fails validation changes nothing: the toast names the
     * reason, and the pad the player is editing stays exactly as it was.
     */
    fun handleActivityResult(requestCode: Int, resultCode: Int, data: android.content.Intent?) {
        if (requestCode != LayoutTransfer.REQUEST_IMPORT) return
        if (resultCode != Activity.RESULT_OK) return
        val uri = data?.data ?: return
        val text = LayoutTransfer.read(activity, uri)
        if (text == null) {
            Toast.makeText(activity, R.string.layout_import_failed, Toast.LENGTH_SHORT).show()
            return
        }
        when (val result = LayoutEnvelope.import(text)) {
            is LayoutEnvelope.ImportResult.Ok -> {
                remember()
                overlay.layout.replaceWith(result.layout)
                overlay.select(null)
                show(null)
                overlay.refresh()
                save()
                Toast.makeText(activity, R.string.layout_imported, Toast.LENGTH_SHORT).show()
            }
            is LayoutEnvelope.ImportResult.Rejected ->
                Toast.makeText(activity, result.reason, Toast.LENGTH_LONG).show()
        }
    }

    fun attach() {
        if (attached) return
        root.addView(view, params(top = false))
        val margin = (6 * activity.resources.displayMetrics.density).roundToInt()
        root.addView(
            actions,
            FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT,
                Gravity.TOP or Gravity.END,
            ).apply { topMargin = margin; marginEnd = margin },
        )
        root.addView(
            idle,
            FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT,
                Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL,
            ),
        )
        atTop = false
        attached = true
        show(null)
    }

    fun detach() {
        if (!attached) return
        root.removeView(view)
        root.removeView(idle)
        root.removeView(actions)
        attached = false
    }

    private fun params(top: Boolean) = FrameLayout.LayoutParams(
        ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT,
        if (top) Gravity.TOP else Gravity.BOTTOM,
    )

    fun show(element: ControlElement?) {
        current = element
        // Only a selected control has anything to adjust; until then the strip stays out of the way.
        view.visibility = if (element == null) View.GONE else View.VISIBLE
        idle.visibility = if (element == null) View.VISIBLE else View.GONE
        resetOne.isEnabled = element != null
        if (element == null) {
            swipes.visibility = View.GONE
            wheel.visibility = View.GONE
            mapping.visibility = View.GONE
        }
        val wantTop = element != null && element.yFracOn(overlay.stance) > 0.55f
        if (attached && wantTop != atTop) {
            view.layoutParams = params(wantTop)
            atTop = wantTop
        }
        if (element == null) {
            title.setText(R.string.edit_pick_one)
            clearance.visibility = View.GONE
            size.isEnabled = false
            opacity.isEnabled = false
            visible.isEnabled = false
            toggle.isEnabled = false
            return
        }
        title.text = activity.getString(R.string.edit_selected, ControlLayout.labelFor(element, overlay.playStation))
        // L1/R1 and Start/Select are not swipe targets: the shoulders are the
        // pad's modifiers and the menu buttons are the title's, so offering four
        // directions for them only invited bindings that could not work.
        swipes.visibility = if (offersSwipes(element)) View.VISIBLE else View.GONE
        wheel.visibility = if (element.kind == ControlKind.TRICK) View.VISIBLE else View.GONE
        mapping.visibility = if (element.kind == ControlKind.LATCH) View.VISIBLE else View.GONE
        size.isEnabled = true
        opacity.isEnabled = true
        visible.isEnabled = true
        toggle.isEnabled = element.kind == ControlKind.BUTTON || element.kind == ControlKind.TRIGGER
        val smallest = if (element.kind == ControlKind.LOOK) 120f else 36f
        val largest = if (element.kind == ControlKind.LOOK) 900f else 200f
        val elementSize = element.sizeOn(overlay.stance)
        size.progress = (((elementSize - smallest) / (largest - smallest)) * 100f)
            .roundToInt().coerceIn(0, 100)
        opacity.progress = (element.alphaOn(overlay.stance) * 100f).roundToInt().coerceIn(0, 100)
        updating = true
        visible.isChecked = element.visibleIn(overlay.stance)
        toggle.isChecked = element.toggle
        updating = false
        showClearance(element)
    }
}
