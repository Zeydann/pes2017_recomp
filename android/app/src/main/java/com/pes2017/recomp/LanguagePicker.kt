package com.pes2017.recomp

import android.app.Activity
import androidx.appcompat.app.AlertDialog

/**
 * The LANGUAGE screen: one row per language, the current one ticked, opened
 * from the main menu directly under CHECK FOR UPDATES.
 *
 * A dialog rather than a full screen, because it is a choice, not a page: the
 * list is short, the menu underneath stays where it was, and Back closes it the
 * way it closes every other prompt in the launcher. Picking a language hands it
 * to [Localization], which saves it and recreates the launcher in it.
 */
object LanguagePicker {

    fun show(activity: Activity, prefs: Prefs) {
        val selected = Localization.TAGS.indexOf(Localization.tagOf(prefs))
        val names = Localization.TAGS
            .map { activity.getString(Localization.label(it)) }
            .toTypedArray()
        AlertDialog.Builder(activity)
            .setTitle(R.string.language_title)
            .setMessage(R.string.language_hint)
            .setSingleChoiceItems(names, selected) { dialog, which ->
                dialog.dismiss()
                Localization.select(prefs, Localization.TAGS[which])
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }
}
