package com.pes2017.recomp

import java.io.File
import java.io.FileOutputStream

/** One saved pad: a numbered slot, or a layout the player named. */
data class LayoutSlot(
    /** The id the file is named for: "1".."5", or a sanitised name. Never a display name. */
    val id: String,
    /** What the player sees and may change; kept inside the layout file. */
    val displayName: String,
    /** True for the five numbered slots, which always exist as places to save to. */
    val numbered: Boolean,
) {
    val builtInPlace: Boolean get() = numbered
}

/**
 * One place the "Save Layout" picker offers: a numbered slot and what, if
 * anything, is already saved in it.
 */
data class SaveSlotChoice(
    /** The numbered slot's id: "1".."5". */
    val id: String,
    /** The name of the layout already in the slot, or "" when it is empty. */
    val displayName: String,
    /** True when saving into it would replace a layout the player already has. */
    val occupied: Boolean,
)

/**
 * The pad files on disk, one per slot.
 *
 * Two things went wrong with the first version, and both were the same mistake:
 * the player's name was used as the file name. "My Pad 1" came back as
 * `my_pad_1`, and the name shown in the list was the file name, so the layout
 * the player had named came back renamed. Here the file name is derived and
 * fixed (`controls_slot_3.json`), the display name lives inside the layout
 * itself, and [sanitize] is only ever used to derive an id for a named layout.
 *
 * Pure file handling on purpose -- [LayoutSlotsTest] runs it against a
 * temporary directory with no Android context.
 */
class LayoutStore(private val dir: File) {

    /** The numbered slots, in order, whether or not a file exists for them yet. */
    fun numberedIds(): List<String> = (1..MAX_NUMBERED).map { it.toString() }

    fun fileFor(id: String): File = File(dir, PREFIX + sanitize(id) + SUFFIX)

    fun exists(id: String): Boolean = fileFor(id).exists()

    /** The raw layout text of a slot, or null when it is absent or unreadable. */
    fun read(id: String): String? = try {
        val file = fileFor(id)
        if (file.exists()) file.readText() else null
    } catch (_: Throwable) {
        null
    }

    /** Writes one slot atomically; false when the bytes could not be put in place. */
    fun write(id: String, text: String): Boolean = try {
        if (!dir.exists()) dir.mkdirs()
        writeAtomically(fileFor(id), text)
    } catch (_: Throwable) {
        false
    }

    fun delete(id: String): Boolean = try {
        fileFor(id).delete()
    } catch (_: Throwable) {
        false
    }

    /** Every slot that exists on disk, numbered order first, then named layouts. */
    fun list(nameOf: (String, String) -> String): List<LayoutSlot> {
        val named = ArrayList<LayoutSlot>()
        val numbered = ArrayList<LayoutSlot>()
        val files = dir.listFiles() ?: return emptyList()
        for (file in files) {
            if (!file.name.startsWith(PREFIX) || !file.name.endsWith(SUFFIX)) continue
            val id = file.name.removePrefix(PREFIX).removeSuffix(SUFFIX)
            if (id.isEmpty()) continue
            val text = try {
                file.readText()
            } catch (_: Throwable) {
                continue
            }
            val label = nameOf(id, text).ifEmpty { prettyId(id) }
            val slot = LayoutSlot(id, label, id.toIntOrNull() in 1..MAX_NUMBERED)
            if (slot.numbered) numbered.add(slot) else named.add(slot)
        }
        numbered.sortBy { it.id.toIntOrNull() ?: Int.MAX_VALUE }
        named.sortBy { it.displayName.lowercase() }
        return numbered + named
    }

    /** The first numbered slot with no file in it, or null when all five are used. */
    fun freeNumberedId(): String? = numberedIds().firstOrNull { !exists(it) }

    /**
     * The five numbered places as the "Save Layout" picker shows them: every
     * slot, in order, with the name of whatever is already saved in it and
     * whether choosing it would replace that layout.
     *
     * A slot counts as taken when its file exists, even if the file cannot be
     * read: a save that ran over something this build could not parse is the
     * player's decision to make, not one to take behind their back.
     */
    fun saveChoices(nameOf: (String, String) -> String): List<SaveSlotChoice> =
        numberedIds().map { id ->
            val text = read(id)
            SaveSlotChoice(
                id,
                if (text == null) "" else nameOf(id, text).ifEmpty { prettyId(id) },
                exists(id),
            )
        }

    /** How many named (non-numbered) layouts exist. */
    fun namedCount(): Int = list({ _, _ -> "" }).count { !it.numbered }

    /**
     * Folds the profiles the old build wrote into slots.
     *
     * The old file name carried the name, so it is recovered from it and used
     * as the slot's display name before the file is moved. Nothing is deleted
     * until its replacement has been written, so a failure half way through
     * loses nothing.
     */
    fun migrateLegacy(rename: (String, String) -> String): Int {
        val files = dir.listFiles() ?: return 0
        var moved = 0
        for (file in files) {
            if (!file.name.startsWith(LEGACY_PREFIX) || !file.name.endsWith(SUFFIX)) continue
            val oldId = file.name.removePrefix(LEGACY_PREFIX).removeSuffix(SUFFIX)
            if (oldId.isEmpty()) continue
            if (exists(oldId)) continue
            val text = try {
                file.readText()
            } catch (_: Throwable) {
                continue
            }
            val named = if (oldId.toIntOrNull() in 1..MAX_NUMBERED) text else rename(oldId, text)
            if (write(oldId, named)) {
                file.delete()
                moved++
            }
        }
        return moved
    }

    companion object {
        /** Files this store owns, and the prefix the old build used. */
        const val PREFIX = "controls_slot_"
        const val LEGACY_PREFIX = "controls_profile_"
        const val SUFFIX = ".json"

        /** The sibling a layout is written to before it is moved into place. */
        const val PART_SUFFIX = ".part"

        /** Five numbered slots: enough alternatives to cover a phone's modes. */
        const val MAX_NUMBERED = 5

        /** Longest id derived from a name, so no file name can grow unbounded. */
        private const val MAX_ID = 40

        /**
         * A safe file id for a name. Lower-cased, runs of anything that is not a
         * letter, digit, dash or underscore collapsed to one underscore, then
         * trimmed. Dots are refused outright, so no id can walk out of the app's
         * own folder.
         */
        /**
         * Writes [text] to [file] by way of a sibling temporary file.
         *
         * A reader can never see half a layout: the bytes go to `<name>.part`,
         * are flushed to the device, and are then moved over the target in one
         * step. If the move is refused, the target is removed *before* the move
         * is tried again rather than being written in place -- the window where
         * nothing exists is better than the window where half a file does, and a
         * crash in either leaves the previous layout readable or nothing at all,
         * never a truncated one. The temporary file never survives a failure.
         */
        fun writeAtomically(file: File, text: String): Boolean {
            val parent = file.absoluteFile.parentFile ?: return false
            if (!parent.exists() && !parent.mkdirs()) return false
            val temporary = File(parent, file.name + PART_SUFFIX)
            try {
                FileOutputStream(temporary).use { out ->
                    out.write(text.toByteArray(Charsets.UTF_8))
                    out.flush()
                    // Best effort: the rename is what makes the write atomic, and
                    // a file system that will not sync is still allowed to save.
                    try {
                        out.fd.sync()
                    } catch (_: Throwable) {
                    }
                }
            } catch (_: Throwable) {
                temporary.delete()
                return false
            }
            if (temporary.renameTo(file)) return true
            if (file.exists() && !file.delete()) {
                temporary.delete()
                return false
            }
            if (temporary.renameTo(file)) return true
            temporary.delete()
            return false
        }

        fun sanitize(name: String): String {
            val collapsed = name.trim().lowercase()
                .replace(Regex("[^a-z0-9_-]+"), "_")
                .trim('_', '-')
            return collapsed.take(MAX_ID).ifEmpty { "layout" }
        }

        /** A display name recovered from an id, for a file that carries none. */
        fun prettyId(id: String): String =
            id.replace('_', ' ').replace('-', ' ').trim()
                .split(' ')
                .filter { it.isNotEmpty() }
                .joinToString(" ") { word ->
                    word.replaceFirstChar { it.uppercase() }
                }
                .ifEmpty { id }
    }
}
