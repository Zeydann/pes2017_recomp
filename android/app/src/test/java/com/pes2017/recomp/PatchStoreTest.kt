package com.pes2017.recomp

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

/**
 * PATCHES without a phone: the slot layout, the backups, and the two rules that
 * matter most -- a backup is kept before anything is replaced, and a backup
 * that does not match its record is refused instead of restored.
 *
 * The paths are the runtime's own: the game folder holds `default.xex`, and
 * `dlc/` and `saves/` sit beside it in the folder above.
 */
class PatchStoreTest {

    @get:Rule
    val temp = TemporaryFolder()

    private fun game(): File {
        val game = temp.newFolder("game")
        File(game, "default.xex").writeText("xex")
        return game
    }

    private fun patchFolder(vararg names: String): File {
        val source = temp.newFolder("patch-${System.nanoTime()}")
        for (name in names) File(source, name).writeText(name)
        return source
    }

    @Test
    fun aPatchIsWrittenToTheSlotAndNotedAsInstalled() {
        val game = game()
        PatchStore.installPatch(game, patchFolder("data.cpk", "DpFileList.bin"))

        val slot = PatchStore.patchSlot(PatchStore.rootOf(game))
        assertTrue(File(slot, "data.cpk").isFile)
        assertTrue(File(slot, "DpFileList.bin").isFile)
        val status = PatchStore.status(game)
        assertTrue(status.installed)
        assertEquals(2, status.patch.files)
        assertEquals("patch-", status.patchFrom.take(6))
        assertNull("nothing was there to back up", status.backups.firstOrNull())
    }

    @Test
    fun theSlotThatWasThereIsKeptBeforeANewPatchReplacesIt() {
        val game = game()
        val slot = PatchStore.patchSlot(PatchStore.rootOf(game))
        slot.mkdirs()
        File(slot, "original.cpk").writeText("original")

        val backup = PatchStore.installPatch(game, patchFolder("new.cpk"))
        assertNotNull(backup)
        assertEquals(1, backup!!.files)
        assertTrue(File(slot, "new.cpk").isFile)
        assertFalse(File(slot, "original.cpk").exists())
        assertTrue(File(PatchStore.backupsRoot(PatchStore.rootOf(game)), "patch/${backup.name}/original.cpk").isFile)
    }

    @Test
    fun restoringPutsThePreviousPackBackAndKeepsTheOneItReplaced() {
        val game = game()
        val slot = PatchStore.patchSlot(PatchStore.rootOf(game))
        slot.mkdirs()
        File(slot, "original.cpk").writeText("original")
        PatchStore.installPatch(game, patchFolder("new.cpk"))

        val kept = PatchStore.restorePatch(game)
        assertNotNull(kept)
        assertTrue(File(slot, "original.cpk").isFile)
        assertFalse(File(slot, "new.cpk").exists())
        assertTrue(File(PatchStore.backupsRoot(PatchStore.rootOf(game)), "patch/${kept!!.name}/new.cpk").isFile)
        assertFalse("the restored pack is no longer app-installed", PatchStore.status(game).installed)
    }

    @Test
    fun aBackupThatDoesNotMatchItsRecordIsRefusedAndNothingIsChanged() {
        val game = game()
        val slot = PatchStore.patchSlot(PatchStore.rootOf(game))
        slot.mkdirs()
        File(slot, "original.cpk").writeText("original")
        PatchStore.installPatch(game, patchFolder("new.cpk"))

        val backupDir = File(PatchStore.backupsRoot(PatchStore.rootOf(game)), "patch").listFiles()!!.first()
        File(backupDir, "original.cpk").delete()   // a backup that lost a file

        try {
            PatchStore.restorePatch(game)
            fail("a short backup must not be restored")
        } catch (expected: PatchStore.Failure) {
            assertTrue(expected.message!!.contains("record") || expected.message!!.contains("empty"))
        }
        assertTrue("the installed pack is untouched", File(slot, "new.cpk").isFile)
    }

    @Test
    fun aSourceWithNoArchivesIsRefusedAndTheSlotIsUntouched() {
        val game = game()
        val slot = PatchStore.patchSlot(PatchStore.rootOf(game))
        slot.mkdirs()
        File(slot, "installed.cpk").writeText("installed")

        try {
            PatchStore.installPatch(game, patchFolder("readme.txt"))
            fail("a folder with no .cpk is not a patch")
        } catch (expected: PatchStore.Failure) {
            assertTrue(expected.message!!.contains("not a PES 2017 patch"))
        }
        assertTrue(File(slot, "installed.cpk").isFile)
    }

    @Test
    fun aFolderWithoutTheGameIsRefused() {
        val folder = temp.newFolder("not-a-game")
        try {
            PatchStore.installPatch(folder, patchFolder("data.cpk"))
            fail("the game has to be there")
        } catch (expected: PatchStore.Failure) {
            assertTrue(expected.message!!.contains("default.xex"))
        }
    }

    @Test
    fun editedDataIsReplacedAfterACopyIsKeptAndRestoredExactly() {
        val game = game()
        val saves = PatchStore.savesDir(PatchStore.rootOf(game)).apply { mkdirs() }
        val edit = File(saves, PatchStore.EDIT).apply { writeText("old edit") }
        val system = File(saves, PatchStore.SYSTEM).apply { writeText("system data") }
        val systemHash = PatchStore.sha256(system)

        val incoming = File(temp.newFolder("incoming"), "EDIT00000000").apply { writeText("new edit") }
        val backup = PatchStore.replaceEditedData(game, incoming)
        assertNotNull(backup)
        assertEquals("new edit", edit.readText())
        assertTrue("SYSTEM00000000 is never written", systemHash == PatchStore.sha256(system))

        PatchStore.restoreEditedData(game)
        assertEquals("old edit", edit.readText())
        assertTrue("SYSTEM00000000 is never written", systemHash == PatchStore.sha256(system))
        assertFalse(PatchStore.status(game).editAt > 0)
    }

    @Test
    fun editedDataWithNoBackupCannotBeRestored() {
        val game = game()
        try {
            PatchStore.restoreEditedData(game)
            fail("there is nothing to restore")
        } catch (expected: PatchStore.Failure) {
            assertTrue(expected.message!!.contains("no edited-data backup"))
        }
    }

    @Test
    fun theStatusReportsBothSlotsAndTheBackupsWithoutTouchingThem() {
        val game = game()
        val saves = PatchStore.savesDir(PatchStore.rootOf(game)).apply { mkdirs() }
        File(saves, PatchStore.EDIT).writeText("edit")
        File(saves, PatchStore.SYSTEM).writeText("system")
        PatchStore.installPatch(game, patchFolder("data.cpk"))

        val status = PatchStore.status(game)
        assertEquals(1, status.patch.files)
        assertEquals(1, status.edit.files)
        assertEquals(1, status.system.files)
        assertFalse(status.backups.any { it.kind == "patch" })
    }
}
