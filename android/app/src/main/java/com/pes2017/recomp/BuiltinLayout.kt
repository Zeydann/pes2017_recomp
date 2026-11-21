package com.pes2017.recomp

/**
 * The pad a fresh install starts from.
 *
 * It is the layout exported from the running app as `controls-current_layout.json`
 * (the "Current layout" the player had arranged), kept here rather than in
 * assets so [ControlLayout.starterLayout] can build it without a Context and
 * the unit tests see exactly the pad the app ships. Width and height are left
 * out on purpose: [ControlLayout.parse] then lays it out for the screen asking
 * for it, and every position is a fraction of that screen already.
 */
internal object BuiltinLayout {

    val JSON: String = """
{
  "version": 9,
  "profile": "modern",
  "elements": [
    { "id": "stick", "x": 0.10145379602909088, "y": 0.765017569065094, "size": 127.84000396728516, "alpha": 0.4000000059604645, "visible": true, "toggle": false, "when": "ALWAYS", "role": "NONE", "label": "LS" },
    { "id": "stick_mode", "x": 0.11354581266641617, "y": 0.4809688627719879, "size": 64, "alpha": 0.550000011920929, "visible": true, "toggle": false, "when": "ALWAYS", "role": "NONE", "label": "MODE" },
    { "id": "a", "x": 0.8685259222984314, "y": 0.8598616123199463, "size": 64, "alpha": 0.44999998807907104, "visible": true, "toggle": false, "when": "ALWAYS", "role": "PASS", "label": "A", "sub": "PASS", "sub2": "PRESS",
      "swipes": [4096, 4096, 4096, 4096, 0],
      "swipeActions": [ { "b": 4096, "m": "LB" }, { "b": 4096, "m": "LT" }, { "b": 4096, "m": "RB" }, { "b": 4096, "m": "RT" } ] },
    { "id": "b", "x": 0.9426294565200806, "y": 0.7145328521728516, "size": 64, "alpha": 0.44999998807907104, "visible": true, "toggle": false, "when": "ALWAYS", "role": "CROSS", "label": "B", "sub": "CROSS", "sub2": "TEAM",
      "swipes": [8192, 8192, 8192, 8192, 0],
      "swipeActions": [ { "b": 8192, "m": "LB" }, { "b": 8192, "d": true }, { "b": 8192, "m": "LT" }, { "b": 8192, "m": "RT" } ] },
    { "id": "x", "x": 0.7944223284721375, "y": 0.7145328521728516, "size": 64, "alpha": 0.44999998807907104, "visible": true, "toggle": false, "when": "ALWAYS", "role": "SHOOT", "label": "X", "sub": "SHOOT", "sub2": "SLIDE",
      "swipes": [16384, 16384, 16384, 16384, 0],
      "swipeActions": [ { "b": 16384, "m": "LB" }, { "b": 16384, "m": "RT" }, { "b": 16384, "m": "LT" }, { "b": 16384, "m": "RB" } ] },
    { "id": "y", "x": 0.8685259222984314, "y": 0.5536332130432129, "size": 64, "alpha": 0.44999998807907104, "visible": true, "toggle": false, "when": "ALWAYS", "role": "THROUGH", "label": "Y", "sub": "THROUGH", "sub2": "GK",
      "swipes": [32768, 32768, 32768, 32768, 0],
      "swipeActions": [ { "b": 32768, "m": "LB" }, { "b": 32768, "m": "LT" }, { "b": 32768, "m": "RB" }, { "b": 32768, "m": "RT" } ] },
    { "id": "lt", "x": 0.3486814498901367, "y": 0.5658521056175232, "size": 56, "alpha": 0.44999998807907104, "visible": false, "toggle": false, "when": "ALWAYS", "role": "NONE", "label": "LT" },
    { "id": "lb", "x": 0.8943958282470703, "y": 0.36999034881591797, "size": 56, "alpha": 0.44999998807907104, "visible": true, "toggle": false, "when": "ATTACK", "role": "NONE", "label": "LB" },
    { "id": "switch", "x": 0.7466135621070862, "y": 0.883217990398407, "size": 56, "alpha": 0.44999998807907104, "visible": true, "toggle": false, "when": "DEFEND", "role": "NONE", "label": "LB", "sub2": "SWITCH" },
    { "id": "rb", "x": 0.8331654071807861, "y": 0.21388408541679382, "size": 56, "alpha": 0.44999998807907104, "visible": true, "toggle": false, "when": "ALWAYS", "role": "NONE", "label": "RB" },
    { "id": "rt", "x": 0.4101375639438629, "y": 0.5516868233680725, "size": 56, "alpha": 0.44999998807907104, "visible": false, "toggle": false, "when": "ALWAYS", "role": "NONE", "label": "RT" },
    { "id": "right_stick", "x": 0.6758213043212891, "y": 0.6128943562507629, "size": 100, "alpha": 0.4000000059604645, "visible": true, "toggle": false, "when": "ALWAYS", "role": "NONE", "label": "RS" },
    { "id": "skill_double_touch", "x": 0.6055777072906494, "y": 0.2733564078807831, "size": 48, "alpha": 0.4000000059604645, "visible": false, "toggle": false, "when": "MATCH", "role": "NONE", "label": "D.TOUCH" },
    { "id": "skill_roulette", "x": 0.6055777072906494, "y": 0.4290657639503479, "size": 48, "alpha": 0.4000000059604645, "visible": false, "toggle": false, "when": "MATCH", "role": "NONE", "label": "ROULETTE" },
    { "id": "skill_rainbow", "x": 0.6055777072906494, "y": 0.5847750902175903, "size": 48, "alpha": 0.4000000059604645, "visible": false, "toggle": false, "when": "MATCH", "role": "NONE", "label": "RAINBOW" },
    { "id": "skill_drag_back", "x": 0.6055777072906494, "y": 0.7404844164848328, "size": 48, "alpha": 0.4000000059604645, "visible": false, "toggle": false, "when": "MATCH", "role": "NONE", "label": "DRAG BK" },
    { "id": "skill_body_feint", "x": 0.6055777072906494, "y": 0.89619380235672, "size": 48, "alpha": 0.4000000059604645, "visible": false, "toggle": false, "when": "MATCH", "role": "NONE", "label": "BODY FNT" },
    { "id": "trick", "x": 0.7131474018096924, "y": 0.3044982850551605, "size": 64, "alpha": 0.44999998807907104, "visible": true, "toggle": false, "when": "MATCH", "role": "NONE", "label": "SKILL", "trick": [6, 7, 4, 2, 3, 5, 0, 1] },
    { "id": "swipe", "x": 0.7430278658866882, "y": 0.8650519251823425, "size": 96, "alpha": 0.4000000059604645, "visible": false, "toggle": false, "when": "ALWAYS", "role": "NONE", "label": "SWIPE" },
    { "id": "dpad", "x": 0.7622263431549072, "y": 0.046055592596530914, "size": 110, "alpha": 0.4000000059604645, "visible": false, "toggle": false, "when": "ALWAYS", "role": "NONE", "label": "D-PAD" },
    { "id": "l3", "x": 0.33007967472076416, "y": 0.6800431609153748, "size": 52, "alpha": 0.44999998807907104, "visible": false, "toggle": false, "when": "ALWAYS", "role": "NONE", "label": "L3" },
    { "id": "r3", "x": 0.5643407106399536, "y": 0.424468457698822, "size": 52, "alpha": 0.44999998807907104, "visible": false, "toggle": false, "when": "ALWAYS", "role": "NONE", "label": "R3" },
    { "id": "start", "x": 0.5717131495475769, "y": 0.09602075815200806, "size": 54, "alpha": 0.75, "visible": true, "toggle": false, "when": "ALWAYS", "role": "NONE", "label": "START" },
    { "id": "back", "x": 0.45219123363494873, "y": 0.09602075815200806, "size": 54, "alpha": 0.75, "visible": true, "toggle": false, "when": "ALWAYS", "role": "NONE", "label": "SELECT" },
    { "id": "c1", "x": 0.07171314209699631, "y": 0.15570934116840363, "size": 48, "alpha": 0.4000000059604645, "visible": false, "toggle": false, "when": "ALWAYS", "role": "NONE", "label": "C1" },
    { "id": "c2", "x": 0.15256202220916748, "y": 0.1893906146287918, "size": 48, "alpha": 0.4000000059604645, "visible": false, "toggle": false, "when": "ALWAYS", "role": "NONE", "label": "C2" },
    { "id": "c3", "x": 0.20557768642902374, "y": 0.15570934116840363, "size": 48, "alpha": 0.4000000059604645, "visible": false, "toggle": false, "when": "ALWAYS", "role": "NONE", "label": "C3" },
    { "id": "c4", "x": 0.27250996232032776, "y": 0.15570934116840363, "size": 48, "alpha": 0.4000000059604645, "visible": false, "toggle": false, "when": "ALWAYS", "role": "NONE", "label": "C4" },
    { "id": "c5", "x": 0.3224601447582245, "y": 0.1492822766304016, "size": 48, "alpha": 0.4000000059604645, "visible": false, "toggle": false, "when": "ALWAYS", "role": "NONE", "label": "C5" },
    { "id": "c6", "x": 0.4063744843006134, "y": 0.15570934116840363, "size": 48, "alpha": 0.4000000059604645, "visible": false, "toggle": false, "when": "ALWAYS", "role": "NONE", "label": "C6" },
    { "id": "c7", "x": 0.4733067750930786, "y": 0.15570934116840363, "size": 48, "alpha": 0.4000000059604645, "visible": false, "toggle": false, "when": "ALWAYS", "role": "NONE", "label": "C7" },
    { "id": "c8", "x": 0.5402390360832214, "y": 0.15570934116840363, "size": 48, "alpha": 0.4000000059604645, "visible": false, "toggle": false, "when": "ALWAYS", "role": "NONE", "label": "C8" },
    { "id": "c9", "x": 0.6071712970733643, "y": 0.15570934116840363, "size": 48, "alpha": 0.4000000059604645, "visible": false, "toggle": false, "when": "ALWAYS", "role": "NONE", "label": "C9" },
    { "id": "c10", "x": 0.6741035580635071, "y": 0.15570934116840363, "size": 48, "alpha": 0.4000000059604645, "visible": false, "toggle": false, "when": "ALWAYS", "role": "NONE", "label": "C10" },
    { "id": "jockey", "x": 0.21035856008529663, "y": 0.19723182916641235, "size": 56, "alpha": 0.44999998807907104, "visible": false, "toggle": true, "when": "ALWAYS", "role": "JOCKEY", "label": "JCK" },
    { "id": "controll", "x": 0.7787358164787292, "y": 0.4404947757720947, "size": 56, "alpha": 0.44999998807907104, "visible": true, "toggle": true, "when": "MATCH", "role": "NONE", "label": "Controll" },
    { "id": "tg1", "x": 0.07171314209699631, "y": 0.38927334547042847, "size": 52, "alpha": 0.4000000059604645, "visible": false, "toggle": false, "when": "ALWAYS", "role": "NONE", "label": "TG1" },
    { "id": "tg2", "x": 0.14342628419399261, "y": 0.38927334547042847, "size": 52, "alpha": 0.4000000059604645, "visible": false, "toggle": false, "when": "ALWAYS", "role": "NONE", "label": "TG2" },
    { "id": "tg3", "x": 0.21513943374156952, "y": 0.38927334547042847, "size": 52, "alpha": 0.4000000059604645, "visible": false, "toggle": false, "when": "ALWAYS", "role": "NONE", "label": "TG3" },
    { "id": "tg4", "x": 0.5085159540176392, "y": 0.37661778926849365, "size": 52, "alpha": 0.4000000059604645, "visible": false, "toggle": false, "when": "ALWAYS", "role": "NONE", "label": "TG4" },
    { "id": "tg5", "x": 0.35856571793556213, "y": 0.38927334547042847, "size": 52, "alpha": 0.4000000059604645, "visible": false, "toggle": false, "when": "ALWAYS", "role": "NONE", "label": "TG5" },
    { "id": "tg6", "x": 0.43027886748313904, "y": 0.38927334547042847, "size": 52, "alpha": 0.4000000059604645, "visible": false, "toggle": false, "when": "ALWAYS", "role": "NONE", "label": "TG6" },
    { "id": "sw1", "x": 0.2663755416870117, "y": 0.5476015210151672, "size": 88, "alpha": 0.4000000059604645, "visible": false, "toggle": false, "when": "ALWAYS", "role": "NONE", "label": "SW1",
      "swipes": [1, 2, 2, 1, 0],
      "swipeActions": [ { "b": 1 }, { "b": 2, "m": "LB" }, { "b": 2, "d": true }, { "b": 1, "d": true } ] },
    { "id": "sw2", "x": 0.4958617687225342, "y": 0.8634780645370483, "size": 88, "alpha": 0.4000000059604645, "visible": true, "toggle": false, "when": "ALWAYS", "role": "NONE", "label": "ADV",
      "swipes": [1, 2, 4, 8, 0],
      "swipeActions": [ { "b": 1, "m": "LT" }, { "b": 2, "m": "LT" }, { "b": 4, "m": "LT" }, { "b": 8, "m": "LT" } ] },
    { "id": "mentality_atk", "x": 0.6071235537528992, "y": 0.9120933413505554, "size": 60.599998474121094, "alpha": 0.44999998807907104, "visible": true, "toggle": false, "when": "MATCH", "role": "NONE", "label": "ATTACK", "sub": "ATTACKING" },
    { "id": "mentality_def", "x": 0.3847433924674988, "y": 0.9146807789802551, "size": 62.23999786376953, "alpha": 0.4399999976158142, "visible": true, "toggle": false, "when": "MATCH", "role": "NONE", "label": "DEFENSE", "sub": "DEFENSE" }
  ],
  "removed": ["look", "stick2"]
}
""".trimIndent()
}
