package com.pes2017.recomp

import android.content.Context
import android.media.AudioAttributes
import android.media.SoundPool

/**
 * The in-game menu's two sounds: a short beep for a press, and the toggle
 * click for a row that switches something on or off.
 *
 * Both live in `res/raw` (`menu_press.mp3`, `menu_toggle.wav`) and are loaded
 * once, lazily, by the first [PesMenu] that opens. Everything here is
 * best-effort: a device with no audio output, a pool that refuses to build and
 * a sample that fails to decode all end the same way -- [press] and [toggle]
 * do nothing, and no menu press throws. That matters because a missing sound
 * must never be the reason a menu button stops working.
 */
object MenuSounds {

    /** Four at once is enough for a fast thumb; each play is one short sample. */
    private const val MAX_STREAMS = 4

    private var pool: SoundPool? = null
    private var pressId = 0
    private var toggleId = 0

    /** Set once a load has failed, so a dead pool is not rebuilt on every menu. */
    private var unavailable = false
    private var prepared = false

    /** Loads both samples. Safe to call from every menu; only the first does work. */
    fun prepare(context: Context) {
        if (prepared || unavailable) return
        prepared = true
        try {
            val built = SoundPool.Builder()
                .setMaxStreams(MAX_STREAMS)
                .setAudioAttributes(
                    AudioAttributes.Builder()
                        // Sonification, not music: the phone's media volume
                        // still applies, and the game's own audio is untouched.
                        .setUsage(AudioAttributes.USAGE_GAME)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build(),
                )
                .build()
            pressId = built.load(context.applicationContext, R.raw.menu_press, 1)
            toggleId = built.load(context.applicationContext, R.raw.menu_toggle, 1)
            pool = built
        } catch (_: Throwable) {
            // No pool, no ids: the menu keeps working silently.
            unavailable = true
            pool = null
            pressId = 0
            toggleId = 0
        }
    }

    /** A row was pressed, the band moved, or a value was adjusted. */
    fun press() = play(pressId, 0.85f)

    /** A row switched something: touch controls, the pad, a HUD level. */
    fun toggle() = play(toggleId, 1f)

    private fun play(sample: Int, volume: Float) {
        val sound = pool ?: return
        if (sample <= 0) return
        try {
            // A sample that is still loading returns 0 and says nothing: the
            // first press of the first menu is simply quiet, never an error.
            sound.play(sample, volume, volume, 1, 0, 1f)
        } catch (_: Throwable) {
            // A released pool, a device that went away: nothing to report.
        }
    }

    /** Ends the pool; the next menu prepares it again. */
    fun release() {
        runCatching { pool?.release() }
        pool = null
        pressId = 0
        toggleId = 0
        prepared = false
    }
}
