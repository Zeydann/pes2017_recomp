package com.pes2017.recomp

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** The latches and the two tactical five-way buttons, without a phone. */
class TogglePadTest {

    private fun pad() = ControlLayout.defaults(800f, 360f)

    @Test
    fun `the jockey button is a hidden left trigger that latches`() {
        val jockey = pad().find("jockey")
        assertNotNull("the jockey button ships with the pad", jockey)
        jockey!!
        assertEquals(ControlKind.TRIGGER, jockey.kind)
        assertEquals("it is the left trigger", 0, jockey.value)
        assertTrue("tapping it latches", jockey.toggle)
        assertEquals(ControlRole.JOCKEY, jockey.role)
        assertFalse("hidden until wanted", jockey.visible)
        val hold = jockey.latchHold()
        assertTrue("it holds LT while engaged", hold.leftTrigger)
        assertFalse(hold.rightTrigger)
        assertEquals(0, hold.buttons)
    }

    @Test
    fun `the two custom toggles ship hidden, unbound, and always latching`() {
        val pad = pad()
        for (id in listOf("tg1", "tg2")) {
            val toggle = pad.find(id)
            assertNotNull("$id ships with the pad", toggle)
            assertEquals(ControlKind.LATCH, toggle!!.kind)
            assertFalse("$id starts hidden", toggle.visible)
            assertEquals("$id starts unbound", 0, toggle.value)
            assertTrue("$id latches without asking", toggle.latches())
            assertFalse("an unbound toggle holds nothing", toggle.latchHold().any)
        }
    }

    @Test
    fun `a bound toggle holds its own bit, and a rebind changes what it holds`() {
        val toggle = pad().find("tg1")!!
        toggle.value = NativeBridge.Y
        assertEquals(LatchHold(buttons = NativeBridge.Y), toggle.latchHold())
        toggle.value = NativeBridge.RIGHT
        assertEquals(LatchHold(buttons = NativeBridge.RIGHT), toggle.latchHold())
    }

    @Test
    fun `a latched plain button still holds its bit`() {
        val button = pad().find("a")!!
        button.toggle = true
        assertTrue(button.latches())
        assertEquals(LatchHold(buttons = NativeBridge.A), button.latchHold())
    }

    @Test
    fun `controls that cannot latch report nothing to hold`() {
        for (id in listOf("stick", "look", "dpad", "swipe", "trick", "stance", "sw1")) {
            val element = pad().find(id)!!
            assertFalse("$id does not latch", element.latches())
            assertEquals("$id holds nothing", LatchHold.NONE, element.latchHold())
        }
    }

    @Test
    fun `a latch built from a command keeps the modifier down with the bit`() {
        assertEquals(
            LatchHold(buttons = NativeBridge.LB or NativeBridge.A),
            LatchHold.of(SwipeAction.held(SwipeModifier.LB, NativeBridge.A)),
        )
        assertEquals(
            LatchHold(buttons = NativeBridge.DOWN, leftTrigger = true),
            LatchHold.of(SwipeAction.held(SwipeModifier.LT, NativeBridge.DOWN)),
        )
        assertEquals(
            LatchHold(buttons = NativeBridge.B, rightTrigger = true),
            LatchHold.of(SwipeAction.held(SwipeModifier.RT, NativeBridge.B)),
        )
        // A double tap latches the same press: a latch is held, not repeated.
        assertEquals(LatchHold.of(SwipeAction.of(NativeBridge.X)),
            LatchHold.of(SwipeAction.double(NativeBridge.X)))
        assertEquals(LatchHold.NONE, LatchHold.of(SwipeAction.NONE))
    }

    @Test
    fun `sw1 cycles the preset tactics and moves the attack-defence level`() {
        val sw1 = pad().find("sw1")!!
        assertEquals(ControlKind.TACTICAL, sw1.kind)
        assertFalse("hidden until wanted", sw1.visible)
        assertEquals(SwipeAction.of(NativeBridge.UP), sw1.swipeAction(Swipe.UP))
        assertEquals(
            "quick tactics rides the d-pad's own arm",
            SwipeAction.held(SwipeModifier.LB, NativeBridge.DOWN),
            sw1.swipeAction(Swipe.DOWN),
        )
        assertEquals(
            "the level down is the double tap the title reads",
            SwipeAction.double(NativeBridge.DOWN),
            sw1.swipeAction(Swipe.LEFT),
        )
        assertEquals(
            SwipeAction.double(NativeBridge.UP),
            sw1.swipeAction(Swipe.RIGHT),
        )
        assertEquals("the centre is the custom slot", SwipeAction.NONE, sw1.swipeAction(Swipe.NONE))
    }

    @Test
    fun `sw2 sends the four advanced instructions under the left trigger`() {
        val sw2 = pad().find("sw2")!!
        assertEquals(ControlKind.TACTICAL, sw2.kind)
        assertFalse(sw2.visible)
        assertEquals(SwipeAction.held(SwipeModifier.LT, NativeBridge.UP), sw2.swipeAction(Swipe.UP))
        assertEquals(SwipeAction.held(SwipeModifier.LT, NativeBridge.DOWN), sw2.swipeAction(Swipe.DOWN))
        assertEquals(SwipeAction.held(SwipeModifier.LT, NativeBridge.LEFT), sw2.swipeAction(Swipe.LEFT))
        assertEquals(SwipeAction.held(SwipeModifier.LT, NativeBridge.RIGHT), sw2.swipeAction(Swipe.RIGHT))
        assertEquals(SwipeAction.NONE, sw2.swipeAction(Swipe.NONE))
    }

    @Test
    fun `the slash-and-burn swipe patch is not the tactical buttons`() {
        val pad = pad()
        val patch = pad.find("swipe")!!
        assertEquals(ControlKind.SWIPE, patch.kind)
        // The patch starts unbound; the new buttons start armed, which is the
        // difference the palette names: one is a blank gesture, the others are
        // the title's tactical commands out of the box.
        assertEquals(0, patch.swipeUp)
        assertTrue(pad.find("sw1")!!.swipeUp != 0)
    }

    // ---- the state machine --------------------------------------------------

    @Test
    fun `a tap latches a jockey and the second lets it go`() {
        val claims = TouchClaims()
        val jockey = pad().find("jockey")!!
        claims.press(1, jockey)
        assertTrue("LT is held", claims.isOn(jockey.id))
        assertTrue(claims.isToggled(jockey.id))
        assertTrue(claims.latchedHold(jockey.id).leftTrigger)
        // Lifting the thumb does not let go: that is the whole point.
        claims.release(1)
        assertTrue("still held after the finger lifts", claims.isOn(jockey.id))
        assertFalse(claims.isToggled("rt"))
        claims.press(2, jockey)
        assertFalse("the second tap lets go", claims.isOn(jockey.id))
        assertEquals(LatchHold.NONE, claims.latchedHold(jockey.id))
    }

    @Test
    fun `a custom toggle latches without the per-button switch`() {
        val claims = TouchClaims()
        val toggle = pad().find("tg1")!!
        toggle.value = NativeBridge.START
        claims.press(1, toggle)
        assertEquals(LatchHold(buttons = NativeBridge.START), claims.latchedHold(toggle.id))
        claims.release(1)
        assertTrue(claims.isOn(toggle.id))
    }

    @Test
    fun `pause and stance flips let every latch go`() {
        val claims = TouchClaims()
        val jockey = pad().find("jockey")!!
        claims.press(1, jockey)
        assertTrue(claims.isOn(jockey.id))
        claims.clearLatches()
        assertFalse("a latch does not outlive the pad it was set on", claims.isOn(jockey.id))
        assertEquals(LatchHold.NONE, claims.latchedHold(jockey.id))

        claims.press(2, jockey)
        claims.clearAll()
        assertFalse(claims.isOn(jockey.id))
    }

    @Test
    fun `a tactical button holds nothing and is not latched by a press`() {
        val claims = TouchClaims()
        val sw1 = pad().find("sw1")!!
        claims.press(1, sw1)
        assertFalse("nothing is sent while the thumb is down", claims.isOn(sw1.id))
        assertFalse(claims.isToggled(sw1.id))
        assertFalse(claims.latchedHold(sw1.id).any)
    }

    @Test
    fun `the mounted latch survives a save and a reload`() {
        val pad = pad()
        pad.find("jockey")!!.visible = true
        pad.find("jockey")!!.toggle = true
        pad.find("tg1")!!.value = NativeBridge.R3
        pad.find("tg2")!!.visible = true
        pad.find("sw1")!!.visible = true
        pad.find("sw2")!!.visible = true

        val restored = ControlLayout.parse(pad.toJson())
        assertNotNull("the pad reads back", restored)
        restored!!
        assertEquals(ControlKind.TRIGGER, restored.find("jockey")!!.kind)
        assertTrue(restored.find("jockey")!!.toggle)
        assertTrue(restored.find("jockey")!!.visible)
        assertEquals(ControlRole.JOCKEY, restored.find("jockey")!!.role)
        assertEquals(NativeBridge.R3, restored.find("tg1")!!.value)
        assertTrue(restored.find("tg2")!!.visible)
        assertEquals(ControlKind.TACTICAL, restored.find("sw1")!!.kind)
        assertEquals(
            "sw1's preset survives the file",
            SwipeAction.held(SwipeModifier.LB, NativeBridge.DOWN),
            restored.find("sw1")!!.swipeAction(Swipe.DOWN),
        )
        assertEquals(
            SwipeAction.held(SwipeModifier.LT, NativeBridge.RIGHT),
            restored.find("sw2")!!.swipeAction(Swipe.RIGHT),
        )
    }

    @Test
    fun `a layout written before the tactical buttons still gains them`() {
        // A v7 file, from before any of this: it must come back knowing the new
        // controls, hidden and armed, rather than losing them.
        val old = """
            {"version":7,"w":800.0,"h":360.0,"elements":[
              {"id":"a","x":0.86,"y":0.85,"size":64.0}
            ]}
        """.trimIndent()
        val restored = ControlLayout.parse(old)
        assertNotNull(restored)
        assertEquals(ControlKind.TACTICAL, restored!!.find("sw1")!!.kind)
        assertEquals(ControlKind.LATCH, restored.find("tg1")!!.kind)
        assertEquals(ControlRole.JOCKEY, restored.find("jockey")!!.role)
        assertEquals(
            SwipeAction.held(SwipeModifier.LT, NativeBridge.UP),
            restored.find("sw2")!!.swipeAction(Swipe.UP),
        )
    }
}
