package com.pes2017.recomp

import android.app.Activity
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat

/**
 * The six languages the app speaks, and the one place the choice is applied.
 *
 * Every screen reads its text through `getString`, so the language is an
 * Android resource qualifier (`values-ar`, `values-fr`, ...) rather than a
 * lookup table in code: the platform picks the right string, lays out Arabic
 * right to left, and shapes its letters. The selected language is kept in
 * [Prefs] and handed to AppCompat, which recreates whatever screen is open so
 * a change shows at once.
 *
 * English is the default for a new install, whatever the phone's own language
 * is: the launcher's first screen is in English until the player chooses
 * otherwise (LANGUAGE, under CHECK FOR UPDATES).
 */
object Localization {

    /** English, for an install that has never chosen. */
    const val DEFAULT = "en"

    /** The stored tags, in the order the selector lists them. */
    val TAGS = listOf("en", "ar", "fr", "es", "ru", "pt")

    /** The language this install uses; anything unknown falls back to English. */
    fun tagOf(prefs: Prefs): String = TAGS.firstOrNull { it == prefs.language } ?: DEFAULT

    /**
     * The name shown in the selector. Each language names itself, so a player
     * who cannot read the current one can still find their own.
     */
    fun label(tag: String): Int = when (tag) {
        "ar" -> R.string.language_name_ar
        "fr" -> R.string.language_name_fr
        "es" -> R.string.language_name_es
        "ru" -> R.string.language_name_ru
        "pt" -> R.string.language_name_pt
        else -> R.string.language_name_en
    }

    /**
     * European Portuguese is `pt-PT`: a bare `pt` would let Android take a
     * Brazilian values-pt translation from a library, and this app ships only
     * Portugal's.
     */
    fun localeTag(tag: String): String = if (tag == "pt") "pt-PT" else tag

    /**
     * Hands the language to AppCompat. It persists its own copy, applies it to
     * the running activity (recreating it) and every one opened afterwards, and
     * also sets the process-wide default so dates and numbers follow.
     */
    fun apply(tag: String) {
        val locales = LocaleListCompat.forLanguageTags(localeTag(tag))
        if (AppCompatDelegate.getApplicationLocales().toLanguageTags() != locales.toLanguageTags()) {
            AppCompatDelegate.setApplicationLocales(locales)
        }
    }

    /** Called once per process start, before any screen exists. */
    fun ensure(prefs: Prefs) {
        val tag = tagOf(prefs)
        if (prefs.language != tag) prefs.language = tag
        apply(tag)
    }

    /** A choice from the selector: saved first, then applied, so a restart keeps it. */
    fun select(prefs: Prefs, tag: String) {
        if (tag !in TAGS) return
        prefs.language = tag
        apply(tag)
    }
}

/**
 * Drops letter-spacing from every label on [this] screen when the layout runs
 * right to left.
 *
 * Two reasons, one of them a bug. Android applies the tracking after every
 * glyph, the last one included, and measures the line with it; in a
 * right-to-left run that trailing space lands at the head of the line, so a
 * label that fills its container is clipped there and the first Arabic letter
 * of the row disappears off the panel's edge. Tracking is also a Latin
 * typographic device: Arabic is written joined, and pulling the glyphs apart
 * reads as disconnected letters rather than a word.
 *
 * [PesMenu] does the same by hand for the dialog it draws; this is how the
 * screens whose labels come from layouts (the Main Menu, Settings, the guide)
 * get it, whichever of the six languages is selected.
 */
fun Activity.dropRtlTracking() {
    if (resources.configuration.layoutDirection != View.LAYOUT_DIRECTION_RTL) return
    val root = window?.decorView ?: return
    root.visitTextViews { it.letterSpacing = 0f }
}

private fun View.visitTextViews(action: (TextView) -> Unit) {
    if (this is TextView) action(this)
    if (this is ViewGroup) for (index in 0 until childCount) getChildAt(index).visitTextViews(action)
}
