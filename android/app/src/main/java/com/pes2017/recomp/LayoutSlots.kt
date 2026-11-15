package com.pes2017.recomp

import java.io.File

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

    fun write(id: String, text: String): Boolean = try {
        if (!dir.exists()) dir.mkdirs()
        val file = fileFor(id)
        // Written beside and moved into place: a layout being saved as the
        // process dies must not leave a file the next start cannot read.
        val temporary = File(dir, file.name + ".part")
        temporary.writeText(text)
        if (!temporary.renameTo(file)) {
            file.writeText(text)
            temporary.delete()
        }
        true
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
