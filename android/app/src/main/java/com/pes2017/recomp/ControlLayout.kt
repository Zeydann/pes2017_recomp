package com.pes2017.recomp

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import kotlin.math.max
import kotlin.math.min

/** The retuned pad: the default for fresh installs. */
const val PROFILE_MODERN = "modern"

/** The spacing before the 64 dp retune, kept for players who liked it. */
const val PROFILE_CLASSIC = "classic"

/** What a control does when it is touched. */
enum class ControlKind {
    /** A round button that holds one of the pad's bits while it is down. */
    BUTTON,

    /** A round button that holds a trigger down (0..255). */
    TRIGGER,

    /** The left stick: a base with a knob that follows the finger. */
    STICK,

    /** The look area: a region where dragging turns the view. */
    LOOK,

    /**
     * Four directions from one socket, as a cross: the arm nearest the thumb is
     * held while it stays there. The menus are navigated with it, and the stick
     * stands in for it in a match, so this is off the pitch by default.
     */
    DPAD,

    /**
     * A flick in one of four directions, read when the finger lifts. It holds no
     * bit of its own, so a swipe sends exactly one command -- putting the
     * gesture on an action button would send that button's bit for the whole
     * travel as well.
     */
    SWIPE,

    /**
     * One tap, one skill: pressing it plays the move its `value` names
     * (runtime/skill_moves.h) on the skill stick, so a feint that needs a
     * two-part stick gesture is a thumb on a button. It holds no bit of its own
     * and fires once, on the press; the runtime owns the stick until the move's
     * last frame, so a second thumb cannot interrupt it.
     */
    SKILL,

    /**
     * The skill wheel: hold it and the eight sectors of a ring appear, each one
     * a move from runtime/skill_moves.h; slide to a sector and lift to play it.
     * A quick tap does nothing -- the wheel only opens after [TrickWheel.HOLD_MS]
     * -- so a thumb resting near it cannot set off a feint. It holds no bit of
     * its own; the runtime owns the stick and buttons until the move's last
     * frame, and the player's left stick supplies whatever exit the move waits
     * for.
     */
    TRICK,

    /**
     * The stance chip: one tap swaps the attacking pad for the defending one and
     * back, with the labels following. It holds no bit of its own, so a flip
     * can never leave a button or a trigger down; the title decides what the
     * action buttons mean from possession, and this only changes what the pad
     * says and which controls are arranged for which half of the game.
     */
    STANCE,

    /**
     * A spare button (c1..c10) with no bit of its own until the player gives it
     * one: its centre tap and its four swipes are bound in the editor. Ten of
     * them ship hidden, so a pad can grow a command the title has but the
     * default pad does not, without the editor having to invent a control.
     *
     * It behaves like a plain button for swipes -- the bits are sent when the
     * finger lifts -- but because the button itself may hold nothing, a thumb
     * resting on an unbound one sends nothing at all.
     */
    CUSTOM,

    /**
     * A latching toggle (tg1, tg2): one tap engages the command bound to it and
     * holds it down until a second tap lets go, so a player who has to hold two
     * things at once has one thumb free. Unbound until the editor gives it a
     * bit; a toggle with nothing bound latches nothing at all.
     */
    LATCH,

    /**
     * A five-way tactical button (sw1, sw2): four swipes and a centre tap, with
     * nothing held in between. It is the same gesture the swipe patch has, sized
     * and named for a thumb, and the directions carry the title's quick tactical
     * commands -- preset tactics, the attack/defence level, the advanced
     * instructions under LT.
     */
    TACTICAL,
}

/** When a control belongs on screen. */
enum class PhaseMask {
    ALWAYS,
    /** The title's menus, including while it loads. */
    MENUS,
    /** A match in progress. */
    MATCH,
    /** A match, with the attacking pad chosen (gamepad and touch alike). */
    ATTACK,
    /** A match, with the defending pad chosen. */
    DEFEND,
}

/**
 * Which of the player's two match pads is out, the attacking one or the
 * defending one. The title does not tell the app who has the ball, so this is
 * the player's own switch -- in Settings and in the pause menu -- and every
 * control can be arranged for one of them.
 */
enum class PadStance {
    ATTACK,
    DEFEND,
}

/**
 * What an action button does, independent of the words printed on it. A player
 * may rename `SHOOT` to anything; the fake shot must still know which button
 * fires the shot, so behaviour reads this rather than the caption.
 */
enum class ControlRole {
    NONE,
    PASS,
    SHOOT,
    CROSS,
    THROUGH,

    /**
     * The jockey button: it holds the left trigger, which is how the title's
     * contain/jockey stance is kept, and it latches so the thumb that would be
     * holding it can tackle instead.
     */
    JOCKEY,
    ;

    companion object {
        /** The role that belongs to a pad bit, by the title's default binding. */
        fun of(bit: Int): ControlRole = when (bit) {
            NativeBridge.A -> PASS
            NativeBridge.B -> CROSS
            NativeBridge.X -> SHOOT
            NativeBridge.Y -> THROUGH
            else -> NONE
        }
    }
}

/**
 * What the title is doing, as the runtime reports it. [STARTING] and [UNKNOWN]
 * keep the menus' controls: a build that cannot tell says nothing, and hiding a
 * d-pad because of a guess would be worse than leaving it where the player put
 * it.
 */
enum class GamePhase {
    UNKNOWN,
    MENUS,
    MATCH,
    ;

    /** Whether a control marked [mask] is on screen in this phase and stance. */
    fun shows(mask: PhaseMask, stance: PadStance = PadStance.ATTACK): Boolean = when (mask) {
        PhaseMask.ALWAYS -> true
        PhaseMask.MENUS -> this != GamePhase.MATCH
        PhaseMask.MATCH -> this == GamePhase.MATCH
        // An attacking or defending control is a match control that only one
        // of the two pads carries. An unknown phase keeps it, as it keeps the
        // menus' controls: hiding it on a guess would be worse.
        PhaseMask.ATTACK -> this != GamePhase.MENUS && stance == PadStance.ATTACK
        PhaseMask.DEFEND -> this != GamePhase.MENUS && stance == PadStance.DEFEND
    }
}

/**
 * One thing on screen. Positions are fractions of the window so a layout
 * made on one phone still makes sense on another, and sizes are in
 * density-independent pixels so a button is the same size in millimetres on
 * every screen.
 */
data class ControlElement(
    val id: String,
    val kind: ControlKind,
    /** The pad bit (BUTTON), or which trigger (TRIGGER: 0 left, 1 right). */
    var value: Int,
    var label: String,
    var xFrac: Float,
    var yFrac: Float,
    var sizeDp: Float,
    var alpha: Float = 0.45f,
    var visible: Boolean = true,
    /** Tap to hold, tap again to let go. */
    var toggle: Boolean = false,
    /** What it does on the ball (or, for a stick, a short name), drawn on the control. */
    var sub: String = "",
    /** What it does without the ball -- defending -- drawn smaller beneath. */
    var sub2: String = "",
    /** What the control does, for behaviour that must survive a renamed label. */
    var role: ControlRole = ControlRole.NONE,
    // Geometry for the defending pad, when the player wants the same control
    // somewhere else (or a different size) with the ball at their feet. Null
    // means "the same as attacking", so a layout made before this keeps the
    // single position it always had.
    var defendX: Float? = null,
    var defendY: Float? = null,
    var defendSize: Float? = null,
    var defendAlpha: Float? = null,
    var defendVisible: Boolean? = null,
    /** Which of the title's screens it belongs to. */
    var shownWhen: PhaseMask = PhaseMask.ALWAYS,
    // What a SWIPE sends for each direction. 0 is "nothing", which is how a
    // fresh pad starts: a swipe that fired something unasked would be a
    // surprise, and every bit is one tap away in the editor.
    var swipeUp: Int = 0,
    var swipeDown: Int = 0,
    var swipeLeft: Int = 0,
    var swipeRight: Int = 0,
    /** What a touch on it sends when it was not a swipe at all. */
    var swipeTap: Int = 0,
    // What each direction also holds or repeats. The bits above stay the primary
    // button, which is what a layout file written before this carried and what a
    // plain binding still uses; these add PES's held-modifier combinations
    // (LB + pass) and the title's double taps (the low cross).
    var swipeUpMod: SwipeModifier = SwipeModifier.NONE,
    var swipeDownMod: SwipeModifier = SwipeModifier.NONE,
    var swipeLeftMod: SwipeModifier = SwipeModifier.NONE,
    var swipeRightMod: SwipeModifier = SwipeModifier.NONE,
    var swipeUpDouble: Boolean = false,
    var swipeDownDouble: Boolean = false,
    var swipeLeftDouble: Boolean = false,
    var swipeRightDouble: Boolean = false,
    /**
     * What a TRICK wheel plays in each sector, as an index into
     * runtime/skill_moves.h, or -1 for a sector with no move. The order is the
     * wheel's: 0 right, then anticlockwise (up-right, up, ...). Changing this
     * array in place keeps the element's identity, which the overlay uses to
     * find the control under a finger.
     */
    var trickMoves: IntArray = IntArray(SkillMoves.COUNT) { -1 },
    /**
     * The saved layout this latch switches to when it is engaged, as a
     * [LayoutStore] slot id, or "" for a plain toggle. Only the two custom
     * toggles (tg1, tg2) use it: one tap puts that pad on screen, which is how a
     * player keeps a defending arrangement and an attacking one a thumb apart.
     */
    var toggleProfile: String = "",
) {
    /** The move a wheel sector plays, or -1 for none and for a sector off the wheel. */
    fun trickMove(sector: Int): Int {
        if (sector < 0 || sector >= trickMoves.size) return -1
        return trickMoves[sector]
    }

    /** Binds one sector of the wheel; -1 leaves it empty. */
    fun setTrickMove(sector: Int, move: Int) {
        if (sector < 0 || sector >= trickMoves.size) return
        trickMoves[sector] = if (SkillMoves.valid(move)) move else -1
    }
    /** Whether it is on screen at all: the player's switch, the phase and the pad, all three. */
    fun shownIn(phase: GamePhase, stance: PadStance = PadStance.ATTACK): Boolean =
        visibleIn(stance) && phase.shows(shownWhen, stance)

    // ---- per-stance geometry ------------------------------------------------

    /** The horizontal position on [stance]'s pad. */
    fun xFracOn(stance: PadStance): Float =
        if (stance == PadStance.DEFEND) defendX ?: xFrac else xFrac

    /** The vertical position on [stance]'s pad. */
    fun yFracOn(stance: PadStance): Float =
        if (stance == PadStance.DEFEND) defendY ?: yFrac else yFrac

    /** The size on [stance]'s pad. */
    fun sizeOn(stance: PadStance): Float =
        if (stance == PadStance.DEFEND) defendSize ?: sizeDp else sizeDp

    /** The opacity on [stance]'s pad. */
    fun alphaOn(stance: PadStance): Float =
        if (stance == PadStance.DEFEND) defendAlpha ?: alpha else alpha

    /** Whether the player shows it on [stance]'s pad. */
    fun visibleIn(stance: PadStance): Boolean =
        if (stance == PadStance.DEFEND) defendVisible ?: visible else visible

    /** Whether this control has its own defending-pad geometry at all. */
    fun hasDefendOverride(): Boolean =
        defendX != null || defendY != null || defendSize != null ||
            defendAlpha != null || defendVisible != null

    // The editor writes through these, so a change made while the defending
    // pad is out lands on the defending copy rather than moving both.
    // One canonical map: a control has one place, one size, one opacity and one
    // switch, and a defending-pad copy is kept in step with it rather than being
    // moved on its own. Two independent copies were the source of the overlap
    // bugs, where moving a control with the ball at the player's feet left its
    // attacking twin under the pass button.
    fun setX(x: Float, stance: PadStance) {
        xFrac = x
        if (defendX != null) defendX = x
    }

    fun setY(y: Float, stance: PadStance) {
        yFrac = y
        if (defendY != null) defendY = y
    }

    fun setSize(size: Float, stance: PadStance) {
        sizeDp = size
        if (defendSize != null) defendSize = size
    }

    fun setAlpha(value: Float, stance: PadStance) {
        alpha = value
        if (defendAlpha != null) defendAlpha = value
    }

    fun setVisible(value: Boolean, stance: PadStance) {
        visible = value
        if (defendVisible != null) defendVisible = value
    }

    /** Starts a distinct defending-pad copy at today's attacking values. */
    fun splitStance() {
        if (hasDefendOverride()) return
        defendX = xFrac
        defendY = yFrac
        defendSize = sizeDp
        defendAlpha = alpha
        defendVisible = true
    }

    /** Drops the defending-pad copy, so both pads share the attacking values again. */
    fun mergeStance() {
        defendX = null
        defendY = null
        defendSize = null
        defendAlpha = null
        defendVisible = null
    }

    /**
     * What one swipe direction sends on this control: its own pad bit, or 0 for
     * a direction the player has not bound. Each of the four is independent, so
     * the same call answers for an action button and for the swipe patch.
     */
    internal fun swipeBit(direction: Swipe): Int = when (direction) {
        Swipe.UP -> swipeUp
        Swipe.DOWN -> swipeDown
        Swipe.LEFT -> swipeLeft
        Swipe.RIGHT -> swipeRight
        // A touch that was not a swipe; unbound unless the player asked for it.
        Swipe.NONE -> swipeTap
    }

    /** Everything one swipe direction sends: its bit, its modifier, its tap count. */
    internal fun swipeAction(direction: Swipe): SwipeAction = when (direction) {
        Swipe.UP -> SwipeAction(swipeUp, swipeUpMod, swipeUpDouble)
        Swipe.DOWN -> SwipeAction(swipeDown, swipeDownMod, swipeDownDouble)
        Swipe.LEFT -> SwipeAction(swipeLeft, swipeLeftMod, swipeLeftDouble)
        Swipe.RIGHT -> SwipeAction(swipeRight, swipeRightMod, swipeRightDouble)
        Swipe.NONE -> SwipeAction(swipeTap)
    }

    /** Replaces one direction whole: bit, modifier and double tap together. */
    internal fun setSwipeAction(direction: Swipe, action: SwipeAction) {
        when (direction) {
            Swipe.UP -> {
                swipeUp = action.bit; swipeUpMod = action.modifier; swipeUpDouble = action.doubleTap
            }
            Swipe.DOWN -> {
                swipeDown = action.bit; swipeDownMod = action.modifier; swipeDownDouble = action.doubleTap
            }
            Swipe.LEFT -> {
                swipeLeft = action.bit; swipeLeftMod = action.modifier; swipeLeftDouble = action.doubleTap
            }
            Swipe.RIGHT -> {
                swipeRight = action.bit; swipeRightMod = action.modifier; swipeRightDouble = action.doubleTap
            }
            Swipe.NONE -> swipeTap = action.bit
        }
    }
}

/**
 * The whole on-screen pad: what it is, where it came from, and how it is
 * kept. Saved as json in the app's own folder, so a layout survives an
 * update, and one bad file never stops the game starting -- it falls back to
 * the defaults instead.
 */
class ControlLayout private constructor(
    val elements: MutableList<ControlElement>,
    /** The window the defaults were laid out for, so a reset puts things back there. */
    val widthDp: Float,
    val heightDp: Float,
    /** Which factory pad a reset restores: [PROFILE_MODERN] or [PROFILE_CLASSIC]. */
    var profile: String = PROFILE_MODERN,
) {

    /** Ids the player took off the pad, so a reload does not put them back. */
    private val removed = LinkedHashSet<String>()

    /**
     * The bit each id ships with, so a rebound control can be written without
     * every other control paying for a field it does not use.
     */
    private val factoryBits: Map<String, Int> by lazy {
        defaults(widthDp, heightDp, profile).elements.associate { it.id to it.value }
    }

    fun find(id: String): ControlElement? = elements.firstOrNull { it.id == id }

    /** Puts a control back on the pad; a second copy of one id is refused. */
    fun add(element: ControlElement): Boolean {
        if (find(element.id) != null) return false
        removed.remove(element.id)
        elements.add(element)
        return true
    }

    /** Takes a control off the pad, remembering it so a reload honours the choice. */
    fun remove(id: String): Boolean {
        if (id == "stick" || id == "look") return false
        val had = elements.removeAll { it.id == id }
        if (had) removed.add(id)
        return had
    }

    fun resetAll() {
        val fresh = defaults(widthDp, heightDp, profile)
        elements.clear()
        elements.addAll(fresh.elements)
        removed.clear()
    }

    fun reset(id: String) {
        val fresh = defaults(widthDp, heightDp, profile).find(id) ?: return
        val index = elements.indexOfFirst { it.id == id }
        if (index >= 0) elements[index] = fresh else elements.add(fresh)
        removed.remove(id)
    }

    /** Copies another pad over this one, for undo, profiles and backup restore. */
    fun replaceWith(other: ControlLayout) {
        elements.clear()
        elements.addAll(other.elements)
        removed.clear()
        removed.addAll(other.removed)
        profile = other.profile
    }

    /** Restores a snapshot written by [toJson]; false if it cannot be read. */
    fun restoreFrom(json: String): Boolean {
        val parsed = parse(json, widthDp, heightDp) ?: return false
        replaceWith(parsed)
        return true
    }

    /** The controls a fresh pad has that this one does not: the add palette. */
    fun missingControls(): List<ControlElement> =
        defaults(widthDp, heightDp, profile).elements.filter { find(it.id) == null }

    fun toJson(): String {
        val array = JSONArray()
        for (element in elements) {
            val item = JSONObject()
            item.put("id", element.id)
            item.put("x", element.xFrac.toDouble())
            item.put("y", element.yFrac.toDouble())
            item.put("size", element.sizeDp.toDouble())
            item.put("alpha", element.alpha.toDouble())
            item.put("visible", element.visible)
            item.put("toggle", element.toggle)
            item.put("when", element.shownWhen.name)
            item.put("role", element.role.name)
            item.put("label", element.label)
            // What the control is bound to, when the editor has rebound it. A
            // value is otherwise implied by the control's id, so only a changed
            // one is written and a pad that was never rebound stays the file it
            // always was. Without this a rebound toggle or button came back
            // holding the factory bit after a restart.
            if (element.value != factoryBits[element.id]) item.put("value", element.value)
            if (element.sub.isNotEmpty()) item.put("sub", element.sub)
            if (element.sub2.isNotEmpty()) item.put("sub2", element.sub2)
            // Only a control the player has given a separate defending pad
            // writes the object; every other control stays one set of numbers.
            if (element.hasDefendOverride()) {
                val defend = JSONObject()
                element.defendX?.let { defend.put("x", it.toDouble()) }
                element.defendY?.let { defend.put("y", it.toDouble()) }
                element.defendSize?.let { defend.put("size", it.toDouble()) }
                element.defendAlpha?.let { defend.put("alpha", it.toDouble()) }
                element.defendVisible?.let { defend.put("visible", it) }
                item.put("defend", defend)
            }
            if (element.swipeUp != 0 || element.swipeDown != 0 ||
                element.swipeLeft != 0 || element.swipeRight != 0 || element.swipeTap != 0
            ) {
                item.put("swipes", JSONArray(listOf(element.swipeUp, element.swipeDown,
                    element.swipeLeft, element.swipeRight, element.swipeTap)))
            }
            // The compound half is written only when it is used, so a layout
            // of plain bits stays the file it always was. An older build reads
            // the `swipes` array above and ignores this; this build reads both.
            if (element.swipeUpMod != SwipeModifier.NONE ||
                element.swipeDownMod != SwipeModifier.NONE ||
                element.swipeLeftMod != SwipeModifier.NONE ||
                element.swipeRightMod != SwipeModifier.NONE ||
                element.swipeUpDouble || element.swipeDownDouble ||
                element.swipeLeftDouble || element.swipeRightDouble
            ) {
                val actions = JSONArray()
                for (direction in DIRECTIONS) {
                    val action = element.swipeAction(direction)
                    val entry = JSONObject()
                    entry.put("b", action.bit)
                    if (action.modifier != SwipeModifier.NONE) entry.put("m", action.modifier.name)
                    if (action.doubleTap) entry.put("d", true)
                    actions.put(entry)
                }
                item.put("swipeActions", actions)
            }
            // The wheel's sectors, written only for a wheel that uses one: a
            // layout from before the wheel keeps the defaults it starts from.
            if (element.kind == ControlKind.TRICK) {
                item.put("trick", JSONArray(element.trickMoves.toList()))
            }
            // A custom toggle's mapping, written only when it has one, so an
            // ordinary pad stays the file it always was.
            if (element.toggleProfile.isNotEmpty()) item.put("profile", element.toggleProfile)
            array.put(item)
        }
        val root = JSONObject()
        root.put("version", FORMAT_VERSION)
        root.put("w", widthDp.toDouble())
        root.put("h", heightDp.toDouble())
        root.put("profile", profile)
        root.put("elements", array)
        if (removed.isNotEmpty()) root.put("removed", JSONArray(removed.toList()))
        return root.toString(2)
    }

    fun save(context: Context) {
        try {
            val file = File(context.filesDir, FILE_NAME)
            // The file being replaced becomes the one-generation backup, so a
            // reset or a bad edit can always be walked back once.
            if (file.exists()) {
                try {
                    file.copyTo(File(context.filesDir, BACKUP_NAME), overwrite = true)
                } catch (_: Throwable) {
                }
            }
            // Written beside and moved into place: a layout being saved as
            // the process is killed must not leave a half-written file that
            // the next start cannot read.
            LayoutStore.writeAtomically(file, toJson())
        } catch (_: Throwable) {
            // A layout that cannot be saved is a small loss; crashing over
            // it would be a large one.
        }
    }

    /** Restores the previous saved pad; false when there is no backup. */
    fun restoreBackup(context: Context): Boolean {
        val text = try {
            File(context.filesDir, BACKUP_NAME).readText()
        } catch (_: Throwable) {
            return false
        }
        if (!restoreFrom(text)) return false
        save(context)
        return true
    }

    companion object {
        private const val FILE_NAME = "controls.json"
        private const val BACKUP_NAME = "controls.backup.json"
        private const val BROKEN_NAME = "controls.broken.json"

        /**
         * The layout file's version. 8 folds the separate defending-pad copies
         * back onto the one canonical map (see [foldDefendOverrides]), so a
         * layout written by an older build cannot leave two controls in the
         * same place.
         */
        private const val FORMAT_VERSION = 8

        /** The four directions, in the order the file and the editor list them. */
        private val DIRECTIONS = arrayOf(Swipe.UP, Swipe.DOWN, Swipe.LEFT, Swipe.RIGHT)

        fun load(context: Context): ControlLayout {
            // The whole screen (the match is full screen, into the cutout), in
            // landscape whichever way the phone was held when asked.
            val metrics = context.resources.displayMetrics
            var widthPx = metrics.widthPixels
            var heightPx = metrics.heightPixels
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
                try {
                    val bounds = context.getSystemService(android.view.WindowManager::class.java)
                        .maximumWindowMetrics.bounds
                    widthPx = bounds.width()
                    heightPx = bounds.height()
                } catch (_: Throwable) {
                }
            }
            val widthDp = max(widthPx, heightPx) / metrics.density
            val heightDp = min(widthPx, heightPx) / metrics.density
            val fallback = defaults(widthDp, heightDp)
            val file = File(context.filesDir, FILE_NAME)
            if (!file.exists()) return fallback
            val text = try {
                file.readText()
            } catch (_: Throwable) {
                return fallback
            }
            val parsed = parse(text, widthDp, heightDp)
            if (parsed == null) {
                // Kept, not deleted: the file may come from a newer build, and
                // destroying a player's pad to recover from it is worse than
                // starting this session on the factory one.
                try {
                    file.copyTo(File(context.filesDir, BROKEN_NAME), overwrite = true)
                } catch (_: Throwable) {
                }
                return fallback
            }
            return parsed
        }

        /**
         * Reads a pad written by [toJson]. Every version is migrated onto the
         * current defaults rather than discarded: an element this build does
         * not know is left out, and one it has gained keeps its default. A null
         * means the text is not a layout at all (or comes from a newer build).
         */
        fun parse(text: String, fallbackWidthDp: Float = 800f, fallbackHeightDp: Float = 360f): ControlLayout? {
            return try {
                val root = JSONObject(text)
                val version = root.optInt("version", 1)
                if (version > FORMAT_VERSION) return null
                val widthDp = root.optDouble("w", fallbackWidthDp.toDouble())
                    .toFloat().coerceIn(320f, 2400f)
                val heightDp = root.optDouble("h", fallbackHeightDp.toDouble())
                    .toFloat().coerceIn(240f, 1600f)
                val profile = root.optString("profile", PROFILE_MODERN)
                val layout = defaults(widthDp, heightDp, profile)
                applyJson(root, layout)
                if (version < 8) foldDefendOverrides(layout)
                sanitize(layout)
                layout
            } catch (_: Throwable) {
                null
            }
        }

        private fun applyJson(root: JSONObject, layout: ControlLayout) {
            val array = root.optJSONArray("elements") ?: return
            for (i in 0 until array.length()) {
                val item = array.optJSONObject(i) ?: continue
                // Only what the app still knows about: an element dropped in a
                // later version is ignored rather than resurrected, and a new
                // one keeps its default.
                val element = layout.find(item.optString("id")) ?: continue
                element.xFrac = item.optDouble("x", element.xFrac.toDouble())
                    .toFloat().coerceIn(0.02f, 0.98f)
                element.yFrac = item.optDouble("y", element.yFrac.toDouble())
                    .toFloat().coerceIn(0.02f, 0.98f)
                element.sizeDp = item.optDouble("size", element.sizeDp.toDouble())
                    .toFloat().coerceIn(32f, 900f)
                element.alpha = item.optDouble("alpha", element.alpha.toDouble())
                    .toFloat().coerceIn(0.05f, 1f)
                element.visible = item.optBoolean("visible", element.visible)
                element.toggle = item.optBoolean("toggle", element.toggle)
                // Read before the role: a rebound button's role is the title's
                // binding for the bit it now holds, and a file with no role
                // derives one from this value.
                if (item.has("value")) element.value = item.optInt("value", element.value)
                if (item.has("label")) element.label = item.optString("label", element.label).take(8)
                if (item.has("sub")) element.sub = item.optString("sub", element.sub).take(8)
                if (item.has("sub2")) element.sub2 = item.optString("sub2", element.sub2).take(8)
                element.role = if (item.has("role")) {
                    runCatching {
                        ControlRole.valueOf(item.optString("role", element.role.name))
                    }.getOrDefault(ControlRole.of(element.value))
                } else {
                    // Files from before roles: the title's default binding.
                    ControlRole.of(element.value)
                }
                // Optional: a layout written before phases existed leaves the
                // element where it always was, and the version stays put so
                // nobody loses their pad over one new field.
                element.shownWhen = runCatching {
                    PhaseMask.valueOf(item.optString("when", element.shownWhen.name))
                }.getOrDefault(element.shownWhen)
                // The defending-pad copy, when the player made one.
                item.optJSONObject("defend")?.let { defend ->
                    if (defend.has("x")) element.defendX = defend.optDouble("x", element.xFrac.toDouble())
                        .toFloat().coerceIn(0.02f, 0.98f)
                    if (defend.has("y")) element.defendY = defend.optDouble("y", element.yFrac.toDouble())
                        .toFloat().coerceIn(0.02f, 0.98f)
                    if (defend.has("size")) element.defendSize = defend.optDouble("size", element.sizeDp.toDouble())
                        .toFloat().coerceIn(32f, 900f)
                    if (defend.has("alpha")) element.defendAlpha = defend.optDouble("alpha", element.alpha.toDouble())
                        .toFloat().coerceIn(0.05f, 1f)
                    if (defend.has("visible")) element.defendVisible = defend.optBoolean("visible", element.visible)
                }
                item.optJSONArray("swipes")?.let { swipes ->
                    element.swipeUp = swipes.optInt(0, element.swipeUp)
                    element.swipeDown = swipes.optInt(1, element.swipeDown)
                    element.swipeLeft = swipes.optInt(2, element.swipeLeft)
                    element.swipeRight = swipes.optInt(3, element.swipeRight)
                    element.swipeTap = swipes.optInt(4, element.swipeTap)
                    // A file written before compound bindings held plain bits
                    // only: the defaults' modifiers and double taps must not
                    // attach themselves to the player's bits. A file with
                    // `swipeActions` re-applies them below.
                    element.swipeUpMod = SwipeModifier.NONE
                    element.swipeDownMod = SwipeModifier.NONE
                    element.swipeLeftMod = SwipeModifier.NONE
                    element.swipeRightMod = SwipeModifier.NONE
                    element.swipeUpDouble = false
                    element.swipeDownDouble = false
                    element.swipeLeftDouble = false
                    element.swipeRightDouble = false
                }
                // Optional and additive: a player's saved layout keeps its
                // plain bits, and the compound half is read over the top when
                // this build wrote it.
                item.optJSONArray("trick")?.let { moves ->
                    for (sector in 0 until min(element.trickMoves.size, moves.length())) {
                        element.setTrickMove(sector, moves.optInt(sector, element.trickMoves[sector]))
                    }
                }
                // A custom toggle's target layout. A slot that has since been
                // deleted leaves the id in place: the player can point the
                // toggle somewhere else, and the toggle itself still works.
                if (item.has("profile")) {
                    element.toggleProfile = item.optString("profile", "").trim()
                }
                item.optJSONArray("swipeActions")?.let { actions ->
                    for (index in 0 until min(DIRECTIONS.size, actions.length())) {
                        val entry = actions.optJSONObject(index) ?: continue
                        val modifier = runCatching {
                            SwipeModifier.valueOf(entry.optString("m", SwipeModifier.NONE.name))
                        }.getOrDefault(SwipeModifier.NONE)
                        element.setSwipeAction(
                            DIRECTIONS[index],
                            SwipeAction(entry.optInt("b", element.swipeBit(DIRECTIONS[index])),
                                modifier, entry.optBoolean("d", false)),
                        )
                    }
                }
            }
            // Controls the player removed stay removed; an id the file has no
            // element for was never on the pad to begin with.
            root.optJSONArray("removed")?.let { gone ->
                for (i in 0 until gone.length()) {
                    val id = gone.optString(i, "")
                    if (id.isNotEmpty()) layout.remove(id)
                }
            }
        }

        /**
         * Repairs a layout that could not be read cleanly: the movement stick
         * and the camera region are not optional, and a pad with almost nothing
         * left on it is indistinguishable from a broken file, so it starts over.
         */
        /**
         * The v8 migration: a layout from before the canonical map carried a
         * second geometry for some controls, and moving one copy left the other
         * where it was. Folding takes the defending values the player last set
         * as the one place the control has, then drops the copies, so the two
         * pads can never disagree again.
         */
        private fun foldDefendOverrides(layout: ControlLayout) {
            for (element in layout.elements) {
                if (!element.hasDefendOverride()) continue
                element.defendX?.let { element.xFrac = it }
                element.defendY?.let { element.yFrac = it }
                element.defendSize?.let { element.sizeDp = it }
                element.defendAlpha?.let { element.alpha = it }
                element.defendVisible?.let { element.visible = it }
                element.mergeStance()
            }
        }

        private fun sanitize(layout: ControlLayout) {
            for (id in listOf("stick", "look")) {
                if (layout.find(id) == null) {
                    defaults(layout.widthDp, layout.heightDp, layout.profile).find(id)
                        ?.let { layout.elements.add(it) }
                }
            }
            // Only an empty pad is certainly broken: a player may deliberately
            // hide all but one control, and wiping that would be a bug.
            if (layout.elements.isEmpty()) layout.resetAll()
        }

        // ---- saved layouts (slots) ------------------------------------------

        private fun store(context: Context) = LayoutStore(context.filesDir)

        /** The name a layout file carries, or "" for one that has none. */
        private fun nameIn(text: String): String =
            runCatching { JSONObject(text).optString("name", "") }.getOrDefault("")

        /** The same layout text with its display name set. */
        private fun withName(text: String, name: String): String = try {
            val root = JSONObject(text)
            root.put("name", name.trim().take(40))
            root.toString(2)
        } catch (_: Throwable) {
            text
        }

        /**
         * Every saved pad, numbered slots first.
         *
         * The old named profiles are folded in first: their file names held the
         * name, so it is recovered into the file before the file stops being a
         * profile. A player who saved "Away pad" on the old build finds
         * "Away Pad" in the list rather than `away_pad`.
         */
        fun listSlots(context: Context): List<LayoutSlot> {
            val store = store(context)
            store.migrateLegacy { id, text -> withName(text, LayoutStore.prettyId(id)) }
            return store.list { _, text -> nameIn(text) }
        }

        /**
         * Saves the pad into [id], under [name]; false when it could not be
         * written. The id is the file, and the name lives inside it, so a save
         * never renames the slot a TG1/TG2 mapping points at.
         */
        fun saveSlot(context: Context, layout: ControlLayout, id: String, name: String): Boolean =
            store(context).write(id, withName(layout.toJson(), name.ifBlank { LayoutStore.prettyId(id) }))

        /** Renames a saved pad without touching its controls. */
        fun renameSlot(context: Context, id: String, name: String): Boolean {
            val store = store(context)
            val text = store.read(id) ?: return false
            return store.write(id, withName(text, name.ifBlank { LayoutStore.prettyId(id) }))
        }

        /** Copies one saved pad into another place, under a name of its own. */
        fun duplicateSlot(context: Context, fromId: String, toId: String, name: String): Boolean {
            val store = store(context)
            val text = store.read(fromId) ?: return false
            return store.write(toId, withName(text, name.ifBlank { LayoutStore.prettyId(toId) }))
        }

        /** The first numbered slot with nothing in it, or null when all five are used. */
        fun freeSlot(context: Context): String? = store(context).freeNumberedId()

        fun loadSlot(context: Context, id: String): ControlLayout? {
            val text = store(context).read(id) ?: return null
            return parse(text)
        }

        fun deleteSlot(context: Context, id: String): Boolean = store(context).delete(id)

        /**
         * The old profile entry points, kept so a caller written before slots
         * still works: a profile is a named slot, sanitised the same way.
         */
        fun saveProfile(context: Context, layout: ControlLayout, name: String): Boolean =
            saveSlot(context, layout, LayoutStore.sanitize(name), name)

        fun listProfiles(context: Context): List<String> =
            listSlots(context).filter { !it.numbered }.map { it.id }

        fun loadProfile(context: Context, name: String): ControlLayout? =
            loadSlot(context, LayoutStore.sanitize(name))

        fun deleteProfile(context: Context, name: String): Boolean =
            deleteSlot(context, LayoutStore.sanitize(name))

        fun hasBackup(context: Context): Boolean = File(context.filesDir, BACKUP_NAME).exists()

        /**
         * Where everything starts: a football pad laid out for thumbs, the way
         * PES is played on a phone. The left thumb has only the move stick. The
         * right thumb has everything else in an arc around the four actions:
         *
         *  - Cross / A (pass) at the bottom, where the thumb rests, with
         *    Square / X (lob, cross) beside it: close enough to roll between,
         *    with a gap a thumb does not bridge by accident.
         *  - R1 / RB (sprint, "run") bigger and left of the actions, so the
         *    thumb can hold it and slide onto pass or shoot.
         *  - L1 / LB (change player) above the actions, the next thing the thumb
         *    reaches for when defending.
         *  - The skill stick above the sprint button; L2 / R2 small, under the
         *    HUD and the MENU button at the top; start and select top centre.
         *
         * Positions are in dp from the nearest edge, so the thumb distances are
         * the same on every phone and a wider screen only opens up the middle.
         * Nothing touches (with the touch slack) from a 592 x 336 dp window up.
         *
         * The game's default binding, for reference: Cross pass, Circle shoot,
         * Square lob / cross, Triangle through pass, R1 sprint, L1 change player.
         */
        fun defaults(
            widthDp: Float = 800f,
            heightDp: Float = 360f,
            profile: String = PROFILE_MODERN,
        ): ControlLayout {
            val w = max(widthDp, 320f)
            val h = max(heightDp, 240f)

            /** A control at x, y dp: positive from the left / top, negative from the right / bottom. */
            fun place(
                id: String, kind: ControlKind, value: Int, label: String,
                x: Float, y: Float, size: Float, alpha: Float = 0.45f, visible: Boolean = true,
                shownWhen: PhaseMask = PhaseMask.ALWAYS, sub: String = "", sub2: String = "",
                toggle: Boolean = false, role: ControlRole = ControlRole.of(value),
            ) = ControlElement(
                id, kind, value, label,
                (if (x >= 0f) x else w + x) / w, (if (y >= 0f) y else h + y) / h,
                size, alpha, visible, toggle, shownWhen = shownWhen, sub = sub, sub2 = sub2,
                role = role,
            )

            val centre = w / 2f
            val pad = ControlLayout(
                mutableListOf(
                    // Left thumb: movement.
                    place("stick", ControlKind.STICK, 0, "LS", 95f, -95f, 150f, 0.40f),

                    // Right thumb: the four actions as the pad's diamond. `sub`
                    // is what the control does on the ball; the fake shot and
                    // the editor read it, so the words matter.
                    // The second line is what the same button does without the
                    // ball. The title already decides that from possession; the
                    // pad only has to say so (`sub2` is drawn in the defending
                    // stance).
                    // 64 dp across: the diamond's diagonals now clear by 7.5 dp
                    // with both touch slacks counted, instead of the old 68 dp
                    // pad's 2 dp. Pass sits a little higher so the gap to B and
                    // X is the same in both directions.
                    place("a", ControlKind.BUTTON, NativeBridge.A, "A", -110f, -54f, 64f, sub = "PASS", sub2 = "PRESS"),
                    place("b", ControlKind.BUTTON, NativeBridge.B, "B", -48f, -110f, 64f, sub = "CROSS", sub2 = "TEAM"),
                    place("x", ControlKind.BUTTON, NativeBridge.X, "X", -172f, -110f, 64f, sub = "SHOOT", sub2 = "SLIDE"),
                    place("y", ControlKind.BUTTON, NativeBridge.Y, "Y", -110f, -172f, 64f, sub = "THROUGH", sub2 = "GK"),

                    // The shoulders where a pad has them: L2 / L1 along the top left,
                    // R1 / R2 along the top right, triggers outermost. LB is an
                    // attacking control now: without the ball it is the player
                    // switch, and the switch below is the one within reach.
                    place("lt", ControlKind.TRIGGER, 0, "LT", 44f, 76f, 56f),
                    place("lb", ControlKind.BUTTON, NativeBridge.LB, "LB", 110f, 76f, 56f,
                        shownWhen = PhaseMask.ATTACK),

                    // The defending player switch, one thumb slide left of pass
                    // and clear of it and of the slide tackle: the top-left LB is
                    // across the screen from the right thumb. Same LB bit, so no
                    // binding changes; shown only on the defending pad.
                    place("switch", ControlKind.BUTTON, NativeBridge.LB, "LB", -212f, -45f, 56f,
                        shownWhen = PhaseMask.DEFEND, sub2 = "SWITCH"),

                    // The stance chip: one tap flips the pad, the labels follow,
                    // and nothing is held. Above the diamond, clear of the
                    // shoot-to-pass drag, of Y, of RB and of the skill wheel.
                    place("stance", ControlKind.STANCE, 0, "ATK", -150f, -232f, 40f, 0.55f,
                        shownWhen = PhaseMask.MATCH),
                    place("rb", ControlKind.BUTTON, NativeBridge.RB, "RB", -110f, 76f, 56f),
                    place("rt", ControlKind.TRIGGER, 1, "RT", -44f, 76f, 56f),

                    // The skill stick, left of the actions.
                    place("right_stick", ControlKind.STICK, 1, "RS", -250f, -170f, 100f, 0.40f),

                    // The skill palette: one button per move, played on the skill
                    // stick by the runtime. Hidden until the player turns it on,
                    // as the swipe pad is, and shown only over the pitch, where a
                    // move means anything. `value` is an index into
                    // runtime/skill_moves.h, so the order here is the table's and
                    // must not be shuffled.
                    // 60 dp apart so no two of their touch areas meet: a button
                    // 48 dp across is a 24 dp circle plus 6 dp of slack, so
                    // anything closer is a thumb that means one and gets the
                    // other. The column stops 40 dp above the bottom edge, clear
                    // of the gesture bar.
                    place("skill_double_touch", ControlKind.SKILL, 0, "D.TOUCH", -330f, -280f, 48f, 0.40f, visible = false, shownWhen = PhaseMask.MATCH),
                    place("skill_roulette", ControlKind.SKILL, 1, "ROULETTE", -330f, -220f, 48f, 0.40f, visible = false, shownWhen = PhaseMask.MATCH),
                    place("skill_rainbow", ControlKind.SKILL, 2, "RAINBOW", -330f, -160f, 48f, 0.40f, visible = false, shownWhen = PhaseMask.MATCH),
                    place("skill_drag_back", ControlKind.SKILL, 3, "DRAG BK", -330f, -100f, 48f, 0.40f, visible = false, shownWhen = PhaseMask.MATCH),
                    place("skill_body_feint", ControlKind.SKILL, 4, "BODY FNT", -330f, -40f, 48f, 0.40f, visible = false, shownWhen = PhaseMask.MATCH),

                    // The skill wheel, above the action diamond and clear of
                    // both the shoot-to-pass drag and the skill stick's touch
                    // area: hold it and a ring of eight moves opens under the
                    // thumb; slide and lift to play one. Nothing happens on a
                    // quick tap.
                    place("trick", ControlKind.TRICK, 0, "SKILL", -240f, -268f, 64f, 0.45f, shownWhen = PhaseMask.MATCH),

                    // Four-way swipes, hidden until the player binds a direction:
                    // an unbound swipe stays out of the way rather than
                    // duplicating an action button. The four action buttons take
                    // their own swipes as well; this patch is for a gesture the
                    // player wants off the buttons altogether.
                    place("swipe", ControlKind.SWIPE, 0, "SWIPE", -215f, -52f, 96f, 0.40f, visible = false),

                    // The camera: a region, not a control, so it is invisible in
                    // play and only outlined in the editor. It lies under
                    // everything (the overlay looks for it last), so it turns the
                    // view on an empty stretch of screen without ever taking a
                    // touch from a button or the stick. It sits in the free band
                    // between the left stick and the skill stick, clear of every
                    // control that is on screen by default; the editor can move
                    // it, resize it, or turn it off.
                    place("look", ControlKind.LOOK, 0, "LOOK", 330f, 200f, 300f, 0f,
                        shownWhen = PhaseMask.MATCH),

                    // The d-pad: tactics and mentality in a match, navigation in
                    // the menus. It sits above the left thumb and clear of the
                    // movement stick's socket, the shoulders and the look band's
                    // controls; over the pitch it fades out after four idle
                    // seconds (DpadAutoHide), so it does not sit on the pitch
                    // for a whole match. In the menus it never fades.
                    place("dpad", ControlKind.DPAD, 0, "D-PAD", 200f, -240f, 110f, 0.40f,
                        shownWhen = PhaseMask.ALWAYS),

                    // Stick clicks: kept for the editor, hidden until wanted. R3
                    // sits out of the skill palette's column, which it used to
                    // share: two controls on one spot is a thumb that means one
                    // and gets the other.
                    place("l3", ControlKind.BUTTON, NativeBridge.L3, "L3", 200f, -40f, 52f, visible = false),
                    place("r3", ControlKind.BUTTON, NativeBridge.R3, "R3", -430f, -45f, 52f, visible = false),

                    // Menus have to be found at a glance in a football game.
                    place("start", ControlKind.BUTTON, NativeBridge.START, "START", centre + 60f, 37f, 54f, 0.75f),
                    place("back", ControlKind.BUTTON, NativeBridge.BACK, "SELECT", centre - 40f, 37f, 54f, 0.75f),

                    // Ten spare buttons, hidden until the player binds one. Each
                    // takes a centre tap plus four swipes (its `swipeTap` and
                    // `swipeUp/Down/Left/Right`), so a command the title has but
                    // the default pad does not is one edit away. Hidden means
                    // hidden: an unbound spare on screen would be a dead circle
                    // under the thumb.
                    place("c1", ControlKind.CUSTOM, 0, "C1", 60f, 60f, 48f, 0.40f, visible = false),
                    place("c2", ControlKind.CUSTOM, 0, "C2", 116f, 60f, 48f, 0.40f, visible = false),
                    place("c3", ControlKind.CUSTOM, 0, "C3", 172f, 60f, 48f, 0.40f, visible = false),
                    place("c4", ControlKind.CUSTOM, 0, "C4", 228f, 60f, 48f, 0.40f, visible = false),
                    place("c5", ControlKind.CUSTOM, 0, "C5", 284f, 60f, 48f, 0.40f, visible = false),
                    place("c6", ControlKind.CUSTOM, 0, "C6", 340f, 60f, 48f, 0.40f, visible = false),
                    place("c7", ControlKind.CUSTOM, 0, "C7", 396f, 60f, 48f, 0.40f, visible = false),
                    place("c8", ControlKind.CUSTOM, 0, "C8", 452f, 60f, 48f, 0.40f, visible = false),
                    place("c9", ControlKind.CUSTOM, 0, "C9", 508f, 60f, 48f, 0.40f, visible = false),
                    place("c10", ControlKind.CUSTOM, 0, "C10", 564f, 60f, 48f, 0.40f, visible = false),

                    // The jockey button: the left trigger, latched. Holding LT
                    // with a thumb is how a defender contains a runner, and a
                    // thumb that is holding it cannot tackle; tapping it on
                    // leaves both thumbs for the tackle and the player switch.
                    // A trigger, so it draws and reads exactly like LT does.
                    place("jockey", ControlKind.TRIGGER, 0, "JCK", 176f, 76f, 56f, 0.45f,
                        visible = false, toggle = true, role = ControlRole.JOCKEY),

                    // Two custom toggles, hidden and unbound: a latch the player
                    // binds to whatever the pad is missing, one command each.
                    place("tg1", ControlKind.LATCH, 0, "TG1", 60f, 150f, 52f, 0.40f, visible = false),
                    place("tg2", ControlKind.LATCH, 0, "TG2", 120f, 150f, 52f, 0.40f, visible = false),

                    // Two tactical five-way buttons, hidden until the player
                    // wants them. sw1 carries the quick tactics and the
                    // attack/defence level, sw2 the advanced instructions under
                    // LT; both take a custom command on the centre tap.
                    // They are parked on the empty stretch the camera band
                    // covers -- the one place a 88 dp control's four corridors
                    // reach nothing -- and move wherever the player wants them
                    // once they are on.
                    place("sw1", ControlKind.TACTICAL, 0, "SW1", 230f, 300f, 88f, 0.40f, visible = false),
                    place("sw2", ControlKind.TACTICAL, 0, "SW2", 340f, 300f, 88f, 0.40f, visible = false),
                ),
                widthDp, heightDp, profile,
            )
            if (profile == PROFILE_CLASSIC) applyClassicSpacing(pad, w, h)
            applyDefaultSwipes(pad)
            applyDefaultTrick(pad)
            applyDefaultTactics(pad)
            return pad
        }

        /**
         * The pad as it was before the retune: 68 dp actions, the old diamond
         * and the old switch spot. Offered as a profile so an update does not
         * move a thumb that had learned where things were.
         */
        private fun applyClassicSpacing(pad: ControlLayout, w: Float, h: Float) {
            fun place(id: String, x: Float, y: Float, size: Float) {
                val element = pad.find(id) ?: return
                element.xFrac = (if (x >= 0f) x else w + x) / w
                element.yFrac = (if (y >= 0f) y else h + y) / h
                element.sizeDp = size
            }
            place("a", -110f, -52f, 68f)
            place("b", -52f, -110f, 68f)
            place("x", -168f, -110f, 68f)
            place("y", -110f, -168f, 68f)
            place("switch", -205f, -45f, 56f)
        }

        /**
         * The sixteen starting swipes.
         *
         * PES 2017 plays each command as a base button plus a held modifier:
         * L1 (LB) is the lobbed or early variant, L2 (LT) the manual one, R1
         * (RB) the driven one and R2 (RT) the controlled or finesse one. The
         * cross's low ball is the title's own double tap. Shoot's right swipe
         * (RB + shoot) points at pass, which is also the fake-shot drag; the
         * fake shot wins that gesture (see SwipePriority), so the swipe fires
         * only when the drag is not towards pass.
         */
        private fun applyDefaultSwipes(pad: ControlLayout) {
            fun bind(id: String, up: SwipeAction, down: SwipeAction, left: SwipeAction, right: SwipeAction) {
                val element = pad.find(id) ?: return
                element.setSwipeAction(Swipe.UP, up)
                element.setSwipeAction(Swipe.DOWN, down)
                element.setSwipeAction(Swipe.LEFT, left)
                element.setSwipeAction(Swipe.RIGHT, right)
            }

            // Pass: one-two, manual, driven ground, controlled.
            bind(
                "a",
                SwipeAction.held(SwipeModifier.LB, NativeBridge.A),
                SwipeAction.held(SwipeModifier.LT, NativeBridge.A),
                SwipeAction.held(SwipeModifier.RB, NativeBridge.A),
                SwipeAction.held(SwipeModifier.RT, NativeBridge.A),
            )
            // Through pass: chipped, manual, lofted, controlled.
            bind(
                "y",
                SwipeAction.held(SwipeModifier.LB, NativeBridge.Y),
                SwipeAction.held(SwipeModifier.LT, NativeBridge.Y),
                SwipeAction.held(SwipeModifier.RB, NativeBridge.Y),
                SwipeAction.held(SwipeModifier.RT, NativeBridge.Y),
            )
            // Cross: early/deep, low (double tap), manual, controlled curl.
            bind(
                "b",
                SwipeAction.held(SwipeModifier.LB, NativeBridge.B),
                SwipeAction.double(NativeBridge.B),
                SwipeAction.held(SwipeModifier.LT, NativeBridge.B),
                SwipeAction.held(SwipeModifier.RT, NativeBridge.B),
            )
            // Shoot: chip, finesse, manual, driven.
            bind(
                "x",
                SwipeAction.held(SwipeModifier.LB, NativeBridge.X),
                SwipeAction.held(SwipeModifier.RT, NativeBridge.X),
                SwipeAction.held(SwipeModifier.LT, NativeBridge.X),
                SwipeAction.held(SwipeModifier.RB, NativeBridge.X),
            )
        }

        /**
         * The two tactical five-way buttons' starting commands.
         *
         * The title's tactics live on the d-pad, and its advanced instructions
         * are the same arms under LT (docs/touch-controls.md). So sw1 is the
         * d-pad's tactical half -- up and down cycle the preset tactics, and the
         * left and right arms are the double taps that lower and raise the
         * attack/defence level -- and sw2 is the four instructions LT + d-pad
         * arms. The centre tap of each is left unbound: it is the custom slot,
         * and a command nobody asked for is worse than one more binding.
         *
         * Quick tactics has no key of its own in the title's pad, so it rides
         * the nearest thing that does: LB + d-pad down, the modifier family the
         * rest of the pad already uses for the same arm. It is one tap in the
         * editor to move it.
         */
        private fun applyDefaultTactics(pad: ControlLayout) {
            fun bind(id: String, up: SwipeAction, down: SwipeAction, left: SwipeAction, right: SwipeAction) {
                val element = pad.find(id) ?: return
                element.setSwipeAction(Swipe.UP, up)
                element.setSwipeAction(Swipe.DOWN, down)
                element.setSwipeAction(Swipe.LEFT, left)
                element.setSwipeAction(Swipe.RIGHT, right)
            }
            bind(
                "sw1",
                SwipeAction.of(NativeBridge.UP),
                SwipeAction.held(SwipeModifier.LB, NativeBridge.DOWN),
                SwipeAction.double(NativeBridge.DOWN),
                SwipeAction.double(NativeBridge.UP),
            )
            bind(
                "sw2",
                SwipeAction.held(SwipeModifier.LT, NativeBridge.UP),
                SwipeAction.held(SwipeModifier.LT, NativeBridge.DOWN),
                SwipeAction.held(SwipeModifier.LT, NativeBridge.LEFT),
                SwipeAction.held(SwipeModifier.LT, NativeBridge.RIGHT),
            )
        }

        /**
         * The wheel's eight starting moves, one per sector, in the wheel's own
         * order: right, up-right, up, up-left, left, down-left, down,
         * down-right. Every move in the table is reachable from the ring out
         * of the box, and no two sectors share one, so the default wheel can
         * be used without opening the editor at all.
         */
        private fun applyDefaultTrick(pad: ControlLayout) {
            val element = pad.find("trick") ?: return
            // ronaldo chop, elastico, body feint, rainbow, drag back, step
            // over, double touch, roulette -- indices into runtime/skill_moves.h.
            val sectors = intArrayOf(6, 7, 4, 2, 3, 5, 0, 1)
            for (sector in sectors.indices) element.setTrickMove(sector, sectors[sector])
        }

        /** The name printed on a control: its Xbox name, or the PlayStation one. */
        fun labelFor(element: ControlElement, playStation: Boolean): String {
            if (!playStation) return element.label
            return when (element.id) {
                "a" -> "CROSS"
                "b" -> "CIRCLE"
                "x" -> "SQUARE"
                "y" -> "TRIANGLE"
                "lb" -> "L1"
                "rb" -> "R1"
                "lt" -> "L2"
                "rt" -> "R2"
                "stick" -> "L"
                "right_stick" -> "R"
                "trick" -> "SKILL"
                "jockey" -> "JCK"
                "tg1" -> "TG1"
                "tg2" -> "TG2"
                "sw1" -> "SW1"
                "sw2" -> "SW2"
                "start" -> "START"
                "back" -> "SELECT"
                else -> element.label
            }
        }
    }
}
