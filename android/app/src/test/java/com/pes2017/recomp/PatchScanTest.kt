package com.pes2017.recomp

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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

    @Test
    fun `a data pack is found anywhere inside the folder`() {
        val root = source(
            "dlc/SYSTEM00000000/data.cpk",
            "dlc/SYSTEM00000000/DpFileList.bin",
            "readme.txt",
        )
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
    fun `an edit save is reported and skipped, never installed by this screen`() {
        val plan = scan(source("data.cpk", "EDIT00000000", "SYSTEM00000000"))
        assertTrue(plan.editSave)
        assertTrue(plan.systemSave)
        assertTrue(plan.warnings.contains(PatchScanner.Warning.EDIT_SAVE_IN_SOURCE))
        assertTrue(plan.warnings.contains(PatchScanner.Warning.SYSTEM_SAVE_IN_SOURCE))
        assertFalse(plan.skipped.any { it.path.endsWith(".cpk") })
    }

    @Test
    fun `the peak keeps the extract, the staging, the game copy, the kept pack and the reserve`() {
        val root = source("data.cpk")  // one byte
        val plan = scan(root, existing = 10, free = 0)
        assertEquals(
            1 + 1 + 1 + 10 + PatchScanner.MARGIN_BYTES,
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
}
