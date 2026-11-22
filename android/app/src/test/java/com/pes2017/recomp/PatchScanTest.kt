package com.pes2017.recomp

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

/**
 * The INSTALL PATCH screen's scan and extraction, without a phone: an
 * extracted folder is a [PatchSource] too, so the rules that matter (which
 * files make a PES 2017 data pack, what gets skipped, what the install costs)
 * are checked here rather than by pressing buttons.
 */
class PatchScanTest {

    @get:Rule
    val temp = TemporaryFolder()

    private fun source(vararg paths: String): File {
        val root = temp.newFolder()
        paths.forEach { path ->
            val file = File(root, path)
            file.parentFile?.mkdirs()
            file.writeText("x")
        }
        return root
    }

    private fun scan(root: File, existing: Long = 0, free: Long = Long.MAX_VALUE / 4): PatchScanner.Plan =
        PatchScanner.scan(FolderSource(root, root.name), existing, free)

    /** A DpFileList with one valid record, so a scan does not report it as unreadable. */
    private fun writeValidDp(file: File) {
        val record = ByteArray(DpFileListParser.RECORD_SIZE)
        record[2] = 0x02; record[3] = 0x62.toByte()
        record[7] = 0x0A
        record[11] = 100
        record[15] = 101
        "data.cpk".toByteArray(Charsets.ISO_8859_1).copyInto(record, 16)
        file.parentFile?.mkdirs()
        file.writeBytes(record)
    }

    @Test
    fun `a data pack is found anywhere inside the folder`() {
        val root = source(
            "dlc/SYSTEM00000000/data.cpk",
            "dlc/SYSTEM00000000/DpFileList.bin",
            "readme.txt",
        )
        writeValidDp(File(root, "dlc/SYSTEM00000000/DpFileList.bin"))
        val plan = scan(root)
        assertEquals(1, plan.cpkCount)
        assertEquals("data.cpk", plan.cpks[0].name)
        assertTrue(plan.dpFileList)
        assertTrue(plan.hasPatch)
        assertEquals(1, plan.skipped.size)
        assertTrue(plan.warnings.isEmpty())
    }

    @Test
    fun `a pack without DpFileList is a plan with a warning, not a failure`() {
        val plan = scan(source("extras.cpk"))
        assertEquals(1, plan.cpkCount)
        assertFalse(plan.dpFileList)
        assertTrue(plan.warnings.contains(PatchScanner.Warning.NO_DPFILELIST))
    }

    @Test
    fun `a folder that holds no pack is refused as the wrong patch`() {
        val error = runCatching { scan(source("readme.txt", "preview.png")) }.exceptionOrNull()
        assertTrue(error is PatchSourceException)
        assertEquals(PatchSourceFailure.WRONG_PATCH, (error as PatchSourceException).failure)
    }

    @Test
    fun `two CPK files with one name are refused rather than silently merged`() {
        val error = runCatching {
            scan(source("a/data.cpk", "b/data.cpk"))
        }.exceptionOrNull()
        assertTrue(error is PatchSourceException)
        assertEquals(PatchSourceFailure.CORRUPT, (error as PatchSourceException).failure)
    }

    @Test
    fun `a loose save file is reported and skipped, only a package save is installed`() {
        val plan = scan(source("data.cpk", "EDIT00000000", "SYSTEM00000000"))
        assertFalse("a loose EDIT00000000 is not a save container", plan.editSave)
        assertFalse(plan.systemSave)
        assertTrue(plan.warnings.contains(PatchScanner.Warning.EDIT_SAVE_IN_SOURCE))
        assertTrue(plan.warnings.contains(PatchScanner.Warning.SYSTEM_SAVE_IN_SOURCE))
        assertTrue(plan.skipped.any { it.path == "EDIT00000000" })
        assertFalse(plan.skipped.any { it.path.endsWith(".cpk") })
    }

    @Test
    fun `the peak keeps the extract, the swap, the kept pack and the reserve`() {
        val root = source("data.cpk")  // one byte
        val plan = scan(root, existing = 10, free = 0)
        assertEquals(
            1 + 1 + 10 + PatchScanner.MARGIN_BYTES,
            plan.peakBytes,
        )
        assertFalse(plan.enough)
    }

    @Test
    fun `extraction writes the pack flat under the names the game reads`() {
        val root = source("deep/dlc/data.cpk", "deep/DpFileList.bin", "readme.txt")
        val plan = scan(root)
        val target = temp.newFolder("out")
        PatchScanner.extract(FolderSource(root, root.name), plan, target)
        assertTrue(File(target, "data.cpk").isFile)
        assertTrue(File(target, "DpFileList.bin").isFile)
        assertFalse(File(target, "readme.txt").exists())
    }

    @Test
    fun `a stopped extraction deletes what it wrote and throws Cancelled`() {
        val root = source("data.cpk")
        val plan = scan(root)
        val target = temp.newFolder("stopped")
        val error = runCatching {
            PatchScanner.extract(FolderSource(root, root.name), plan, target) { _, _ -> false }
        }.exceptionOrNull()
        assertTrue(error is PatchScanner.Cancelled)
        assertFalse(File(target, "data.cpk").exists())
    }

    @Test
    fun `a missing multi-part volume is refused by the source`() {
        val error = runCatching {
            choosePartGroup(listOf("patch.part1.rar", "patch.part3.rar"))
        }.exceptionOrNull()
        assertTrue(error is PatchSourceException)
        assertEquals(PatchSourceFailure.MISSING_PART, (error as PatchSourceException).failure)
    }

    @Test
    fun `an unsafe entry path cannot escape the folder`() {
        val error = runCatching { normalizePatchPath("../../etc/passwd") }.exceptionOrNull()
        assertTrue(error is PatchSourceException)
    }

    @Test
    fun `a DpFileList record is read and an unknown format is reported, not refused`() {
        val name = "data.cpk".toByteArray(Charsets.ISO_8859_1)
        val record = ByteArray(DpFileListParser.RECORD_SIZE)
        record[0] = 0x00; record[1] = 0x00; record[2] = 0x02; record[3] = 0x62
        record[7] = 0x0A // priority 10
        record[11] = 100
        record[15] = 101
        name.copyInto(record, 16)
        val entries = DpFileListParser.parseOrNull(record)
        assertEquals(1, entries!!.size)
        assertEquals("data.cpk", entries[0].name)
        assertEquals(10, entries[0].priority)
        assertNull("an unknown list stays installed as-is", DpFileListParser.parseOrNull(ByteArray(96)))
    }
}
