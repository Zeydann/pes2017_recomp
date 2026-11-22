package com.pes2017.recomp

import java.io.BufferedInputStream
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.io.OutputStream
import java.util.Locale

/**
 * The INSTALL PATCH scan: what a source contains and what it will cost.
 *
 * A source can be either the folder/archive a patch was extracted to (loose
 * `.cpk` files plus `DpFileList.bin`, the shape this app has always accepted)
 * or, as the community actually ships data packs, Xbox 360 content packages
 * (CON/LIVE/PIRS) for title `0x4A3007D1`. Both are read in one pass:
 *
 *  - a file whose first four bytes are an STFS magic is spooled to the scan
 *    scratch folder and read with [StfsPackage]; the inner CPK, DpFileList or
 *    save files are what the plan names;
 *  - anything else keeps the loose-file rules.
 *
 * Title updates, profiles, caches and packages for another title are skipped,
 * with the reason kept for the summary. A source that holds no pack at all is
 * refused as a wrong patch.
 */
object PatchScanner {

    /** The list that orders a data pack's archives. */
    const val DP_FILE_LIST = PatchStore.DP_FILE_LIST

    /** The edit save the title loads. */
    const val EDIT = PatchStore.EDIT

    /** The shared system save; installed only when the player asks for it. */
    const val SYSTEM = PatchStore.SYSTEM

    /** Headroom the install keeps free, on top of the payload. */
    const val MARGIN_BYTES = 8L * 1024 * 1024

    /** PES 2017, Europe. */
    const val TITLE_ID = 0x4A3007D1L

    private const val CT_DLC = 1L
    private const val CT_PROFILE = 0x10000L
    private const val CT_CACHE = 0x40000L
    private const val CT_TITLE_UPDATE = 0xB0000L

    /** A CPK the install writes under [name] in `dlc/SYSTEM00000000`. */
    data class Cpk(
        val path: String,
        val name: String,
        val size: Long,
        val sha256: String = "",
        val stfs: Boolean = false,
    )

    /** A save container the plan carries; [slot] is `EDIT00000000` or `SYSTEM00000000`. */
    data class SaveItem(
        val slot: String,
        val path: String,
        val dataBinSize: Long,
        val hasInf: Boolean,
        val hasPng: Boolean,
    )

    data class Skipped(val path: String, val reason: String)

    enum class Warning {
        NO_DPFILELIST,
        NO_CPK,
        EDIT_SAVE_IN_SOURCE,
        SYSTEM_SAVE_IN_SOURCE,
        EMPTY_CPK,
        DP_UNREADABLE,
        DP_MISSING_CPK,
        DP_ORDER,
    }

    /**
     * Everything the summary shows. [existingBytes] is what the patch slot
     * holds now, [freeBytes] the smallest free space of the volumes involved.
     */
    data class Plan(
        val label: String,
        val kind: PatchSourceKind,
        val cpks: List<Cpk>,
        val dpFileList: Boolean,
        val dpPath: String?,
        val dpFileListSize: Long,
        val dpEntries: Int,
        val dpReadable: Boolean,
        val editSave: Boolean,
        val systemSave: Boolean,
        val saves: List<SaveItem>,
        val skipped: List<Skipped>,
        val warnings: List<Warning>,
        val payloadBytes: Long,
        val existingBytes: Long,
        val freeBytes: Long,
        val stfs: Boolean,
    ) {
        val cpkCount: Int get() = cpks.size
        val cpkBytes: Long get() = cpks.sumOf { it.size }

        /** The source is extracted once, into the app's own storage. */
        val extractBytes: Long get() = payloadBytes

        /** [PatchStore] moves that extraction into the slot; the pack there moves to a backup. */
        val stagingBytes: Long get() = payloadBytes

        /** The game folder is not written: the runtime mounts `dlc/SYSTEM00000000` itself. */
        val gameBytes: Long get() = 0L

        /** Extraction copy + the swap + the pack being replaced + the reserve. */
        val peakBytes: Long
            get() = safeAdd(safeAdd(safeAdd(extractBytes, stagingBytes), existingBytes), MARGIN_BYTES)

        val enough: Boolean get() = freeBytes >= peakBytes

        val hasPatch: Boolean get() = cpks.isNotEmpty() || dpFileList
    }

    private fun safeAdd(a: Long, b: Long): Long = try {
        Math.addExact(a, b)
    } catch (_: ArithmeticException) {
        Long.MAX_VALUE
    }

    /** Scratch for the packages an archive or a folder hands over as streams. */
    private fun defaultScratch(): File =
        File(System.getProperty("java.io.tmpdir") ?: ".", "pes17-patch-scan-" + System.nanoTime())

    fun scan(source: PatchSource, existingBytes: Long, freeBytes: Long): Plan =
        scan(source, existingBytes, freeBytes, defaultScratch())

    fun scan(
        source: PatchSource,
        existingBytes: Long,
        freeBytes: Long,
        scratch: File,
    ): Plan {
        val cpks = ArrayList<Cpk>()
        val byName = HashMap<String, String>()
        val saves = ArrayList<SaveItem>()
        val skipped = ArrayList<Skipped>()
        val warnings = ArrayList<Warning>()
        var dp: Cpk? = null
        var dpPath: String? = null
        var dpEntries = 0
        var dpReadable = false
        var sawStfs = false
        var stfsCount = 0

        val session = source.openSession()
        try {
            while (true) {
                val entry = session.next() ?: break
                if (entry.isDirectory) continue
                val input = java.io.PushbackInputStream(
                    BufferedInputStream(session.open(), 64 * 1024),
                    4,
                )
                val head = readExactly(input, 4)
                if (head.size == 4 && isStfsMagic(String(head, Charsets.ISO_8859_1))) {
                    sawStfs = true
                    stfsCount++
                    scratch.mkdirs()
                    val temp = File(scratch, "scan-$stfsCount-${System.nanoTime()}.con")
                    try {
                        spool(input, temp, entry.size)
                        val packaged = inspectPackage(temp, entry, byName, skipped)
                        when (packaged) {
                            null -> Unit
                            is PackageResult.CpkResult -> cpks.add(packaged.cpk)
                            is PackageResult.DpResult -> {
                                if (dp != null) {
                                    throw PatchSourceException(
                                        "the patch holds two $DP_FILE_LIST files " +
                                            "(${dp.path} and ${entry.path})",
                                        PatchSourceFailure.CORRUPT,
                                    )
                                }
                                dp = packaged.cpk
                                dpPath = entry.path
                                val parsed = parseDp(packaged.bytes, warnings)
                                dpEntries = parsed ?: 0
                                dpReadable = parsed != null
                            }
                            is PackageResult.SaveResult -> {
                                if (saves.any { it.slot.equals(packaged.save.slot, ignoreCase = true) }) {
                                    throw PatchSourceException(
                                        "the patch holds two ${packaged.save.slot} saves",
                                        PatchSourceFailure.CORRUPT,
                                    )
                                }
                                saves.add(packaged.save)
                            }
                        }
                    } finally {
                        temp.delete()
                    }
                    continue
                }
                if (head.isNotEmpty()) input.unread(head)

                // Loose files: the shape an extracted pack has.
                val baseName = entry.path.substringAfterLast('/')
                val lower = baseName.lowercase(Locale.US)
                when {
                    lower.endsWith(".cpk") -> {
                        checkName(baseName, byName)
                        if (entry.size == 0L) warnings.add(Warning.EMPTY_CPK)
                        cpks.add(Cpk(entry.path, baseName, entry.size.coerceAtLeast(0)))
                    }

                    lower == DP_FILE_LIST.lowercase(Locale.US) -> {
                        if (dp != null) {
                            throw PatchSourceException(
                                "the patch holds two $DP_FILE_LIST files " +
                                    "(${dp.path} and ${entry.path})",
                                PatchSourceFailure.CORRUPT,
                            )
                        }
                        val bytes = readCapped(input, DpFileListParser.MAX_BYTES)
                        val parsed = parseDp(bytes, warnings)
                        dpEntries = parsed ?: 0
                        dpReadable = parsed != null
                        dpPath = entry.path
                        dp = Cpk(entry.path, DP_FILE_LIST, bytes.size.toLong(), sha256(bytes))
                    }

                    baseName.equals(EDIT, ignoreCase = true) ||
                        baseName.equals(SYSTEM, ignoreCase = true) -> {
                        skipped.add(Skipped(entry.path, "a save file outside a content package"))
                        warnings.add(
                            if (baseName.equals(EDIT, ignoreCase = true)) {
                                Warning.EDIT_SAVE_IN_SOURCE
                            } else {
                                Warning.SYSTEM_SAVE_IN_SOURCE
                            },
                        )
                    }

                    else -> skipped.add(Skipped(entry.path, "belongs to no patch"))
                }
            }
        } finally {
            runCatching { session.close() }
        }

        if (dp != null && cpks.isEmpty()) warnings.add(Warning.NO_CPK)
        if (dp == null && cpks.isNotEmpty()) warnings.add(Warning.NO_DPFILELIST)
        if (saves.any { it.slot == SYSTEM }) warnings.add(Warning.SYSTEM_SAVE_IN_SOURCE)
        if (saves.any { it.slot == EDIT }) warnings.add(Warning.EDIT_SAVE_IN_SOURCE)

        if (cpks.isEmpty() && dp == null && saves.isEmpty()) {
            throw PatchSourceException(
                "not a PES 2017 patch: ${source.label} holds no .cpk files, " +
                    "no $DP_FILE_LIST and no content package for title 0x4A3007D1",
                PatchSourceFailure.WRONG_PATCH,
            )
        }

        val payload = safeAdd(
            safeAdd(cpks.sumOf { it.size }, dp?.size ?: 0L),
            saves.sumOf {
                it.dataBinSize + (if (it.hasInf) 4096L else 0L) + (if (it.hasPng) 64L * 1024 else 0L)
            },
        )

        return Plan(
            label = source.label,
            kind = source.kind,
            cpks = cpks.sortedBy { it.name.lowercase(Locale.US) },
            dpFileList = dp != null,
            dpPath = dpPath,
            dpFileListSize = dp?.size ?: 0L,
            dpEntries = dpEntries,
            dpReadable = dpReadable,
            editSave = saves.any { it.slot == EDIT },
            systemSave = saves.any { it.slot == SYSTEM },
            saves = saves,
            skipped = skipped,
            warnings = warnings.distinct(),
            payloadBytes = payload,
            existingBytes = existingBytes,
            freeBytes = freeBytes,
            stfs = sawStfs,
        )
    }

    // ---- package classification --------------------------------------------

    private sealed interface PackageResult {
        class CpkResult(val cpk: Cpk) : PackageResult
        class DpResult(val cpk: Cpk, val bytes: ByteArray) : PackageResult
        class SaveResult(val save: SaveItem) : PackageResult
    }

    /**
     * Classifies one STFS package. Returns null when it was skipped (the
     * reason goes into [skipped]), and throws when it looks like a patch but
     * cannot be read.
     */
    private fun inspectPackage(
        file: File,
        entry: PatchEntry,
        byName: MutableMap<String, String>,
        skipped: MutableList<Skipped>,
    ): PackageResult? {
        val pkg = try {
            StfsPackage.open(file)
        } catch (e: StfsException) {
            throw PatchSourceException(
                "${entry.path}: ${e.message}",
                PatchSourceFailure.CORRUPT,
                e,
            )
        }
        pkg.use {
            val header = pkg.header
            when (header.contentType) {
                CT_TITLE_UPDATE -> return skip(entry, "title update (content type 0xB0000)", skipped)
                CT_PROFILE -> return skip(entry, "Xbox profile container (content type 0x10000)", skipped)
                CT_CACHE -> return skip(entry, "content cache (content type 0x40000)", skipped)
            }
            val files = header.entries.filter { !it.directory }
            return when (header.layout) {
                StfsLayout.PERSISTENT -> {
                    if (header.titleId != TITLE_ID) {
                        skip(
                            entry,
                            "another title (0x${header.titleId.toString(16).uppercase(Locale.US)})",
                            skipped,
                        )
                    } else if (header.contentType != CT_DLC) {
                        skip(
                            entry,
                            "content type 0x${header.contentType.toString(16)} is not DLC",
                            skipped,
                        )
                    } else if (files.size != 1) {
                        skip(entry, "a package with ${files.size} files, expected one", skipped)
                    } else {
                        val inner = files[0]
                        when {
                            inner.name.equals(DP_FILE_LIST, ignoreCase = true) -> {
                                if (inner.size > DpFileListParser.MAX_BYTES) {
                                    skip(entry, "$DP_FILE_LIST is larger than 64 KiB", skipped)
                                } else {
                                    val sink = java.io.ByteArrayOutputStream(inner.size.toInt())
                                    pkg.readEntry(inner, sink, verify = true)
                                    val bytes = sink.toByteArray()
                                    PackageResult.DpResult(
                                        Cpk(
                                            path = entry.path,
                                            name = DP_FILE_LIST,
                                            size = inner.size,
                                            sha256 = sha256(bytes),
                                            stfs = true,
                                        ),
                                        bytes,
                                    )
                                }
                            }

                            !inner.name.lowercase(Locale.US).endsWith(".cpk") -> {
                                skip(
                                    entry,
                                    "${inner.name} is neither a CPK nor $DP_FILE_LIST",
                                    skipped,
                                )
                            }

                            else -> {
                                checkName(inner.name, byName)
                                if (inner.size == 0L) throw PatchSourceException(
                                    "${entry.path}: ${inner.name} is empty",
                                    PatchSourceFailure.CORRUPT,
                                )
                                PackageResult.CpkResult(
                                    Cpk(
                                        path = entry.path,
                                        name = inner.name,
                                        size = inner.size,
                                        stfs = true,
                                    ),
                                )
                            }
                        }
                    }
                }

                StfsLayout.SAVE -> {
                    if (header.titleId != TITLE_ID) {
                        skip(
                            entry,
                            "another title (0x${header.titleId.toString(16).uppercase(Locale.US)})",
                            skipped,
                        )
                    } else {
                        val name = entry.path.substringAfterLast('/').uppercase(Locale.US)
                        val dataBin = pkg.findNamed("data.bin")
                        when {
                            name != EDIT && name != SYSTEM ->
                                skip(entry, "a save container with an unknown slot name", skipped)

                            dataBin == null || dataBin.directory || dataBin.size <= 0 ->
                                skip(entry, "a save container without a data.bin", skipped)

                            else -> PackageResult.SaveResult(
                                SaveItem(
                                    slot = name,
                                    path = entry.path,
                                    dataBinSize = dataBin.size,
                                    hasInf = pkg.findNamed("data.inf") != null,
                                    hasPng = pkg.findNamed("data.png") != null,
                                ),
                            )
                        }
                    }
                }
            }
        }
    }

    private fun skip(entry: PatchEntry, reason: String, skipped: MutableList<Skipped>): Nothing? {
        skipped.add(Skipped(entry.path, reason))
        return null
    }

    private fun checkName(name: String, byName: MutableMap<String, String>) {
        val key = name.lowercase(Locale.US)
        val previous = byName.put(key, name)
        if (previous != null) {
            throw PatchSourceException(
                "the source holds two files called $name; the game reads one",
                PatchSourceFailure.CORRUPT,
            )
        }
    }

    private fun parseDp(bytes: ByteArray, warnings: MutableList<Warning>): Int? {
        val entries = try {
            DpFileListParser.parseOrNull(bytes)
        } catch (e: DpFileListParser.Malformed) {
            warnings.add(Warning.DP_UNREADABLE)
            return null
        }
        if (entries == null) {
            warnings.add(Warning.DP_UNREADABLE)
            return null
        }
        var last = Int.MAX_VALUE
        for (record in entries) {
            if (record.priority > last) warnings.add(Warning.DP_ORDER)
            last = record.priority
        }
        return entries.size
    }

    // ---- extraction ----------------------------------------------------------

    /**
     * Copies the planned payload into [target]: the CPKs and `DpFileList.bin`
     * flat (the shape `dlc/SYSTEM00000000` has), and each save in its own
     * `EDIT00000000/` or `SYSTEM00000000/` folder.
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
        val cpkByPath = plan.cpks.associateBy { it.path }
        val saveByPath = plan.saves.associateBy { it.path }
        val total = plan.payloadBytes.coerceAtLeast(1L)
        var copied = 0L
        val scratch = File(target.parentFile ?: target, "scratch-" + System.nanoTime())

        fun report() {
            if (progress != null && !progress(copied, total)) throw Cancelled()
        }

        fun copyStream(input: InputStream, out: File): Long {
            out.delete()
            var bytes = 0L
            out.outputStream().use { sink ->
                val buffer = ByteArray(1 shl 20)
                while (true) {
                    val read = input.read(buffer)
                    if (read <= 0) break
                    sink.write(buffer, 0, read)
                    bytes += read
                    copied += read
                    report()
                }
            }
            return bytes
        }

        fun copyInner(pkg: StfsPackage, entry: StfsEntry, out: File): Long {
            out.parentFile?.mkdirs()
            out.delete()
            val counting = CountingStream(FileOutputStream(out)) { n ->
                copied += n
                report()
            }
            counting.use { sink -> return pkg.readEntry(entry, sink, verify = true).bytes }
        }

        val session = source.openSession()
        try {
            while (true) {
                val entry = session.next() ?: break
                if (entry.isDirectory) continue
                val input = java.io.PushbackInputStream(
                    BufferedInputStream(session.open(), 64 * 1024),
                    4,
                )
                val head = readExactly(input, 4)
                val stfs = head != null && isStfsMagic(String(head, Charsets.ISO_8859_1))
                if (!stfs) {
                    if (head.isNotEmpty()) input.unread(head)
                    val baseName = entry.path.substringAfterLast('/')
                    val cpk = cpkByPath[entry.path]
                    when {
                        cpk != null && !cpk.stfs -> copyStream(input, File(target, cpk.name))
                        plan.dpFileList && !plan.stfs && plan.dpPath == entry.path ->
                            copyStream(input, File(target, DP_FILE_LIST))
                        else -> Unit
                    }
                    continue
                }

                scratch.mkdirs()
                val temp = File(scratch, "extract-${System.nanoTime()}.con")
                try {
                    spool(input, temp, entry.size)
                    StfsPackage.open(temp).use { pkg ->
                        val cpk = cpkByPath[entry.path]
                        val save = saveByPath[entry.path]
                        when {
                            cpk != null -> {
                                val inner = pkg.findNamed(cpk.name)
                                    ?: throw PatchSourceException(
                                        "${entry.path}: no ${cpk.name} inside the package",
                                        PatchSourceFailure.CORRUPT,
                                    )
                                val read = copyInner(pkg, inner, File(target, cpk.name))
                                if (read != cpk.size) {
                                    throw PatchSourceException(
                                        "${cpk.name} wrote $read of ${cpk.size} bytes",
                                        PatchSourceFailure.CORRUPT,
                                    )
                                }
                            }

                            plan.dpPath == entry.path -> {
                                val inner = pkg.findNamed(DP_FILE_LIST)
                                    ?: throw PatchSourceException(
                                        "${entry.path}: no $DP_FILE_LIST inside the package",
                                        PatchSourceFailure.CORRUPT,
                                    )
                                copyInner(pkg, inner, File(target, DP_FILE_LIST))
                            }

                            save != null -> {
                                val dir = File(target, save.slot).apply { mkdirs() }
                                val dataBin = pkg.findNamed("data.bin")
                                    ?: throw PatchSourceException(
                                        "${entry.path}: no data.bin",
                                        PatchSourceFailure.CORRUPT,
                                    )
                                copyInner(pkg, dataBin, File(dir, "data.bin"))
                                pkg.findNamed("data.inf")?.let {
                                    copyInner(pkg, it, File(dir, "data.inf"))
                                }
                                pkg.findNamed("data.png")?.let {
                                    copyInner(pkg, it, File(dir, "data.png"))
                                }
                            }
                        }
                    }
                } finally {
                    temp.delete()
                }
            }
        } catch (error: PatchSourceException) {
            target.deleteRecursively()
            throw error
        } catch (error: Throwable) {
            target.deleteRecursively()
            throw error
        } finally {
            scratch.deleteRecursively()
            runCatching { session.close() }
        }

        val missing = plan.cpks.filter { !File(target, it.name).isFile }
        if (missing.isNotEmpty()) {
            target.deleteRecursively()
            throw PatchSourceException(
                "the extraction lost ${missing.first().name}; the source may be corrupt",
                PatchSourceFailure.CORRUPT,
            )
        }
        if (plan.dpFileList && !File(target, DP_FILE_LIST).isFile) {
            target.deleteRecursively()
            throw PatchSourceException(
                "the extraction lost $DP_FILE_LIST; the source may be corrupt",
                PatchSourceFailure.CORRUPT,
            )
        }
        for (save in plan.saves) {
            if (!File(File(target, save.slot), "data.bin").isFile) {
                target.deleteRecursively()
                throw PatchSourceException(
                    "the extraction lost ${save.slot}/data.bin",
                    PatchSourceFailure.CORRUPT,
                )
            }
        }
    }

    // ---- small helpers -------------------------------------------------------

    private class CountingStream(
        private val sink: OutputStream,
        private val onBytes: (Long) -> Unit,
    ) : OutputStream() {
        override fun write(b: Int) {
            sink.write(b)
            onBytes(1)
        }

        override fun write(b: ByteArray, off: Int, len: Int) {
            sink.write(b, off, len)
            onBytes(len.toLong())
        }

        override fun close() = sink.close()
    }

    private fun readCapped(input: InputStream, max: Int): ByteArray {
        val out = java.io.ByteArrayOutputStream()
        val buffer = ByteArray(16 * 1024)
        var total = 0
        while (total < max) {
            val read = input.read(buffer, 0, minOf(buffer.size, max - total))
            if (read <= 0) break
            out.write(buffer, 0, read)
            total += read
        }
        return out.toByteArray()
    }

    /** Reads up to [count] bytes; a short read returns what there was. */
    private fun readExactly(input: InputStream, count: Int): ByteArray {
        val out = ByteArray(count)
        var offset = 0
        while (offset < count) {
            val read = input.read(out, offset, count - offset)
            if (read <= 0) break
            offset += read
        }
        return if (offset == count) out else out.copyOf(offset)
    }

    private fun spool(input: InputStream, target: File, expected: Long): Long {
        var bytes = 0L
        target.outputStream().use { sink ->
            val buffer = ByteArray(1 shl 20)
            while (true) {
                val read = input.read(buffer)
                if (read <= 0) break
                sink.write(buffer, 0, read)
                bytes += read
            }
        }
        if (expected >= 0 && bytes != expected) {
            target.delete()
            throw PatchSourceException(
                "${target.name}: $bytes bytes read, $expected in the source",
                PatchSourceFailure.CORRUPT,
            )
        }
        return bytes
    }

    private fun sha256(bytes: ByteArray): String =
        java.security.MessageDigest.getInstance("SHA-256").digest(bytes).toHex()

    /** Thrown when the player stopped the extraction. */
    class Cancelled : Exception("cancelled")
}

/** The sizes the install screen prints. */
object PatchFormat {

    fun bytes(value: Long): String {
        val units = arrayOf("B", "KiB", "MiB", "GiB", "TiB")
        var amount = value.toDouble()
        var unit = 0
        while (amount >= 1024.0 && unit < units.lastIndex) {
            amount /= 1024.0
            unit++
        }
        return if (unit == 0) {
            "$value ${units[0]}"
        } else {
            String.format(Locale.US, "%.1f %s", amount, units[unit])
        }
    }

    fun count(value: Int): String = String.format(Locale.US, "%,d", value)
}
