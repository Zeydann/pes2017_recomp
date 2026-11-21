package com.pes2017.recomp

import android.content.Context
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.system.Os
import android.system.OsConstants
import me.zhanghai.android.libarchive.Archive
import me.zhanghai.android.libarchive.ArchiveEntry
import java.io.File
import java.io.InputStream
import java.nio.ByteBuffer
import java.util.Locale

/**
 * INSTALL PATCH: one archive (.zip, .rar or .7z), or every part of one
 * multi-part RAR, read with libarchive -- the same library and the same
 * version as the PES 2018 import screen, because the Android runtime has no
 * RAR or 7Z reader of its own and `java.util.zip` cannot decrypt a password.
 *
 * The password is kept for this install only: it is never written to disk and
 * never stored with the plan. A wrong one comes back as [PatchSourceFailure.PASSWORD],
 * so the screen can ask again instead of calling the archive corrupt.
 */
class PatchArchiveSource private constructor(
    private val context: Context?,
    private val paths: List<String>?,
    private val uri: Uri?,
    override val label: String,
    override val kind: PatchSourceKind,
    override val partCount: Int,
    private val password: String?,
) : PatchSource {

    private var closed = false

    override fun openSession(): PatchSession {
        if (closed) throw PatchSourceException("the source is already closed", PatchSourceFailure.IO)
        return Session()
    }

    override fun close() {
        closed = true
    }

    private inner class Session : PatchSession {
        var archive: Long = 0
        private var descriptor: ParcelFileDescriptor? = null
        private var currentOpen = false
        var currentPath = ""
        private var closing = false
        private var stream: EntryStream? = null

        init {
            archive = Archive.readNew()
            try {
                Archive.readSupportFilterAll(archive)
                Archive.readSupportFormatAll(archive)
                password?.takeIf { it.isNotEmpty() }?.let {
                    Archive.readAddPassphrase(archive, it.toByteArray(Charsets.UTF_8))
                }
                val pathList = paths
                if (pathList != null) {
                    Archive.readOpenFileNames(
                        archive,
                        pathList.map { it.toByteArray(Charsets.UTF_8) }.toTypedArray(),
                        READ_BLOCK,
                    )
                } else {
                    val target = uri ?: throw PatchSourceException(
                        "no archive was opened",
                        PatchSourceFailure.IO,
                    )
                    val opened = context?.contentResolver?.openFileDescriptor(target, "r")
                        ?: throw PatchSourceException(
                            "cannot open $label",
                            PatchSourceFailure.IO,
                        )
                    descriptor = opened
                    // Os.lseek takes the FileDescriptor, while libarchive's
                    // readOpenFd takes the raw int: the two are not the same
                    // and swapping them does not compile.
                    runCatching { Os.lseek(opened.fileDescriptor, 0, OsConstants.SEEK_SET) }
                    Archive.readOpenFd(archive, opened.fd, READ_BLOCK)
                }
            } catch (error: Throwable) {
                close()
                throw archiveFailure("cannot open $label", error)
            }
        }

        override fun next(): PatchEntry? {
            if (closing) return null
            stream?.invalidate()
            stream = null
            if (currentOpen) skipCurrent()
            while (true) {
                val entry = try {
                    Archive.readNextHeader(archive)
                } catch (error: Throwable) {
                    throw archiveFailure("cannot read the next entry of $label", error)
                }
                if (entry == 0L) return null
                val rawPath = ArchiveEntry.pathnameUtf8(entry) ?: ""
                val path = normalizePatchPath(rawPath)
                if (path.isEmpty()) {
                    runCatching { Archive.readDataSkip(archive) }
                    continue
                }
                val directory = ArchiveEntry.filetype(entry) == ArchiveEntry.AE_IFDIR ||
                    rawPath.endsWith("/")
                val size = if (directory) {
                    0L
                } else {
                    // Some providers leave the 64-bit size at 0 until the data
                    // is read; the stat block is the reliable answer.
                    val statSize = runCatching { ArchiveEntry.stat(entry).stSize }.getOrDefault(-1L)
                    if (statSize > 0) {
                        statSize
                    } else if (ArchiveEntry.sizeIsSet(entry)) {
                        ArchiveEntry.size(entry).takeIf { it > 0 } ?: -1L
                    } else {
                        -1L
                    }
                }
                currentPath = path
                currentOpen = !directory
                return PatchEntry(path, size, directory)
            }
        }

        override fun open(): InputStream {
            if (closing) throw PatchSourceException("the session is closed", PatchSourceFailure.IO)
            if (!currentOpen) throw PatchSourceException("no entry was read before it was opened", PatchSourceFailure.IO)
            val created = EntryStream(this)
            stream = created
            return created
        }

        fun skipCurrent() {
            if (!currentOpen) return
            currentOpen = false
            try {
                Archive.readDataSkip(archive)
            } catch (error: Throwable) {
                throw archiveFailure("cannot skip $currentPath", error)
            }
        }

        fun entryEnded() {
            currentOpen = false
        }

        override fun close() {
            if (closing) return
            closing = true
            stream?.invalidate()
            stream = null
            runCatching { if (currentOpen) Archive.readDataSkip(archive) }
            currentOpen = false
            runCatching { Archive.readClose(archive) }
            runCatching { Archive.readFree(archive) }
            runCatching { descriptor?.close() }
            descriptor = null
        }
    }

    private class EntryStream(private val session: Session) : InputStream() {
        private val direct: ByteBuffer = ByteBuffer.allocateDirect(READ_BUFFER)
        private val bytes = ByteArray(READ_BUFFER)
        private var position = 0
        private var limit = 0
        private var ended = false
        private var closed = false

        override fun read(): Int {
            if (!fill()) return -1
            return bytes[position++].toInt() and 0xFF
        }

        override fun read(b: ByteArray, off: Int, len: Int): Int {
            if (len == 0) return 0
            if (!fill()) return -1
            val count = minOf(len, limit - position)
            System.arraycopy(bytes, position, b, off, count)
            position += count
            return count
        }

        private fun fill(): Boolean {
            if (closed) return false
            if (position < limit) return true
            if (ended) return false
            direct.clear()
            try {
                Archive.readData(session.archive, direct)
            } catch (error: Throwable) {
                ended = true
                session.entryEnded()
                throw archiveFailure("cannot read an entry of the archive", error)
            }
            val read = direct.position()
            if (read <= 0) {
                ended = true
                session.entryEnded()
                return false
            }
            direct.flip()
            direct.get(bytes, 0, read)
            position = 0
            limit = read
            return true
        }

        fun invalidate() {
            closed = true
        }

        override fun close() {
            if (closed) return
            closed = true
            if (!ended) session.skipCurrent()
        }
    }

    companion object {
        fun fromFiles(
            files: List<File>,
            label: String,
            kind: PatchSourceKind,
            password: String?,
        ): PatchArchiveSource {
            if (files.isEmpty()) throw PatchSourceException("no archive files were chosen", PatchSourceFailure.MISSING_PART)
            files.forEach {
                if (!it.isFile || !it.canRead()) {
                    throw PatchSourceException("cannot read archive part ${it.name}", PatchSourceFailure.IO)
                }
            }
            checkExtension(files.map { it.name })
            return PatchArchiveSource(
                context = null,
                paths = files.map { it.absolutePath },
                uri = null,
                label = label,
                kind = kind,
                partCount = files.size,
                password = password,
            )
        }

        fun fromDocument(
            context: Context,
            uri: Uri,
            label: String,
            password: String?,
        ): PatchArchiveSource = PatchArchiveSource(
            context = context,
            paths = null,
            uri = uri,
            label = label,
            kind = PatchSourceKind.ARCHIVE,
            partCount = 1,
            password = password,
        )

        /** Refuses an extension libarchive has no reader for, before any read. */
        private fun checkExtension(names: List<String>) {
            val first = names.firstOrNull()?.lowercase(Locale.US) ?: return
            val readable = first.endsWith(".zip") || first.endsWith(".rar") ||
                first.endsWith(".7z") || first.endsWith(".001") ||
                first.endsWith(".tar") || first.endsWith(".gz") ||
                first.endsWith(".tgz") || first.endsWith(".bz2") ||
                first.endsWith(".xz")
            if (!readable) {
                throw PatchSourceException(
                    "this is not an archive the app can read; use a .zip, .rar or .7z patch",
                    PatchSourceFailure.UNSUPPORTED_ARCHIVE,
                )
            }
        }

    }
}

private const val READ_BLOCK = 1L shl 20
private const val READ_BUFFER = 64 * 1024

/** libarchive's own words decide whether it was the password or the file. */
private fun archiveFailure(action: String, error: Throwable): PatchSourceException {
    val message = error.message ?: error.toString()
    val lower = message.lowercase(Locale.US)
    val password = lower.contains("passphrase") ||
        lower.contains("password") ||
        lower.contains("encrypt")
    return PatchSourceException(
        if (password) {
            "$action: $message (the archive is locked; type its password)"
        } else {
            "$action: $message"
        },
        if (password) PatchSourceFailure.PASSWORD else PatchSourceFailure.CORRUPT,
        error,
    )
}
