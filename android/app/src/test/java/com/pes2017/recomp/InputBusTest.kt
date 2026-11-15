package com.pes2017.recomp

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The two pieces of plumbing the optimization pass added: source-counted holds and
 * the FIFO macro queue. Both are the kind of bug that only shows up on a phone in
 * the middle of a match, so they are checked as arithmetic instead.
 */
class InputBusTest {

    private fun pad() = ControlLayout.defaults(800f, 360f)

    // ---- source-counted holds ----------------------------------------------

    @Test
    fun `sw2's left trigger finishing does not lift the jockey's latched lt`() {
        val claims = TouchClaims()
        val mixer = HoldMixer()
        val jockey = pad().find("jockey")!!
        val sw2 = pad().find("sw2")!!

        // The player latches the jockey: LT is down with no finger on it.
        claims.press(1, jockey)
        claims.release(1)
        assertTrue(claims.isToggled("jockey"))
        mixer.set(
            HoldSource.JOCKEY_TOGGLE,
            claims.latchedHold("jockey").buttons,
            claims.latchedHold("jockey").leftTrigger,
            claims.latchedHold("jockey").rightTrigger,
        )
        assertTrue("the jockey alone holds LT", mixer.leftTrigger)

        // Now SW2 sends its first instruction: LT is wanted by the macro too.
        val steps = SwipeSequence.steps(sw2.swipeAction(Swipe.UP))
        assertTrue("the instruction is a compound one", steps.isNotEmpty())
        mixer.set(HoldSource.MACRO_SEQUENCE, steps[1].buttons, steps[1].leftTrigger, steps[1].rightTrigger)
        assertTrue(mixer.leftTrigger)
        assertEquals("LT and the arm", NativeBridge.UP, steps[1].buttons and NativeBridge.UP)

        // The sequence ends the way every sequence does: with a clear step.
        val clear = steps.last()
        assertEquals("the last step holds nothing", 0, clear.buttons)
        assertFalse(clear.leftTrigger)
        mixer.set(HoldSource.MACRO_SEQUENCE, clear.buttons, clear.leftTrigger, clear.rightTrigger)

        assertTrue("LT is still down: the jockey never let go", mixer.leftTrigger)
        assertEquals("and nothing else is", 0, mixer.buttons)

        // Only the jockey's own second tap takes it up.
        mixer.release(HoldSource.JOCKEY_TOGGLE)
        assertFalse("now nothing wants it", mixer.leftTrigger)
    }

    @Test
    fun `two sources on one bit both have to let go`() {
        val mixer = HoldMixer()
        mixer.set(HoldSource.PHYSICAL_TOUCH, NativeBridge.A or NativeBridge.LB)
        mixer.set(HoldSource.TG1_TOGGLE, NativeBridge.A)
        assertEquals(NativeBridge.A or NativeBridge.LB, mixer.buttons)
        mixer.release(HoldSource.PHYSICAL_TOUCH)
        assertEquals("tg1 still holds A", NativeBridge.A, mixer.buttons)
        mixer.release(HoldSource.TG1_TOGGLE)
        assertEquals(0, mixer.buttons)
    }

    @Test
    fun `a trigger held by two sources survives either one letting go`() {
        val mixer = HoldMixer()
        mixer.set(HoldSource.MACRO_SEQUENCE, 0, leftTrigger = true)
        mixer.set(HoldSource.JOCKEY_TOGGLE, 0, leftTrigger = true)
        mixer.release(HoldSource.MACRO_SEQUENCE)
        assertTrue(mixer.leftTrigger)
        assertFalse(mixer.rightTrigger)
        mixer.release(HoldSource.JOCKEY_TOGGLE)
        assertFalse(mixer.leftTrigger)
    }

    @Test
    fun `clearing lets go of everything and holds reports per source`() {
        val mixer = HoldMixer()
        mixer.set(HoldSource.TG2_TOGGLE, NativeBridge.START, rightTrigger = true)
        assertTrue(mixer.holds(HoldSource.TG2_TOGGLE))
        assertEquals(NativeBridge.START, mixer.maskFor(HoldSource.TG2_TOGGLE))
        assertFalse(mixer.holds(HoldSource.TG1_TOGGLE))
        mixer.clear()
        assertEquals(0, mixer.buttons)
        assertFalse(mixer.rightTrigger)
        assertFalse(mixer.holds(HoldSource.TG2_TOGGLE))
    }

    @Test
    fun `each latch is given its own source`() {
        assertEquals(HoldSource.JOCKEY_TOGGLE, HoldMixer.sourceFor("jockey"))
        assertEquals(HoldSource.TG1_TOGGLE, HoldMixer.sourceFor("tg1"))
        assertEquals(HoldSource.TG2_TOGGLE, HoldMixer.sourceFor("tg2"))
        assertEquals(HoldSource.PHYSICAL_TOUCH, HoldMixer.sourceFor("a"))
        assertEquals(HoldSource.PHYSICAL_TOUCH, HoldMixer.sourceFor("c3"))
    }

    @Test
    fun `a latched button is not also counted as a thumb`() {
        val claims = TouchClaims()
        val a = pad().find("a")!!
        a.toggle = true
        claims.press(1, a)
        claims.release(1)
        assertTrue("the pad draws it as on", claims.isOn("a"))
        assertFalse("but no finger is on it", claims.isHeld("a"))
        assertTrue(claims.latchIds().contains("a"))
        assertEquals(LatchHold(buttons = NativeBridge.A), claims.latchedHold("a"))
    }

    // ---- the FIFO queue ----------------------------------------------------

    @Test
    fun `one sequence is queued with poll-safe dwells`() {
        val queue = MacroSequencer()
        val first = queue.enqueue(SwipeAction.held(SwipeModifier.LT, NativeBridge.UP), 1_000L)
        assertEquals(1_000L, first)
        assertEquals(
            "one step for the trigger, one for the arm, one to let go",
            3, queue.pending,
        )
        assertTrue(queue.busy)

        // Nothing is due before its time.
        assertTrue(queue.takeDue(999L).isEmpty())
        val atStart = queue.takeDue(1_000L)
        assertEquals(1, atStart.size)
        assertTrue(atStart[0].leftTrigger)
    }

    @Test
    fun `a second sequence starts after the first ends plus the gap`() {
        val queue = MacroSequencer()
        val now = 500L
        queue.enqueue(SwipeAction.held(SwipeModifier.LT, NativeBridge.UP), now)
        val steps = SwipeSequence.steps(SwipeAction.held(SwipeModifier.LT, NativeBridge.UP))
        val end = now + steps.last().delayMs

        // The second flick lands immediately, while the first is still playing.
        val second = queue.enqueue(SwipeAction.double(NativeBridge.DOWN), now)!!
        assertEquals(
            "the second waits for the first, plus the gap",
            end + MacroSequencer.GAP_MS,
            second,
        )
        // In order: the first sequence's four steps, then the second's four.
        val all = queue.takeDue(10_000L)
        assertEquals(3 + 4, all.size)
        assertTrue("first step belongs to SW2's instruction", all[0].leftTrigger)
        assertFalse(queue.busy)
    }

    @Test
    fun `the queue keeps the order the player flicked in`() {
        val queue = MacroSequencer()
        assertNull("an empty queue has nothing due", queue.nextDueMs())

        val a = queue.enqueue(SwipeAction.double(NativeBridge.A), 100L)!!
        val b = queue.enqueue(SwipeAction.double(NativeBridge.B), 100L)!!
        assertTrue("the first flick is due first", a < b)
        val due = queue.takeDue(10_000L)
        assertEquals(8, due.size)
        // The first sequence's second press is A, and B only appears after it.
        assertEquals(NativeBridge.A, due[2].buttons)
        assertEquals(NativeBridge.B, due[6].buttons)
    }

    @Test
    fun `clearing the queue drops what was playing`() {
        val queue = MacroSequencer()
        queue.enqueue(SwipeAction.held(SwipeModifier.LB, NativeBridge.DOWN), 0L)
        assertTrue(queue.busy)
        queue.clear()
        assertFalse(queue.busy)
        assertEquals(0, queue.pending)
        assertTrue(queue.takeDue(Long.MAX_VALUE).isEmpty())
        // A cleared queue starts from "now" again rather than from the old cursor.
        assertEquals(42L, queue.enqueue(SwipeAction.double(NativeBridge.A), 42L))
    }

    @Test
    fun `the steps of a sequence never overlap each other`() {
        val steps = SwipeSequence.steps(SwipeAction.held(SwipeModifier.LT, NativeBridge.UP))
        assertEquals(3, steps.size)
        for (index in 1 until steps.size) {
            assertTrue(
                "step $index starts after step ${index - 1}",
                steps[index].delayMs > steps[index - 1].delayMs,
            )
        }
        assertEquals("one dwell for every beat", SwipeSequence.HOLD_MS, SwipeSequence.MODIFIER_LEAD_MS)
    }

    // ---- cardinal bias on the five-way buttons ------------------------------

    private fun flick(x: Float, y: Float, bias: Float): Swipe {
        val classifier = SwipeClassifier(minTravelPx = 18f)
        classifier.begin(1, 0f, 0f, 0L, bias)
        classifier.move(1, x, y)
        return classifier.end(1, 10L)
    }

    @Test
    fun `a leaning tactical flick takes the arm it leans towards`() {
        // About 42 degrees off vertical: an action button sends nothing at that
        // angle, which is where the "I swiped up and got nothing" reports came
        // from. Screen y grows downward, so up is negative.
        assertEquals(
            "the pad's usual rule refuses it",
            Swipe.NONE,
            flick(27f, -30f, SwipeClassifier.DEFAULT_DOMINANCE),
        )
        assertEquals(
            "a tactical button reads the arm it leans towards",
            Swipe.UP,
            flick(27f, -30f, SwipeClassifier.CARDINAL_DOMINANCE),
        )
        assertEquals(Swipe.RIGHT, flick(30f, -27f, SwipeClassifier.CARDINAL_DOMINANCE))
        assertEquals(Swipe.DOWN, flick(-27f, 30f, SwipeClassifier.CARDINAL_DOMINANCE))
        assertEquals(Swipe.LEFT, flick(-30f, 27f, SwipeClassifier.CARDINAL_DOMINANCE))
    }

    @Test
    fun `the tactical bias only widens the arms`() {
        // Straight flicks are unaffected, and a flick too short to mean anything
        // is still nothing.
        assertEquals(Swipe.UP, flick(0f, -20f, SwipeClassifier.CARDINAL_DOMINANCE))
        // The same straight flick is the same direction under the pad's rule: the
        // bias changes the angle a direction is accepted at, not the distance a
        // thumb has to travel.
        assertEquals(Swipe.UP, flick(0f, -20f, SwipeClassifier.DEFAULT_DOMINANCE))
        assertEquals(Swipe.NONE, flick(0f, -4f, SwipeClassifier.CARDINAL_DOMINANCE))
        assertEquals(Swipe.NONE, flick(0f, -4f, SwipeClassifier.DEFAULT_DOMINANCE))
    }

    @Test
    fun `peek agrees with end under the tactical bias`() {
        val classifier = SwipeClassifier(minTravelPx = 18f)
        classifier.begin(1, 0f, 0f, 0L, SwipeClassifier.CARDINAL_DOMINANCE)
        classifier.move(1, 27f, -30f)
        assertEquals(Swipe.UP, classifier.peek(1))
        assertEquals(Swipe.UP, classifier.end(1, 10L))
    }

    // ---- atomic slot writes -------------------------------------------------

    @Test
    fun `a slot is replaced whole and leaves no part file behind`() {
        val dir = kotlin.io.path.createTempDirectory("slots").toFile()
        try {
            val store = LayoutStore(dir)
            assertTrue(store.write("3", "{\"name\":\"first\"}"))
            assertTrue(store.write("3", "{\"name\":\"second\"}"))
            assertEquals("{\"name\":\"second\"}", store.read("3"))
            val leftovers = dir.listFiles()!!.filter { it.name.endsWith(".part") }
            assertTrue("no temporary survived: $leftovers", leftovers.isEmpty())
        } finally {
            dir.deleteRecursively()
        }
    }

    @Test
    fun `the old slot survives a write that cannot be completed`() {
        val dir = kotlin.io.path.createTempDirectory("slots").toFile()
        try {
            val store = LayoutStore(dir)
            assertTrue(store.write("1", "{\"good\":true}"))
            // A directory where the temporary has to go: the write cannot finish,
            // and the file the player already had must still be there.
            assertEquals("controls_slot_1.json.part", LayoutStore.PREFIX + "1" + LayoutStore.SUFFIX + LayoutStore.PART_SUFFIX)
            val blocker = java.io.File(dir, LayoutStore.PREFIX + "1" + LayoutStore.SUFFIX + LayoutStore.PART_SUFFIX)
            assertTrue(blocker.mkdir())
            assertFalse("the write reports failure", store.write("1", "{\"bad\":true}"))
            assertEquals("{\"good\":true}", store.read("1"))
        } finally {
            dir.deleteRecursively()
        }
    }

    @Test
    fun `a slot written by the store reads back into a pad`() {
        val dir = kotlin.io.path.createTempDirectory("slots").toFile()
        try {
            val store = LayoutStore(dir)
            val pad = pad()
            pad.find("jockey")!!.visible = true
            assertTrue(store.write("2", pad.toJson()))
            val text = store.read("2")
            assertNotNull(text)
            val restored = ControlLayout.parse(text!!)
            assertNotNull(restored)
            assertTrue(restored!!.find("jockey")!!.visible)
            assertTrue(restored.find("jockey")!!.toggle)
        } finally {
            dir.deleteRecursively()
        }
    }
}
