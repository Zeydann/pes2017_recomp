package com.pes2017.recomp

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

/**
 * Saved pads on disk: the five numbered slots, the named ones, and the names
 * the player actually typed.
 *
 * The bugs this replaces were both name/file confusion -- "My Pad 1" shown as
 * `my_pad_1`, and saving the same name twice silently overwriting. So the tests
 * are less about files than about what comes back in the list.
 */
class LayoutSlotsTest {

    @get:Rule
    val folder = TemporaryFolder()

    private fun store() = LayoutStore(folder.root)

    /** The name as it would be read out of a layout file. */
    private fun nameFromJson(id: String, text: String): String =
        runCatching { org.json.JSONObject(text).optString("name", "") }.getOrDefault("")

    @Test
    fun aDisplayNameBecomesASafeId() {
        assertEquals("my_pad_1", LayoutStore.sanitize("My Pad 1"))
        // Anything outside the safe set is a separator, so accents do not make
        // a file name that a zip or a share sheet would have to escape.
        assertEquals("caf_pad", LayoutStore.sanitize("Café Pad"))
    }

    @Test
    fun anIdCanNeverLeaveTheAppsFolders() {
        // Dots refused, separators collapsed: "../etc/passwd" is one flat word.
        assertEquals("etc_passwd", LayoutStore.sanitize("../etc/passwd"))
        assertFalse(LayoutStore.sanitize("../../x").contains('.'))
        assertFalse(LayoutStore.sanitize("a/b").contains('/'))
    }

    @Test
    fun anEmptyOrSymbolOnlyNameStillGetsAnId() {
        assertEquals("layout", LayoutStore.sanitize(""))
        assertEquals("layout", LayoutStore.sanitize("   "))
        assertEquals("layout", LayoutStore.sanitize("///"))
    }

    @Test
    fun aVeryLongNameIsCapped() {
        assertTrue(LayoutStore.sanitize("x".repeat(200)).length <= 40)
    }

    @Test
    fun aSlotComesBackWithTheNameThatWasStoredInIt() {
        val store = store()
        assertTrue(store.write("my_pad", """{"name":"My Pad"}"""))

        val slot = store.list(::nameFromJson).single()
        assertEquals("My Pad", slot.displayName)
        assertEquals("my_pad", slot.id)
        assertFalse(slot.numbered)
    }

    @Test
    fun aFileWithoutANameFallsBackToAReadableOne() {
        val store = store()
        store.write("away_kit", "{}")

        assertEquals("Away Kit", store.list(::nameFromJson).single().displayName)
    }

    @Test
    fun numberedSlotsSortFirstAndInOrder() {
        val store = store()
        store.write("zebra", """{"name":"Zebra"}""")
        store.write("3", """{"name":"Third"}""")
        store.write("1", """{"name":"First"}""")

        val ids = store.list(::nameFromJson).map { it.id }
        assertEquals(listOf("1", "3", "zebra"), ids)
    }

    @Test
    fun theFirstFreeNumberedSlotIsOffered() {
        val store = store()
        assertEquals("1", store.freeNumberedId())
        store.write("1", "{}")
        store.write("2", "{}")
        assertEquals("3", store.freeNumberedId())
    }

    @Test
    fun fiveNumberedSlotsFillTheFivePlaces() {
        val store = store()
        for (id in store.numberedIds()) store.write(id, "{}")
        assertNull(store.freeNumberedId())
        assertEquals(5, store.numberedIds().size)
    }

    @Test
    fun onlyNamesAreRemovedByDelete() {
        val store = store()
        store.write("2", "{}")
        store.write("mine", """{"name":"Mine"}""")

        assertTrue(store.delete("mine"))
        assertFalse(store.exists("mine"))
        assertTrue(store.exists("2"))
    }

    @Test
    fun theSavePickerOffersAllFiveNumberedSlotsInOrder() {
        val choices = store().saveChoices(::nameFromJson)

        assertEquals(listOf("1", "2", "3", "4", "5"), choices.map { it.id })
        assertTrue("nothing is saved yet", choices.none { it.occupied })
        assertTrue(choices.all { it.displayName.isEmpty() })
    }

    @Test
    fun aTakenSlotIsMarkedAndNamedInTheSavePicker() {
        val store = store()
        store.write("2", """{"name":"Away Pad"}""")

        val choices = store.saveChoices(::nameFromJson)

        val taken = choices.single { it.occupied }
        assertEquals("2", taken.id)
        assertEquals("Away Pad", taken.displayName)
        assertEquals(4, choices.count { !it.occupied })
    }

    @Test
    fun aNamedSaveLandsInsideItsSlotRatherThanBelowTheList() {
        // "Save current layout as…" must put the pad in a numbered slot with
        // the typed name as its label ("1 · test"), not create a loose item
        // that leaves the slot looking empty.
        val store = store()
        store.write("1", """{"name":"test"}""")

        val slots = store.list(::nameFromJson)

        val slot = slots.single()
        assertEquals("1", slot.id)
        assertTrue(slot.numbered)
        assertEquals("test", slot.displayName)
        assertFalse("no loose profile beside the slot", store.exists("test"))
    }

    @Test
    fun aNamedSaveOverASlotKeepsTheSlotAndRenamesIt() {
        val store = store()
        store.write("3", """{"name":"Old"}""")
        store.write("3", """{"name":"New"}""")

        val slot = store.list(::nameFromJson).single()

        assertEquals("3", slot.id)
        assertEquals("New", slot.displayName)
        assertEquals(1, store.saveChoices(::nameFromJson).count { it.occupied })
    }

    @Test
    fun anUnreadableSlotStillCountsAsTaken() {
        // A slot holding something this build cannot parse must still ask
        // before it is overwritten, rather than reading as empty.
        File(folder.root, "controls_slot_4.json").mkdirs()

        val choices = store().saveChoices(::nameFromJson)

        assertTrue(choices.first { it.id == "4" }.occupied)
        // Nothing readable to name it with; the picker falls back to the slot number.
        assertEquals("", choices.first { it.id == "4" }.displayName)
    }

    @Test
    fun aMissingSlotReadsAsNullRatherThanFailing() {
        assertNull(store().read("nothing"))
    }

    @Test
    fun theOldProfilesAreFoldedIntoSlots() {
        val store = store()
        File(folder.root, "controls_profile_away_pad.json").writeText("""{"version":8}""")

        val moved = store.migrateLegacy { _, text -> """{"name":"Away Pad","inner":$text}""" }

        assertEquals(1, moved)
        assertFalse("the old file is gone", File(folder.root, "controls_profile_away_pad.json").exists())
        assertNotNull(store.read("away_pad"))
        assertEquals("Away Pad", store.list(::nameFromJson).single().displayName)
    }

    @Test
    fun aLegacyFileWhoseSlotAlreadyExistsIsLeftAlone() {
        val store = store()
        store.write("away_pad", """{"name":"Current"}""")
        File(folder.root, "controls_profile_away_pad.json").writeText("{}")

        assertEquals(0, store.migrateLegacy { _, _ -> """{"name":"Old"}""" })
        assertEquals("Current", store.list(::nameFromJson).single().displayName)
    }

    @Test
    fun aReadableNameIsRecoveredFromAnId() {
        assertEquals("My Pad 1", LayoutStore.prettyId("my_pad_1"))
        assertEquals("Away", LayoutStore.prettyId("away"))
    }
}
