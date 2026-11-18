package com.pes2017.recomp

import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import org.json.JSONObject

/**
 * A release tag as a value: "v0.2.2", and the pre-releases the project also
 * publishes -- "V0.2.2-rc1", "v0.3.0-alpha.13".
 *
 * The update check reads a tag, not a hand-written number: a release is
 * published by pushing a tag, so a new one has to be read without a code change
 * for every version. The order is the one every package manager uses (semantic
 * versioning, pre-release precedence): 0.2.2-alpha < 0.2.2-beta < 0.2.2 <
 * 0.2.3-alpha. A pre-release of a version is older than that version, and
 * pre-releases of one version are ordered by identifier, numerically where they
 * are numbers ("alpha.10" after "alpha.9", not before it).
 *
 * This is the Android twin of launcher/version.h: the same grammar, the same
 * order, and the same refusal to read what is not a version. A tag that cannot
 * be read has no order at all rather than a guessed one, because a guessed
 * order is how an update goes backwards.
 */
class ReleaseTag private constructor(
    private val numbers: List<Long>,
    private val pre: List<String>,
    /** False for "", "nightly", "1.x": a tag that is not a version. */
    val valid: Boolean,
) {

    companion object {
        private val NONE = ReleaseTag(emptyList(), emptyList(), false)

        /** Reads "v1.2.3", "V1.2", "v0.2.2-alpha.1"; anything else is not a version. */
        fun parse(tag: String): ReleaseTag {
            var at = 0
            // The tag this project publishes is "V0.2.2": both cases are a prefix.
            if (at < tag.length && (tag[at] == 'v' || tag[at] == 'V')) at++
            if (at >= tag.length) return NONE

            val numbers = ArrayList<Long>(4)
            while (true) {
                val start = at
                while (at < tag.length && tag[at] in '0'..'9') at++
                // "1.2." and "1..2" are not versions: a field has to be digits.
                if (at == start) return NONE
                numbers.add(number(tag, start, at))
                if (at < tag.length && tag[at] == '.') {
                    at++
                    continue
                }
                break
            }

            val pre = ArrayList<String>(2)
            if (at < tag.length) {
                if (tag[at] != '-') return NONE
                at++
                if (at >= tag.length) return NONE
                while (true) {
                    val start = at
                    while (at < tag.length && tag[at] != '.') {
                        val c = tag[at]
                        val allowed = c in '0'..'9' || c in 'a'..'z' || c in 'A'..'Z' || c == '-'
                        if (!allowed) return NONE
                        at++
                    }
                    if (at == start) return NONE          // "alpha..1", or a trailing '.'
                    pre.add(tag.substring(start, at))
                    if (at >= tag.length) break
                    at++                                   // the '.'
                    if (at >= tag.length) return NONE
                }
            }
            return ReleaseTag(numbers, pre, true)
        }

        /** -1 older, 0 the same version, 1 newer. Unreadable tags have no order. */
        fun compare(left: ReleaseTag, right: ReleaseTag): Int {
            if (!left.valid || !right.valid) return 0
            val fields = maxOf(left.numbers.size, right.numbers.size)
            for (i in 0 until fields) {
                // "1.2" is "1.2.0": a field one tag does not write is a zero.
                val a = left.numbers.getOrElse(i) { 0L }
                val b = right.numbers.getOrElse(i) { 0L }
                if (a != b) return if (a < b) -1 else 1
            }
            // The same release: the one without a pre-release came after those with.
            if (left.pre.isEmpty() != right.pre.isEmpty()) return if (left.pre.isEmpty()) 1 else -1
            for (i in 0 until minOf(left.pre.size, right.pre.size)) {
                val order = compareIdentifier(left.pre[i], right.pre[i])
                if (order != 0) return order
            }
            return left.pre.size.compareTo(right.pre.size)
        }

        /** Whether [candidate] is a later version than [installed]. */
        fun newer(candidate: ReleaseTag, installed: ReleaseTag): Boolean =
            compare(candidate, installed) > 0

        /** Numeric identifiers by value, and a numeric one below an alphanumeric one. */
        private fun compareIdentifier(left: String, right: String): Int {
            val leftNumber = numeric(left)
            val rightNumber = numeric(right)
            if (leftNumber && rightNumber) {
                val a = number(left, 0, left.length)
                val b = number(right, 0, right.length)
                return if (a < b) -1 else if (a > b) 1 else 0
            }
            if (leftNumber != rightNumber) return if (leftNumber) -1 else 1
            return left.compareTo(right)
        }

        private fun numeric(id: String): Boolean =
            id.isNotEmpty() && id.all { it in '0'..'9' }

        /** A run of digits, as a number too large for the type counts as very large. */
        private fun number(text: String, from: Int, to: Int): Long {
            var value = 0L
            for (i in from until to) {
                val digit = (text[i] - '0').toLong()
                if (value > (Long.MAX_VALUE - digit) / 10) return Long.MAX_VALUE
                value = value * 10 + digit
            }
            return value
        }
    }
}

/** What a look at the release feed found. */
sealed class UpdateResult {

    /**
     * A later release than this install. [apkUrl] is empty when the release
     * carries no APK, in which case the release page is the way to it.
     */
    class Newer(
        val tag: String,
        val versionName: String,
        val releaseCode: Int,
        val apkUrl: String,
        val page: String,
        val notes: String,
    ) : UpdateResult()

    /** This install is the newest published release. */
    class Current(val installed: String) : UpdateResult()

    /** The repository has no published release at all -- not an error. */
    object NoReleases : UpdateResult()

    /** The feed could not be reached: no connection, DNS, a timeout. */
    class Unreachable(val detail: String) : UpdateResult()

    /** The feed answered, but not with a release that can be compared. */
    class Unreadable(val detail: String) : UpdateResult()

    /** GitHub is refusing this network's requests for now (its rate limit). */
    object RateLimited : UpdateResult()
}

/**
 * The release feed of this port, and what the newest release means for the
 * installed APK.
 *
 * The address is the repository this port publishes from, and the version read
 * is the release's own tag: a new release is picked up by publishing it, with no
 * change here for every version. What can be compared is compared in the most
 * specific way the release offers -- a build number stated in the notes when it
 * is, the tag otherwise -- and nothing is ever assumed: a feed that cannot be
 * read is reported as such rather than as "up to date".
 */
object UpdateCheck {

    /** The repository these releases are published from. */
    const val REPO = "typhon-op/pes2017-recomp"

    /** Where GitHub describes the newest published release. */
    const val API = "https://api.github.com/repos/$REPO/releases/latest"

    /** Where a player goes to read about it, and to download by hand. */
    const val PAGE = "https://github.com/$REPO/releases/latest"

    /** One answer from the feed: the status, and whatever body came with it. */
    class Response(val status: Int, val body: String)

    /**
     * Checks the newest published release against the installed build.
     *
     * [fetch] is the transport, so the judgement can be checked without a
     * network ([UpdateCheckTest]).
     */
    fun check(
        installedCode: Int,
        installedName: String,
        api: String = API,
        fetch: (String) -> Response = ::http,
    ): UpdateResult {
        val response = try {
            fetch(api)
        } catch (error: IOException) {
            return UpdateResult.Unreachable(error.message ?: "no connection")
        } catch (error: Throwable) {
            return UpdateResult.Unreachable(error.javaClass.simpleName)
        }
        return when {
            // No published release is a state of the repository, not a failure.
            response.status == 404 -> UpdateResult.NoReleases
            // GitHub answers 403 when a network has spent its unauthenticated
            // requests; it comes back, so it is its own answer rather than an error.
            response.status == 403 -> UpdateResult.RateLimited
            response.status != 200 -> UpdateResult.Unreadable("HTTP ${response.status}")
            else -> judge(response.body, installedCode, installedName)
        }
    }

    /** What one release description says about [installedCode]/[installedName]. */
    fun judge(json: String, installedCode: Int, installedName: String): UpdateResult {
        val release = try {
            JSONObject(json)
        } catch (error: Throwable) {
            return UpdateResult.Unreadable("the feed is not a release")
        }
        val tag = release.optString("tag_name").trim()
        if (tag.isEmpty()) return UpdateResult.Unreadable("the release has no tag")
        val body = release.optString("body")
        val page = release.optString("html_url").ifEmpty { PAGE }
        val apkUrl = apkAsset(release)
        val releaseCode = versionCodeIn(body)

        val candidate = ReleaseTag.parse(tag)
        val installed = ReleaseTag.parse(installedName)
        val newer = when {
            candidate.valid && installed.valid -> {
                val order = ReleaseTag.compare(candidate, installed)
                // The tag is the version; a release re-published under the same
                // tag with a higher build is later too.
                order > 0 || (order == 0 && releaseCode > installedCode)
            }
            // A tag nobody can read falls back to the build number the release
            // states, if it states one.
            releaseCode > 0 -> releaseCode > installedCode
            else -> return UpdateResult.Unreadable("the tag \"$tag\" is not a version")
        }
        if (!newer) return UpdateResult.Current(installedName)
        return UpdateResult.Newer(
            tag = tag,
            versionName = tag.removePrefix("v").removePrefix("V"),
            releaseCode = releaseCode,
            apkUrl = apkUrl,
            page = page,
            notes = body.take(1500),
        )
    }

    /** The first APK among the release's assets, or "" when it has none. */
    private fun apkAsset(release: JSONObject): String {
        val assets = release.optJSONArray("assets") ?: return ""
        for (i in 0 until assets.length()) {
            val asset = assets.optJSONObject(i) ?: continue
            if (!asset.optString("name").endsWith(".apk", ignoreCase = true)) continue
            val url = asset.optString("browser_download_url")
            if (url.isNotEmpty()) return url
        }
        return ""
    }

    /**
     * The build number a release states in its notes -- "versionCode 42",
     * "versionCode: 42", "APK versionCode: 42" -- or 0 when it states none.
     * Android compares this number to decide what installs over what, so it is
     * worth reading when a release publishes it; a release that does not is
     * compared by its tag.
     */
    fun versionCodeIn(body: String): Int =
        Regex("versionCode\\s*[:=]?\\s*(\\d+)", RegexOption.IGNORE_CASE)
            .find(body)?.groupValues?.get(1)?.toIntOrNull() ?: 0

    /** GitHub's answer, read as one object, with an error status left to the caller. */
    private fun http(api: String): Response {
        val connection = (URL(api).openConnection() as HttpURLConnection).apply {
            connectTimeout = 15000
            readTimeout = 15000
            setRequestProperty("Accept", "application/vnd.github+json")
            setRequestProperty("X-GitHub-Api-Version", "2022-11-28")
            // GitHub refuses a request with no user agent.
            setRequestProperty("User-Agent", "pes2017-recomp-android")
        }
        try {
            val status = connection.responseCode
            val stream = if (status in 200..299) connection.inputStream else connection.errorStream
            val body = stream?.bufferedReader()?.use { it.readText() }.orEmpty()
            return Response(status, body)
        } finally {
            connection.disconnect()
        }
    }
}
