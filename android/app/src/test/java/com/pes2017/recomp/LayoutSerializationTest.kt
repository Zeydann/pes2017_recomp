package com.pes2017.recomp

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The saved pad, across versions: what a v7 file keeps, what an older file
 * becomes, and what a file that is not a layout at all does. None of these
 * paths may delete a player's controls.
 */
class LayoutSerializationTest {

    @Test
    fun aRoundTripKeepsRolesLabelsAndTheDefendingCopy() {
        val layout = ControlLayout.defaults(800f, 360f)
        val a = layout.find("a")!!
        a.role = ControlRole.SHOOT
        a.label = "FIRE"
        a.splitStance()
        a.defendX = 0.3f
        a.defendY = 0.7f

        val parsed = ControlLayout.parse(layout.toJson())!!
        val again = parsed.find("a")!!
        assertEquals(ControlRole.SHOOT, again.role)
        assertEquals("FIRE", again.label)
        assertEquals(0.3f, again.defendX!!, 0.0001f)
        assertEquals(0.7f, again.defendY!!, 0.0001f)
    }

    @Test
    fun aCustomTogglesMappingSurvivesAReload() {
        val layout = ControlLayout.defaults(800f, 360f)
        layout.find("tg1")!!.toggleProfile = "3"

        val parsed = ControlLayout.parse(layout.toJson())!!

        assertEquals("3", parsed.find("tg1")!!.toggleProfile)
        // A toggle the player never mapped stays a plain toggle.
        assertEquals("", parsed.find("tg2")!!.toggleProfile)
    }

    @Test
    fun aVersionSixFileIsMigratedOverTheDefaultsRatherThanDiscarded() {
        // No w/h, no role, no defend object: exactly what the previous build wrote.
        val old = """
            {"version":6,"elements":[
              {"id":"a","x":0.5,"y":0.5,"size":68,"alpha":0.5,"visible":true,"toggle":false,
               "when":"ALWAYS","swipes":[0,0,0,0,0]}
            ]}
        """.trimIndent()
        val parsed = ControlLayout.parse(old)!!
        assertEquals(0.5f, parsed.find("a")!!.xFrac, 0.001f)
        assertEquals(68f, parsed.find("a")!!.sizeDp, 0.001f)
        // The elements the old file never mentioned keep the factory pad.
        assertTrue(parsed.find("b") != null)
        assertTrue(parsed.find("stick") != null)
    }

    @Test
    fun aRemovedControlStaysRemovedAcrossAReload() {
        val layout = ControlLayout.defaults(800f, 360f)
        assertTrue(layout.remove("l3"))
        val parsed = ControlLayout.parse(layout.toJson())!!
        assertNull("a removed control must not be resurrected", parsed.find("l3"))
    }

    @Test
    fun theMovementStickCannotBeRemoved() {
        val layout = ControlLayout.defaults(800f, 360f)
        assertFalse(layout.remove("stick"))
        assertTrue(layout.find("stick") != null)
    }

    @Test
    fun resetAllBringsTheFactoryPadBack() {
        val layout = ControlLayout.defaults(800f, 360f)
        layout.find("a")!!.xFrac = 0.1f
        layout.remove("b")
        layout.resetAll()
        assertEquals(64f, layout.find("a")!!.sizeDp, 0.001f)
        assertTrue(layout.find("b") != null)
    }

    @Test
    fun classicSpacingIsSelectable() {
        val classic = ControlLayout.defaults(800f, 360f, PROFILE_CLASSIC)
        assertEquals(68f, classic.find("a")!!.sizeDp, 0.001f)
        assertEquals(308f, classic.find("a")!!.yFrac * 360f, 0.5f)
    }

    @Test
    fun aRenamedTacticalButtonKeepsThePlayersOwnWord() {
        val layout = ControlLayout.defaults(800f, 360f)
        val sw1 = layout.find("sw1")!!
        // Unrenamed, the factory word stands on either button style.
        assertEquals("SW1", ControlLayout.labelFor(sw1, playStation = true))

        sw1.label = "SW2"
        // A rename used to be kept in the file and then ignored on every control
        // the PlayStation naming table knew, which is exactly the tactical pair.
        assertEquals("SW2", ControlLayout.labelFor(sw1, playStation = true))
        assertEquals("SW2", ControlLayout.labelFor(sw1, playStation = false))

        val parsed = ControlLayout.parse(layout.toJson())!!
        assertEquals("SW2", parsed.find("sw1")!!.label)
        assertEquals("SW2", ControlLayout.labelFor(parsed.find("sw1")!!, playStation = true))
        // A control nobody renamed still says its console name.
        assertEquals("L1", ControlLayout.labelFor(parsed.find("lb")!!, playStation = true))
    }

    @Test
    fun textThatIsNotALayoutIsRejectedWithoutThrowing() {
        assertNull(ControlLayout.parse("not a layout"))
    }

    @Test
    fun aNewerFileIsLeftForTheNewerBuild() {
        // Version 99 must not be half-read into a v7 pad; the loader keeps the
        // file and starts this session on the defaults.
        assertNull(ControlLayout.parse("""{"version":99,"elements":[]}"""))
    }
}
