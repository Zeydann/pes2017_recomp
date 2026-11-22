package com.pes2017.recomp

import java.io.Closeable
import java.io.EOFException
import java.io.File
import java.io.OutputStream
import java.io.RandomAccessFile
import java.security.MessageDigest

/**
 * Xbox 360 STFS content packages: the CON, LIVE and PIRS containers a data pack
 * is downloaded as.
 *
 * The title's own content packages are directories here (the save system in
 * `kernel/content.cpp` mounts a directory, not a container), but a patch from
 * the community is a real STFS file. Reading one means finding the inner file
 * table through the hash-table layout and then streaming the inner files —
 * which is what this does. Every block is checked against the SHA-1 stored in
 * its level-0 table before it is used, so a wrong layout or a truncated
 * download fails instead of producing a wrong CPK.
 *
 * The layouts (`0xAD0E` for DLC packages, `0x971A` for saves) follow the same
 * STFS format the PES 2018 tooling documents; PES 2017's packs use the same
 * container format.
 */

/** STFS block size. */
const val STFS_BLOCK_SIZE: Int = 0x1000

/** Data blocks covered by one level-0 hash table. */
const val STFS_PER_TABLE: Int = 0xAA

/** Data blocks covered by one level-1 hash table. */
const val STFS_PER_LEVEL1: Int = 0x70E4

/** Largest 24-bit data block number. */
const val STFS_MAX_BLOCK: Int = 0xFFFFFF

enum class StfsErrorKind {
    UNKNOWN_LAYOUT,
    TRUNCATED,
    HASH_MISMATCH,
    MALFORMED,
    UNSUPPORTED_LIMIT,
}

class StfsException(
    message: String,
    val kind: StfsErrorKind = StfsErrorKind.MALFORMED,
    cause: Throwable? = null,
) : java.io.IOException(message, cause)

/** The two STFS hash-table layouts used by this title's content. */
enum class StfsLayout(
    val headerSize: Int,
    val headerBlocks: Int,
    val topTables: Int,
    val duplicateTables: Int,
) {
    PERSISTENT(0xAD0E, 11, 1, 1),
    SAVE(0x971A, 10, 2, 2),
    ;

    /** First data block (block 0) offset. */
    val dataBase: Long
        get() = (headerBlocks + topTables).toLong() * STFS_BLOCK_SIZE

    /** Hash-table blocks inserted between data block 0 and [block]. */
    fun tablesBefore(block: Int): Long {
        if (block < 0 || block > STFS_MAX_BLOCK) {
            throw StfsException(
                "data block ${block.toString(16)} is outside the 24-bit range",
                StfsErrorKind.MALFORMED,
            )
        }
        var tables = 0L
        if (block >= STFS_PER_TABLE) {
            tables += (block / STFS_PER_TABLE + 1).toLong() * duplicateTables
        }
        if (block >= STFS_PER_LEVEL1) {
            if (this == SAVE) throw unsupportedSaveLimit(block)
            tables += (block / STFS_PER_LEVEL1 + 1).toLong() * duplicateTables
        }
        return tables
    }

    /** File offset of data block [block]. */
    fun offset(block: Int): Long {
        if (this == SAVE && block >= STFS_PER_LEVEL1) throw unsupportedSaveLimit(block)
        return dataBase + (block + tablesBefore(block)) * STFS_BLOCK_SIZE
    }

    /**
     * File offset of the level-0 table that hashes [block]. Group 0's primary
     * table is the last top table; later groups are preceded by their tables.
     */
    fun l0Offset(block: Int): Long {
        if (block < 0 || block > STFS_MAX_BLOCK) {
            throw StfsException(
                "data block ${block.toString(16)} is outside the 24-bit range",
                StfsErrorKind.MALFORMED,
            )
        }
        val group = block / STFS_PER_TABLE
        return if (group == 0) {
            headerBlocks.toLong() * STFS_BLOCK_SIZE
        } else {
            offset(group * STFS_PER_TABLE) - duplicateTables.toLong() * STFS_BLOCK_SIZE
        }
    }

    private fun unsupportedSaveLimit(block: Int): StfsException = StfsException(
        "0x971A data block ${block.toString(16)} is at or beyond 0x70E4 " +
            "($STFS_PER_LEVEL1); that supergroup term is not verified",
        StfsErrorKind.UNSUPPORTED_LIMIT,
    )

    companion object {
        fun forHeader(headerSize: Int): StfsLayout =
            entries.firstOrNull { it.headerSize == headerSize } ?: throw StfsException(
                "unknown STFS hash layout header 0x${headerSize.toString(16)} " +
                    "(supported: " + entries.joinToString { "0x${it.headerSize.toString(16)}" } +
                    "); refusing to guess a table structure",
                StfsErrorKind.UNKNOWN_LAYOUT,
            )
    }
}

/** True when [magic] is one of the three STFS container magics. */
fun isStfsMagic(magic: String): Boolean =
    magic == "CON " || magic == "LIVE" || magic == "PIRS"

internal fun dataBlockOffset(reader: StfsReader, layout: StfsLayout, block: Int): Long {
    val offset = layout.offset(block)
    if (offset < 0 || offset + STFS_BLOCK_SIZE > reader.size) {
        throw StfsException(
            "data block ${block.toString(16)} at 0x${offset.toString(16)} is past " +
                "the end of the package (0x${reader.size.toString(16)})",
            StfsErrorKind.TRUNCATED,
        )
    }
    return offset
}

/** Bounded random access to a package. */
internal interface StfsReader {
    val size: Long

    /** Reads exactly [length] bytes at [offset] or throws [StfsException]. */
    fun read(offset: Long, length: Int): ByteArray
}

internal class FileStfsReader(private val file: RandomAccessFile) : StfsReader {
    override val size: Long = file.length()

    override fun read(offset: Long, length: Int): ByteArray {
        if (offset < 0 || length < 0 || offset + length > size) {
            throw StfsException(
                "short read at 0x${offset.toString(16)} ($length bytes requested, " +
                    "0x$size package size)",
                StfsErrorKind.TRUNCATED,
            )
        }
        val out = ByteArray(length)
        try {
            file.seek(offset)
            file.readFully(out)
        } catch (e: EOFException) {
            throw StfsException(
                "short read at 0x${offset.toString(16)} ($length bytes requested)",
                StfsErrorKind.TRUNCATED,
                e,
            )
        }
        return out
    }
}

/** One 0x40-byte entry of the STFS file table. */
data class StfsEntry(
    val index: Int,
    val name: String,
    val flags: Int,
    val consecutive: Boolean,
    val directory: Boolean,
    val blocks: Int,
    val start: Int,
    val parent: Int,
    val size: Long,
)

/** A parsed and sanity-checked STFS header and file table. */
data class StfsHeader(
    val magic: String,
    val layout: StfsLayout,
    val contentType: Long,
    val metadataVersion: Long,
    val titleId: Long,
    val displayName: String,
    val blockSeparation: Int,
    val fileTableBlocks: Int,
    val fileTableBlock: Int,
    val allocatedBlocks: Long,
    val entries: List<StfsEntry>,
) {
    /** Full path of [entry], following parent indexes. */
    fun pathOf(entry: StfsEntry): String {
        val parts = ArrayList<String>()
        val seen = HashSet<Int>()
        var current: StfsEntry? = entry
        while (current != null) {
            if (!seen.add(current.index)) {
                throw StfsException(
                    "bad parent chain at entry ${current.index} (${current.name})",
                    StfsErrorKind.MALFORMED,
                )
            }
            parts.add(current.name)
            current = if (current.parent >= 0) {
                entries.getOrNull(current.parent) ?: throw StfsException(
                    "bad parent index ${current.parent} on ${current.name}",
                    StfsErrorKind.MALFORMED,
                )
            } else {
                null
            }
        }
        parts.reverse()
        return parts.joinToString("/")
    }

    fun find(path: String): StfsEntry? =
        entries.firstOrNull { !it.directory && pathOf(it) == path }

    fun findNamed(name: String): StfsEntry? =
        entries.firstOrNull { !it.directory && it.name.equals(name, ignoreCase = true) }
}

internal object StfsHeaderParser {

    private const val HEADER_READ = 0x1000
    private const val ENTRY_SIZE = 0x40
    private const val MAX_NAME = 0x28
    private const val MAX_FILE_TABLE_BLOCKS = 16

    fun parse(reader: StfsReader): StfsHeader {
        val head = reader.read(0, HEADER_READ)
        val magic = String(head, 0, 4, Charsets.ISO_8859_1)
        if (!isStfsMagic(magic)) {
            throw StfsException("not an STFS package (magic $magic)", StfsErrorKind.MALFORMED)
        }

        val headerSize = be32(head, 0x340).toInt()
        val layout = StfsLayout.forHeader(headerSize)
        val expectedHeaderBlocks = (layout.headerSize + STFS_BLOCK_SIZE - 1) / STFS_BLOCK_SIZE
        if (expectedHeaderBlocks != layout.headerBlocks) {
            throw StfsException(
                "STFS header 0x${headerSize.toString(16)} needs " +
                    "$expectedHeaderBlocks header blocks, layout says ${layout.headerBlocks}",
                StfsErrorKind.MALFORMED,
            )
        }

        val contentType = be32(head, 0x344)
        val metadataVersion = be32(head, 0x348)
        val titleId = be32(head, 0x360)
        val displayName = parseDisplayName(head)
        val volumeDescriptorSize = head[0x379].toInt() and 0xFF
        if (volumeDescriptorSize != 0x24) {
            throw StfsException(
                "volume descriptor size 0x${volumeDescriptorSize.toString(16)}, expected 0x24",
                StfsErrorKind.MALFORMED,
            )
        }
        val blockSeparation = head[0x37B].toInt() and 0xFF
        val fileTableBlocks = le16(head, 0x37C)
        val fileTableBlock = le24(head, 0x37E)
        val allocatedBlocks = be32(head, 0x395)
        if (fileTableBlocks == 0) {
            throw StfsException("file table block count is zero", StfsErrorKind.MALFORMED)
        }
        if (fileTableBlocks > MAX_FILE_TABLE_BLOCKS) {
            throw StfsException(
                "file table block count $fileTableBlocks exceeds the " +
                    "$MAX_FILE_TABLE_BLOCKS-block sanity limit",
                StfsErrorKind.MALFORMED,
            )
        }
        if (allocatedBlocks == 0L) {
            throw StfsException("allocated block count is zero", StfsErrorKind.MALFORMED)
        }
        if (layout == StfsLayout.SAVE && allocatedBlocks > STFS_PER_LEVEL1) {
            throw StfsException(
                "0x971A package has $allocatedBlocks data blocks; the layout is only " +
                    "verified below 0x70E4 ($STFS_PER_LEVEL1)",
                StfsErrorKind.UNSUPPORTED_LIMIT,
            )
        }

        // SHA-1(0x344 .. first hash table) proves the header extent and the
        // number of top tables.
        val firstTable = layout.dataBase - layout.topTables.toLong() * STFS_BLOCK_SIZE
        val storedMaster = reader.read(0x32C, 20)
        val actualMaster = sha1(reader.read(0x344, (firstTable - 0x344).toInt()))
        if (!MessageDigest.isEqual(storedMaster, actualMaster)) {
            throw StfsException(
                "header SHA-1 at 0x32C does not cover 0x344..0x" +
                    firstTable.toString(16) + "; wrong layout or corrupt header",
                StfsErrorKind.HASH_MISMATCH,
            )
        }

        if (fileTableBlock.toLong() + fileTableBlocks > allocatedBlocks) {
            throw StfsException(
                "file table blocks $fileTableBlock+$fileTableBlocks exceed " +
                    "$allocatedBlocks allocated blocks",
                StfsErrorKind.MALFORMED,
            )
        }
        val tableBytes = ByteArray(fileTableBlocks * STFS_BLOCK_SIZE)
        for (i in 0 until fileTableBlocks) {
            val blockOffset = dataBlockOffset(reader, layout, fileTableBlock + i)
            reader.read(blockOffset, STFS_BLOCK_SIZE).copyInto(tableBytes, i * STFS_BLOCK_SIZE)
        }
        val entries = parseFileTable(tableBytes, allocatedBlocks)
        if (entries.isEmpty()) {
            throw StfsException("STFS file table is empty", StfsErrorKind.MALFORMED)
        }
        validateEntries(entries, allocatedBlocks)

        // Prove the table positions by hashing data block 0.
        val l0Offset = layout.l0Offset(0)
        val storedBlock0 = reader.read(l0Offset, 0x18)
        val block0 = reader.read(dataBlockOffset(reader, layout, 0), STFS_BLOCK_SIZE)
        val actualBlock0 = sha1(block0)
        if (!MessageDigest.isEqual(storedBlock0.copyOfRange(0, 20), actualBlock0)) {
            throw StfsException(
                "level-0 table at 0x${l0Offset.toString(16)} does not hash data " +
                    "block 0; layout 0x${headerSize.toString(16)} does not match this " +
                    "package",
                StfsErrorKind.HASH_MISMATCH,
            )
        }

        return StfsHeader(
            magic = magic,
            layout = layout,
            contentType = contentType,
            metadataVersion = metadataVersion,
            titleId = titleId,
            displayName = displayName,
            blockSeparation = blockSeparation,
            fileTableBlocks = fileTableBlocks,
            fileTableBlock = fileTableBlock,
            allocatedBlocks = allocatedBlocks,
            entries = entries,
        )
    }

    private fun parseDisplayName(head: ByteArray): String {
        if (head.size < 0x512) return ""
        val raw = head.copyOfRange(0x411, 0x511)
        val text = String(raw, Charsets.UTF_16BE)
        return text.substringBefore('\u0000').trim()
    }

    private fun parseFileTable(table: ByteArray, allocatedBlocks: Long): List<StfsEntry> {
        val entries = ArrayList<StfsEntry>()
        var offset = 0
        while (offset + ENTRY_SIZE <= table.size) {
            val raw = table.copyOfRange(offset, offset + ENTRY_SIZE)
            if (raw[0].toInt() == 0) break
            val flags = raw[0x28].toInt() and 0xFF
            val nameLength = flags and 0x3F
            if (nameLength == 0 || nameLength > MAX_NAME) {
                throw StfsException(
                    "invalid name length $nameLength at file table entry ${entries.size}",
                    StfsErrorKind.MALFORMED,
                )
            }
            val name = String(raw, 0, nameLength, Charsets.ISO_8859_1)
            entries.add(
                StfsEntry(
                    index = entries.size,
                    name = name,
                    flags = flags,
                    consecutive = (flags and 0x40) != 0,
                    directory = (flags and 0x80) != 0,
                    blocks = le24(raw, 0x29),
                    start = le24(raw, 0x2F),
                    parent = be16s(raw, 0x32),
                    size = be32(raw, 0x34),
                ),
            )
            offset += ENTRY_SIZE
        }
        return entries
    }

    private fun validateEntries(entries: List<StfsEntry>, allocatedBlocks: Long) {
        entries.forEach { entry ->
            if (entry.parent < -1 || entry.parent >= entries.size) {
                throw StfsException(
                    "bad parent index ${entry.parent} on ${entry.name}",
                    StfsErrorKind.MALFORMED,
                )
            }
            if (entry.directory) return@forEach
            if (entry.blocks == 0 && entry.size > 0) {
                throw StfsException(
                    "${entry.name}: ${entry.size} bytes but no data blocks",
                    StfsErrorKind.MALFORMED,
                )
            }
            if (entry.blocks > 0) {
                if (entry.start.toLong() >= allocatedBlocks) {
                    throw StfsException(
                        "${entry.name}: start block ${entry.start} is beyond " +
                            "$allocatedBlocks allocated blocks",
                        StfsErrorKind.MALFORMED,
                    )
                }
                if (entry.consecutive && entry.start.toLong() + entry.blocks > allocatedBlocks) {
                    throw StfsException(
                        "${entry.name}: blocks ${entry.start}.." +
                            "${entry.start + entry.blocks - 1} exceed " +
                            "$allocatedBlocks allocated blocks",
                        StfsErrorKind.MALFORMED,
                    )
                }
                if (entry.size > entry.blocks.toLong() * STFS_BLOCK_SIZE) {
                    throw StfsException(
                        "${entry.name}: size ${entry.size} exceeds ${entry.blocks} " +
                            "blocks (${entry.blocks * STFS_BLOCK_SIZE} bytes)",
                        StfsErrorKind.MALFORMED,
                    )
                }
            }
        }
    }
}

/** What [StfsPackage.readEntry] produced. */
data class StfsRead(
    val bytes: Long,
    val blocks: Int,
    val sha1: String,
)

/** A read-only STFS package backed by a file. */
class StfsPackage private constructor(
    private val file: RandomAccessFile,
    private val reader: FileStfsReader,
    val header: StfsHeader,
) : Closeable {

    private var closed = false

    private val blockCache = object : LinkedHashMap<Int, ByteArray>(8, 0.75f, true) {
        override fun removeEldestEntry(
            eldest: MutableMap.MutableEntry<Int, ByteArray>?,
        ): Boolean = size > 8
    }

    val layout: StfsLayout get() = header.layout
    val titleId: Long get() = header.titleId
    val displayName: String get() = header.displayName
    val entries: List<StfsEntry> get() = header.entries

    fun entryPath(entry: StfsEntry): String = header.pathOf(entry)

    fun find(path: String): StfsEntry? = header.find(path)

    fun findNamed(name: String): StfsEntry? = header.findNamed(name)

    /** Streams [entry] into [sink], verifying every block against its L0 SHA-1. */
    fun readEntry(entry: StfsEntry, sink: OutputStream, verify: Boolean = true): StfsRead {
        if (closed) throw StfsException("package is closed", StfsErrorKind.MALFORMED)
        if (entry.directory) {
            throw StfsException("${entry.name} is a directory", StfsErrorKind.MALFORMED)
        }

        val digest = MessageDigest.getInstance("SHA-1")
        var remaining = entry.size
        var blocks = 0
        if (entry.blocks == 0) {
            if (entry.size != 0L) {
                throw StfsException(
                    "${entry.name}: ${entry.size} bytes but no data blocks",
                    StfsErrorKind.MALFORMED,
                )
            }
            return StfsRead(0, 0, sha1(ByteArray(0)).toHex())
        }

        var block = entry.start
        val seen = HashSet<Int>()
        for (i in 0 until entry.blocks) {
            if (!seen.add(block)) {
                throw StfsException(
                    "${entry.name}: block chain loops at 0x${block.toString(16)}",
                    StfsErrorKind.MALFORMED,
                )
            }
            val data = readBlock(block, verify)
            val take = minOf(STFS_BLOCK_SIZE.toLong(), remaining).toInt()
            if (take > 0) {
                sink.write(data, 0, take)
                digest.update(data, 0, take)
            }
            blocks++
            remaining -= take
            if (remaining <= 0) break
            block = if (entry.consecutive) block + 1 else nextBlock(entry, block)
        }
        if (remaining != 0L) {
            throw StfsException(
                "${entry.name}: block chain ended $remaining bytes early",
                StfsErrorKind.TRUNCATED,
            )
        }
        return StfsRead(entry.size, blocks, digest.digest().toHex())
    }

    /** Reads at most [maxBytes] from the start of [entry], verifying each block. */
    fun readPrefix(entry: StfsEntry, maxBytes: Int, verify: Boolean = true): ByteArray {
        if (closed) throw StfsException("package is closed", StfsErrorKind.MALFORMED)
        if (entry.directory) {
            throw StfsException("${entry.name} is a directory", StfsErrorKind.MALFORMED)
        }
        val wanted = minOf(entry.size, maxBytes.toLong()).toInt()
        if (wanted <= 0) return ByteArray(0)
        val out = ByteArray(wanted)
        var written = 0
        var block = entry.start
        var index = 0
        while (written < wanted && index < entry.blocks) {
            val data = readBlock(block, verify)
            val take = minOf(STFS_BLOCK_SIZE, wanted - written)
            data.copyInto(out, written, 0, take)
            written += take
            index++
            if (written >= wanted) break
            block = if (entry.consecutive) block + 1 else nextBlock(entry, block)
        }
        if (written < wanted) {
            throw StfsException(
                "${entry.name}: only $written of $wanted bytes are addressable",
                StfsErrorKind.TRUNCATED,
            )
        }
        return out
    }

    private fun readBlock(block: Int, verify: Boolean): ByteArray {
        if (block < 0 || block > STFS_MAX_BLOCK) {
            throw StfsException(
                "data block ${block.toString(16)} is outside the 24-bit range",
                StfsErrorKind.MALFORMED,
            )
        }
        var data = blockCache[block]
        if (data == null) {
            data = reader.read(dataBlockOffset(reader, layout, block), STFS_BLOCK_SIZE)
            blockCache[block] = data
        }
        if (verify) {
            val l0 = l0Entry(block)
            val actual = sha1(data)
            if (!MessageDigest.isEqual(l0.sha1, actual)) {
                throw StfsException(
                    "block 0x${block.toString(16)} SHA-1 mismatch: table " +
                        "${l0.sha1.toHex()}, actual ${actual.toHex()}",
                    StfsErrorKind.HASH_MISMATCH,
                )
            }
        }
        return data
    }

    private fun nextBlock(entry: StfsEntry, block: Int): Int {
        val next = l0Entry(block).nextBlock
        if (next == STFS_MAX_BLOCK) {
            throw StfsException(
                "${entry.name}: chain ends early after block 0x${block.toString(16)}",
                StfsErrorKind.TRUNCATED,
            )
        }
        if (next.toLong() >= header.allocatedBlocks) {
            throw StfsException(
                "${entry.name}: next block 0x${next.toString(16)} is beyond " +
                    "${header.allocatedBlocks} allocated blocks",
                StfsErrorKind.MALFORMED,
            )
        }
        return next
    }

    private fun l0Entry(block: Int): L0Entry {
        val table = layout.l0Offset(block)
        val offset = table + (block % STFS_PER_TABLE).toLong() * 0x18
        val raw = reader.read(offset, 0x18)
        return L0Entry(
            sha1 = raw.copyOfRange(0, 20),
            status = raw[0x14].toInt() and 0xFF,
            nextBlock = be24(raw, 0x15),
        )
    }

    override fun close() {
        if (closed) return
        closed = true
        blockCache.clear()
        file.close()
    }

    private data class L0Entry(
        val sha1: ByteArray,
        val status: Int,
        val nextBlock: Int,
    )

    companion object {
        fun open(file: File): StfsPackage {
            if (!file.isFile) {
                throw StfsException("no such package: ${file.path}", StfsErrorKind.TRUNCATED)
            }
            val raf = RandomAccessFile(file, "r")
            try {
                val reader = FileStfsReader(raf)
                val header = StfsHeaderParser.parse(reader)
                return StfsPackage(raf, reader, header)
            } catch (t: Throwable) {
                try {
                    raf.close()
                } catch (_: Throwable) {
                }
                throw t
            }
        }
    }
}

// ---- small readers and endian helpers ------------------------------------

internal fun sha1(bytes: ByteArray): ByteArray =
    MessageDigest.getInstance("SHA-1").digest(bytes)

internal fun ByteArray.toHex(): String {
    val chars = CharArray(size * 2)
    for (i in indices) {
        val value = this[i].toInt() and 0xFF
        chars[i * 2] = HEX[value ushr 4]
        chars[i * 2 + 1] = HEX[value and 0x0F]
    }
    return String(chars)
}

private val HEX = "0123456789abcdef".toCharArray()

internal fun be16(bytes: ByteArray, offset: Int): Int =
    ((bytes[offset].toInt() and 0xFF) shl 8) or (bytes[offset + 1].toInt() and 0xFF)

internal fun be16s(bytes: ByteArray, offset: Int): Int =
    be16(bytes, offset).let { if (it >= 0x8000) it - 0x10000 else it }

internal fun be24(bytes: ByteArray, offset: Int): Int =
    ((bytes[offset].toInt() and 0xFF) shl 16) or
        ((bytes[offset + 1].toInt() and 0xFF) shl 8) or
        (bytes[offset + 2].toInt() and 0xFF)

internal fun be32(bytes: ByteArray, offset: Int): Long =
    ((bytes[offset].toLong() and 0xFF) shl 24) or
        ((bytes[offset + 1].toLong() and 0xFF) shl 16) or
        ((bytes[offset + 2].toLong() and 0xFF) shl 8) or
        (bytes[offset + 3].toLong() and 0xFF)

internal fun le16(bytes: ByteArray, offset: Int): Int =
    (bytes[offset].toInt() and 0xFF) or ((bytes[offset + 1].toInt() and 0xFF) shl 8)

internal fun le24(bytes: ByteArray, offset: Int): Int =
    (bytes[offset].toInt() and 0xFF) or
        ((bytes[offset + 1].toInt() and 0xFF) shl 8) or
        ((bytes[offset + 2].toInt() and 0xFF) shl 16)

internal fun le32(bytes: ByteArray, offset: Int): Long =
    (bytes[offset].toLong() and 0xFF) or
        ((bytes[offset + 1].toLong() and 0xFF) shl 8) or
        ((bytes[offset + 2].toLong() and 0xFF) shl 16) or
        ((bytes[offset + 3].toLong() and 0xFF) shl 24)
