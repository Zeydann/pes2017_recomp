package com.pes2017.recomp

import android.app.Activity
import android.graphics.Typeface
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
 * Takes the Latin-only type treatment off every label on [this] screen when the
 * layout runs right to left. Three things, all of them ways Arabic text gets
 * cut on screen rather than in the translation:
 *
 *  - **Tracking.** Android applies letter spacing after every glyph, the last
 *    one included, and measures the line with it. In a right-to-left run that
 *    trailing space lands at the head of the line, so a label that fills its
 *    container is clipped there and the first letter of the row is cut off.
 *    Tracking also pulls a joined script's strokes apart, which reads as
 *    disconnected letters rather than a word.
 *  - **All caps.** The capital transformation is meaningful only for the
 *    Latin alphabet, and the transformation method rewrites the string before
 *    shaping; on Arabic it can drop the last glyph of a run. There is nothing
 *    to capitalise in Arabic, so it is turned off rather than left to do
 *    something.
 *  - **Italic.** `sans-serif-condensed` has no Arabic glyphs, so the system
 *    falls back to a Naskh face and *synthesises* the italic by shearing the
 *    whole line. The shear pushes the outer glyphs of a run past the text's
 *    own advances, and the view clips them: the first and last letters lose
 *    their edges. The family and its weight stay; only the skew goes.
 *
 * [PesMenu] applies the same three rules as it builds its rows; this is how the
 * screens whose labels come from layouts (the Main Menu, Settings, the guide)
 * get them, whichever of the six languages is selected.
 */
fun Activity.relaxRtlText() {
    if (resources.configuration.layoutDirection != View.LAYOUT_DIRECTION_RTL) return
    val root = window?.decorView ?: return
    root.visitTextViews { view ->
        view.letterSpacing = 0f
        if (view.isAllCaps) view.isAllCaps = false
        val face = view.typeface ?: return@visitTextViews
        if (face.style and Typeface.ITALIC != 0) {
            val weight = if (face.style and Typeface.BOLD != 0) Typeface.BOLD else Typeface.NORMAL
            view.typeface = Typeface.create(face, weight)
        }
    }
}

private fun View.visitTextViews(action: (TextView) -> Unit) {
    if (this is TextView) action(this)
    if (this is ViewGroup) for (index in 0 until childCount) getChildAt(index).visitTextViews(action)
}
