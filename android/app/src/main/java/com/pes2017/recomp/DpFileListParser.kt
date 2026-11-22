package com.pes2017.recomp

/** One 48-byte DpFileList record. */
data class DpFileListEntry(
    val index: Int,
    val priority: Int,
    val unknown0: Int,
    val unknown1: Int,
    val name: String,
)

/**
 * Parser for the data pack's `DpFileList.bin`: the list that orders the CPKs.
 *
 * Records are 48 bytes: u32 BE magic 0x00000262, priority, 100, 101 and a
 * NUL-padded 32-byte Latin-1 name. The file is zero-padded after the last
 * record, so parsing stops at the first all-zero record instead of dividing
 * the size by 48 (real lists are not a multiple of 48).
 *
 * [parseOrNull] returns null when the file is not in this format at all. That
 * is deliberate: the container this app installs into is the same one the
 * title reads, but a patch tool may ship an older or differently ordered list,
 * and refusing to install a pack because its list is not understood would be
 * worse than keeping the file as it is (the scanner reports it as a warning).
 */
object DpFileListParser {

    const val RECORD_SIZE: Int = 48
    const val MAGIC: Long = 0x00000262
    const val UNKNOWN0: Long = 100
    const val UNKNOWN1: Long = 101

    /** Largest list this app reads; a real one is a few KB. */
    const val MAX_BYTES: Int = 64 * 1024

    class Malformed(message: String) : Exception(message)

    fun parse(bytes: ByteArray): List<DpFileListEntry> =
        parseOrNull(bytes) ?: throw Malformed("DpFileList.bin has no 0x262 records")

    fun parseOrNull(bytes: ByteArray): List<DpFileListEntry>? {
        if (bytes.size < RECORD_SIZE) return null
        val entries = ArrayList<DpFileListEntry>()
        var offset = 0
        while (offset + RECORD_SIZE <= bytes.size) {
            val magic = be32(bytes, offset)
            val priority = be32(bytes, offset + 4)
            val unknown0 = be32(bytes, offset + 8)
            val unknown1 = be32(bytes, offset + 12)
            if (magic == 0L && priority == 0L && unknown0 == 0L && unknown1 == 0L) break
            if (magic != MAGIC) {
                if (entries.isEmpty()) return null
                throw Malformed(
                    "DpFileList record ${entries.size} has magic " +
                        "0x${magic.toString(16)}, expected 0x${MAGIC.toString(16)}",
                )
            }
            if (unknown0 != UNKNOWN0 || unknown1 != UNKNOWN1) {
                throw Malformed(
                    "DpFileList record ${entries.size} has fields " +
                        "$unknown0/$unknown1, expected $UNKNOWN0/$UNKNOWN1",
                )
            }
            val nameBytes = bytes.copyOfRange(offset + 16, offset + RECORD_SIZE)
            val name = String(nameBytes, Charsets.ISO_8859_1).substringBefore('\u0000')
            if (name.isEmpty()) {
                throw Malformed("DpFileList record ${entries.size} has an empty name")
            }
            if (!isSafePatchFileName(name) || !name.lowercase().endsWith(".cpk")) {
                throw Malformed(
                    "DpFileList record ${entries.size} has an unsafe or non-CPK " +
                        "name: $name",
                )
            }
            entries.add(
                DpFileListEntry(
                    index = entries.size,
                    priority = priority.toInt(),
                    unknown0 = unknown0.toInt(),
                    unknown1 = unknown1.toInt(),
                    name = name,
                ),
            )
            offset += RECORD_SIZE
        }
        if (entries.isEmpty()) return null
        return entries
    }
}

/** A file name that stays inside the folder it is joined to. */
internal fun isSafePatchFileName(name: String): Boolean =
    name.isNotEmpty() && name != "." && name != ".." &&
        !name.contains('/') && !name.contains('\\') && !name.contains(':')

/** The first 0x24 bytes of a save's data.bin, read leniently. */
data class SavePayload(
    val version: Int,
    val headerSize: Int,
    val payloadSize: Long,
    val players: Int,
    val teams: Int,
)

/**
 * Reads the header a save's `data.bin` starts with.
 *
 * Only the self-describing fields are checked (header size, payload size
 * against the file size). Player and team capacities differ between patches
 * and between PES 2017 versions, so the counts are reported, not enforced:
 * refusing a save the title would accept is worse than installing it.
 */
object SavePayloadParser {

    const val HEADER_SIZE: Int = 80
    const val MIN_HEADER_BYTES: Int = 0x24

    fun parse(header: ByteArray, fileSize: Long): SavePayload? {
        if (header.size < MIN_HEADER_BYTES || fileSize < HEADER_SIZE) return null
        val headerSize = be32(header, 4)
        if (headerSize != HEADER_SIZE.toLong()) return null
        val payloadSize = be32(header, 8)
        if (payloadSize != fileSize - HEADER_SIZE) return null
        return SavePayload(
            version = le32(header, 0).toInt(),
            headerSize = headerSize.toInt(),
            payloadSize = payloadSize,
            players = be32(header, 0x0C).toInt(),
            teams = be32(header, 0x10).toInt(),
        )
    }
}
