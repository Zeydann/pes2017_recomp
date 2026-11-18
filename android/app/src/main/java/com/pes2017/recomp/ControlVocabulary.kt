package com.pes2017.recomp

import android.content.Context

/**
 * The words the pad editor and the floating labels use for what a control
 * sends: "Chip Shot", "One-Two", "Sprint: RT".
 *
 * [SwipeLabels] builds those names without Android types, so they can be
 * tested on a workstation and so the *layout* never depends on a language.
 * This object is the one place the English name a player sees is turned into
 * the current language: the UI passes its `getString` in, and any name without
 * a translation (a player's own pad label, a binding like "LB + A") is left
 * exactly as it was.
 *
 * The codes a player reads as buttons — A, B, LB, RT — stay as they are in
 * every language; only the words around them are translated.
 */
object ControlVocabulary {

    /** The resource for an English control name, or 0 when it is not ours. */
    private fun resource(english: String): Int = when (english.lowercase()) {
        // The four action buttons and what their swipes do.
        "shot" -> R.string.act_shot
        "cross" -> R.string.act_cross
        "pass" -> R.string.act_pass
        "through ball" -> R.string.act_through
        "double shot" -> R.string.act_double_shot
        "low cross" -> R.string.act_low_cross
        "double pass" -> R.string.act_double_pass
        "double through ball" -> R.string.act_double_through
        "chip shot" -> R.string.act_chip_shot
        "finesse shot" -> R.string.act_finesse_shot
        "manual shot" -> R.string.act_manual_shot
        "driven shot" -> R.string.act_driven_shot
        "early cross" -> R.string.act_early_cross
        "manual cross" -> R.string.act_manual_cross
        "controlled cross" -> R.string.act_controlled_cross
        "driven cross" -> R.string.act_driven_cross
        "one-two" -> R.string.act_one_two
        "manual pass" -> R.string.act_manual_pass
        "driven pass" -> R.string.act_driven_pass
        "controlled pass" -> R.string.act_controlled_pass
        "chipped through ball" -> R.string.act_chipped_through
        "manual through ball" -> R.string.act_manual_through
        "lofted through ball" -> R.string.act_lofted_through
        "controlled through ball" -> R.string.act_controlled_through
        // The tactical button's four arms.
        "attack 1" -> R.string.act_attack_1
        "attack 2" -> R.string.act_attack_2
        "defense 2" -> R.string.act_defense_2
        "defense 1" -> R.string.act_defense_1
        // A full stick throw.
        "ls up" -> R.string.flick_ls_up
        "ls down" -> R.string.flick_ls_down
        "ls left" -> R.string.flick_ls_left
        "ls right" -> R.string.flick_ls_right
        "rs up" -> R.string.flick_rs_up
        "rs down" -> R.string.flick_rs_down
        "rs left" -> R.string.flick_rs_left
        "rs right" -> R.string.flick_rs_right
        // The pad's own names for its bits, where they are words rather than
        // letters. "START"/"BACK" are spelled differently in the two editors,
        // hence the lowercase comparison.
        "start" -> R.string.bit_start
        "select", "back" -> R.string.bit_select
        "d-pad up" -> R.string.bit_dpad_up
        "d-pad down" -> R.string.bit_dpad_down
        "d-pad left" -> R.string.bit_dpad_left
        "d-pad right" -> R.string.bit_dpad_right
        "action" -> R.string.bit_action
        else -> 0
    }

    /** The name in the language of [context], or [english] unchanged. */
    fun of(context: Context, english: String): String {
        val id = resource(english)
        return if (id == 0) english else context.getString(id)
    }

    /** A TG1..TG6 action, as the mapping picker lists it. */
    fun toggle(context: Context, action: ToggleAction): String = context.getString(
        when (action) {
            ToggleAction.NOTHING -> R.string.toggle_nothing
            ToggleAction.SPRINT -> R.string.toggle_sprint
            ToggleAction.JOCKEY -> R.string.toggle_jockey
            ToggleAction.RUNNING_JOCKEY -> R.string.toggle_running_jockey
            ToggleAction.TEAMMATE_PRESSURE -> R.string.toggle_teammate_pressure
            ToggleAction.PRESSURE -> R.string.toggle_pressure
            ToggleAction.MANUAL_GK -> R.string.toggle_manual_gk
            ToggleAction.SUPER_CANCEL -> R.string.toggle_super_cancel
            ToggleAction.SHIELDING -> R.string.toggle_shielding
            ToggleAction.PLAYER_MOVEMENT -> R.string.toggle_player_movement
        },
    )
}
