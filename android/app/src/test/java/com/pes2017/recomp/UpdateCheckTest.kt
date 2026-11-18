package com.pes2017.recomp

import java.io.IOException
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The release check: what a tag means, what a release description says about
 * the installed APK, and which answers are errors.
 *
 * This is the part of the updater that is wrong quietly: a tag read as text
 * offers an update that goes backwards, a missing build number looks like "no
 * update", and a network that refused is reported as "up to date". None of it
 * needs a phone, a network or a release to check.
 */
class UpdateCheckTest {

    private fun release(
        tag: String,
        body: String = "",
        apk: String = "app-debug.apk",
        url: String = "https://example.test/app-debug.apk",
        page: String = "https://example.test/releases/tag/$tag",
    ): String {
        val json = JSONObject()
        json.put("tag_name", tag)
        json.put("body", body)
        json.put("html_url", page)
        if (apk.isNotEmpty()) {
            json.put("assets", JSONArray().put(
                JSONObject().put("name", apk).put("browser_download_url", url),
            ))
        }
        return json.toString()
    }

    // ---- the tag as a value ------------------------------------------------

    private fun order(a: String, b: String): Int =
        ReleaseTag.compare(ReleaseTag.parse(a), ReleaseTag.parse(b))

    @Test
    fun aLeadingVIsTheSameTagInEitherCase() {
        // This project's own release tags are "V0.2.2"; the app says "0.2.2".
        assertTrue(ReleaseTag.parse("V0.2.2").valid)
        assertTrue(ReleaseTag.parse("v0.2.2").valid)
        assertEquals(0, order("V0.2.2", "0.2.2"))
        assertEquals(0, order("v1.2", "v1.2.0"))
    }

    @Test
    fun aLaterTagIsNewerAndTheComparisonIsTwoWay() {
        assertTrue(order("v0.2.2", "v0.2.3") < 0)
        assertTrue(order("v0.2.3", "v0.2.2") > 0)
        assertTrue(ReleaseTag.newer(ReleaseTag.parse("v0.2.3"), ReleaseTag.parse("v0.2.2")))
        assertFalse(ReleaseTag.newer(ReleaseTag.parse("v0.2.2"), ReleaseTag.parse("v0.2.3")))
    }

    @Test
    fun aPreReleaseIsOlderThanItsRelease() {
        assertTrue(order("v0.2.2-alpha", "v0.2.2") < 0)
        assertTrue(order("v0.2.2", "v0.3.0-alpha") < 0)
        assertFalse(ReleaseTag.newer(ReleaseTag.parse("V0.2.2"),
            ReleaseTag.parse("0.2.2")))
        assertFalse(ReleaseTag.newer(ReleaseTag.parse("v0.2.2-alpha"), ReleaseTag.parse("v0.2.2")))
    }

    @Test
    fun preReleaseIdentifiersAreOrderedAsTheSpecificationDoes() {
        assertTrue(order("v0.2.2-alpha", "v0.2.2-alpha.1") < 0)
        assertTrue(order("v0.2.2-alpha.2", "v0.2.2-alpha.10") < 0)   // numeric, not textual
        assertTrue(order("v0.2.2-alpha", "v0.2.2-beta") < 0)
        assertTrue(order("v0.2.2-1", "v0.2.2-alpha") < 0)             // a number sorts below text
        assertEquals(0, order("v0.2.2-alpha.1", "v0.2.2-alpha.01"))
    }

    @Test
    fun whatIsNotAVersionHasNoOrder() {
        val nothing = ReleaseTag.parse("nightly")
        assertFalse(nothing.valid)
        assertEquals(0, ReleaseTag.compare(nothing, ReleaseTag.parse("v1.0.0")))
        assertFalse(ReleaseTag.newer(nothing, ReleaseTag.parse("v1.0.0")))
        for (bad in listOf("", "v", "1.x", "1..2", "1.2.", "v1.2.3-", "v1.2.3 ", "-1.2.3")) {
            assertFalse("\"$bad\" is not a version", ReleaseTag.parse(bad).valid)
        }
    }

    // ---- what a release says about this install ----------------------------

    @Test
    fun theTagAloneDecidesWhichReleaseIsNewer() {
        // The published 0.2.2 release states no build number at all, so the tag
        // is what has to be compared -- this is the case that used to be read as
        // "no update" for ever.
        val installed = UpdateCheck.judge(release("V0.2.2"), 42, "0.2.2")
        assertTrue(installed is UpdateResult.Current)

        val newer = UpdateCheck.judge(release("V0.2.3"), 42, "0.2.2")
        assertTrue(newer is UpdateResult.Newer)
        newer as UpdateResult.Newer
        assertEquals("V0.2.3", newer.tag)
        assertEquals("0.2.3", newer.versionName)
        assertEquals("https://example.test/app-debug.apk", newer.apkUrl)
        assertEquals("https://example.test/releases/tag/V0.2.3", newer.page)
    }

    @Test
    fun anyFutureTagIsPickedUpWithoutACodeChange() {
        // Nothing here knows version 0.4.1 in advance: the tag is the version.
        assertTrue(UpdateCheck.judge(release("v0.4.1"), 42, "0.2.2") is UpdateResult.Newer)
        assertTrue(UpdateCheck.judge(release("v1.0.0"), 42, "0.2.2") is UpdateResult.Newer)
        assertTrue(UpdateCheck.judge(release("v0.2.2"), 42, "0.2.2") is UpdateResult.Current)
        assertTrue(UpdateCheck.judge(release("v0.2.1"), 42, "0.2.2") is UpdateResult.Current)
    }

    @Test
    fun aBuildNumberInTheNotesIsReadInEveryFormTheProjectWritesIt() {
        assertEquals(43, UpdateCheck.versionCodeIn("PES 2017 Android 0.2.3 (versionCode 43)"))
        assertEquals(43, UpdateCheck.versionCodeIn("APK versionCode: 43"))
        assertEquals(43, UpdateCheck.versionCodeIn("versionCode:43"))
        assertEquals(0, UpdateCheck.versionCodeIn("no number here"))
    }

    @Test
    fun aReleaseRepublishedUnderTheSameTagWithAHigherBuildIsNewer() {
        val body = "version 0.2.2 (versionCode 43)"
        val result = UpdateCheck.judge(release("V0.2.2", body = body), 42, "0.2.2")
        assertTrue(result is UpdateResult.Newer)
        assertEquals(43, (result as UpdateResult.Newer).releaseCode)
    }

    @Test
    fun anUnreadableTagFallsBackToTheStatedBuildNumber() {
        val body = "nightly, versionCode 44"
        assertTrue(UpdateCheck.judge(release("nightly", body = body), 42, "0.2.2") is UpdateResult.Newer)
        assertTrue(UpdateCheck.judge(release("nightly", body = body), 44, "0.2.2") is UpdateResult.Current)
    }

    @Test
    fun aReleaseWithNothingToCompareIsReportedRatherThanCalledCurrent() {
        val result = UpdateCheck.judge(release("nightly"), 42, "0.2.2")
        assertTrue(result is UpdateResult.Unreadable)
    }

    @Test
    fun aReleaseWithoutAnApkIsStillAnUpdate() {
        val result = UpdateCheck.judge(release("v0.3.0", apk = ""), 42, "0.2.2")
        assertTrue(result is UpdateResult.Newer)
        assertEquals("", (result as UpdateResult.Newer).apkUrl)
    }

    @Test
    fun aFeedThatIsNotAReleaseIsReported() {
        assertTrue(UpdateCheck.judge("not json at all", 42, "0.2.2") is UpdateResult.Unreadable)
        assertTrue(UpdateCheck.judge(JSONObject().toString(), 42, "0.2.2") is UpdateResult.Unreadable)
    }

    // ---- the answers that are not a version --------------------------------

    @Test
    fun aRepositoryWithoutReleasesIsNotAnError() {
        val result = UpdateCheck.check(42, "0.2.2") { UpdateCheck.Response(404, "") }
        assertTrue(result is UpdateResult.NoReleases)
    }

    @Test
    fun aRateLimitedNetworkSaysSoRatherThanUpToDate() {
        val result = UpdateCheck.check(42, "0.2.2") { UpdateCheck.Response(403, "{}") }
        assertTrue(result is UpdateResult.RateLimited)
    }

    @Test
    fun aServerErrorIsAFailedCheckNotAnUpToDateOne() {
        val result = UpdateCheck.check(42, "0.2.2") { UpdateCheck.Response(500, "") }
        assertTrue(result is UpdateResult.Unreadable)
    }

    @Test
    fun noConnectionIsAFailedCheckNotAnUpToDateOne() {
        val result = UpdateCheck.check(42, "0.2.2") { throw IOException("offline") }
        assertTrue(result is UpdateResult.Unreachable)
    }

    @Test
    fun aGoodAnswerIsJudgedAgainstTheInstalledBuild() {
        val result = UpdateCheck.check(42, "0.2.2") {
            UpdateCheck.Response(200, release("v0.3.0"))
        }
        assertTrue(result is UpdateResult.Newer)
    }
}
