package com.pes2017.recomp

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Sharing a pad: what an exported file carries, and what an imported one is
 * refused for.
 *
 * The rejections matter more than the round trip. A player will eventually pick
 * the wrong file in the picker, and the difference between "nothing happened"
 * and "half my pad changed" is these checks.
 */
class LayoutEnvelopeTest {

    private fun layout(): ControlLayout {
        val pad = ControlLayout.defaults(800f, 360f)
        // A pad someone has actually edited: a spare bound, a swipe moved, a
        // renamed action button.
        pad.find("c1")!!.apply {
            swipeTap = NativeBridge.X
            swipeUp = NativeBridge.Y
            visible = true
        }
        pad.find("a")!!.label = "P1"
        pad.find("x")!!.setSwipeAction(Swipe.LEFT, SwipeAction.held(SwipeModifier.RT, NativeBridge.X))
        return pad
    }

    @Test
    fun anExportedPadReadsBackWithEverythingInIt() {
        val text = LayoutEnvelope.export(layout(), "Away pad", "0.0.1", createdAt = 123L)
        val result = LayoutEnvelope.import(text)

        assertTrue("expected an accepted layout, got $result", result is LayoutEnvelope.ImportResult.Ok)
        val ok = result as LayoutEnvelope.ImportResult.Ok
        assertEquals("Away pad", ok.name)
        assertEquals("P1", ok.layout.find("a")!!.label)
        assertEquals(NativeBridge.X, ok.layout.find("c1")!!.swipeTap)
        assertEquals(NativeBridge.Y, ok.layout.find("c1")!!.swipeUp)
        assertEquals(
            SwipeAction.held(SwipeModifier.RT, NativeBridge.X),
            ok.layout.find("x")!!.swipeAction(Swipe.LEFT),
        )
    }

    @Test
    fun theEnvelopeSaysWhatItIsAndWhoWroteIt() {
        val text = LayoutEnvelope.export(layout(), "Mine", "9.9.9", createdAt = 7L)
        val root = org.json.JSONObject(text)

        assertEquals(LayoutEnvelope.FORMAT, root.getString("format"))
        assertEquals(LayoutEnvelope.VERSION, root.getInt("envelope"))
        assertEquals("9.9.9", root.getString("app"))
        assertEquals(800.0, root.getJSONObject("device").getDouble("w"), 0.001)
    }

    @Test
    fun aBlankNameStillProducesAUsableOne() {
        val text = LayoutEnvelope.export(layout(), "   ", "0.0.1")
        val ok = LayoutEnvelope.import(text) as LayoutEnvelope.ImportResult.Ok
        assertEquals("Layout", ok.name)
    }

    @Test
    fun anImportedFileWithNoNameGetsOne() {
        val text = LayoutEnvelope.export(layout(), "x", "0.0.1")
            .replace(Regex("\"name\"\\s*:\\s*\"x\""), "\"name\":\"\"")
        val ok = LayoutEnvelope.import(text) as LayoutEnvelope.ImportResult.Ok
        assertEquals("Imported layout", ok.name)
    }

    @Test
    fun aFileThatIsNotJsonIsRefused() {
        assertTrue(LayoutEnvelope.import("<html>not a pad</html>") is LayoutEnvelope.ImportResult.Rejected)
    }

    @Test
    fun anotherAppsJsonIsRefused() {
        assertTrue(
            LayoutEnvelope.import("""{"something":"else"}""") is LayoutEnvelope.ImportResult.Rejected,
        )
    }

    @Test
    fun aNewerEnvelopeIsRefusedRatherThanGuessedAt() {
        val text = LayoutEnvelope.export(layout(), "Future", "9")
            .replace("\"envelope\": 1", "\"envelope\": 9")
        assertTrue(LayoutEnvelope.import(text) is LayoutEnvelope.ImportResult.Rejected)
    }

    @Test
    fun aLayoutWithNoControlsIsRefused() {
        val text = """{"format":"pes17-controls","envelope":1,"name":"Empty",
            "layout":{"version":8,"w":800,"h":360,"elements":[]}}"""
        assertTrue(LayoutEnvelope.import(text) is LayoutEnvelope.ImportResult.Rejected)
    }

    @Test
    fun aLayoutThisBuildCannotReadIsRefused() {
        // Inner version newer than the build: ControlLayout.parse says no.
        val text = LayoutEnvelope.export(layout(), "Newer", "1")
            .replace(Regex("\"version\"\\s*:\\s*9"), "\"version\": 99")
        assertTrue(LayoutEnvelope.import(text) is LayoutEnvelope.ImportResult.Rejected)
    }

    @Test
    fun anEnormousFileIsRefusedBeforeItIsParsed() {
        val huge = "x".repeat(LayoutEnvelope.MAX_CHARS + 1)
        assertTrue(LayoutEnvelope.import(huge) is LayoutEnvelope.ImportResult.Rejected)
    }

    @Test
    fun aRejectionSaysSomethingAPlayerCanActOn() {
        val result = LayoutEnvelope.import("{}") as LayoutEnvelope.ImportResult.Rejected
        assertTrue("the reason is a sentence", result.reason.endsWith("."))
    }
}
