package com.pes2017.recomp

/**
 * Who is holding what on the on-screen pad.
 *
 * A finger is a pointer id, and a control can be under more than one finger:
 * two thumbs resting on the same button is not rare, and neither is a thumb
 * landing on a button the other thumb is already holding. So what a control is
 * doing is counted here rather than assumed, and a control is only let go when
 * the last finger on it lifts. Toggles are latched instead: a tap flips them
 * and no finger has to stay down.
 *
 * A press is also counted separately from what is held: the guest samples the
 * pad once a frame, so a tap that begins and ends between two samples would
 * otherwise not exist. Every new press leaves its bit for [takeNewPresses],
 * which the overlay sends as a pulse on top of the held state.
 *
 * Kept out of [TouchOverlayView] and free of Android types, so the state
 * machine can be read and tested on its own (TouchClaimsTest) rather than only
 * by playing with two thumbs.
 */
internal class TouchClaims {

    private class Claim(val element: ControlElement, val holds: Boolean)

    /** What a lift let go of; [lastClaim] is false while another finger still has it. */
    class Released(val element: ControlElement, val lastClaim: Boolean)

    private val byPointer = HashMap<Int, Claim>()
    private val holds = HashMap<String, Int>()        // momentary buttons: element id -> fingers
    private val stickOwners = HashMap<String, Int>()  // sticks: element id -> fingers
    // A latch is kept with the command it holds, not just its name: a toggle
    // bound to LB + down holds both, one bound to the left trigger holds that,
    // and pushState has to know which without asking the layout again.
    private val latched = LinkedHashMap<String, LatchHold>()
    private var newPresses = 0                       // button bits that went down since the last take

    fun elementFor(pointerId: Int): ControlElement? = byPointer[pointerId]?.element

    /** Whether the guest should see this control as down. */
    fun isOn(id: String): Boolean = (holds[id] ?: 0) > 0 || latched.containsKey(id)

    /**
     * Whether a finger is on it right now, ignoring latches. The state the pad
     * pushes is built from this plus one source per latch, so a latched button is
     * never also counted as a thumb: the two are different sources.
     */
    fun isHeld(id: String): Boolean = (holds[id] ?: 0) > 0

    /**
     * The ids with an engaged latch, as a live view of the keys: no copy is made,
     * so this can be read on every frame. Pair it with [latchedHold].
     */
    fun latchIds(): Collection<String> = latched.keys

    fun isToggled(id: String): Boolean = latched.containsKey(id)

    /** What this control is holding with its latch, or [LatchHold.NONE]. */
    fun latchedHold(id: String): LatchHold = latched[id] ?: LatchHold.NONE

    fun holdCount(id: String): Int = holds[id] ?: 0

    fun stickHoldCount(id: String): Int = stickOwners[id] ?: 0

    /**
     * A finger went down on [element]. Returns whether the guest's buttons or
     * triggers changed; a stick's deflection is the caller's to compute.
     */
    fun press(pointerId: Int, element: ControlElement, pulse: Boolean = true): Boolean {
        // A pointer cannot press twice without lifting; drop whatever it had,
        // so a lost ACTION_UP cannot leave a count that never comes back down.
        release(pointerId)
        // A stick deflects instead of holding a bit; a skill palette button
        // fires its move on the press and holds nothing; and the wheel holds
        // nothing at all until the thumb lifts. None of their `value`s is a
        // button mask, so none may reach newPresses.
        if (element.kind == ControlKind.STICK || element.kind == ControlKind.SKILL ||
            element.kind == ControlKind.TRICK ||
            element.kind == ControlKind.STICK_MODE || element.kind == ControlKind.TACTICAL ||
            element.kind == ControlKind.MENTALITY
        ) {
            byPointer[pointerId] = Claim(element, holds = false)
            if (element.kind == ControlKind.STICK) {
                stickOwners[element.id] = (stickOwners[element.id] ?: 0) + 1
            }
            return false
        }
        val latching = element.latches()
        byPointer[pointerId] = Claim(element, holds = !latching)
        if (latching) {
            // One tap on, the next off. The hold is read from the control as it
            // is now, so a toggle the editor has just rebound engages with the
            // new command rather than the one it was latched with last time.
            if (latched.remove(element.id) == null) latched[element.id] = element.latchHold()
        } else {
            holds[element.id] = (holds[element.id] ?: 0) + 1
            // A press on a control another finger already holds is still a
            // press: the holds merge, the tap must not. Triggers are left out
            // -- they are held by their release grace instead, and their value
            // is not a button bit.
            // A second press of the same control from another thumb merges
            // into the hold; [pulse] is false only when the caller has decided
            // this press was a bounce of the one before it.
            if (element.kind == ControlKind.BUTTON && pulse) {
                newPresses = newPresses or element.value
            }
        }
        return true
    }

    /**
     * The button bits that have gone down since the last call, and forgot them.
     * The overlay sends these as a pulse beside the held state, so a tap shorter
     * than the title's poll interval is still seen.
     */
    fun takeNewPresses(): Int {
        val pressed = newPresses
        newPresses = 0
        return pressed
    }

    /** A finger lifted. Null when no finger was on that pointer id. */
    fun release(pointerId: Int): Released? {
        val claim = byPointer.remove(pointerId) ?: return null
        val id = claim.element.id
        val last = when {
            claim.element.kind == ControlKind.STICK -> {
                val left = (stickOwners[id] ?: 1) - 1
                if (left <= 0) stickOwners.remove(id) else stickOwners[id] = left
                left <= 0
            }
            claim.holds -> {
                val left = (holds[id] ?: 1) - 1
                if (left <= 0) holds.remove(id) else holds[id] = left
                left <= 0
            }
            else -> true   // a toggle: nothing was counted, so nothing is left over
        }
        return Released(claim.element, last)
    }

    /** Every finger up. Toggles stay: a latched run is still latched after a pause. */
    fun clearMomentary() {
        byPointer.clear()
        holds.clear()
        stickOwners.clear()
        // A press that was on its way to the guest is dropped with the holds:
        // it belonged to a gesture the pad has just let go of.
        newPresses = 0
    }

    /** Every latch let go, leaving the fingers where they are. */
    fun clearLatches() {
        latched.clear()
    }

    /** Every finger up and every toggle let go: a pause, a reset or the editor. */
    fun clearAll() {
        clearMomentary()
        latched.clear()
    }
}
