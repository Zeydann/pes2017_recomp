package com.pes2017.recomp

import org.json.JSONObject

/**
 * The file a layout is exported as, and the checks an imported one has to pass.
 *
 * A bare `controls.json` is the app's working file: it has no name, no idea
 * which build wrote it, and no marker that says what it is. Sharing one is
 * therefore easy to get wrong -- a player can send a screenshot, a capture or
 * an unrelated json and the app has to say no rather than half-apply it.
 *
 * The envelope wraps the layout with enough to answer three questions before
 * anything is touched: is this ours, is it from a build that understood it, and
 * is it small enough to be a pad rather than a mistake. Only then is the inner
 * layout handed to [ControlLayout.parse], which keeps its own clamps and its
 * own version rule, so a hostile file cannot produce out-of-range geometry or a
 * control this build does not know.
 */
object LayoutEnvelope {

    /** What an exported pad says it is. */
    const val FORMAT = "pes17-controls"

    /** The envelope's own version, independent of the layout's. */
    const val VERSION = 1

    /** A pad is a few kilobytes; this is far above any real one and far below a crash. */
    const val MAX_CHARS = 512 * 1024

    /** No pad has ever had this many controls; a file that does is not a pad. */
    const val MAX_ELEMENTS = 64

    /** What reading an imported file produced: a pad, or the reason there is none. */
    sealed class ImportResult {
        data class Ok(val layout: ControlLayout, val name: String) : ImportResult()
        data class Rejected(val reason: String) : ImportResult()
    }

    /**
     * The text of an exported layout: the pad, its name, when and by what it was
     * written, and the window it was laid out for. The device size travels so a
     * pad opened on another phone can be read against the shape it was made on.
     */
    fun export(
        layout: ControlLayout,
        name: String,
        appVersion: String,
        createdAt: Long = System.currentTimeMillis(),
    ): String {
        val root = JSONObject()
        root.put("format", FORMAT)
        root.put("envelope", VERSION)
        root.put("name", name.trim().take(40).ifEmpty { "Layout" })
        root.put("created", createdAt)
        root.put("app", appVersion)
        val device = JSONObject()
        device.put("w", layout.widthDp.toDouble())
        device.put("h", layout.heightDp.toDouble())
        root.put("device", device)
        root.put("layout", JSONObject(layout.toJson()))
        return root.toString(2)
    }

    /**
     * Reads an exported layout. Every rejection names what was wrong in words a
     * player can act on ("that file is not a layout"), and nothing is applied
     * unless the whole file reads.
     */
    fun import(text: String): ImportResult {
        if (text.length > MAX_CHARS) return ImportResult.Rejected("That file is too large to be a layout.")
        val root = try {
            JSONObject(text)
        } catch (_: Throwable) {
            return ImportResult.Rejected("That file is not a layout.")
        }
        if (root.optString("format") != FORMAT) {
            return ImportResult.Rejected("That file is not a PES 2017 controls layout.")
        }
        val envelope = root.optInt("envelope", 0)
        if (envelope <= 0) return ImportResult.Rejected("That layout has no version.")
        if (envelope > VERSION) {
            return ImportResult.Rejected("That layout was written by a newer version of the app.")
        }
        val inner = root.optJSONObject("layout")
            ?: return ImportResult.Rejected("That file has no controls in it.")
        val elements = inner.optJSONArray("elements")
            ?: return ImportResult.Rejected("That file has no controls in it.")
        if (elements.length() == 0) return ImportResult.Rejected("That layout has no controls in it.")
        if (elements.length() > MAX_ELEMENTS) {
            return ImportResult.Rejected("That layout has more controls than the pad has room for.")
        }
        val device = root.optJSONObject("device")
        val widthDp = device?.optDouble("w", 800.0)?.toFloat() ?: 800f
        val heightDp = device?.optDouble("h", 360.0)?.toFloat() ?: 360f
        val layout = ControlLayout.parse(inner.toString(), widthDp, heightDp)
            ?: return ImportResult.Rejected("That layout could not be read by this version of the app.")
        val name = root.optString("name", "").trim().take(40).ifEmpty { "Imported layout" }
        return ImportResult.Ok(layout, name)
    }
}
