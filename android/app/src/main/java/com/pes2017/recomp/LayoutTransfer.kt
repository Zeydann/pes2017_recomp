package com.pes2017.recomp

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.provider.OpenableColumns
import androidx.core.content.FileProvider
import java.io.File

/**
 * Getting a layout out of the app, and back in.
 *
 * Two doors, because players share pads in two ways: the Share Sheet (send it
 * to someone now) and a document the player chooses (keep it, put it in Drive,
 * paste it anywhere). Import is the system's own picker rather than a custom
 * file list, so the app never sees a path it does not have permission for.
 *
 * The exported file is written to the app's cache, never to the game folder:
 * a shared layout is a temporary artefact, and the provider below only grants
 * read access to the URI it hands out.
 */
object LayoutTransfer {

    /** The request code an import is launched with, forwarded by the host activity. */
    const val REQUEST_IMPORT = 0x1A70

    /** Largest file the picker will read: the envelope refuses more, so it is not read. */
    private const val MAX_BYTES = 512 * 1024

    private const val CACHE_DIR = "layouts"

    /**
     * Writes the layout to the cache and opens the Share Sheet with it. Returns
     * false when the file could not be written, in which case nothing is shown.
     */
    fun share(activity: Activity, json: String, name: String): Boolean {
        val file = try {
            val dir = File(activity.cacheDir, CACHE_DIR)
            if (!dir.exists()) dir.mkdirs()
            val safe = LayoutStore.sanitize(name)
            File(dir, "controls-$safe.json").apply { writeText(json) }
        } catch (_: Throwable) {
            return false
        }
        return try {
            val uri = FileProvider.getUriForFile(activity, "${activity.packageName}.captures", file)
            val send = Intent(Intent.ACTION_SEND).apply {
                type = "application/json"
                putExtra(Intent.EXTRA_STREAM, uri)
                putExtra(Intent.EXTRA_SUBJECT, name)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            activity.startActivity(
                Intent.createChooser(send, activity.getString(R.string.layout_export))
                    .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION),
            )
            true
        } catch (_: Throwable) {
            false
        }
    }

    /** Opens the system's document picker for a layout file. */
    fun openDocument(activity: Activity) {
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "application/json"
            putExtra(Intent.EXTRA_MIME_TYPES, arrayOf("application/json", "text/plain"))
        }
        try {
            activity.startActivityForResult(intent, REQUEST_IMPORT)
        } catch (_: Throwable) {
            // No picker on this device: the player can still share one out.
        }
    }

    /** Reads back what the picker returned, or null when it cannot be read. */
    fun read(activity: Activity, uri: Uri): String? = try {
        activity.contentResolver.openInputStream(uri)?.use { stream ->
            // Read at most one byte past the cap, so an enormous file is refused
            // by size instead of being loaded into memory first.
            val buffer = ByteArray(MAX_BYTES + 1)
            var total = 0
            while (total < buffer.size) {
                val read = stream.read(buffer, total, buffer.size - total)
                if (read <= 0) break
                total += read
            }
            if (total > MAX_BYTES) null else String(buffer, 0, total, Charsets.UTF_8)
        }
    } catch (_: Throwable) {
        null
    }

    /** The name the picked document has, for a layout that carries none of its own. */
    fun displayName(activity: Activity, uri: Uri): String = try {
        activity.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
            ?.use { cursor ->
                if (cursor.moveToFirst()) cursor.getString(0) ?: "" else ""
            } ?: ""
    } catch (_: Throwable) {
        ""
    }
}
