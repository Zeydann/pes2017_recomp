package com.pes2017.recomp

import java.io.File
import java.util.Locale

/**
 * INSTALL PATCH: what a source holds, before anything is written.
 *
 * PES 2017's data pack is the shape [PatchStore] installs: `.cpk` archives and
 * `DpFileList.bin`, anywhere inside the source, written flat into
 * `dlc/SYSTEM00000000`. A scan therefore looks for exactly those two names and
 * says what it found, what it will skip and what the install will cost at its
 * peak. It never opens a `.cpk` -- PES 2017's packs are not content packages
 * the app can validate, so "valid" here means "the game's own file names".
 *
 * The plan is data only and touches no Android class, so a JVM test can build
 * one from a temp folder ([PatchScanTest]).
 */
object PatchScanner {

    /** The list that orders a data pack's archives; see [PatchStore.DP_FILE_LIST]. */
    const val DP_FILE_LIST = PatchStore.DP_FILE_LIST

    /** The edit save, reported when a source carries one. Never installed by this screen. */
    const val EDIT = PatchStore.EDIT

    /** The shared system save. Never written by this app; reported if a source has one. */
    const val SYSTEM = PatchStore.SYSTEM

    /** Space kept free besides the copy, so the swap does not fill the volume. */
    const val MARGIN_BYTES = 8L * 1024 * 1024

    /** One `.cpk` that would be written into the slot. */
    data class Cpk(val path: String, val name: String, val size: Long)

    /** A file that belongs to no patch, with why it is skipped. */
    data class Skipped(val path: String, val reason: String)

    /** Something the player should know before pressing INSTALL. */
    enum class Warning { NO_DPFILELIST, NO_CPK, EDIT_SAVE_IN_SOURCE, SYSTEM_SAVE_IN_SOURCE, EMPTY_CPK }

    /**
     * Everything the summary shows. [existingBytes] is what the slot holds now
     * (it moves into a backup, so it still occupies space), [freeBytes] is the
     * smallest free space of the two volumes involved -- the game folder's and
     * the app's own, where the archive is extracted.
     */
    data class Plan(
        val label: String,
        val kind: PatchSourceKind,
        val cpks: List<Cpk>,
        val dpFileList: Boolean,
        val dpFileListSize: Long,
        val editSave: Boolean,
        val systemSave: Boolean,
        val skipped: List<Skipped>,
        val warnings: List<Warning>,
        val payloadBytes: Long,
        val existingBytes: Long,
        val freeBytes: Long,
    ) {
        val cpkCount: Int get() = cpks.size
        val cpkBytes: Long get() = cpks.sumOf { it.size }

        /** The archive is extracted once and then staged by [PatchStore]: two copies. */
        val extractBytes: Long get() = payloadBytes
        val stagingBytes: Long get() = payloadBytes

        /** Extraction copy + staging copy + the pack being replaced + the reserve. */
        val peakBytes: Long
            get() = safeAdd(
                safeAdd(safeAdd(extractBytes, stagingBytes), existingBytes),
                MARGIN_BYTES,
            )

        val enough: Boolean get() = freeBytes >= peakBytes

        val hasPatch: Boolean get() = cpks.isNotEmpty() || dpFileList
    }

    /**
     * Reads [source] once and returns the plan. [existingBytes] is the size of
     * the pack in the slot now; [freeBytes] the free space to compare against.
     */
    fun scan(source: PatchSource, existingBytes: Long, freeBytes: Long): Plan {
        val cpks = ArrayList<Cpk>()
        val byName = HashMap<String, String>()
        val skipped = ArrayList<Skipped>()
        val warnings = ArrayList<Warning>()
        var dpFileList = false
        var dpFileListSize = 0L
        var editSave = false
        var systemSave = false

        val session = source.openSession()
        try {
            while (true) {
                val entry = session.next() ?: break
                if (entry.isDirectory) continue
                val name = entry.path.substringAfterLast('/')
                val lower = name.lowercase(Locale.US)
                when {
                    lower.endsWith(".cpk") -> {
                        val previous = byName[name.lowercase(Locale.US)]
                        if (previous != null) {
                            throw PatchSourceException(
                                "the source holds two files called $name ($previous and ${entry.path}); " +
                                    "the game reads them by name, so one would replace the other",
                                PatchSourceFailure.CORRUPT,
                            )
                        }
                        byName[lower] = entry.path
                        cpks.add(Cpk(entry.path, name, entry.size.coerceAtLeast(0)))
                        if (entry.size == 0L) warnings.add(Warning.EMPTY_CPK)
                    }

                    lower == DP_FILE_LIST.lowercase(Locale.US) -> {
                        if (dpFileList) {
                            throw PatchSourceException(
                                "the source holds two $DP_FILE_LIST files; the game reads one",
                                PatchSourceFailure.CORRUPT,
                            )
                        }
                        dpFileList = true
                        dpFileListSize = entry.size.coerceAtLeast(0)
                    }

                    name.equals(EDIT, ignoreCase = true) -> {
                        editSave = true
                        skipped.add(Skipped(entry.path, "edit save (install it from EDITED DATA)"))
                    }

                    name.equals(SYSTEM, ignoreCase = true) -> {
                        systemSave = true
                        skipped.add(Skipped(entry.path, "system save (never modified)"))
                    }

                    else -> skipped.add(Skipped(entry.path, "not part of a data pack"))
                }
            }
        } finally {
            runCatching { session.close() }
        }

        if (!dpFileList && cpks.isEmpty()) {
            throw PatchSourceException(
                "not a PES 2017 patch: ${source.label} holds no .cpk files and no $DP_FILE_LIST",
                PatchSourceFailure.WRONG_PATCH,
            )
        }
        if (!dpFileList) warnings.add(Warning.NO_DPFILELIST)
        if (cpks.isEmpty()) warnings.add(Warning.NO_CPK)
        if (editSave) warnings.add(Warning.EDIT_SAVE_IN_SOURCE)
        if (systemSave) warnings.add(Warning.SYSTEM_SAVE_IN_SOURCE)

        val payload = cpks.sumOf { it.size } + dpFileListSize
        return Plan(
            label = source.label,
            kind = source.kind,
            cpks = cpks.sortedBy { it.name.lowercase(Locale.US) },
            dpFileList = dpFileList,
            dpFileListSize = dpFileListSize,
            editSave = editSave,
            systemSave = systemSave,
            skipped = skipped,
            warnings = warnings,
            payloadBytes = payload,
            existingBytes = existingBytes.coerceAtLeast(0),
            freeBytes = freeBytes.coerceAtLeast(0),
        )
    }

    /**
     * Copies the plan's files out of [source] into [target], flat, under the
     * names the game reads. [progress] gets (copied, total) and returns false
     * to stop: a stopped extraction deletes [target] and writes nothing else.
     */
    fun extract(
        source: PatchSource,
        plan: Plan,
        target: File,
        progress: ((Long, Long) -> Boolean)? = null,
    ) {
        target.deleteRecursively()
        if (!target.mkdirs()) {
            throw PatchSourceException(
                "cannot make a folder to extract into: ${target.absolutePath}",
                PatchSourceFailure.IO,
            )
        }
        val wanted = HashMap<String, String>()
        plan.cpks.forEach { wanted[it.path] = it.name }
        val total = plan.payloadBytes
        var copied = 0L
        val session = source.openSession()
        try {
            while (true) {
                val entry = session.next() ?: break
                if (entry.isDirectory) continue
                val baseName = entry.path.substringAfterLast('/')
                val flat = when {
                    wanted.containsKey(entry.path) -> wanted.getValue(entry.path)
                    plan.dpFileList && baseName.equals(DP_FILE_LIST, ignoreCase = true) -> DP_FILE_LIST
                    else -> continue
                }
                val out = File(target, flat)
                session.open().use { input ->
                    out.outputStream().use { output ->
                        val buffer = ByteArray(1 shl 20)
                        while (true) {
                            val read = input.read(buffer)
                            if (read <= 0) break
                            output.write(buffer, 0, read)
                            copied += read
                            if (progress != null && !progress(copied, total)) {
                                throw Cancelled()
                            }
                        }
                    }
                }
            }
        } catch (error: PatchSourceException) {
            target.deleteRecursively()
            throw error
        } catch (error: Throwable) {
            target.deleteRecursively()
            throw error
        } finally {
            runCatching { session.close() }
        }
        val missing = plan.cpks.filter { !File(target, it.name).isFile }
        if (plan.dpFileList && !File(target, DP_FILE_LIST).isFile) {
            throw PatchSourceException(
                "the extraction lost $DP_FILE_LIST; the source may be corrupt",
                PatchSourceFailure.CORRUPT,
            )
        }
        if (missing.isNotEmpty()) {
            throw PatchSourceException(
                "the extraction lost ${missing.first().name}; the source may be corrupt",
                PatchSourceFailure.CORRUPT,
            )
        }
    }

    /** Thrown when the player stopped the extraction. */
    class Cancelled : Exception("cancelled")

    private fun safeAdd(a: Long, b: Long): Long = try {
        Math.addExact(a, b)
    } catch (_: ArithmeticException) {
        Long.MAX_VALUE
    }
}

/** One formatting of byte counts, so the summary and the PATCHES screen agree. */
object PatchFormat {

    fun bytes(value: Long): String {
        val size = value.coerceAtLeast(0)
        if (size < 1024) return "$size B"
        val units = arrayOf("KB", "MB", "GB", "TB")
        var amount = size.toDouble() / 1024.0
        var unit = 0
        while (amount >= 1024 && unit < units.size - 1) {
            amount /= 1024.0
            unit++
        }
        return String.format(Locale.US, "%.1f %s", amount, units[unit])
    }

    fun count(value: Int): String = String.format(Locale.US, "%,d", value)
}
