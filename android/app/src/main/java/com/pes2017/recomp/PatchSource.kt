package com.pes2017.recomp

import android.content.Context
import android.net.Uri
import android.os.Environment
import android.provider.DocumentsContract
import androidx.documentfile.provider.DocumentFile
import java.io.Closeable
import java.io.File
import java.io.InputStream
import java.util.Locale

/**
 * INSTALL PATCH: how the player handed over a patch.
 *
 * Three shapes, the same three the PES 2018 import screen offers:
 *  - [ARCHIVE]   one .zip, .rar or .7z as it was downloaded;
 *  - [FOLDER]    the folder a patch was extracted to (the shape PES 2017's
 *                [PatchStore] already installs from);
 *  - [MULTIPART] a folder holding every part of one archive, part 1 picked.
 *
 * A source is read twice: once to scan (names and sizes only) and once to
 * extract. Nothing is written to the game folder by either pass -- the
 * extraction lands in the app's own storage and only [PatchStore] writes the
 * payload, into the game folder and `dlc/SYSTEM00000000`, after it keeps a
 * backup.
 */
enum class PatchSourceKind { ARCHIVE, FOLDER, MULTIPART }

/** Why a source could not be read; each one has its own result card. */
enum class PatchSourceFailure { PASSWORD, CORRUPT, MISSING_PART, UNSUPPORTED_ARCHIVE, WRONG_PATCH, IO }

class PatchSourceException(
    message: String,
    val failure: PatchSourceFailure,
    cause: Throwable? = null,
) : Exception(message, cause)

/** One entry in a source; [size] is -1 when the archive does not say up front. */
data class PatchEntry(val path: String, val size: Long, val isDirectory: Boolean)

/** A patch as the player supplied it. [partCount] is 1 unless it is multi-part. */
interface PatchSource {
    val label: String
    val kind: PatchSourceKind
    val partCount: Int

    fun openSession(): PatchSession

    /** Releases anything the source opened (a copied cache file, a descriptor). */
    fun close()
}

/**
 * A sequential read of a source. libarchive (which is what reads RAR and 7Z)
 * only walks forward, so the interface does too: [next], then [open] for that
 * entry's bytes, then [next] again.
 */
interface PatchSession : Closeable {
    fun next(): PatchEntry?

    fun open(): InputStream
}

/** Strips empty and `.` segments and refuses `..`, so an entry cannot escape a folder. */
internal fun normalizePatchPath(raw: String): String {
    val slashed = raw.replace('\\', '/')
    val parts = ArrayList<String>()
    for (part in slashed.split('/')) {
        when (part) {
            "", "." -> Unit
            ".." -> throw PatchSourceException(
                "the source holds a path that climbs out of it: $raw",
                PatchSourceFailure.UNSUPPORTED_ARCHIVE,
            )
            else -> parts.add(part)
        }
    }
    return parts.joinToString("/")
}

private const val MAX_DEPTH = 8
private const val MAX_ENTRIES = 40_000

/** Copies every file under a real folder, in a stable order. */
class FolderSource(
    private val root: File,
    override val label: String = root.name,
) : PatchSource {

    override val kind = PatchSourceKind.FOLDER
    override val partCount = 1

    override fun openSession(): PatchSession = object : PatchSession {
        private val base = root.absoluteFile
        private val files = root.walkTopDown().maxDepth(MAX_DEPTH)
            .filter { it.isFile }
            .sortedBy { it.path.lowercase(Locale.US) }
            .iterator()
        private var current: File? = null
        private var seen = 0

        override fun next(): PatchEntry? {
            if (!files.hasNext()) return null
            if (++seen > MAX_ENTRIES) {
                throw PatchSourceException(
                    "the folder holds more than $MAX_ENTRIES files; is this the patch?",
                    PatchSourceFailure.IO,
                )
            }
            val file = files.next()
            val name = try {
                file.relativeTo(base).invariantSeparatorsPath
            } catch (_: Throwable) {
                file.name
            }
            current = file
            return PatchEntry(normalizePatchPath(name), file.length(), false)
        }

        override fun open(): InputStream {
            val file = current
                ?: throw PatchSourceException("no entry was read before it was opened", PatchSourceFailure.IO)
            return try {
                file.inputStream()
            } catch (error: Throwable) {
                throw PatchSourceException(
                    "cannot read ${file.name}: ${error.message ?: error}",
                    PatchSourceFailure.IO,
                    error,
                )
            }
        }

        override fun close() {
            current = null
        }
    }

    override fun close() = Unit
}

/**
 * A folder chosen through the system picker whose real path cannot be named
 * (another volume, a provider). Its entries are read through the provider,
 * which is how a patch on a card or in a Downloads tree is still installable.
 */
class DocumentFolderSource(
    private val context: Context,
    tree: Uri,
    override val label: String,
) : PatchSource {

    override val kind = PatchSourceKind.FOLDER
    override val partCount = 1

    private val root = DocumentFile.fromTreeUri(context, tree)
        ?: throw PatchSourceException("cannot open the picked folder", PatchSourceFailure.IO)

    override fun openSession(): PatchSession = object : PatchSession {
        private val queue = ArrayDeque<Pair<String, DocumentFile>>()
        private var current: DocumentFile? = null
        private var seen = 0

        init {
            queue.addLast("" to root)
        }

        override fun next(): PatchEntry? {
            while (queue.isNotEmpty()) {
                val (prefix, directory) = queue.removeFirst()
                val children = try {
                    directory.listFiles().toList()
                } catch (error: Throwable) {
                    throw PatchSourceException(
                        "cannot list ${directory.name ?: label}: ${error.message ?: error}",
                        PatchSourceFailure.IO,
                        error,
                    )
                }
                for (child in children) {
                    val name = child.name ?: continue
                    val path = if (prefix.isEmpty()) name else "$prefix/$name"
                    if (child.isDirectory) {
                        if (path.count { it == '/' } < MAX_DEPTH) queue.addLast(path to child)
                        continue
                    }
                    if (++seen > MAX_ENTRIES) {
                        throw PatchSourceException(
                            "the folder holds more than $MAX_ENTRIES files; is this the patch?",
                            PatchSourceFailure.IO,
                        )
                    }
                    current = child
                    return PatchEntry(normalizePatchPath(path), child.length(), false)
                }
            }
            return null
        }

        override fun open(): InputStream {
            val file = current
                ?: throw PatchSourceException("no entry was read before it was opened", PatchSourceFailure.IO)
            return try {
                context.contentResolver.openInputStream(file.uri)
                    ?: throw PatchSourceException("cannot open ${file.name}", PatchSourceFailure.IO)
            } catch (error: PatchSourceException) {
                throw error
            } catch (error: Throwable) {
                throw PatchSourceException(
                    "cannot read ${file.name}: ${error.message ?: error}",
                    PatchSourceFailure.IO,
                    error,
                )
            }
        }

        override fun close() {
            current = null
        }
    }

    override fun close() = Unit
}

/**
 * Builds a [PatchSource] from what each button on the INSTALL PATCH screen
 * picked. A tree is probed in this order: does it hold the patch's files
 * (a `.cpk` or `DpFileList.bin`), so it is an extracted patch; does it hold
 * one archive (or one multi-part set), so it is that archive; otherwise it is
 * left as a folder and the scanner reports it is not a PES 2017 patch.
 */
object PatchSources {

    fun fromFile(file: File, password: String? = null): PatchSource =
        if (file.isDirectory) {
            FolderSource(file)
        } else {
            PatchArchiveSource.fromFiles(listOf(file), file.name, PatchSourceKind.ARCHIVE, password)
        }

    fun fromDocument(
        context: Context,
        uri: Uri,
        displayName: String?,
        password: String? = null,
    ): PatchSource = PatchArchiveSource.fromDocument(
        context,
        uri,
        displayName?.takeIf { it.isNotBlank() } ?: "patch archive",
        password,
    )

    fun fromTree(
        context: Context,
        treeUri: Uri,
        displayName: String?,
        password: String? = null,
    ): PatchSource {
        val label = displayName?.takeIf { it.isNotBlank() } ?: "patch folder"
        val real = resolveRealDirectory(treeUri)
        if (real != null && real.canRead()) {
            if (holdsPatchPayload(real)) return FolderSource(real, real.name)
            val group = findArchiveGroup(real)
            if (group != null) {
                val kind = if (group.size > 1) PatchSourceKind.MULTIPART else PatchSourceKind.ARCHIVE
                return PatchArchiveSource.fromFiles(group, label.ifBlank { real.name }, kind, password)
            }
            return FolderSource(real, real.name)
        }

        val root = DocumentFile.fromTreeUri(context, treeUri)
            ?: throw PatchSourceException("cannot open the picked folder", PatchSourceFailure.IO)
        val children = try {
            root.listFiles().toList()
        } catch (error: Throwable) {
            throw PatchSourceException(
                "cannot list the picked folder: ${error.message ?: error}",
                PatchSourceFailure.IO,
                error,
            )
        }
        val documents = children.filter { it.isFile && it.name != null }
        if (documents.any { holdsPatchPayload(it.name.orEmpty()) }) {
            return DocumentFolderSource(context, treeUri, label)
        }
        val chosen = try {
            choosePartGroup(documents.mapNotNull { it.name })
        } catch (error: PatchSourceException) {
            throw error
        }
        if (chosen != null) {
            if (chosen.size > 1) {
                throw PatchSourceException(
                    "a multi-part archive in a folder the app can only read through the system " +
                        "picker cannot be opened; put the parts on the same storage and choose " +
                        "the folder again, or extract the patch",
                    PatchSourceFailure.UNSUPPORTED_ARCHIVE,
                )
            }
            val document = documents.firstOrNull { it.name == chosen.first() }
            if (document != null) {
                return PatchArchiveSource.fromDocument(context, document.uri, chosen.first(), password)
            }
        }
        return DocumentFolderSource(context, treeUri, label)
    }

    /** The real directory behind a picked tree, or null when no path can name it. */
    fun resolveRealDirectory(tree: Uri): File? = try {
        val id = DocumentsContract.getTreeDocumentId(tree)
        val volume = id.substringBefore(':')
        val relative = id.substringAfter(':', "")
        val base = if (volume.equals("primary", ignoreCase = true)) {
            Environment.getExternalStorageDirectory()
        } else {
            File("/storage/$volume").takeIf { volume.isNotEmpty() && volume != id }
        }
        when {
            base == null -> null
            relative.isEmpty() -> base.takeIf { it.isDirectory }
            else -> File(base, relative).takeIf { it.isDirectory }
        }
    } catch (_: Throwable) {
        null
    }
}

/** The names a patch's own files have; see [PatchStore]. */
internal fun holdsPatchPayload(name: String): Boolean {
    val lower = name.lowercase(Locale.US)
    return lower.endsWith(".cpk") || lower == PatchScanner.DP_FILE_LIST.lowercase(Locale.US)
}

internal fun holdsPatchPayload(directory: File): Boolean =
    directory.walkTopDown().maxDepth(MAX_DEPTH)
        .any { it.isFile && holdsPatchPayload(it.name) }

/** True when the tree already carries a `Content` directory (a PES 2018 shape, kept for parity). */
internal fun hasContentDirectory(directory: File): Boolean =
    directory.name.equals("Content", ignoreCase = true) ||
        File(directory, "Content").isDirectory ||
        File(directory, "content").isDirectory

/**
 * The ordered parts of one archive in [names], or null when there is none.
 * `.001` volumes and split ZIPs are refused with a reason, a missing part is
 * refused rather than opened half-way.
 */
internal fun choosePartGroup(names: List<String>): List<String>? {
    if (names.isEmpty()) return null
    val lower = names.map { it.lowercase(Locale.US) }

    if (lower.any { it.matches(Regex(""".*\.7z\.\d{3}$""")) }) {
        throw PatchSourceException(
            "a multi-volume 7z (.001) cannot be read; use a single .7z or extract the patch",
            PatchSourceFailure.UNSUPPORTED_ARCHIVE,
        )
    }

    val partRar = Regex("""^(.*)\.part(\d{1,3})\.rar$""")
    val partGroups = HashMap<String, MutableList<Pair<String, Int>>>()
    names.forEach { name ->
        val match = partRar.matchEntire(name.lowercase(Locale.US))
        if (match != null) {
            partGroups.getOrPut(match.groupValues[1]) { ArrayList() }
                .add(name to match.groupValues[2].toInt())
        }
    }
    if (partGroups.isNotEmpty()) {
        if (partGroups.size > 1) {
            throw PatchSourceException(
                "more than one multi-part RAR in the folder: " + partGroups.keys.sorted().joinToString(", "),
                PatchSourceFailure.UNSUPPORTED_ARCHIVE,
            )
        }
        val group = partGroups.values.first().sortedBy { it.second }
        group.forEachIndexed { index, pair ->
            if (pair.second != index + 1) {
                throw PatchSourceException(
                    "the multi-part RAR is missing part ${index + 1}",
                    PatchSourceFailure.MISSING_PART,
                )
            }
        }
        return group.map { it.first }
    }

    val rarBases = names.filter {
        it.lowercase(Locale.US).endsWith(".rar") && !partRar.matches(it.lowercase(Locale.US))
    }
    if (rarBases.isNotEmpty()) {
        if (rarBases.size > 1) {
            throw PatchSourceException(
                "more than one RAR in the folder: ${rarBases.joinToString(", ")}",
                PatchSourceFailure.UNSUPPORTED_ARCHIVE,
            )
        }
        val selected = rarBases[0]
        val base = selected.lowercase(Locale.US).dropLast(4)
        val extras = names.filter {
            it.lowercase(Locale.US).matches(Regex("^" + Regex.escape(base) + "\\.r\\d\\d$"))
        }.sortedBy { it.lowercase(Locale.US).takeLast(2).toIntOrNull() ?: 0 }
        extras.forEachIndexed { index, name ->
            val number = name.lowercase(Locale.US).takeLast(2).toIntOrNull() ?: -1
            if (number != index) {
                throw PatchSourceException(
                    "the multi-part RAR is missing part r${index.toString().padStart(2, '0')}",
                    PatchSourceFailure.MISSING_PART,
                )
            }
        }
        return listOf(selected) + extras
    }

    val sevenZips = names.filter { it.lowercase(Locale.US).endsWith(".7z") }
    if (sevenZips.isNotEmpty()) {
        if (sevenZips.size > 1) {
            throw PatchSourceException(
                "more than one 7z in the folder: ${sevenZips.joinToString(", ")}",
                PatchSourceFailure.UNSUPPORTED_ARCHIVE,
            )
        }
        return sevenZips
    }

    val zips = names.filter { it.lowercase(Locale.US).endsWith(".zip") }
    if (zips.isNotEmpty()) {
        if (zips.size > 1) {
            throw PatchSourceException(
                "more than one ZIP in the folder: ${zips.joinToString(", ")}",
                PatchSourceFailure.UNSUPPORTED_ARCHIVE,
            )
        }
        val base = zips[0].lowercase(Locale.US).dropLast(4)
        if (names.any { it.lowercase(Locale.US).matches(Regex("^" + Regex.escape(base) + "\\.z\\d\\d$")) }) {
            throw PatchSourceException(
                "split ZIP volumes cannot be read; use a single ZIP",
                PatchSourceFailure.UNSUPPORTED_ARCHIVE,
            )
        }
        return zips
    }

    if (lower.any { it.matches(Regex(""".*\.\d{3}$""")) }) {
        throw PatchSourceException(
            "numbered archive volumes cannot be read; choose the folder with every part",
            PatchSourceFailure.UNSUPPORTED_ARCHIVE,
        )
    }
    return null
}

/** The files of one archive group in [directory], or null when there is none. */
internal fun findArchiveGroup(directory: File): List<File>? {
    val files = directory.listFiles().orEmpty()
        .filter { it.isFile }
        .sortedBy { it.name.lowercase(Locale.US) }
    val chosen = choosePartGroup(files.map { it.name }) ?: return null
    val byName = files.groupBy { it.name }
    return chosen.map { name ->
        val matches = byName[name].orEmpty()
        if (matches.size != 1) {
            throw PatchSourceException("ambiguous archive part $name", PatchSourceFailure.UNSUPPORTED_ARCHIVE)
        }
        matches[0]
    }
}
